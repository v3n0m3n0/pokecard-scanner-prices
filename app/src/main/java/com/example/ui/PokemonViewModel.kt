package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

sealed interface MainSearchUiState {
    object Idle : MainSearchUiState
    object Loading : MainSearchUiState
    data class Success(val cards: List<TcgCard>) : MainSearchUiState
    data class Error(val message: String) : MainSearchUiState
}

class PokemonViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PokemonCardDatabase.getDatabase(application)
    private val repository = PokemonCardRepository(db)

    // Reactive lists from Database
    val favorites: StateFlow<List<FavoritePokemonCard>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntry>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _searchUiState = MutableStateFlow<MainSearchUiState>(MainSearchUiState.Idle)
    val searchUiState: StateFlow<MainSearchUiState> = _searchUiState.asStateFlow()

    private val _selectedCard = MutableStateFlow<TcgCard?>(null)
    val selectedCard: StateFlow<TcgCard?> = _selectedCard.asStateFlow()

    private val _isAnalyzingImage = MutableStateFlow(false)
    val isAnalyzingImage: StateFlow<Boolean> = _isAnalyzingImage.asStateFlow()

    private val _analysingResultText = MutableStateFlow<String?>(null)
    val analysingResultText: StateFlow<String?> = _analysingResultText.asStateFlow()

    // OLX filters
    private val _olxLocation = MutableStateFlow("")
    val olxLocation: StateFlow<String> = _olxLocation.asStateFlow()

    // Rarity filter state
    private val _selectedRarity = MutableStateFlow<String?>(null)
    val selectedRarity: StateFlow<String?> = _selectedRarity.asStateFlow()

    val speciesList: StateFlow<List<String>> = PokemonSpeciesRepository.speciesList

    init {
        viewModelScope.launch {
            PokemonSpeciesRepository.loadSpeciesFromApi()
        }
    }

    fun getPokemonSuggestions(query: String): List<String> {
        return PokemonSpeciesRepository.getSuggestions(query)
    }

    fun updateOlxLocation(city: String) {
        _olxLocation.value = city
    }

    fun updateSelectedRarity(rarity: String?) {
        _selectedRarity.value = rarity
    }

    /**
     * Parse query text to handle compound queries. Highly robust and scores results 
     * to sort the best matches (such as when selected from search history) at index 0.
     */
    fun performTextSearch(rawQuery: String) {
        if (rawQuery.isBlank()) return

        viewModelScope.launch {
            _searchUiState.value = MainSearchUiState.Loading
            _selectedCard.value = null
            
            // Add entry to search history
            repository.addHistory(
                SearchHistoryEntry(
                    query = rawQuery,
                    isImageSearch = false
                )
            )

            try {
                var queryParam = buildSearchQuery(rawQuery, _selectedRarity.value)
                var response = PokemonTcgClient.apiService.searchCards(queryParam)
                
                // Fallback 1: If rarity was set, try without rarity
                if (response.data.isEmpty() && !_selectedRarity.value.isNullOrBlank()) {
                    queryParam = buildSearchQuery(rawQuery, null)
                    response = PokemonTcgClient.apiService.searchCards(queryParam)
                }
                
                // Fallback 2: If no matches with number-parsed, search purely by name terms
                if (response.data.isEmpty()) {
                    val terms = rawQuery.trim().split(Regex("\\s+"))
                    val nameOnly = terms.filter { !it.matches(Regex("(?i)^(\\d+(/\\d+)?|[a-zA-Z]+\\d+|\\d+[a-zA-Z]+)$")) }.joinToString(" ")
                    val fallbackQuery = if (nameOnly.isNotBlank()) "name:\"*$nameOnly*\"" else "name:\"*${rawQuery.trim()}*\""
                    response = PokemonTcgClient.apiService.searchCards(fallbackQuery)
                }

                if (response.data.isEmpty()) {
                    _searchUiState.value = MainSearchUiState.Error("Nie znaleziono żadnych kart Pokémon o podanych kryteriach. Spróbuj zmienić zapytanie.")
                } else {
                    // Score and sort search results to place the most matching card on top (guarantees correct history card displaying)
                    val queryWords = rawQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val sortedList = response.data.sortedByDescending { card ->
                        var score = 0
                        for (word in queryWords) {
                            val cleanWord = word.trim()
                            if (cleanWord.isEmpty()) continue
                            
                            // Check card name
                            if (card.name.lowercase().contains(cleanWord)) {
                                score += 100
                                if (card.name.lowercase() == cleanWord) score += 50
                            }
                            // Check card number
                            val cleanWordNum = cleanWord.substringBefore("/")
                            if (card.number.lowercase().contains(cleanWordNum)) {
                                score += 200
                                if (card.number.lowercase() == cleanWordNum) score += 100
                            }
                            // Check set name
                            if (card.set.name.lowercase().contains(cleanWord)) {
                                score += 50
                            }
                            // Check set series
                            if (card.set.series.lowercase().contains(cleanWord)) {
                                score += 20
                            }
                        }
                        score
                    }

                    _searchUiState.value = MainSearchUiState.Success(sortedList)
                    // Auto-select the top-scored card
                    _selectedCard.value = sortedList.first()
                }
            } catch (e: Exception) {
                _searchUiState.value = MainSearchUiState.Error(handleApiException(e))
            }
        }
    }

    /**
     * Performs standard search after OCR extraction
     */
    fun performOcrSearch(name: String, number: String?, setKeyword: String?) {
        viewModelScope.launch {
            _searchUiState.value = MainSearchUiState.Loading
            
            val queryText = buildString {
                append(name)
                if (!number.isNullOrBlank()) append(" ").append(number)
                if (!setKeyword.isNullOrBlank()) append(" ").append(setKeyword)
            }

            repository.addHistory(
                SearchHistoryEntry(
                    query = queryText,
                    isImageSearch = true
                )
            )

            try {
                // Build robust structured card query
                val queryParam = buildString {
                    append("name:\"*$name*\"")
                    if (!number.isNullOrBlank()) {
                        val cleanNum = number.substringBefore("/")
                        append(" number:\"$cleanNum\"")
                    }
                }
                
                var response = PokemonTcgClient.apiService.searchCards(queryParam)
                
                // Fallback: If no cards found with numeric filters, search purely by name instead
                if (response.data.isEmpty()) {
                    response = PokemonTcgClient.apiService.searchCards("name:\"*$name*\"")
                }

                if (response.data.isEmpty()) {
                    _searchUiState.value = MainSearchUiState.Error("Pokemon TCG API nie zwróciło kart dla zidentyfikowanej nazwy: '$name'.")
                } else {
                    // Score and sort response list to place the exact match at index 0
                    val sortedList = response.data.sortedByDescending { card ->
                        var score = 0
                        // 1. Name match
                        if (card.name.equals(name, ignoreCase = true)) {
                            score += 1000
                        } else if (card.name.contains(name, ignoreCase = true)) {
                            score += 500
                        }
                        
                        // 2. Number match
                        if (!number.isNullOrBlank()) {
                            val cleanScanned = number.replace(Regex("[^0-9a-zA-Z]"), "").lowercase().trim()
                            val cleanCardNum = card.number.replace(Regex("[^0-9a-zA-Z]"), "").lowercase().trim()
                            if (cleanCardNum == cleanScanned) {
                                score += 2000
                            } else if (cleanCardNum.contains(cleanScanned) || cleanScanned.contains(cleanCardNum)) {
                                score += 800
                            }
                        }
                        
                        // 3. Set Name / Keyword Match
                        if (!setKeyword.isNullOrBlank()) {
                            val cleanSetKw = setKeyword.lowercase().trim()
                            val cleanSetName = card.set.name.lowercase().trim()
                            val cleanSetId = card.set.id.lowercase().trim()
                            if (cleanSetName == cleanSetKw || cleanSetId == cleanSetKw) {
                                score += 1500
                            } else if (cleanSetName.contains(cleanSetKw) || cleanSetId.contains(cleanSetKw)) {
                                score += 1000
                            } else if (card.set.series.lowercase().contains(cleanSetKw)) {
                                score += 400
                            }
                        }
                        score
                    }
                    
                    _searchUiState.value = MainSearchUiState.Success(sortedList)
                    _selectedCard.value = sortedList.first()
                }
            } catch (e: Exception) {
                _searchUiState.value = MainSearchUiState.Error(handleApiException(e))
            }
        }
    }

    private fun handleApiException(e: Exception): String {
        return if (e is retrofit2.HttpException) {
            when (e.code()) {
                500 -> "Błąd serwera (HTTP 500): Wewnętrzny błąd serwera Pokémon TCG. Przeprowadzono automatyczne próbkowania, lecz serwer jest niedostępny lub odrzucił zapytanie."
                502, 503, 504 -> "Błąd serwera (HTTP ${e.code()}): Usługa zewnętrzna jest przeciążona lub nieodpowiada. Spróbuj ponownie za chwilę."
                429 -> "Limit zapytań przekroczony (HTTP 429). Odczekaj chwilę przed kolejnym wyszukiwaniem."
                else -> "Błąd sieciowy HTTP ${e.code()}: ${e.message()}"
            }
        } else {
            "Błąd pobierania danych: ${e.localizedMessage ?: "Nieznany błąd połączenia."}"
        }
    }

    /**
     * Conduct multi-modal card identification via Gemini
     */
    fun analyzeImageAndSearch(bitmap: Bitmap) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _searchUiState.value = MainSearchUiState.Error("Klucz Gemini API nie został ustawiony w portfelu sekretów (Secrets Panel w AI Studio). Proszę go skonfigurować, aby włączyć rozpoznawanie obrazu.")
            return
        }

        viewModelScope.launch {
            _isAnalyzingImage.value = true
            _analysingResultText.value = "Trwa analiza zdjęcia karty przez Gemini..."
            _searchUiState.value = MainSearchUiState.Loading
            
            val ocrResult = GeminiClient.identifyPokemonCard(bitmap, apiKey)
            _isAnalyzingImage.value = false

            if (ocrResult.error != null) {
                _searchUiState.value = MainSearchUiState.Error("Karta nie rozpoznana: ${ocrResult.error}")
                _analysingResultText.value = null
                return@launch
            }

            val cardName = ocrResult.name
            if (cardName.isNullOrBlank()) {
                _searchUiState.value = MainSearchUiState.Error("Nie udało się odczytać nazwy karty ze zdjęcia.")
                _analysingResultText.value = null
                return@launch
            }

            _analysingResultText.value = "Zidentyfikowano: $cardName " + 
                (if (!ocrResult.number.isNullOrBlank()) "#${ocrResult.number} " else "") +
                (if (!ocrResult.set.isNullOrBlank()) "[Set: ${ocrResult.set}]" else "")

            performOcrSearch(cardName, ocrResult.number, ocrResult.set)
        }
    }

    fun selectCard(card: TcgCard) {
        _selectedCard.value = card
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // Favorite Management
    fun isFavorite(cardId: String): Boolean {
        return favorites.value.any { it.id == cardId }
    }

    fun toggleFavorite(card: TcgCard) {
        viewModelScope.launch {
            val exists = favorites.value.any { it.id == card.id }
            if (exists) {
                repository.deleteFavorite(card.id)
            } else {
                // Extract price averages for saving
                val tcgLow = getTcgPlayerPrice(card, "low")
                val tcgMid = getTcgPlayerPrice(card, "mid")
                val tcgHigh = getTcgPlayerPrice(card, "high")

                val cmPrices = card.cardmarket?.prices
                val cmLow = cmPrices?.lowPrice
                val cmAvg = cmPrices?.averageSellPrice
                val cmTrend = cmPrices?.trendPrice

                repository.saveFavorite(
                    FavoritePokemonCard(
                        id = card.id,
                        name = card.name,
                        imageUrl = card.images.large,
                        setName = card.set.name,
                        number = card.number,
                        rarity = card.rarity ?: "Standardowa",
                        types = card.types?.joinToString(", ") ?: "Brak",
                        tcgLow = tcgLow,
                        tcgMid = tcgMid,
                        tcgHigh = tcgHigh,
                        cmLow = cmLow,
                        cmAvg = cmAvg,
                        cmTrend = cmTrend
                    )
                )
            }
        }
    }

    fun toggleFavoriteModel(favCard: FavoritePokemonCard) {
        viewModelScope.launch {
            repository.deleteFavorite(favCard.id)
        }
    }

    // UTILITIES FOR PRICING EXTRACTS
    fun getTcgPlayerPrice(card: TcgCard, type: String): Double? {
        val pricesGroup = card.tcgplayer?.prices ?: return null
        // Inspect normal, holofoil, reverseHolofoil sequentially to find the first populated double
        val candidates = listOfNotNull(
            pricesGroup.normal,
            pricesGroup.holofoil,
            pricesGroup.reverseHolofoil,
            pricesGroup.unlimitedHolofoil,
            pricesGroup.firstEditionHolofoil
        )
        if (candidates.isEmpty()) return null

        return when (type) {
            "low" -> candidates.mapNotNull { it.low }.firstOrNull()
            "mid" -> candidates.mapNotNull { it.market ?: it.mid }.firstOrNull()
            "high" -> candidates.mapNotNull { it.high }.firstOrNull()
            else -> null
        }
    }

    // HELPERS FOR LINK GENERATION
    private fun padNumberSegment(segment: String): String {
        val num = segment.toIntOrNull()
        return if (num != null && segment.length < 3) {
            num.toString().padStart(3, '0')
        } else {
            segment
        }
    }

    private fun sanitizeSearchQueryText(text: String): String {
        return text.replace('É', 'E')
            .replace('é', 'e')
            .replace('’', '\'')
            .replace('\'', ' ')
            .replace(Regex("[♂♀★☆]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildExternalSearchPhrase(card: TcgCard): String {
        val setName = sanitizeSearchQueryText(card.set.name)
        val rawNumber = card.number.trim()
        val printedTotal = card.set.printedTotal

        val formattedNumber = if (rawNumber.contains("/")) {
            rawNumber.split("/").joinToString("/") { padNumberSegment(it.trim()) }
        } else if (printedTotal != null) {
            "${padNumberSegment(rawNumber)}/${padNumberSegment(printedTotal.toString())}"
        } else {
            padNumberSegment(rawNumber)
        }

        val name = sanitizeSearchQueryText(card.name)
        return if (setName.isNotBlank()) {
            "$setName $formattedNumber $name"
        } else {
            "$formattedNumber $name"
        }
    }

    /**
     * Fraza dla Allegro/OLX: bez oficjalnej (angielskiej) nazwy setu, bo w realnych
     * ogłoszeniach na tych portalach sprzedający prawie nigdy jej nie wpisują
     * (np. piszą "151" zamiast "Scarlet & Violet 151") — dodatkowe słowa tylko
     * zaśmiecają zapytanie i pogarszają trafność wyników. Zera wiodące w numerze
     * karty (np. "025/165") są zachowane zgodnie z ustaleniami.
     */
    fun buildMarketplaceSearchPhrase(card: TcgCard): String {
        val rawNumber = card.number.trim()
        val printedTotal = card.set.printedTotal

        val formattedNumber = if (rawNumber.contains("/")) {
            rawNumber.split("/").joinToString("/") { padNumberSegment(it.trim()) }
        } else if (printedTotal != null) {
            "${padNumberSegment(rawNumber)}/${padNumberSegment(printedTotal.toString())}"
        } else {
            padNumberSegment(rawNumber)
        }

        val name = sanitizeSearchQueryText(card.name)
        return "$formattedNumber $name"
    }

    fun getAllegroLink(card: TcgCard): String {
        val query = buildMarketplaceSearchPhrase(card)
        return "https://allegro.pl/listing?string=${urlEncode(query)}"
    }

    fun getPokekartyLink(card: TcgCard): String {
        // pokekarty.pl to sklep na WordPress/WooCommerce, a nie PrestaShop —
        // prawidłowy adres wyszukiwania to parametr "s" (+ post_type=product),
        // a nie "/szukaj?controller=search" (to nieistniejąca na tej stronie trasa).
        val query = buildExternalSearchPhrase(card)
        return "https://www.pokekarty.pl/?s=${urlEncode(query)}&post_type=product"
    }

    fun getPriceChartingLink(card: TcgCard): String {
        val setName = sanitizeSearchQueryText(card.set.name)
        val rawNumber = card.number.trim()
        val printedTotal = card.set.printedTotal

        val formattedNumber = if (rawNumber.contains("/")) {
            rawNumber.split("/").joinToString("/") { padNumberSegment(it.trim()) }
        } else if (printedTotal != null) {
            "${padNumberSegment(rawNumber)}/${padNumberSegment(printedTotal.toString())}"
        } else {
            padNumberSegment(rawNumber)
        }

        val name = sanitizeSearchQueryText(card.name)
        val query = if (setName.isNotBlank()) {
            "pokemon $name $setName $formattedNumber"
        } else {
            "pokemon $name $formattedNumber"
        }
        return "https://www.pricecharting.com/search-products?q=${urlEncode(query)}&type=prices"
    }

    fun getOlxLink(card: TcgCard): String {
        val loc = _olxLocation.value
        val query = buildMarketplaceSearchPhrase(card)
        val encodedQuery = urlEncode(query)
        
        return if (loc.isNotBlank() && loc != "Brak filtru" && loc != "Kraj (brak)") {
            // OLX nie rozpoznaje przedrostka "województwo"/"woj." w adresie — segment
            // ścieżki to sama nazwa regionu (np. "swietokrzyskie", nie "wojewodztwo-swietokrzyskie"),
            // inaczej link prowadzi na nieistniejącą stronę (404).
            val normalizedLoc = loc.replace(Regex("(?i)^woj(ew[oó]dztwo|\\.)\\s+"), "")
            val locSlug = slugify(normalizedLoc)
            "https://www.olx.pl/${locSlug}/q-${encodedQuery}/"
        } else {
            "https://www.olx.pl/oferty/q-${encodedQuery}/"
        }
    }

    // PRIVATES
    private fun buildSearchQuery(rawQuery: String, rarity: String? = null): String {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isBlank()) return ""

        val terms = cleanQuery.split(Regex("\\s+"))
        
        // Find a term that represents the card number
        // e.g. "143/198", "TG12", "SV1", "25", "025/025"
        val numberTerm = terms.firstOrNull { term ->
            term.matches(Regex("(?i)^(\\d+(/\\d+)?|[a-zA-Z]+\\d+|\\d+[a-zA-Z]+)$"))
        }

        val baseQuery = if (numberTerm != null) {
            val nameTerms = terms.filter { it != numberTerm }
            val rawNumVal = numberTerm.substringBefore("/")
            val cleanNumVal = if (rawNumVal.all { it.isDigit() }) {
                rawNumVal.trimStart('0').ifEmpty { "0" }
            } else {
                rawNumVal
            }
            
            if (nameTerms.isNotEmpty()) {
                val namePart = nameTerms.joinToString(" ")
                "name:\"*$namePart*\" number:\"$cleanNumVal\""
            } else {
                "number:\"$cleanNumVal\""
            }
        } else {
            "name:\"*$cleanQuery*\""
        }

        return if (!rarity.isNullOrBlank()) {
            "$baseQuery rarity:\"$rarity\""
        } else {
            baseQuery
        }
    }

    private fun urlEncode(text: String): String {
        return try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }
    }

    fun slugify(text: String): String {
        return text.lowercase()
            .replace('ą', 'a')
            .replace('ć', 'c')
            .replace('ę', 'e')
            .replace('ł', 'l')
            .replace('ń', 'n')
            .replace('ó', 'o')
            .replace('ś', 's')
            .replace('ź', 'z')
            .replace('ż', 'z')
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim()
    }
}

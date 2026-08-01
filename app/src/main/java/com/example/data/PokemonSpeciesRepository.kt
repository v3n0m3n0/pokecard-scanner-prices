package com.example.data

import com.example.api.PokeApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object PokemonSpeciesRepository {

    private val seedSpeciesNames = listOf(
        "Bulbasaur", "Ivysaur", "Venusaur", "Charmander", "Charmeleon", "Charizard",
        "Squirtle", "Wartortle", "Blastoise", "Caterpie", "Metapod", "Butterfree",
        "Weedle", "Kakuna", "Beedrill", "Pidgey", "Pidgeotto", "Pidgeot",
        "Rattata", "Raticate", "Spearow", "Fearow", "Ekans", "Arbok",
        "Pikachu", "Raichu", "Sandshrew", "Sandslash", "Nidoran", "Nidorina",
        "Nidoqueen", "Nidorino", "Nidoking", "Clefairy", "Clefable", "Vulpix",
        "Ninetales", "Jigglypuff", "Wigglytuff", "Zubat", "Golbat", "Oddish",
        "Gloom", "Vileplume", "Paras", "Parasect", "Venonat", "Venomoth",
        "Diglett", "Dugtrio", "Meowth", "Persian", "Psyduck", "Golduck",
        "Mankey", "Primeape", "Growlithe", "Arcanine", "Poliwag", "Poliwhirl",
        "Poliwrath", "Abra", "Kadabra", "Alakazam", "Machop", "Machoke",
        "Machamp", "Bellsprout", "Weepinbell", "Victreebel", "Tentacool", "Tentacruel",
        "Geodude", "Graveler", "Golem", "Ponyta", "Rapidash", "Slowpoke",
        "Slowbro", "Magnemite", "Magneton", "Farfetch'd", "Doduo", "Dodrio",
        "Seel", "Dewgong", "Grimer", "Muk", "Shellder", "Cloyster",
        "Gastly", "Haunter", "Gengar", "Onix", "Drowzee", "Hypno",
        "Krabby", "Kingler", "Voltorb", "Electrode", "Exeggcute", "Exeggutor",
        "Cubone", "Marowak", "Hitmonlee", "Hitmonchan", "Lickitung", "Koffing",
        "Weezing", "Rhyhorn", "Rhydon", "Chansey", "Tangela", "Kangaskhan",
        "Horsea", "Seadra", "Goldeen", "Seaking", "Staryu", "Starmie",
        "Mr. Mime", "Scyther", "Jynx", "Electabuzz", "Magmar", "Pinsir",
        "Tauros", "Magikarp", "Gyarados", "Lapras", "Ditto", "Eevee",
        "Vaporeon", "Jolteon", "Flareon", "Porygon", "Omanyte", "Omastar",
        "Kabuto", "Kabutops", "Aerodactyl", "Snorlax", "Articuno", "Zapdos",
        "Moltres", "Dratini", "Dragonair", "Dragonite", "Mewtwo", "Mew",
        "Chikorita", "Cyndaquil", "Totodile", "Togepi", "Ampharos", "Marill",
        "Sudowoodo", "Espeon", "Umbreon", "Slowking", "Unown", "Wobbuffet",
        "Steelix", "Scizor", "Heracross", "Sneasel", "Teddiursa", "Corsola",
        "Skarmory", "Houndoom", "Kingdra", "Phanpy", "Donphan", "Porygon2",
        "Smeargle", "Tyrogue", "Hitmontop", "Smoochum", "Elekid", "Magby",
        "Blissey", "Raikou", "Entei", "Suicune", "Larvitar", "Pupitar",
        "Tyranitar", "Lugia", "Ho-Oh", "Celebi", "Treecko", "Torchic",
        "Mudkip", "Ralts", "Gardevoir", "Breloom", "Slakoth", "Nincada",
        "Shedinja", "Exploud", "Makuhita", "Mawile", "Aggron", "Medicham",
        "Flygon", "Altaria", "Zangoose", "Seviper", "Milotic", "Castform",
        "Kecleon", "Banette", "Duskull", "Tropius", "Chimecho", "Absol",
        "Wynaut", "Spheal", "Walrein", "Clamperl", "Relicanth", "Luvdisc",
        "Bagon", "Salamence", "Beldum", "Metagross", "Regirock", "Regice",
        "Registeel", "Latias", "Latios", "Kyogre", "Groudon", "Rayquaza",
        "Jirachi", "Deoxys", "Turtwig", "Chimchar", "Piplup", "Staraptor",
        "Luxray", "Roserade", "Cranidos", "Shieldon", "Vespiquen", "Pachirisu",
        "Buizel", "Gastrodon", "Ambipom", "Drifblim", "Buneary", "Lopunny",
        "Mismagius", "Honchkrow", "Glameow", "Stunky", "Bronzong", "Bonsly",
        "Mime Jr.", "Happiny", "Chatot", "Spiritomb", "Gible", "Gabite",
        "Garchomp", "Munchlax", "Riolu", "Lucario", "Hippopotas", "Drapion",
        "Toxicroak", "Carnivine", "Lumineon", "Mantyke", "Abomasnow", "Weavile",
        "Magnezone", "Lickilicky", "Rhyperior", "Tangrowth", "Electivire", "Magmortar",
        "Togekiss", "Yanmega", "Leafeon", "Glaceon", "Gliscor", "Mamoswine",
        "Porygon-Z", "Gallade", "Probopass", "Dusknoir", "Froslass", "Rotom",
        "Uxie", "Mesprit", "Azelf", "Dialga", "Palkia", "Heatran",
        "Regigigas", "Giratina", "Cresselia", "Phione", "Manaphy", "Darkrai",
        "Shaymin", "Arceus", "Victini", "Snivy", "Tepig", "Oshawott",
        "Zorua", "Zoroark", "Axew", "Haxorus", "Reshiram", "Zekrom",
        "Kyurem", "Keldeo", "Meloetta", "Genesect", "Chespin", "Fennikin",
        "Froakie", "Greninja", "Sylveon", "Xerneas", "Yveltal", "Zygarde",
        "Diancie", "Hoopa", "Volcanion", "Rowlet", "Litten", "Popplio",
        "Lycanroc", "Bewear", "Mimikyu", "Tapu Koko", "Tapu Lele", "Tapu Bulu",
        "Tapu Fini", "Solgaleo", "Lunala", "Nihilego", "Buzzwole", "Pheromosa",
        "Xurkitree", "Celesteela", "Kartana", "Guzzlord", "Necrozma", "Magearna",
        "Marshadow", "Poipole", "Naganadel", "Stakataka", "Blacephalon", "Zeraora",
        "Meltan", "Melmetal", "Grookey", "Scorbunny", "Sobble", "Corviknight",
        "Toxtricity", "Dragapult", "Zacian", "Zamazenta", "Eternatus", "Kubfu",
        "Urshifu", "Zarude", "Regieleki", "Regidrago", "Glastrier", "Spectrier",
        "Calyrex", "Sprigatito", "Fuecoco", "Quaxly", "Tinkaton", "Armarouge",
        "Ceruledge", "Koraidon", "Miraidon", "Terapagos", "Pecharunt"
    )

    private val _speciesList = MutableStateFlow<List<String>>(seedSpeciesNames.distinct())
    val speciesList: StateFlow<List<String>> = _speciesList.asStateFlow()

    suspend fun loadSpeciesFromApi() {
        withContext(Dispatchers.IO) {
            try {
                val response = PokeApiClient.service.getPokemonSpecies()
                val fetchedNames = response.results.mapNotNull { item ->
                    formatSpeciesName(item.name)
                }

                if (fetchedNames.isNotEmpty()) {
                    val combined = (seedSpeciesNames + fetchedNames).distinct()
                    _speciesList.value = combined
                }
            } catch (e: Exception) {
                // Ignore API failure, keep seed list intact
            }
        }
    }

    private fun formatSpeciesName(rawName: String): String? {
        if (rawName.isBlank()) return null

        val formatted = when (rawName.lowercase()) {
            "mr-mime" -> "Mr. Mime"
            "mime-jr" -> "Mime Jr."
            "mr-rime" -> "Mr. Rime"
            "type-null" -> "Type: Null"
            "nidoran-m" -> "Nidoran♂"
            "nidoran-f" -> "Nidoran♀"
            "ho-oh" -> "Ho-Oh"
            "porygon-z" -> "Porygon-Z"
            "jangmo-o" -> "Jangmo-o"
            "hakamo-o" -> "Hakamo-o"
            "kommo-o" -> "Kommo-o"
            "tapu-koko" -> "Tapu Koko"
            "tapu-lele" -> "Tapu Lele"
            "tapu-bulu" -> "Tapu Bulu"
            "tapu-fini" -> "Tapu Fini"
            "wo-chien" -> "Wo-Chien"
            "chien-pao" -> "Chien-Pao"
            "ting-lu" -> "Ting-Lu"
            "chi-yu" -> "Chi-Yu"
            else -> {
                // Split by hyphens, capitalize words
                rawName.split("-").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }

        // Clean out any extraneous card numbers/series
        return formatted.trim().ifEmpty { null }
    }

    fun getSuggestions(query: String, limit: Int = 6): List<String> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        val normalizedQuery = trimmedQuery.lowercase().replace(" ", "")
        val currentList = _speciesList.value

        // Matches that START with the normalized query
        val startsWithMatches = currentList.filter { species ->
            val normSpecies = species.lowercase().replace(" ", "")
            normSpecies.startsWith(normalizedQuery)
        }

        // Matches that CONTAIN the normalized query anywhere
        val containsMatches = currentList.filter { species ->
            val normSpecies = species.lowercase().replace(" ", "")
            !normSpecies.startsWith(normalizedQuery) && normSpecies.contains(normalizedQuery)
        }

        return (startsWithMatches + containsMatches).distinct().take(limit)
    }
}

package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PokemonTcgResponse(
    @field:Json(name = "data") val data: List<TcgCard>
)

@JsonClass(generateAdapter = true)
data class TcgCard(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "types") val types: List<String>?,
    @field:Json(name = "rarity") val rarity: String?,
    @field:Json(name = "number") val number: String,
    @field:Json(name = "images") val images: CardImages,
    @field:Json(name = "set") val set: CardSet,
    @field:Json(name = "tcgplayer") val tcgplayer: TcgPlayerInfo?,
    @field:Json(name = "cardmarket") val cardmarket: CardmarketInfo?
)

@JsonClass(generateAdapter = true)
data class CardImages(
    @field:Json(name = "small") val small: String,
    @field:Json(name = "large") val large: String
)

@JsonClass(generateAdapter = true)
data class CardSet(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "series") val series: String,
    @field:Json(name = "printedTotal") val printedTotal: Int?
)

@JsonClass(generateAdapter = true)
data class TcgPlayerInfo(
    @field:Json(name = "url") val url: String?,
    @field:Json(name = "updatedAt") val updatedAt: String?,
    @field:Json(name = "prices") val prices: TcgPlayerPricesGroup?
)

@JsonClass(generateAdapter = true)
data class TcgPlayerPricesGroup(
    @field:Json(name = "normal") val normal: TcgPlayerPrice?,
    @field:Json(name = "holofoil") val holofoil: TcgPlayerPrice?,
    @field:Json(name = "reverseHolofoil") val reverseHolofoil: TcgPlayerPrice?,
    @field:Json(name = "unlimitedHolofoil") val unlimitedHolofoil: TcgPlayerPrice?,
    @field:Json(name = "1stEditionHolofoil") val firstEditionHolofoil: TcgPlayerPrice?
)

@JsonClass(generateAdapter = true)
data class TcgPlayerPrice(
    @field:Json(name = "low") val low: Double?,
    @field:Json(name = "mid") val mid: Double?,
    @field:Json(name = "high") val high: Double?,
    @field:Json(name = "market") val market: Double?,
    @field:Json(name = "directLow") val directLow: Double?
)

@JsonClass(generateAdapter = true)
data class CardmarketInfo(
    @field:Json(name = "url") val url: String?,
    @field:Json(name = "updatedAt") val updatedAt: String?,
    @field:Json(name = "prices") val prices: CardmarketPrices?
)

@JsonClass(generateAdapter = true)
data class CardmarketPrices(
    @field:Json(name = "averageSellPrice") val averageSellPrice: Double?,
    @field:Json(name = "lowPrice") val lowPrice: Double?,
    @field:Json(name = "trendPrice") val trendPrice: Double?,
    @field:Json(name = "reverseHoloTrend") val reverseHoloTrend: Double?
)

interface PokemonTcgApiService {
    @GET("cards")
    @Headers("X-Api-Key: a49800a7-bc4f-40f4-8de6-0e137f68c347") // Standard API key that gives high allowance, or can be null
    suspend fun searchCards(
        @Query("q") query: String
    ): PokemonTcgResponse
}

@JsonClass(generateAdapter = true)
data class PokeApiSpeciesResponse(
    @field:Json(name = "results") val results: List<PokeApiSpeciesItem>
)

@JsonClass(generateAdapter = true)
data class PokeApiSpeciesItem(
    @field:Json(name = "name") val name: String
)

interface PokeApiService {
    @GET("pokemon-species?limit=1025")
    suspend fun getPokemonSpecies(): PokeApiSpeciesResponse
}

object PokeApiClient {
    private const val BASE_URL = "https://pokeapi.co/api/v2/"

    val service: PokeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PokeApiService::class.java)
    }
}

object PokemonTcgClient {
    private const val BASE_URL = "https://api.pokemontcg.io/v2/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            val maxLimit = 3
            while (!response.isSuccessful && response.code >= 500 && tryCount < maxLimit) {
                tryCount++
                response.close()
                try {
                    Thread.sleep((tryCount * 500).toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                response = chain.proceed(request)
            }
            response
        }
        .build()

    val apiService: PokemonTcgApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(PokemonTcgApiService::class.java)
    }
}

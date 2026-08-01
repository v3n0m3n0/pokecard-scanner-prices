package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @field:Json(name = "contents") val contents: List<GeminiContent>,
    @field:Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @field:Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @field:Json(name = "text") val text: String? = null,
    @field:Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @field:Json(name = "mimeType") val mimeType: String,
    @field:Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @field:Json(name = "responseMimeType") val responseMimeType: String? = null,
    @field:Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @field:Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @field:Json(name = "content") val content: GeminiResponseContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponseContent(
    @field:Json(name = "parts") val parts: List<GeminiResponsePart>?
)

@JsonClass(generateAdapter = true)
data class GeminiResponsePart(
    @field:Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class IdentifiedCardResult(
    @field:Json(name = "name") val name: String?,
    @field:Json(name = "number") val number: String?,
    @field:Json(name = "set") val set: String?,
    @field:Json(name = "error") val error: String?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val cardAdapter = moshi.adapter(IdentifiedCardResult::class.java)

    /**
     * Converts a Bitmap to safe Base64 JPEG
     */
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize image to 1200 maxDim to preserve high resolution details for small print OCR while staying within reasonable network payload bounds
        val maxDim = 1200
        val srcWidth = width
        val srcHeight = height
        val (dstWidth, dstHeight) = if (srcWidth > srcHeight) {
            val ratio = srcWidth.toFloat() / maxDim
            Pair(maxDim, (srcHeight / ratio).toInt())
        } else {
            val ratio = srcHeight.toFloat() / maxDim
            Pair((srcWidth / ratio).toInt(), maxDim)
        }
        val resized = Bitmap.createScaledBitmap(this, dstWidth, dstHeight, true)
        // High quality setting to prevent JPEG compression artifacts around fine text
        resized.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Calls Gemini to perform OCR and spot Pokemon card details
     */
    suspend fun identifyPokemonCard(bitmap: Bitmap, apiKey: String): IdentifiedCardResult {
        val base64Data = try {
            bitmap.toBase64()
        } catch (e: Exception) {
            return IdentifiedCardResult(null, null, null, "Failed to encode image: ${e.localizedMessage}")
        }

        val prompt = """
            Analyze the Pokémon TCG card shown in the picture with ultra precision. 
            Identify and OCR:
            1. The EXACT Card Name: This is usually located at the top border (e.g., 'Charizard ex', 'Pikachu', 'Mewtwo VMAX', 'Eternatus V'). Keep it in English and preserve special designations like 'ex', 'V', 'VMAX', 'GX', or character names.
            2. The Card Number & Set Sub-Number: Look in the bottom-left or bottom-right corner fields. It is usually structured as a fraction or a code (e.g., '143/198', 'TG12/TG30', 'GG02/GG70', '025/025', or simply a single number like '58' or '6'). Ensure you don't miss alphabetic leading characters like 'RC', 'GG', 'TG', 'SV'. 
            3. The Official Set Name or Set abbreviation code: Check the set icon/logo on the card or identify the card's expansion from your knowledge base (e.g., 'Scarlet & Violet', 'Crown Zenith', 'Obsidian Flames', 'Evolving Skies', 'Unified Minds').
            
            Return ONLY a valid JSON object matching this schema:
            {
              "name": "Exact Name of Pokemon Card", 
              "number": "Full set card number (e.g. '143/198' or 'TG12')", 
              "set": "Identified Expansion/Set Name"
            }
            
            Do NOT include markdown formatting wrappers like ```json.
            If the image is not a Pokémon TCG card, return:
            {
              "error": "Reason why it is not recognized as a Pokemon card"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2
            )
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return IdentifiedCardResult(null, null, null, "No output returned from Gemini.")
            
            var cleanJson = jsonText.trim()
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }
            
            cardAdapter.fromJson(cleanJson) ?: IdentifiedCardResult(null, null, null, "Failed to parse result.")
        } catch (e: Exception) {
            IdentifiedCardResult(null, null, null, "Communication Error: ${e.localizedMessage}")
        }
    }
}

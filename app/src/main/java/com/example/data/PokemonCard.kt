package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_pokemon_cards")
data class FavoritePokemonCard(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String,
    val setName: String,
    val number: String,
    val rarity: String,
    val types: String,
    val tcgLow: Double?,
    val tcgMid: Double?,
    val tcgHigh: Double?,
    val cmLow: Double?,
    val cmAvg: Double?,
    val cmTrend: Double?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val isImageSearch: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

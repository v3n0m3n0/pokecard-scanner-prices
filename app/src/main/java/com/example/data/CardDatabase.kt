package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteCardDao {
    @Query("SELECT * FROM favorite_pokemon_cards ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoritePokemonCard>>

    @Query("SELECT * FROM favorite_pokemon_cards WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: String): FavoritePokemonCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(card: FavoritePokemonCard)

    @Query("DELETE FROM favorite_pokemon_cards WHERE id = :id")
    suspend fun deleteFavoriteById(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getHistory(): Flow<List<SearchHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SearchHistoryEntry)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Database(entities = [FavoritePokemonCard::class, SearchHistoryEntry::class], version = 1, exportSchema = false)
abstract class PokemonCardDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteCardDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: PokemonCardDatabase? = null

        fun getDatabase(context: Context): PokemonCardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PokemonCardDatabase::class.java,
                    "pokemon_card_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class PokemonCardRepository(private val db: PokemonCardDatabase) {
    val allFavorites: Flow<List<FavoritePokemonCard>> = db.favoriteDao().getAllFavorites()
    val searchHistory: Flow<List<SearchHistoryEntry>> = db.historyDao().getHistory()

    suspend fun findFavoriteById(id: String): FavoritePokemonCard? {
        return db.favoriteDao().getFavoriteById(id)
    }

    suspend fun saveFavorite(card: FavoritePokemonCard) {
        db.favoriteDao().insertFavorite(card)
    }

    suspend fun deleteFavorite(id: String) {
        db.favoriteDao().deleteFavoriteById(id)
    }

    suspend fun addHistory(entry: SearchHistoryEntry) {
        db.historyDao().insertHistory(entry)
    }

    suspend fun clearAllHistory() {
        db.historyDao().clearHistory()
    }
}

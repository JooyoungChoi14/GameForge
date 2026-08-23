package com.gameforge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GameEntry::class,
        GameHtmlVersion::class,
        GameStateSnapshot::class,
        ChatMessage::class,
        LlmProvider::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GameForgeDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun htmlVersionDao(): HtmlVersionDao
    abstract fun stateSnapshotDao(): StateSnapshotDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun llmProviderDao(): LlmProviderDao

    companion object {
        @Volatile
        private var INSTANCE: GameForgeDatabase? = null

        fun getInstance(context: Context): GameForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameForgeDatabase::class.java,
                    "gameforge-db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
package com.vexmusic.player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        LyricsEntity::class,
        TranslationEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VexDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun translationDao(): TranslationDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var instance: VexDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN matchedProvider TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN matchedProviderId TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN originalTitle TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN originalArtist TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN originalAlbum TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN versionInfo TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN matchedAt INTEGER")
            }
        }

        fun get(context: Context): VexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VexDatabase::class.java,
                "vex_music.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

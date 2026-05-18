package com.example.notepad.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class, NoteTombstoneEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class NotepadDatabase : RoomDatabase() {
    abstract fun notepadDao(): NotepadDao

    companion object {
        @Volatile
        private var instance: NotepadDatabase? = null

        fun getInstance(context: Context): NotepadDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotepadDatabase::class.java,
                    "local_notepad.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAt INTEGER")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE folders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE folders SET syncId = '$DEFAULT_FOLDER_SYNC_ID' WHERE id = $DEFAULT_FOLDER_ID")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_syncId ON folders(syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_syncId ON notes(syncId)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note_tombstones (" +
                        "syncId TEXT NOT NULL PRIMARY KEY, " +
                        "deletedAt INTEGER NOT NULL" +
                        ")",
                )
            }
        }
    }
}

package com.example.notepad.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class],
    version = 1,
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
                    .build()
                    .also { instance = it }
            }
        }
    }
}

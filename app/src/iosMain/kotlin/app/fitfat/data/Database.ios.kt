package app.fitfat.data

import androidx.room.Room
import androidx.room.RoomDatabase
import app.fitfat.platform.documentDirectory

/** `Documents/fitfat.db` — попадает в резервную копию iCloud вместе с приложением. */
actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = documentDirectory() + "/fitfat.db")

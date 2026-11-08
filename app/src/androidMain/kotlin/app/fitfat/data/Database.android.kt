package app.fitfat.data

import androidx.room.Room
import androidx.room.RoomDatabase
import app.fitfat.platform.AndroidApp

/** Тот же файл `fitfat.db` в каталоге баз приложения, что и у прежних версий. */
actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = AndroidApp.context
    return Room.databaseBuilder<AppDatabase>(context = ctx, name = ctx.getDatabasePath("fitfat.db").absolutePath)
}

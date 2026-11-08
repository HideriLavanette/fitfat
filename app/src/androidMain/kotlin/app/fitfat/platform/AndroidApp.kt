package app.fitfat.platform

import android.content.Context

/** Контекст приложения для `actual`-реализаций. Задаётся в `FitfatApp.onCreate`. */
object AndroidApp {
    lateinit var context: Context
}

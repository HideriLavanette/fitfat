package app.fitfat.platform

import android.app.Activity
import android.content.Context

/** Контекст приложения для `actual`-реализаций. Задаётся в `FitfatApp.onCreate`. */
object AndroidApp {
    lateinit var context: Context
    /** Открытая активити — системным окнам (вход Google) нужна именно она. Ставит `MainActivity`. */
    var activity: Activity? = null
}

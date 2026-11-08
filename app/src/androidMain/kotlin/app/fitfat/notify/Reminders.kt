package app.fitfat.notify

import app.fitfat.time.*
import app.fitfat.time.Fmt
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.fitfat.FitfatApp
import app.fitfat.MainActivity
import app.fitfat.R
import app.fitfat.data.AppSettings
import app.fitfat.data.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Напоминание о весе и недельный отчёт (макет Settings). Будильники неточные (`AlarmManager.setWindow`, окно 10 минут):
 * точное время не нужно, а разрешение на точные будильники — лишнее. После срабатывания ставится следующий,
 * после перезагрузки телефона и обновления приложения — заново ([BootReceiver]).
 */
object Reminders {
    const val WEIGHT = "weight"
    const val WEEK = "week"
    private const val CHANNEL = "reminders"

    /** Что открыть по нажатию на уведомление: [WEIGHT] — шторка веса, [WEEK] — «Неделя». Читает AppRoot. */
    val open = MutableStateFlow<String?>(null)

    fun schedule(context: Context) {
        plan(context, WEIGHT, AppSettings.remind.value)
        plan(context, WEEK, AppSettings.report.value)
    }

    private fun pending(context: Context, kind: String) = PendingIntent.getBroadcast(
        context, kind.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction("app.fitfat.ALARM.$kind").putExtra("kind", kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun plan(context: Context, kind: String, s: Schedule) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, kind)
        am.cancel(pi)
        val at = next(s, LocalDateTime.now()) ?: return
        val ms = at.toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60_000L, pi)
    }

    /** Ближайшее срабатывание после [now]: выбранный день недели (0 = Пн) и время; null — выключено или дни не выбраны. */
    fun next(s: Schedule, now: LocalDateTime): LocalDateTime? {
        if (!s.on || s.days.isEmpty()) return null
        for (i in 0..7) {
            val d = now.toLocalDate().plusDays(i.toLong())
            val t = d.atTime(s.minute / 60, s.minute % 60)
            if ((d.dayOfWeek.value - 1) in s.days && t.isAfter(now)) return t
        }
        return null
    }

    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context, kind: String, title: String, text: String) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Взвешивание и недельный отчёт" },
        )
        val tap = PendingIntent.getActivity(
            context, kind.hashCode(),
            Intent(context, MainActivity::class.java).putExtra("open", kind).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap).setAutoCancel(true).build()
        nm.notify(kind.hashCode(), n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FitfatApp
                when (kind) {
                    // Уже взвесились сегодня — не напоминаем
                    Reminders.WEIGHT -> if (app.database.weightDao().get(LocalDate.now().toEpochDay()) == null) {
                        Reminders.post(context, kind, "Время взвеситься", "Натощак, после туалета — так тренд веса точнее.")
                    }
                    Reminders.WEEK -> Reminders.post(context, kind, "Недельный отчёт готов", "Баланс калорий, вес и средние БЖУ за 7 дней.")
                }
                Reminders.schedule(context)
            } finally {
                done.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Reminders.schedule(context)
    }
}

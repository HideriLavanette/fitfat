package app.fitfat.notify

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
import app.fitfat.platform.AndroidApp
import app.fitfat.time.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Будильники неточные (`AlarmManager.setWindow`, окно 10 минут): точное время не нужно, а разрешение на точные
 * будильники — лишнее. После срабатывания ставится следующий, после перезагрузки телефона и обновления
 * приложения — заново ([BootReceiver]).
 */
actual object ReminderScheduler {
    private const val CHANNEL = "reminders"

    actual fun schedule() {
        val context = AndroidApp.context
        plan(context, Reminders.WEIGHT, AppSettings.remind.value)
        plan(context, Reminders.WEEK, AppSettings.report.value)
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
        val at = Reminders.next(s, LocalDateTime.now()) ?: return
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), 10 * 60_000L, pi)
    }

    actual fun canPost(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(AndroidApp.context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context, kind: String, title: String, text: String) {
        if (!canPost()) return
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
                        ReminderScheduler.post(context, kind, Reminders.WEIGHT_TITLE, Reminders.WEIGHT_TEXT)
                    }
                    Reminders.WEEK -> ReminderScheduler.post(context, kind, Reminders.WEEK_TITLE, Reminders.WEEK_TEXT)
                }
                ReminderScheduler.schedule()
            } finally {
                done.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ReminderScheduler.schedule()
    }
}

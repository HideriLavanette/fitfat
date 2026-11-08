package app.fitfat.notify

import app.fitfat.data.AppSettings
import app.fitfat.data.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Локальные уведомления: по одному повторяющемуся запросу на каждый выбранный день недели.
 * В момент срабатывания код не выполняется, поэтому напоминание о весе приходит и в день, когда уже взвесились.
 */
actual object ReminderScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private val main = CoroutineScope(Dispatchers.Main)
    private var granted = false
    private val delegate = Delegate()

    /** Один раз при старте: получать нажатия на уведомления и узнать, есть ли разрешение. */
    fun install() {
        center.delegate = delegate
        refresh()
    }

    private fun refresh() {
        center.getNotificationSettingsWithCompletionHandler { s ->
            val st = s?.authorizationStatus
            granted = st == UNAuthorizationStatusAuthorized || st == UNAuthorizationStatusProvisional
        }
    }

    /** Спросить разрешение; [onResult] — на главном потоке. */
    fun request(onResult: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { ok, _ ->
            granted = ok
            main.launch { onResult(ok); if (ok) schedule() }
        }
    }

    private val ids = listOf(Reminders.WEIGHT, Reminders.WEEK).flatMap { k -> (0..6).map { "${k}_$it" } }

    actual fun schedule() {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        plan(Reminders.WEIGHT, AppSettings.remind.value, Reminders.WEIGHT_TITLE, Reminders.WEIGHT_TEXT)
        plan(Reminders.WEEK, AppSettings.report.value, Reminders.WEEK_TITLE, Reminders.WEEK_TEXT)
    }

    private fun plan(kind: String, s: Schedule, title: String, text: String) {
        if (!s.on) return
        s.days.forEach { d ->
            // Наши дни 0 = Пн … 6 = Вс; у iOS 1 = Вс … 7 = Сб
            val comps = NSDateComponents().apply {
                weekday = ((d + 2) % 7 + if ((d + 2) % 7 == 0) 7 else 0).toLong()
                hour = (s.minute / 60).toLong()
                minute = (s.minute % 60).toLong()
            }
            val content = UNMutableNotificationContent().apply {
                setTitle(title); setBody(text)
                setUserInfo(mapOf("open" to kind))
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, repeats = true)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("${kind}_$d", content, trigger), withCompletionHandler = null)
        }
    }

    actual fun canPost(): Boolean = granted

    private class Delegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(center: UNUserNotificationCenter, didReceiveNotificationResponse: UNNotificationResponse, withCompletionHandler: () -> Unit) {
            val kind = didReceiveNotificationResponse.notification.request.content.userInfo["open"] as? String
            if (kind != null) Reminders.open.value = kind
            withCompletionHandler()
        }

        override fun userNotificationCenter(center: UNUserNotificationCenter, willPresentNotification: UNNotification, withCompletionHandler: (UNNotificationPresentationOptions) -> Unit) {
            withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
        }
    }
}

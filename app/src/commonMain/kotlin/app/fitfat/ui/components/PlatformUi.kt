package app.fitfat.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable

/** Внутри `Dialog`: выключить системное затемнение — скрим рисуем сами (Android); на iOS его и нет. */
@Composable
expect fun DialogNoDim()

/**
 * Разрешение на уведомления. Возвращает функцию «попросить, если нужно»: на Android 13+ — системный запрос,
 * на iOS — запрос авторизации `UNUserNotificationCenter`; где уже разрешено — ничего. [onDenied] — отказали.
 */
@Composable
expect fun rememberNotificationPermission(onDenied: () -> Unit = {}): () -> Unit

/**
 * Сохранить текст в файл по выбору пользователя (Android — диалог «Сохранить как», iOS — лист «Поделиться»).
 * Возвращает функцию: имя файла и содержимое (считается в фоне). [onDone] — получилось ли.
 */
@Composable
expect fun rememberTextFileSaver(mime: String, onDone: (Boolean) -> Unit): (name: String, content: suspend () -> String) -> Unit

/** Открыть текстовый файл (JSON): [onText] — содержимое, null — отмена или не прочитался. */
@Composable
expect fun rememberTextFileOpener(onText: (String?) -> Unit): () -> Unit

/** Окно во весь экран со своим скримом: без системной ширины и (на Android) без подгонки под системные панели. */
expect fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties

/** Текст без вертикальных полей шрифта (Android `includeFontPadding = false`); на iOS их и так нет — null. */
expect fun noFontPadding(): androidx.compose.ui.text.PlatformTextStyle?

/** Видна ли клавиатура — по высоте вставки IME (общий код, без Android-only `isImeVisible`). */
/** Высота читается через `derivedStateOf`: экран перестраивается, только когда клавиатура появилась или ушла, а не на каждом кадре её въезда. */
@androidx.compose.runtime.Composable
fun imeVisible(): Boolean {
    val ime = androidx.compose.foundation.layout.WindowInsets.ime
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.runtime.remember(ime, density) {
        androidx.compose.runtime.derivedStateOf { ime.getBottom(density) > 0 }
    }.value
}

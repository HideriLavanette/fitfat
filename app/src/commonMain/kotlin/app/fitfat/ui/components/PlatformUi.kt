package app.fitfat.ui.components

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

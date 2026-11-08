package app.fitfat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.DialogProperties
import app.fitfat.notify.ReminderScheduler
import app.fitfat.platform.topViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/** На iOS у `Dialog` своего затемнения нет — скрим рисует само окно. */
@Composable
actual fun DialogNoDim() {}

@Composable
actual fun rememberNotificationPermission(onDenied: () -> Unit): () -> Unit {
    val denied by rememberUpdatedState(onDenied)
    return { ReminderScheduler.request { ok -> if (!ok) denied() } }
}

/** Файл пишется во временный каталог и уходит в лист «Поделиться» — оттуда «Сохранить в Файлы», почта и т. д. */
@Composable
actual fun rememberTextFileSaver(mime: String, onDone: (Boolean) -> Unit): (name: String, content: suspend () -> String) -> Unit {
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    return { name, content ->
        scope.launch {
            val text = withContext(Dispatchers.Default) { runCatching { content() }.getOrNull() }
            val path = NSTemporaryDirectory() + name
            @Suppress("CAST_NEVER_SUCCEEDS")
            val written = text != null && (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            val top = topViewController()
            if (!written || top == null) { done(false); return@launch }
            val vc = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
            vc.completionWithItemsHandler = { _, completed, _, _ -> done(completed) }
            vc.popoverPresentationController?.sourceView = top.view
            top.presentViewController(vc, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberTextFileOpener(onText: (String?) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onText)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                val text = url?.let { NSString.stringWithContentsOfURL(it, encoding = NSUTF8StringEncoding, error = null) }
                deliver(text)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
        }
    }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText, UTTypeData), asCopy = true)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

actual fun noFontPadding(): androidx.compose.ui.text.PlatformTextStyle? = null

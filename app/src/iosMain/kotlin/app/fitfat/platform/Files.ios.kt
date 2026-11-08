package app.fitfat.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/** Каталог документов приложения — база и фото. */
fun documentDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = true, error = null,
    )
    return requireNotNull(url?.path) { "Нет каталога документов" }
}

/** Контроллер, поверх которого показывать системные окна (лист «Поделиться», выбор файла). */
fun topViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
    var vc = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

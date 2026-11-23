@file:OptIn(BetaInteropApi::class)

package app.fitfat.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Связка ключей iOS: здесь лежит токен обновления Google — он не попадает в резервные копии открытым текстом
 * и недоступен другим приложениям. Запись — «общий пароль» со службой [SERVICE].
 */
object Keychain {
    private const val SERVICE = "app.fitfat.google"

    /** Запрос «служба + имя записи». Вернувшиеся ссылки надо освободить: [release]. */
    private fun query(key: String): Pair<CFMutableDictionaryRef?, List<kotlinx.cinterop.COpaquePointer?>> {
        val dict = CFDictionaryCreateMutable(null, 6, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(key)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, service)
        CFDictionaryAddValue(dict, kSecAttrAccount, account)
        return dict to listOf(service, account)
    }

    private fun release(dict: CFMutableDictionaryRef?, refs: List<kotlinx.cinterop.COpaquePointer?>) {
        refs.forEach { if (it != null) CFRelease(it) }
        if (dict != null) CFRelease(dict)
    }

    /** Записать (или стереть при null). false — связка недоступна (например, сборка без подписи в симуляторе). */
    fun put(key: String, value: String?): Boolean {
        val (dict, refs) = query(key)
        try {
            SecItemDelete(dict)
            if (value == null) return true
            @Suppress("CAST_NEVER_SUCCEEDS")
            val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
            val cfData = CFBridgingRetain(data)
            CFDictionaryAddValue(dict, kSecValueData, cfData)
            CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            val status = SecItemAdd(dict, null)
            if (cfData != null) CFRelease(cfData)
            return status == errSecSuccess
        } finally {
            release(dict, refs)
        }
    }

    fun get(key: String): String? = memScoped {
        val (dict, refs) = query(key)
        try {
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
            val out = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(dict, out.ptr) != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(out.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        } finally {
            release(dict, refs)
        }
    }
}

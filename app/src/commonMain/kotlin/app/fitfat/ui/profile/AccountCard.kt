package app.fitfat.ui.profile

import app.fitfat.ui.components.FfAlert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.GoogleAccount
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.launch
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import org.jetbrains.compose.resources.decodeToImageBitmap
import androidx.compose.runtime.LaunchedEffect

/**
 * Аккаунт Google (макет Profile, верх экрана): круг 44 — аватарка Google (пока грузится или её нет — первая буква имени,
 * до входа — значок профиля), имя и почта, справа «Войти» (`primary`) / «Выйти» (тональная). Под карточкой — подпись 12
 * или текст ошибки. Решение автора 09.11.2026: «Войти / Выйти» вместо «Подключить / Отключить», аватарка из аккаунта.
 */
@Composable
fun AccountCard() {
    val colors = MaterialTheme.colorScheme
    val store = remember { GoogleAccount.get() }
    val account by store.account.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** Нашлась копия в Диске, а телефон пустой — предложить вернуть (время копии, мс). */
    var offer by remember { mutableStateOf<Long?>(null) }
    var restored by remember { mutableStateOf(false) }
    // Вошли раньше, чем приложение запоминало аватарку, — спросить её тихо, один раз
    LaunchedEffect(account?.email) { store.refreshProfile() }
    if (!app.fitfat.platform.Platform.supportsGoogleSignIn) {
        // iPhone: входа Google пока нет (docs/IOS.md) — карточка без кнопки
        Text(
            "Вход через Google на iPhone появится в следующих версиях. Пока данные только на телефоне.",
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow)
                .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                val letter = account?.let { (it.name ?: it.email).firstOrNull()?.uppercaseChar() }
                val avatar = rememberAvatar(account?.photo)
                when {
                    avatar != null -> androidx.compose.foundation.Image(
                        avatar, "Аватарка аккаунта", Modifier.size(44.dp).clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    letter != null -> Text("$letter", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
                    else -> Icon(MockIcons.NavProfile, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account?.name ?: "Google", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account?.email ?: "Вход не выполнен", fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedContent((account != null) to busy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "acc") { (on, wait) ->
                when {
                    wait -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    on -> FilledTonalButton(
                        onClick = { scope.launch { busy = true; store.signOut(); error = null; busy = false } },
                        modifier = Modifier.height(40.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainerHighest),
                    ) { Text("Выйти", fontSize = 14.sp) }
                    else -> Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                val r = store.signIn()
                                error = (r as? GoogleAccount.Result.Error)?.message
                                if (r is GoogleAccount.Result.Ok) {
                                    val db = app.fitfat.data.AppGraph.get().database
                                    val empty = runCatching { app.fitfat.data.Backup.isEmpty(db) }.getOrDefault(false)
                                    val remote = runCatching { app.fitfat.data.CloudBackup.remote(interactive = true) }.getOrNull()
                                    if (remote != null && empty) offer = remote.savedAt
                                    else if (remote == null) app.fitfat.data.CloudBackup.save(interactive = false)
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.height(40.dp).semantics { contentDescription = "Войти через Google" },
                    ) { Text("Войти", fontSize = 14.sp) }
                }
            }
        }
        Text(
            error ?: if (account != null) "Вы вошли. Дневник, вес и база продуктов сохраняются в ваш Google Диск."
            else "Войдите, чтобы не потерять дневник, вес и базу продуктов при смене телефона: копия хранится в вашем Google Диске. Приложение видит только имя, почту и аватарку.",
            fontSize = 12.sp, lineHeight = 16.sp, color = if (error != null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    offer?.let { at ->
        app.fitfat.ui.components.FfAlert(
            onDismissRequest = { offer = null },
            title = { Text("Нашлась копия") },
            text = { Text("В вашем Google Диске есть копия FitFat от ${app.fitfat.data.cloudWhen(at)}. Вернуть дневник, вес, базу продуктов и нормы на этот телефон?") },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { offer = null }) { Text("Не нужно") } },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    offer = null; busy = true
                    scope.launch {
                        when (val r = app.fitfat.data.CloudBackup.restore(interactive = true)) {
                            app.fitfat.data.CloudBackup.Result.Ok -> restored = true
                            is app.fitfat.data.CloudBackup.Result.Error -> error = r.message
                        }
                        busy = false
                    }
                }) { Text("Восстановить") }
            },
        )
    }
    if (restored) app.fitfat.ui.components.FfAlert(
        onDismissRequest = {},
        text = { Text("Данные восстановлены. Приложение перезапустится, чтобы всё подхватить.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { app.fitfat.platform.restartApp() }) { Text("Перезапустить") } },
    )
}

/** Аватарка Google по адресу: загружается один раз за запуск и держится в памяти; null — нет адреса, нет сети или не картинка. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun rememberAvatar(url: String?): androidx.compose.ui.graphics.ImageBitmap? {
    var img by remember(url) { mutableStateOf(url?.let { AvatarCache[it] }) }
    LaunchedEffect(url) {
        if (url == null || img != null) return@LaunchedEffect
        img = runCatching<androidx.compose.ui.graphics.ImageBitmap> {
            app.fitfat.data.Http.client.get(url).readRawBytes().decodeToImageBitmap()
        }.getOrNull()?.also { AvatarCache[url] = it }
    }
    return img
}

private val AvatarCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

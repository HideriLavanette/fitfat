package app.fitfat.ui.profile

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.GoogleAccount
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.launch

/**
 * Аккаунт Google (макет Profile, верх экрана): круг 44 с иконкой (после входа — первая буква имени), «Google» и статус,
 * справа «Подключить» (`primary`) / «Отключить» (тональная). Под карточкой — подпись 12 или текст ошибки.
 */
@Composable
fun AccountCard() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { GoogleAccount.get() }
    val account by store.account.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow)
                .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                val letter = account?.let { (it.name ?: it.email).firstOrNull()?.uppercaseChar() }
                if (letter != null) Text("$letter", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
                else Icon(MockIcons.NavProfile, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account?.name ?: "Google", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account?.email ?: "Не подключён", fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedContent((account != null) to busy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "acc") { (on, wait) ->
                when {
                    wait -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                    on -> FilledTonalButton(
                        onClick = { scope.launch { busy = true; store.signOut(); error = null; busy = false } },
                        modifier = Modifier.height(40.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainerHighest),
                    ) { Text("Отключить", fontSize = 14.sp) }
                    else -> Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = when (val r = store.signIn()) {
                                    is GoogleAccount.Result.Error -> r.message
                                    else -> null
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.height(40.dp).semantics { contentDescription = "Подключить аккаунт Google" },
                    ) { Text("Подключить", fontSize = 14.sp) }
                }
            }
        }
        Text(
            error ?: if (account != null) "Аккаунт подключён. Сохранение дневника, веса и базы продуктов в облаке появится в следующих версиях — пока данные только на телефоне."
            else "Подключите аккаунт, чтобы потом не потерять дневник, вес и базу продуктов при смене телефона. Приложение видит только имя и почту.",
            fontSize = 12.sp, lineHeight = 16.sp, color = if (error != null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

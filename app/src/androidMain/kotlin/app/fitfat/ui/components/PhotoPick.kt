package app.fitfat.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Photos
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.launch

/** Фото из `files/photos`, обрезанное по рамке; пока грузится — пусто (фон даёт [modifier]). */
@Composable
fun PhotoImage(name: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val img by produceState(Photos.cached(name), name) { value = Photos.load(ctx, name) }
    Box(modifier) {
        Crossfade(img, animationSpec = tween(180), label = "photo") { b ->
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Камера и галерея. Лаунчеры живут на экране, а не в шторке, — иначе результат теряется, когда шторка закрывается. */
class PhotoPicker internal constructor(val busy: Boolean, val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoPicker(onPhoto: (String) -> Unit): PhotoPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onPhoto)
    var busy by remember { mutableStateOf(false) }
    fun take(uri: android.net.Uri) {
        busy = true
        scope.launch { Photos.import(ctx, uri)?.let(deliver); busy = false }
    }
    val galleryL = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::take) }
    val camUri = remember { Photos.cameraUri(ctx) }
    val cameraL = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) take(camUri) }
    return PhotoPicker(
        busy,
        camera = { try { cameraL.launch(camUri) } catch (_: ActivityNotFoundException) { galleryL.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        gallery = { galleryL.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/**
 * Плитка фото из макетов (4.3, 4.4): нет фото — `surfaceContainerHigh`, камера и «Фото»;
 * есть — само фото и кружок с камерой в углу («сменить»).
 */
@Composable
fun PhotoTile(photo: String?, size: Dp, radius: Dp, busy: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(colors.surfaceContainerHigh).clickable(onClick = onClick)
            .semantics { contentDescription = if (photo == null) "Добавить фото" else "Сменить фото" },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            PhotoImage(photo, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(MockIcons.Camera, null, Modifier.size(16.dp), tint = colors.onSurface) }
        } else if (!busy) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(MockIcons.Camera, null, Modifier.size(28.dp), tint = colors.onSurfaceVariant)
                Text("Фото", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = colors.primary)
    }
}

/** Шторка выбора: «Снять» · «Выбрать из галереи» · «Убрать фото» (если есть). */
@Composable
fun PhotoSheet(picker: PhotoPicker, hasPhoto: Boolean, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FfSheet(onDismiss = onDismiss, gap = 8.dp) {
        Text(if (hasPhoto) "Сменить фото" else "Добавить фото", fontSize = 20.sp, fontWeight = FontWeight(650), modifier = Modifier.padding(bottom = 6.dp))
        PhotoRow(MockIcons.Camera, "Снять на камеру", colors.onSurface) { onDismiss(); picker.camera() }
        PhotoRow(MockIcons.Gallery, "Выбрать из галереи", colors.onSurface) { onDismiss(); picker.gallery() }
        if (hasPhoto) PhotoRow(MockIcons.Delete, "Убрать фото", colors.error) { onDismiss(); onRemove() }
    }
}

@Composable
private fun PhotoRow(icon: ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = ink)
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink)
    }
}

package app.fitfat.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.fitfat.data.Photos
import kotlinx.coroutines.launch

@Composable
actual fun rememberPhotoPicker(onPhoto: (String) -> Unit): PhotoPicker {
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

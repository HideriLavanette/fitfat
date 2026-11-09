package app.fitfat.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.fitfat.data.Photos
import app.fitfat.data.Product
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.macroLine
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

private val FORMATS = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build()

/** Найденный штрихкод: цифры и рамка в долях кадра анализа (уже повёрнутого), [aspect] — ширина / высота кадра. */
private data class Seen(val code: String, val l: Float, val t: Float, val r: Float, val b: Float, val aspect: Float, val at: Long)

/** Что под найденным штрихкодом: ищем, нашли товар, нет в базе. */
private sealed interface Found {
    data object Looking : Found
    data class Item(val product: Product) : Found
    data object Missing : Found
}

/**
 * Камера на CameraX. Лёгкая: превью — SurfaceView (режим PERFORMANCE), анализ — кадр 1280×720 не чаще чем раз в 150 мс,
 * снимок подключается только при доступе к ассистенту. Поверх живой картинки — белый и полупрозрачный чёрный.
 */
@Composable
actual fun FoodCamera(
    allowPhoto: Boolean,
    lookup: suspend (String) -> Product?,
    onProduct: (Product) -> Unit,
    onCreate: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val find by rememberUpdatedState(lookup)
    val photo by rememberUpdatedState(onPhoto)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf<Seen?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Found?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    // Новый штрихкод — вибрация и поиск товара; рамка гаснет, если штрихкода в кадре нет дольше 0,8 с
    LaunchedEffect(code) {
        val c = code ?: return@LaunchedEffect
        Feedback.tickSilent()
        found = Found.Looking
        found = find(c)?.let { Found.Item(it) } ?: Found.Missing
    }
    LaunchedEffect(seen) { val s = seen ?: return@LaunchedEffect; delay(800); if (seen?.at == s.at) seen = null }

    fun detected(c: String) { if (c != code) code = c }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; note = null
        val still = BarcodeScanning.getClient(FORMATS)
        val task = runCatching { still.process(InputImage.fromFilePath(ctx, uri)) }.getOrNull()
        if (task == null) { still.close(); busy = false; return@rememberLauncherForActivityResult }
        task.addOnCompleteListener { t ->
            still.close()
            val c = if (t.isSuccessful) t.result.firstNotNullOfOrNull { b -> b.rawValue?.filter(Char::isDigit)?.takeIf { it.length >= 8 } } else null
            when {
                c != null -> { busy = false; detected(c) }
                allowPhoto -> scope.launch { Photos.import(ctx, uri)?.let(photo); busy = false }
                else -> { busy = false; note = "На фото не нашёлся штрихкод." }
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                val exec = remember { Executors.newSingleThreadExecutor() }
                val scanner = remember { BarcodeScanning.getClient(FORMATS) }
                DisposableEffect(Unit) { onDispose { scanner.close(); exec.shutdown() } }
                AndroidView(
                    factory = { c ->
                        PreviewView(c).also { view ->
                            view.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            view.scaleType = PreviewView.ScaleType.FILL_CENTER
                            val future = ProcessCameraProvider.getInstance(c)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                val analysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(
                                        ResolutionSelector.Builder().setResolutionStrategy(
                                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                                        ).build(),
                                    )
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                var last = 0L
                                analysis.setAnalyzer(exec) { proxy ->
                                    val media = proxy.image
                                    val now = SystemClock.uptimeMillis()
                                    if (media == null || now - last < 150) { proxy.close(); return@setAnalyzer }
                                    last = now
                                    val rot = proxy.imageInfo.rotationDegrees
                                    val w = (if (rot % 180 == 0) proxy.width else proxy.height).toFloat()
                                    val h = (if (rot % 180 == 0) proxy.height else proxy.width).toFloat()
                                    scanner.process(InputImage.fromMediaImage(media, rot))
                                        .addOnSuccessListener { codes ->
                                            val b = codes.firstOrNull { it.rawValue?.count(Char::isDigit) ?: 0 >= 8 } ?: return@addOnSuccessListener
                                            val box = b.boundingBox ?: return@addOnSuccessListener
                                            val digits = b.rawValue!!.filter(Char::isDigit)
                                            view.post {
                                                seen = Seen(digits, box.left / w, box.top / h, box.right / w, box.bottom / h, w / h, SystemClock.uptimeMillis())
                                                detected(digits)
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                }
                                runCatching {
                                    provider.unbindAll()
                                    val cases = mutableListOf<UseCase>(preview, analysis)
                                    if (allowPhoto) cases += capture
                                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray())
                                }
                            }, ContextCompat.getMainExecutor(c))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } },
                )
                // Рамка — только вокруг найденного штрихкода (координаты кадра анализа → экран, как FILL_CENTER)
                val alpha by animateFloatAsState(if (seen != null) 1f else 0f, tween(180), label = "frame")
                val last = remember { mutableStateOf<Seen?>(null) }
                seen?.let { last.value = it }
                Canvas(Modifier.fillMaxSize()) {
                    val s = last.value ?: return@Canvas
                    if (alpha <= 0f) return@Canvas
                    val vw = size.width; val vh = size.height
                    val iw: Float; val ih: Float
                    if (vw / vh > s.aspect) { iw = vw; ih = vw / s.aspect } else { ih = vh; iw = vh * s.aspect }
                    val dx = (vw - iw) / 2; val dy = (vh - ih) / 2
                    val pad = 10.dp.toPx()
                    val l = dx + s.l * iw - pad; val t = dy + s.t * ih - pad
                    val r = dx + s.r * iw + pad; val b = dy + s.b * ih + pad
                    drawRoundRect(Color.White.copy(alpha = alpha), Offset(l, t), GSize(r - l, b - t), CornerRadius(14.dp.toPx()), style = Stroke(3.dp.toPx()))
                }
            } else {
                Text(
                    if (denied) "Нет доступа к камере. Разрешите его в настройках телефона — или выберите фото из галереи." else "Нужен доступ к камере",
                    color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            // Верх: «Закрыть» слева и подсказка в плашке
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(onClick = onClose, shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Close, "Закрыть", Modifier.size(24.dp), tint = Color.White) }
                }
                Text(
                    if (allowPhoto) "Наведите на штрихкод или сфотографируйте блюдо" else "Наведите на штрихкод",
                    color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Box(Modifier.size(48.dp)) // симметрия с «Закрыть», подсказка ровно по центру
            }

            // Низ: затемнение, как под навбаром; карточка найденного товара, снимок, широкая «Из галереи»
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.8f)))
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(code != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                    val c = code
                    Surface(
                        onClick = {
                            when (val f = found) {
                                is Found.Item -> onProduct(f.product)
                                Found.Missing -> if (c != null) onCreate(c)
                                else -> {}
                            }
                        },
                        shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (val f = found) {
                                is Found.Item -> {
                                    val p = f.product
                                    CategoryIcon(p.category, 44.dp, radius = 14.dp, iconSize = 22.dp, photo = p.photo)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(macroLine(p.protein, p.fat, p.carbs, prefix = "${fmt0(p.kcal)} ккал · ", tail = if (p.perPiece) " на 1 шт" else " на 100 г"), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
                                    }
                                    Icon(MockIcons.ChevronRight, "Добавить", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                                }
                                Found.Missing -> {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Товара нет в базе", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Штрихкод $c", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                    }
                                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primary).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Text("Создать продукт", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                                    }
                                }
                                else -> {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Text("Ищу товар…", fontSize = 15.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                note?.let {
                    Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp))
                }
                if (allowPhoto) Surface(
                    onClick = {
                        if (busy || !granted) return@Surface
                        busy = true
                        val file = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "food.jpg")
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                    scope.launch { Photos.import(ctx, Uri.fromFile(file))?.let(photo); busy = false }
                                }
                                override fun onError(e: ImageCaptureException) { busy = false }
                            },
                        )
                    },
                    shape = CircleShape, color = Color.White,
                    modifier = Modifier.size(76.dp).border(5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .semantics { contentDescription = "Сфотографировать блюдо" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.Black)
                    }
                }
                Surface(
                    onClick = { if (!busy) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MockIcons.Gallery, null, Modifier.size(20.dp), tint = Color.White)
                        Text("Из галереи", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

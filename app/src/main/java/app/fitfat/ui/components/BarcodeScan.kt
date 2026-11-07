package app.fitfat.ui.components

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Сканер штрихкодов Google (ML Kit): свой экран камеры, разрешение камеры приложению не нужно.
 * [onCode] — цифры штрихкода; отмена — ничего.
 */
fun scanBarcode(ctx: Context, onCode: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { b -> b.rawValue?.filter { it.isDigit() }?.takeIf { it.length >= 8 }?.let(onCode) }
        .addOnFailureListener { Toast.makeText(ctx, "Сканер недоступен: нужны сервисы Google Play", Toast.LENGTH_LONG).show() }
}

package com.guruyuknow.hisabbook.Bills

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

object BillOcr {

    suspend fun readTextFromUri(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val stream = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = BitmapFactory.decodeStream(stream)
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(input).await()
        return result.text ?: ""
    }

    /** Extracts the largest plausible money value like 1,234.56 or 1234.00 or ₹999 */
    fun extractBestAmount(text: String): Double? {
        if (text.isBlank()) return null
        val clean = text.replace(",", "")
        val pattern = Pattern.compile("""(?:₹?\s*)(\d+(?:\.\d{1,2})?)""")
        val m = pattern.matcher(clean)
        var best: Double? = null
        while (m.find()) {
            val v = m.group(1)?.toDoubleOrNull() ?: continue
            // Heuristic: ignore tiny numbers that are likely item counts
            if (v < 5) continue
            if (best == null || v > best!!) best = v
        }
        return best
    }
}

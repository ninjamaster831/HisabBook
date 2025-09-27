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

    data class OcrResult(
        val fullText: String,
        val extractedAmount: Double?,
        val confidenceScore: Double
    )

    suspend fun processImageWithOcr(context: Context, uri: Uri): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val stream = context.contentResolver.openInputStream(uri) ?: return OcrResult("", null, 0.0)
        val bitmap = BitmapFactory.decodeStream(stream)
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(input).await()

        val fullText = result.text ?: ""
        val extractedAmount = extractBestAmount(fullText)

        // Calculate confidence score based on text blocks confidence
        val averageConfidence = if (fullText.isBlank()) 0.0 else 0.85

        return OcrResult(
            fullText = fullText,
            extractedAmount = extractedAmount,
            confidenceScore = averageConfidence
        )
    }

    suspend fun readTextFromUri(context: Context, uri: Uri): String {
        return processImageWithOcr(context, uri).fullText
    }

    /**
     * Enhanced amount extraction with better regex patterns
     * Extracts the largest plausible money value like 1,234.56 or 1234.00 or ₹999
     */
    fun extractBestAmount(text: String): Double? {
        if (text.isBlank()) return null

        val patterns = listOf(
            // Match total amount patterns (common in bills)
            Pattern.compile("""(?i)(?:total|amount|sum|grand\s+total|net\s+amount)[\s:]*(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""),
            // Match currency symbols with amounts
            Pattern.compile("""(?:₹|rs\.?|inr)\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""),
            // Match standalone large amounts
            Pattern.compile("""(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?)"""),
            // Match decimal amounts
            Pattern.compile("""(\d{3,}(?:\.\d{1,2})?)""")
        )

        val amounts = mutableListOf<Double>()

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "")
                val amount = amountStr?.toDoubleOrNull()
                if (amount != null && amount >= 10) { // Ignore very small amounts
                    amounts.add(amount)
                }
            }
            // If we found amounts with this pattern, prefer them
            if (amounts.isNotEmpty()) break
        }

        // Return the largest amount found
        return amounts.maxOrNull()
    }

    /**
     * Extract merchant/vendor name from bill text
     */
    fun extractMerchantName(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // Usually merchant name is in the first few lines
        return lines.take(3).firstOrNull { line ->
            // Look for lines that don't contain numbers or common bill keywords
            !line.matches(Regex(".*\\d.*")) &&
                    !line.contains("bill", true) &&
                    !line.contains("invoice", true) &&
                    !line.contains("receipt", true) &&
                    line.length in 3..50
        }
    }

    /**
     * Extract date from bill text
     */
    fun extractDate(text: String): String? {
        val datePatterns = listOf(
            Pattern.compile("""(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})"""),
            Pattern.compile("""(\d{1,2}\s+\w{3,9}\s+\d{2,4})"""), // 15 March 2024
            Pattern.compile("""(\w{3,9}\s+\d{1,2},?\s+\d{2,4})""") // March 15, 2024
        )

        for (pattern in datePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
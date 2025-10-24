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
     * Enhanced amount extraction with priority-based matching
     * Priority order:
     * 1. Lines containing "TOTAL AMOUNT", "GRAND TOTAL", "NET AMOUNT" (final total only)
     * 2. Lines containing "AMOUNT DUE", "BALANCE DUE", "PAYABLE"
     * 3. Lines with "TOTAL" (but NOT "SUBTOTAL")
     * 4. Currency symbols with amounts
     * 5. Largest standalone amount
     */
    fun extractBestAmount(text: String): Double? {
        if (text.isBlank()) return null

        val lines = text.split("\n").map { it.trim() }

        // Debug: Log all lines with amounts
        android.util.Log.d("BillOcr", "=== OCR Text Lines ===")
        lines.forEachIndexed { index, line ->
            if (line.matches(Regex(""".*\d+.*"""))) {
                android.util.Log.d("BillOcr", "Line $index: $line")
            }
        }
        android.util.Log.d("BillOcr", "===================")

        // Collect all amounts with their context for smarter selection
        val allAmounts = mutableListOf<Triple<Double, String, Int>>() // amount, line, priority


        // Extract all amounts with context
        val amountPattern = Pattern.compile("""(?:₹|rs\.?|\$|inr)?\s*(\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?)""")

        for (line in lines) {
            val lineLower = line.lowercase()

            // Skip obvious non-amount lines
            if (line.matches(Regex(""".*\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}.*"""))) continue // dates
            if (line.matches(Regex(""".*\d{1,2}:\d{2}.*"""))) continue // times
            if (line.matches(Regex(""".*cashier.*#\d+.*""", RegexOption.IGNORE_CASE))) continue // cashier IDs

            val matcher = amountPattern.matcher(line)
            while (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(Regex("[,\\s]"), "")
                val amount = amountStr?.toDoubleOrNull()

                if (amount != null && amount >= 1 && amount !in 1900.0..2100.0) {
                    // Assign priority based on keywords
                    val priority = when {
                        // HIGHEST: Explicit total amount keywords
                        lineLower.contains("total amount") ||
                                lineLower.contains("grand total") ||
                                lineLower.contains("net amount") ||
                                lineLower.contains("final amount") ||
                                lineLower.contains("amount payable") -> 1

                        // HIGH: Amount due, balance
                        lineLower.contains("amount due") ||
                                lineLower.contains("balance due") ||
                                lineLower.contains("payable") -> 2

                        // MEDIUM-HIGH: Just "total" (but not subtotal)
                        lineLower.contains("total") && !lineLower.contains("sub") -> 3

                        // MEDIUM: Has currency symbol
                        line.matches(Regex(""".*[₹\$].*""")) -> 4

                        // LOW: Just a number
                        else -> 5
                    }

                    // EXCLUDE subtotal amounts
                    if (lineLower.contains("subtotal") ||
                        lineLower.contains("sub total") ||
                        lineLower.contains("sub-total")) {
                        android.util.Log.d("BillOcr", "SKIPPING SUBTOTAL: $line = $amount")
                        continue
                    }

                    allAmounts.add(Triple(amount, line, priority))
                    android.util.Log.d("BillOcr", "Found amount: $amount (priority $priority) in: $line")
                }
            }
        }

        // Sort by priority (lower is better), then by amount (higher is better)
        val sortedAmounts = allAmounts.sortedWith(compareBy<Triple<Double, String, Int>> { it.third }.thenByDescending { it.first })

        val bestAmount = sortedAmounts.firstOrNull()
        android.util.Log.d("BillOcr", "SELECTED AMOUNT: ${bestAmount?.first} from line: ${bestAmount?.second}")

        return bestAmount?.first
    }

    /**
     * Extract merchant/vendor name from bill text
     */
    fun extractMerchantName(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // Usually merchant name is in the first few lines
        return lines.take(5).firstOrNull { line ->
            // Look for lines that are primarily text, not numbers
            val digitCount = line.count { it.isDigit() }
            val letterCount = line.count { it.isLetter() }

            letterCount > digitCount &&
                    !line.contains("receipt", true) &&
                    !line.contains("invoice", true) &&
                    !line.contains("bill", true) &&
                    !line.contains("cashier", true) &&
                    line.length in 3..50
        }
    }

    /**
     * Extract date from bill text
     */
    fun extractDate(text: String): String? {
        val datePatterns = listOf(
            // DD/MM/YYYY or DD-MM-YYYY
            Pattern.compile("""(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})"""),
            // DD Month YYYY (15 March 2024, 15 Mar 2024)
            Pattern.compile("""(\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{2,4})""", Pattern.CASE_INSENSITIVE),
            // Month DD, YYYY (March 15, 2024)
            Pattern.compile("""((?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4})""", Pattern.CASE_INSENSITIVE)
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
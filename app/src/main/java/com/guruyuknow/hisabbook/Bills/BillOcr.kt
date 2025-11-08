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
     * Enhanced amount extraction with priority-based matching and context-aware detection
     * Priority order:
     * 1. Lines containing "TOTAL AMOUNT", "GRAND TOTAL", "NET AMOUNT" (final total only)
     * 2. Lines containing "AMOUNT DUE", "BALANCE DUE", "PAYABLE"
     * 3. Lines with "TOTAL" or "AMOUNT" (but NOT "SUBTOTAL")
     * 4. Currency symbols with amounts
     * 5. Context-based detection (line position, surrounding lines)
     * 6. Largest reasonable amount
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
        val amountPattern = Pattern.compile("""(?:₹|rs\.?|\$|€|inr)?\s*(\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?)""")

        for ((index, line) in lines.withIndex()) {
            val lineLower = line.lowercase()

            // Skip obvious non-amount lines
            if (line.matches(Regex(""".*\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}.*"""))) continue // dates
            if (line.matches(Regex(""".*\d{1,2}:\d{2}.*"""))) continue // times
            if (line.matches(Regex(""".*cashier.*#\d+.*""", RegexOption.IGNORE_CASE))) continue // cashier IDs
            if (line.matches(Regex(""".*\d{3}[-.\s]\d{3}[-.\s]\d{4}.*"""))) continue // phone numbers
            if (line.matches(Regex(""".*\|{3,}.*"""))) continue // barcodes (3+ pipes)
            if (line.matches(Regex("""^[|\s]+$"""))) continue // barcode-only lines
            if (lineLower.contains("address") || lineLower.contains("adress")) continue // address lines
            if (lineLower.contains("tel:") || lineLower.contains("tel ")) continue // telephone lines
            if (line.contains(".com/") || line.contains(".net/")) continue // URLs/watermarks
            if (line.matches(Regex(""".*www\..*"""))) continue // websites

            // Get context from surrounding lines (for label detection)
            val prevLine = if (index > 0) lines[index - 1].lowercase() else ""
            val nextLine = if (index < lines.size - 1) lines[index + 1].lowercase() else ""
            val contextLines = "$prevLine $lineLower $nextLine"

            val matcher = amountPattern.matcher(line)
            while (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(Regex("[,\\s]"), "")
                val amount = amountStr?.toDoubleOrNull()

                // Filter valid amounts: between 0.01 and 999999, not years
                if (amount != null && amount >= 0.01 && amount < 1000000.0 && amount !in 1900.0..2100.0) {
                    // Assign priority based on keywords (check both line and context)
                    val priority = when {
                        // HIGHEST: Explicit total amount keywords
                        contextLines.contains("total amount") ||
                                contextLines.contains("grand total") ||
                                contextLines.contains("net amount") ||
                                contextLines.contains("final amount") ||
                                contextLines.contains("amount payable") -> 1

                        // HIGH: Amount due, balance
                        contextLines.contains("amount due") ||
                                contextLines.contains("balance due") ||
                                contextLines.contains("payable") -> 2

                        // MEDIUM-HIGH: Just "total" or "amount" (but not subtotal/sub-total)
                        (contextLines.contains("total") || contextLines.contains("amount")) &&
                                !contextLines.contains("sub") -> 3

                        // MEDIUM: Has currency symbol
                        line.matches(Regex(""".*[₹\$€].*""")) -> 4

                        // MEDIUM-LOW: Appears after "subtotal" section (likely the final total)
                        index > lines.indexOfFirst { it.lowercase().contains("sub") } &&
                                index > lines.size / 2 -> 5

                        // LOW: Just a number
                        else -> 6
                    }

                    // EXCLUDE known non-total keywords
                    if (contextLines.contains("subtotal") ||
                        contextLines.contains("sub total") ||
                        contextLines.contains("sub-total") ||
                        contextLines.contains("discount") ||
                        contextLines.contains("tax only") ||
                        contextLines.contains("sales tax") && !contextLines.contains("amount") ||
                        contextLines.contains("change") ||
                        contextLines.contains("cash given") ||
                        contextLines.contains("balance") && !contextLines.contains("due")) {
                        android.util.Log.d("BillOcr", "SKIPPING (excluded keyword): $line = $amount")
                        continue
                    }

                    // Skip amounts from address/header section (typically first 5 lines)
                    if (index < 5 && amount > 100) {
                        android.util.Log.d("BillOcr", "SKIPPING (likely address): $line = $amount")
                        continue
                    }

                    // Skip amounts that are too small to be realistic bill totals (unless high priority)
                    if (amount < 1.0 && priority > 3) {
                        android.util.Log.d("BillOcr", "SKIPPING (too small): $line = $amount")
                        continue
                    }

                    allAmounts.add(Triple(amount, line, priority))
                    android.util.Log.d("BillOcr", "Found amount: $amount (priority $priority) in: $line")
                }
            }
        }

        // If we have no high-priority amounts, use heuristics:
        // The final/total amount is usually one of the larger amounts in the bottom half
        val hasHighPriority = allAmounts.any { it.third <= 3 }

        val sortedAmounts = if (hasHighPriority) {
            // Sort by priority (lower is better), then by amount (higher is better)
            allAmounts.sortedWith(
                compareBy<Triple<Double, String, Int>> { it.third }
                    .thenByDescending { it.first }
            )
        } else {
            // No context found - use smart fallback:
            // Prefer larger amounts that aren't suspiciously large
            android.util.Log.d("BillOcr", "No high-priority amounts found, using fallback logic")
            allAmounts
                .filter { it.first < 10000 } // Exclude unrealistically large amounts
                .sortedWith(
                    compareBy<Triple<Double, String, Int>> { it.third }
                        .thenByDescending { it.first }
                )
        }

        val bestAmount = sortedAmounts.firstOrNull()
        android.util.Log.d("BillOcr", "SELECTED AMOUNT: ${bestAmount?.first} from line: ${bestAmount?.second}")

        // Log all candidates for debugging
        android.util.Log.d("BillOcr", "All candidates (sorted):")
        sortedAmounts.take(5).forEach { (amt, line, prio) ->
            android.util.Log.d("BillOcr", "  Priority $prio: $amt from '$line'")
        }

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
                    !line.contains("address", true) &&
                    !line.contains("tel", true) &&
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
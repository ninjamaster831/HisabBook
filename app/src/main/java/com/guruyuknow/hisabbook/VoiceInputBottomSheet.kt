package com.guruyuknow.hisabbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.FragmentVoiceInputBinding
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "VoiceInputFragment"
private const val RECORD_AUDIO_PERMISSION_CODE = 101

class VoiceInputBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentVoiceInputBinding? = null
    private val binding get() = _binding!!

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var retryCount = 0
    private val maxRetries = 2

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @Serializable
    private data class CashbookEntryInsert(
        @SerialName("user_id") val userId: String,
        val amount: Double,
        val type: String,
        @SerialName("payment_method") val paymentMethod: String,
        val description: String? = null,
        val category: String? = null,
        val date: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        showManualFallbackUI(false)

        // Check capabilities
        val micOk = hasMicrophone()
        val recoOk = SpeechRecognizer.isRecognitionAvailable(requireContext())

        Log.d(TAG, "=== Voice Input Initialization ===")
        Log.d(TAG, "Microphone available: $micOk")
        Log.d(TAG, "Recognition available: $recoOk")

        if (!micOk || !recoOk) {
            Toast.makeText(
                requireContext(),
                "Speech recognition not available. Using manual input.",
                Toast.LENGTH_LONG
            ).show()
            showManualFallbackUI(true)
            binding.statusText.text = "Manual input mode"
        } else {
            // Delay initialization slightly to ensure dialog is fully loaded
            mainHandler.postDelayed({
                if (_binding != null) {
                    checkPermissionAndInitialize()
                }
            }, 300)
        }
    }

    private fun setupUI() {
        binding.micButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                if (!isInitialized) {
                    checkPermissionAndInitialize()
                } else {
                    startListening()
                }
            }
        }

        binding.closeButton.setOnClickListener { dismiss() }

        binding.manualSubmitButton.setOnClickListener {
            val typedText = binding.manualInputField.text?.toString()?.trim().orEmpty()
            if (typedText.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a command", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleSpokenCommand(typedText)
            binding.manualInputField.text?.clear()
        }
    }

    private fun checkPermissionAndInitialize() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Permission granted, initializing...")
                initializeSpeechRecognizer()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    requireContext(),
                    "Microphone permission needed for voice input",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_CODE
                )
            }
            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_CODE
                )
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            // Clean up any existing instance
            speechRecognizer?.destroy()
            speechRecognizer = null

            Log.d(TAG, "Creating new SpeechRecognizer instance...")

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
                setRecognitionListener(createRecognitionListener())
            }

            isInitialized = true
            retryCount = 0

            Log.d(TAG, "SpeechRecognizer initialized successfully")

            // Start listening automatically
            startListening()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            Toast.makeText(
                requireContext(),
                "Voice input initialization failed. Use manual input.",
                Toast.LENGTH_LONG
            ).show()
            showManualFallbackUI(true)
            isInitialized = false
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "✓ Ready for speech")
            isListening = true
            retryCount = 0
            updateUIForListening(true)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "✓ Speech started")
            binding.statusText.text = "Listening..."
            binding.transcriptionText.text = "Speak now..."
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Visual feedback for volume levels
            val volume = (rmsdB + 2) / 12 // Normalize to 0-1 range
            // Could update UI here with volume indicator
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "✓ Speech ended")
            isListening = false
            binding.statusText.text = "Processing..."
            updateUIForListening(false)
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Speech recognition error: $error")
            isListening = false
            updateUIForListening(false)

            val (errorMessage, shouldRetry, showFallback) = when (error) {
                SpeechRecognizer.ERROR_AUDIO ->
                    Triple("Audio recording error", false, true)
                SpeechRecognizer.ERROR_CLIENT ->
                    Triple("Client error", true, false)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    Triple("Microphone permission denied", false, true)
                SpeechRecognizer.ERROR_NETWORK ->
                    Triple("Network error", true, false)
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    Triple("Network timeout", true, false)
                SpeechRecognizer.ERROR_NO_MATCH ->
                    Triple("No speech detected. Speak clearly.", true, false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    Triple("Service busy", true, false)
                SpeechRecognizer.ERROR_SERVER ->
                    Triple("Server error", true, false)
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    Triple("No speech input", true, false)
                9 -> // ERROR_INSUFFICIENT_PERMISSIONS on some devices
                    Triple("Permission denied", false, true)
                11 -> // Custom error on some devices
                    Triple("Recognition service unavailable", true, true)
                else ->
                    Triple("Error $error occurred", true, false)
            }

            Log.d(TAG, "Error details: $errorMessage (shouldRetry=$shouldRetry, showFallback=$showFallback)")

            binding.statusText.text = errorMessage

            if (shouldRetry && retryCount < maxRetries) {
                retryCount++
                Log.d(TAG, "Retry attempt $retryCount of $maxRetries")

                binding.statusText.text = "$errorMessage - Retrying..."

                mainHandler.postDelayed({
                    if (_binding != null && !isListening) {
                        startListening()
                    }
                }, 1000)
            } else {
                if (showFallback || retryCount >= maxRetries) {
                    binding.transcriptionText.text =
                        "Voice input unavailable. Please use manual input below."
                    showManualFallbackUI(true)
                } else {
                    binding.transcriptionText.text =
                        "Tap microphone to try again or use manual input"
                    showManualFallbackUI(true)
                }
            }

            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "✓ Results received: $matches")

            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                Log.d(TAG, "Processing: '$spokenText'")
                handleSpokenCommand(spokenText)
            } else {
                binding.statusText.text = "No speech detected"
                binding.transcriptionText.text = "Try again or use manual input"
                showManualFallbackUI(true)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                binding.transcriptionText.text = matches[0]
                Log.d(TAG, "Partial: ${matches[0]}")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Event: $eventType")
        }
    }

    private fun startListening() {
        if (!isInitialized) {
            Log.e(TAG, "Cannot start listening - not initialized")
            initializeSpeechRecognizer()
            return
        }

        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // Indian English
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Try offline first for faster response
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            Log.d(TAG, "Starting listening...")
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            Toast.makeText(requireContext(), "Failed to start listening: ${e.message}", Toast.LENGTH_LONG).show()
            showManualFallbackUI(true)
        }
    }

    private fun stopListening() {
        try {
            Log.d(TAG, "Stopping listening")
            speechRecognizer?.stopListening()
            isListening = false
            updateUIForListening(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }

    private fun updateUIForListening(listening: Boolean) {
        if (listening) {
            binding.micButton.setIconResource(R.drawable.ic_mic_off)
            binding.statusText.text = "Listening... Tap to stop"
            binding.listeningIndicator.visibility = View.VISIBLE
        } else {
            binding.micButton.setIconResource(R.drawable.ic_mic)
            binding.statusText.text = "Tap microphone to speak"
            binding.listeningIndicator.visibility = View.GONE
        }
    }

    private fun handleSpokenCommand(spokenText: String) {
        binding.transcriptionText.text = spokenText
        binding.statusText.text = "Processing..."

        val command = parseVoiceCommand(spokenText)
        if (command != null) {
            binding.statusText.text = "Adding ${command.type.lowercase()}..."
            addTransaction(command)
        } else {
            binding.statusText.text = "Could not understand command"
            binding.transcriptionText.text = """
                $spokenText
                
                ❌ Could not parse command
                
                Try these formats:
                • "Add expense 100 for petrol"
                • "Spent 50 on lunch"
                • "Received 1000 salary"
                • "Sale 500"
            """.trimIndent()
            showManualFallbackUI(true)
        }
    }

    private fun parseVoiceCommand(text: String): VoiceCommand? {
        val t = text.lowercase(Locale.getDefault()).trim()
        Log.d(TAG, "🎤 Parsing: '$text'")

        // Type detection
        val outKeywords = listOf("expense", "spent", "spend", "paid", "purchase", "bought", "buy", "pay")
        val inKeywords = listOf("income", "sale", "received", "receive", "earned", "earn", "got", "salary")

        val hasOut = outKeywords.any { t.contains(it) }
        val hasIn = inKeywords.any { t.contains(it) }

        val type = when {
            hasIn && !hasOut -> "IN"
            hasOut && !hasIn -> "OUT"
            t.contains("income") -> "IN"
            t.contains("expense") -> "OUT"
            else -> null
        }

        if (type == null) {
            Log.d(TAG, "❌ No type detected")
            return null
        }

        Log.d(TAG, "✅ Type: $type")

        // Amount extraction - multiple patterns
        val patterns = listOf(
            Regex("""(?:₹|rs\.?|rupees?|inr)?\s*(\d+(?:,\d{3})*(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)""")
        )

        var foundAmount: Double? = null
        for (pattern in patterns) {
            pattern.findAll(t).forEach { match ->
                val numStr = match.groups[1]?.value?.replace(",", "")
                val num = numStr?.toDoubleOrNull()
                if (num != null && num > 0) {
                    foundAmount = num
                    Log.d(TAG, "💰 Amount: $foundAmount")
                    return@forEach
                }
            }
            if (foundAmount != null) break
        }

        val amount = foundAmount ?: run {
            Log.d(TAG, "❌ No amount found")
            return null
        }

        // Description extraction
        val descPatterns = listOf(" for ", " on ", " to ")
        var description: String? = null

        for (pattern in descPatterns) {
            val idx = t.indexOf(pattern)
            if (idx >= 0) {
                description = t.substring(idx + pattern.length)
                    .replace(Regex("""\b(only|today|now|rupees?|rs)\b"""), "")
                    .trim()
                    .takeIf { it.isNotEmpty() }
                if (description != null) break
            }
        }

        // Category detection
        val category = when {
            t.contains("salary") -> "Salary"
            t.contains("petrol") || t.contains("fuel") -> "Fuel"
            t.contains("rent") -> "Rent"
            t.contains("grocer") -> "Groceries"
            t.contains("food") || t.contains("lunch") || t.contains("dinner") -> "Food"
            t.contains("sale") -> "Sales"
            t.contains("purchase") -> "Purchase"
            t.contains("bill") || t.contains("electricity") -> "Utilities"
            t.contains("travel") || t.contains("cab") || t.contains("taxi") -> "Travel"
            type == "IN" -> "Income"
            else -> "Expense"
        }

        val finalDesc = (description ?: category).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        Log.d(TAG, "✅ Parsed: $finalDesc, ₹$amount, $category")

        return VoiceCommand(type, amount, finalDesc, category)
    }

    private fun addTransaction(command: VoiceCommand) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = SupabaseManager.getCurrentUser()
                val userId = user?.id

                if (userId.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
                    binding.statusText.text = "Error: Not logged in"
                    return@launch
                }

                val body = CashbookEntryInsert(
                    userId = userId,
                    amount = command.amount,
                    type = command.type,
                    paymentMethod = "CASH",
                    description = command.description,
                    category = command.category,
                    date = isoDateFormatter.format(Date())
                )

                Log.d(TAG, "Inserting: $body")

                SupabaseManager.client
                    .from("cashbook_entries")
                    .insert(body) { select() }

                binding.statusText.text = "✓ Added successfully"
                binding.transcriptionText.text = "✓ ${command.description} - ₹${command.amount}\nCategory: ${command.category}"

                Toast.makeText(requireContext(), "Transaction added!", Toast.LENGTH_SHORT).show()

                parentFragmentManager.setFragmentResult("transaction_added", Bundle())

                mainHandler.postDelayed({ dismiss() }, 2000)

            } catch (e: Exception) {
                Log.e(TAG, "Error adding transaction", e)
                binding.statusText.text = "Error: ${e.message}"
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasMicrophone(): Boolean {
        return try {
            requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        } catch (e: Exception) {
            false
        }
    }

    private fun showManualFallbackUI(show: Boolean) {
        binding.manualInputContainer.visibility = if (show) View.VISIBLE else View.GONE
        binding.manualSubmitButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            mainHandler.removeCallbacksAndMessages(null)
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanup", e)
        }
        _binding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✓ Permission granted")
                initializeSpeechRecognizer()
            } else {
                Log.d(TAG, "❌ Permission denied")
                showManualFallbackUI(true)
                Toast.makeText(requireContext(), "Using manual input mode", Toast.LENGTH_LONG).show()
            }
        }
    }

    data class VoiceCommand(
        val type: String,
        val amount: Double,
        val description: String,
        val category: String?
    )
}
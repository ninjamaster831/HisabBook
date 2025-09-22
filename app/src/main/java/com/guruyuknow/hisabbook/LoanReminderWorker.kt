package com.guruyuknow.hisabbook

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoanReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "loan_reminders"
        private const val NOTIFICATION_ID_BASE = 1001
        private const val PKG_WA = "com.whatsapp"
        private const val PKG_WA_BUSINESS = "com.whatsapp.w4b"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val loanId = inputData.getString("loanId") ?: return@withContext Result.failure()
            val friendName = inputData.getString("friend_name") ?: return@withContext Result.failure()
            val phoneNumber = inputData.getString("phone_number") ?: return@withContext Result.failure()
            val amount = inputData.getDouble("amount", 0.0)
            val notes = inputData.getString("notes") ?: ""

            val loan = LoanDatabaseManager.getLoanById(loanId)
            if (loan?.is_paid == true) return@withContext Result.success()

            createNotificationChannel()
            showReminderNotification(friendName, phoneNumber, amount, notes)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    // ---- helpers ----

    private fun isInstalled(pkg: String): Boolean {
        val pm = applicationContext.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Loan Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for loan payment reminders"
                enableVibration(true)
                enableLights(true)
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun normalizeForWhatsApp(phone: String, defaultCountryCode: String = "91"): String {
        // WhatsApp wa.me needs country code; no "+"
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 10) digits else defaultCountryCode + digits
    }

    private fun waIntentPreferPackage(phone: String, text: String, pkg: String): Intent {
        val phoneWa = normalizeForWhatsApp(phone)
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$phoneWa?text=${Uri.encode(text)}")
            // prefer the specific package; if it fails we’ll fall back to no-package
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun waIntentNoPackage(phone: String, text: String): Intent {
        val phoneWa = normalizeForWhatsApp(phone)
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$phoneWa?text=${Uri.encode(text)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun waPendingIntent(phone: String, text: String, pkg: String?, requestCode: Int): PendingIntent {
        val i = if (pkg == null) waIntentNoPackage(phone, text) else waIntentPreferPackage(phone, text, pkg)
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun smsPendingIntent(phone: String, text: String): PendingIntent {
        val smsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$phone")
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            applicationContext,
            2002,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showReminderNotification(friendName: String, phoneNumber: String, amount: Double, notes: String) {
        val message = buildString {
            append("Hi $friendName! This is a friendly reminder about the loan of ₹${"%.2f".format(amount)}.")
            if (notes.isNotEmpty()) append(" Note: $notes")
        }

        val waBusinessInstalled = isInstalled(PKG_WA_BUSINESS)
        val waInstalled = isInstalled(PKG_WA)

        val nm = NotificationManagerCompat.from(applicationContext)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (waBusinessInstalled || waInstalled) {
            // Tapping notification → straight to WhatsApp with prefilled message
            val preferredPkg = if (waBusinessInstalled) PKG_WA_BUSINESS else PKG_WA
            val contentPi = waPendingIntent(phoneNumber, message, preferredPkg, 3001)

            // action button + SMS fallback
            builder
                .setContentTitle("Send reminder on WhatsApp")
                .setContentText("Tap to message $friendName about ₹${"%.2f".format(amount)}")
                .setContentIntent(contentPi)
                .addAction(0, "Send via WhatsApp", contentPi)
                .addAction(0, "SMS", smsPendingIntent(phoneNumber, message))
        } else {
            val smsPi = smsPendingIntent(phoneNumber, message)
            builder
                .setContentTitle("WhatsApp not installed")
                .setContentText("Tap to send SMS to $friendName about ₹${"%.2f".format(amount)}")
                .setContentIntent(smsPi)
                .addAction(0, "Send SMS", smsPi)
        }

        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        if (canNotify) {
            val notifId = NOTIFICATION_ID_BASE + friendName.hashCode() + phoneNumber.hashCode()
            nm.notify(notifId, builder.build())
        }
    }
}

package com.guruyuknow.hisabbook

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class HisabBookApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SettingsManager
        SettingsManager.initialize(this)

        // Create notification channels
        createNotificationChannels()

        // Schedule auto backup if enabled
        scheduleAutoBackup()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_PAYMENT_REMINDERS,
                    "Payment Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for pending payments"
                },
                NotificationChannel(
                    CHANNEL_BILL_ALERTS,
                    "Bill Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts for new bills and invoices"
                },
                NotificationChannel(
                    CHANNEL_LOW_STOCK,
                    "Low Stock Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when items are low in stock"
                },
                NotificationChannel(
                    CHANNEL_DAILY_REPORTS,
                    "Daily Reports",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Daily business summary reports"
                },
                NotificationChannel(
                    CHANNEL_BACKUP,
                    "Backup Notifications",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Backup status notifications"
                }
            )

            val notificationManager = getSystemService(NotificationManager::class.java)
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    private fun scheduleAutoBackup() {
        if (SettingsManager.isAutoBackupEnabled()) {
            // TODO: Use WorkManager to schedule periodic backups
            // based on SettingsManager.getBackupFrequency()
        }
    }

    companion object {
        const val CHANNEL_PAYMENT_REMINDERS = "payment_reminders"
        const val CHANNEL_BILL_ALERTS = "bill_alerts"
        const val CHANNEL_LOW_STOCK = "low_stock_alerts"
        const val CHANNEL_DAILY_REPORTS = "daily_reports"
        const val CHANNEL_BACKUP = "backup_notifications"
    }
}
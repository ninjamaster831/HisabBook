package com.guruyuknow.hisabbook

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {

    fun showPaymentReminder(
        context: Context,
        partyName: String,
        amount: Double,
        dueDate: String
    ) {
        if (!SettingsManager.isPaymentRemindersEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val currencySymbol = SettingsManager.getCurrencySymbol()

        val notification = NotificationCompat.Builder(context, HisabBookApplication.CHANNEL_PAYMENT_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Payment Reminder")
            .setContentText("$partyName has payment due: $currencySymbol$amount on $dueDate")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showBillAlert(
        context: Context,
        billNumber: String,
        amount: Double
    ) {
        if (!SettingsManager.isBillAlertsEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val currencySymbol = SettingsManager.getCurrencySymbol()

        val notification = NotificationCompat.Builder(context, HisabBookApplication.CHANNEL_BILL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Bill Created")
            .setContentText("Bill #$billNumber for $currencySymbol$amount has been created")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showLowStockAlert(
        context: Context,
        itemName: String,
        currentStock: Int,
        minStock: Int
    ) {
        if (!SettingsManager.isLowStockAlertsEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HisabBookApplication.CHANNEL_LOW_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Low Stock Alert")
            .setContentText("$itemName is running low. Current: $currentStock, Min: $minStock")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showDailyReport(
        context: Context,
        totalSales: Double,
        totalExpenses: Double,
        profit: Double
    ) {
        if (!SettingsManager.isDailyReportsEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val currencySymbol = SettingsManager.getCurrencySymbol()

        val notification = NotificationCompat.Builder(context, HisabBookApplication.CHANNEL_DAILY_REPORTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Business Report")
            .setContentText("Sales: $currencySymbol$totalSales | Expenses: $currencySymbol$totalExpenses | Profit: $currencySymbol$profit")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Today's Summary:\nSales: $currencySymbol$totalSales\nExpenses: $currencySymbol$totalExpenses\nNet Profit: $currencySymbol$profit"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showBackupComplete(context: Context, success: Boolean, message: String) {
        val intent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HisabBookApplication.CHANNEL_BACKUP)
            .setSmallIcon(if (success) R.drawable.ic_check else R.drawable.ic_currency_modern)
            .setContentTitle(if (success) "Backup Complete" else "Backup Failed")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
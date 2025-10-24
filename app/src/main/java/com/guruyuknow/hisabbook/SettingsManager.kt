package com.guruyuknow.hisabbook

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Central Settings Manager for HisabBook
 * Handles all app-wide settings and preferences
 */
object SettingsManager {

    private const val PREFS_NAME = "HisabBookPrefs"

    // Keys
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_BUSINESS_NAME = "business_name"
    private const val KEY_BUSINESS_ADDRESS = "business_address"
    private const val KEY_BUSINESS_PHONE = "business_phone"
    private const val KEY_BUSINESS_EMAIL = "business_email"
    private const val KEY_GST_NUMBER = "gst_number"
    private const val KEY_PAYMENT_REMINDERS = "payment_reminders"
    private const val KEY_BILL_ALERTS = "bill_alerts"
    private const val KEY_LOW_STOCK_ALERTS = "low_stock_alerts"
    private const val KEY_DAILY_REPORTS = "daily_reports"
    private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    private const val KEY_BACKUP_FREQUENCY = "backup_frequency"
    private const val KEY_CURRENCY_SYMBOL = "currency_symbol"
    private const val KEY_DATE_FORMAT = "date_format"
    private const val KEY_FIRST_DAY_WEEK = "first_day_of_week"

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    // State flows for reactive updates
    private val _themeFlow = MutableStateFlow("System Default")
    val themeFlow: StateFlow<String> = _themeFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow("English")
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _businessInfoFlow = MutableStateFlow(BusinessInfo())
    val businessInfoFlow: StateFlow<BusinessInfo> = _businessInfoFlow.asStateFlow()

    fun initialize(context: Context) {
        if (isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        loadSettings()
        applyTheme()
        applyLanguage(context)
    }

    private fun getPrefs(context: Context? = null): SharedPreferences {
        if (prefs == null && context != null) {
            initialize(context)
        }
        return prefs ?: throw IllegalStateException("SettingsManager not initialized. Call initialize(context) first.")
    }

    private fun loadSettings() {
        val currentPrefs = prefs ?: return
        _themeFlow.value = currentPrefs.getString(KEY_THEME, "System Default") ?: "System Default"
        _languageFlow.value = currentPrefs.getString(KEY_LANGUAGE, "English") ?: "English"
        _businessInfoFlow.value = BusinessInfo(
            name = currentPrefs.getString(KEY_BUSINESS_NAME, "") ?: "",
            address = currentPrefs.getString(KEY_BUSINESS_ADDRESS, "") ?: "",
            phone = currentPrefs.getString(KEY_BUSINESS_PHONE, "") ?: "",
            email = currentPrefs.getString(KEY_BUSINESS_EMAIL, "") ?: "",
            gstNumber = currentPrefs.getString(KEY_GST_NUMBER, "") ?: ""
        )
    }

    // Theme Management
    fun setTheme(theme: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_THEME, theme).apply()
        _themeFlow.value = theme
        applyTheme()
    }

    fun getTheme(context: Context? = null): String {
        return getPrefs(context).getString(KEY_THEME, "System Default") ?: "System Default"
    }

    private fun applyTheme() {
        when (_themeFlow.value) {
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    // Language Management
    fun setLanguage(language: String, context: Context) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language).apply()
        _languageFlow.value = language
        applyLanguage(context)
    }

    fun getLanguage(context: Context? = null): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "English") ?: "English"
    }

    private fun applyLanguage(context: Context) {
        val locale = when (_languageFlow.value) {
            "Hindi" -> Locale("hi")
            "Bengali" -> Locale("bn")
            "Telugu" -> Locale("te")
            "Marathi" -> Locale("mr")
            "Tamil" -> Locale("ta")
            "Gujarati" -> Locale("gu")
            else -> Locale("en")
        }

        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    // Business Information
    fun updateBusinessInfo(info: BusinessInfo, context: Context? = null) {
        getPrefs(context).edit().apply {
            putString(KEY_BUSINESS_NAME, info.name)
            putString(KEY_BUSINESS_ADDRESS, info.address)
            putString(KEY_BUSINESS_PHONE, info.phone)
            putString(KEY_BUSINESS_EMAIL, info.email)
            putString(KEY_GST_NUMBER, info.gstNumber)
            apply()
        }
        _businessInfoFlow.value = info
    }

    fun getBusinessInfo(context: Context? = null): BusinessInfo {
        val currentPrefs = getPrefs(context)
        return BusinessInfo(
            name = currentPrefs.getString(KEY_BUSINESS_NAME, "") ?: "",
            address = currentPrefs.getString(KEY_BUSINESS_ADDRESS, "") ?: "",
            phone = currentPrefs.getString(KEY_BUSINESS_PHONE, "") ?: "",
            email = currentPrefs.getString(KEY_BUSINESS_EMAIL, "") ?: "",
            gstNumber = currentPrefs.getString(KEY_GST_NUMBER, "") ?: ""
        )
    }

    // Notification Settings
    fun setPaymentReminders(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_PAYMENT_REMINDERS, enabled).apply()
    }

    fun isPaymentRemindersEnabled(context: Context? = null): Boolean =
        getPrefs(context).getBoolean(KEY_PAYMENT_REMINDERS, true)

    fun setBillAlerts(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_BILL_ALERTS, enabled).apply()
    }

    fun isBillAlertsEnabled(context: Context? = null): Boolean =
        getPrefs(context).getBoolean(KEY_BILL_ALERTS, true)

    fun setLowStockAlerts(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_LOW_STOCK_ALERTS, enabled).apply()
    }

    fun isLowStockAlertsEnabled(context: Context? = null): Boolean =
        getPrefs(context).getBoolean(KEY_LOW_STOCK_ALERTS, true)

    fun setDailyReports(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_DAILY_REPORTS, enabled).apply()
    }

    fun isDailyReportsEnabled(context: Context? = null): Boolean =
        getPrefs(context).getBoolean(KEY_DAILY_REPORTS, false)

    // Backup Settings
    fun setAutoBackup(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    fun isAutoBackupEnabled(context: Context? = null): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_BACKUP, false)

    fun setBackupFrequency(frequency: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_BACKUP_FREQUENCY, frequency).apply()
    }

    fun getBackupFrequency(context: Context? = null): String =
        getPrefs(context).getString(KEY_BACKUP_FREQUENCY, "Weekly") ?: "Weekly"

    // Currency Settings
    fun setCurrencySymbol(symbol: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_CURRENCY_SYMBOL, symbol).apply()
    }

    fun getCurrencySymbol(context: Context? = null): String =
        getPrefs(context).getString(KEY_CURRENCY_SYMBOL, "₹") ?: "₹"

    // Date Format Settings
    fun setDateFormat(format: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_DATE_FORMAT, format).apply()
    }

    fun getDateFormat(context: Context? = null): String =
        getPrefs(context).getString(KEY_DATE_FORMAT, "DD/MM/YYYY") ?: "DD/MM/YYYY"

    // First Day of Week
    fun setFirstDayOfWeek(day: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_FIRST_DAY_WEEK, day).apply()
    }

    fun getFirstDayOfWeek(context: Context? = null): String =
        getPrefs(context).getString(KEY_FIRST_DAY_WEEK, "Monday") ?: "Monday"

    // Export all settings as JSON string for backup
    fun exportSettings(context: Context? = null): String {
        val allSettings = getPrefs(context).all
        return android.util.JsonWriter(java.io.StringWriter()).use { writer ->
            writer.beginObject()
            allSettings.forEach { (key, value) ->
                writer.name(key)
                when (value) {
                    is String -> writer.value(value)
                    is Boolean -> writer.value(value)
                    is Int -> writer.value(value)
                    is Long -> writer.value(value)
                    is Float -> writer.value(value.toDouble())
                    else -> writer.value(value.toString())
                }
            }
            writer.endObject()
            writer.toString()
        }
    }

    // Import settings from JSON string
    fun importSettings(json: String, context: Context? = null): Boolean {
        return try {
            val jsonObject = org.json.JSONObject(json)
            getPrefs(context).edit().apply {
                jsonObject.keys().forEach { key ->
                    when (val value = jsonObject.get(key)) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putFloat(key, value.toFloat())
                    }
                }
                apply()
            }
            loadSettings()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class BusinessInfo(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val gstNumber: String = ""
)
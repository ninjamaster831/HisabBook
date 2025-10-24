package com.guruyuknow.hisabbook

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Defensive initialization (important!)
        SettingsManager.initialize(newBase.applicationContext)
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure safety in preview/test environments
        SettingsManager.initialize(applicationContext)

        applyLanguageSettings()
        observeThemeChanges()
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val language = SettingsManager.getLanguage(context) // context-safe call
        val locale = when (language) {
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
        return context.createConfigurationContext(config)
    }

    private fun applyLanguageSettings() {
        val language = SettingsManager.getLanguage(this)
        val locale = when (language) {
            "Hindi" -> Locale("hi")
            "Bengali" -> Locale("bn")
            "Telugu" -> Locale("te")
            "Marathi" -> Locale("mr")
            "Tamil" -> Locale("ta")
            "Gujarati" -> Locale("gu")
            else -> Locale("en")
        }

        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun observeThemeChanges() {
        lifecycleScope.launch {
            SettingsManager.themeFlow.collectLatest { theme ->
                onThemeChanged(theme)
            }
        }
    }

    protected open fun onThemeChanged(theme: String) {}

    protected fun formatCurrency(amount: Double): String {
        val symbol = SettingsManager.getCurrencySymbol(this)
        return String.format("%s%.2f", symbol, amount)
    }

    protected fun formatDate(date: String): String {
        return date
    }
}

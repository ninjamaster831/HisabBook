package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val splashTimeOut: Long = 3000 // 3 seconds

    companion object {
        private const val TAG = "SplashActivity"
        private const val PREF_NAME = "HisabBookPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAuthenticationState()
        }, splashTimeOut)
    }

    private fun checkUserAuthenticationState() {
        Log.d(TAG, "Checking user authentication state...")

        lifecycleScope.launch {
            try {
                // First check SharedPreferences for login state
                val sharedPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                val isLoggedIn = sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
                val savedEmail = sharedPrefs.getString(KEY_USER_EMAIL, null)

                Log.d(TAG, "SharedPrefs - IsLoggedIn: $isLoggedIn, Email: $savedEmail")

                if (isLoggedIn && !savedEmail.isNullOrEmpty()) {
                    // User was previously logged in, check if session is still valid
                    val currentUser = SupabaseManager.getCurrentUser()

                    if (currentUser != null && currentUser.email == savedEmail) {
                        Log.d(TAG, "Valid session found for user: ${currentUser.email}")
                        navigateToMain()
                    } else {
                        Log.d(TAG, "Session expired or user mismatch, clearing preferences")
                        clearLoginState()
                        navigateToLogin()
                    }
                } else {
                    Log.d(TAG, "No previous login found, showing login screen")
                    navigateToLogin()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking authentication: ${e.message}")
                // On error, clear any stored state and show login
                clearLoginState()
                navigateToLogin()
            }
        }
    }

    private fun navigateToLogin() {
        Log.d(TAG, "Navigating to LoginActivity")
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        Log.d(TAG, "Navigating to MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun clearLoginState() {
        Log.d(TAG, "Clearing login state from SharedPreferences")
        val sharedPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        sharedPrefs.edit()
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }
}
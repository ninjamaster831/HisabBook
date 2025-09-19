package com.guruyuknow.hisabbook

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREF_NAME = "HisabBookPrefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"

    /**
     * Save user login state
     */
    fun saveLoginState(context: Context, email: String, userName: String) {
        Log.d(TAG, "Saving login state for user: $email")
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, userName)
            .putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Login state saved successfully")
    }

    /**
     * Clear user login state
     */
    fun clearLoginState(context: Context) {
        Log.d(TAG, "Clearing login state")
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_LOGIN_TIMESTAMP)
            .apply()
        Log.d(TAG, "Login state cleared")
    }

    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Get logged in user email
     */
    fun getLoggedInUserEmail(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            sharedPrefs.getString(KEY_USER_EMAIL, null)
        } else null
    }

    /**
     * Get logged in user name
     */
    fun getLoggedInUserName(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            sharedPrefs.getString(KEY_USER_NAME, null)
        } else null
    }

    /**
     * Get login timestamp
     */
    fun getLoginTimestamp(context: Context): Long {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getLong(KEY_LOGIN_TIMESTAMP, 0)
    }

    /**
     * Check if user login session is valid (you can add expiration logic here)
     */
    fun isSessionValid(context: Context): Boolean {
        if (!isUserLoggedIn(context)) {
            return false
        }

        val loginTime = getLoginTimestamp(context)
        val currentTime = System.currentTimeMillis()

        // Optional: Add session expiration logic
        // For example, expire session after 30 days
        val sessionDuration = 30L * 24 * 60 * 60 * 1000 // 30 days in milliseconds

        return (currentTime - loginTime) < sessionDuration
    }

    /**
     * Logout user and redirect to login
     */
    fun logout(activity: AppCompatActivity) {
        Log.d(TAG, "Logging out user...")

        // Clear SharedPreferences
        clearLoginState(activity)

        // Sign out from Google
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(activity, gso)
            googleSignInClient.signOut().addOnCompleteListener { task ->
                Log.d(TAG, "Google signout completed: ${task.isSuccessful}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out from Google: ${e.message}")
        }

        // Navigate to LoginActivity
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }

    /**
     * Get all user info as a data class
     */
    data class UserInfo(
        val email: String?,
        val name: String?,
        val loginTimestamp: Long
    )

    fun getUserInfo(context: Context): UserInfo? {
        return if (isUserLoggedIn(context)) {
            UserInfo(
                email = getLoggedInUserEmail(context),
                name = getLoggedInUserName(context),
                loginTimestamp = getLoginTimestamp(context)
            )
        } else null
    }
}
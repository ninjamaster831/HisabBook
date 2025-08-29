package com.guruyuknow.hisabbook

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.guruyuknow.hisabbook.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    companion object {
        private const val TAG = "LoginActivity"
        private const val PREF_NAME = "HisabBookPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Debug logging
        logDebugInfo()

        setupGoogleSignIn()
        setupClickListeners()

        // Check if user is already signed in
        checkExistingUser()
    }

    private fun logDebugInfo() {
        Log.d(TAG, "=== DEBUG INFO START ===")
        Log.d(TAG, "Date/Time: ${System.currentTimeMillis()}")
        Log.d(TAG, "Package name: ${packageName}")
        Log.d(TAG, "Web client ID: ${getString(R.string.default_web_client_id)}")
        Log.d(TAG, "Google Play Services available: ${GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)}")

        // Check if google-services.json exists and its size
        try {
            val inputStream = assets.open("google-services.json")
            val size = inputStream.available()
            Log.d(TAG, "google-services.json size: $size bytes")
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read google-services.json", e)
        }

        // Get current app signing info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in packageInfo.signatures!!) {
                // Get SHA1
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val current = md.digest()
                val sha1 = current.joinToString("") { "%02x".format(it) }
                Log.d(TAG, "Current SHA1: $sha1")

                // Get KeyHash for Facebook-style debugging
                val md5 = MessageDigest.getInstance("SHA")
                md5.update(signature.toByteArray())
                val hashKey = String(Base64.encode(md5.digest(), 0))
                Log.d(TAG, "KeyHash: $hashKey")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signing info", e)
        }

        // Check existing account
        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        Log.d(TAG, "Last signed in account: ${lastAccount?.email ?: "None"}")

        // Check previous error history
        val prefs = getSharedPreferences("auth_errors", MODE_PRIVATE)
        val errorCount = prefs.getInt("error_count", 0)
        val lastErrorTime = prefs.getString("last_error_time", "Never")
        val lastErrorCode = prefs.getInt("last_error_code", -1)
        Log.d(TAG, "Previous errors: $errorCount, Last: $lastErrorTime, Code: $lastErrorCode")

        Log.d(TAG, "=== DEBUG INFO END ===")
    }

    private fun setupGoogleSignIn() {
        Log.d(TAG, "Setting up Google Sign In...")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        Log.d(TAG, "GoogleSignInClient created successfully")
    }

    private fun setupClickListeners() {
        binding.googleSignInButton.setOnClickListener {
            Log.d(TAG, "Google Sign In button clicked")

            // Prevent multiple clicks
            if (binding.googleSignInButton.text == "Signing in...") {
                Log.d(TAG, "Already signing in, ignoring click")
                return@setOnClickListener
            }

            showLoading(true)
            clearAllCaches()
            signInWithGoogle()
        }

        binding.skipButton?.setOnClickListener {
            Log.d(TAG, "Skip button clicked")
            navigateToMain()
        }
    }

    private fun clearAllCaches() {
        Log.d(TAG, "Clearing all authentication caches...")

        // Clear Google Sign-In cache
        googleSignInClient.signOut().addOnCompleteListener { task ->
            Log.d(TAG, "Google sign out completed. Success: ${task.isSuccessful}")

            googleSignInClient.revokeAccess().addOnCompleteListener { revokeTask ->
                Log.d(TAG, "Google access revoked. Success: ${revokeTask.isSuccessful}")
            }
        }
    }

    private fun checkExistingUser() {
        Log.d(TAG, "Checking for existing user...")
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseManager.getCurrentUser()
                if (currentUser != null) {
                    Log.d(TAG, "User already authenticated: ${currentUser.email}")
                    navigateToMain()
                } else {
                    Log.d(TAG, "No existing user found")
                }
            } catch (e: Exception) {
                Log.d(TAG, "No existing user session: ${e.message}")
            }
        }
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "Starting Google Sign In process...")

        // Clear any existing state first
        googleSignInClient.signOut().addOnCompleteListener { task ->
            Log.d(TAG, "Pre-sign-in signout completed. Success: ${task.isSuccessful}")

            val signInIntent = googleSignInClient.signInIntent
            Log.d(TAG, "Sign in intent created, starting activity...")
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        Log.d(TAG, "Handling sign in result at: ${System.currentTimeMillis()}")

        try {
            val account = completedTask.getResult(ApiException::class.java)

            Log.d(TAG, "=== SIGN IN SUCCESS ===")
            Log.d(TAG, "Email: ${account.email}")
            Log.d(TAG, "Display Name: ${account.displayName}")
            Log.d(TAG, "ID Token present: ${account.idToken != null}")
            Log.d(TAG, "ID Token length: ${account.idToken?.length ?: 0}")
            Log.d(TAG, "Success timestamp: ${System.currentTimeMillis()}")
            Log.d(TAG, "========================")

            // Clear error history on success
            val prefs = getSharedPreferences("auth_errors", MODE_PRIVATE)
            prefs.edit().clear().apply()

            showUserInfo(account)
            authenticateWithSupabase(account)

        } catch (e: ApiException) {
            Log.e(TAG, "=== SIGN IN FAILED ===")
            Log.e(TAG, "Status Code: ${e.statusCode}")
            Log.e(TAG, "Status Message: ${e.status}")
            Log.e(TAG, "Error Message: ${e.message}")
            Log.e(TAG, "Local Message: ${e.localizedMessage}")
            Log.e(TAG, "Failure timestamp: ${System.currentTimeMillis()}")
            Log.e(TAG, "======================")

            // Save error to track pattern
            val prefs = getSharedPreferences("auth_errors", MODE_PRIVATE)
            val errorCount = prefs.getInt("error_count", 0)
            prefs.edit()
                .putInt("error_count", errorCount + 1)
                .putString("last_error_time", System.currentTimeMillis().toString())
                .putInt("last_error_code", e.statusCode)
                .apply()

            Log.d(TAG, "Total errors so far: ${errorCount + 1}")

            showLoading(false)

            when (e.statusCode) {
                10 -> {
                    Log.e(TAG, "DEVELOPER_ERROR: Check SHA fingerprints, package name, and Firebase config")
                    Toast.makeText(this, "Configuration error. Check SHA fingerprints in Firebase Console.", Toast.LENGTH_LONG).show()
                }
                12501 -> {
                    Log.d(TAG, "User cancelled sign in")
                    Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
                }
                7 -> {
                    Log.e(TAG, "NETWORK_ERROR: Check internet connection")
                    Toast.makeText(this, "Network error. Please try again", Toast.LENGTH_SHORT).show()
                }
                12502 -> {
                    Log.e(TAG, "SIGN_IN_CANCELLED: User cancelled the sign-in flow")
                    Toast.makeText(this, "Sign-in was cancelled", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.e(TAG, "Unknown error occurred")
                    Toast.makeText(this, "Sign-in failed: Code ${e.statusCode}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUserInfo(account: GoogleSignInAccount) {
        Log.d(TAG, "Showing user info for: ${account.displayName}")
        val userName = account.displayName ?: "Unknown"
        binding.welcomeTextView.text = "Hello, $userName!"
    }

    private fun authenticateWithSupabase(account: GoogleSignInAccount) {
        Log.d(TAG, "Starting Supabase authentication...")
        val idToken = account.idToken

        if (idToken != null) {
            Log.d(TAG, "ID Token available, length: ${idToken.length}")

            lifecycleScope.launch {
                try {
                    Log.d(TAG, "Calling SupabaseManager.signInWithGoogle...")
                    val result = SupabaseManager.signInWithGoogle(idToken)

                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        Log.d(TAG, "Supabase authentication successful: ${user?.email}")

                        // Save login state to SharedPreferences
                        saveLoginState(user?.email ?: account.email ?: "")

                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(this@LoginActivity,
                                "Welcome ${user?.fullName ?: user?.email}!",
                                Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        }
                    } else {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Supabase authentication failed: ${error?.message}")

                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(this@LoginActivity,
                                "Authentication failed: ${error?.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during Supabase authentication: ${e.message}")
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity,
                            "Authentication error: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Log.e(TAG, "ID Token is null!")
            showLoading(false)
            Toast.makeText(this, "Failed to get ID token", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this new method to save login state
    private fun saveLoginState(email: String) {
        Log.d(TAG, "Saving login state for user: $email")
        val sharedPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }


    private fun showLoading(show: Boolean) {
        Log.d(TAG, "showLoading: $show")
        binding.apply {
            googleSignInButton.isEnabled = !show
            googleSignInButton.text = if (show) "Signing in..." else "Sign in with Google"
        }
    }

    private fun navigateToMain() {
        Log.d(TAG, "Navigating to MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }
}
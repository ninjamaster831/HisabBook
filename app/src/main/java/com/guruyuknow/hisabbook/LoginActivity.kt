package com.guruyuknow.hisabbook

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "LoginActivity"
        private const val PREF_ERRORS = "auth_errors"
        private const val KEY_ERROR_COUNT = "error_count"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"
        private const val KEY_LAST_ERROR_CODE = "last_error_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, skip login UI
        if (SessionManager.isUserLoggedIn(this) && SessionManager.isSessionValid(this)) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logDebugInfo()
        setupGoogleSignIn()
        setupActivityResult()
        setupClickListeners()
    }

    private fun setupActivityResult() {
        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        binding.googleSignInButton.setOnClickListener {
            if (binding.googleSignInButton.text == "Signing in...") return@setOnClickListener
            showLoading(true)

            // Fresh sign-in to avoid stale account state
            googleSignInClient.signOut().addOnCompleteListener {
                signInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        binding.skipButton?.setOnClickListener {
            // Optional: allow exploring without login (remove if not desired)
            navigateToMain()
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            // Clear error history on success
            getSharedPreferences(PREF_ERRORS, MODE_PRIVATE).edit().clear().apply()

            showUserInfo(account)
            authenticateWithSupabase(account)

        } catch (e: ApiException) {
            // Save error info
            val prefs = getSharedPreferences(PREF_ERRORS, MODE_PRIVATE)
            val count = prefs.getInt(KEY_ERROR_COUNT, 0)
            prefs.edit()
                .putInt(KEY_ERROR_COUNT, count + 1)
                .putString(KEY_LAST_ERROR_TIME, System.currentTimeMillis().toString())
                .putInt(KEY_LAST_ERROR_CODE, e.statusCode)
                .apply()

            showLoading(false)
            when (e.statusCode) {
                10 -> Toast.makeText(this, "Configuration error. Check SHA + Firebase.", Toast.LENGTH_LONG).show()
                12501, 12502 -> Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
                7 -> Toast.makeText(this, "Network error. Try again.", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Sign-in failed: Code ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUserInfo(account: GoogleSignInAccount) {
        binding.welcomeTextView.text = "Hello, ${account.displayName ?: "User"}!"
    }

    private fun authenticateWithSupabase(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken.isNullOrEmpty()) {
            showLoading(false)
            Toast.makeText(this, "Failed to get ID token", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = SupabaseManager.signInWithGoogle(idToken)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    // Persist with SessionManager (single source of truth)
                    SessionManager.saveLoginState(
                        context = this@LoginActivity,
                        email = user?.email ?: account.email.orEmpty(),
                        userName = user?.fullName ?: account.displayName.orEmpty()
                    )
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(
                            this@LoginActivity,
                            "Welcome ${user?.fullName ?: user?.email ?: account.displayName ?: "User"}!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    }
                } else {
                    val err = result.exceptionOrNull()
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Authentication failed: ${err?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Authentication error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.googleSignInButton.isEnabled = !show
        binding.googleSignInButton.text = if (show) "Signing in..." else "Sign in with Google"
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // Optional diagnostics — safe to keep or remove
    private fun logDebugInfo() {
        try {
            val inputStream = assets.open("google-services.json")
            val size = inputStream.available()
            Log.d(TAG, "google-services.json size: $size bytes")
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read google-services.json", e)
        }

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in packageInfo.signatures!!) {
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val sha1 = md.digest().joinToString("") { "%02x".format(it) }
                Log.d(TAG, "Current SHA1: $sha1")

                val md5 = MessageDigest.getInstance("SHA")
                md5.update(signature.toByteArray())
                val hashKey = String(Base64.encode(md5.digest(), 0))
                Log.d(TAG, "KeyHash: $hashKey")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signing info", e)
        }

        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        Log.d(TAG, "Last signed in account: ${lastAccount?.email ?: "None"}")
        Log.d(TAG, "Play Services: ${GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)}")
    }
}

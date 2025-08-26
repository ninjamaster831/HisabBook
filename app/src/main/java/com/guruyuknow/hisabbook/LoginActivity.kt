package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.guruyuknow.hisabbook.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupClickListeners()

        // Check if user is already signed in
        checkExistingUser()
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
            showLoading(true)
            signInWithGoogle()
        }

        binding.skipButton?.setOnClickListener {
            navigateToMain()
        }
    }

    private fun checkExistingUser() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseManager.getCurrentUser()
                if (currentUser != null) {
                    Log.d(TAG, "User already authenticated: ${currentUser.email}")
                    navigateToMain()
                }
            } catch (e: Exception) {
                Log.d(TAG, "No existing user session")
            }
        }
    }

    private fun signInWithGoogle() {
        // Sign out first to force account selection
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Google Sign-In successful: ${account.email}")

            // Show user info
            showUserInfo(account)

            // Authenticate with Supabase
            authenticateWithSupabase(account)

        } catch (e: ApiException) {
            Log.w(TAG, "Google Sign-In failed with code: ${e.statusCode}")
            showLoading(false)

            when (e.statusCode) {
                12501 -> Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
                7 -> Toast.makeText(this, "Network error. Please try again", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUserInfo(account: GoogleSignInAccount) {
        val userName = account.displayName ?: "Unknown"
        binding.welcomeTextView.text = "Hello, $userName!"
    }

    private fun authenticateWithSupabase(account: GoogleSignInAccount) {
        val idToken = account.idToken

        if (idToken != null) {
            lifecycleScope.launch {
                try {
                    val result = SupabaseManager.signInWithGoogle(idToken)

                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        Log.d(TAG, "Supabase authentication successful: ${user?.email}")

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
            showLoading(false)
            Toast.makeText(this, "Failed to get ID token", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            googleSignInButton.isEnabled = !show
            googleSignInButton.text = if (show) "Signing in..." else "Sign in with Google"
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
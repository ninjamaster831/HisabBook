package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    // Adjust if you want a longer logo reveal
    private val splashTimeOut: Long = 1500 // ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            routeFromLocalSession()
        }, splashTimeOut)
    }

    private fun routeFromLocalSession() {
        // OFFLINE-FIRST: do not hit network or Supabase here
        val isLoggedIn = SessionManager.isUserLoggedIn(this)
        val isValid = SessionManager.isSessionValid(this)

        val next = if (isLoggedIn && isValid) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        // Make Splash a one-shot router
        next.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(next)
        finish()
    }
}

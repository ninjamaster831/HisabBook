package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashTimeOut: Long = 2000 // Slightly longer for animations
    private val animationDuration: Long = 1000 // Animation duration in ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Find views
        val logoImageView = findViewById<ImageView>(R.id.logoImageView)
        val taglineTextView = findViewById<TextView>(R.id.taglineTextView)

        // Set up fade-in animations
        logoImageView.animate()
            .alpha(1f)
            .setDuration(animationDuration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()


        taglineTextView.animate()
            .alpha(1f)
            .setDuration(animationDuration)
            .setStartDelay(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Route after splash timeout
        Handler(Looper.getMainLooper()).postDelayed({
            routeFromLocalSession()
        }, splashTimeOut)
    }

    private fun routeFromLocalSession() {
        val isLoggedIn = SessionManager.isUserLoggedIn(this)
        val isValid = SessionManager.isSessionValid(this)

        val next = if (isLoggedIn && isValid) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        next.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(next)
        finish()
    }
}
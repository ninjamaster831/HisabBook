package com.guruyuknow.hisabbook

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SplashActivity : AppCompatActivity() {

    private val splashTimeOut: Long = 3000
    private val animationDuration: Long = 800

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide system UI for immersive experience
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Find views
        val logoCard = findViewById<CardView>(R.id.logoCard)
        val appNameTextView = findViewById<TextView>(R.id.appNameTextView)
        val taglineTextView = findViewById<TextView>(R.id.taglineTextView)
        val loadingContainer = findViewById<LinearLayout>(R.id.loadingContainer)
        val versionTextView = findViewById<TextView>(R.id.versionTextView)
        val contentContainer = findViewById<LinearLayout>(R.id.contentContainer)
        val circle1 = findViewById<View>(R.id.circle1)
        val circle2 = findViewById<View>(R.id.circle2)

        // Animate background circles
        animateBackgroundCircles(circle1, circle2)

        // Animate logo card with scale and bounce
        Handler(Looper.getMainLooper()).postDelayed({
            animateLogoCard(logoCard)
        }, 200)

        // Fade in content container
        Handler(Looper.getMainLooper()).postDelayed({
            contentContainer.animate()
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, 300)

        // Animate app name with slide up
        Handler(Looper.getMainLooper()).postDelayed({
            animateAppName(appNameTextView)
        }, 800)

        // Animate tagline
        Handler(Looper.getMainLooper()).postDelayed({
            taglineTextView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, 1000)

        // Animate loading indicator
        Handler(Looper.getMainLooper()).postDelayed({
            animateLoadingDots(loadingContainer)
        }, 1200)

        // Fade in version
        Handler(Looper.getMainLooper()).postDelayed({
            versionTextView.animate()
                .alpha(1f)
                .setDuration(400)
                .start()
        }, 1400)

        // Route after splash timeout
        Handler(Looper.getMainLooper()).postDelayed({
            routeFromLocalSession()
        }, splashTimeOut)
    }

    private fun animateBackgroundCircles(circle1: View, circle2: View) {
        // Rotate and scale animation for circle 1
        val rotate1 = ObjectAnimator.ofFloat(circle1, View.ROTATION, 0f, 360f).apply {
            duration = 20000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scale1X = ObjectAnimator.ofFloat(circle1, View.SCALE_X, 1f, 1.2f, 1f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scale1Y = ObjectAnimator.ofFloat(circle1, View.SCALE_Y, 1f, 1.2f, 1f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Rotate and scale animation for circle 2
        val rotate2 = ObjectAnimator.ofFloat(circle2, View.ROTATION, 0f, -360f).apply {
            duration = 25000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scale2X = ObjectAnimator.ofFloat(circle2, View.SCALE_X, 1f, 1.3f, 1f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scale2Y = ObjectAnimator.ofFloat(circle2, View.SCALE_Y, 1f, 1.3f, 1f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        rotate1.start()
        scale1X.start()
        scale1Y.start()
        rotate2.start()
        scale2X.start()
        scale2Y.start()
    }

    private fun animateLogoCard(logoCard: CardView) {
        logoCard.scaleX = 0f
        logoCard.scaleY = 0f

        val scaleX = ObjectAnimator.ofFloat(logoCard, View.SCALE_X, 0f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(logoCard, View.SCALE_Y, 0f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(logoCard, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = animationDuration
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun animateAppName(appNameTextView: TextView) {
        appNameTextView.translationY = 50f

        appNameTextView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(1f))
            .start()
    }

    private fun animateLoadingDots(loadingContainer: LinearLayout) {
        loadingContainer.alpha = 0f

        loadingContainer.animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val dot3 = findViewById<View>(R.id.dot3)

        // Create bouncing animation for dots
        animateDot(dot1, 0)
        animateDot(dot2, 150)
        animateDot(dot3, 300)
    }

    private fun animateDot(dot: View, delay: Long) {
        val animator = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -20f, 0f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            startDelay = delay
            interpolator = AccelerateDecelerateInterpolator()
        }
        animator.start()
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

        // Add custom transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.guruyuknow.hisabbook.databinding.ActivityMainBinding
import kotlinx.coroutines.*

// NOTE: MainActivity extends your BaseActivity (unchanged)
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce auth gate before UI inflate
        if (!SessionManager.isUserLoggedIn(this) || !SessionManager.isSessionValid(this)) {
            Log.d(TAG, "No valid session — redirecting to LoginActivity")
            redirectToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Unify system bars + bottom nav styling
        applySystemBars()
        styleBottomNav()

        setupBottomNavigation()

        if (savedInstanceState == null) {
            showLoading()
            mainScope.launch {
                delay(1500) // replace with your initial data load if needed
                loadFragment(HomeFragment())
                binding.bottomNavigation.selectedItemId = R.id.nav_home
                hideLoading()
            }
        }
    }

    /** Status bar: teal with light icons; Nav bar: white with dark icons */
    private fun applySystemBars() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.brand_teal)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)

        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.isAppearanceLightStatusBars = false   // teal bg -> light icons
        insets.isAppearanceLightNavigationBars = true // white bg -> dark icons
    }

    /** BottomNavigationView colors (green selected, gray unselected, white bg) */
    private fun styleBottomNav() {
        binding.bottomNavigation.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.white))
            itemIconTintList = ContextCompat.getColorStateList(context, R.color.bnv_selector)
            itemTextColor   = ContextCompat.getColorStateList(context, R.color.bnv_selector)
            itemRippleColor = ContextCompat.getColorStateList(context, R.color.bnv_ripple)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragmentWithLoading(HomeFragment()); true }
                R.id.nav_chat -> { loadFragmentWithLoading(ChatFragment()); true }
                R.id.nav_add -> {
                    AddActionBottomSheet().show(supportFragmentManager, "add_action")
                    false // keep current selection
                }
                R.id.nav_statistics -> { loadFragmentWithLoading(StatisticsFragment()); true }
                R.id.nav_profile -> { loadFragmentWithLoading(ProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun loadFragmentWithLoading(fragment: Fragment) {
        showLoading()
        mainScope.launch {
            delay(300)
            loadFragment(fragment)
            hideLoading()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.bottomNavigation.visibility = View.GONE
    }

    fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
        binding.bottomNavigation.visibility = View.VISIBLE
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!SessionManager.isUserLoggedIn(this) || !SessionManager.isSessionValid(this)) {
            Log.d(TAG, "Session invalidated onResume — redirecting to login")
            redirectToLogin()
        }
    }

    fun hideBottomNav() { binding.bottomNavigation.visibility = View.GONE }
    fun showBottomNav() { binding.bottomNavigation.visibility = View.VISIBLE }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}

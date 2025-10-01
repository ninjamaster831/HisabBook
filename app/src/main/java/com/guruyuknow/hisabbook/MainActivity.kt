package com.guruyuknow.hisabbook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.guruyuknow.hisabbook.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce auth gate BEFORE inflating UI
        if (!SessionManager.isUserLoggedIn(this) || !SessionManager.isSessionValid(this)) {
            Log.d(TAG, "No valid session — redirecting to LoginActivity")
            redirectToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(HomeFragment()); true }
                R.id.nav_chat -> { loadFragment(ChatFragment()); true }
                R.id.nav_add -> {
                    // Show the add action bottom sheet
                    AddActionBottomSheet().show(supportFragmentManager, "add_action")
                    false // Keep current selection unchanged
                }
                R.id.nav_statistics -> { loadFragment(StatisticsFragment()); true }
                R.id.nav_profile -> { loadFragment(ProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Re-check session when returning to foreground
        if (!SessionManager.isUserLoggedIn(this) || !SessionManager.isSessionValid(this)) {
            Log.d(TAG, "Session invalidated onResume — redirecting to login")
            redirectToLogin()
        }
    }
}
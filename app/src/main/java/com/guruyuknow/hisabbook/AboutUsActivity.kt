package com.guruyuknow.hisabbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.guruyuknow.hisabbook.databinding.ActivityAboutUsBinding

data class TeamMember(
    val name: String,
    val role: String,
    val image: Int
)
data class AboutFeature(
    val icon: Int,
    val title: String,
    val description: String,
    val color: String
)


data class SocialLink(
    val icon: Int,
    val name: String,
    val url: String
)

class AboutUsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutUsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutUsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top)
            insets
        }
        setupToolbar()

        setupFeatures()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "About Us"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }


    private fun setupFeatures() {
        val features = listOf(
            AboutFeature(
                R.drawable.ic_bill,
                "Easy Billing",
                "Create and manage bills effortlessly with our intuitive interface",
                "#EC4899"
            ),
            AboutFeature(
                R.drawable.ic_staff,
                "Staff Management",
                "Track attendance and manage your team efficiently",
                "#8B5CF6"
            ),
            AboutFeature(
                R.drawable.ic_book,
                "Digital Cashbook",
                "Keep track of all your cash transactions in one place",
                "#6366F1"
            ),
            AboutFeature(
                R.drawable.ic_collection,
                "Smart Analytics",
                "Get insights into your business with detailed reports",
                "#10B981"
            )
        )

        val container = binding.featuresContainer
        features.forEach { feature ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_about_feature, container, false) as MaterialCardView

            val icon = card.findViewById<ImageView>(R.id.featureIcon)
            val iconBg = card.findViewById<MaterialCardView>(R.id.iconBackground)
            val title = card.findViewById<TextView>(R.id.featureTitle)
            val description = card.findViewById<TextView>(R.id.featureDescription)

            icon.setImageResource(feature.icon)
            title.text = feature.title
            description.text = feature.description

            val color = android.graphics.Color.parseColor(feature.color)
            val softColor = softPastel(color, 0.88f)
            iconBg.setCardBackgroundColor(softColor)
            androidx.core.widget.ImageViewCompat.setImageTintList(
                icon,
                android.content.res.ColorStateList.valueOf(color)
            )

            container.addView(card)
        }
    }




    private fun setupClickListeners() {
        binding.rateUsCard.setOnClickListener {
            openPlayStore()
        }

        binding.privacyPolicyCard.setOnClickListener {
            openUrl("https://hisabbook.com/privacy-policy")
        }

        binding.termsConditionsCard.setOnClickListener {
            openUrl("https://hisabbook.com/terms-conditions")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openPlayStore() {
        try {
            val packageName = packageName
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(intent)
        }
    }

    private fun softPastel(color: Int, strength: Float): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val nr = (r + (255 - r) * strength).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * strength).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * strength).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(nr, ng, nb)
    }
}
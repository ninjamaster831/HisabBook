package com.guruyuknow.hisabbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.guruyuknow.hisabbook.databinding.ActivityHelpSupportBinding

data class HelpCategory(
    val title: String,
    val icon: Int,
    val action: () -> Unit
)

data class FAQItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpSupportBinding
    private val faqList = mutableListOf<FAQItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Pad the AppBar by the status bar height; the AppBar has the gradient background
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = topInset)
            insets
        }

        setupToolbar()
        setupHelpCategories()
        setupFAQs()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Help & Support"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupHelpCategories() {
        val categories = listOf(
            HelpCategory("Contact Us", R.drawable.ic_email) {
                openEmail()
            },
            HelpCategory("WhatsApp Support", R.drawable.ic_whatsapp) {
                openWhatsApp()
            },
            HelpCategory("User Guide", R.drawable.ic_book) {
                openUserGuide()
            },
            HelpCategory("Video Tutorials", R.drawable.ic_play) {
                openVideoTutorials()
            }
        )

        val container = binding.categoriesContainer
        categories.forEach { category ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_help_category, container, false) as MaterialCardView

            card.findViewById<ImageView>(R.id.categoryIcon).setImageResource(category.icon)
            card.findViewById<TextView>(R.id.categoryTitle).text = category.title

            card.setOnClickListener { category.action() }
            container.addView(card)
        }
    }

    private fun setupFAQs() {
        faqList.addAll(
            listOf(
                FAQItem(
                    "How do I add a new bill?",
                    "Go to the Bills section from the home screen or profile menu. Tap the '+' button at the bottom right. Fill in the customer details, items, and payment information. Tap 'Save' to create the bill."
                ),
                FAQItem(
                    "How can I track staff attendance?",
                    "Navigate to the Staff section and select a staff member. You can view their attendance history and mark attendance for the current date. The system automatically tracks check-in and check-out times."
                ),
                FAQItem(
                    "How do I manage my cashbook entries?",
                    "Open the Cashbook section from your profile. Tap '+' to add a new entry. Choose between 'Cash In' or 'Cash Out', enter the amount, category, and description. Your running balance will be automatically calculated."
                ),
                FAQItem(
                    "Can I export my data?",
                    "Yes! Go to Settings from your profile page. Under 'Data Management', you'll find options to export your bills, cashbook entries, and staff records as CSV or PDF files."
                ),
                FAQItem(
                    "How do I backup my data?",
                    "Your data is automatically backed up to the cloud when you have an internet connection. You can also manually trigger a backup from Settings > Data Management > Backup Now."
                ),
                FAQItem(
                    "How can I change my business details?",
                    "Tap on your profile picture or the 'Edit Profile' button on the profile screen. You can update your business name, email, and profile picture. Don't forget to save your changes!"
                ),
                FAQItem(
                    "What should I do if I forgot my password?",
                    "On the login screen, tap 'Forgot Password'. Enter your registered email address, and we'll send you a password reset link. Follow the instructions in the email to create a new password."
                ),
                FAQItem(
                    "How do I track loans and collections?",
                    "Navigate to the Collections section from your profile. You can add new loans, track payments, view outstanding amounts, and send payment reminders to customers."
                ),
                FAQItem(
                    "Can I use the app offline?",
                    "Yes! HisabBook works offline. Your data will be stored locally and automatically synced when you reconnect to the internet."
                ),
                FAQItem(
                    "How do I generate reports?",
                    "Go to the respective section (Bills, Cashbook, etc.) and look for the 'Reports' or 'Analytics' option. You can generate daily, weekly, monthly, or custom date range reports."
                ),
                FAQItem(
                    "Is my data secure?",
                    "Absolutely! We use industry-standard encryption to protect your data. Your information is stored securely and is only accessible to you."
                ),
                FAQItem(
                    "How do I delete my account?",
                    "Go to Settings > Account > Delete Account. Please note that this action is permanent and will delete all your data. Make sure to export any important information before proceeding."
                )
            )
        )

        binding.faqRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HelpSupportActivity)
            adapter = FAQAdapter(faqList)
        }
    }

    private fun openEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@hisabbook.com")
            putExtra(Intent.EXTRA_SUBJECT, "HisabBook Support Request")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun openWhatsApp() {
        try {
            val phoneNumber = "+919876543210" // Replace with your actual WhatsApp support number
            val message = "Hi, I need help with HisabBook app"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if WhatsApp is not installed
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://hisabbook.com/support")
            }
            startActivity(intent)
        }
    }

    private fun openUserGuide() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://hisabbook.com/user-guide")
        }
        startActivity(intent)
    }

    private fun openVideoTutorials() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://youtube.com/@hisabbook") // Replace with your actual YouTube channel
        }
        startActivity(intent)
    }
}

class FAQAdapter(private val faqList: List<FAQItem>) :
    RecyclerView.Adapter<FAQAdapter.FAQViewHolder>() {

    inner class FAQViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val questionText: TextView = itemView.findViewById(R.id.questionText)
        val answerText: TextView = itemView.findViewById(R.id.answerText)
        val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        val card: MaterialCardView = itemView.findViewById(R.id.faqCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FAQViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faq, parent, false)
        return FAQViewHolder(view)
    }

    override fun onBindViewHolder(holder: FAQViewHolder, position: Int) {
        val faq = faqList[position]

        holder.questionText.text = faq.question
        holder.answerText.text = faq.answer

        // Set initial state
        holder.answerText.visibility = if (faq.isExpanded) View.VISIBLE else View.GONE
        holder.expandIcon.rotation = if (faq.isExpanded) 180f else 0f

        holder.card.setOnClickListener {
            faq.isExpanded = !faq.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = faqList.size
}
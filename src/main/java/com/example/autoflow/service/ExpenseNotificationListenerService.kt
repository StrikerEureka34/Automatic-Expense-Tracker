package com.example.autoflow.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.autoflow.firebase.FirebaseManager
import com.example.autoflow.model.Expense
import com.example.autoflow.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern

class ExpenseNotificationListenerService : NotificationListenerService() {

    private lateinit var firebaseManager: FirebaseManager
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var llmParser: LLMExpenseParser
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "ExpenseNotificationSvc"
        
        // Enhanced list of financial apps
        private val TARGET_APP_PACKAGES = listOf(
            // Indian Payment Apps
            "com.google.android.apps.nbu.paisa.user", // Google Pay (India)
            "com.phonepe.app", // PhonePe
            "net.one97.paytm", // Paytm
            "com.mobikwik_new", // MobiKwik
            "in.amazon.mShop.android.shopping", // Amazon Pay
            "in.org.npci.upiapp", // BHIM UPI
            
            // Banking Apps
            "com.sbi.lotza.upi", // SBI Pay
            "com.icicibank.pockets", // ICICI Pockets
            "com.axis.mobile", // Axis Mobile
            "com.hdfcbank.payzapp", // HDFC PayZapp
            "com.kotak.mobile", // Kotak Mobile Banking
            
            // International Payment Apps
            "com.paypal.android.p2pmobile", // PayPal
            "com.venmo", // Venmo
            "com.squareup.cash", // Cash App
            "com.zellepay.zelle", // Zelle
            
            // E-commerce with payments
            "com.amazon.mShop.android.shopping", // Amazon
            "com.flipkart.android", // Flipkart
            "in.swiggy.android", // Swiggy
            "com.application.zomato", // Zomato
            
            // Banking SMS notifications
            "com.android.mms", // SMS app
            "com.google.android.apps.messaging" // Messages app
        )
        
        // Enhanced patterns for different currencies and formats
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("(?:Rs\\.?|₹|INR)\\s*([\\d,]+\\.?\\d{0,2})"),
            Pattern.compile("([\\d,]+\\.?\\d{0,2})\\s*(?:Rs\\.?|₹|INR)"),
            Pattern.compile("USD\\s*\\$?([\\d,]+\\.?\\d{0,2})"),
            Pattern.compile("\\$([\\d,]+\\.?\\d{0,2})"),
            Pattern.compile("€([\\d,]+\\.?\\d{0,2})"),
            Pattern.compile("£([\\d,]+\\.?\\d{0,2})")
        )
        
        // Keywords that indicate expense transactions
        private val EXPENSE_KEYWORDS = listOf(
            "paid", "debited", "charged", "transaction", "purchase", "spent",
            "payment", "bill", "invoice", "receipt", "order", "booking"
        )
    }

    override fun onCreate() {
        super.onCreate()
        firebaseManager = FirebaseManager()
        expenseRepository = ExpenseRepository(this)
        llmParser = LLMExpenseParser(this)
        
        Log.d(TAG, "Expense Notification Listener Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return
        
        // Check if it's from a target app or contains expense-related content
        val isTargetApp = TARGET_APP_PACKAGES.contains(sbn.packageName)
        val notificationText = extractNotificationText(sbn)
        val containsExpenseKeywords = containsExpenseKeywords(notificationText)
        
        if (!isTargetApp && !containsExpenseKeywords) {
            return // Not relevant for expense tracking
        }

        Log.d(TAG, "Processing notification from ${sbn.packageName}: $notificationText")
        
        // Process notification asynchronously
        serviceScope.launch {
            processExpenseNotification(sbn, notificationText)
        }
    }
    
    private fun extractNotificationText(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras
        val title = extras?.getString("android.title") ?: ""
        val text = extras?.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras?.getCharSequence("android.subText")?.toString() ?: ""
        val bigText = extras?.getCharSequence("android.bigText")?.toString() ?: ""
        
        return "$title $text $subText $bigText".trim()
    }
    
    private fun containsExpenseKeywords(text: String): Boolean {
        val lowerText = text.lowercase()
        return EXPENSE_KEYWORDS.any { keyword ->
            lowerText.contains(keyword)
        } || AMOUNT_PATTERNS.any { pattern ->
            pattern.matcher(text).find()
        }
    }
    
    private suspend fun processExpenseNotification(sbn: StatusBarNotification, notificationText: String) {
        try {
            // Use LLM to parse the expense
            val expense = llmParser.parseExpenseFromText(
                rawText = notificationText,
                source = "notification"
            )
            
            if (expense != null && expense.amount > 0) {
                // Add app name to the title for context
                val appName = getAppName(sbn.packageName)
                val enhancedExpense = expense.copy(
                    title = "${expense.title} ($appName)",
                    notes = "${expense.notes}\\n\\nApp: $appName\\nPackage: ${sbn.packageName}"
                )
                
                // Save to local database
                val success = expenseRepository.addExpense(
                    expense = enhancedExpense,
                    source = "notification",
                    rawData = notificationText
                )
                
                if (success) {
                    Log.i(TAG, "Expense added from notification: ${enhancedExpense.title} - ₹${enhancedExpense.amount}")
                } else {
                    Log.e(TAG, "Failed to save expense from notification")
                }
            } else {
                Log.d(TAG, "Could not parse valid expense from notification: $notificationText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing expense notification", e)
        }
    }

    // Legacy method - kept for backward compatibility but not used
    private fun parseExpenseFromNotification(title: String?, text: String?, packageName: String): Expense? {
        if (title == null && text == null) {
            return null
        }

        val content = "${title.orEmpty()} ${text.orEmpty()}".lowercase()

        // Basic Amount Extraction using multiple patterns
        var amount: Double? = null
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                try {
                    val amountString = matcher.group(1)?.replace(",", "")
                    amount = amountString?.toDoubleOrNull()
                    if (amount != null && amount > 0) break
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing amount: ${e.message}")
                }
            }
        }

        if (amount == null || amount <= 0) {
            Log.d(TAG, "Could not parse valid amount from notification.")
            return null
        }

        var expenseTitle = "Expense from ${getAppName(packageName)}"
        if (title?.isNotBlank() == true) {
            expenseTitle = title
        } else if (text?.isNotBlank() == true) {
            expenseTitle = text.split(Regex("\\s+")).take(5).joinToString(" ")
        }

        val category = firebaseManager.categorizeExpense(content)

        return Expense(
            title = expenseTitle,
            amount = amount,
            category = category,
            timestamp = Date(),
            notes = "Auto-recorded from ${getAppName(packageName)} notification.\\nContent: $title - $text"
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
            applicationContext.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected - Enhanced with LLM parsing")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification Listener Disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing coroutines
        serviceScope.cancel()
    }
}

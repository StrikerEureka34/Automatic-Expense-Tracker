package com.example.autoflow.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.autoflow.firebase.FirebaseManager
import com.example.autoflow.model.Expense // Assuming your Expense model
import java.util.regex.Pattern

class ExpenseNotificationListener : NotificationListenerService() {

    private lateinit var firebaseManager: FirebaseManager
    private val TAG = "ExpenseNotification"

    // Regex patterns to identify expense-related information
    // These are examples and will need significant refinement and testing
    private val amountPattern = Pattern.compile("rs\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)|inr\\s*([\\d,]+(?:\\.\\d{1,2})?)|(?:\\B|\\s)([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs|inr)", Pattern.CASE_INSENSITIVE)
    private val merchantKeywords = listOf("spent at", "payment to", "order from", "charged by", "paid to")
    private val transactionKeywords = listOf("transaction", "payment", "spent", "debited", "credited", "purchase") // Added credited for potential income tracking or refunds

    override fun onCreate() {
        super.onCreate()
        firebaseManager = FirebaseManager()
        Log.d(TAG, "ExpenseNotificationListener Service Created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        if (notification == null) {
            Log.d(TAG, "Notification object is null")
            return
        }

        // Basic filtering: Ignore ongoing notifications or certain apps if needed
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            Log.d(TAG, "Ignoring ongoing notification from $packageName")
            return
        }
        // Add more app-specific filters if desired (e.g., ignore notifications from this app itself)
        // if (packageName.equals("com.example.autoflow")) return;

        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        val fullNotificationText = "$title $text $bigText".lowercase()
        Log.d(TAG, "Notification received from $packageName: Title='$title', Text='$text', BigText='$bigText'")

        // Check if notification likely contains transaction info
        if (!transactionKeywords.any { keyword -> fullNotificationText.contains(keyword) }) {
            Log.d(TAG, "Notification does not seem transaction-related.")
            return
        }

        parseExpenseFromNotification(title, text, bigText, packageName)
    }

    private fun parseExpenseFromNotification(title: String, text: String, bigText: String, packageName: String) {
        val combinedText = "$title. $text. $bigText".lowercase()
        var identifiedAmount: Double? = null
        var identifiedMerchant: String? = null

        // 1. Attempt to extract amount
        val matcher = amountPattern.matcher(combinedText)
        if (matcher.find()) {
            // Iterate through capturing groups to find the one that matched
            for (i in 1..matcher.groupCount()) {
                matcher.group(i)?.let {
                    val amountStr = it.replace(",", "")
                    identifiedAmount = amountStr.toDoubleOrNull()
                    if (identifiedAmount != null) {
                        Log.d(TAG, "Extracted amount: $identifiedAmount")
                        return@let // Exit let block once amount is found
                    }
                }
            }
        }

        if (identifiedAmount == null || identifiedAmount == 0.0) {
            Log.d(TAG, "Could not extract a valid amount from notification: $combinedText")
            return // Essential information missing
        }

        // 2. Attempt to identify merchant (very basic example)
        // More sophisticated merchant extraction would be needed
        merchantKeywords.forEach { keyword ->
            if (combinedText.contains(keyword)) {
                val startIndex = combinedText.indexOf(keyword) + keyword.length
                // Take a substring after the keyword, try to find a plausible merchant name
                var potentialMerchant = combinedText.substring(startIndex).trim().split(Regex("[\\s.,!?;]+"))[0]
                if (potentialMerchant.length > 25) potentialMerchant = potentialMerchant.substring(0, 25) // Cap length
                identifiedMerchant = potentialMerchant.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                Log.d(TAG, "Potential merchant: $identifiedMerchant based on keyword: $keyword")
                return@forEach
            }
        }
        // If no keyword match, use app name as a fallback (can be inaccurate)
        if (identifiedMerchant == null) {
            try {
                val appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                identifiedMerchant = appName
                Log.d(TAG, "Using app name as merchant: $appName")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting app name for $packageName", e)
                identifiedMerchant = "Unknown Source"
            }
        }

        // Determine a title for the expense
        val expenseTitle = title.takeIf { it.isNotBlank() } ?: text.takeIf { it.isNotBlank() }?.take(50) ?: "Expense from $identifiedMerchant"

        // 3. Create and save expense
        val category = firebaseManager.categorizeExpense(expenseTitle + " " + (identifiedMerchant ?: ""))

        val newExpense = Expense(
            title = expenseTitle,
            amount = identifiedAmount!!, // Already checked for null
            category = category,
            description = "Auto-captured from notification: $packageName. Content: $title - $text".take(200) // Truncate for safety
            // id will be generated by FirebaseManager, timestamp by default Expense constructor
        )

        firebaseManager.addExpense(newExpense) { success, error ->
            if (success) {
                Log.i(TAG, "Expense auto-added: ${newExpense.title} - ${newExpense.amount}")
                // Optionally, send a local notification confirming the expense was added
            } else {
                Log.e(TAG, "Failed to auto-add expense: $error")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optional: Handle notification removal if needed
        // Log.d(TAG, "Notification Removed: ${sbn?.notification?.extras?.getString(Notification.EXTRA_TITLE)}")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener Connected")
        // You can perform initial setup here if needed
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification Listener Disconnected")
        // Attempt to rebind or handle disconnection if necessary
    }
}

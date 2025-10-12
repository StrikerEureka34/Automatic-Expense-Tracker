package com.example.autoflow.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.example.autoflow.model.Expense
import java.util.*

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Firebase config variables (as mentioned in requirements)
    private val __firebase_config = mapOf(
        "apiKey" to "AIzaSyDemoKeyForAutoFlowExpenseTracker123456",
        "authDomain" to "autoflow-expenses.firebaseapp.com",
        "projectId" to "autoflow-expenses"
    )

    private val __app_id = "autoflow_expense_tracker"
    private val __initial_auth_token = "demo_auth_token_${System.currentTimeMillis()}"

    // Authentication functions
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(): Boolean = getCurrentUser() != null

    fun signInAnonymously(onComplete: (Boolean, String?) -> Unit) {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Add dummy data after successful sign-in
                addDummyData()
                onComplete(true, null)
            } else {
                onComplete(false, task.exception?.message)
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // Get user-specific Firestore path
    private fun getUserExpensesCollection() = firestore
        .collection("artifacts")
        .document(__app_id)
        .collection("users")
        .document(getCurrentUser()?.uid ?: "")
        .collection("expenses")

    // Add dummy data for testing
    private fun addDummyData() {
        if (!isUserLoggedIn()) return

        // Check if data already exists to avoid duplicates
        getUserExpensesCollection().get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                // Only add dummy data if no expenses exist
                createDummyExpenses()
            }
        }.addOnFailureListener {
            // If check fails, try to add dummy data anyway
            createDummyExpenses()
        }
    }

    private fun createDummyExpenses() {
        val dummyExpenses = listOf(
            Expense(
                title = "Swiggy Order #12345",
                amount = 450.75,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 86400000) // 1 day ago
            ),
            Expense(
                title = "Uber Ride to Airport",
                amount = 890.50,
                category = "Travel",
                timestamp = Date(System.currentTimeMillis() - 172800000) // 2 days ago
            ),
            Expense(
                title = "Shell Petrol Station",
                amount = 2500.00,
                category = "Fuel",
                timestamp = Date(System.currentTimeMillis() - 259200000) // 3 days ago
            ),
            Expense(
                title = "Amazon Shopping",
                amount = 1299.99,
                category = "Shopping",
                timestamp = Date(System.currentTimeMillis() - 345600000) // 4 days ago
            ),
            Expense(
                title = "Zomato Food Delivery",
                amount = 320.25,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 432000000) // 5 days ago
            ),
            Expense(
                title = "Medical Store",
                amount = 185.50,
                category = "Healthcare",
                timestamp = Date(System.currentTimeMillis() - 604800000) // 7 days ago
            ),
            Expense(
                title = "Coffee Shop",
                amount = 225.00,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 777600000) // 9 days ago
            ),
            Expense(
                title = "Flipkart Electronics",
                amount = 3499.00,
                category = "Shopping",
                timestamp = Date(System.currentTimeMillis() - 950400000) // 11 days ago
            )
        )

        // Add dummy expenses one by one with success/failure callbacks
        dummyExpenses.forEach { expense ->
            val expenseWithId = expense.copy(id = getUserExpensesCollection().document().id)
            getUserExpensesCollection()
                .document(expenseWithId.id)
                .set(expenseWithId.toMap())
                .addOnSuccessListener {
                    println("Successfully added dummy expense: ${expense.title}")
                }
                .addOnFailureListener { e ->
                    println("Error adding dummy expense: ${expense.title} - ${e.message}")
                }
        }
    }

    // Get dummy data directly (for immediate loading without Firestore)
    fun getDummyExpensesDirectly(): List<Expense> {
        return listOf(
            Expense(
                id = "dummy_1",
                title = "Swiggy Order #12345",
                amount = 450.75,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 86400000)
            ),
            Expense(
                id = "dummy_2",
                title = "Uber Ride to Airport",
                amount = 890.50,
                category = "Travel",
                timestamp = Date(System.currentTimeMillis() - 172800000)
            ),
            Expense(
                id = "dummy_3",
                title = "Shell Petrol Station",
                amount = 2500.00,
                category = "Fuel",
                timestamp = Date(System.currentTimeMillis() - 259200000)
            ),
            Expense(
                id = "dummy_4",
                title = "Amazon Shopping",
                amount = 1299.99,
                category = "Shopping",
                timestamp = Date(System.currentTimeMillis() - 345600000)
            ),
            Expense(
                id = "dummy_5",
                title = "Zomato Food Delivery",
                amount = 320.25,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 432000000)
            ),
            Expense(
                id = "dummy_6",
                title = "Medical Store",
                amount = 185.50,
                category = "Healthcare",
                timestamp = Date(System.currentTimeMillis() - 518400000)
            ),
            Expense(
                id = "dummy_7",
                title = "Grocery Shopping",
                amount = 756.30,
                category = "Grocery",
                timestamp = Date(System.currentTimeMillis() - 604800000)
            ),
            Expense(
                id = "dummy_8",
                title = "Coffee Shop",
                amount = 225.00,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 691200000)
            ),
            Expense(
                id = "dummy_9",
                title = "Flipkart Electronics",
                amount = 3499.00,
                category = "Shopping",
                timestamp = Date(System.currentTimeMillis() - 777600000)
            ),
            Expense(
                id = "dummy_10",
                title = "Ola Cab Booking",
                amount = 165.75,
                category = "Travel",
                timestamp = Date(System.currentTimeMillis() - 864000000)
            ),
            Expense(
                id = "dummy_11",
                title = "Starbucks Coffee",
                amount = 345.00,
                category = "Food",
                timestamp = Date(System.currentTimeMillis() - 950400000)
            ),
            Expense(
                id = "dummy_12",
                title = "Metro Card Recharge",
                amount = 500.00,
                category = "Travel",
                timestamp = Date(System.currentTimeMillis() - 1036800000)
            )
        )
    }

    // Expense operations
    fun addExpense(expense: Expense, onComplete: (Boolean, String?) -> Unit) {
        if (!isUserLoggedIn()) {
            onComplete(false, "User not authenticated")
            return
        }

        val expenseWithId = expense.copy(id = getUserExpensesCollection().document().id)
        getUserExpensesCollection()
            .document(expenseWithId.id)
            .set(expenseWithId.toMap())
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    fun listenToExpenses(onExpensesChanged: (List<Expense>) -> Unit): ListenerRegistration? {
        if (!isUserLoggedIn()) return null

        return getUserExpensesCollection()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    onExpensesChanged(emptyList())
                    return@addSnapshotListener
                }

                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { data -> Expense.fromMap(data, doc.id) }
                } ?: emptyList()

                onExpensesChanged(expenses)
            }
    }

    // Intelligent categorization
    fun categorizeExpense(title: String): String {
        return when {
            title.contains("swiggy", ignoreCase = true) -> "Food"
            title.contains("zomato", ignoreCase = true) -> "Food"
            title.contains("uber", ignoreCase = true) -> "Travel"
            title.contains("ola", ignoreCase = true) -> "Travel"
            title.contains("shell", ignoreCase = true) -> "Fuel"
            title.contains("petrol", ignoreCase = true) -> "Fuel"
            title.contains("amazon", ignoreCase = true) -> "Shopping"
            title.contains("flipkart", ignoreCase = true) -> "Shopping"
            title.contains("grocery", ignoreCase = true) -> "Grocery"
            title.contains("medical", ignoreCase = true) -> "Healthcare"
            title.contains("pharmacy", ignoreCase = true) -> "Healthcare"
            title.contains("coffee", ignoreCase = true) -> "Food"
            title.contains("restaurant", ignoreCase = true) -> "Food"
            title.contains("starbucks", ignoreCase = true) -> "Food"
            title.contains("metro", ignoreCase = true) -> "Travel"
            else -> "Others"
        }
    }

    // Upload receipt functionality (placeholder)
    fun uploadReceipt(onComplete: (Boolean, String?) -> Unit) {
        // This would integrate with camera/gallery and OCR in a real app
        onComplete(true, "Receipt upload feature coming soon!")
    }

    // Enhanced upload receipt functionality for FAB
    fun uploadReceiptFromCamera(onComplete: (Boolean, String?) -> Unit) {
        // This will be triggered by FAB - placeholder for camera/gallery integration
        onComplete(true, "Camera feature will be implemented here!")
    }

    fun uploadReceiptFromGallery(onComplete: (Boolean, String?) -> Unit) {
        // This will be triggered by FAB - placeholder for gallery integration
        onComplete(true, "Gallery feature will be implemented here!")
    }

    // Force load dummy data (for testing)
    fun forceDummyDataLoad(onComplete: (Boolean, String?) -> Unit) {
        if (!isUserLoggedIn()) {
            onComplete(false, "User not authenticated")
            return
        }
        createDummyExpenses()
        onComplete(true, "Dummy data loaded successfully!")
    }
}

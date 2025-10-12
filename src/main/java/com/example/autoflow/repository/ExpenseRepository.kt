package com.example.autoflow.repository

import android.content.Context
import android.util.Log
import com.example.autoflow.database.ExpenseDatabase
import com.example.autoflow.database.ExpenseEntity
import com.example.autoflow.firebase.FirebaseManager
import com.example.autoflow.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpenseRepository(private val context: Context) {
    
    private val database = ExpenseDatabase.getDatabase(context)
    private val expenseDao = database.expenseDao()
    private val firebaseManager = FirebaseManager()
    
    companion object {
        private const val TAG = "ExpenseRepository"
    }
    
    // Local database operations
    fun getAllExpenses(): Flow<List<Expense>> {
        Log.d(TAG, "Getting all expenses from database...")
        return expenseDao.getAllExpenses().map { entities ->
            Log.d(TAG, "Database returned ${entities.size} expense entities")
            entities.forEach { entity ->
                Log.v(TAG, "Entity: ${entity.title} - ₹${entity.amount} (source: ${entity.source})")
            }
            entities.map { it.toExpense() }
        }
    }
    
    fun getExpensesBySource(source: String): Flow<List<Expense>> {
        return expenseDao.getExpensesBySource(source).map { entities ->
            entities.map { it.toExpense() }
        }
    }
    
    fun getExpensesByCategory(category: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByCategory(category).map { entities ->
            entities.map { it.toExpense() }
        }
    }
    
    suspend fun addExpense(expense: Expense, source: String = "manual", rawData: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entity = ExpenseEntity.fromExpense(expense, source, rawData)
                
                Log.d(TAG, "Attempting to save expense: ${expense.title} - ₹${expense.amount} from $source")
                
                val rowId = expenseDao.insertExpense(entity)
                
                // Verify the expense was actually saved
                val verifyCount = expenseDao.getExpenseCount()
                Log.i(TAG, "Successfully saved expense to local database: ${expense.title} - ₹${expense.amount}")
                Log.i(TAG, "Database now contains $verifyCount total expenses")
                
                // Small delay to ensure database transaction is committed
                kotlinx.coroutines.delay(100)
                
                // Sync to Firebase asynchronously
                syncToFirebase(entity)
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error adding expense to local database", e)
                false
            }
        }
    }
    
    suspend fun updateExpense(expense: Expense): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entity = ExpenseEntity.fromExpense(expense)
                expenseDao.updateExpense(entity)
                
                // Sync to Firebase
                syncToFirebase(entity)
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating expense", e)
                false
            }
        }
    }
    
    suspend fun deleteExpense(expenseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                expenseDao.deleteExpense(expenseId)
                
                // TODO: Also delete from Firebase if needed
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting expense", e)
                false
            }
        }
    }
    
    suspend fun getUnprocessedExpenses(): List<Expense> {
        return withContext(Dispatchers.IO) {
            expenseDao.getUnprocessedExpenses().map { it.toExpense() }
        }
    }
    
    suspend fun markAsProcessed(expenseId: String) {
        withContext(Dispatchers.IO) {
            expenseDao.markAsProcessed(expenseId)
        }
    }
    
    suspend fun getTotalAmountByDateRange(startDate: Long, endDate: Long): Double {
        return withContext(Dispatchers.IO) {
            expenseDao.getTotalAmountByDateRange(startDate, endDate) ?: 0.0
        }
    }
    
    suspend fun getCategoryWiseTotals(startDate: Long, endDate: Long): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            expenseDao.getCategoryWiseTotals(startDate, endDate)
                .associate { it.category to it.total }
        }
    }
    
    suspend fun getExpenseCount(): Int {
        return withContext(Dispatchers.IO) {
            expenseDao.getExpenseCount()
        }
    }
    
    // Firebase sync operations
    private suspend fun syncToFirebase(entity: ExpenseEntity) {
        try {
            if (firebaseManager.isUserLoggedIn()) {
                firebaseManager.addExpense(entity.toExpense()) { success, error ->
                    if (success) {
                        // Mark as synced in local database
                        GlobalScope.launch {
                            expenseDao.markAsSynced(entity.id)
                        }
                        Log.d(TAG, "Expense synced to Firebase: ${entity.title}")
                    } else {
                        Log.e(TAG, "Failed to sync expense to Firebase: $error")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Firebase", e)
        }
    }
    
    suspend fun syncUnsyncedExpenses() {
        withContext(Dispatchers.IO) {
            try {
                val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
                for (entity in unsyncedExpenses) {
                    syncToFirebase(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing unsynced expenses", e)
            }
        }
    }
    
    // Pull expenses from Firebase and store locally
    suspend fun syncFromFirebase() {
        withContext(Dispatchers.IO) {
            try {
                // This would require modifying FirebaseManager to have a suspend function
                // For now, we'll keep the existing callback pattern
                Log.d(TAG, "Firebase sync from remote not implemented yet")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from Firebase", e)
            }
        }
    }
}

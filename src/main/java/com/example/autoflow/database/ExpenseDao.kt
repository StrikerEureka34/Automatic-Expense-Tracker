package com.example.autoflow.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isProcessed = 0")
    suspend fun getUnprocessedExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE source = :source ORDER BY timestamp DESC")
    fun getExpensesBySource(source: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY timestamp DESC")
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByDateRange(startDate: Long, endDate: Long): Double?

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE timestamp BETWEEN :startDate AND :endDate GROUP BY category")
    suspend fun getCategoryWiseTotals(startDate: Long, endDate: Long): List<CategoryTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET isProcessed = 1 WHERE id = :id")
    suspend fun markAsProcessed(id: String)

    @Query("UPDATE expenses SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int
}

data class CategoryTotal(
    val category: String,
    val total: Double
)

package com.example.autoflow.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val description: String?,
    val timestamp: Long, // Store as timestamp for Room
    val notes: String?,
    val imageUrl: String?,
    val source: String, // "notification", "camera", "gallery", "manual"
    val isProcessed: Boolean = true,
    val rawData: String? = null, // Store original notification text or OCR data
    val isSynced: Boolean = false // Track Firebase sync status
) {
    // Convert to Expense model
    fun toExpense(): com.example.autoflow.model.Expense {
        return com.example.autoflow.model.Expense(
            id = id,
            title = title,
            amount = amount,
            category = category,
            description = description,
            timestamp = Date(timestamp),
            notes = notes,
            imageUrl = imageUrl
        )
    }

    companion object {
        // Convert from Expense model
        fun fromExpense(expense: com.example.autoflow.model.Expense, source: String = "manual", rawData: String? = null): ExpenseEntity {
            return ExpenseEntity(
                id = expense.id.ifEmpty { java.util.UUID.randomUUID().toString() },
                title = expense.title,
                amount = expense.amount,
                category = expense.category,
                description = expense.description,
                timestamp = expense.timestamp.time,
                notes = expense.notes,
                imageUrl = expense.imageUrl,
                source = source,
                rawData = rawData
            )
        }
    }
}

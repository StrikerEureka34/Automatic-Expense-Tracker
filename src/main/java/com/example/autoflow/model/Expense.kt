package com.example.autoflow.model

import com.google.firebase.Timestamp
import java.util.Date // Import for java.util.Date if used elsewhere, though timestamp is Firebase Timestamp

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String? = null, // Renamed from 'notes' for consistency if needed, or keep 'notes'
    val timestamp: Date = Date(), // Changed to java.util.Date for consistency with FirstFragment, ensure Firebase handles conversion or use Timestamp
    val notes: String? = null, // Added notes field as used in FirstFragment
    val imageUrl: String? = null
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this("", "", 0.0, "", null, Date(), null, null)

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "title" to title,
            "amount" to amount,
            "category" to category,
            "description" to description,
            "timestamp" to com.google.firebase.Timestamp(timestamp), // Convert Date to Firebase Timestamp for Firestore
            "notes" to notes,
            "imageUrl" to imageUrl
            // id is excluded as it's used as the document ID
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>, documentId: String): Expense {
            return Expense(
                id = documentId,
                title = map["title"] as? String ?: "",
                amount = map["amount"] as? Double ?: 0.0,
                category = map["category"] as? String ?: "",
                description = map["description"] as? String,
                timestamp = (map["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(), // Convert Firebase Timestamp to Date
                notes = map["notes"] as? String,
                imageUrl = map["imageUrl"] as? String
            )
        }
    }
}
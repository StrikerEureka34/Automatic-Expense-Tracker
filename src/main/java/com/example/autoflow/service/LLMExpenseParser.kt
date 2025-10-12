package com.example.autoflow.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.autoflow.BuildConfig
import com.example.autoflow.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*

class LLMExpenseParser(private val context: Context) {
    
    private val openRouterClient = OpenRouterApiClient(context)

    companion object {
        private const val TAG = "LLMExpenseParser"
        private const val MAX_IMAGE_SIZE = 1024 * 1024 // 1MB max for base64 encoding
    }

    suspend fun parseExpenseFromText(
        rawText: String,
        source: String = "unknown",
        imageUrl: String? = null
    ): Expense? = withContext(Dispatchers.IO) {
        return@withContext try {
            val basicExpense = extractBasicExpense(rawText, source, imageUrl)
            
            // Try OpenRouter API first
            val openRouterResult = openRouterClient.parseExpenseFromText(rawText)
            
            if (openRouterResult != null && openRouterResult.confidence > 0.3) {
                enhanceExpenseWithOpenRouterResult(basicExpense, openRouterResult, rawText)
            } else {
                Log.w(TAG, "OpenRouter API not available or low confidence, using basic parsing")
                basicExpense
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expense from text", e)
            null
        }
    }

    suspend fun parseExpenseFromImage(
        imageUri: Uri,
        ocrText: String = "",
        source: String = "camera"
    ): Expense? = withContext(Dispatchers.IO) {
        return@withContext try {
            val basicExpense = extractBasicExpense(ocrText, source, imageUri.toString())
            
            // Convert image to base64 for OpenRouter
            val imageBase64 = convertImageToBase64(imageUri)
            
            if (imageBase64 != null) {
                val openRouterResult = openRouterClient.parseExpenseFromImage(imageBase64, ocrText)
                
                if (openRouterResult != null && openRouterResult.confidence > 0.3) {
                    enhanceExpenseWithOpenRouterResult(basicExpense, openRouterResult, ocrText)
                } else {
                    Log.w(TAG, "OpenRouter image parsing not available or low confidence")
                    basicExpense
                }
            } else {
                Log.w(TAG, "Could not convert image to base64, using OCR text only")
                if (ocrText.isNotBlank()) {
                    parseExpenseFromText(ocrText, source, imageUri.toString())
                } else {
                    basicExpense
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expense from image", e)
            null
        }
    }

    private fun extractBasicExpense(text: String, source: String, imageUrl: String?): Expense {
        // Basic regex patterns for amount extraction
        val amountPatterns = listOf(
            Regex("(?:Rs\\.?|INR|₹)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)", RegexOption.IGNORE_CASE),
            Regex("\\$([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"),
            Regex("€([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"),
            Regex("£([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"),
            Regex("\\b([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\b")
        )

        var amount = 0.0
        for (pattern in amountPatterns) {
            val match = pattern.find(text.replace("\n", " "))
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val parsedAmount = amountStr.toDoubleOrNull()
                if (parsedAmount != null && parsedAmount > 0) {
                    amount = parsedAmount
                    break
                }
            }
        }

        // Extract potential merchant/title from first meaningful line
        val title = text.lines()
            .firstOrNull { it.trim().isNotBlank() && it.trim().length > 3 }
            ?.trim()
            ?.take(50)
            ?: when (source) {
                "notification" -> "Payment Notification"
                "camera" -> "Receipt Scan"
                "gallery" -> "Gallery Image"
                else -> "Expense"
            }

        // Basic category detection
        val category = detectCategory(text)

        return Expense(
            title = title,
            amount = amount,
            category = category,
            timestamp = Date(),
            notes = "Auto-parsed from $source using OpenRouter AI\\n\\nOriginal text:\\n${text.take(500)}",
            imageUrl = imageUrl
        )
    }

    private fun enhanceExpenseWithOpenRouterResult(
        basicExpense: Expense,
        openRouterResult: ExpenseParseResult,
        originalText: String
    ): Expense {
        return basicExpense.copy(
            title = if (openRouterResult.merchant.isNotBlank() && openRouterResult.merchant != "Unknown Merchant") {
                openRouterResult.merchant
            } else {
                basicExpense.title
            },
            amount = if (openRouterResult.amount > 0) {
                openRouterResult.amount
            } else {
                basicExpense.amount
            },
            category = if (isValidCategory(openRouterResult.category)) {
                openRouterResult.category
            } else {
                basicExpense.category
            },
            description = openRouterResult.description.takeIf { it.isNotBlank() },
            notes = buildEnhancedNotes(basicExpense, openRouterResult, originalText)
        )
    }

    private fun buildEnhancedNotes(
        basicExpense: Expense,
        openRouterResult: ExpenseParseResult,
        originalText: String
    ): String {
        val notes = StringBuilder()
        notes.append("Parsed with OpenRouter AI (confidence: ${String.format("%.2f", openRouterResult.confidence)})\\n")
        
        if (openRouterResult.items.isNotEmpty()) {
            notes.append("Items: ${openRouterResult.items.joinToString(", ")}\\n")
        }
        
        if (openRouterResult.date.isNotBlank()) {
            notes.append("Receipt Date: ${openRouterResult.date}\\n")
        }
        
        notes.append("\\nOriginal text:\\n${originalText.take(300)}")
        
        return notes.toString()
    }

    private suspend fun convertImageToBase64(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Converting image to Base64: $imageUri")
            
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $imageUri")
                return@withContext null
            }
            
            // Use BitmapFactory.Options to avoid OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // Get image dimensions without loading the full image
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size to reduce memory usage
            options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
            options.inJustDecodeBounds = false
            
            // Decode the actual bitmap with reduced size
            val inputStream2 = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2?.close()
            
            if (bitmap == null) {
                Log.e(TAG, "Could not decode image from URI")
                return@withContext null
            }
            
            Log.d(TAG, "Original bitmap size: ${bitmap.width}x${bitmap.height}")
            
            // Further resize if still too large
            val resizedBitmap = resizeImageIfNeeded(bitmap)
            
            Log.d(TAG, "Resized bitmap size: ${resizedBitmap.width}x${resizedBitmap.height}")
            
            // Convert to base64 with compression
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream) // Reduced quality for smaller size
            val imageBytes = outputStream.toByteArray()
            
            Log.d(TAG, "Image bytes size: ${imageBytes.size} bytes")
            
            if (imageBytes.size > MAX_IMAGE_SIZE) {
                Log.w(TAG, "Image too large for API call: ${imageBytes.size} bytes")
                return@withContext null
            }
            
            // Clean up bitmaps to free memory
            if (bitmap != resizedBitmap) {
                bitmap.recycle()
            }
            resizedBitmap.recycle()
            
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory converting image to base64", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception accessing image", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to base64", e)
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun resizeImageIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun detectCategory(text: String): String {
        val lowerText = text.lowercase()
        
        return when {
            lowerText.contains(Regex("\\b(swiggy|zomato|food|restaurant|cafe|pizza|burger|meal|dining)\\b")) -> "Food"
            lowerText.contains(Regex("\\b(uber|ola|taxi|bus|train|travel|flight|hotel|booking)\\b")) -> "Travel"
            lowerText.contains(Regex("\\b(petrol|diesel|fuel|gas|station|shell|bp|indian oil)\\b")) -> "Fuel"
            lowerText.contains(Regex("\\b(amazon|flipkart|shopping|store|mall|purchase|myntra|ajio)\\b")) -> "Shopping"
            lowerText.contains(Regex("\\b(doctor|hospital|medical|pharmacy|medicine|health|apollo)\\b")) -> "Healthcare"
            lowerText.contains(Regex("\\b(grocery|supermarket|vegetables|fruits|milk|dmart|reliance)\\b")) -> "Grocery"
            lowerText.contains(Regex("\\b(movie|cinema|entertainment|game|music|netflix|spotify)\\b")) -> "Entertainment"
            lowerText.contains(Regex("\\b(electricity|water|internet|mobile|bill|recharge|jio|airtel)\\b")) -> "Bills"
            else -> "Others"
        }
    }

    private fun isValidCategory(category: String): Boolean {
        val validCategories = setOf(
            "Food", "Travel", "Fuel", "Shopping", "Healthcare", 
            "Grocery", "Entertainment", "Bills", "Others"
        )
        return validCategories.contains(category)
    }
}

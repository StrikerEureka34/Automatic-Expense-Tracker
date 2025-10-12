package com.example.autoflow.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autoflow.MainActivity
import com.example.autoflow.R
import com.example.autoflow.repository.ExpenseRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume

class MediaMonitoringService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var llmParser: LLMExpenseParser
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    
    private var mediaObserver: ContentObserver? = null
    
    companion object {
        private const val TAG = "MediaMonitoringService"
        private const val SERVICE_ID = 1001
        private const val CHANNEL_ID = "AutoFlowMediaMonitoring"
        private const val PROCESSING_DELAY_MS = 3000L // Wait 3 seconds before processing
        
        fun start(context: Context) {
            val intent = Intent(context, MediaMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, MediaMonitoringService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        expenseRepository = ExpenseRepository(this)
        llmParser = LLMExpenseParser(this)
        
        createNotificationChannel()
        setupMediaObserver()
        
        Log.d(TAG, "Media Monitoring Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_ID, createForegroundNotification())
        Log.i(TAG, "MediaMonitoringService started in foreground - watching for receipt images")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoFlow Media Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors camera and gallery for expense receipts"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoFlow Monitoring")
            .setContentText("Watching for expense receipts...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun setupMediaObserver() {
        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { processNewMedia(it) }
            }
        }
        
        // Monitor external images
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver!!
        )
        
        Log.d(TAG, "Media observer registered")
    }
    
    private fun processNewMedia(uri: Uri) {
        // Add delay to ensure file is fully written
        serviceScope.launch {
            delay(PROCESSING_DELAY_MS)
            
            try {
                val imageInfo = getImageInfo(uri)
                if (imageInfo != null && isPotentialReceipt(imageInfo)) {
                    Log.d(TAG, "Processing potential receipt: ${imageInfo.displayName}")
                    processReceiptImage(uri, imageInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing new media", e)
            }
        }
    }
    
    private fun getImageInfo(uri: Uri): ImageInfo? {
        return try {
            val cursor = contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE
                ),
                null, null, null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                    
                    ImageInfo(displayName, dateAdded, size, mimeType)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting image info", e)
            null
        }
    }
    
    private fun isPotentialReceipt(imageInfo: ImageInfo): Boolean {
        val now = System.currentTimeMillis() / 1000 // Convert to seconds
        val imageAge = now - imageInfo.dateAdded
        
        // Only process images added in the last 10 minutes
        if (imageAge > 600) {
            return false
        }
        
        // Check if filename suggests it might be a receipt
        val name = imageInfo.displayName.lowercase()
        val receiptKeywords = listOf(
            "receipt", "bill", "invoice", "payment", "transaction",
            "img_", "screenshot", "photo", "camera"
        )
        
        return receiptKeywords.any { keyword -> name.contains(keyword) } ||
               imageInfo.mimeType.startsWith("image/")
    }
    
    private suspend fun processReceiptImage(uri: Uri, imageInfo: ImageInfo) {
        try {
            // Extract text using OCR first
            val ocrText = extractTextFromImage(uri)
            
            Log.d(TAG, "Extracted OCR text from image: ${ocrText.take(100)}...")
            
            // Determine source based on image characteristics
            val source = determineImageSource(imageInfo)
            
            // Use LLM to parse expense from both image and OCR text
            val expense = if (ocrText.isNotBlank()) {
                // Try image-based parsing first (more accurate for receipts)
                llmParser.parseExpenseFromImage(uri, ocrText, source)
                    ?: llmParser.parseExpenseFromText(ocrText, source, uri.toString())
            } else {
                // If no OCR text, try image-only parsing
                llmParser.parseExpenseFromImage(uri, "", source)
            }
            
            Log.d(TAG, "LLM parsing result: ${expense?.let { "Title: ${it.title}, Amount: ${it.amount}, Category: ${it.category}" } ?: "null"}")
            
            if (expense != null) {
                // Save even if amount is 0 - user can edit later
                val success = expenseRepository.addExpense(
                    expense = expense,
                    source = source,
                    rawData = ocrText
                )
                
                if (success) {
                    Log.i(TAG, "Auto-processed expense from $source: ${expense.title} - ₹${expense.amount}")
                    showProcessingNotification(expense, source)
                } else {
                    Log.e(TAG, "Failed to save auto-processed expense")
                }
            } else {
                Log.d(TAG, "No valid expense found in image - LLM returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing receipt image", e)
        }
    }
    
    private suspend fun extractTextFromImage(uri: Uri): String = withContext(Dispatchers.Main) {
        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(this@MediaMonitoringService, uri)
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "OCR failed", exception)
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating InputImage", e)
                continuation.resume("")
            }
        }
    }
    
    private fun determineImageSource(imageInfo: ImageInfo): String {
        val name = imageInfo.displayName.lowercase()
        return when {
            name.contains("camera") || name.contains("img_") -> "camera"
            name.contains("screenshot") -> "gallery"
            else -> "gallery"
        }
    }
    
    private fun showProcessingNotification(expense: com.example.autoflow.model.Expense, source: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Expense Auto-Added")
            .setContentText("${expense.title} - ₹${expense.amount} from $source")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(expense.hashCode(), notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        mediaObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        
        serviceScope.cancel()
        Log.d(TAG, "Media Monitoring Service destroyed")
    }
}

data class ImageInfo(
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String
)

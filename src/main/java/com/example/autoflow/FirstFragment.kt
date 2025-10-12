package com.example.autoflow

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autoflow.adapter.ExpenseAdapter
import com.example.autoflow.databinding.FragmentFirstBinding
import com.example.autoflow.firebase.FirebaseManager
import com.example.autoflow.manager.AutoFlowManager
import com.example.autoflow.model.Expense
import com.example.autoflow.repository.ExpenseRepository
import com.example.autoflow.service.LLMExpenseParser
import com.google.firebase.firestore.ListenerRegistration
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.formatter.ValueFormatter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Job
import kotlin.coroutines.resume
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Dashboard Fragment - Main screen showing expense summary and list
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseManager: FirebaseManager
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var autoFlowManager: AutoFlowManager
    private lateinit var expenseAdapter: ExpenseAdapter
    private var expenseListener: ListenerRegistration? = null

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var tempImageUri: Uri? = null
    private lateinit var llmParser: LLMExpenseParser

    companion object { private const val TAG = "FirstFragment" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseManager = FirebaseManager()
        expenseRepository = ExpenseRepository(requireContext())
        autoFlowManager = AutoFlowManager(requireContext())
        llmParser = LLMExpenseParser(requireContext())
        
        setupRecyclerView()
        setupClickListeners()
        setupMonitoringStatusUI()

        // Initialize database and wait for it to be ready before starting UI updates
        lifecycleScope.launch {
            try {
                // First, check app startup state
                checkAppStartupState()
                
                // Ensure database is initialized by performing a simple query
                val dbReady = withContext(Dispatchers.IO) {
                    try {
                        val count = expenseRepository.getExpenseCount()
                        Log.i(TAG, "Database ready with $count expenses")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Database initialization failed", e)
                        false
                    }
                }
                
                if (dbReady) {
                    Log.i(TAG, "Database ready - starting expense listening")
                    startListeningToExpenses()
                } else {
                    Log.e(TAG, "Database not ready - retrying in 1 second")
                    delay(1000)
                    startListeningToExpenses()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during database initialization", e)
                // Still try to start listening as fallback
                startListeningToExpenses()
            }
        }
    }
    
    private suspend fun checkAppStartupState() {
        try {
            Log.i(TAG, "=== APP STARTUP STATE CHECK ===")
            Log.i(TAG, "Fragment created: ${isAdded}")
            Log.i(TAG, "Context available: ${context != null}")
            Log.i(TAG, "Binding available: ${_binding != null}")
            
            val dbCount = withContext(Dispatchers.IO) {
                try {
                    expenseRepository.getExpenseCount()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot access database during startup check", e)
                    -1
                }
            }
            Log.i(TAG, "Database expense count: $dbCount")
            
            val authStatus = firebaseManager.isUserLoggedIn()
            Log.i(TAG, "Firebase auth status: $authStatus")
            
            Log.i(TAG, "=== END STARTUP STATE CHECK ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error during startup state check", e)
        }
        
        // Check if user is authenticated for Firebase sync, but don't block local functionality
        if (!firebaseManager.isUserLoggedIn()) {
            signInUser()
        }
    }

    override fun onResume() {
        super.onResume()
        // Always restart listening when fragment comes back into view
        Log.d(TAG, "Fragment resumed - checking listening state")
        
        if (!isListening) {
            Log.i(TAG, "Not currently listening - restarting expense monitoring")
            startListeningToExpenses()
        } else {
            Log.d(TAG, "Already listening to expenses")
        }
        
        // Check notification permission status when resuming
        checkNotificationPermissionOnResume()
        
        // Also force a refresh to ensure UI is up to date
        lifecycleScope.launch {
            delay(500) // Give database time to settle
            val currentCount = withContext(Dispatchers.IO) {
                try {
                    expenseRepository.getExpenseCount()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot get expense count on resume", e)
                    0
                }
            }
            Log.i(TAG, "On resume: Database has $currentCount expenses")
        }
    }
    
    private fun checkNotificationPermissionOnResume() {
        try {
            val hasPermission = autoFlowManager.isNotificationAccessGranted()
            Log.d(TAG, "Notification access status on resume: $hasPermission")
            
            if (hasPermission) {
                // Permission was granted - start notification monitoring if not already enabled
                if (!autoFlowManager.isNotificationMonitoringEnabled()) {
                    autoFlowManager.enableNotificationMonitoring()
                    Toast.makeText(
                        context,
                        "✅ Expense monitoring enabled! AutoFlow will now detect notifications.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.i(TAG, "Notification monitoring enabled after permission grant")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification permission on resume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop Firebase listener to avoid memory leaks
        expenseListener?.remove()
        // Cancel expense collection job
        expenseCollectionJob?.cancel()
        isListening = false
        Log.d(TAG, "Fragment paused, stopped all listeners")
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(emptyList())
        binding.expensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = expenseAdapter
        }
    }

    private fun setupClickListeners() {
        // Click detection for different actions
        var clickCount = 0
        var lastClickTime = 0L
        
        binding.fabUploadReceipt.setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastClickTime < 500) { // Fast clicks
                clickCount++
            } else {
                clickCount = 1 // Reset count for slow clicks
            }
            
            when (clickCount) {
                1 -> {
                    // Delay to check for multiple clicks
                    view.postDelayed({
                        if (clickCount == 1) {
                            showReceiptInputOptions()
                        }
                        clickCount = 0
                    }, 500)
                }
                2 -> {
                    forceRefreshExpenses()
                    clickCount = 0
                }
                3 -> {
                    debugExpensePipeline()
                    clickCount = 0
                }
            }
            
            lastClickTime = currentTime
        }
        
        // Add a test expense button (for debugging)
        binding.fabUploadReceipt.setOnLongClickListener {
            addSimpleTestExpense()
            true
        }
    }
    
    private fun addSimpleTestExpense() {
        lifecycleScope.launch {
            try {
                val testExpense = Expense(
                    id = "test-${System.currentTimeMillis()}",
                    title = "Test Expense ${System.currentTimeMillis() % 1000}",
                    amount = (10..100).random().toDouble(),
                    category = listOf("Food", "Travel", "Shopping", "Fuel").random(),
                    description = "Simple test expense",
                    timestamp = Date(),
                    notes = "Added via long press for testing"
                )
                
                Log.i(TAG, "Adding simple test expense: ${testExpense.title} - ₹${testExpense.amount}")
                
                val success = withContext(Dispatchers.IO) {
                    expenseRepository.addExpense(testExpense, "manual_test", "Simple test")
                }
                
                if (success) {
                    Toast.makeText(context, "✅ Test expense added: ₹${testExpense.amount}", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "✅ Test expense successfully added and should appear in UI")
                    
                    // Force a refresh to ensure UI updates
                    delay(500)
                    forceRefreshExpenses()
                } else {
                    Toast.makeText(context, "❌ Failed to add test expense", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "❌ Failed to add test expense to database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding simple test expense", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun debugExpensePipeline() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "=== DEBUGGING EXPENSE PIPELINE ===")
                
                // 1. Check database directly
                val directCount: Int = withContext(Dispatchers.IO) {
                    expenseRepository.getExpenseCount()
                }
                Log.i(TAG, "Direct database count: $directCount")
                
                // 2. Check Flow emission
                var flowCount = 0
                val job = launch(Dispatchers.Main) {
                    expenseRepository.getAllExpenses().collect { expenses ->
                        flowCount = expenses.size
                        Log.i(TAG, "Flow emitted ${expenses.size} expenses")
                        expenses.forEachIndexed { index, expense ->
                            Log.i(TAG, "[$index] ${expense.title} - ₹${expense.amount} (${expense.timestamp})")
                        }
                    }
                }
                
                // Cancel after a short time to avoid infinite collection
                kotlinx.coroutines.delay(1000)
                job.cancel()
                
                // 3. Add a test expense and verify
                val testExpense = Expense(
                    id = "debug-${System.currentTimeMillis()}",
                    title = "Debug Expense",
                    amount = 99.99,
                    category = "Debug",
                    description = "Debug test expense",
                    timestamp = Date(),
                    notes = "Debug test"
                )
                
                val addSuccess: Boolean = withContext(Dispatchers.IO) {
                    expenseRepository.addExpense(testExpense, "debug", "debug data")
                }
                
                Log.i(TAG, "Add expense success: $addSuccess")
                
                // 4. Check count again
                val newDirectCount: Int = withContext(Dispatchers.IO) {
                    expenseRepository.getExpenseCount()
                }
                Log.i(TAG, "New direct database count: $newDirectCount")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Debug: DB=$newDirectCount, Flow=$flowCount, Added=$addSuccess", 
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                Log.i(TAG, "=== END DEBUGGING ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in debug pipeline", e)
                Toast.makeText(context, "Debug Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun forceRefreshExpenses() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Force refreshing expenses from database")
                
                expenseRepository.getAllExpenses().collect { expenses ->
                    Log.i(TAG, "Force refresh found ${expenses.size} expenses in database")
                    expenses.forEach { expense ->
                        Log.d(TAG, "DB Expense: ${expense.title} - ₹${expense.amount}")
                    }
                    
                    applyExpensesToUi(expenses)
                    Toast.makeText(context, "Refreshed: ${expenses.size} expenses found", Toast.LENGTH_SHORT).show()
                    
                    return@collect // Only process the first emission
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error force refreshing expenses", e)
                Toast.makeText(context, "Refresh failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun addTestExpense() {
        lifecycleScope.launch {
            try {
                // First, let's check current expenses count
                expenseRepository.getAllExpenses().collect { expenses ->
                    Log.d(TAG, "Current expenses in database: ${expenses.size}")
                    expenses.forEach { expense ->
                        Log.d(TAG, "Existing expense: ${expense.title} - ₹${expense.amount} (source: ${expense.id})")
                    }
                    
                    // Add test expense
                    val testExpense = Expense(
                        id = UUID.randomUUID().toString(),
                        title = "Test Receipt ${System.currentTimeMillis()}",
                        amount = 25.50,
                        category = "Food",
                        description = "Test expense from receipt",
                        timestamp = Date(),
                        notes = "Added via long press test"
                    )
                    
                    val success = withContext(Dispatchers.IO) {
                        expenseRepository.addExpense(testExpense, "manual_test", "Test raw data")
                    }
                    
                    if (success) {
                        Toast.makeText(context, "Test expense added! Total: ${expenses.size + 1}", Toast.LENGTH_LONG).show()
                        Log.i(TAG, "Test expense added: ${testExpense.title}")
                    } else {
                        Toast.makeText(context, "Failed to add test expense", Toast.LENGTH_SHORT).show()
                    }
                    
                    return@collect // Only process the first emission
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding test expense", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupMonitoringStatusUI() {
        // Update UI based on monitoring status
        val status = autoFlowManager.getMonitoringStatus()
        
        // Show notification access dialog if not granted
        if (!status.notificationPermission) {
            showNotificationAccessDialog()
        }
        
        // Check OpenRouter API key configuration
        if (!isOpenRouterConfigured()) {
            Toast.makeText(
                context,
                "Configure OpenRouter API key for enhanced AI parsing",
                Toast.LENGTH_LONG
            ).show()
        }
        
        Log.d(TAG, "Monitoring Status: $status")
    }
    
    private fun showNotificationAccessDialog() {
        if (!isAdded || context == null) return
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Enable Expense Monitoring")
            .setMessage(
                "AutoFlow can automatically detect and parse expense notifications from apps like:\n\n" +
                "• Banking apps\n" +
                "• Payment apps (Google Pay, PhonePe, etc.)\n" +
                "• Shopping apps\n" +
                "• Food delivery apps\n\n" +
                "To enable this feature, please grant notification access permission."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    autoFlowManager.openNotificationAccessSettings()
                    Toast.makeText(
                        context,
                        "Find 'AutoFlow' in the list and toggle it ON",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening notification settings", e)
                    Toast.makeText(context, "Please enable notification access in Settings > Apps", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    context,
                    "You can enable this later in Settings",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun isOpenRouterConfigured(): Boolean {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        return !apiKey.contains("PLACEHOLDER", ignoreCase = true) && 
               apiKey.isNotBlank() && 
               apiKey.startsWith("sk-or-v1-")
    }

    private fun showReceiptInputOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Load Test Data", "Monitor Settings")
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Add Expense")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> uploadReceiptFromCamera()
                1 -> uploadReceiptFromGallery()
                2 -> loadTestData()
                3 -> showMonitoringSettings()
            }
        }
        builder.show()
    }
    
    private fun showMonitoringSettings() {
        val status = autoFlowManager.getMonitoringStatus()
        val openRouterConfigured = isOpenRouterConfigured()
        
        val message = """
            AutoFlow Monitoring Status:
            
            📱 Notification Monitoring: ${if (status.notificationMonitoring) "✅ Enabled" else "❌ Disabled"}
            📸 Media Monitoring: ${if (status.mediaMonitoring) "✅ Enabled" else "❌ Disabled"}
            ☁️ Auto Sync: ${if (status.autoSync) "✅ Enabled" else "❌ Disabled"}
            🔐 Notification Permission: ${if (status.notificationPermission) "✅ Granted" else "❌ Not Granted"}
            🤖 OpenRouter API: ${if (openRouterConfigured) "✅ Configured" else "❌ Not Configured"}
            
            Model: Gemini 2.5 Flash (Free) - Optimized for text & vision
        """.trimIndent()
        
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("AutoFlow AI Monitoring")
        builder.setMessage(message)
        
        if (!status.notificationPermission) {
            builder.setPositiveButton("Grant Permission") { _, _ ->
                autoFlowManager.openNotificationAccessSettings()
            }
        }
        
        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun signInUser() {
        firebaseManager.signInAnonymously { success, error ->
            if (success) {
                Toast.makeText(context, "Welcome to AutoFlow! Firebase sync enabled.", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Firebase authentication successful - sync enabled")
            } else {
                Toast.makeText(context, "Sign in failed: $error", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Firebase authentication failed - app will work in offline mode")
            }
        }
    }

    private fun uploadReceipt() {
        firebaseManager.uploadReceipt { success, message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        try {
            Log.d(TAG, "Camera result: success=$ok, tempImageUri=$tempImageUri")
            
            if (ok && tempImageUri != null) {
                // Verify the URI is accessible via content resolver
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(tempImageUri!!)
                    if (inputStream != null) {
                        val available = inputStream.available()
                        inputStream.close()
                        Log.d(TAG, "Image URI verified: $available bytes available")
                        processReceiptImage(tempImageUri!!, "camera")
                    } else {
                        Log.e(TAG, "Cannot open input stream for camera image")
                        Toast.makeText(context, "Camera image not accessible", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying camera image", e)
                    Toast.makeText(context, "Camera image verification failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                val reason = when {
                    !ok -> "Camera capture cancelled or failed"
                    tempImageUri == null -> "No image file created"
                    else -> "Unknown error"
                }
                Log.w(TAG, "Camera capture failed: $reason")
                Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling camera result", e)
            Toast.makeText(context, "Camera processing error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processReceiptImage(uri, "gallery") else Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    // === Receipt Capture ===
    private fun uploadReceiptFromCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        try {
            if (!isAdded || context == null) {
                Log.e(TAG, "Fragment not attached, cannot launch camera")
                return
            }
            
            val imagesDir = File(requireContext().cacheDir, "images").apply { 
                if (!exists()) {
                    val created = mkdirs()
                    Log.d(TAG, "Created images directory: $created")
                }
            }
            
            val file = File.createTempFile("receipt_", ".jpg", imagesDir)
            tempImageUri = FileProvider.getUriForFile(
                requireContext(), 
                "${requireContext().packageName}.fileprovider", 
                file
            )
            
            Log.d(TAG, "Created temp image URI: $tempImageUri")
            
            if (tempImageUri != null) {
                cameraLauncher.launch(tempImageUri)
            } else {
                Toast.makeText(context, "Failed to create image file", Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Camera file creation error", e)
            Toast.makeText(context, "Camera setup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider configuration error", e)
            Toast.makeText(context, "Camera configuration error", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected camera launch error", e)
            Toast.makeText(context, "Camera launch failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadReceiptFromGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processReceiptImage(uri: Uri, source: String = "manual") {
        if (!isAdded || context == null) {
            Log.e(TAG, "Fragment not attached, cannot process image")
            return
        }
        
        Toast.makeText(context, "Processing receipt with AI...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Processing image URI: $uri from source: $source")
                
                // Check if URI is accessible
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot access image file", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                inputStream.close()
                
                // Process with OCR in background thread
                val ocrText = withContext(Dispatchers.IO) {
                    try {
                        val img = InputImage.fromFilePath(requireContext(), uri)
                        extractTextFromImage(img)
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR processing failed", e)
                        ""
                    }
                }
                
                Log.d(TAG, "OCR extracted: ${ocrText.take(100)}...")
                
                // Process expense in background
                buildExpenseFromImage(uri, ocrText, source)
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception accessing image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Permission denied to access image", Toast.LENGTH_LONG).show()
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory processing image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Image too large to process", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing receipt image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Processing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private suspend fun extractTextFromImage(image: InputImage): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed", e)
                        continuation.resume("")
                    }
            }
        }
    }

    private suspend fun buildExpenseFromImage(imageUri: Uri, ocrText: String, source: String = "manual") {
        try {
            Log.d(TAG, "Building expense from image with OCR text length: ${ocrText.length}, source: $source")
            
            // Use the new image parsing method
            val finalExpense = withContext(Dispatchers.IO) {
                if (ocrText.isNotBlank()) {
                    // Try image + OCR text parsing first
                    llmParser.parseExpenseFromImage(imageUri, ocrText, source)
                        ?: llmParser.parseExpenseFromText(ocrText, source, imageUri.toString())
                } else {
                    // Try image-only parsing
                    llmParser.parseExpenseFromImage(imageUri, "", source)
                }
            }
            
            withContext(Dispatchers.Main) {
                if (!isAdded || context == null) {
                    Log.w(TAG, "Fragment no longer attached, skipping UI update")
                    return@withContext
                }
                
                if (finalExpense != null) {
                    Log.d(TAG, "Successfully parsed expense: ${finalExpense.title} - ₹${finalExpense.amount} from $source")
                    
                    val success = withContext(Dispatchers.IO) {
                        expenseRepository.addExpense(
                            expense = finalExpense,
                            source = source,
                            rawData = ocrText
                        )
                    }
                    
                    if (success) {
                        Toast.makeText(context, "Receipt processed and saved!", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "Expense successfully saved to database from $source")
                    } else {
                        Toast.makeText(context, "Failed to save receipt", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Failed to save expense to database from $source")
                    }
                } else {
                    Log.w(TAG, "Could not parse expense from image (source: $source)")
                    Toast.makeText(context, "Could not extract expense from image", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory building expense", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Image too large to process", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error building expense from image", e)
            withContext(Dispatchers.Main) {
                if (isAdded && context != null) {
                    Toast.makeText(context, "Processing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadTestData() {
        try {
            val dummyData = firebaseManager.getDummyExpensesDirectly()
            applyExpensesToUi(dummyData)
            firebaseManager.forceDummyDataLoad { success, message ->
                Toast.makeText(context, message ?: "", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadTestData crash", e)
            Toast.makeText(context, "Failed to load test data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyExpensesToUi(expenses: List<Expense>) {
        if (!isAdded || _binding == null) {
            Log.w(TAG, "Cannot update UI - fragment not added or binding is null")
            return
        }
        
        Log.d(TAG, "Applying ${expenses.size} expenses to UI")
        expenses.forEach { expense ->
            Log.d(TAG, "Expense: ${expense.title} - ₹${expense.amount} (${expense.category})")
        }
        
        expenseAdapter.updateExpenses(expenses)
        updateDashboardSummary(expenses)
        updatePieChart(expenses)
        updateBarChart(expenses)
        updateLineChart(expenses)
    }

    private var isListening = false
    private var expenseCollectionJob: kotlinx.coroutines.Job? = null
    
    private fun startListeningToExpenses() {
        if (isListening) {
            Log.d(TAG, "Already listening to expenses, skipping")
            return
        }
        
        isListening = true
        
        // Cancel any existing collection job
        expenseCollectionJob?.cancel()
        
        // Listen to local database changes for real-time UI updates (always works)
        expenseCollectionJob = lifecycleScope.launch {
            try {
                Log.i(TAG, "Starting to listen to local database expenses...")
                
                expenseRepository.getAllExpenses().collect { expenses ->
                    try { 
                        Log.i(TAG, "✅ Received ${expenses.size} expenses from local database")
                        expenses.forEach { expense ->
                            Log.v(TAG, "  - ${expense.title}: ₹${expense.amount}")
                        }
                        
                        withContext(Dispatchers.Main) {
                            applyExpensesToUi(expenses)
                        }
                    } catch (e: Exception) { 
                        Log.e(TAG, "Error applying expenses to UI", e) 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in expense collection", e)
                isListening = false
            }
        }
        
        // Firebase listener only if authenticated
        if (firebaseManager.isUserLoggedIn()) {
            expenseListener = firebaseManager.listenToExpenses { expenses ->
                try { 
                    Log.d(TAG, "Received ${expenses.size} expenses from Firebase")
                    // Don't override local data with Firebase data automatically
                    // applyExpensesToUi(expenses) 
                } catch (e: Exception) { 
                    Log.e(TAG, "Firebase realtime update crash", e) 
                }
            }
        } else {
            Log.d(TAG, "Firebase not authenticated - using local data only")
        }
    }

    // Replace date parsing logic with epoch-based grouping to avoid parse crashes
    private fun buildDailyTotals(expenses: List<Expense>): List<Pair<String, Double>> {
        if (expenses.isEmpty()) return emptyList()
        val cal = Calendar.getInstance()
        val map = mutableMapOf<Long, Double>()
        expenses.forEach { exp ->
            cal.time = exp.timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            map[dayStart] = (map[dayStart] ?: 0.0) + exp.amount
        }
        val dateFormatLabel = SimpleDateFormat("dd/MM", Locale.getDefault())
        return map.entries
            .sortedBy { it.key }
            .map { dateFormatLabel.format(Date(it.key)) to it.value }
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun updatePieChart(expenses: List<Expense>) {
        try {
            if (!isAdded || _binding == null) return
            val chart = binding.categoryPieChart
            chart.visibility = View.VISIBLE
            if (expenses.isEmpty()) { chart.clear(); chart.centerText = "No data"; chart.invalidate(); return }

            val dark = isDarkMode()
            val labelColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            val valueColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

            // Straightforward category totals (no merging):
            val totals = expenses.groupBy { it.category.ifBlank { "Uncategorized" } }
                .mapValues { it.value.sumOf { e -> e.amount } }
                .filter { it.value > 0.0 }
            if (totals.isEmpty()) { chart.clear(); chart.centerText = "No positive values"; chart.invalidate(); return }

            val totalSum = totals.values.sum().coerceAtLeast(0.0001)
            val entries = totals.map { (cat, amt) -> PieEntry(amt.toFloat(), cat) }

            val dataSet = PieDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                sliceSpace = 1.5f
                valueTextSize = 11f
                valueTextColor = valueColor
                yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
                xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
                valueLinePart1Length = 0.3f
                valueLinePart2Length = 0.4f
                valueLinePart1OffsetPercentage = 80f
                valueLineColor = labelColor
                isUsingSliceColorAsValueLineColor = false
            }

            val formatter = object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    if (pieEntry == null) return ""
                    val pct = (pieEntry.value / totalSum.toFloat()) * 100f
                    return if (pct < 3f) "" else "${pieEntry.label} ${String.format(Locale.getDefault(), "%.1f%%", pct)}"
                }
            }

            val pieData = PieData(dataSet).apply { setValueFormatter(formatter); setValueTextColor(valueColor) }

            chart.apply {
                data = pieData
                setUsePercentValues(false)
                setDrawHoleEnabled(true)
                setHoleRadius(24f)
                setTransparentCircleRadius(28f)
                setEntryLabelColor(android.graphics.Color.TRANSPARENT)
                description.isEnabled = false
                legend.apply {
                    isEnabled = true
                    textColor = labelColor
                    textSize = 11f
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    yEntrySpace = 4f
                    xEntrySpace = 6f
                    isWordWrapEnabled = true
                }
                centerText = "Spending\n${totals.size} cats"
                setCenterTextColor(labelColor)
                setCenterTextSize(11f)
                setExtraOffsets(4f,4f,4f,4f)
                highlightValues(null)
                animateY(500)
                invalidate()
            }
            Log.d(TAG, "Pie chart updated: entries=${entries.size} total=$totalSum")
        } catch (e: Exception) {
            Log.e(TAG, "updatePieChart fatal", e)
            if (isAdded && _binding != null) {
                binding.categoryPieChart.clear()
                binding.categoryPieChart.centerText = "Chart error"
                binding.categoryPieChart.invalidate()
            }
        }
    }

    private fun updateBarChart(expenses: List<Expense>) {
        try {
            if (!isAdded || _binding == null) return
            if (expenses.isEmpty()) { binding.categoryBarChart.clear(); binding.categoryBarChart.invalidate(); return }
            val dark = isDarkMode()
            val axisColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            val valueColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val categoryTotals = expenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.toList().sortedByDescending { it.second }
            val entries = categoryTotals.mapIndexed { idx, (c,t) -> BarEntry(idx.toFloat(), t.toFloat()) }
            val dataSet = BarDataSet(entries, "Category Spending").apply {
                colors = ColorTemplate.COLORFUL_COLORS.toList()
                valueTextSize = 11f
                valueTextColor = valueColor
            }
            binding.categoryBarChart.apply {
                data = BarData(dataSet)
                setNoDataText("No expenses")
                setNoDataTextColor(axisColor)
                description.isEnabled = false
                xAxis.apply {
                    valueFormatter = object : ValueFormatter() { override fun getFormattedValue(value: Float) = categoryTotals.getOrNull(value.toInt())?.first ?: "" }
                    granularity = 1f
                    textColor = axisColor
                    setDrawGridLines(false)
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                }
                axisLeft.apply { textColor = axisColor; setDrawGridLines(false) }
                axisRight.isEnabled = false
                legend.textColor = axisColor
                setPinchZoom(false); setScaleEnabled(false)
                animateY(600)
                invalidate()
            }
        } catch (e: Exception) { Log.e(TAG, "updateBarChart crash", e) }
    }

    private fun updateLineChart(expenses: List<Expense>) {
        try {
            if (!isAdded || _binding == null) return
            if (expenses.isEmpty()) { binding.spendingTrendChart.clear(); binding.spendingTrendChart.invalidate(); return }
            val dark = isDarkMode()
            val axisColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            val valueColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val dailyTotals = buildDailyTotals(expenses) // list of (label, amount)
            val entries = dailyTotals.mapIndexed { idx, pair -> Entry(idx.toFloat(), pair.second.toFloat()) }
            val dataSet = LineDataSet(entries, "Daily Spending Trend").apply {
                color = ColorTemplate.getHoloBlue()
                setCircleColor(ColorTemplate.getHoloBlue())
                lineWidth = 3f
                circleRadius = 4f
                setDrawCircleHole(false)
                valueTextSize = 10f
                valueTextColor = valueColor
                setDrawFilled(true)
                fillColor = ColorTemplate.getHoloBlue()
            }
            binding.spendingTrendChart.apply {
                data = LineData(dataSet)
                setNoDataText("No expenses")
                setNoDataTextColor(axisColor)
                description.isEnabled = false
                legend.textColor = axisColor
                xAxis.apply {
                    valueFormatter = object : ValueFormatter() { override fun getFormattedValue(value: Float) = dailyTotals.getOrNull(value.toInt())?.first ?: "" }
                    granularity = 1f
                    textColor = axisColor
                    setDrawGridLines(false)
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                }
                axisLeft.apply { textColor = axisColor; setDrawGridLines(false) }
                axisRight.isEnabled = false
                setPinchZoom(false); setScaleEnabled(false)
                animateX(600)
                invalidate()
            }
        } catch (e: Exception) { Log.e(TAG, "updateLineChart crash", e) }
    }

    // Re-added: summarizes totals, monthly total, and daily average
    private fun updateDashboardSummary(expenses: List<Expense>) {
        if (!isAdded || _binding == null) return
        val totalAmount = expenses.sumOf { it.amount }
        val expenseCount = expenses.size
        binding.totalAmountText.text = "₹${String.format("%.2f", totalAmount)}"
        binding.expenseCountText.text = "$expenseCount expenses"
        // Current month filter
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val monthlyTotal = expenses.filter { exp ->
            cal.time = exp.timestamp
            cal.get(Calendar.MONTH) == currentMonth
        }.sumOf { it.amount }
        binding.monthlyTotalText.text = "This month: ₹${String.format("%.2f", monthlyTotal)}"
        // Daily average since first expense
        val daysSpan = if (expenses.isNotEmpty()) {
            val first = expenses.minByOrNull { it.timestamp.time }!!.timestamp.time
            val diffDays = ((System.currentTimeMillis() - first) / (1000L * 60L * 60L * 24L)).toInt().coerceAtLeast(1)
            diffDays
        } else 1
        val dailyAvg = totalAmount / daysSpan.toDouble()
        binding.dailyAverageText.text = "Daily avg: ₹${String.format("%.2f", dailyAvg)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up all resources
        expenseListener?.remove()
        expenseCollectionJob?.cancel()
        isListening = false
        _binding = null
        Log.d(TAG, "View destroyed, cleaned up all resources")
    }
}

package com.example.autoflow.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.autoflow.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ExpenseSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val expenseRepository = ExpenseRepository(context)

    companion object {
        private const val TAG = "ExpenseSyncWorker"
        const val WORK_NAME = "expense_sync_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncWork = PeriodicWorkRequestBuilder<ExpenseSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncWork
                )

            Log.d(TAG, "Expense sync work scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Expense sync work cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting expense sync work")

            // Process any unprocessed expenses
            processUnprocessedExpenses()

            // Sync unsynced expenses to Firebase
            syncUnsyncedExpenses()

            Log.d(TAG, "Expense sync work completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Expense sync work failed", e)
            Result.retry()
        }
    }

    private suspend fun processUnprocessedExpenses() {
        try {
            val unprocessedExpenses = expenseRepository.getUnprocessedExpenses()
            Log.d(TAG, "Found ${unprocessedExpenses.size} unprocessed expenses")

            for (expense in unprocessedExpenses) {
                // Mark as processed (this could be enhanced with additional processing logic)
                expenseRepository.markAsProcessed(expense.id)
                Log.d(TAG, "Marked expense as processed: ${expense.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing unprocessed expenses", e)
            throw e
        }
    }

    private suspend fun syncUnsyncedExpenses() {
        try {
            expenseRepository.syncUnsyncedExpenses()
            Log.d(TAG, "Synced unsynced expenses to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing expenses to Firebase", e)
            throw e
        }
    }
}

class ExpenseProcessingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ExpenseProcessingWorker"
        const val NOTIFICATION_TEXT_KEY = "notification_text"
        const val SOURCE_KEY = "source"
        const val IMAGE_URI_KEY = "image_uri"

        fun enqueueNotificationProcessing(
            context: Context,
            notificationText: String,
            source: String = "notification"
        ) {
            val workData = workDataOf(
                NOTIFICATION_TEXT_KEY to notificationText,
                SOURCE_KEY to source
            )

            val work = OneTimeWorkRequestBuilder<ExpenseProcessingWorker>()
                .setInputData(workData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(work)
        }

        fun enqueueImageProcessing(
            context: Context,
            imageUri: String,
            source: String = "camera"
        ) {
            val workData = workDataOf(
                IMAGE_URI_KEY to imageUri,
                SOURCE_KEY to source
            )

            val work = OneTimeWorkRequestBuilder<ExpenseProcessingWorker>()
                .setInputData(workData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(work)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val notificationText = inputData.getString(NOTIFICATION_TEXT_KEY)
            val imageUri = inputData.getString(IMAGE_URI_KEY)
            val source = inputData.getString(SOURCE_KEY) ?: "unknown"

            when {
                notificationText != null -> processNotificationText(notificationText, source)
                imageUri != null -> processImageUri(imageUri, source)
                else -> {
                    Log.w(TAG, "No valid input data for processing")
                    return@withContext Result.failure()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Expense processing work failed", e)
            Result.retry()
        }
    }

    private suspend fun processNotificationText(text: String, source: String) {
        val llmParser = LLMExpenseParser(context)
        val expenseRepository = ExpenseRepository(context)

        val expense = llmParser.parseExpenseFromText(text, source)
        if (expense != null && expense.amount > 0) {
            expenseRepository.addExpense(expense, source, text)
            Log.i(TAG, "Processed expense from $source: ${expense.title}")
        }
    }

    private suspend fun processImageUri(uri: String, source: String) {
        // This would involve OCR processing and LLM parsing
        // Implementation similar to MediaMonitoringService
        Log.d(TAG, "Processing image URI: $uri from $source")
        // TODO: Implement image processing logic
    }
}

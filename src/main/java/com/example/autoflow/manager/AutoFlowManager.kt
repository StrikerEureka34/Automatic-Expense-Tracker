package com.example.autoflow.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.autoflow.service.ExpenseSyncWorker
import com.example.autoflow.service.MediaMonitoringService

class AutoFlowManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoFlowManager"
        
        // SharedPreferences keys
        private const val PREFS_NAME = "autoflow_settings"
        private const val KEY_NOTIFICATION_MONITORING_ENABLED = "notification_monitoring_enabled"
        private const val KEY_MEDIA_MONITORING_ENABLED = "media_monitoring_enabled"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Initialize the AutoFlow pipeline
    fun initializePipeline() {
        Log.d(TAG, "Initializing AutoFlow expense monitoring pipeline")
        
        // Check and request necessary permissions
        checkPermissions()
        
        // Start services based on user preferences
        if (isNotificationMonitoringEnabled()) {
            enableNotificationMonitoring()
        }
        
        if (isMediaMonitoringEnabled()) {
            enableMediaMonitoring()
        }
        
        if (isAutoSyncEnabled()) {
            enableAutoSync()
        }
    }
    
    private fun checkPermissions() {
        // Check notification listener permission
        if (!isNotificationAccessGranted()) {
            Log.w(TAG, "Notification access not granted")
        }
        
        // Check media permissions
        val hasMediaPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasMediaPermission) {
            Log.w(TAG, "Media access permission not granted")
        }
    }
    
    // Notification monitoring
    fun enableNotificationMonitoring() {
        if (isNotificationAccessGranted()) {
            setNotificationMonitoringEnabled(true)
            Log.d(TAG, "Notification monitoring enabled")
        } else {
            Log.w(TAG, "Cannot enable notification monitoring: permission not granted")
        }
    }
    
    fun disableNotificationMonitoring() {
        setNotificationMonitoringEnabled(false)
        Log.d(TAG, "Notification monitoring disabled")
    }
    
    fun isNotificationMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_MONITORING_ENABLED, true) && 
               isNotificationAccessGranted()
    }
    
    private fun setNotificationMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_MONITORING_ENABLED, enabled).apply()
    }
    
    // Media monitoring
    fun enableMediaMonitoring() {
        MediaMonitoringService.start(context)
        setMediaMonitoringEnabled(true)
        Log.d(TAG, "Media monitoring enabled")
    }
    
    fun disableMediaMonitoring() {
        MediaMonitoringService.stop(context)
        setMediaMonitoringEnabled(false)
        Log.d(TAG, "Media monitoring disabled")
    }
    
    fun isMediaMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MEDIA_MONITORING_ENABLED, true)
    }
    
    private fun setMediaMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_MONITORING_ENABLED, enabled).apply()
    }
    
    // Auto-sync
    fun enableAutoSync() {
        ExpenseSyncWorker.schedule(context)
        setAutoSyncEnabled(true)
        Log.d(TAG, "Auto-sync enabled")
    }
    
    fun disableAutoSync() {
        ExpenseSyncWorker.cancel(context)
        setAutoSyncEnabled(false)
        Log.d(TAG, "Auto-sync disabled")
    }
    
    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    }
    
    private fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }
    
    // Utility methods
    fun isNotificationAccessGranted(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(packageName)
    }
    
    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    fun getMonitoringStatus(): MonitoringStatus {
        return MonitoringStatus(
            notificationMonitoring = isNotificationMonitoringEnabled(),
            mediaMonitoring = isMediaMonitoringEnabled(),
            autoSync = isAutoSyncEnabled(),
            notificationPermission = isNotificationAccessGranted()
        )
    }
    
    fun shutdown() {
        Log.d(TAG, "Shutting down AutoFlow pipeline")
        disableMediaMonitoring()
        disableAutoSync()
    }
}

data class MonitoringStatus(
    val notificationMonitoring: Boolean,
    val mediaMonitoring: Boolean,
    val autoSync: Boolean,
    val notificationPermission: Boolean
)

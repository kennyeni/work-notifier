package com.worknotifier.app.utils

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/**
 * Manages DND and Bedtime mode state when connecting/disconnecting from Android Auto.
 *
 * When connecting to Android Auto:
 * - Saves current DND and Bedtime mode states
 * - Disables both modes to ensure notifications work
 *
 * When disconnecting from Android Auto:
 * - Restores DND and Bedtime mode to their previous states
 *
 * Requires:
 * - ACCESS_NOTIFICATION_POLICY permission for DND control
 * - WRITE_SECURE_SETTINGS permission for Bedtime mode control (grant via ADB)
 */
object AutoModeManager {

    private const val TAG = "AutoModeManager"
    private const val PREFS_NAME = "auto_mode_manager"
    private const val KEY_DND_WAS_ACTIVE = "dnd_was_active"
    private const val KEY_BEDTIME_WAS_ACTIVE = "bedtime_was_active"

    // Bedtime mode settings keys (Pixel-specific)
    private const val BEDTIME_MODE_SETTING = "bedtime_mode"

    private var context: Context? = null
    private var notificationManager: NotificationManager? = null
    private var prefs: SharedPreferences? = null
    private var isConnectedToAuto = false
    private var observer: Observer<Int>? = null

    /**
     * Initialize the AutoModeManager with application context.
     * Should be called once during application startup.
     */
    fun initialize(appContext: Context) {
        context = appContext
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Observe Android Auto connection changes
        val carConnection = CarConnection(appContext)
        observer = Observer<Int> { connectionType ->
            handleConnectionChange(connectionType)
        }
        observer?.let { carConnection.type.observeForever(it) }

        Log.d(TAG, "AutoModeManager initialized")
    }

    /**
     * Handle Android Auto connection state changes.
     */
    private fun handleConnectionChange(connectionType: Int) {
        val wasConnected = isConnectedToAuto
        val isNowConnected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION ||
                            connectionType == CarConnection.CONNECTION_TYPE_NATIVE

        if (!wasConnected && isNowConnected) {
            // Just connected to Android Auto
            Log.d(TAG, "Connected to Android Auto - disabling DND and Bedtime mode")
            onAndroidAutoConnected()
        } else if (wasConnected && !isNowConnected) {
            // Just disconnected from Android Auto
            Log.d(TAG, "Disconnected from Android Auto - restoring DND and Bedtime mode")
            onAndroidAutoDisconnected()
        }

        isConnectedToAuto = isNowConnected
    }

    /**
     * Called when Android Auto connects.
     * Saves current state and disables DND and Bedtime mode.
     */
    private fun onAndroidAutoConnected() {
        context ?: return

        var dndDisabled = false
        var bedtimeDisabled = false

        try {
            // Check and save DND state
            val dndWasActive = isDndActive()
            prefs?.edit()?.putBoolean(KEY_DND_WAS_ACTIVE, dndWasActive)?.apply()
            Log.d(TAG, "DND was active: $dndWasActive")

            // Disable DND if it was active
            if (dndWasActive) {
                if (disableDnd()) {
                    dndDisabled = true
                    Log.d(TAG, "DND disabled successfully")
                } else {
                    Log.w(TAG, "Failed to disable DND")
                }
            }

            // Check and save Bedtime mode state
            val bedtimeWasActive = isBedtimeActive()
            prefs?.edit()?.putBoolean(KEY_BEDTIME_WAS_ACTIVE, bedtimeWasActive)?.apply()
            Log.d(TAG, "Bedtime mode was active: $bedtimeWasActive")

            // Disable Bedtime mode if it was active
            if (bedtimeWasActive) {
                if (disableBedtimeMode()) {
                    bedtimeDisabled = true
                    Log.d(TAG, "Bedtime mode disabled successfully")
                } else {
                    Log.w(TAG, "Failed to disable Bedtime mode")
                }
            }

            // Create notification for success
            if (dndDisabled || bedtimeDisabled) {
                val modes = mutableListOf<String>()
                if (dndDisabled) modes.add("DND")
                if (bedtimeDisabled) modes.add("Bedtime mode")
                createSuccessNotification("Disabled ${modes.joinToString(" and ")} for Android Auto")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling Android Auto connection", e)
            createErrorNotification("Failed to manage DND/Bedtime mode: ${e.message}")
        }
    }

    /**
     * Called when Android Auto disconnects.
     * Restores DND and Bedtime mode to their previous states.
     */
    private fun onAndroidAutoDisconnected() {
        context ?: return

        var dndRestored = false
        var bedtimeRestored = false

        try {
            // Restore DND if it was active
            val dndWasActive = prefs?.getBoolean(KEY_DND_WAS_ACTIVE, false) ?: false
            if (dndWasActive) {
                if (enableDnd()) {
                    dndRestored = true
                    Log.d(TAG, "DND restored successfully")
                } else {
                    Log.w(TAG, "Failed to restore DND")
                }
            }

            // Restore Bedtime mode if it was active
            val bedtimeWasActive = prefs?.getBoolean(KEY_BEDTIME_WAS_ACTIVE, false) ?: false
            if (bedtimeWasActive) {
                if (enableBedtimeMode()) {
                    bedtimeRestored = true
                    Log.d(TAG, "Bedtime mode restored successfully")
                } else {
                    Log.w(TAG, "Failed to restore Bedtime mode")
                }
            }

            // Create notification for success
            if (dndRestored || bedtimeRestored) {
                val modes = mutableListOf<String>()
                if (dndRestored) modes.add("DND")
                if (bedtimeRestored) modes.add("Bedtime mode")
                createSuccessNotification("Restored ${modes.joinToString(" and ")} after Android Auto")
            }

            // Clear saved states
            prefs?.edit()
                ?.remove(KEY_DND_WAS_ACTIVE)
                ?.remove(KEY_BEDTIME_WAS_ACTIVE)
                ?.apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling Android Auto disconnection", e)
            createErrorNotification("Failed to restore DND/Bedtime mode: ${e.message}")
        }
    }

    /**
     * Check if DND is currently active.
     */
    private fun isDndActive(): Boolean {
        return try {
            val filter = notificationManager?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Error checking DND status", e)
            false
        }
    }

    /**
     * Disable DND.
     * Returns true if successful.
     */
    private fun disableDnd(): Boolean {
        return try {
            if (!notificationManager?.isNotificationPolicyAccessGranted!!) {
                Log.w(TAG, "DND access not granted")
                return false
            }
            notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling DND", e)
            false
        }
    }

    /**
     * Enable DND (Priority only mode).
     * Returns true if successful.
     */
    private fun enableDnd(): Boolean {
        return try {
            if (!notificationManager?.isNotificationPolicyAccessGranted!!) {
                Log.w(TAG, "DND access not granted")
                return false
            }
            notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling DND", e)
            false
        }
    }

    /**
     * Check if Bedtime mode is currently active.
     * Bedtime mode is a Pixel-specific feature from Digital Wellbeing.
     */
    private fun isBedtimeActive(): Boolean {
        return try {
            context?.let {
                val value = Settings.Secure.getInt(it.contentResolver, BEDTIME_MODE_SETTING, 0)
                value == 1
            } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read Bedtime mode setting", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bedtime mode status", e)
            false
        }
    }

    /**
     * Disable Bedtime mode.
     * Requires WRITE_SECURE_SETTINGS permission (grant via ADB).
     * Returns true if successful.
     */
    private fun disableBedtimeMode(): Boolean {
        return try {
            context?.let {
                Settings.Secure.putInt(it.contentResolver, BEDTIME_MODE_SETTING, 0)
                true
            } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to write Bedtime mode setting. Grant via: adb shell pm grant ${context?.packageName} android.permission.WRITE_SECURE_SETTINGS", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Bedtime mode", e)
            false
        }
    }

    /**
     * Enable Bedtime mode.
     * Requires WRITE_SECURE_SETTINGS permission (grant via ADB).
     * Returns true if successful.
     */
    private fun enableBedtimeMode(): Boolean {
        return try {
            context?.let {
                Settings.Secure.putInt(it.contentResolver, BEDTIME_MODE_SETTING, 1)
                true
            } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to write Bedtime mode setting. Grant via: adb shell pm grant ${context?.packageName} android.permission.WRITE_SECURE_SETTINGS", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Bedtime mode", e)
            false
        }
    }

    /**
     * Create a mimic notification for successful mode changes.
     */
    private fun createSuccessNotification(message: String) {
        context?.let { ctx ->
            // Use NotificationInterceptorService to create mimic notification
            ctx.sendBroadcast(android.content.Intent("com.worknotifier.app.AUTO_MODE_SUCCESS").apply {
                putExtra("message", message)
            })
            Log.d(TAG, "Success notification requested: $message")
        }
    }

    /**
     * Create a mimic notification for errors.
     */
    private fun createErrorNotification(message: String) {
        context?.let { ctx ->
            // Use NotificationInterceptorService to create mimic notification
            ctx.sendBroadcast(android.content.Intent("com.worknotifier.app.AUTO_MODE_ERROR").apply {
                putExtra("message", message)
            })
            Log.d(TAG, "Error notification requested: $message")
        }
    }
}

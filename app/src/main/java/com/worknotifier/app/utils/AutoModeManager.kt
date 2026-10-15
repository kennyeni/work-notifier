package com.worknotifier.app.utils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

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
    private const val KEY_DND_FILTER = "dnd_filter"
    private const val KEY_BEDTIME_BLIND_TOGGLED = "bedtime_blind_toggled"
    private const val KEY_BEDTIME_WAS_ACTIVE = "bedtime_was_active"

    // Bedtime mode control via Quick Settings tile
    private const val BEDTIME_TILE_COMPONENT = "com.google.android.apps.wellbeing/com.google.android.apps.wellbeing.screen.ui.GrayscaleTileService"

    // Grayscale setting (part of Bedtime mode)
    private const val GRAYSCALE_SETTING = "accessibility_display_grayscale"

    // Connect/disconnect handling runs root commands and sleeps, so it must never run on the
    // main thread (the CarConnection LiveData observer is delivered there). A single thread
    // also keeps rapid connect/disconnect events strictly ordered.
    private val serialScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    private var context: Context? = null
    private var notificationManager: NotificationManager? = null
    private var prefs: SharedPreferences? = null
    private var isConnectedToAuto = false
    private var observer: Observer<Int>? = null
    private var carConnection: CarConnection? = null

    /**
     * Initialize the AutoModeManager with application context.
     * Should be called once during application startup.
     */
    fun initialize(appContext: Context) {
        context = appContext
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Observe Android Auto connection changes
        carConnection = CarConnection(appContext)
        observer = Observer<Int> { connectionType ->
            handleConnectionChange(connectionType)
        }
        observer?.let { carConnection?.type?.observeForever(it) }

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
            serialScope.launch { onAndroidAutoConnected() }
        } else if (wasConnected && !isNowConnected) {
            // Just disconnected from Android Auto
            Log.d(TAG, "Disconnected from Android Auto - restoring DND and Bedtime mode")
            serialScope.launch { onAndroidAutoDisconnected() }
        }

        isConnectedToAuto = isNowConnected
    }

    /**
     * Called when Android Auto connects.
     * Saves current state and disables DND and Bedtime mode.
     */
    private fun onAndroidAutoConnected() {
        context ?: return

        try {
            // The saved states are only cleared on disconnect. If they still exist, this is a
            // reconnect after the app process died mid-drive: the modes were already disabled by
            // us, so re-reading them now would overwrite the real original state with "off" and
            // they'd never be restored.
            if (prefs?.contains(KEY_DND_WAS_ACTIVE) == true) {
                Log.d(TAG, "Saved mode state already present - keeping it, skipping re-save")
                return
            }

            val dndWasActive = isDndActive()
            val bedtimeWasActive = isBedtimeActive()
            Log.d(TAG, "Before drive: DND active=$dndWasActive, Bedtime active=$bedtimeWasActive")
            prefs?.edit()
                ?.putBoolean(KEY_DND_WAS_ACTIVE, dndWasActive)
                ?.putInt(KEY_DND_FILTER, currentDndFilter())
                ?.putBoolean(KEY_BEDTIME_WAS_ACTIVE, bedtimeWasActive)
                ?.putBoolean(KEY_BEDTIME_BLIND_TOGGLED, false)
                ?.apply()

            val result = disableForDrive(dndWasActive, bedtimeWasActive)

            val modes = mutableListOf<String>()
            if (result.dndDisabled) modes.add("DND")
            if (result.bedtimeDisabled) modes.add("Bedtime mode")
            if (modes.isNotEmpty()) {
                createSuccessNotification("Disabled ${modes.joinToString(" and ")} for Android Auto")
            }
            if (result.failures.isNotEmpty()) {
                createErrorNotification("Could not disable ${result.failures.joinToString(" and ")}")
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

        try {
            val dndWasActive = prefs?.getBoolean(KEY_DND_WAS_ACTIVE, false) ?: false
            val savedFilter = prefs?.getInt(KEY_DND_FILTER, NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                ?: NotificationManager.INTERRUPTION_FILTER_PRIORITY
            val bedtimeWasActive = prefs?.getBoolean(KEY_BEDTIME_WAS_ACTIVE, false) ?: false
            val blindToggled = prefs?.getBoolean(KEY_BEDTIME_BLIND_TOGGLED, false) ?: false

            val result = restoreAfterDrive(dndWasActive, savedFilter, bedtimeWasActive, blindToggled)

            val modes = mutableListOf<String>()
            if (result.dndDisabled) modes.add("DND")
            if (result.bedtimeDisabled) modes.add("Bedtime mode")
            if (modes.isNotEmpty()) {
                createSuccessNotification("Restored ${modes.joinToString(" and ")} after Android Auto")
            }
            if (result.failures.isNotEmpty()) {
                createErrorNotification("Could not restore ${result.failures.joinToString(" and ")}")
            }

            // Clear saved states
            prefs?.edit()
                ?.remove(KEY_DND_WAS_ACTIVE)
                ?.remove(KEY_DND_FILTER)
                ?.remove(KEY_BEDTIME_WAS_ACTIVE)
                ?.remove(KEY_BEDTIME_BLIND_TOGGLED)
                ?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Android Auto disconnection", e)
            createErrorNotification("Failed to restore DND/Bedtime mode: ${e.message}")
        }
    }

    /** Which modes a [disableForDrive] / [restoreAfterDrive] call actually changed. */
    private class ModeChange(
        val dndDisabled: Boolean,
        val bedtimeDisabled: Boolean,
        val failures: List<String>
    )

    /**
     * Switches off Bedtime mode and DND. Used by both the real connect handler and the test button.
     *
     * Bedtime mode goes first: it owns an automatic DND rule, and while that rule is active the
     * system ignores every manual attempt to turn DND off (API, `cmd notification`, and writing
     * `zen_mode` all silently do nothing). Turning Bedtime mode off removes the rule.
     *
     * Bedtime detection (grayscale setting) is not reliable on every device, so if DND is still
     * on after the normal attempts we also try the Bedtime tile blindly, and undo it if DND did
     * not change. The blind toggle is remembered so restore can undo it.
     */
    private fun disableForDrive(dndActive: Boolean, bedtimeActive: Boolean): ModeChange {
        var dndDisabled = false
        var bedtimeDisabled = false
        val failures = mutableListOf<String>()

        if (bedtimeActive) {
            if (disableBedtimeMode()) bedtimeDisabled = true else failures.add("Bedtime mode")
        }

        if (dndActive) {
            // Bedtime's DND rule tears down asynchronously after the tile click
            val clearedByBedtime = bedtimeDisabled && waitForDndFilter(NotificationManager.INTERRUPTION_FILTER_ALL, 3000)
            when {
                clearedByBedtime -> dndDisabled = true
                disableDnd() -> dndDisabled = true
                !bedtimeActive && disableDndViaBedtimeTile() -> {
                    dndDisabled = true
                    bedtimeDisabled = true
                    prefs?.edit()?.putBoolean(KEY_BEDTIME_BLIND_TOGGLED, true)?.apply()
                }
                else -> failures.add("DND")
            }
        }
        return ModeChange(dndDisabled, bedtimeDisabled, failures)
    }

    /**
     * Undoes [disableForDrive]. Bedtime mode comes back first (it re-creates its DND rule); a
     * separately saved DND setting is only re-applied if it is not already in place.
     */
    private fun restoreAfterDrive(
        dndWasActive: Boolean,
        dndFilter: Int,
        bedtimeWasActive: Boolean,
        blindToggled: Boolean
    ): ModeChange {
        var dndRestored = false
        var bedtimeRestored = false
        val failures = mutableListOf<String>()

        if (bedtimeWasActive) {
            if (enableBedtimeMode()) bedtimeRestored = true else failures.add("Bedtime mode")
        } else if (blindToggled) {
            // We switched it off without being able to detect it, so don't rely on detection
            if (toggleBedtimeMode()) bedtimeRestored = true else failures.add("Bedtime mode")
        }

        if (dndWasActive) {
            if (waitForDndFilter(dndFilter, 3000) || enableDnd(dndFilter)) {
                dndRestored = true
            } else {
                failures.add("DND")
            }
        }
        return ModeChange(dndRestored, bedtimeRestored, failures)
    }

    /**
     * Last resort when DND won't turn off: click the Bedtime tile blindly. If DND turned off,
     * Bedtime mode was the owner. If not, the click just switched Bedtime mode ON, so put it back.
     */
    private fun disableDndViaBedtimeTile(): Boolean {
        if (!toggleBedtimeMode()) return false
        if (waitForDndFilter(NotificationManager.INTERRUPTION_FILTER_ALL, 3000)) return true
        Log.w(TAG, "Bedtime tile click did not clear DND - reverting it")
        toggleBedtimeMode()
        return false
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

    private fun currentDndFilter(): Int =
        notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    /**
     * Disable DND (allow all interruptions).
     * Returns true only if the system filter really ended up as "all".
     */
    private fun disableDnd(): Boolean = setDndFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

    /**
     * Restore DND to [filter] (defaults to Priority only).
     * Returns true only if the system filter really ended up as [filter].
     */
    private fun enableDnd(filter: Int = NotificationManager.INTERRUPTION_FILTER_PRIORITY): Boolean =
        setDndFilter(filter)

    /**
     * Sets the interruption filter and verifies it took effect.
     *
     * `setInterruptionFilter()` alone is not reliable on Android 9+: an app's call is tied to its
     * own implicit DND rule, so it cannot switch off DND that the user (Quick Settings) or the
     * system (Bedtime mode, schedules) turned on, yet it returns without error. So after the API
     * call we check the real filter, and fall back to `cmd notification set_dnd` via root.
     */
    private fun setDndFilter(target: Int): Boolean {
        return try {
            val nm = notificationManager ?: return false

            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(target)
                if (waitForDndFilter(target)) return true
                Log.w(TAG, "setInterruptionFilter had no effect (current=${currentDndFilter()}, wanted=$target)")
            } else {
                Log.w(TAG, "DND access not granted")
            }

            val rootArg = when (target) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
                else -> return false
            }
            if (!RootUtils.isRooted()) {
                Log.w(TAG, "No root: cannot force DND filter")
                return false
            }
            val cmdOutput = RootUtils.executeRootCommand("cmd notification set_dnd $rootArg")
            Log.d(TAG, "set_dnd $rootArg -> ${cmdOutput?.trim()}")
            if (waitForDndFilter(target)) return true

            // `cmd notification` goes through the same per-app rule logic as the API, so it can be
            // ignored just the same. Writing the global zen_mode setting as root is applied by the
            // system as the user's own manual DND change.
            val zenValue = when (target) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> 0
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> 1
                NotificationManager.INTERRUPTION_FILTER_NONE -> 2
                else -> 3 // ALARMS
            }
            val putOutput = RootUtils.executeRootCommand("settings put global zen_mode $zenValue")
            Log.d(TAG, "zen_mode=$zenValue -> ${putOutput?.trim()}; filter now ${currentDndFilter()}")
            waitForDndFilter(target)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting DND filter to $target", e)
            false
        }
    }

    /** The filter change is applied asynchronously; poll briefly for it. */
    private fun waitForDndFilter(target: Int, timeoutMs: Long = 1000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (currentDndFilter() == target) return true
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(100)
        }
    }

    /**
     * Check if Bedtime mode is currently active.
     * Bedtime mode enables grayscale, so we check the grayscale setting.
     */
    private fun isBedtimeActive(): Boolean {
        return try {
            context?.let { ctx ->
                val grayscaleEnabled = Settings.Secure.getInt(ctx.contentResolver, GRAYSCALE_SETTING, 0)
                val isActive = grayscaleEnabled == 1
                Log.d(TAG, "Bedtime mode check - grayscale: $grayscaleEnabled, active: $isActive")
                isActive
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bedtime mode status", e)
            false
        }
    }

    /**
     * Toggle Bedtime mode by clicking its Quick Settings tile.
     * Requires root access.
     * Returns true if the command executed successfully.
     */
    private fun toggleBedtimeMode(): Boolean {
        return try {
            if (!RootUtils.isRooted()) {
                Log.w(TAG, "Root access required to toggle Bedtime mode tile")
                return false
            }

            val command = "cmd statusbar click-tile $BEDTIME_TILE_COMPONENT"
            Log.d(TAG, "Toggling Bedtime mode via tile: $command")

            val result = RootUtils.executeRootCommand(command)

            // Give the system a moment to process the tile click
            Thread.sleep(500)

            Log.d(TAG, "Bedtime mode tile toggled (result: ${result?.take(100)})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Bedtime mode tile", e)
            false
        }
    }

    /**
     * Disable Bedtime mode by toggling the tile if it's currently active.
     * Returns true if successful.
     */
    private fun disableBedtimeMode(): Boolean {
        return try {
            if (isBedtimeActive()) {
                toggleBedtimeMode()
            } else {
                Log.d(TAG, "Bedtime mode already disabled")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Bedtime mode", e)
            false
        }
    }

    /**
     * Enable Bedtime mode by toggling the tile if it's currently inactive.
     * Returns true if successful.
     */
    private fun enableBedtimeMode(): Boolean {
        return try {
            if (!isBedtimeActive()) {
                toggleBedtimeMode()
            } else {
                Log.d(TAG, "Bedtime mode already enabled")
                true
            }
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
                setPackage(ctx.packageName)
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
                setPackage(ctx.packageName)
                putExtra("message", message)
            })
            Log.d(TAG, "Error notification requested: $message")
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS via root, silently, on app startup.
     *
     * Runs on every launch, so it must be a no-op in the common cases: nothing happens (no root
     * prompt, no notification, no settings screen) when the permission is already granted or the
     * device isn't rooted. DND access can't be granted by root; enable it once in
     * Settings -> Notifications -> Do Not Disturb access.
     */
    fun setupPermissionsWithRoot() {
        val ctx = context ?: return
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        serialScope.launch {
            try {
                if (!RootUtils.isRooted()) {
                    Log.d(TAG, "No root: skipping WRITE_SECURE_SETTINGS grant")
                    return@launch
                }

                Log.d(TAG, "Granting WRITE_SECURE_SETTINGS via root...")
                val grantResult = RootUtils.executeRootCommand(
                    "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
                )
                if (!grantResult.isNullOrEmpty() && grantResult.contains("error", ignoreCase = true)) {
                    Log.e(TAG, "Error granting permission: $grantResult")
                } else {
                    Log.d(TAG, "WRITE_SECURE_SETTINGS granted successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up permissions", e)
            }
        }
    }

    /**
     * Test DND and Bedtime mode toggle functionality.
     * Runs the same disable/restore routines as a real Android Auto connect/disconnect:
     * disables both modes, waits, then restores them, posting a notification at each step.
     */
    fun testToggleModes() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting mode toggle test...")

                val dndWasActive = isDndActive()
                val dndFilter = currentDndFilter()
                val bedtimeWasActive = isBedtimeActive()

                withContext(Dispatchers.Main) {
                    createSuccessNotification("Test started\nDND: ${if (dndWasActive) "ON" else "OFF"}\nBedtime: ${if (bedtimeWasActive) "ON" else "OFF"}")
                }
                kotlinx.coroutines.delay(2000)

                // Same "blind" flag a real connect would persist, kept in memory for the test
                prefs?.edit()?.putBoolean(KEY_BEDTIME_BLIND_TOGGLED, false)?.apply()
                val disabled = disableForDrive(dndWasActive, bedtimeWasActive)
                val blindToggled = prefs?.getBoolean(KEY_BEDTIME_BLIND_TOGGLED, false) ?: false

                val disableLines = mutableListOf<String>()
                if (dndWasActive) disableLines.add(if (disabled.dndDisabled) "✓ DND disabled" else "✗ DND disable failed")
                if (bedtimeWasActive || blindToggled) {
                    disableLines.add(if (disabled.bedtimeDisabled) "✓ Bedtime disabled" else "✗ Bedtime disable failed")
                }
                withContext(Dispatchers.Main) {
                    when {
                        disableLines.isEmpty() -> createSuccessNotification("Disable step:\nNo modes were active")
                        disabled.failures.isNotEmpty() -> createErrorNotification("Disable step:\n${disableLines.joinToString("\n")}")
                        else -> createSuccessNotification("Disable step:\n${disableLines.joinToString("\n")}")
                    }
                }

                kotlinx.coroutines.delay(3000)

                val restored = restoreAfterDrive(dndWasActive, dndFilter, bedtimeWasActive, blindToggled)
                prefs?.edit()?.remove(KEY_BEDTIME_BLIND_TOGGLED)?.apply()

                val restoreLines = mutableListOf<String>()
                if (dndWasActive) restoreLines.add(if (restored.dndDisabled) "✓ DND restored" else "✗ DND restore failed")
                if (bedtimeWasActive || blindToggled) {
                    restoreLines.add(if (restored.bedtimeDisabled) "✓ Bedtime restored" else "✗ Bedtime restore failed")
                }
                withContext(Dispatchers.Main) {
                    when {
                        restoreLines.isEmpty() -> createSuccessNotification("Test complete\nNo modes were active")
                        restored.failures.isNotEmpty() -> createErrorNotification("Restore step:\n${restoreLines.joinToString("\n")}")
                        else -> createSuccessNotification("Test complete:\n${restoreLines.joinToString("\n")}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during mode toggle test", e)
                withContext(Dispatchers.Main) {
                    createErrorNotification("Test failed: ${e.message}")
                }
            }
        }
    }
}

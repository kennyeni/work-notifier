package com.worknotifier.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed Module Entry Point - MINIMAL READ-ONLY IMPLEMENTATION
 *
 * SECURITY: This module ONLY reads notification data. NO modifications are made.
 *
 * Purpose: Intercept notifications from Private Space (Android 15+) by hooking
 * NotificationManagerService at the system level before profile isolation is applied.
 *
 * Safety Features:
 * - Read-only access (no parameter modifications)
 * - Single hook point (minimal attack surface)
 * - Extensive error handling (no system crashes)
 * - Kill switch support (can be disabled)
 * - Logging for transparency
 */
class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "WorkNotifier-Xposed"
        private const val TARGET_PACKAGE = "android"
        private const val MODULE_VERSION = "1.0.0-READONLY"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook the Android system server
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        try {
            XposedBridge.log("$TAG: Module loaded (v$MODULE_VERSION) - READ-ONLY mode")

            // Check for kill switch file
            if (SafetyUtils.isKillSwitchActive()) {
                XposedBridge.log("$TAG: Kill switch active - module disabled")
                return
            }

            // Initialize the notification hook (read-only)
            NotificationHook.init(lpparam.classLoader)

            XposedBridge.log("$TAG: Notification hook initialized successfully")

        } catch (e: Throwable) {
            // CRITICAL: Never crash the system - log and fail gracefully
            XposedBridge.log("$TAG: ERROR during initialization")
            XposedBridge.log(e)
        }
    }
}

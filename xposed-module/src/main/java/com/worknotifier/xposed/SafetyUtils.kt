package com.worknotifier.xposed

import java.io.File

/**
 * Safety utilities for the Xposed module.
 *
 * Provides kill switch and safety checks to prevent system issues.
 */
object SafetyUtils {

    private const val KILL_SWITCH_PATH = "/data/local/tmp/worknotifier_xposed_disable"

    /**
     * Checks if the kill switch is active.
     *
     * To disable the module without uninstalling:
     * adb shell touch /data/local/tmp/worknotifier_xposed_disable
     *
     * To re-enable:
     * adb shell rm /data/local/tmp/worknotifier_xposed_disable
     */
    fun isKillSwitchActive(): Boolean {
        return try {
            File(KILL_SWITCH_PATH).exists()
        } catch (e: Throwable) {
            // If we can't check, assume disabled for safety
            true
        }
    }

    /**
     * Safely extracts a string from an object using toString().
     * Returns null if extraction fails.
     */
    fun safeToString(obj: Any?): String? {
        return try {
            obj?.toString()
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Safely gets an integer value.
     * Returns default value if extraction fails.
     */
    fun safeGetInt(obj: Any?, default: Int = 0): Int {
        return try {
            when (obj) {
                is Int -> obj
                is Number -> obj.toInt()
                else -> default
            }
        } catch (e: Throwable) {
            default
        }
    }
}

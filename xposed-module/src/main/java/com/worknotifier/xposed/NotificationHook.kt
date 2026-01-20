package com.worknotifier.xposed

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Notification Hook - READ-ONLY IMPLEMENTATION
 *
 * SECURITY CRITICAL: This class hooks into NotificationManagerService.
 * It ONLY reads notification data and NEVER modifies any parameters.
 *
 * Hook Strategy:
 * - Target: NotificationManagerService.enqueueNotificationInternal()
 * - Action: Read notification data BEFORE profile isolation
 * - Safety: All operations wrapped in try-catch
 * - Guarantee: Original parameters NEVER modified
 */
object NotificationHook {

    private const val TAG = "WorkNotifier-Hook"

    // Target class and method
    private const val TARGET_CLASS = "com.android.server.notification.NotificationManagerService"
    private const val TARGET_METHOD = "enqueueNotificationInternal"

    /**
     * Initialize the hook.
     *
     * @param classLoader The system class loader
     */
    fun init(classLoader: ClassLoader) {
        try {
            // Find the NotificationManagerService class
            val nmsClass = XposedHelpers.findClass(TARGET_CLASS, classLoader)

            // Hook the enqueueNotificationInternal method
            // This method is called BEFORE profile isolation, so we can see all notifications
            XposedHelpers.findAndHookMethod(
                nmsClass,
                TARGET_METHOD,
                String::class.java,              // pkg (package name)
                String::class.java,              // opPkg
                Int::class.javaPrimitiveType,    // callingUid
                Int::class.javaPrimitiveType,    // callingPid
                String::class.java,              // tag
                Int::class.javaPrimitiveType,    // id
                Notification::class.java,        // notification
                Int::class.javaPrimitiveType,    // userId
                object : XC_MethodHook() {
                    /**
                     * BEFORE the method executes - READ-ONLY
                     */
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            // READ parameters (never modify!)
                            val pkg = SafetyUtils.safeToString(param.args[0]) ?: return
                            val notification = param.args[6] as? Notification ?: return
                            val userId = SafetyUtils.safeGetInt(param.args[7], 0)

                            // Skip system package
                            if (pkg == "android") {
                                return
                            }

                            // Extract notification data (READ-ONLY)
                            val extras: Bundle? = notification.extras
                            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

                            // Log the notification (for the main app to read via logcat)
                            // Format: WN_NOTIFICATION|pkg|userId|title|text|timestamp
                            val timestamp = System.currentTimeMillis()
                            val logMsg = "WN_NOTIFICATION|$pkg|$userId|${title ?: ""}|${text ?: ""}|$timestamp"

                            XposedBridge.log("$TAG: $logMsg")

                        } catch (e: Throwable) {
                            // CRITICAL: Never crash the system
                            // Just log the error and continue normally
                            XposedBridge.log("$TAG: Error reading notification (non-critical)")
                            XposedBridge.log(e)
                        }
                    }

                    /**
                     * AFTER the method executes - NOT USED
                     *
                     * We don't need afterHookedMethod because we're read-only.
                     * Included here for completeness and potential future diagnostics.
                     */
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Intentionally empty - read-only module doesn't modify results
                    }
                }
            )

            XposedBridge.log("$TAG: Successfully hooked $TARGET_METHOD (READ-ONLY)")

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: FATAL - Failed to hook $TARGET_METHOD")
            XposedBridge.log(e)
            throw e
        }
    }
}

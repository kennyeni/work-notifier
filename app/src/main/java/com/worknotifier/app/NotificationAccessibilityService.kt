package com.worknotifier.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.worknotifier.app.data.InterceptedNotification
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType
import com.worknotifier.app.utils.RootUtils

/**
 * AccessibilityService to intercept notifications from all apps, including Private Space.
 * This service may have broader access than NotificationListenerService for Private Space apps.
 *
 * IMPORTANT: This service is ONLY used for notification interception. It does not
 * perform any other accessibility functions or user interaction monitoring.
 */
class NotificationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NotificationAccessibility"
    }

    private var isRooted: Boolean = false
    private var userProfileInfo: Map<Int, String> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Configure accessibility service
        val info = AccessibilityServiceInfo().apply {
            // Only listen to notification events
            eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

            // Listen to all packages
            packageNames = null

            // Set feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // Don't retrieve window content (we only need notification data)
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            // No delay
            notificationTimeout = 0
        }

        serviceInfo = info

        Log.d(TAG, "Accessibility Service Connected")

        // Check for root and get user profile info
        isRooted = RootUtils.isRooted()
        if (isRooted) {
            userProfileInfo = RootUtils.getUserProfileInfo()
            Log.d(TAG, "Root detected. User profiles: $userProfileInfo")
        } else {
            Log.d(TAG, "No root access. Profile detection may be limited.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only process notification events
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return
        }

        try {
            val packageName = event.packageName?.toString() ?: return

            // Skip our own notifications
            if (packageName == application.packageName) {
                return
            }

            // Get notification parcelable (if available)
            val notification = event.parcelableData as? Notification

            // Extract notification data
            val title: String?
            val text: String?

            if (notification != null) {
                // Extract from Notification object
                val extras: Bundle = notification.extras
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            } else {
                // Fallback: use event text
                title = event.contentDescription?.toString()
                text = event.text?.firstOrNull()?.toString()
            }

            // Only process if we have at least title or text
            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                return
            }

            val timestamp = event.eventTime
            val key = generateNotificationKey(packageName, timestamp)

            // Get app name
            val appName = getAppName(packageName)

            // Determine profile type - AccessibilityService may not have access to UserHandle
            // So we'll mark all as PERSONAL unless we can determine otherwise
            val profileType = ProfileType.PERSONAL
            val userId = 0

            // Create intercepted notification object
            val interceptedNotification = InterceptedNotification(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = timestamp,
                key = key,
                profileType = profileType,
                userId = userId
            )

            // Store the notification
            NotificationStorage.addNotification(interceptedNotification)

            Log.d(
                TAG,
                "Notification intercepted via Accessibility - App: $appName, Title: $title"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    /**
     * Gets the human-readable app name from package name.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = application.packageManager
            val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(
                packageName,
                0
            )
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name if app name not found
        }
    }

    /**
     * Generates a unique key for notification deduplication.
     */
    private fun generateNotificationKey(packageName: String, timestamp: Long): String {
        return "$packageName:$timestamp"
    }
}

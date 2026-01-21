package com.worknotifier.app

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.worknotifier.app.data.InterceptedNotification
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType
import com.worknotifier.app.utils.RootUtils
import java.io.ByteArrayOutputStream

/**
 * NotificationListenerService to intercept notifications from all apps.
 * This service can intercept notifications from personal and work profiles
 * (work profile access may be restricted by IT admins).
 */
class NotificationInterceptorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationInterceptor"
    }

    private var isRooted: Boolean = false
    private var userProfileInfo: Map<Int, String> = emptyMap()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")

        // Check for root and get user profile info
        isRooted = RootUtils.isRooted()
        if (isRooted) {
            userProfileInfo = RootUtils.getUserProfileInfo()
            Log.d(TAG, "Root detected. User profiles: $userProfileInfo")
        } else {
            Log.d(TAG, "No root access. Work and private profile detection may be limited.")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        try {
            val packageName = sbn.packageName
            val notification = sbn.notification ?: return

            // Skip our own notifications
            if (packageName == applicationContext.packageName) {
                return
            }

            // Extract notification data
            val extras: Bundle = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val timestamp = sbn.postTime
            val key = sbn.key

            // Get app name and icon
            val appName = getAppName(packageName)
            val appIconBase64 = getAppIconBase64(packageName)

            // Determine profile type and user ID
            val (profileType, userId) = determineProfileType(sbn)

            // Create intercepted notification object
            val interceptedNotification = InterceptedNotification(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = timestamp,
                key = key,
                profileType = profileType,
                userId = userId,
                appIconBase64 = appIconBase64
            )

            // Store the notification
            NotificationStorage.addNotification(interceptedNotification)

            Log.d(
                TAG,
                "Notification intercepted - App: $appName, Title: $title, Text: $text"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    /**
     * Gets the human-readable app name from package name.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
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
     * Gets the app icon and encodes it as Base64 string for storage.
     * This allows icons from Work and Private profiles to be displayed correctly.
     */
    private fun getAppIconBase64(packageName: String): String? {
        return try {
            val packageManager = applicationContext.packageManager
            val appIcon = packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(appIcon)
            bitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Could not get app icon for $packageName", e)
            null
        }
    }

    /**
     * Converts a Drawable to a Bitmap.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Converts a Bitmap to a Base64 encoded string.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Compress to reduce storage size (PNG format, quality doesn't apply to PNG)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    /**
     * Determines the profile type and user ID of a notification.
     * Returns a Pair of ProfileType and user ID.
     *
     * With root access:
     * - Can distinguish between WORK and PRIVATE profiles using profile names
     * - More accurate detection across all profile types
     *
     * Without root access:
     * - Can only detect if notification is from a different profile
     * - Cannot distinguish between WORK and PRIVATE profiles
     */
    private fun determineProfileType(sbn: StatusBarNotification): Pair<ProfileType, Int> {
        return try {
            val notificationUser = sbn.user
            val currentUser = Process.myUserHandle()

            // If from current user, it's personal profile
            if (notificationUser == currentUser) {
                return Pair(ProfileType.PERSONAL, 0)
            }

            // Notification is from a different user profile
            // Try to get user ID using reflection
            val userId = try {
                val getIdentifierMethod = UserHandle::class.java.getMethod("getIdentifier")
                getIdentifierMethod.invoke(notificationUser) as Int
            } catch (e: Exception) {
                Log.w(TAG, "Could not get user ID via reflection", e)
                -1
            }

            // If we have root access, use profile name to distinguish
            if (isRooted && userId >= 0) {
                val profileName = userProfileInfo[userId]
                Log.d(TAG, "Profile detected - UserID: $userId, Name: $profileName")

                return when {
                    RootUtils.isPrivateProfile(userId, profileName) -> {
                        Pair(ProfileType.PRIVATE, userId)
                    }
                    RootUtils.isWorkProfileByName(profileName) -> {
                        Pair(ProfileType.WORK, userId)
                    }
                    else -> {
                        // Unknown profile type, default to WORK for backwards compatibility
                        Pair(ProfileType.WORK, userId)
                    }
                }
            }

            // Without root, we can only detect it's a different profile
            // Default to WORK for backwards compatibility
            Pair(ProfileType.WORK, userId)

        } catch (e: Exception) {
            Log.e(TAG, "Error determining profile type", e)
            Pair(ProfileType.PERSONAL, 0)
        }
    }
}

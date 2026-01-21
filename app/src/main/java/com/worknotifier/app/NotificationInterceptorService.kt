package com.worknotifier.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.worknotifier.app.data.InterceptedNotification
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType
import com.worknotifier.app.utils.AndroidAutoDetector
import com.worknotifier.app.utils.RootUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationListenerService to intercept notifications from all apps.
 * This service can intercept notifications from personal and work profiles
 * (work profile access may be restricted by IT admins).
 */
class NotificationInterceptorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationInterceptor"
        private const val MIMIC_CHANNEL_ID = "mimic_notifications"
        private const val MIMIC_CHANNEL_NAME = "Mimic Notifications"
        private const val MIMIC_NOTIFICATION_ID_BASE = 100000
        private const val ACTION_MIMIC_DISMISSED = "com.worknotifier.app.MIMIC_DISMISSED"
        private const val ACTION_MIMIC_REPLY = "com.worknotifier.app.MIMIC_REPLY"
        private const val ACTION_MIMIC_MARK_READ = "com.worknotifier.app.MIMIC_MARK_READ"
        private const val EXTRA_ORIGINAL_KEY = "original_key"
        private const val REMOTE_INPUT_KEY = "remote_input_key"
    }

    private var isRooted: Boolean = false
    private var userProfileInfo: Map<Int, String> = emptyMap()

    // Track relationship between original notification keys and mimic notification IDs
    // Map<originalNotificationKey, mimicNotificationId>
    private val originalToMimic = ConcurrentHashMap<String, Int>()
    // Map<mimicNotificationId, Set<originalNotificationKeys>>
    private val mimicToOriginals = ConcurrentHashMap<Int, MutableSet<String>>()
    // Track manual mimics by storage key (packageName|profileType) to prevent duplicates
    // Map<storageKey, mimicNotificationId>
    private val manualMimics = ConcurrentHashMap<String, Int>()
    // Track mimics by content hash to prevent duplicate content with different keys
    // Map<contentHash, mimicNotificationId>
    private val contentToMimic = ConcurrentHashMap<String, Int>()
    private var nextMimicId = MIMIC_NOTIFICATION_ID_BASE
    // Lock for synchronizing mimic creation to prevent race conditions
    private val mimicCreationLock = Any()

    // Broadcast receiver to handle mimic notification actions
    private val mimicActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_MIMIC_DISMISSED -> {
                    val originalKey = intent.getStringExtra(EXTRA_ORIGINAL_KEY)
                    if (originalKey != null && originalKey.isNotEmpty()) {
                        // Cancel ALL original notifications with this content
                        try {
                            val statusBarNotifications = activeNotifications
                            val mimicId = originalToMimic[originalKey]

                            if (mimicId != null) {
                                // Get all notification keys that map to this mimic
                                val allKeys = mimicToOriginals[mimicId] ?: mutableSetOf()

                                // Cancel all original notifications with this content
                                allKeys.forEach { key ->
                                    val originalNotification = statusBarNotifications.find { it.key == key }
                                    if (originalNotification != null) {
                                        cancelNotification(key)
                                        originalToMimic.remove(key)
                                        Log.d(TAG, "Cancelled original notification: $key")
                                    }
                                }

                                // Clean up tracking
                                mimicToOriginals.remove(mimicId)

                                // Remove from content hash tracking
                                val contentHashEntry = contentToMimic.entries.find { it.value == mimicId }
                                if (contentHashEntry != null) {
                                    contentToMimic.remove(contentHashEntry.key)
                                    Log.d(TAG, "Removed content hash entry for dismissed mimic: $mimicId")
                                }

                                Log.d(TAG, "Mimic dismissed, cancelled ${allKeys.size} original notifications")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cancelling original notifications", e)
                        }
                    }
                }
                ACTION_MIMIC_REPLY -> {
                    // Reply action does nothing as per requirements
                    val replyText = RemoteInput.getResultsFromIntent(intent)?.getString(REMOTE_INPUT_KEY)
                    Log.d(TAG, "Mimic reply action (does nothing): $replyText")
                }
                ACTION_MIMIC_MARK_READ -> {
                    // Mark as read action does nothing as per requirements
                    Log.d(TAG, "Mimic mark as read action (does nothing)")
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")

        // Initialize Android Auto detector
        AndroidAutoDetector.initialize(applicationContext)

        // Clean up tracking maps to prevent memory leaks
        // Remove entries for notifications that no longer exist
        cleanupStaleEntries()

        // Create notification channel for mimic notifications
        createMimicNotificationChannel()

        // Register broadcast receiver for mimic actions
        val filter = IntentFilter().apply {
            addAction(ACTION_MIMIC_DISMISSED)
            addAction(ACTION_MIMIC_REPLY)
            addAction(ACTION_MIMIC_MARK_READ)
        }
        registerReceiver(mimicActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

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

        // Unregister broadcast receiver
        try {
            unregisterReceiver(mimicActionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    /**
     * Cleans up stale entries from tracking maps to prevent memory leaks.
     * Removes entries for notifications and mimics that no longer exist.
     */
    private fun cleanupStaleEntries() {
        try {
            val activeKeys = activeNotifications.map { it.key }.toSet()
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val activeNotifs = notificationManager.activeNotifications.map { it.id }.toSet()

            // Clean up originalToMimic - remove keys that don't exist in active notifications
            val staleOriginalKeys = originalToMimic.keys.filter { it !in activeKeys }
            staleOriginalKeys.forEach { key ->
                val mimicId = originalToMimic.remove(key)
                if (mimicId != null) {
                    mimicToOriginals[mimicId]?.remove(key)
                }
            }

            // Clean up mimicToOriginals - remove mimics that have no keys or don't exist
            val staleMimicIds = mimicToOriginals.keys.filter { mimicId ->
                mimicToOriginals[mimicId]?.isEmpty() == true || mimicId !in activeNotifs
            }
            staleMimicIds.forEach { mimicId ->
                mimicToOriginals.remove(mimicId)

                // Also remove from contentToMimic
                val contentEntry = contentToMimic.entries.find { it.value == mimicId }
                if (contentEntry != null) {
                    contentToMimic.remove(contentEntry.key)
                }
            }

            // Clean up manualMimics - remove if mimic doesn't exist
            val staleManualKeys = manualMimics.entries.filter { (_, mimicId) ->
                mimicId !in activeNotifs
            }.map { it.key }
            staleManualKeys.forEach { manualMimics.remove(it) }

            Log.d(TAG, "Cleanup complete: removed ${staleOriginalKeys.size} original keys, ${staleMimicIds.size} mimic IDs, ${staleManualKeys.size} manual mimics")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "MIMIC_NOTIFICATION") {
            // Handle request to mimic a notification (triggered when checkbox is first checked)
            val packageName = intent.getStringExtra("packageName") ?: return START_NOT_STICKY
            val appName = intent.getStringExtra("appName") ?: packageName
            val title = intent.getStringExtra("title")
            val text = intent.getStringExtra("text")
            val profileTypeStr = intent.getStringExtra("profileType") ?: ProfileType.PERSONAL.name
            val profileType = try {
                ProfileType.valueOf(profileTypeStr)
            } catch (e: Exception) {
                ProfileType.PERSONAL
            }
            val appIconBase64 = intent.getStringExtra("appIconBase64")

            // Only create mimic if Android Auto conditions are met
            if (shouldCreateMimic()) {
                // Create the mimic notification
                createMimicNotification(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    profileType = profileType,
                    appIconBase64 = appIconBase64,
                    originalNotificationKey = null // No original notification key for manual mimic
                )
            } else {
                Log.d(TAG, "Manual mimic skipped: Android Auto Only Mode enabled but not connected")
            }
        }
        return START_NOT_STICKY
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

            // Check if we should create a mimic notification
            // Conditions:
            // 1. Mimic is enabled for this app+profile
            // 2. Notification passes regex filters
            // 3. If Android Auto Only Mode is enabled, must be connected to Android Auto
            if (NotificationStorage.isMimicEnabled(packageName, profileType) &&
                NotificationStorage.matchesFilters(interceptedNotification) &&
                shouldCreateMimic()) {
                createMimicNotification(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    profileType = profileType,
                    appIconBase64 = appIconBase64,
                    originalNotificationKey = key,
                    originalNotification = notification
                )
            }

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

        try {
            val notificationKey = sbn.key

            // Check if this is an original notification that has a mimic
            val mimicId = originalToMimic[notificationKey]
            if (mimicId != null) {
                // Remove this key from tracking
                originalToMimic.remove(notificationKey)

                // Get all keys still pointing to this mimic
                val allKeys = mimicToOriginals[mimicId]
                if (allKeys != null) {
                    allKeys.remove(notificationKey)

                    // Only dismiss the mimic if NO other notifications with this content exist
                    if (allKeys.isEmpty()) {
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(mimicId)
                        mimicToOriginals.remove(mimicId)

                        // Remove from content hash tracking
                        val contentHashEntry = contentToMimic.entries.find { it.value == mimicId }
                        if (contentHashEntry != null) {
                            contentToMimic.remove(contentHashEntry.key)
                            Log.d(TAG, "Removed content hash entry for mimic: $mimicId")
                        }

                        Log.d(TAG, "Last notification removed, dismissed mimic: $mimicId")
                    } else {
                        Log.d(TAG, "Notification removed but ${allKeys.size} other(s) with same content still exist, keeping mimic: $mimicId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification removal", e)
        }
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
            // Scale down to reduce storage size (96x96 is sufficient for display)
            val scaledBitmap = scaleBitmap(bitmap, 96, 96)
            bitmapToBase64(scaledBitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Could not get app icon for $packageName", e)
            null
        }
    }

    /**
     * Converts a Drawable to a Bitmap.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Scales a bitmap to the specified dimensions.
     */
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true)
    }

    /**
     * Converts a Bitmap to a Base64 encoded string.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Use JPEG with 85% quality for better compression (icons don't need PNG transparency usually)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Determines if a mimic notification should be created based on Android Auto settings.
     *
     * Returns true if:
     * - Android Auto Only Mode is disabled (always create mimics), OR
     * - Android Auto Only Mode is enabled AND device is connected to Android Auto
     */
    private fun shouldCreateMimic(): Boolean {
        val androidAutoOnlyMode = NotificationStorage.isAndroidAutoOnlyMode()

        return if (androidAutoOnlyMode) {
            // Only create mimic if connected to Android Auto
            val isConnected = AndroidAutoDetector.isConnectedToAndroidAuto()
            Log.d(TAG, "Android Auto Only Mode enabled. Connected: $isConnected")
            isConnected
        } else {
            // Always create mimics when mode is disabled
            true
        }
    }

    /**
     * Creates the notification channel for mimic notifications.
     */
    private fun createMimicNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            MIMIC_CHANNEL_ID,
            MIMIC_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Mimic notifications from other apps"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a mimic notification that duplicates the original notification.
     * Uses MessagingStyle for Android Auto compatibility.
     */
    private fun createMimicNotification(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        profileType: ProfileType,
        appIconBase64: String?,
        originalNotificationKey: String?,
        originalNotification: Notification? = null
    ) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Generate content hash for deduplication (packageName + profileType + title + text)
            val contentHash = "$packageName|${profileType.name}|${NotificationStorage.getContentHash(title, text)}"

            // Atomically check and create mimic to prevent race conditions
            val mimicId = synchronized(mimicCreationLock) {
                // Check if we already have a mimic for this exact content
                val existingMimicId = contentToMimic[contentHash]
                if (existingMimicId != null && originalNotificationKey != null) {
                    // Already have a mimic with this exact content
                    // Update tracking so this notification key can also dismiss the mimic (two-way dismissal)
                    originalToMimic[originalNotificationKey] = existingMimicId
                    mimicToOriginals.getOrPut(existingMimicId) { mutableSetOf() }.add(originalNotificationKey)
                    Log.d(TAG, "Reusing existing mimic $existingMimicId for duplicate content, added key: $originalNotificationKey")
                    return
                }

                // Get or create a unique notification ID for this mimic
                val newMimicId = nextMimicId++

                // Track the mimic by content hash to prevent duplicates
                contentToMimic[contentHash] = newMimicId

                newMimicId
            }

            // Track the relationship between original and mimic
            if (originalNotificationKey != null) {
                // This is an automatic mimic from onNotificationPosted
                // Cancel any existing mimic for this original notification key (should not happen with content dedup)
                originalToMimic[originalNotificationKey]?.let { oldMimicId ->
                    // Remove this key from the old mimic's tracking
                    mimicToOriginals[oldMimicId]?.remove(originalNotificationKey)

                    // If old mimic has no more keys, cancel it
                    if (mimicToOriginals[oldMimicId]?.isEmpty() == true) {
                        notificationManager.cancel(oldMimicId)
                        mimicToOriginals.remove(oldMimicId)

                        // Clean up old content hash entry
                        val oldContentHashEntry = contentToMimic.entries.find { it.value == oldMimicId }
                        if (oldContentHashEntry != null) {
                            contentToMimic.remove(oldContentHashEntry.key)
                        }
                    }
                }

                // Add this notification to the new mimic's tracking
                originalToMimic[originalNotificationKey] = mimicId
                mimicToOriginals.getOrPut(mimicId) { mutableSetOf() }.add(originalNotificationKey)
            } else {
                // This is a manual mimic (triggered by checkbox)
                // Use storage key to prevent duplicates
                val storageKey = "$packageName|${profileType.name}"
                manualMimics[storageKey]?.let { oldMimicId ->
                    // Cancel existing manual mimic for this app+profile
                    notificationManager.cancel(oldMimicId)

                    // Clean up old content hash entry
                    val oldContentHashEntry = contentToMimic.entries.find { it.value == oldMimicId }
                    if (oldContentHashEntry != null) {
                        contentToMimic.remove(oldContentHashEntry.key)
                    }

                    Log.d(TAG, "Cancelled previous manual mimic: $oldMimicId for $storageKey")
                }
                manualMimics[storageKey] = mimicId
                Log.d(TAG, "Created manual mimic: $mimicId for $storageKey")
            }

            // Add profile badge to conversation title for Work/Private profiles
            val profileBadge = when (profileType) {
                ProfileType.WORK -> " [WORK]"
                ProfileType.PRIVATE -> " [PRIVATE]"
                ProfileType.PERSONAL -> ""
            }

            // Create Person objects for MessagingStyle (required for Android Auto)
            val senderName = title ?: appName
            val senderPerson = Person.Builder()
                .setName("$senderName$profileBadge")
                .apply {
                    appIconBase64?.let { iconBase64 ->
                        decodeBase64ToBitmap(iconBase64)?.let { bitmap ->
                            setIcon(IconCompat.createWithBitmap(bitmap))
                        }
                    }
                }
                .build()

            val deviceUser = Person.Builder()
                .setName("You")
                .build()

            // Try to extract existing MessagingStyle from original notification
            val originalMessagingStyle = originalNotification?.let { notif ->
                try {
                    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notif)
                } catch (e: Exception) {
                    null
                }
            }

            // Create or recreate MessagingStyle for Android Auto compatibility
            val messagingStyle = if (originalMessagingStyle != null) {
                // Use original MessagingStyle if available
                originalMessagingStyle
            } else {
                // Create new MessagingStyle with the message
                NotificationCompat.MessagingStyle(deviceUser)
                    .setConversationTitle("$senderName$profileBadge")
                    .addMessage(
                        text ?: "(No message content)",
                        System.currentTimeMillis(),
                        senderPerson
                    )
            }

            // Create Reply action (required for Android Auto)
            val replyIntent = Intent(ACTION_MIMIC_REPLY).apply {
                setPackage(applicationContext.packageName)
                putExtra(EXTRA_ORIGINAL_KEY, originalNotificationKey ?: "")
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                mimicId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("Reply")
                .build()
            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply,
                "Reply",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()

            // Create Mark as Read action (required for Android Auto)
            val markReadIntent = Intent(ACTION_MIMIC_MARK_READ).apply {
                setPackage(applicationContext.packageName)
                putExtra(EXTRA_ORIGINAL_KEY, originalNotificationKey ?: "")
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                this,
                mimicId + 1,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val markReadAction = NotificationCompat.Action.Builder(
                R.drawable.ic_mark_read,
                "Mark as Read",
                markReadPendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()

            // Create the notification builder with MessagingStyle
            val builder = NotificationCompat.Builder(this, MIMIC_CHANNEL_ID)
                .setStyle(messagingStyle)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(replyAction)
                .addAction(markReadAction)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Set app icon as large icon if available
            appIconBase64?.let { iconBase64 ->
                decodeBase64ToBitmap(iconBase64)?.let { bitmap ->
                    builder.setLargeIcon(bitmap)
                }
            }

            // Add delete intent to cancel original notification when mimic is dismissed
            if (originalNotificationKey != null) {
                val deleteIntent = Intent(ACTION_MIMIC_DISMISSED).apply {
                    setPackage(applicationContext.packageName)
                    putExtra(EXTRA_ORIGINAL_KEY, originalNotificationKey)
                }
                val deletePendingIntent = PendingIntent.getBroadcast(
                    this,
                    mimicId + 2,
                    deleteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setDeleteIntent(deletePendingIntent)
            }

            // Post the mimic notification
            notificationManager.notify(mimicId, builder.build())

            Log.d(TAG, "Mimic notification created with MessagingStyle: ID=$mimicId, App=$appName")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating mimic notification", e)

            // Cleanup: remove tracking entries for failed mimic creation
            try {
                val contentHash = "$packageName|${profileType.name}|${NotificationStorage.getContentHash(title, text)}"

                // Remove from content hash tracking
                contentToMimic.remove(contentHash)

                // Remove from original tracking if applicable
                if (originalNotificationKey != null) {
                    val failedMimicId = originalToMimic.remove(originalNotificationKey)
                    if (failedMimicId != null) {
                        mimicToOriginals[failedMimicId]?.remove(originalNotificationKey)
                        if (mimicToOriginals[failedMimicId]?.isEmpty() == true) {
                            mimicToOriginals.remove(failedMimicId)
                        }
                    }
                } else {
                    // Remove from manual mimic tracking
                    val storageKey = "$packageName|${profileType.name}"
                    manualMimics.remove(storageKey)
                }

                Log.d(TAG, "Cleaned up tracking entries for failed mimic creation")
            } catch (cleanupException: Exception) {
                Log.e(TAG, "Error during cleanup after failed mimic creation", cleanupException)
            }
        }
    }

    /**
     * Decodes a Base64 string to a Bitmap.
     */
    private fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = try {
                Base64.decode(base64, Base64.NO_WRAP)
            } catch (e: Exception) {
                Base64.decode(base64, Base64.DEFAULT)
            }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 to Bitmap", e)
            null
        }
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

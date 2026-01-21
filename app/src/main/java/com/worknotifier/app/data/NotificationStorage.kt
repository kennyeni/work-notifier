package com.worknotifier.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton to store intercepted notifications in memory and persistently.
 * Keeps the last 10 notifications per app per profile.
 */
object NotificationStorage {

    private const val MAX_NOTIFICATIONS_PER_APP = 10
    private const val PREFS_NAME = "notification_storage"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_APP_ICONS = "app_icons"

    // Map key: "packageName|profileType"
    private val notifications = ConcurrentHashMap<String, MutableList<InterceptedNotification>>()
    // Separate storage for app icons to avoid duplication
    private val appIcons = ConcurrentHashMap<String, String>()
    private val gson = Gson()
    private var sharedPrefs: SharedPreferences? = null

    /**
     * Initializes storage with context for persistence.
     */
    fun init(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    /**
     * Generates composite key for app + profile combination.
     */
    private fun getStorageKey(packageName: String, profileType: ProfileType): String {
        return "$packageName|${profileType.name}"
    }

    /**
     * Adds a notification to storage.
     * Keeps only the most recent MAX_NOTIFICATIONS_PER_APP notifications per app per profile.
     * Deduplicates notifications based on their key to avoid showing duplicates.
     */
    fun addNotification(notification: InterceptedNotification) {
        // Validate notification has a valid key and timestamp
        if (notification.key.isBlank() || notification.timestamp <= 0) {
            return
        }

        val storageKey = getStorageKey(notification.packageName, notification.profileType)

        // Store app icon separately if provided (only once per app+profile)
        if (!notification.appIconBase64.isNullOrBlank() && !appIcons.containsKey(storageKey)) {
            appIcons[storageKey] = notification.appIconBase64
        }

        val appNotifications = notifications.getOrPut(storageKey) {
            mutableListOf()
        }

        // Remove any existing notification with the same key (update case)
        // Also remove any notification with the same title/text but invalid timestamp (duplicate detection)
        appNotifications.removeAll {
            it.key == notification.key ||
            (it.title == notification.title && it.text == notification.text && it.timestamp <= 0)
        }

        // Add the new notification at the beginning (without icon to save space)
        val notificationWithoutIcon = notification.copy(appIconBase64 = null)
        appNotifications.add(0, notificationWithoutIcon)

        // Keep only the most recent notifications
        if (appNotifications.size > MAX_NOTIFICATIONS_PER_APP) {
            appNotifications.removeAt(appNotifications.size - 1)
        }

        // Save to persistent storage
        saveToPrefs()
    }

    /**
     * Gets all apps that have sent notifications, sorted by most recent.
     * Returns a list of (storageKey, notificationList) pairs where storageKey is "packageName|profileType".
     */
    fun getAppsWithNotifications(): List<Pair<String, List<InterceptedNotification>>> {
        return notifications.entries
            .map { (storageKey, notificationList) ->
                storageKey to notificationList.toList()
            }
            .sortedByDescending { (_, notificationList) ->
                notificationList.firstOrNull()?.timestamp ?: 0L
            }
    }

    /**
     * Gets notifications for a specific app and profile combination.
     */
    fun getNotificationsForApp(packageName: String, profileType: ProfileType): List<InterceptedNotification> {
        val storageKey = getStorageKey(packageName, profileType)
        return notifications[storageKey]?.toList() ?: emptyList()
    }

    /**
     * Gets the app icon for a specific app and profile combination.
     */
    fun getAppIcon(packageName: String, profileType: ProfileType): String? {
        val storageKey = getStorageKey(packageName, profileType)
        return appIcons[storageKey]
    }

    /**
     * Gets the app icon by storage key.
     */
    fun getAppIconByKey(storageKey: String): String? {
        return appIcons[storageKey]
    }

    /**
     * Removes a specific notification by its key.
     */
    fun removeNotification(packageName: String, profileType: ProfileType, notificationKey: String) {
        val storageKey = getStorageKey(packageName, profileType)
        val appNotifications = notifications[storageKey]
        appNotifications?.removeAll { it.key == notificationKey }

        // Remove the app entry if no notifications left
        if (appNotifications?.isEmpty() == true) {
            notifications.remove(storageKey)
        }

        saveToPrefs()
    }

    /**
     * Removes all notifications for a specific app + profile combination.
     */
    fun removeApp(packageName: String, profileType: ProfileType) {
        val storageKey = getStorageKey(packageName, profileType)
        notifications.remove(storageKey)
        appIcons.remove(storageKey)
        saveToPrefs()
    }

    /**
     * Clears all stored notifications.
     */
    fun clear() {
        notifications.clear()
        appIcons.clear()
        saveToPrefs()
    }

    /**
     * Gets the total number of apps with notifications (each app+profile combination counted separately).
     */
    fun getAppCount(): Int {
        return notifications.size
    }

    /**
     * Gets the total number of notifications stored.
     */
    fun getTotalNotificationCount(): Int {
        return notifications.values.sumOf { it.size }
    }

    /**
     * Saves notifications and icons to SharedPreferences.
     */
    private fun saveToPrefs() {
        sharedPrefs?.let { prefs ->
            try {
                val editor = prefs.edit()

                // Save notifications
                val notificationsToSave = notifications.mapValues { it.value.toList() }
                val notificationsJson = gson.toJson(notificationsToSave)
                editor.putString(KEY_NOTIFICATIONS, notificationsJson)

                // Save app icons separately
                val iconsToSave = appIcons.toMap()
                val iconsJson = gson.toJson(iconsToSave)
                editor.putString(KEY_APP_ICONS, iconsJson)

                editor.apply()
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    /**
     * Loads notifications and icons from SharedPreferences.
     */
    private fun loadFromPrefs() {
        sharedPrefs?.let { prefs ->
            try {
                // Load notifications
                val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, null)
                if (notificationsJson != null) {
                    val type = object : TypeToken<Map<String, List<InterceptedNotification>>>() {}.type
                    val loadedData: Map<String, List<InterceptedNotification>> = gson.fromJson(notificationsJson, type)
                    notifications.clear()
                    loadedData.forEach { (key, notificationList) ->
                        notifications[key] = notificationList.toMutableList()
                    }
                }

                // Load app icons
                val iconsJson = prefs.getString(KEY_APP_ICONS, null)
                if (iconsJson != null) {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val loadedIcons: Map<String, String> = gson.fromJson(iconsJson, type)
                    appIcons.clear()
                    appIcons.putAll(loadedIcons)
                }
            } catch (e: Exception) {
                // If loading fails, start fresh
                notifications.clear()
                appIcons.clear()
            }
        }
    }
}

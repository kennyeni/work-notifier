package com.worknotifier.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

/**
 * Regex filters for an app's notifications.
 * Logic: If include patterns exist, notification must match at least one.
 * Then, notification must NOT match any exclude pattern.
 */
data class RegexFilters(
    val includePatterns: MutableList<String> = mutableListOf(),
    val excludePatterns: MutableList<String> = mutableListOf()
)

/**
 * Singleton to store intercepted notifications in memory and persistently.
 * Keeps the last 10 notifications per app per profile.
 */
object NotificationStorage {

    private const val MAX_NOTIFICATIONS_PER_APP = 10
    private const val PREFS_NAME = "notification_storage"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_APP_ICONS = "app_icons"
    private const val KEY_MIMIC_ENABLED = "mimic_enabled"
    private const val KEY_REGEX_FILTERS = "regex_filters"

    // Map key: "packageName|profileType"
    private val notifications = ConcurrentHashMap<String, MutableList<InterceptedNotification>>()
    // Separate storage for app icons to avoid duplication
    private val appIcons = ConcurrentHashMap<String, String>()
    // Track which app+profile combinations should be mimicked
    private val mimicEnabled = ConcurrentHashMap<String, Boolean>()
    // Regex filters for each app+profile combination
    private val regexFilters = ConcurrentHashMap<String, RegexFilters>()
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
     * Generates a content hash for deduplication based on title and text.
     * Returns a consistent hash for notifications with identical content.
     */
    fun getContentHash(title: String?, text: String?): String {
        val normalizedTitle = title?.trim() ?: ""
        val normalizedText = text?.trim() ?: ""
        return "$normalizedTitle|$normalizedText".hashCode().toString()
    }

    /**
     * Adds a notification to storage.
     * Keeps only the most recent MAX_NOTIFICATIONS_PER_APP notifications per app per profile.
     * Deduplicates notifications based on content (title+text) to avoid showing duplicates.
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

        // Remove any existing notification with the same key OR same content
        // This prevents duplicates even when apps use different keys for same content
        appNotifications.removeAll {
            it.key == notification.key ||
            (it.title == notification.title && it.text == notification.text)
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
        mimicEnabled.clear()
        regexFilters.clear()
        saveToPrefs()
    }

    /**
     * Sets whether notifications from this app+profile should be mimicked.
     */
    fun setMimicEnabled(packageName: String, profileType: ProfileType, enabled: Boolean) {
        val storageKey = getStorageKey(packageName, profileType)
        if (enabled) {
            mimicEnabled[storageKey] = true
        } else {
            mimicEnabled.remove(storageKey)
        }
        saveToPrefs()
    }

    /**
     * Checks if notifications from this app+profile should be mimicked.
     */
    fun isMimicEnabled(packageName: String, profileType: ProfileType): Boolean {
        val storageKey = getStorageKey(packageName, profileType)
        return mimicEnabled[storageKey] == true
    }

    /**
     * Gets regex filters for an app+profile combination.
     */
    fun getRegexFilters(packageName: String, profileType: ProfileType): RegexFilters {
        val storageKey = getStorageKey(packageName, profileType)
        return regexFilters.getOrPut(storageKey) { RegexFilters() }
    }

    /**
     * Sets regex filters for an app+profile combination.
     */
    fun setRegexFilters(packageName: String, profileType: ProfileType, filters: RegexFilters) {
        val storageKey = getStorageKey(packageName, profileType)
        regexFilters[storageKey] = filters
        saveToPrefs()
    }

    /**
     * Evaluates if a notification matches the configured regex filters.
     * Returns true if the notification should be shown (passes filters).
     *
     * Logic:
     * 1. If include patterns exist, notification must match at least one include pattern
     * 2. Then, notification must NOT match any exclude pattern
     */
    fun matchesFilters(notification: InterceptedNotification): Boolean {
        val filters = getRegexFilters(notification.packageName, notification.profileType)

        // Combine title and text for matching
        val content = "${notification.title ?: ""} ${notification.text ?: ""}".trim()

        // Filter out blank patterns before checking
        val validIncludePatterns = filters.includePatterns.filter { it.isNotBlank() }
        val validExcludePatterns = filters.excludePatterns.filter { it.isNotBlank() }

        // If include patterns exist, must match at least one
        if (validIncludePatterns.isNotEmpty()) {
            val matchesInclude = validIncludePatterns.any { pattern ->
                try {
                    content.contains(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    false // Invalid regex = no match
                }
            }
            if (!matchesInclude) {
                return false
            }
        }

        // Must NOT match any exclude pattern
        if (validExcludePatterns.isNotEmpty()) {
            val matchesExclude = validExcludePatterns.any { pattern ->
                try {
                    content.contains(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    false // Invalid regex = no match
                }
            }
            if (matchesExclude) {
                return false
            }
        }

        return true
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

                // Save mimic enabled states
                val mimicToSave = mimicEnabled.toMap()
                val mimicJson = gson.toJson(mimicToSave)
                editor.putString(KEY_MIMIC_ENABLED, mimicJson)

                // Save regex filters
                val filtersToSave = regexFilters.toMap()
                val filtersJson = gson.toJson(filtersToSave)
                editor.putString(KEY_REGEX_FILTERS, filtersJson)

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

                // Load mimic enabled states
                val mimicJson = prefs.getString(KEY_MIMIC_ENABLED, null)
                if (mimicJson != null) {
                    val type = object : TypeToken<Map<String, Boolean>>() {}.type
                    val loadedMimic: Map<String, Boolean> = gson.fromJson(mimicJson, type)
                    mimicEnabled.clear()
                    mimicEnabled.putAll(loadedMimic)
                }

                // Load regex filters
                val filtersJson = prefs.getString(KEY_REGEX_FILTERS, null)
                if (filtersJson != null) {
                    val type = object : TypeToken<Map<String, RegexFilters>>() {}.type
                    val loadedFilters: Map<String, RegexFilters> = gson.fromJson(filtersJson, type)
                    regexFilters.clear()
                    regexFilters.putAll(loadedFilters)
                }
            } catch (e: Exception) {
                // If loading fails, start fresh
                notifications.clear()
                appIcons.clear()
                mimicEnabled.clear()
                regexFilters.clear()
            }
        }
    }
}

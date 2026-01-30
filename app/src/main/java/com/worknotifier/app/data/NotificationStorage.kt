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
    val includePatterns: MutableList<FilterPattern> = mutableListOf(),
    val excludePatterns: MutableList<FilterPattern> = mutableListOf()
)

/**
 * Enum for which field the filter match was found in.
 */
enum class MatchFieldType {
    TITLE, CONTENT
}

/**
 * Represents match information for a filter pattern.
 */
data class FilterMatch(
    val pattern: FilterPattern,
    val matchedText: String,
    val startIndex: Int,
    val endIndex: Int,
    val isInclude: Boolean,
    val fieldType: MatchFieldType
)

/**
 * Singleton to store intercepted notifications in memory and persistently.
 * Keeps the last 100 notifications per app per profile.
 */
object NotificationStorage {

    private const val MAX_NOTIFICATIONS_PER_APP = 100
    private const val PREFS_NAME = "notification_storage"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_APP_ICONS = "app_icons"
    private const val KEY_MIMIC_ENABLED = "mimic_enabled"
    private const val KEY_REGEX_FILTERS = "regex_filters"
    private const val KEY_ANDROID_AUTO_ONLY = "android_auto_only"
    private const val KEY_DISABLED_APPS = "disabled_apps"

    // Map key: "packageName|profileType"
    private val notifications = ConcurrentHashMap<String, MutableList<InterceptedNotification>>()
    // Separate storage for app icons to avoid duplication
    private val appIcons = ConcurrentHashMap<String, String>()
    // Track which app+profile combinations should be mimicked
    private val mimicEnabled = ConcurrentHashMap<String, Boolean>()
    // Regex filters for each app+profile combination
    private val regexFilters = ConcurrentHashMap<String, RegexFilters>()
    // Track disabled apps (won't show in list)
    private val disabledApps = ConcurrentHashMap<String, Boolean>()
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
     * Gets all apps that have sent notifications, filtered by disabled status and sorted.
     * Returns a list of (storageKey, notificationList) pairs where storageKey is "packageName|profileType".
     *
     * Sorting:
     * 1. Enabled apps first (mimic enabled), then disabled (mimic disabled)
     * 2. Within each group, sort alphabetically by app name
     */
    fun getAppsWithNotifications(): List<Pair<String, List<InterceptedNotification>>> {
        return notifications.entries
            .filter { (storageKey, _) ->
                // Filter out disabled apps
                disabledApps[storageKey] != true
            }
            .map { (storageKey, notificationList) ->
                storageKey to notificationList.toList()
            }
            .sortedWith(
                compareByDescending<Pair<String, List<InterceptedNotification>>> { (storageKey, _) ->
                    // First sort by mimic enabled (true first)
                    mimicEnabled[storageKey] == true
                }.thenBy { (_, notificationList) ->
                    // Then sort alphabetically by app name
                    notificationList.firstOrNull()?.appName?.lowercase() ?: ""
                }
            )
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
        disabledApps.clear()
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
     * Disables an app completely - it will not show in the list.
     * Different from dismiss which clears history but allows app to reappear.
     */
    fun disableApp(packageName: String, profileType: ProfileType) {
        val storageKey = getStorageKey(packageName, profileType)
        disabledApps[storageKey] = true
        saveToPrefs()
    }

    /**
     * Re-enables a previously disabled app.
     */
    fun enableApp(packageName: String, profileType: ProfileType) {
        val storageKey = getStorageKey(packageName, profileType)
        disabledApps.remove(storageKey)
        saveToPrefs()
    }

    /**
     * Checks if an app is disabled.
     */
    fun isAppDisabled(packageName: String, profileType: ProfileType): Boolean {
        val storageKey = getStorageKey(packageName, profileType)
        return disabledApps[storageKey] == true
    }

    /**
     * Gets all disabled apps with their notifications.
     * Returns a list of (storageKey, notificationList) pairs.
     */
    fun getDisabledApps(): List<Pair<String, List<InterceptedNotification>>> {
        return notifications.entries
            .filter { (storageKey, _) ->
                disabledApps[storageKey] == true
            }
            .map { (storageKey, notificationList) ->
                storageKey to notificationList.toList()
            }
            .sortedBy { (_, notificationList) ->
                notificationList.firstOrNull()?.appName?.lowercase() ?: ""
            }
    }

    /**
     * Sets the global Android Auto only mode.
     * When enabled, mimic notifications are only generated when connected to Android Auto.
     * When disabled, mimic notifications are always generated (if app has mimic enabled).
     */
    fun setAndroidAutoOnlyMode(enabled: Boolean) {
        sharedPrefs?.edit()?.putBoolean(KEY_ANDROID_AUTO_ONLY, enabled)?.apply()
    }

    /**
     * Gets the global Android Auto only mode setting.
     * Returns false by default (always generate mimics).
     */
    fun isAndroidAutoOnlyMode(): Boolean {
        return sharedPrefs?.getBoolean(KEY_ANDROID_AUTO_ONLY, false) ?: false
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

        // Filter out blank patterns before checking
        val validIncludePatterns = filters.includePatterns.filter { it.pattern.isNotBlank() }
        val validExcludePatterns = filters.excludePatterns.filter { it.pattern.isNotBlank() }

        // If include patterns exist, must match at least one
        if (validIncludePatterns.isNotEmpty()) {
            val matchesInclude = validIncludePatterns.any { pattern ->
                patternMatchesField(pattern, notification.title, notification.text)
            }
            if (!matchesInclude) {
                return false
            }
        }

        // Must NOT match any exclude pattern
        if (validExcludePatterns.isNotEmpty()) {
            val matchesExclude = validExcludePatterns.any { pattern ->
                patternMatchesField(pattern, notification.title, notification.text)
            }
            if (matchesExclude) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if a pattern matches the notification fields based on pattern settings.
     */
    private fun patternMatchesField(pattern: FilterPattern, title: String?, text: String?): Boolean {
        try {
            val regex = Regex(pattern.pattern, RegexOption.IGNORE_CASE)

            val titleMatches = pattern.matchTitle && title != null && regex.containsMatchIn(title)
            val contentMatches = pattern.matchContent && text != null && regex.containsMatchIn(text)

            return titleMatches || contentMatches
        } catch (e: Exception) {
            return false // Invalid regex = no match
        }
    }

    /**
     * Gets all filter matches for a notification (for highlighting).
     * Returns a list of FilterMatch objects containing match details.
     */
    fun getFilterMatches(notification: InterceptedNotification): List<FilterMatch> {
        val filters = getRegexFilters(notification.packageName, notification.profileType)
        val matches = mutableListOf<FilterMatch>()

        // Check include patterns
        val validIncludePatterns = filters.includePatterns.filter { it.pattern.isNotBlank() }
        validIncludePatterns.forEach { pattern ->
            findMatchInFields(pattern, notification.title, notification.text, true)?.let {
                matches.add(it)
            }
        }

        // Check exclude patterns
        val validExcludePatterns = filters.excludePatterns.filter { it.pattern.isNotBlank() }
        validExcludePatterns.forEach { pattern ->
            findMatchInFields(pattern, notification.title, notification.text, false)?.let {
                matches.add(it)
            }
        }

        return matches
    }

    /**
     * Finds the last (most recent) match in title or content.
     * Returns the match details or null if no match.
     * Prioritizes content matches over title matches.
     */
    private fun findMatchInFields(
        pattern: FilterPattern,
        title: String?,
        text: String?,
        isInclude: Boolean
    ): FilterMatch? {
        try {
            val regex = Regex(pattern.pattern, RegexOption.IGNORE_CASE)

            // Try to find match in content (prioritized over title for display)
            if (pattern.matchContent && text != null) {
                val match = regex.find(text)
                if (match != null) {
                    return FilterMatch(
                        pattern = pattern,
                        matchedText = match.value,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        isInclude = isInclude,
                        fieldType = MatchFieldType.CONTENT
                    )
                }
            }

            // Try to find match in title
            if (pattern.matchTitle && title != null) {
                val match = regex.find(title)
                if (match != null) {
                    return FilterMatch(
                        pattern = pattern,
                        matchedText = match.value,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        isInclude = isInclude,
                        fieldType = MatchFieldType.TITLE
                    )
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
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

                // Save disabled apps
                val disabledToSave = disabledApps.toMap()
                val disabledJson = gson.toJson(disabledToSave)
                editor.putString(KEY_DISABLED_APPS, disabledJson)

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

                // Load disabled apps
                val disabledJson = prefs.getString(KEY_DISABLED_APPS, null)
                if (disabledJson != null) {
                    val type = object : TypeToken<Map<String, Boolean>>() {}.type
                    val loadedDisabled: Map<String, Boolean> = gson.fromJson(disabledJson, type)
                    disabledApps.clear()
                    disabledApps.putAll(loadedDisabled)
                }
            } catch (e: Exception) {
                // If loading fails, start fresh
                notifications.clear()
                appIcons.clear()
                mimicEnabled.clear()
                regexFilters.clear()
                disabledApps.clear()
            }
        }
    }
}

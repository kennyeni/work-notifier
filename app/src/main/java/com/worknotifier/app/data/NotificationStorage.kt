package com.worknotifier.app.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton to store intercepted notifications in memory.
 * Keeps the last 3 notifications per app.
 */
object NotificationStorage {

    private const val MAX_NOTIFICATIONS_PER_APP = 3
    private val notifications = ConcurrentHashMap<String, MutableList<InterceptedNotification>>()

    /**
     * Adds a notification to storage.
     * Keeps only the most recent MAX_NOTIFICATIONS_PER_APP notifications per app.
     * Deduplicates notifications based on their key to avoid showing duplicates.
     */
    fun addNotification(notification: InterceptedNotification) {
        val appNotifications = notifications.getOrPut(notification.packageName) {
            mutableListOf()
        }

        // Remove any existing notification with the same key (update case)
        appNotifications.removeAll { it.key == notification.key }

        // Add the new notification at the beginning
        appNotifications.add(0, notification)

        // Keep only the most recent notifications
        if (appNotifications.size > MAX_NOTIFICATIONS_PER_APP) {
            appNotifications.removeAt(appNotifications.size - 1)
        }
    }

    /**
     * Gets all apps that have sent notifications, sorted by most recent.
     */
    fun getAppsWithNotifications(): List<Pair<String, List<InterceptedNotification>>> {
        return notifications.entries
            .map { (packageName, notificationList) ->
                packageName to notificationList.toList()
            }
            .sortedByDescending { (_, notificationList) ->
                notificationList.firstOrNull()?.timestamp ?: 0L
            }
    }

    /**
     * Gets notifications for a specific app.
     */
    fun getNotificationsForApp(packageName: String): List<InterceptedNotification> {
        return notifications[packageName]?.toList() ?: emptyList()
    }

    /**
     * Clears all stored notifications.
     */
    fun clear() {
        notifications.clear()
    }

    /**
     * Gets the total number of apps with notifications.
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
}

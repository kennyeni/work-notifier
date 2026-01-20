package com.worknotifier.app.data

/**
 * Data class representing an intercepted notification.
 */
data class InterceptedNotification(
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val key: String,
    val isWorkProfile: Boolean = false
) {
    companion object {
        /**
         * Creates a short summary of the notification for display.
         */
        fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                else -> "${diff / 86400000}d ago"
            }
        }
    }
}

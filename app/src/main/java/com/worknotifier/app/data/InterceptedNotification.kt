package com.worknotifier.app.data

/**
 * Profile type for the notification source.
 */
enum class ProfileType {
    PERSONAL,
    WORK,
    PRIVATE
}

/**
 * Represents a single regex filter pattern with color and field matching options.
 * Supports matching against notification title, content, or both.
 */
data class FilterPattern(
    val pattern: String = "",
    val colorIndex: Int = 0,  // 0-14, cycles through color palette
    val matchTitle: Boolean = true,
    val matchContent: Boolean = true
)

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
    val profileType: ProfileType = ProfileType.PERSONAL,
    val userId: Int = 0,
    val appIconBase64: String? = null  // Base64 encoded app icon for cross-profile support
) {
    // Backwards compatibility
    val isWorkProfile: Boolean
        get() = profileType == ProfileType.WORK
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

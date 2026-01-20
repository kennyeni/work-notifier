package com.worknotifier.app

import android.app.IntentService
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * IntentService to handle notification actions for Android Auto compatibility.
 * This service processes reply and mark-as-read actions from notifications.
 */
class MessagingService : IntentService("MessagingService") {

    companion object {
        const val ACTION_REPLY = "com.worknotifier.app.ACTION_REPLY"
        const val ACTION_MARK_AS_READ = "com.worknotifier.app.ACTION_MARK_AS_READ"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val TAG = "MessagingService"
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_REPLY -> handleReply(intent, notificationId)
            ACTION_MARK_AS_READ -> handleMarkAsRead(notificationId)
        }
    }

    private fun handleReply(intent: Intent, notificationId: Int) {
        // Extract the reply text from the RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)
            Log.d(TAG, "Reply received: $replyText")

            // TODO: Process the reply (send to server, update conversation, etc.)
            // For now, we'll just log it and dismiss the notification

            // Dismiss the notification after replying
            NotificationManagerCompat.from(this).cancel(notificationId)
        }
    }

    private fun handleMarkAsRead(notificationId: Int) {
        Log.d(TAG, "Mark as read for notification: $notificationId")

        // TODO: Update the message status (mark as read in database, notify server, etc.)
        // For now, we'll just dismiss the notification

        // Dismiss the notification
        NotificationManagerCompat.from(this).cancel(notificationId)
    }
}

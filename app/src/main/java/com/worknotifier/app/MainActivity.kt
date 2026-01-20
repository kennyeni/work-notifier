package com.worknotifier.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ID = "work_notifier_messages"
        private const val NOTIFICATION_ID = 1001
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create notification channel
        createNotificationChannel()

        // Set up buttons
        findViewById<MaterialButton>(R.id.btnSendNotification).setOnClickListener {
            if (checkNotificationPermission()) {
                sendTestNotification()
            } else {
                requestNotificationPermission()
            }
        }

        findViewById<MaterialButton>(R.id.btnSelectApps).setOnClickListener {
            // Launch the InterceptedAppsActivity
            val intent = Intent(this, InterceptedAppsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No runtime permission needed for Android 12 and below
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendTestNotification()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission is required to send notifications",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sendTestNotification() {
        // Create the sender person
        val sender = Person.Builder()
            .setName(getString(R.string.test_notification_sender))
            .setKey("sender_1")
            .setImportant(true)
            .build()

        // Create the device user (the recipient)
        val deviceUser = Person.Builder()
            .setName("Me")
            .setKey("device_user")
            .build()

        // Create MessagingStyle with unread message
        val messagingStyle = NotificationCompat.MessagingStyle(deviceUser)
            .setConversationTitle(getString(R.string.test_notification_title))
            .addMessage(
                getString(R.string.test_notification_content),
                System.currentTimeMillis(),
                sender
            )

        // Create reply action
        val replyAction = createReplyAction()

        // Create mark as read action
        val markAsReadAction = createMarkAsReadAction()

        // Build the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .addAction(replyAction)
            .addAction(markAsReadAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, notification)
                Toast.makeText(
                    this@MainActivity,
                    R.string.notification_posted,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun createReplyAction(): NotificationCompat.Action {
        val replyIntent = Intent(this, MessagingService::class.java).apply {
            action = MessagingService.ACTION_REPLY
            putExtra(MessagingService.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }

        val replyPendingIntent = PendingIntent.getService(
            this,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder(MessagingService.KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        return NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            getString(R.string.reply_action),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun createMarkAsReadAction(): NotificationCompat.Action {
        val markAsReadIntent = Intent(this, MessagingService::class.java).apply {
            action = MessagingService.ACTION_MARK_AS_READ
            putExtra(MessagingService.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }

        val markAsReadPendingIntent = PendingIntent.getService(
            this,
            1,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_mark_read,
            getString(R.string.mark_as_read_action),
            markAsReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }
}

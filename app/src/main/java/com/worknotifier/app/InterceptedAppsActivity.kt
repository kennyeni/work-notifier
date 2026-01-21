package com.worknotifier.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.worknotifier.app.data.InterceptedNotification
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType

/**
 * Activity that displays all intercepted apps and their recent notifications.
 */
class InterceptedAppsActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnEnablePermission: Button
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercepted_apps)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.intercepted_apps_title)

        // Initialize views
        rvApps = findViewById(R.id.rvApps)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnEnablePermission = findViewById(R.id.btnEnablePermission)

        // Set up RecyclerView
        adapter = AppsAdapter(this)
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter

        // Set up permission button
        btnEnablePermission.setOnClickListener {
            openNotificationListenerSettings()
        }

        // Check permission status
        checkPermissionStatus()

        // Load data
        loadInterceptedApps()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data and permission status when returning to this activity
        checkPermissionStatus()
        loadInterceptedApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Checks if the notification listener permission is granted.
     */
    private fun checkPermissionStatus() {
        val isEnabled = isNotificationListenerEnabled()
        btnEnablePermission.visibility = if (isEnabled) View.GONE else View.VISIBLE
    }

    /**
     * Checks if NotificationListenerService is enabled.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, NotificationInterceptorService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    /**
     * Opens the notification listener settings screen.
     */
    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    /**
     * Loads and displays intercepted apps.
     */
    private fun loadInterceptedApps() {
        val appsWithNotifications = NotificationStorage.getAppsWithNotifications()

        if (appsWithNotifications.isEmpty()) {
            rvApps.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            tvStatus.text = getString(R.string.no_apps_intercepted)
        } else {
            rvApps.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE

            val appCount = NotificationStorage.getAppCount()
            val notificationCount = NotificationStorage.getTotalNotificationCount()
            tvStatus.text = getString(
                R.string.intercepted_status,
                appCount,
                notificationCount
            )

            adapter.setData(appsWithNotifications)
        }
    }

    /**
     * RecyclerView adapter for displaying apps with their notifications.
     */
    private class AppsAdapter(private val context: Context) :
        RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        private var data: List<Pair<String, List<InterceptedNotification>>> = emptyList()

        fun setData(newData: List<Pair<String, List<InterceptedNotification>>>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_with_notifications, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val (packageName, notifications) = data[position]
            holder.bind(context, packageName, notifications)
        }

        override fun getItemCount(): Int = data.size

        class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
            private val tvWorkProfileBadge: TextView = itemView.findViewById(R.id.tvWorkProfileBadge)
            private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
            private val tvNotificationCount: TextView = itemView.findViewById(R.id.tvNotificationCount)
            private val llNotifications: LinearLayout = itemView.findViewById(R.id.llNotifications)

            fun bind(
                context: Context,
                packageName: String,
                notifications: List<InterceptedNotification>
            ) {
                // Set app icon
                val appIcon = getAppIcon(context, packageName)
                if (appIcon != null) {
                    ivAppIcon.setImageDrawable(appIcon)
                } else {
                    ivAppIcon.setImageResource(R.mipmap.ic_launcher)
                }

                // Set app name
                val appName = notifications.firstOrNull()?.appName ?: packageName
                tvAppName.text = appName

                // Show appropriate profile badge
                val profileTypes = notifications.map { it.profileType }.distinct()
                when {
                    profileTypes.contains(ProfileType.PRIVATE) -> {
                        // Show PRIVATE badge (takes priority if both exist)
                        tvWorkProfileBadge.visibility = View.VISIBLE
                        tvWorkProfileBadge.text = context.getString(R.string.private_profile_badge)
                        tvWorkProfileBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.private_profile_badge)
                        )
                        tvWorkProfileBadge.setBackgroundResource(R.drawable.private_profile_badge_bg)
                    }
                    profileTypes.contains(ProfileType.WORK) -> {
                        // Show WORK badge
                        tvWorkProfileBadge.visibility = View.VISIBLE
                        tvWorkProfileBadge.text = context.getString(R.string.work_profile_badge)
                        tvWorkProfileBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.work_profile_badge)
                        )
                        tvWorkProfileBadge.setBackgroundResource(R.drawable.work_profile_badge_bg)
                    }
                    else -> {
                        // No special profile, hide badge
                        tvWorkProfileBadge.visibility = View.GONE
                    }
                }

                // Set package name
                tvPackageName.text = packageName

                // Set notification count
                tvNotificationCount.text = notifications.size.toString()

                // Clear previous notifications
                llNotifications.removeAllViews()

                // Add notification items
                notifications.forEach { notification ->
                    val notificationView = LayoutInflater.from(context)
                        .inflate(R.layout.item_notification, llNotifications, false)

                    val tvTitle: TextView = notificationView.findViewById(R.id.tvNotificationTitle)
                    val tvText: TextView = notificationView.findViewById(R.id.tvNotificationText)
                    val tvTime: TextView = notificationView.findViewById(R.id.tvNotificationTime)
                    val tvExpandCollapse: TextView = notificationView.findViewById(R.id.tvExpandCollapse)

                    tvTitle.text = notification.title ?: context.getString(R.string.no_title)
                    val notificationText = notification.text ?: context.getString(R.string.no_content)
                    tvTime.text = InterceptedNotification.formatTimestamp(notification.timestamp)

                    // Handle empty text
                    if (notification.text.isNullOrBlank()) {
                        tvText.visibility = View.GONE
                        tvExpandCollapse.visibility = View.GONE
                    } else {
                        tvText.visibility = View.VISIBLE
                        tvText.text = notificationText

                        // Set up expand/collapse
                        var isExpanded = false
                        tvText.maxLines = 2
                        tvText.post {
                            val lineCount = tvText.lineCount
                            if (lineCount > 2) {
                                tvExpandCollapse.visibility = View.VISIBLE
                                tvExpandCollapse.text = context.getString(R.string.show_more)

                                tvExpandCollapse.setOnClickListener {
                                    isExpanded = !isExpanded
                                    if (isExpanded) {
                                        tvText.maxLines = Int.MAX_VALUE
                                        tvExpandCollapse.text = context.getString(R.string.show_less)
                                    } else {
                                        tvText.maxLines = 2
                                        tvExpandCollapse.text = context.getString(R.string.show_more)
                                    }
                                }
                            } else {
                                tvExpandCollapse.visibility = View.GONE
                            }
                        }
                    }

                    llNotifications.addView(notificationView)
                }
            }

            private fun getAppIcon(context: Context, packageName: String): Drawable? {
                return try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }
    }
}

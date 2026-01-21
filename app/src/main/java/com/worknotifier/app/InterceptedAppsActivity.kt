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
        adapter = AppsAdapter(this, this)
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
    private class AppsAdapter(private val context: Context, private val activity: InterceptedAppsActivity) :
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
            val (storageKey, notifications) = data[position]
            holder.bind(context, activity, storageKey, notifications)
        }

        override fun getItemCount(): Int = data.size

        class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
            private val tvWorkProfileBadge: TextView = itemView.findViewById(R.id.tvWorkProfileBadge)
            private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
            private val tvNotificationCount: TextView = itemView.findViewById(R.id.tvNotificationCount)
            private val llNotifications: LinearLayout = itemView.findViewById(R.id.llNotifications)
            private val btnToggleNotifications: Button = itemView.findViewById(R.id.btnToggleNotifications)
            private val btnDismissApp: Button = itemView.findViewById(R.id.btnDismissApp)

            private var isExpanded = true

            fun bind(
                context: Context,
                activity: InterceptedAppsActivity,
                storageKey: String,
                notifications: List<InterceptedNotification>
            ) {
                // Parse storage key: "packageName|profileType"
                val parts = storageKey.split("|")
                val packageName = parts[0]
                val profileType = if (parts.size > 1) {
                    try {
                        ProfileType.valueOf(parts[1])
                    } catch (e: Exception) {
                        ProfileType.PERSONAL
                    }
                } else {
                    ProfileType.PERSONAL
                }

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

                // Show profile badge based on the profile type
                when (profileType) {
                    ProfileType.PRIVATE -> {
                        tvWorkProfileBadge.visibility = View.VISIBLE
                        tvWorkProfileBadge.text = context.getString(R.string.private_profile_badge)
                        tvWorkProfileBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.private_profile_badge)
                        )
                        tvWorkProfileBadge.setBackgroundResource(R.drawable.private_profile_badge_bg)
                    }
                    ProfileType.WORK -> {
                        tvWorkProfileBadge.visibility = View.VISIBLE
                        tvWorkProfileBadge.text = context.getString(R.string.work_profile_badge)
                        tvWorkProfileBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.work_profile_badge)
                        )
                        tvWorkProfileBadge.setBackgroundResource(R.drawable.work_profile_badge_bg)
                    }
                    ProfileType.PERSONAL -> {
                        tvWorkProfileBadge.visibility = View.GONE
                    }
                }

                // Set package name with profile indicator
                tvPackageName.text = packageName

                // Set notification count
                tvNotificationCount.text = notifications.size.toString()

                // Set up collapse/expand button
                btnToggleNotifications.text = if (isExpanded)
                    context.getString(R.string.collapse_notifications)
                else
                    context.getString(R.string.expand_notifications)

                btnToggleNotifications.setOnClickListener {
                    isExpanded = !isExpanded
                    llNotifications.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    btnToggleNotifications.text = if (isExpanded)
                        context.getString(R.string.collapse_notifications)
                    else
                        context.getString(R.string.expand_notifications)
                }

                // Set up dismiss app button
                btnDismissApp.setOnClickListener {
                    NotificationStorage.removeApp(packageName, profileType)
                    activity.loadInterceptedApps()
                }

                // Clear previous notifications
                llNotifications.removeAllViews()
                llNotifications.visibility = if (isExpanded) View.VISIBLE else View.GONE

                // Add notification items
                notifications.forEach { notification ->
                    val notificationView = LayoutInflater.from(context)
                        .inflate(R.layout.item_notification, llNotifications, false)

                    val tvTitle: TextView = notificationView.findViewById(R.id.tvNotificationTitle)
                    val tvText: TextView = notificationView.findViewById(R.id.tvNotificationText)
                    val tvTime: TextView = notificationView.findViewById(R.id.tvNotificationTime)
                    val tvExpandCollapse: TextView = notificationView.findViewById(R.id.tvExpandCollapse)
                    val btnDismissNotification: Button = notificationView.findViewById(R.id.btnDismissNotification)

                    tvTitle.text = notification.title ?: context.getString(R.string.no_title)
                    val notificationText = notification.text ?: context.getString(R.string.no_content)
                    tvTime.text = InterceptedNotification.formatTimestamp(notification.timestamp)

                    // Set up dismiss notification button
                    btnDismissNotification.setOnClickListener {
                        NotificationStorage.removeNotification(packageName, profileType, notification.key)
                        activity.loadInterceptedApps()
                    }

                    // Handle empty text
                    if (notification.text.isNullOrBlank()) {
                        tvText.visibility = View.GONE
                        tvExpandCollapse.visibility = View.GONE
                    } else {
                        tvText.visibility = View.VISIBLE
                        tvText.text = notificationText

                        // Set up expand/collapse
                        var isTextExpanded = false
                        tvText.maxLines = 2
                        tvText.post {
                            val lineCount = tvText.lineCount
                            if (lineCount > 2) {
                                tvExpandCollapse.visibility = View.VISIBLE
                                tvExpandCollapse.text = context.getString(R.string.show_more)

                                tvExpandCollapse.setOnClickListener {
                                    isTextExpanded = !isTextExpanded
                                    if (isTextExpanded) {
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

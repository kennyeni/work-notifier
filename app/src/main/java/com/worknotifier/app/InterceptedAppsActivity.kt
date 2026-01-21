package com.worknotifier.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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
import com.worknotifier.app.data.RegexFilters

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
            private val cbMimicNotifications: CheckBox = itemView.findViewById(R.id.cbMimicNotifications)
            private val llNotifications: LinearLayout = itemView.findViewById(R.id.llNotifications)
            private val btnToggleNotifications: Button = itemView.findViewById(R.id.btnToggleNotifications)
            private val btnDismissApp: Button = itemView.findViewById(R.id.btnDismissApp)
            private val btnToggleFilters: Button = itemView.findViewById(R.id.btnToggleFilters)
            private val llFiltersSection: LinearLayout = itemView.findViewById(R.id.llFiltersSection)
            private val llIncludePatterns: LinearLayout = itemView.findViewById(R.id.llIncludePatterns)
            private val llExcludePatterns: LinearLayout = itemView.findViewById(R.id.llExcludePatterns)
            private val btnAddIncludePattern: Button = itemView.findViewById(R.id.btnAddIncludePattern)
            private val btnAddExcludePattern: Button = itemView.findViewById(R.id.btnAddExcludePattern)

            private var isExpanded = true
            private var isFiltersExpanded = false
            private val debounceHandler = Handler(Looper.getMainLooper())
            private val debounceDelay = 500L // milliseconds

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

                // Set app icon - try stored icon first, then PackageManager, then default
                val storedIconBase64 = NotificationStorage.getAppIconByKey(storageKey)
                var appIcon: Drawable? = null

                // Try stored icon first (works for all profiles)
                if (!storedIconBase64.isNullOrBlank()) {
                    appIcon = decodeBase64ToDrawable(context, storedIconBase64)
                }

                // Fallback to PackageManager (works for personal profile)
                if (appIcon == null) {
                    appIcon = getAppIconFromPackageManager(context, packageName)
                }

                // Set icon or use default
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

                // Set up mimic checkbox
                cbMimicNotifications.setOnCheckedChangeListener(null) // Clear previous listener
                cbMimicNotifications.isChecked = NotificationStorage.isMimicEnabled(packageName, profileType)
                cbMimicNotifications.setOnCheckedChangeListener { _, isChecked ->
                    val wasEnabled = NotificationStorage.isMimicEnabled(packageName, profileType)
                    NotificationStorage.setMimicEnabled(packageName, profileType, isChecked)

                    // If just enabled, immediately mimic the latest notification to show it's working
                    if (isChecked && !wasEnabled && notifications.isNotEmpty()) {
                        val latestNotification = notifications.first()
                        // Trigger mimic via intent to NotificationInterceptorService
                        val intent = android.content.Intent(context, NotificationInterceptorService::class.java)
                        intent.action = "MIMIC_NOTIFICATION"
                        intent.putExtra("packageName", packageName)
                        intent.putExtra("appName", latestNotification.appName)
                        intent.putExtra("title", latestNotification.title)
                        intent.putExtra("text", latestNotification.text)
                        intent.putExtra("profileType", profileType.name)
                        intent.putExtra("appIconBase64", NotificationStorage.getAppIcon(packageName, profileType))
                        context.startService(intent)
                    }
                }

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

                // Set up filter toggle button
                btnToggleFilters.text = if (isFiltersExpanded)
                    context.getString(R.string.hide_filters)
                else
                    context.getString(R.string.show_filters)

                btnToggleFilters.setOnClickListener {
                    isFiltersExpanded = !isFiltersExpanded
                    llFiltersSection.visibility = if (isFiltersExpanded) View.VISIBLE else View.GONE
                    btnToggleFilters.text = if (isFiltersExpanded)
                        context.getString(R.string.hide_filters)
                    else
                        context.getString(R.string.show_filters)
                }

                // Set up regex filters
                setupRegexFilters(context, packageName, profileType, notifications)

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

                    // Apply visual indicator based on filter match
                    val matchesFilters = NotificationStorage.matchesFilters(notification)
                    if (matchesFilters) {
                        // Matching notification - normal appearance
                        notificationView.alpha = 1.0f
                    } else {
                        // Non-matching notification - grayed out
                        notificationView.alpha = 0.35f
                    }

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

            /**
             * Sets up the regex filters UI for this app.
             */
            private fun setupRegexFilters(
                context: Context,
                packageName: String,
                profileType: ProfileType,
                notifications: List<InterceptedNotification>
            ) {
                val filters = NotificationStorage.getRegexFilters(packageName, profileType)

                // Clear previous filter views
                llIncludePatterns.removeAllViews()
                llExcludePatterns.removeAllViews()

                // Populate include patterns
                filters.includePatterns.forEach { pattern ->
                    addFilterPatternView(context, packageName, profileType, pattern, true, notifications)
                }

                // Populate exclude patterns
                filters.excludePatterns.forEach { pattern ->
                    addFilterPatternView(context, packageName, profileType, pattern, false, notifications)
                }

                // Set up add pattern buttons
                btnAddIncludePattern.setOnClickListener {
                    val newPattern = ""
                    filters.includePatterns.add(newPattern)
                    NotificationStorage.setRegexFilters(packageName, profileType, filters)
                    addFilterPatternView(context, packageName, profileType, newPattern, true, notifications)
                }

                btnAddExcludePattern.setOnClickListener {
                    val newPattern = ""
                    filters.excludePatterns.add(newPattern)
                    NotificationStorage.setRegexFilters(packageName, profileType, filters)
                    addFilterPatternView(context, packageName, profileType, newPattern, false, notifications)
                }
            }

            /**
             * Adds a filter pattern view (EditText with remove button).
             */
            private fun addFilterPatternView(
                context: Context,
                packageName: String,
                profileType: ProfileType,
                pattern: String,
                isInclude: Boolean,
                notifications: List<InterceptedNotification>
            ) {
                val patternView = LayoutInflater.from(context)
                    .inflate(R.layout.item_filter_pattern, null, false)

                val etPattern: android.widget.EditText = patternView.findViewById(R.id.etPattern)
                val btnRemove: Button = patternView.findViewById(R.id.btnRemovePattern)

                etPattern.setText(pattern)

                // Save pattern when text changes (with debouncing to reduce disk writes)
                var saveRunnable: Runnable? = null
                etPattern.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        // Cancel previous save if still pending
                        saveRunnable?.let { debounceHandler.removeCallbacks(it) }

                        // Schedule new save after delay
                        saveRunnable = Runnable {
                            val filters = NotificationStorage.getRegexFilters(packageName, profileType)
                            val index = if (isInclude) {
                                llIncludePatterns.indexOfChild(patternView)
                            } else {
                                llExcludePatterns.indexOfChild(patternView)
                            }

                            if (index >= 0) {
                                val newPattern = s.toString()
                                if (isInclude && index < filters.includePatterns.size) {
                                    filters.includePatterns[index] = newPattern
                                } else if (!isInclude && index < filters.excludePatterns.size) {
                                    filters.excludePatterns[index] = newPattern
                                }
                                NotificationStorage.setRegexFilters(packageName, profileType, filters)

                                // Update notification visual indicators
                                updateNotificationVisuals(notifications)
                            }
                        }
                        debounceHandler.postDelayed(saveRunnable!!, debounceDelay)
                    }
                })

                // Remove pattern
                btnRemove.setOnClickListener {
                    val filters = NotificationStorage.getRegexFilters(packageName, profileType)
                    val index = if (isInclude) {
                        llIncludePatterns.indexOfChild(patternView)
                    } else {
                        llExcludePatterns.indexOfChild(patternView)
                    }

                    if (index >= 0) {
                        if (isInclude && index < filters.includePatterns.size) {
                            filters.includePatterns.removeAt(index)
                        } else if (!isInclude && index < filters.excludePatterns.size) {
                            filters.excludePatterns.removeAt(index)
                        }
                        NotificationStorage.setRegexFilters(packageName, profileType, filters)

                        if (isInclude) {
                            llIncludePatterns.removeView(patternView)
                        } else {
                            llExcludePatterns.removeView(patternView)
                        }

                        // Update notification visual indicators
                        updateNotificationVisuals(notifications)
                    }
                }

                // Add to appropriate container
                if (isInclude) {
                    llIncludePatterns.addView(patternView)
                } else {
                    llExcludePatterns.addView(patternView)
                }
            }

            /**
             * Updates notification visual indicators based on filter matches.
             */
            private fun updateNotificationVisuals(notifications: List<InterceptedNotification>) {
                // Update the alpha of each notification view based on filter match
                for (i in 0 until llNotifications.childCount) {
                    val notificationView = llNotifications.getChildAt(i)
                    if (i < notifications.size) {
                        val notification = notifications[i]
                        val matchesFilters = NotificationStorage.matchesFilters(notification)
                        notificationView.alpha = if (matchesFilters) 1.0f else 0.35f
                    }
                }
            }

            /**
             * Decodes a Base64 encoded string back to a Drawable.
             */
            private fun decodeBase64ToDrawable(context: Context, base64: String): Drawable? {
                return try {
                    // Try NO_WRAP first (new format), then DEFAULT (old format) for compatibility
                    val decodedBytes = try {
                        Base64.decode(base64, Base64.NO_WRAP)
                    } catch (e: Exception) {
                        Base64.decode(base64, Base64.DEFAULT)
                    }
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        BitmapDrawable(context.resources, bitmap)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            /**
             * Gets app icon from PackageManager (fallback for personal profile apps).
             */
            private fun getAppIconFromPackageManager(context: Context, packageName: String): Drawable? {
                return try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }
    }
}

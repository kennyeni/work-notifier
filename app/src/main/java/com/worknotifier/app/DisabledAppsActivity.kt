package com.worknotifier.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.worknotifier.app.data.InterceptedNotification
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType

/**
 * Activity that displays all disabled apps and allows re-enabling them.
 */
class DisabledAppsActivity : AppCompatActivity() {

    private lateinit var rvDisabledApps: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var adapter: DisabledAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disabled_apps)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.disabled_apps_title)

        // Initialize views
        rvDisabledApps = findViewById(R.id.rvDisabledApps)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        // Set up RecyclerView
        adapter = DisabledAppsAdapter(this, this)
        rvDisabledApps.layoutManager = LinearLayoutManager(this)
        rvDisabledApps.adapter = adapter

        // Load data
        loadDisabledApps()
    }

    override fun onResume() {
        super.onResume()
        loadDisabledApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Loads and displays disabled apps.
     */
    private fun loadDisabledApps() {
        val disabledApps = NotificationStorage.getDisabledApps()

        if (disabledApps.isEmpty()) {
            rvDisabledApps.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvDisabledApps.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
            adapter.setData(disabledApps)
        }
    }

    /**
     * RecyclerView adapter for displaying disabled apps.
     */
    private class DisabledAppsAdapter(private val context: Context, private val activity: DisabledAppsActivity) :
        RecyclerView.Adapter<DisabledAppsAdapter.DisabledAppViewHolder>() {

        private var data: List<Pair<String, List<InterceptedNotification>>> = emptyList()

        fun setData(newData: List<Pair<String, List<InterceptedNotification>>>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisabledAppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_disabled_app, parent, false)
            return DisabledAppViewHolder(view)
        }

        override fun onBindViewHolder(holder: DisabledAppViewHolder, position: Int) {
            val (storageKey, notifications) = data[position]
            holder.bind(context, activity, storageKey, notifications)
        }

        override fun getItemCount(): Int = data.size

        class DisabledAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
            private val tvWorkProfileBadge: TextView = itemView.findViewById(R.id.tvWorkProfileBadge)
            private val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
            private val btnEnableApp: Button = itemView.findViewById(R.id.btnEnableApp)

            fun bind(
                context: Context,
                activity: DisabledAppsActivity,
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
                val storedIconBase64 = NotificationStorage.getAppIconByKey(storageKey)
                var appIcon: Drawable? = null

                if (!storedIconBase64.isNullOrBlank()) {
                    appIcon = decodeBase64ToDrawable(context, storedIconBase64)
                }

                if (appIcon != null) {
                    ivAppIcon.setImageDrawable(appIcon)
                } else {
                    ivAppIcon.setImageResource(R.mipmap.ic_launcher)
                }

                // Set app name
                val appName = notifications.firstOrNull()?.appName ?: packageName
                tvAppName.text = appName

                // Show profile badge
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

                // Set package name
                tvPackageName.text = packageName

                // Set up enable app button
                btnEnableApp.setOnClickListener {
                    NotificationStorage.enableApp(packageName, profileType)
                    activity.loadDisabledApps()
                }
            }

            /**
             * Decodes a Base64 encoded string back to a Drawable.
             */
            private fun decodeBase64ToDrawable(context: Context, base64: String): Drawable? {
                return try {
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
        }
    }
}

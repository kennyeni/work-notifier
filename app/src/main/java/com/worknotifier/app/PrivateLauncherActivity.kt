package com.worknotifier.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType
import com.worknotifier.app.utils.RootUtils

/**
 * Data class representing a Private profile app.
 */
data class PrivateApp(
    val packageName: String,
    val appName: String,
    val appIconBase64: String?,
    val userHandle: UserHandle?
)

/**
 * Activity that displays all Private profile apps and allows creating launcher shortcuts.
 */
class PrivateLauncherActivity : AppCompatActivity() {

    private lateinit var rvPrivateApps: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var adapter: PrivateAppsAdapter

    companion object {
        private const val TAG = "PrivateLauncherActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_launcher)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.private_launcher_title)

        // Initialize views
        rvPrivateApps = findViewById(R.id.rvPrivateApps)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        // Set up RecyclerView with grid layout (2 columns)
        adapter = PrivateAppsAdapter(this)
        rvPrivateApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        rvPrivateApps.adapter = adapter

        // Load Private profile apps
        loadPrivateApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Loads all apps from the Private profile.
     * Uses root access to enumerate ALL apps, falls back to notification storage.
     */
    private fun loadPrivateApps() {
        try {
            val privateApps = mutableMapOf<String, PrivateApp>()

            // Method 1: Try root access to get ALL Private Space apps
            if (RootUtils.isRooted()) {
                try {
                    val privateUserId = RootUtils.getPrivateSpaceUserId()
                    if (privateUserId != null) {
                        Log.d(TAG, "Found Private Space with user ID: $privateUserId")

                        // Get all packages for Private Space user
                        val packages = RootUtils.getPackagesForUser(privateUserId)
                        Log.d(TAG, "Found ${packages.size} packages in Private Space")

                        // Filter to only user-installed apps (exclude system apps)
                        packages.forEach { packageName ->
                            try {
                                // Try to get app info from PackageManager
                                val appInfo = packageManager.getApplicationInfo(packageName, 0)

                                // Skip system apps
                                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0) {
                                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                                    // Try to get cached icon from notification storage first
                                    val storageKey = "$packageName|${ProfileType.PRIVATE.name}"
                                    val appIconBase64 = NotificationStorage.getAppIconByKey(storageKey)

                                    privateApps[packageName] = PrivateApp(
                                        packageName = packageName,
                                        appName = appName,
                                        appIconBase64 = appIconBase64,
                                        userHandle = null
                                    )
                                }
                            } catch (e: Exception) {
                                // Package might not be accessible from personal profile
                                // Try to get it from notification storage instead
                                val storageKey = "$packageName|${ProfileType.PRIVATE.name}"
                                val notifications = NotificationStorage.getNotificationsForApp(packageName, ProfileType.PRIVATE)
                                if (notifications.isNotEmpty()) {
                                    val appName = notifications.first().appName
                                    val appIconBase64 = NotificationStorage.getAppIconByKey(storageKey)

                                    privateApps[packageName] = PrivateApp(
                                        packageName = packageName,
                                        appName = appName,
                                        appIconBase64 = appIconBase64,
                                        userHandle = null
                                    )
                                }
                            }
                        }

                        Log.d(TAG, "Loaded ${privateApps.size} Private Space apps via root")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting Private Space apps via root", e)
                }
            }

            // Method 2: Fallback - get apps from notification history if root failed or not available
            if (privateApps.isEmpty()) {
                Log.d(TAG, "Root access unavailable or no apps found, falling back to notification storage")

                val appsWithNotifications = NotificationStorage.getAppsWithNotifications()
                appsWithNotifications.forEach { (storageKey, notifications) ->
                    // Parse storage key: "packageName|profileType"
                    val parts = storageKey.split("|")
                    if (parts.size >= 2) {
                        val packageName = parts[0]
                        val profileTypeStr = parts[1]

                        // Only include Private profile apps
                        if (profileTypeStr == ProfileType.PRIVATE.name) {
                            val appName = notifications.firstOrNull()?.appName ?: packageName
                            val appIconBase64 = NotificationStorage.getAppIconByKey(storageKey)

                            privateApps[packageName] = PrivateApp(
                                packageName = packageName,
                                appName = appName,
                                appIconBase64 = appIconBase64,
                                userHandle = null
                            )
                        }
                    }
                }

                Log.d(TAG, "Loaded ${privateApps.size} Private Space apps from notifications")
            }

            // Update UI
            if (privateApps.isEmpty()) {
                rvPrivateApps.visibility = View.GONE
                tvEmptyState.visibility = View.VISIBLE
            } else {
                rvPrivateApps.visibility = View.VISIBLE
                tvEmptyState.visibility = View.GONE

                // Sort apps alphabetically by name
                val sortedApps = privateApps.values.sortedBy { it.appName.lowercase() }
                adapter.setData(sortedApps)
            }

            Log.d(TAG, "Total: ${privateApps.size} Private Space apps loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Private Space apps", e)
            Toast.makeText(this, "Error loading Private apps", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Creates a pinned shortcut for a Private profile app.
     */
    fun createShortcut(privateApp: PrivateApp) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Shortcuts require Android 8.0+", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shortcutManager = getSystemService(ShortcutManager::class.java)

            if (!shortcutManager.isRequestPinShortcutSupported) {
                Toast.makeText(
                    this,
                    "Your launcher doesn't support shortcuts",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Check if shortcut already exists
            val existingShortcuts = shortcutManager.pinnedShortcuts
            val shortcutId = "private_${privateApp.packageName}"

            if (existingShortcuts.any { it.id == shortcutId }) {
                Toast.makeText(
                    this,
                    "Shortcut already exists for ${privateApp.appName}",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Create intent to launch the app
            // Note: Launching apps from other profiles requires the app to be in the same profile
            // or use cross-profile intent APIs. Since we're in the personal profile,
            // we'll create an intent that the launcher can handle.
            val launchIntent = packageManager.getLaunchIntentForPackage(privateApp.packageName)

            if (launchIntent == null) {
                // Try to create a basic launch intent
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(privateApp.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Create shortcut with icon
                val shortcutIcon = createShortcutIcon(privateApp)

                val shortcut = ShortcutInfo.Builder(this, shortcutId)
                    .setShortLabel(privateApp.appName)
                    .setLongLabel(privateApp.appName)
                    .setIcon(shortcutIcon)
                    .setIntent(intent)
                    .build()

                // Request to pin the shortcut
                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcut)
                shortcutManager.requestPinShortcut(shortcut, null)

                Toast.makeText(
                    this,
                    "Creating shortcut for ${privateApp.appName}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // We have a launch intent from PackageManager
                val shortcutIcon = createShortcutIcon(privateApp)

                val shortcut = ShortcutInfo.Builder(this, shortcutId)
                    .setShortLabel(privateApp.appName)
                    .setLongLabel(privateApp.appName)
                    .setIcon(shortcutIcon)
                    .setIntent(launchIntent)
                    .build()

                shortcutManager.requestPinShortcut(shortcut, null)

                Toast.makeText(
                    this,
                    "Creating shortcut for ${privateApp.appName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shortcut for ${privateApp.packageName}", e)
            Toast.makeText(
                this,
                "Error creating shortcut: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Creates an Icon for the shortcut from the app's icon.
     */
    private fun createShortcutIcon(privateApp: PrivateApp): Icon {
        // Try to use stored icon first
        if (!privateApp.appIconBase64.isNullOrBlank()) {
            try {
                val bitmap = decodeBase64ToBitmap(privateApp.appIconBase64)
                if (bitmap != null) {
                    return Icon.createWithBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode stored icon", e)
            }
        }

        // Fallback: try to get icon from PackageManager
        try {
            val appIcon = packageManager.getApplicationIcon(privateApp.packageName)
            val bitmap = drawableToBitmap(appIcon)
            return Icon.createWithBitmap(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Could not get icon from PackageManager", e)
        }

        // Last resort: use app icon
        return Icon.createWithResource(this, R.mipmap.ic_launcher)
    }

    /**
     * Converts a Drawable to a Bitmap.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Decodes a Base64 string to a Bitmap.
     */
    private fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = try {
                Base64.decode(base64, Base64.NO_WRAP)
            } catch (e: Exception) {
                Base64.decode(base64, Base64.DEFAULT)
            }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 to Bitmap", e)
            null
        }
    }

    /**
     * RecyclerView adapter for displaying Private profile apps.
     */
    private class PrivateAppsAdapter(private val activity: PrivateLauncherActivity) :
        RecyclerView.Adapter<PrivateAppsAdapter.PrivateAppViewHolder>() {

        private var data: List<PrivateApp> = emptyList()

        fun setData(newData: List<PrivateApp>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateAppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_private_app, parent, false)
            return PrivateAppViewHolder(view)
        }

        override fun onBindViewHolder(holder: PrivateAppViewHolder, position: Int) {
            val privateApp = data[position]
            holder.bind(activity, privateApp)
        }

        override fun getItemCount(): Int = data.size

        class PrivateAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivPrivateAppIcon)
            private val tvAppName: TextView = itemView.findViewById(R.id.tvPrivateAppName)

            fun bind(activity: PrivateLauncherActivity, privateApp: PrivateApp) {
                val context = itemView.context

                // Set app icon
                var appIcon: Drawable? = null
                if (!privateApp.appIconBase64.isNullOrBlank()) {
                    appIcon = decodeBase64ToDrawable(context, privateApp.appIconBase64)
                }

                if (appIcon == null) {
                    // Try to get icon from PackageManager
                    appIcon = getAppIconFromPackageManager(context, privateApp.packageName)
                }

                if (appIcon != null) {
                    ivAppIcon.setImageDrawable(appIcon)
                } else {
                    ivAppIcon.setImageResource(R.mipmap.ic_launcher)
                }

                // Set app name
                tvAppName.text = privateApp.appName

                // Make entire card clickable to create shortcut
                itemView.setOnClickListener {
                    activity.createShortcut(privateApp)
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

            /**
             * Gets app icon from PackageManager.
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

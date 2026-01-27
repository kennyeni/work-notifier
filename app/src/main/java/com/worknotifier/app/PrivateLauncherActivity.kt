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
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.worknotifier.app.data.NotificationStorage
import com.worknotifier.app.data.ProfileType
import com.worknotifier.app.utils.RootUtils
import com.worknotifier.app.utils.IconPackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: PrivateAppsAdapter

    companion object {
        private const val TAG = "PrivateLauncherActivity"

        // Icon pack packages to try (in priority order)
        // IconPackHelper will parse each pack's appfilter.xml to get proper icon mappings
        private val ICON_PACK_PACKAGES = listOf(
            "com.akbon.myd",             // DynIcons (Material You Dynamic Icon pack)
            "com.donnnno.arcticons",     // Arcticons
            "rk.android.app.shortcutmaker", // Icon Changer / QuickShortcutMaker
            "com.lx.launcher8",          // Launcher themes
            "ginlemon.flowerfree",       // Flower icon pack
            "com.novalauncher.icons.perfect" // Various icon packs
        )
    }

    /**
     * Checks if a package is a system package that should be filtered out.
     * Only filters well-known Android system package prefixes.
     */
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.google.android.") ||
                packageName.startsWith("android.")
    }

    /**
     * Tries to get an app icon from installed icon packs using proper appfilter.xml parsing.
     * Uses IconPackHelper which parses appfilter.xml and extracts package -> drawable mappings.
     */
    private fun getIconFromIconPacks(packageName: String): Drawable? {
        return IconPackHelper.getIconFromAnyPack(this, ICON_PACK_PACKAGES, packageName)
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
        progressBar = findViewById(R.id.progressBar)

        // Set up RecyclerView with grid layout (2 columns)
        adapter = PrivateAppsAdapter(this)
        rvPrivateApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        rvPrivateApps.adapter = adapter

        // Load Private profile apps in background
        loadPrivateApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Loads all apps from the Private profile.
     * Uses root access to enumerate ALL apps, falls back to notification storage.
     * Runs on background thread to avoid blocking UI.
     */
    private fun loadPrivateApps() {
        lifecycleScope.launch {
            try {
                // Show loading indicator
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    rvPrivateApps.visibility = View.GONE
                    tvEmptyState.visibility = View.GONE
                }

                // Load apps on background thread (IO operations)
                val privateApps = withContext(Dispatchers.IO) {
                    loadPrivateAppsBackground()
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Private Space apps", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEmptyState.visibility = View.VISIBLE
                    Toast.makeText(this@PrivateLauncherActivity, "Error loading Private apps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Background task to load Private Space apps.
     * This runs on a background thread to avoid blocking UI.
     */
    private suspend fun loadPrivateAppsBackground(): MutableMap<String, PrivateApp> = withContext(Dispatchers.IO) {
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

                    // Get app info for each package
                    packages.forEach { packageName ->
                        // Skip system apps by checking if it's a common system package
                        if (isSystemPackage(packageName)) {
                            return@forEach
                        }

                        // Check notification storage for app name first (faster)
                        val storageKey = "$packageName|${ProfileType.PRIVATE.name}"
                        val notifications = NotificationStorage.getNotificationsForApp(packageName, ProfileType.PRIVATE)
                        val appIconBase64 = NotificationStorage.getAppIconByKey(storageKey)

                        val appName = if (notifications.isNotEmpty()) {
                            // We have the app name from notifications (fast path)
                            notifications.first().appName
                        } else {
                            // Only use root to get label if we don't have it from notifications
                            // This is slow, so we skip it to avoid the spinner being stuck
                            // RootUtils.getAppLabel(packageName, privateUserId)
                            null
                        } ?: packageName.substringAfterLast('.') // Fallback to package name

                        privateApps[packageName] = PrivateApp(
                            packageName = packageName,
                            appName = appName,
                            appIconBase64 = appIconBase64,
                            userHandle = null
                        )
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

        return@withContext privateApps
    }

    /**
     * Shows a dialog to let the user name the app before creating a shortcut.
     */
    fun showShortcutNameDialog(privateApp: PrivateApp) {
        val input = EditText(this)
        input.setText(privateApp.appName)
        input.selectAll()

        AlertDialog.Builder(this)
            .setTitle("Name Shortcut")
            .setMessage("Choose a name for the shortcut:")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val customName = input.text.toString().trim()
                if (customName.isNotEmpty()) {
                    createShortcut(privateApp, customName)
                } else {
                    Toast.makeText(this, "Shortcut name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Creates a pinned shortcut for a Private profile app.
     *
     * The shortcut launches PrivateAppLauncherActivity (a trampoline) which uses root
     * to execute: `am start --user <private_user_id> <package>`
     * This is necessary because the personal profile cannot directly launch Private Space apps.
     */
    private fun createShortcut(privateApp: PrivateApp, customName: String = privateApp.appName) {
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
                    "Shortcut already exists for $customName",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Create intent that launches our trampoline activity
            // The trampoline will use root to launch the Private Space app
            val launchIntent = Intent(this, PrivateAppLauncherActivity::class.java).apply {
                action = Intent.ACTION_MAIN  // Required by ShortcutInfo
                putExtra(PrivateAppLauncherActivity.EXTRA_PACKAGE_NAME, privateApp.packageName)
                putExtra(PrivateAppLauncherActivity.EXTRA_APP_NAME, customName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            // Create shortcut with icon
            val shortcutIcon = createShortcutIcon(privateApp)

            val shortcut = ShortcutInfo.Builder(this, shortcutId)
                .setShortLabel(customName)
                .setLongLabel(customName)
                .setIcon(shortcutIcon)
                .setIntent(launchIntent)
                .build()

            // Request to pin the shortcut
            shortcutManager.requestPinShortcut(shortcut, null)

            Toast.makeText(
                this,
                "Creating shortcut for $customName",
                Toast.LENGTH_SHORT
            ).show()

            Log.d(TAG, "Created shortcut '$customName' for ${privateApp.packageName} (Private Space)")
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
     * Gets the best available icon for an app using the standard fallback chain.
     * This method is used by both UI display and shortcut creation to ensure consistency.
     *
     * Fallback order:
     * 1. Cached Base64 icon from notification storage
     * 2. PackageManager (works for personal profile apps)
     * 3. Icon packs (parses appfilter.xml for proper mappings)
     * 4. Default app icon
     */
    private fun getAppIconDrawable(privateApp: PrivateApp): Drawable {
        // 1. Try cached icon from notification storage
        if (!privateApp.appIconBase64.isNullOrBlank()) {
            try {
                val icon = decodeBase64ToDrawable(privateApp.appIconBase64)
                if (icon != null) {
                    Log.d(TAG, "Using cached icon for ${privateApp.packageName}")
                    return icon
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode stored icon", e)
            }
        }

        // 2. Try PackageManager (usually won't work for Private Space apps)
        try {
            val icon = packageManager.getApplicationIcon(privateApp.packageName)
            Log.d(TAG, "Using PackageManager icon for ${privateApp.packageName}")
            return icon
        } catch (e: Exception) {
            // Expected for Private Space apps
        }

        // 3. Try icon packs (DynIcons, etc.)
        try {
            val icon = getIconFromIconPacks(privateApp.packageName)
            if (icon != null) {
                Log.d(TAG, "Using icon pack for ${privateApp.packageName}")
                return icon
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get icon from icon packs", e)
        }

        // 4. Fallback to default icon
        Log.d(TAG, "Using default icon for ${privateApp.packageName}")
        return ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
            ?: resources.getDrawable(android.R.drawable.sym_def_app_icon, null)
    }

    /**
     * Creates an Icon for the shortcut from the app's icon.
     * Uses the same fallback chain as UI display for consistency.
     */
    private fun createShortcutIcon(privateApp: PrivateApp): Icon {
        val drawable = getAppIconDrawable(privateApp)
        val bitmap = drawableToBitmap(drawable)
        return Icon.createWithBitmap(bitmap)
    }

    /**
     * Decodes a Base64 encoded string to a Drawable.
     * Shared utility to avoid code duplication.
     */
    private fun decodeBase64ToDrawable(base64: String): Drawable? {
        return try {
            val decodedBytes = try {
                Base64.decode(base64, Base64.NO_WRAP)
            } catch (e: Exception) {
                Base64.decode(base64, Base64.DEFAULT)
            }
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                BitmapDrawable(resources, bitmap)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 to Drawable", e)
            null
        }
    }

    /**
     * Converts a Drawable to a Bitmap.
     * Limits size to prevent memory pressure from very large icons.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        // Limit icon size to prevent memory issues
        val maxSize = 192 // Max 192px for launcher icons
        val width = minOf(
            if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96,
            maxSize
        )
        val height = minOf(
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96,
            maxSize
        )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
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
                // Use the activity's getAppIconDrawable method for consistency
                // This ensures UI and shortcuts use the same icon fallback chain
                val appIcon = activity.getAppIconDrawable(privateApp)
                ivAppIcon.setImageDrawable(appIcon)

                // Set app name
                tvAppName.text = privateApp.appName

                // Make entire card clickable to show name dialog
                itemView.setOnClickListener {
                    activity.showShortcutNameDialog(privateApp)
                }
            }
        }
    }
}

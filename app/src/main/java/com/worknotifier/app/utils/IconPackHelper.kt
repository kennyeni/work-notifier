package com.worknotifier.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Helper class for parsing and using icon packs.
 * Specifically designed to work with Private Space apps where we can't get full ComponentName info.
 */
object IconPackHelper {

    private const val TAG = "IconPackHelper"

    // Cache of parsed icon packs: iconPackPackage -> (packageName -> drawableName)
    private val iconPackCache = mutableMapOf<String, Map<String, String>>()

    /**
     * Parses an icon pack's appfilter.xml file and builds a package -> drawable mapping.
     * Since we can't get ComponentName for Private Space apps, we extract just the package name.
     *
     * appfilter.xml format:
     * <item component="ComponentInfo{com.instagram.android/com.instagram.MainActivity}" drawable="instagram"/>
     *
     * We extract: com.instagram.android -> instagram
     */
    fun parseIconPack(context: Context, iconPackPackage: String): Map<String, String> {
        Log.d(TAG, "parseIconPack() called for: $iconPackPackage")

        // Check cache first
        iconPackCache[iconPackPackage]?.let {
            Log.d(TAG, "Using cached icon pack: $iconPackPackage (${it.size} mappings)")
            return it
        }

        val mapping = mutableMapOf<String, String>()

        try {
            // Check if the icon pack is installed
            try {
                context.packageManager.getApplicationInfo(iconPackPackage, 0)
                Log.d(TAG, "Icon pack $iconPackPackage is installed")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Icon pack NOT installed: $iconPackPackage")
                return mapping
            }

            val iconPackResources = context.packageManager.getResourcesForApplication(iconPackPackage)

            // Try to find appfilter.xml in res/xml
            var resId = iconPackResources.getIdentifier("appfilter", "xml", iconPackPackage)

            if (resId != 0) {
                // Found in res/xml
                Log.d(TAG, "Found appfilter.xml in res/xml for $iconPackPackage (resId: $resId)")
                val parser = iconPackResources.getXml(resId)
                parseAppFilterXml(parser, mapping)
                Log.d(TAG, "✓ Parsed appfilter.xml from res/xml for $iconPackPackage: ${mapping.size} mappings")
            } else {
                // Try assets folder
                Log.d(TAG, "appfilter.xml not in res/xml, trying assets for $iconPackPackage")
                try {
                    val assetManager = iconPackResources.assets
                    assetManager.open("appfilter.xml").use { inputStream ->
                        Log.d(TAG, "Found appfilter.xml in assets for $iconPackPackage")
                        val parser = android.util.Xml.newPullParser()
                        parser.setInput(inputStream, "UTF-8")
                        parseAppFilterXml(parser, mapping)
                    }
                    Log.d(TAG, "✓ Parsed appfilter.xml from assets for $iconPackPackage: ${mapping.size} mappings")
                } catch (e: IOException) {
                    Log.w(TAG, "✗ No appfilter.xml found in assets for $iconPackPackage: ${e.message}")
                }
            }

            // Cache the result
            if (mapping.isNotEmpty()) {
                iconPackCache[iconPackPackage] = mapping
                Log.d(TAG, "Cached ${mapping.size} mappings for $iconPackPackage")
            } else {
                Log.w(TAG, "No mappings found for $iconPackPackage")
            }

        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "✗ Icon pack not found: $iconPackPackage")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error parsing icon pack $iconPackPackage", e)
        }

        return mapping
    }

    /**
     * Parses the appfilter.xml using XmlPullParser.
     */
    private fun parseAppFilterXml(parser: XmlPullParser, mapping: MutableMap<String, String>) {
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    // Get the component and drawable attributes
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (component != null && drawable != null) {
                        // Extract package name from ComponentInfo{packageName/activity}
                        val packageName = extractPackageFromComponent(component)
                        if (packageName != null) {
                            mapping[packageName] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing appfilter XML", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO error parsing appfilter XML", e)
        }
    }

    /**
     * Extracts the package name from a ComponentInfo string.
     * Example: "ComponentInfo{com.instagram.android/com.instagram.MainActivity}" -> "com.instagram.android"
     */
    private fun extractPackageFromComponent(component: String): String? {
        // Match: ComponentInfo{packageName/...}
        val regex = Regex("ComponentInfo\\{([^/]+)/")
        val match = regex.find(component)
        return match?.groupValues?.get(1)
    }

    /**
     * Gets an icon from an icon pack for a specific package.
     *
     * @param context Android context
     * @param iconPackPackage The icon pack package name (e.g., "com.akbon.myd")
     * @param targetPackage The app package name to get icon for (e.g., "com.instagram.android")
     * @return Drawable icon if found, null otherwise
     */
    fun getIconForPackage(
        context: Context,
        iconPackPackage: String,
        targetPackage: String
    ): Drawable? {
        Log.d(TAG, "getIconForPackage() called - iconPack: $iconPackPackage, target: $targetPackage")

        try {
            // Parse the icon pack (uses cache if already parsed)
            val mapping = parseIconPack(context, iconPackPackage)

            // Get the drawable name for this package
            val drawableName = mapping[targetPackage]
            if (drawableName == null) {
                Log.d(TAG, "✗ No icon mapping found for $targetPackage in $iconPackPackage (total mappings: ${mapping.size})")
                // Log first few mappings to help debug
                if (mapping.isNotEmpty()) {
                    val sampleMappings = mapping.entries.take(5).joinToString(", ") { "${it.key} -> ${it.value}" }
                    Log.d(TAG, "  Sample mappings: $sampleMappings")
                }
                return null
            }

            Log.d(TAG, "Found mapping: $targetPackage -> $drawableName")

            // Get the icon pack's resources
            val iconPackResources = context.packageManager.getResourcesForApplication(iconPackPackage)

            // Get the drawable resource ID
            val drawableId = iconPackResources.getIdentifier(
                drawableName,
                "drawable",
                iconPackPackage
            )

            if (drawableId == 0) {
                Log.w(TAG, "✗ Drawable '$drawableName' not found in $iconPackPackage (resId is 0)")
                return null
            }

            Log.d(TAG, "Found drawable resource: $drawableName (resId: $drawableId)")

            // Get and return the drawable
            val drawable = iconPackResources.getDrawable(drawableId, null)
            if (drawable != null) {
                Log.d(TAG, "✓ Successfully loaded icon for $targetPackage from $iconPackPackage: $drawableName")
            } else {
                Log.w(TAG, "✗ Failed to load drawable for $drawableName (null returned)")
            }
            return drawable

        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "✗ Icon pack not found: $iconPackPackage")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error getting icon for $targetPackage from $iconPackPackage", e)
        }

        return null
    }

    /**
     * Tries to get an icon from multiple icon packs in order of priority.
     *
     * @param context Android context
     * @param iconPackPackages List of icon pack packages to try (in priority order)
     * @param targetPackage The app package to get icon for
     * @return First matching icon found, or null if none found
     */
    fun getIconFromAnyPack(
        context: Context,
        iconPackPackages: List<String>,
        targetPackage: String
    ): Drawable? {
        Log.d(TAG, "getIconFromAnyPack() called for $targetPackage - trying ${iconPackPackages.size} icon packs")

        for (iconPackPackage in iconPackPackages) {
            Log.d(TAG, "Trying icon pack: $iconPackPackage for $targetPackage")
            val icon = getIconForPackage(context, iconPackPackage, targetPackage)
            if (icon != null) {
                Log.d(TAG, "✓ Found icon in $iconPackPackage for $targetPackage")
                return icon
            }
        }

        Log.d(TAG, "✗ No icon found in any icon pack for $targetPackage")
        return null
    }

    /**
     * Clears the icon pack cache. Useful if icon packs are updated.
     */
    fun clearCache() {
        iconPackCache.clear()
        Log.d(TAG, "Icon pack cache cleared")
    }
}

package com.worknotifier.app

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.worknotifier.app.utils.RootUtils

/**
 * Trampoline activity that launches Private Space apps using root access.
 *
 * This activity is invisible and finishes immediately after launching the target app.
 * It's needed because shortcuts cannot directly launch Private Space apps from the
 * personal profile - we need to use root to execute:
 * `am start --user <private_user_id> <package>`
 */
class PrivateAppLauncherActivity : Activity() {

    companion object {
        private const val TAG = "PrivateAppLauncher"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get package name from intent
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val appName = intent.getStringExtra(EXTRA_APP_NAME)

        if (packageName == null) {
            Log.e(TAG, "No package name provided in intent")
            Toast.makeText(this, "Error: No package name", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Attempting to launch Private Space app: $packageName")

        // Check if root is available
        if (!RootUtils.isRooted()) {
            Log.e(TAG, "Root access not available")
            Toast.makeText(
                this,
                "Root access required to launch Private Space apps",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // Get Private Space user ID
        val privateUserId = RootUtils.getPrivateSpaceUserId()
        if (privateUserId == null) {
            Log.e(TAG, "Could not find Private Space user ID")
            Toast.makeText(
                this,
                "Could not find Private Space. Is it unlocked?",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        Log.d(TAG, "Found Private Space user ID: $privateUserId")

        // Launch the app using root
        val success = launchPrivateApp(packageName, privateUserId)

        if (success) {
            Log.d(TAG, "Successfully launched $packageName")
            // Don't show success toast - user will see the app launch
        } else {
            Log.e(TAG, "Failed to launch $packageName")
            val displayName = appName ?: packageName
            Toast.makeText(
                this,
                "Failed to launch $displayName",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Always finish - this activity should be invisible
        finish()
    }

    /**
     * Launches a Private Space app using root access.
     *
     * @param packageName The package name of the app to launch
     * @param userId The Private Space user ID
     * @return true if launch command succeeded, false otherwise
     */
    private fun launchPrivateApp(packageName: String, userId: Int): Boolean {
        return try {
            // First, resolve the main launcher activity for this package in the target user
            // This ensures we get the activity from the correct user profile
            val resolveCommand = "cmd package resolve-activity --user $userId -c android.intent.category.LAUNCHER $packageName"
            Log.d(TAG, "Resolving launcher activity: $resolveCommand")
            val resolveOutput = RootUtils.executeRootCommand(resolveCommand)
            Log.d(TAG, "Resolve output: $resolveOutput")

            // Extract the component name from resolve output
            // Output format includes "name=<component>" line
            val componentName = extractComponentName(resolveOutput)

            if (componentName != null) {
                // Launch using the explicit component name
                val launchCommand = "am start --user $userId -n $componentName"
                Log.d(TAG, "Executing: $launchCommand")
                val output = RootUtils.executeRootCommand(launchCommand)
                Log.d(TAG, "Launch output: $output")

                val success = output != null &&
                             !output.contains("Error", ignoreCase = true) &&
                             output.contains("Starting:")

                if (success) {
                    Log.d(TAG, "Successfully launched $packageName via component $componentName")
                } else {
                    Log.w(TAG, "Launch may have failed. Output: $output")
                }

                return success
            } else {
                Log.e(TAG, "Could not extract component name from resolve output")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception launching app", e)
            false
        }
    }

    /**
     * Extracts the component name from cmd package resolve-activity output.
     * Looks for the "name=<component>" line in the output.
     */
    private fun extractComponentName(output: String?): String? {
        if (output == null) return null

        // Look for "name=<package>/<activity>" pattern
        val nameRegex = Regex("name=([^\\s]+)")
        val match = nameRegex.find(output)
        val componentName = match?.groupValues?.get(1)

        Log.d(TAG, "Extracted component name: $componentName")
        return componentName
    }
}

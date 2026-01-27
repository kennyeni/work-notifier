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
            // Use monkey command to launch the app in the Private Space user profile
            // monkey is designed for testing but works reliably for cross-profile app launches
            // It simulates a user tap on the app's launcher icon in the specified user profile
            val command = "monkey --user $userId -p $packageName -c android.intent.category.LAUNCHER 1"

            Log.d(TAG, "Executing: $command")
            val output = RootUtils.executeRootCommand(command)

            Log.d(TAG, "Launch output: $output")

            // Check if the command succeeded
            // monkey outputs "Events injected: 1" on success
            val success = output != null &&
                         (output.contains("Events injected: 1") || output.contains("// Allowing start of Intent"))

            if (!success) {
                Log.w(TAG, "Launch command may have failed. Output: $output")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching app", e)
            false
        }
    }
}

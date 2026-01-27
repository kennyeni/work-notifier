package com.worknotifier.app.utils

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Utility class for root operations.
 * Requires Magisk or similar root solution.
 */
object RootUtils {

    private const val TAG = "RootUtils"

    /**
     * Checks if the device is rooted by attempting to execute 'su' command.
     */
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.d(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    /**
     * Executes a shell command with root privileges.
     * Returns the command output or null if execution fails.
     */
    fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = StringBuilder()
            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            val result = output.toString()
            Log.d(TAG, "Command executed: $command\nOutput: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root command: $command", e)
            null
        }
    }

    /**
     * Gets the list of all user IDs on the device.
     * Requires root access.
     */
    fun getAllUserIds(): List<Int> {
        val output = executeRootCommand("pm list users") ?: return listOf(0)
        val userIds = mutableListOf<Int>()

        // Parse output like: "UserInfo{0:Owner:c13} running"
        // or "UserInfo{10:Work profile:1030} running"
        val userIdPattern = Regex("UserInfo\\{(\\d+):")
        output.lines().forEach { line ->
            userIdPattern.find(line)?.let { matchResult ->
                val userId = matchResult.groupValues[1].toIntOrNull()
                if (userId != null) {
                    userIds.add(userId)
                }
            }
        }

        return if (userIds.isEmpty()) listOf(0) else userIds
    }

    /**
     * Gets user profile information.
     * Returns a map of user ID to profile name.
     */
    fun getUserProfileInfo(): Map<Int, String> {
        val output = executeRootCommand("pm list users") ?: return mapOf(0 to "Primary")
        val profileInfo = mutableMapOf<Int, String>()

        // Parse output like: "UserInfo{0:Owner:c13} running"
        val userInfoPattern = Regex("UserInfo\\{(\\d+):([^:]+):.*\\}")
        output.lines().forEach { line ->
            userInfoPattern.find(line)?.let { matchResult ->
                val userId = matchResult.groupValues[1].toIntOrNull()
                val userName = matchResult.groupValues[2]
                if (userId != null) {
                    profileInfo[userId] = userName
                }
            }
        }

        return if (profileInfo.isEmpty()) mapOf(0 to "Primary") else profileInfo
    }

    /**
     * Checks if a user ID corresponds to a private profile.
     * Private profiles are typically named "Private" or similar.
     */
    fun isPrivateProfile(userId: Int, profileName: String?): Boolean {
        // Private space is typically identified by name containing "Private"
        // or being a profile type PRIVATE (user type: android.os.usertype.profile.PRIVATE)
        return profileName?.contains("Private", ignoreCase = true) == true ||
                profileName?.contains("隐私空间", ignoreCase = true) == true // Chinese name
    }

    /**
     * Checks if a user ID corresponds to a work profile.
     * Work profiles are typically named "Work" or "Work profile".
     */
    fun isWorkProfileByName(profileName: String?): Boolean {
        return profileName?.contains("Work", ignoreCase = true) == true ||
                profileName?.contains("工作", ignoreCase = true) == true // Chinese name
    }

    /**
     * Gets all installed packages for a specific user.
     * Returns a list of package names.
     */
    fun getPackagesForUser(userId: Int): List<String> {
        val output = executeRootCommand("pm list packages --user $userId") ?: return emptyList()
        val packages = mutableListOf<String>()

        // Parse output like: "package:com.android.vending"
        output.lines().forEach { line ->
            if (line.startsWith("package:")) {
                val packageName = line.substring(8).trim()
                if (packageName.isNotEmpty()) {
                    packages.add(packageName)
                }
            }
        }

        Log.d(TAG, "Found ${packages.size} packages for user $userId")
        return packages
    }

    /**
     * Gets the Private Space user ID if it exists.
     * Returns null if Private Space is not found.
     */
    fun getPrivateSpaceUserId(): Int? {
        val userProfiles = getUserProfileInfo()
        return userProfiles.entries.find { (userId, profileName) ->
            isPrivateProfile(userId, profileName)
        }?.key
    }
}

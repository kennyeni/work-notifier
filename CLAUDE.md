# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Work Notifier - Development Guide

## Project Overview

Work Notifier is an Android application that intercepts and displays notifications from all apps across different profiles (Personal, Work, and Private Space). The app demonstrates Android Auto compatibility and multi-profile notification access using only NotificationListenerService.

## Features

### Notification Interception

The app uses `NotificationListenerService` to intercept notifications from:
- **Personal Profile**: Standard apps installed in the user's personal space
- **Work Profile**: Apps managed by an enterprise MDM (if present)
- **Private Space**: Apps in Android 15's Private Space (when unlocked)

**Note**: Private Space notifications are successfully intercepted by NotificationListenerService on Android 15 when Private Space is unlocked - no root or Xposed modifications needed!

**Multi-Profile Support**: When the same app is installed across multiple profiles (e.g., WhatsApp in Personal, Work, and Private), each instance is now tracked separately with its own notification history and profile badge.

### UI Features
- Dark theme optimized interface
- App icons and names (cross-profile icon support - Work/Private app icons displayed correctly)
- Profile badges (orange "WORK", purple "PRIVATE") - now shows per profile instance
- Last 100 notifications per app per profile (all unique messages preserved)
- Pagination: Display 10 notifications at a time with "Load More" button
- App ordering: Enabled apps first (mimic enabled), then alphabetically sorted
- Expand/collapse for long notification text
- Collapse/expand notifications per app to reduce clutter
- Dismiss individual notifications or entire apps from history
- Disable apps: Permanently hide apps from list via hamburger menu
- Deduplication by notification key only (preserves conversation threads and repeated messages)
- Persistent notification history (survives app restart)

### Private Space Launcher Shortcuts
- List all Private Space apps that have sent notifications
- Create pinned launcher shortcuts for Private Space apps
- Shortcuts display with app icon and name
- Purple "PRIVATE" badge on app list
- Duplicate shortcut detection with toast notification
- Shortcuts automatically grayed out by launcher when Private Space is locked
- Requires Android 8.0+ (ShortcutManager API)

### Android Auto Test Notifications
- Send MessagingStyle test notifications
- Compatible with Android Auto
- Reply and Mark-as-Read actions

### Android Auto Only Mode
- Global toggle to control when mimic notifications are generated
- Real-time Android Auto connection status display
- When enabled, mimic notifications only created when connected to Android Auto
- When disabled, mimic notifications always generated (default behavior)
- Uses official CarConnection API for reliable detection
- Supports both Android Auto (projection) and Android Automotive OS (native)
- No special permissions required for detection

### DND and Bedtime Mode Management
- Automatically manages Do Not Disturb (DND) and Bedtime mode when connecting to Android Auto
- **When connecting to Android Auto**:
  - Detects if DND is active, saves state, and disables it
  - Detects if Bedtime mode is active, saves state, and disables it
  - Creates a mimic notification confirming which modes were disabled
- **When disconnecting from Android Auto**:
  - Restores DND if it was previously active
  - Restores Bedtime mode if it was previously active
  - Creates a mimic notification confirming which modes were restored
- Fully automatic operation (no user toggles required)
- **Root-based Permission Setup** (automatic on app startup):
  - Automatically grants `WRITE_SECURE_SETTINGS` permission via root if available
  - Opens DND settings if permission not granted
  - Shows mimic notification with setup results
- **Test Button**: "Test DND & Bedtime" button in MainActivity
  - Manually tests mode toggling: disables → waits 3 seconds → re-enables
  - Shows mimic notifications for each step with checkmarks/X marks
  - Useful for verifying permissions and functionality
- Requires permissions:
  - `ACCESS_NOTIFICATION_POLICY` for DND control (user must enable in Settings)
  - `WRITE_SECURE_SETTINGS` for Bedtime mode control (auto-granted via root on startup)
- Uses official NotificationManager API for DND
- Uses Settings.Secure API for Bedtime mode (Pixel-specific feature)

## Project Structure

```
work-notifier/
├── .github/workflows/
│   └── pr-build.yml          # CI/CD workflow
├── app/
│   ├── src/main/java/com/worknotifier/app/
│   │   ├── MainActivity.kt
│   │   ├── MessagingService.kt
│   │   ├── NotificationInterceptorService.kt  # Core interception logic
│   │   ├── InterceptedAppsActivity.kt        # Display intercepted apps
│   │   ├── PrivateLauncherActivity.kt        # Create shortcuts for Private apps
│   │   ├── data/
│   │   │   ├── InterceptedNotification.kt    # Data model with ProfileType enum and icon storage
│   │   │   └── NotificationStorage.kt        # Persistent and in-memory storage
│   │   └── utils/
│   │       ├── AndroidAutoDetector.kt        # Android Auto detection utility
│   │       ├── AutoModeManager.kt            # DND and Bedtime mode management
│   │       └── RootUtils.kt                  # Optional root features
│   └── build.gradle
├── build_jks.gradle          # Deterministic keystore generation
└── settings.gradle
```

## Technical Specifications

- **Min SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Language**: Kotlin
- **Build System**: Gradle 8.2

### Key Dependencies
- AndroidX Core KTX 1.12.0
- AppCompat 1.6.1
- Material Components 1.11.0
- ConstraintLayout 2.1.4
- RecyclerView 1.3.2
- Gson 2.10.1 (for persistent storage)
- AndroidX Car App 1.4.0 (for Android Auto detection)

## Key Implementation Files

### NotificationInterceptorService.kt
Core service that intercepts notifications using `NotificationListenerService`.

**Key Methods:**
- `onNotificationPosted()`: Called when a notification is posted
- `determineProfileType()`: Detects if notification is from PERSONAL/WORK/PRIVATE profile
- `getAppIconBase64()`: Captures app icon and encodes as Base64 for cross-profile support
- Uses `UserHandle` comparison and optional root access for accurate profile detection

**Cross-Profile Icon Support:**
- Captures app icons when notifications are intercepted (works for all profiles)
- Encodes icons as Base64 and stores with notification data
- Solves the issue where personal profile PackageManager can't access Work/Private app icons

### InterceptedAppsActivity.kt
Displays intercepted apps and their notifications with:
- RecyclerView-based list
- Profile badges (WORK/PRIVATE) shown per app instance
- Collapse/expand buttons for each app's notification list
- Dismiss buttons for individual notifications and entire apps
- Permission request UI

### PrivateLauncherActivity.kt
Creates launcher shortcuts for Private Space apps with:
- Lists all Private Space apps from notification storage
- Displays app icons (from Base64 cache) and names
- Purple "PRIVATE" badge for each app
- "Create Shortcut" button for each app
- Uses Android ShortcutManager API for pinned shortcuts
- Duplicate shortcut detection with toast notification
- Shortcuts automatically grayed out when Private Space is locked
- Works with Pixel Launcher and other standard Android launchers

### NotificationStorage.kt
Persistent and in-memory storage with:
- **Composite key**: Stores apps by "packageName|profileType" to differentiate same app across profiles
- **Deduplication Strategy**: Only deduplicates by notification key (format: `USER_ID|PACKAGE_NAME|TAG|ID`)
  - Each notification key is unique per Android's notification system
  - If keys differ, they ARE different notifications (even if content is identical)
  - Preserves conversation threads, repeated messages, and rapid-fire messaging
  - No content-based deduplication (trusts Android's notification key assignment)
- **Persistence**: Saves to SharedPreferences using Gson serialization, survives app restarts
- **Maximum**: 100 unique notifications per app per profile
- **Pagination**: UI displays 10 notifications at a time with "Load More" button
- **Sorting**: Apps sorted by enabled status (mimic enabled first), then alphabetically
- **Thread-safe**: ConcurrentHashMap for concurrent access
- **Dismiss functionality**: Remove individual notifications or entire app instances
- **Disable functionality**: Permanently hide apps from list (accessible via hamburger menu)
- **Android Auto Only Mode**: Global preference to control mimic notification generation based on Android Auto connectivity

### Mimic Notification Strategy (NotificationInterceptorService.kt)
Adaptive deduplication for Android Auto compatibility with intelligent pattern detection.

**Problem Solved:**
Different messaging apps use different notification patterns. Some update a single notification with full conversation threads (e.g., WhatsApp, Signal), while others send separate notifications for each message (e.g., some Slack configurations, SMS apps).

**Adaptive Detection:**
The system automatically detects which pattern each app uses by examining the notification's `MessagingStyle`:

1. **Threaded Conversations** (Option 1: MessagingStyle with 2+ messages)
   - **Detection**: Notification contains MessagingStyle with 2+ messages
   - **Behavior**: App updates ONE notification with full conversation thread
   - **Example**: WhatsApp group chat with 5 messages in one notification
   - **Mimic Strategy**: Content-based deduplication (reuse existing mimic)
   - **Android Auto Display**: Shows full thread, updates as new messages arrive
   - **Why**: Avoids creating duplicate mimics for same conversation

2. **Separate Notifications** (Option 2: No MessagingStyle or single message)
   - **Detection**: No MessagingStyle OR only 1 message in MessagingStyle
   - **Behavior**: App sends NEW notification for each message
   - **Example**: Slack sending individual notifications for each message
   - **Mimic Strategy**: No content deduplication (create new mimic per notification)
   - **Android Auto Display**: Shows each message individually
   - **Why**: Ensures all messages appear, even if content is identical

**Benefits:**
- Automatically adapts per app and notification
- Supports both messaging patterns correctly
- Preserves conversation threads (threaded mode)
- Preserves individual messages (separate mode)
- Works with rapid-fire messages and repeated content
- Debug logging shows detected pattern: `threaded=true/false`

**Implementation Details:**
- `createMimicNotification()` extracts MessagingStyle early (line ~590)
- Counts messages to determine pattern: `isThreadedConversation = messages.size > 1`
- Conditionally applies content hash tracking only for threaded conversations
- Tracks mimic relationships for two-way dismissal (dismiss mimic → dismiss originals)

### AndroidAutoDetector.kt
Utility for detecting Android Auto connection status.

**Key Features:**
- Uses official `CarConnection` API from androidx.car.app
- Detects three connection types:
  - `CONNECTION_TYPE_NOT_CONNECTED`: Not connected to any car head unit
  - `CONNECTION_TYPE_NATIVE`: Running natively on Android Automotive OS
  - `CONNECTION_TYPE_PROJECTION`: Connected to Android Auto (projection mode)
- LiveData observation for real-time status updates
- No special permissions required
- Thread-safe singleton implementation

**Key Methods:**
- `initialize(context)`: Initialize detector (call once at app startup)
- `isConnectedToAndroidAuto()`: Check if connected (projection or native)
- `isProjectionMode()`: Check if connected via Android Auto projection
- `isNativeMode()`: Check if running on Android Automotive OS
- `getConnectionType()`: Get current connection type code
- `getConnectionStatusString()`: Get human-readable status

### AutoModeManager.kt
Manages DND and Bedtime mode state when connecting/disconnecting from Android Auto.

**Key Features:**
- Automatic operation with no user interaction required
- Observes Android Auto connection state via `CarConnection` API
- Saves mode states before disabling them
- Restores modes to previous state on disconnect
- Creates mimic notifications for user feedback
- Thread-safe singleton implementation
- Gracefully handles permission failures

**Behavior:**
- **On Android Auto Connect**:
  - Checks if DND is active and saves state
  - Checks if Bedtime mode is active and saves state
  - Disables both modes to ensure notifications work
  - Creates success notification listing disabled modes
- **On Android Auto Disconnect**:
  - Restores DND if it was previously active
  - Restores Bedtime mode if it was previously active
  - Creates success notification listing restored modes
  - Clears saved state

**Permissions:**
- `ACCESS_NOTIFICATION_POLICY`: For DND control (user must enable in Settings)
- `WRITE_SECURE_SETTINGS`: For Bedtime mode control (auto-granted via root on startup)
  - Manual grant command (if needed): `adb shell pm grant com.worknotifier.app android.permission.WRITE_SECURE_SETTINGS`

**Public Methods:**
- `initialize(context)`: Initialize manager and start observing Android Auto connection
- `setupPermissionsWithRoot()`: Auto-grant WRITE_SECURE_SETTINGS via root (called on app startup)
- `testToggleModes()`: Manually test DND/Bedtime toggling with step-by-step notifications

**Implementation Notes:**
- DND detection: Uses `NotificationManager.currentInterruptionFilter`
- DND control: Uses `NotificationManager.setInterruptionFilter()`
- Bedtime mode detection: Reads `Settings.Secure.BEDTIME_MODE_SETTING` (Pixel-specific)
- Bedtime mode control: Writes to `Settings.Secure.BEDTIME_MODE_SETTING`
- Notifications: Sends broadcast intents to NotificationInterceptorService for mimic notification creation
- Root permission grant: Uses `RootUtils.executeRootCommand()` to run `pm grant` command
- Test function: Uses coroutines with delays for step-by-step demonstration

### RootUtils.kt (Optional)
If root access is available (Magisk), the app can:
- Detect profile names via `pm list users`
- Distinguish between Work and Private profiles more reliably

**Without root**, the app still works but may not distinguish Work vs Private profiles as accurately.

## Building the Project

### Build Commands

**Build debug APK:**
```bash
./gradlew assembleDebug
```

**Build and deploy (cleans, builds, and installs via ADB):**
```bash
./build-and-deploy.sh              # Normal output
./build-and-deploy.sh --verbose    # Verbose output
```

The build script automatically:
- Detects OS (Windows/Linux) and copies appropriate `local.properties`
- Cleans stale build-tools directories
- Builds the debug APK
- Uninstalls previous version (if ADB available)
- Installs new APK on connected device (if ADB available)

### CI/CD Environment

**IMPORTANT for Cloud Claude**: Do NOT attempt local Gradle builds in cloud environments. Network restrictions prevent dependency downloads.

**Instead:**
1. Make code changes and commit
2. Push to development branch
3. GitHub Actions CI/CD automatically builds and validates
4. Check workflow results in GitHub Actions tab

GitHub Actions automatically builds on PR and push to main/master with:
- Deterministic keystore generation (`build_jks.gradle`) for consistent APK signing
- Advanced Gradle caching (branch-scoped)
- Lint validation
- 7-day artifact retention (`app-debug.apk`, `lint-results-debug.html`)

## Installation & Testing

1. Install the APK (via `./build-and-deploy.sh` or `adb install app/build/outputs/apk/debug/app-debug.apk`)
2. Grant Notification Access permission (Settings → Notification Access)
3. Tap "Select Apps" to view intercepted notifications
4. Optionally grant root access for better profile detection (if rooted with Magisk)

## Private Space Support

**Success!** NotificationListenerService successfully intercepts Private Space notifications on Android 15 when Private Space is unlocked.

**How it works:**
- When Private Space is unlocked, notifications are accessible via NotificationListenerService
- Profile type detected via UserHandle comparison
- Optional root access improves profile name detection (WORK vs PRIVATE)

**Limitations:**
- Private Space must be unlocked for notifications to be accessible
- When locked, Private Space apps are in stopped state

## Development Notes

- Notification data is stored in-memory with persistent backup to SharedPreferences
- Data persists across app restarts and device reboots
- Each app+profile combination is tracked separately (e.g., WhatsApp Personal vs WhatsApp Work)
- Deduplication strategy:
  - Storage: Only by notification key (preserves all legitimate messages)
  - Mimics: Adaptive based on app's notification pattern (threaded vs separate)
- Trusts Android's notification key assignment (different keys = different notifications)
- Supports conversation threads, repeated messages, and rapid-fire messaging
- Respects Android's profile isolation boundaries
- Read-only access to notification data

## Architecture Notes

### Data Flow
1. **Notification Interception**: `NotificationInterceptorService.onNotificationPosted()` intercepts all notifications system-wide
2. **Profile Detection**: `determineProfileType()` uses UserHandle comparison and optional root access to identify PERSONAL/WORK/PRIVATE
3. **Icon Capture**: `getAppIconBase64()` captures and encodes app icon as Base64 (solves cross-profile icon access)
4. **Storage**: `NotificationStorage` uses composite key "packageName|profileType" for multi-profile tracking
5. **UI Display**: `InterceptedAppsActivity` shows apps grouped by profile with RecyclerView-based lists

### Storage Architecture
- **In-Memory**: `ConcurrentHashMap` for thread-safe access
- **Persistence**: SharedPreferences with Gson serialization (survives app/device restarts)
- **Deduplication**:
  - **Storage**: By notification key only (no content-based deduplication)
  - **Mimics**: Adaptive strategy based on MessagingStyle detection
    - Threaded conversations (2+ messages): Content-based deduplication
    - Separate notifications (0-1 messages): No content deduplication
  - **Result**: All messages stored in history, mimics adapt to app's notification pattern
- **Limits**: 100 notifications per app per profile, 10 displayed per page

### Cross-Profile Challenges
- Personal profile `PackageManager` cannot access Work/Private app icons or metadata
- Solution: Capture and encode icons as Base64 when notification is intercepted (service has access)
- Profile detection: UserHandle comparison + optional root for profile name detection

---

**Last Updated**: 2026-02-03
**Android Version**: Android 15 (API 35)
**Build System**: Gradle 8.2
**Deduplication Strategy**: Key-based storage, adaptive mimic (threaded vs separate)

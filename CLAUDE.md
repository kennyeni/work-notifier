# Work Notifier - Claude Development Guide

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
- Last 100 notifications per app per profile (unique, deduplicated)
- Pagination: Display 10 notifications at a time with "Load More" button
- App ordering: Enabled apps first (mimic enabled), then alphabetically sorted
- Expand/collapse for long notification text
- Collapse/expand notifications per app to reduce clutter
- Dismiss individual notifications or entire apps from history
- Disable apps: Permanently hide apps from list via hamburger menu
- Deduplication by notification key with improved validation
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
- **Deduplication**: Enhanced validation - removes duplicates by notification key and rejects invalid entries
- **Persistence**: Saves to SharedPreferences using Gson serialization, survives app restarts
- **Maximum**: 100 unique notifications per app per profile
- **Pagination**: UI displays 10 notifications at a time with "Load More" button
- **Sorting**: Apps sorted by enabled status (mimic enabled first), then alphabetically
- **Thread-safe**: ConcurrentHashMap for concurrent access
- **Dismiss functionality**: Remove individual notifications or entire app instances
- **Disable functionality**: Permanently hide apps from list (accessible via hamburger menu)
- **Android Auto Only Mode**: Global preference to control mimic notification generation based on Android Auto connectivity

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

### RootUtils.kt (Optional)
If root access is available (Magisk), the app can:
- Detect profile names via `pm list users`
- Distinguish between Work and Private profiles more reliably

**Without root**, the app still works but may not distinguish Work vs Private profiles as accurately.

## Building the Project

### Local Development (WSL/Linux)
Use the build-and-deploy script:
```bash
./build-and-deploy.sh
```

Or build only (without deploying):
```bash
./gradlew assembleDebug
```

**Requirements**: `local.properties` must have `sdk.dir` set to your Android SDK path

### Cloud Claude (Claude Code Online)
**IMPORTANT**: Do NOT attempt local Gradle builds in cloud environments. Network restrictions prevent dependency downloads.

**Instead:**
1. Make code changes and commit
2. Push to development branch
3. GitHub Actions CI/CD automatically builds and validates
4. Check workflow results in GitHub Actions tab

### Build Artifacts & CI
GitHub Actions automatically builds on PR and push to main/master.

Deterministic keystore generation (`build_jks.gradle`) ensures consistent APK signing across builds.

**Artifacts:**
- `app-debug.apk` (7-day retention)
- `lint-results-debug.html` (7-day retention)

## Installation & Setup

1. Install the APK
2. Grant Notification Access permission (Settings → Notification Access)
3. Tap "Select Apps" to view intercepted notifications
4. Optionally grant root access for better profile detection

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
- Enhanced duplicate detection validates notification keys and timestamps
- Respects Android's profile isolation boundaries
- Read-only access to notification data

## Git Workflow

- **Development Branch**: `claude/android-auto-notification-toggle-CSvb3`
- **Main Branch**: `main`

---

**Last Updated**: 2026-01-27
**Android Version**: Android 15 (API 35)
**Build System**: Gradle 8.2

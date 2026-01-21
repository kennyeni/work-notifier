# Work Notifier - Claude Development Guide

## Project Overview

Work Notifier is an Android application that intercepts and displays notifications from all apps across different profiles (Personal, Work, and Private Space). The app demonstrates Android Auto compatibility and multi-profile notification access.

## Features

### Notification Interception

The app uses `NotificationListenerService` to intercept notifications from:
- **Personal Profile**: Standard apps installed in the user's personal space
- **Work Profile**: Apps managed by an enterprise MDM (if present)
- **Private Space**: Apps in Android 15's Private Space (when unlocked)

### UI Features
- Dark theme optimized interface
- App icons and names
- Profile badges (orange "WORK", purple "PRIVATE")
- Last 3 notifications per app
- Expand/collapse for long notification text
- Deduplication by notification key

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
│   │   ├── data/
│   │   │   ├── InterceptedNotification.kt
│   │   │   ├── NotificationStorage.kt
│   │   │   └── ProfileType.kt
│   │   └── utils/
│   │       └── RootUtils.kt                  # Optional root features
│   └── build.gradle
└── settings.gradle
```

## Technical Specifications

- **Min SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Language**: Kotlin
- **Build System**: Gradle 8.2

## Key Implementation Files

### NotificationInterceptorService.kt
Core service that intercepts notifications using `NotificationListenerService`.

**Key Methods:**
- `onNotificationPosted()`: Called when a notification is posted
- `determineProfileType()`: Detects if notification is from PERSONAL/WORK/PRIVATE profile
- Uses `UserHandle` comparison and optional root access for accurate profile detection

### InterceptedAppsActivity.kt
Displays intercepted apps and their notifications with:
- RecyclerView-based list
- Profile badges
- Expand/collapse functionality
- Permission request UI

### NotificationStorage.kt
In-memory storage with:
- Deduplication by notification key
- Maximum 3 notifications per app
- Thread-safe operations

## Root Features (Optional)

If root access is available (Magisk), the app can:
- Detect profile names via `pm list users`
- Distinguish between Work and Private profiles more reliably

**Without root**, the app still works but may not distinguish Work vs Private profiles as accurately.

## Building the Project

### Local Build
```bash
./gradlew assembleDebug
```

### CI Build
GitHub Actions automatically builds on PR and push to main/master.

**Artifacts:**
- `app-debug.apk` (7-day retention)
- `lint-results-debug.html` (7-day retention)

## Android Auto Integration

The app includes Android Auto compatibility for test notifications:
- MessagingStyle notifications
- Reply and Mark-as-Read actions
- Automotive app descriptor

## Installation & Setup

1. Install the APK
2. Grant Notification Access permission
3. Open "Select Apps" to view intercepted notifications
4. Optionally grant root access for better profile detection

## Private Space Support

**Good News**: NotificationListenerService successfully intercepts Private Space notifications on Android 15 when Private Space is unlocked. No additional permissions or root/Xposed modifications needed!

**Limitations:**
- Private Space must be unlocked for notifications to be accessible
- When locked, Private Space apps are in stopped state

## Development Notes

- All notification data is stored in-memory only
- No persistent storage or databases used
- Respects Android's profile isolation boundaries
- Read-only access to notification data

## Git Workflow

- **Development Branch**: `claude/android-notification-interception-bUrSP`
- **Backup Branch** (with experimental code): `claude/android-notification-interception-with-xposed-bUrSP`

---

**Last Updated**: 2026-01-21
**Android Version**: Android 15 (API 35)

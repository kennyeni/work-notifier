# Work Notifier - Claude Development Guide

## Project Overview

Work Notifier is an Android application designed to send test notifications that are **fully compatible with Android Auto**. The app demonstrates best practices for creating messaging-style notifications that appear in Android Auto's notification system.

## Project Structure

```
work-notifier/
├── .github/
│   └── workflows/
│       └── pr-build.yml          # GitHub Actions workflow for PR builds
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/worknotifier/app/
│   │       │   ├── MainActivity.kt        # Main UI with two buttons
│   │       │   └── MessagingService.kt    # Handles notification actions
│   │       ├── res/
│   │       │   ├── drawable/              # Vector icons
│   │       │   ├── layout/                # UI layouts
│   │       │   ├── mipmap-*/              # App launcher icons
│   │       │   ├── values/                # Strings, colors, themes
│   │       │   └── xml/
│   │       │       └── automotive_app_desc.xml  # Android Auto config
│   │       └── AndroidManifest.xml
│   ├── build.gradle                       # App-level Gradle config
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle                           # Project-level Gradle config
├── settings.gradle
├── gradle.properties
├── gradlew                                # Linux/Mac Gradle wrapper
├── gradlew.bat                            # Windows Gradle wrapper
├── .gitignore
├── CLAUDE.md                              # This file
└── README.md
```

## Technical Specifications

### Android Version Support
- **Minimum SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35 (Android 15)

### Key Dependencies
- AndroidX Core KTX 1.12.0
- AppCompat 1.6.1
- Material Components 1.11.0
- ConstraintLayout 2.1.4

### Build System
- Gradle 8.2
- Android Gradle Plugin 8.2.0
- Kotlin 1.9.20

## Android Auto Integration

### Requirements for Android Auto Notifications

The app implements all required specifications for Android Auto compatibility:

1. **MessagingStyle Notification**
   - Uses `NotificationCompat.MessagingStyle`
   - Includes sender information via `Person` objects
   - Shows unread messages only

2. **Required Actions**
   - **Reply Action**: Uses `SEMANTIC_ACTION_REPLY` with RemoteInput
   - **Mark as Read Action**: Uses `SEMANTIC_ACTION_MARK_AS_READ`
   - Both actions have `setShowsUserInterface(false)` set

3. **Automotive App Descriptor**
   - Location: `app/src/main/res/xml/automotive_app_desc.xml`
   - Declares `notification` usage for Android Auto

4. **Intent Service**
   - `MessagingService` extends `IntentService`
   - Handles reply and mark-as-read actions
   - Required for Android Auto compatibility (not Activity/Fragment)

### Key Implementation Files

- **MainActivity.kt** (lines 90-133): Creates MessagingStyle notification
- **MainActivity.kt** (lines 135-193): Creates reply and mark-as-read actions
- **MessagingService.kt**: Processes notification actions
- **AndroidManifest.xml** (lines 14-16): Declares Android Auto meta-data

## Features

### Current Features

1. **Send Test Notification**
   - Sends a MessagingStyle notification
   - Visible in Android Auto
   - Includes reply and mark-as-read actions
   - Requests notification permission on Android 13+

2. **Select Apps**
   - Button placeholder for future functionality
   - Shows toast message: "Feature coming soon"

### Notification Behavior

When a test notification is sent:
- Creates a messaging conversation with a test sender
- Displays in Android Auto's notification center
- Supports voice reply through Android Auto
- Can be marked as read from Android Auto
- Auto-dismisses when actioned

## Building the Project

### Prerequisites

1. **Java Development Kit (JDK)**
   - JDK 17 or higher required
   - Download: https://adoptium.net/

2. **Android SDK**
   - Install via Android Studio, or
   - Use command-line tools

3. **Environment Setup (Windows)**
   ```cmd
   # Set JAVA_HOME
   set JAVA_HOME=C:\Path\To\JDK17

   # Add to PATH
   set PATH=%PATH%;%JAVA_HOME%\bin
   ```

### Building Locally (Windows)

1. **Initialize Gradle Wrapper**
   ```cmd
   # First time only - download Gradle wrapper JAR
   # If you have Gradle installed:
   gradle wrapper

   # Otherwise, download gradle-wrapper.jar manually from:
   # https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar
   # Place it in: gradle/wrapper/gradle-wrapper.jar
   ```

2. **Build Debug APK**
   ```cmd
   gradlew.bat assembleDebug
   ```

3. **Build Release APK**
   ```cmd
   gradlew.bat assembleRelease
   ```

4. **Run Lint**
   ```cmd
   gradlew.bat lint
   ```

5. **Clean Build**
   ```cmd
   gradlew.bat clean build
   ```

### Building Locally (Linux/Mac)

1. **Initialize Gradle Wrapper**
   ```bash
   # Make gradlew executable
   chmod +x gradlew

   # First time only - download Gradle wrapper JAR
   ./gradlew wrapper
   ```

2. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Build Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

### GitHub Actions Build

The project automatically builds on pull requests to `main` or `master` branches.

**Workflow**: `.github/workflows/pr-build.yml`

**Build Steps**:
1. Checkout code
2. Set up JDK 17
3. Grant execute permission for gradlew
4. Build with Gradle
5. Run Lint
6. Upload APK and Lint reports as artifacts

**Artifacts**:
- Debug APK: `app-debug.apk`
- Lint Report: `lint-results-debug.html`

## Development Notes

### Notification Permissions

The app requests `POST_NOTIFICATIONS` permission on Android 13+ (API 33+). This is handled automatically in `MainActivity`:
- Checks permission before sending notification
- Requests permission if not granted
- Provides user feedback via Toast messages

### Testing Android Auto

To test Android Auto notifications:

1. **Using Real Device**
   - Connect phone to Android Auto-compatible car
   - Send test notification
   - Check Android Auto display

2. **Using Android Auto App (Development)**
   - Install Android Auto app from Play Store
   - Enable Developer Mode in Android Auto
   - Use "Developer settings" → "Start head unit server"
   - Connect via `adb forward` and access DHU (Desktop Head Unit)

3. **Using Android Emulator**
   - Limited support - prefer real device testing

### Code Style

- Language: Kotlin
- Architecture: Simple Activity-based
- Notification Framework: AndroidX Core (NotificationCompat)
- Service Type: IntentService for background action handling

## Future Enhancements

### Planned Features

1. **Select Apps Functionality**
   - UI to select which apps to monitor
   - Notification listener service implementation
   - App filtering and whitelisting

2. **Notification Forwarding**
   - Listen to notifications from selected apps
   - Forward as MessagingStyle to Android Auto
   - Configurable forwarding rules

3. **Settings Screen**
   - Enable/disable notification forwarding
   - Customize notification appearance
   - Configure action behaviors

4. **Reply Integration**
   - Actually send replies back to original apps
   - Support for different messaging protocols
   - Reply history and logging

## Common Issues and Solutions

### Gradle Build Fails

**Issue**: `Could not find gradle-wrapper.jar`

**Solution**:
```bash
# Download the wrapper JAR manually or run:
gradle wrapper
```

### Notification Not Showing in Android Auto

**Checklist**:
- [ ] App installed on phone connected to Android Auto
- [ ] Notification permission granted
- [ ] MessagingStyle used (not other notification styles)
- [ ] Reply action has `SEMANTIC_ACTION_REPLY`
- [ ] Mark-as-read action has `SEMANTIC_ACTION_MARK_AS_READ`
- [ ] Both actions have `setShowsUserInterface(false)`
- [ ] `automotive_app_desc.xml` exists and is referenced in manifest
- [ ] Service is IntentService (not Activity)

### Permission Denied on Android 13+

**Issue**: Notification not appearing on Android 13+

**Solution**: The app automatically requests `POST_NOTIFICATIONS` permission. User must grant it in the permission dialog.

## References

### Official Documentation

- [Android Auto Messaging Notifications](https://developer.android.com/training/cars/communication/notification-messaging)
- [MessagingStyle API](https://developer.android.com/reference/android/app/Notification.MessagingStyle)
- [Android Auto Design Guidelines](https://developers.google.com/cars/design/android-auto/apps/voice-messaging)

### Related Technologies

- **Android Gradle Plugin**: 8.2.0
- **Gradle**: 8.2
- **Kotlin**: 1.9.20
- **AndroidX**: Latest stable versions

## Git Workflow

### Branch Strategy

- Development branch: `claude/android-notification-app-beVZC`
- Main branch: `main` or `master` (for PRs)

### Commit Guidelines

- Use clear, descriptive commit messages
- Reference issue numbers when applicable
- Keep commits focused and atomic

### Creating a Pull Request

1. Ensure all changes are on the development branch
2. Push to remote: `git push -u origin claude/android-notification-app-beVZC`
3. Create PR to `main` or `master` branch
4. GitHub Actions will automatically build and test
5. Review artifacts for build success

## License

This project is created for demonstration and development purposes.

---

**Last Updated**: 2026-01-20
**Android Version**: Android 15 (API 35)
**Build System**: Gradle 8.2

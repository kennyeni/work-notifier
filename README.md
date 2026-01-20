# Work Notifier

An Android application that sends test notifications compatible with **Android Auto**.

## Features

- **Send Test Notification**: Sends a MessagingStyle notification that appears in Android Auto
- **Select Apps**: Placeholder for future app selection functionality
- **Android Auto Compatible**: Full support for Android Auto notification display with reply actions

## Requirements

- Android 10 (API 29) or higher
- Android 15 (API 35) target support

## Quick Start - Build & Deploy

The easiest way to build and install the app on your Android device:

### Windows (PowerShell)
```powershell
powershell -ExecutionPolicy Bypass -File build-and-deploy.ps1
```

### Windows (Git Bash)
```bash
./build-and-deploy.sh
```

### Linux/Mac
```bash
./build-and-deploy.sh
```

This automated script will:
- Clean and build the debug APK
- Check for connected Android devices
- Uninstall previous version (if exists)
- Install the new APK on your device

**Requirements**: ADB (Android Debug Bridge) must be installed and in your PATH

## Manual Build Instructions

### Windows
```cmd
gradlew.bat assembleDebug
```

### Linux/Mac
```bash
./gradlew assembleDebug
```

## Manual Installation

1. Build the APK using the instructions above
2. Install on your Android device: `adb install app/build/outputs/apk/debug/app-debug.apk`
3. Grant notification permission when prompted
4. Connect device to Android Auto

## Usage

1. Open the Work Notifier app
2. Tap "Send Test Notification"
3. Check your Android Auto display to see the notification
4. Use voice commands to reply or mark as read

## Android Auto Integration

This app implements the official Android Auto notification requirements:

- Uses `MessagingStyle` for notifications
- Includes reply action with voice input support
- Includes mark-as-read action
- Follows Android Auto design guidelines

## Documentation

See [CLAUDE.md](CLAUDE.md) for detailed development documentation, architecture, and implementation details.

## CI/CD

The project includes GitHub Actions workflow that automatically builds the app on pull requests.

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Build System**: Gradle 8.2
- **UI**: Material Design Components
- **Architecture**: Activity + IntentService

## License

Created for demonstration and development purposes.

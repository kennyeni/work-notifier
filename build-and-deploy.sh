#!/bin/bash
set -e

echo "================================"
echo "Work Notifier - Build & Deploy"
echo "================================"
echo ""

# Detect OS
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" || "$OSTYPE" == "cygwin" ]]; then
    IS_WINDOWS=true
    GRADLE_CMD="./gradlew.bat"
    echo "Detected Windows environment"
else
    IS_WINDOWS=false
    GRADLE_CMD="./gradlew"
    echo "Detected Unix/Linux/Mac environment"
fi

# Navigate to project root
cd "$(dirname "$0")"

# Step 1: Clean previous builds
echo ""
echo "Step 1: Cleaning previous builds..."
$GRADLE_CMD clean

# Step 2: Build debug APK
echo ""
echo "Step 2: Building debug APK..."
$GRADLE_CMD assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Check if APK was built successfully
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    echo "Build may have failed. Check the logs above."
    exit 1
fi

echo ""
echo "✓ Build successful! APK location: $APK_PATH"
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "  APK size: $APK_SIZE"

# Step 3: Check for ADB
echo ""
echo "Step 3: Checking for ADB..."

if ! command -v adb &> /dev/null; then
    echo "WARNING: ADB not found in PATH"
    echo ""
    echo "To install ADB:"
    echo "  - Install Android Studio, or"
    echo "  - Download Android SDK Platform Tools from:"
    echo "    https://developer.android.com/studio/releases/platform-tools"
    echo ""
    echo "After installing, add ADB to your PATH:"
    if [ "$IS_WINDOWS" = true ]; then
        echo "  Windows: Add C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk\\platform-tools to PATH"
    else
        echo "  Unix/Mac: Add ~/Android/Sdk/platform-tools to PATH"
    fi
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Step 4: Check for connected devices
echo ""
echo "Step 4: Checking for connected Android devices..."
adb devices -l

DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo ""
    echo "ERROR: No Android devices connected"
    echo ""
    echo "Please:"
    echo "  1. Connect your Android device via USB"
    echo "  2. Enable Developer Options on your device"
    echo "  3. Enable USB Debugging in Developer Options"
    echo "  4. Accept the USB debugging authorization prompt on your device"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

echo ""
echo "✓ Found $DEVICE_COUNT connected device(s)"

# Step 5: Uninstall previous version (if exists)
echo ""
echo "Step 5: Uninstalling previous version (if exists)..."
adb uninstall com.worknotifier.app 2>/dev/null && echo "✓ Previous version uninstalled" || echo "  No previous version found"

# Step 6: Install APK
echo ""
echo "Step 6: Installing APK to device..."
adb install "$APK_PATH"

if [ $? -eq 0 ]; then
    echo ""
    echo "================================"
    echo "✓ SUCCESS!"
    echo "================================"
    echo ""
    echo "The app has been installed on your device."
    echo ""
    echo "To launch the app:"
    echo "  1. Find 'Work Notifier' in your app drawer"
    echo "  2. Grant notification permission when prompted"
    echo "  3. Tap 'Send Test Notification'"
    echo "  4. Connect to Android Auto to see the notification"
    echo ""
else
    echo ""
    echo "ERROR: Failed to install APK"
    echo "Check if:"
    echo "  - Your device is properly connected"
    echo "  - USB debugging is enabled"
    echo "  - You've authorized the computer on your device"
    exit 1
fi

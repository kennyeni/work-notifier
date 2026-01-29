#!/bin/bash
set -e

# Check for verbose flag
VERBOSE=false
if [[ "$1" == "--verbose" ]]; then
    VERBOSE=true
fi

# Detect OS and clean build-tools
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" || "$OSTYPE" == "cygwin" ]]; then
    IS_WINDOWS=true
    cp ./local.properties.windows ./local.properties

    # Windows: Delete build-tools to force fresh SDK install
    SDK_PATH="$LOCALAPPDATA/Android/Sdk"
    BUILD_TOOLS="$SDK_PATH/build-tools"
    if [[ -d "$BUILD_TOOLS" ]]; then
        cmd //c "rmdir /s /q \"$BUILD_TOOLS\""
    fi

    GRADLE_CMD="./gradlew.bat"
else
    IS_WINDOWS=false
    cp ./local.properties.linux ./local.properties

    # Linux/WSL: Delete build-tools to force fresh SDK install
    if [[ -d "/mnt/c/Users/$USER/AppData/Local/Android/Sdk" ]]; then
        # WSL accessing Windows SDK
        SDK_PATH="/mnt/c/Users/$USER/AppData/Local/Android/Sdk"
    else
        # Native Linux
        SDK_PATH="$HOME/Android/Sdk"
    fi
    BUILD_TOOLS="$SDK_PATH/build-tools"
    if [[ -d "$BUILD_TOOLS" ]]; then
        rm -rf "$BUILD_TOOLS"
    fi

    GRADLE_CMD="./gradlew"
fi

# Navigate to project root
cd "$(dirname "$0")"

# Clean and build
if [ "$VERBOSE" = true ]; then
    $GRADLE_CMD clean assembleDebug
else
    $GRADLE_CMD clean assembleDebug -q 2>&1 | grep -E "(BUILD|error|Error|ERROR)" || true
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK build failed"
    exit 1
fi

echo "✓ Build successful"

# Check for ADB
if ! command -v adb &> /dev/null; then
    if [ "$VERBOSE" = true ]; then
        echo "WARNING: ADB not found in PATH - skipping install"
    fi
    exit 0
fi

# Check devices
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    if [ "$VERBOSE" = true ]; then
        echo "WARNING: No Android devices connected - skipping install"
    fi
    exit 0
fi

if [ "$VERBOSE" = true ]; then
    echo "Found $DEVICE_COUNT device(s) - installing APK..."
fi

# Uninstall and install
adb uninstall com.worknotifier.app 2>/dev/null || true
adb install "$APK_PATH" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✓ APK installed successfully"
else
    echo "ERROR: Failed to install APK"
    exit 1
fi

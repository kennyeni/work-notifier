# PowerShell script to build and deploy the app to Android device
# Run this with: powershell -ExecutionPolicy Bypass -File build-and-deploy.ps1

$ErrorActionPreference = "Stop"

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Work Notifier - Build & Deploy" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Navigate to script directory
Set-Location $PSScriptRoot

# Step 1: Clean previous builds
Write-Host ""
Write-Host "Step 1: Cleaning previous builds..." -ForegroundColor Yellow
& .\gradlew.bat clean

# Step 2: Build debug APK
Write-Host ""
Write-Host "Step 2: Building debug APK..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

# Check if APK was built successfully
if (-not (Test-Path $apkPath)) {
    Write-Host ""
    Write-Host "ERROR: APK not found at $apkPath" -ForegroundColor Red
    Write-Host "Build may have failed. Check the logs above." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "✓ Build successful! APK location: $apkPath" -ForegroundColor Green
$apkSize = (Get-Item $apkPath).Length / 1MB
Write-Host ("  APK size: {0:N2} MB" -f $apkSize) -ForegroundColor Green

# Step 3: Check for ADB
Write-Host ""
Write-Host "Step 3: Checking for ADB..." -ForegroundColor Yellow

$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbPath) {
    Write-Host ""
    Write-Host "WARNING: ADB not found in PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "To install ADB:" -ForegroundColor Yellow
    Write-Host "  - Install Android Studio, or"
    Write-Host "  - Download Android SDK Platform Tools from:"
    Write-Host "    https://developer.android.com/studio/releases/platform-tools"
    Write-Host ""
    Write-Host "After installing, add ADB to your PATH:" -ForegroundColor Yellow
    Write-Host "  Add C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools to PATH"
    Write-Host ""
    Write-Host "Or set it temporarily in this session:"
    Write-Host '  $env:Path += ";C:\Users\' + $env:USERNAME + '\AppData\Local\Android\Sdk\platform-tools"'
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "✓ ADB found at: $($adbPath.Source)" -ForegroundColor Green

# Step 4: Check for connected devices
Write-Host ""
Write-Host "Step 4: Checking for connected Android devices..." -ForegroundColor Yellow
& adb devices -l

$devicesOutput = & adb devices | Select-String "device$"
$deviceCount = ($devicesOutput | Measure-Object).Count

if ($deviceCount -eq 0) {
    Write-Host ""
    Write-Host "ERROR: No Android devices connected" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please:" -ForegroundColor Yellow
    Write-Host "  1. Connect your Android device via USB"
    Write-Host "  2. Enable Developer Options on your device"
    Write-Host "  3. Enable USB Debugging in Developer Options"
    Write-Host "  4. Accept the USB debugging authorization prompt on your device"
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "✓ Found $deviceCount connected device(s)" -ForegroundColor Green

# Step 5: Uninstall previous version (if exists)
Write-Host ""
Write-Host "Step 5: Uninstalling previous version (if exists)..." -ForegroundColor Yellow
$uninstallResult = & adb uninstall com.worknotifier.app 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Previous version uninstalled" -ForegroundColor Green
} else {
    Write-Host "  No previous version found" -ForegroundColor Gray
}

# Step 6: Install APK
Write-Host ""
Write-Host "Step 6: Installing APK to device..." -ForegroundColor Yellow
& adb install $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "================================" -ForegroundColor Green
    Write-Host "✓ SUCCESS!" -ForegroundColor Green
    Write-Host "================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "The app has been installed on your device." -ForegroundColor Cyan
    Write-Host ""
    Write-Host "To launch the app:" -ForegroundColor Yellow
    Write-Host "  1. Find 'Work Notifier' in your app drawer"
    Write-Host "  2. Grant notification permission when prompted"
    Write-Host "  3. Tap 'Send Test Notification'"
    Write-Host "  4. Connect to Android Auto to see the notification"
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "ERROR: Failed to install APK" -ForegroundColor Red
    Write-Host "Check if:" -ForegroundColor Yellow
    Write-Host "  - Your device is properly connected"
    Write-Host "  - USB debugging is enabled"
    Write-Host "  - You've authorized the computer on your device"
    Read-Host "Press Enter to exit"
    exit 1
}

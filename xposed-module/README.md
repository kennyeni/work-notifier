# Work Notifier - LSPosed Module (READ-ONLY)

## ⚠️ CRITICAL WARNING

**This is an Xposed/LSPosed module that hooks Android system services.**

- Can cause bootloops if not configured correctly
- Requires LSPosed framework installed
- TEST ON NON-CRITICAL DEVICE ONLY
- BACKUP YOUR DEVICE FIRST

---

## Purpose

This module intercepts notifications from **Android 15 Private Space** by hooking into `NotificationManagerService` BEFORE profile isolation is applied.

### Why This Module?

- NotificationListenerService: ❌ Cannot access Private Space (blocked by Android)
- AccessibilityService: ❌ Cannot access Private Space (tested, failed)
- System Hook: ✅ Can intercept before isolation (this module)

---

## Safety Features

### READ-ONLY IMPLEMENTATION

This module is **100% read-only**:
- ✅ Reads notification data
- ✅ Logs to logcat
- ❌ **NEVER** modifies parameters
- ❌ **NEVER** blocks notifications
- ❌ **NEVER** changes system behavior

### Error Handling

- All code wrapped in try-catch
- Errors logged but don't crash system
- Graceful failure on any issue
- Kill switch support

---

## Requirements

1. **Rooted device** with Magisk
2. **LSPosed framework** installed (JingMatrix fork for Android 15)
3. **Android 10+** (API 29+)
4. **Android 15** for Private Space support

---

## Installation

### Step 1: Build the Module

```bash
cd /home/user/work-notifier
./gradlew :xposed-module:assembleDebug
```

Output: `xposed-module/build/outputs/apk/debug/xposed-module-debug.apk`

### Step 2: Install via LSPosed

1. Install the APK on your device
2. Open LSPosed Manager
3. Go to "Modules"
4. Enable "Work Notifier Xposed Module"
5. **Configure scope: Select "System Framework (android)"**
6. **Reboot device**

### Step 3: Verify Installation

```bash
# Check if module is loaded
adb shell logcat | grep "WorkNotifier-Xposed"

# Expected output:
# WorkNotifier-Xposed: Module loaded (v1.0.0-READONLY) - READ-ONLY mode
# WorkNotifier-Xposed: Successfully hooked enqueueNotificationInternal (READ-ONLY)
```

---

## Testing

### Test Private Space Notifications

1. **Unlock Private Space** on your device
2. Open an app in Private Space (e.g., WhatsApp)
3. Send yourself a test notification
4. Check logcat:

```bash
adb shell logcat | grep "WN_NOTIFICATION"
```

Expected output:
```
WorkNotifier-Hook: WN_NOTIFICATION|com.whatsapp|11|New message|Hello|1737350400000
                                   └─package   └userId └title    └text  └timestamp
```

### Verify Main App Integration

The main Work Notifier app would need to be updated to read these logcat entries, but for now you can verify the hook is working by checking the logs.

---

## Kill Switch

If the module causes issues, you can disable it **without uninstalling**:

```bash
# Disable module
adb shell touch /data/local/tmp/worknotifier_xposed_disable

# Reboot
adb reboot

# Re-enable module
adb shell rm /data/local/tmp/worknotifier_xposed_disable
adb reboot
```

---

## Rollback Plan

### If Bootloop Occurs:

**Option 1: Disable via LSPosed Manager**
1. Boot to safe mode (if possible)
2. Open LSPosed Manager
3. Disable "Work Notifier Xposed Module"
4. Reboot

**Option 2: Disable LSPosed**
1. Boot to recovery (TWRP/custom recovery)
2. Go to Magisk section
3. Disable LSPosed module
4. Reboot

**Option 3: Flash Stock Boot**
1. Boot to fastboot mode
2. Flash stock boot.img:
   ```bash
   fastboot flash boot boot.img
   fastboot reboot
   ```

---

## How It Works

### Hook Point

```
NotificationManagerService.enqueueNotificationInternal()
├── Called BEFORE profile isolation
├── Receives ALL notifications (personal, work, private)
├── We READ notification data here
└── Log to logcat for main app to consume
```

### Data Flow

```
App sends notification
    ↓
enqueueNotificationInternal() [← WE HOOK HERE]
    ↓
[READ: package, title, text, userId]
    ↓
[LOG: WN_NOTIFICATION|pkg|userId|title|text|timestamp]
    ↓
Profile isolation applied
    ↓
Notification shown to user
```

---

## Code Structure

```
xposed-module/
├── src/main/
│   ├── java/com/worknotifier/xposed/
│   │   ├── HookEntry.kt          # Module entry point
│   │   ├── NotificationHook.kt   # Read-only hook implementation
│   │   └── SafetyUtils.kt        # Kill switch & safety
│   ├── assets/
│   │   └── xposed_init           # LSPosed configuration
│   ├── res/values/
│   │   └── arrays.xml            # Scope configuration
│   └── AndroidManifest.xml
├── build.gradle
├── README.md                      # This file
└── SECURITY_AUDIT.md              # Security review
```

---

## Troubleshooting

### Module Not Loading

**Check LSPosed logs:**
```bash
adb shell logcat | grep LSPosed
```

**Verify scope:**
- Module must be enabled for "System Framework (android)"
- Reboot required after enabling

### No Notifications Logged

**Check hook status:**
```bash
adb shell logcat | grep "WorkNotifier-Hook"
```

**Verify Private Space is unlocked:**
- Private Space must be unlocked to send notifications

### System Issues

**Immediate fix:**
```bash
adb shell touch /data/local/tmp/worknotifier_xposed_disable
adb reboot
```

---

## Security Considerations

### What This Module Can Access

- ✅ All notification data (title, text, package, user)
- ✅ System-level access via hook

### What This Module Cannot Do

- ❌ Modify notification content
- ❌ Block or cancel notifications
- ❌ Access files or contacts
- ❌ Network access
- ❌ Change system settings

### Privacy

- Data is only logged to logcat (local device)
- No remote transmission
- No persistent storage
- Can be disabled anytime

---

## Future Integration

To integrate with the main app, you would:

1. Read logcat in the main app (requires root)
2. Parse "WN_NOTIFICATION" entries
3. Create InterceptedNotification objects
4. Store in NotificationStorage
5. Display in UI with PRIVATE badge

This is left for Phase 2 if the hook works successfully.

---

## License

This module is part of the Work Notifier app.
Use at your own risk.

---

**Last Updated:** 2026-01-20
**Module Version:** 1.0.0-READONLY
**Target:** Android 15+ Private Space

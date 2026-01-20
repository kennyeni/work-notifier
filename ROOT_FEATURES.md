# Root Features - Work & Private Profile Support

This document describes the enhanced notification interception capabilities available with root access (Magisk).

## Overview

Work Notifier can intercept notifications from:
- ✅ **Personal Profile** - Always supported (no root required)
- ⚠️ **Work Profile** - Partially supported without root, **fully supported with root**
- ⚡ **Private Profile (Android 15+)** - **Requires root access**

## Features Without Root

### What Works
- Intercept notifications from personal profile apps
- Detect notifications from work/private profiles (limited)
- Cannot distinguish between work and private profiles

### Limitations
- Cannot accurately identify private vs work profiles
- Work profile access depends on IT admin policy (`setPermittedCrossProfileNotificationListeners`)
- Private profile notifications only available when profile is unlocked

## Features With Root (Magisk)

### What Works
- ✅ Full detection of all user profiles on device
- ✅ Accurate identification of Personal, Work, and Private profiles
- ✅ Profile-specific badges in UI (orange WORK, purple PRIVATE)
- ✅ Access to profile metadata (user ID, profile name)
- ✅ Better cross-profile notification support

### Requirements

1. **Rooted Device with Magisk**
   - Magisk 24.0 or higher recommended
   - Zygisk enabled (optional but recommended)
   - MagiskSU properly configured

2. **Multi-User Root Access**
   - Set Magisk to "User-independent" mode in settings
   - Reboot after changing multiuser settings
   - Grant root permission to Work Notifier when prompted

3. **NotificationListenerService Permission**
   - Enable in Settings > Apps > Special app access > Notification access
   - Must be enabled for the personal profile

4. **For LSPosed Users (Optional)**
   - Use JingMatrix's LSPosed fork for Android 15 support
   - Install module on all user profiles where needed
   - Enables advanced system hooks if needed in future

## How It Works

### Technical Implementation

#### Profile Detection Algorithm

```
1. NotificationListenerService receives notification
2. Extract UserHandle from StatusBarNotification
3. Compare with current process UserHandle:
   - Same UserHandle → PERSONAL profile
   - Different UserHandle → Other profile (WORK or PRIVATE)
4. If root available:
   - Execute `pm list users` via su
   - Parse profile names and user IDs
   - Match notification UserID to profile info
   - Determine if WORK or PRIVATE based on name
5. Display appropriate badge in UI
```

#### Root Commands Used

| Command | Purpose |
|---------|---------|
| `su` | Gain root shell access |
| `pm list users` | List all user profiles and their IDs |
| Profile name parsing | Identify "Work profile", "Private", etc. |

### Profile Type Detection

The app uses multiple signals to detect profile types:

**Work Profile Detection:**
- Profile name contains "Work" or "工作" (Chinese)
- UserHandle different from current process
- Root-based profile info confirms work profile type

**Private Profile Detection (Android 15+):**
- Profile name contains "Private" or "隐私空间" (Chinese)
- Profile type is `android.os.usertype.profile.PRIVATE`
- UserHandle different from current process
- Requires root to access stopped profile information

### UI Indicators

| Profile Type | Badge | Color | Background |
|--------------|-------|-------|------------|
| Personal | None | - | - |
| Work | WORK | Orange (#FF9800) | Semi-transparent orange |
| Private | PRIVATE | Purple (#9C27B0) | Semi-transparent purple |

## Setup Instructions

### Step 1: Root Your Device

1. Install Magisk via TWRP or fastboot
2. Boot device and verify root access
3. Open Magisk Manager app
4. Go to Settings
5. Enable "Zygisk" (optional)
6. Set "Multiuser Mode" to "User-independent"
7. Reboot device

### Step 2: Configure Work Notifier

1. Install Work Notifier APK
2. Open app
3. Grant root permission when prompted (SuperUser dialog)
4. Tap "Select Apps" button
5. Tap "Enable Notification Access"
6. Enable Work Notifier in the list
7. Return to app
8. Tap "Select Apps" again

### Step 3: Verify Profile Detection

1. Send test notifications from different profiles:
   - Personal profile app (e.g., Gmail personal)
   - Work profile app (e.g., Slack work)
   - Private profile app (e.g., WhatsApp private)
2. Check logcat for detection logs:
   ```bash
   adb logcat | grep NotificationInterceptor
   ```
3. Expected log output:
   ```
   Root detected. User profiles: {0=Owner, 10=Work profile, 11=Private}
   Profile detected - UserID: 10, Name: Work profile
   Profile detected - UserID: 11, Name: Private
   ```

## Privacy & Security Considerations

### Root Access Risks
- Root access bypasses Android security boundaries
- Only grant root to trusted applications
- Work Notifier uses root only for profile detection
- No data is transmitted outside the device

### Data Storage
- Notifications stored in memory only
- Data cleared when app is closed
- Maximum 3 notifications per app stored
- No persistent storage or databases used

### Work Profile Compliance
- IT admins can still restrict notification access via policy
- Some MDM solutions may detect and block root access
- Check your organization's BYOD policy before using root

## Troubleshooting

### Root Not Detected

**Problem:** App shows "No root access" in logs

**Solutions:**
1. Verify Magisk is properly installed: Open Magisk Manager
2. Grant SuperUser permission: Check Magisk > Superuser list
3. Test root access manually:
   ```bash
   adb shell
   su
   ```
4. Reinstall Magisk if needed

### Profile Not Detected

**Problem:** Work/Private badge not showing

**Solutions:**
1. Check if root access is granted
2. Verify profile exists:
   ```bash
   adb shell su -c "pm list users"
   ```
3. Check notification is from correct profile
4. Review logcat for detection errors:
   ```bash
   adb logcat | grep NotificationInterceptor
   ```

### Private Profile Always Shows as WORK

**Problem:** Cannot distinguish private from work profile

**Possible Causes:**
- Root access not available
- Profile name doesn't contain "Private"
- Custom ROM with different naming

**Solution:**
- Update `RootUtils.isPrivateProfile()` to match your ROM's naming
- Check profile name via: `adb shell su -c "pm list users"`

### Magisk Detection by Apps

**Problem:** Banking/work apps detect Magisk

**Solutions:**
1. Enable MagiskHide (legacy) or Zygisk DenyList
2. Hide Magisk app with random package name
3. Use Shamiko module for better hiding
4. Consider using separate profiles for sensitive apps

## Compatibility

### Android Versions
- ✅ Android 10 (API 29) - Work profile supported
- ✅ Android 11 (API 30) - Work profile supported
- ✅ Android 12 (API 31) - Work profile supported
- ✅ Android 13 (API 33) - Work profile supported
- ✅ Android 14 (API 34) - Work profile supported
- ⚡ Android 15 (API 35) - **Private profile supported**

### Magisk Versions
- Magisk 24.0+ recommended
- Magisk 25.0+ preferred for Android 14+
- Magisk 26.0+ required for Android 15

### Known Issues
- LSPosed maintenance stopped (use JingMatrix fork for Android 15)
- Some ROMs have custom profile names (requires code adaptation)
- Private profile detection only works when profile is unlocked

## Future Enhancements

Potential improvements with root access:
- [ ] Monitor notifications even when profiles are locked
- [ ] Support for cloned apps (MIUI, ColorOS, etc.)
- [ ] Bulk notification export/backup
- [ ] Cross-profile app communication
- [ ] Advanced filtering based on profile type

## References

### Official Documentation
- [Android Multi-User Support](https://source.android.com/docs/devices/admin/multi-user)
- [Android Private Space (Android 15)](https://source.android.com/docs/security/features/private-space)
- [Work Profiles](https://developer.android.com/work/managed-profiles)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

### Root Tools
- [Magisk Official](https://github.com/topjohnwu/Magisk)
- [LSPosed (JingMatrix Fork)](https://github.com/JingMatrix/LSPosed)
- [Awesome Android Root](https://github.com/awesome-android-root/awesome-android-root)

---

**Last Updated:** 2026-01-20
**Android Version:** Android 15 (API 35)
**Magisk Version:** 26.0+
**Root Required:** Yes (for full private profile support)

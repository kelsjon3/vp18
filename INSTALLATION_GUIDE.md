# 📱 APK Installation Guide - Pixel 19 XL

## 📍 **APK Location**
**✅ Updated APK**: `./app/build/outputs/apk/debug/app-debug.apk` (17MB)

## 🛠️ **Installation Methods**

### Method 1: Enable Developer Options (Recommended)

#### Step 1: Enable Developer Options
1. **Settings** → **About phone**
2. **Tap "Build number" 7 times**
3. Enter your PIN/password when prompted
4. You'll see "You are now a developer!"

#### Step 2: Enable USB Debugging  
1. **Settings** → **System** → **Developer options**
2. **Toggle on "USB debugging"**
3. **Toggle on "Install via USB"**

#### Step 3: Install via ADB
```bash
# Connect phone via USB
# Install APK
adb install ./app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Manual Installation

#### Step 1: Copy APK to Phone
```bash
# Copy APK to phone storage
adb push ./app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```

#### Step 2: Install from Phone
1. **Open Files app** on your Pixel
2. **Navigate to Downloads folder**
3. **Tap app-debug.apk**
4. **Allow installation from unknown sources** when prompted
5. **Tap Install**

### Method 3: Enable Unknown Sources

#### For Android 12+ (Pixel 19 XL):
1. **Settings** → **Apps** → **Special app access**
2. **Install unknown apps**
3. **Select browser/file manager** you'll use
4. **Allow from this source**

Then download APK via browser or use file manager.

## 🔧 **Troubleshooting "Invalid Package"**

### Issue 1: Corrupted Transfer
```bash
# Re-download/re-copy the APK
adb push ./app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/app-debug-new.apk
```

### Issue 2: Previous Installation
```bash
# Uninstall any previous version
adb uninstall com.vp18.mediaplayer
# Then reinstall
adb install ./app/build/outputs/apk/debug/app-debug.apk
```

### Issue 3: Architecture Mismatch
The APK is built for x86_64 (emulator). Let me build for ARM64 (Pixel):

#### Build ARM64 Version
Let me create a proper ARM64 build...

### Issue 4: Android Version Compatibility
- **Minimum**: Android 7.0 (API 24)
- **Target**: Android 14 (API 34)
- **Pixel 19 XL**: Should be compatible

## 🎯 **Quick Fix - Try This First**

```bash
# 1. Enable developer options and USB debugging
# 2. Connect phone via USB
# 3. Uninstall any previous version
adb uninstall com.vp18.mediaplayer

# 4. Install fresh
adb install ./app/build/outputs/apk/debug/app-debug.apk

# 5. If still fails, try with replacement
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
```

## 📋 **Debug Commands**

```bash
# Check device connection
adb devices

# Check if app is installed
adb shell pm list packages | grep vp18

# Get detailed error
adb install -v ./app/build/outputs/apk/debug/app-debug.apk

# Force install (replace existing)
adb install -r -d ./app/build/outputs/apk/debug/app-debug.apk
```

## 🔄 **Alternative: Build ARM64 APK**

If the current APK doesn't work, I can build a proper ARM64 version for your Pixel. The current APK might be optimized for x86_64 emulators.

Let me know if you need an ARM64-specific build!

---

## 💡 **Quick Test Steps**

1. **Enable Developer Options**: Settings → About → Tap Build Number 7 times
2. **Enable USB Debugging**: Settings → Developer Options → USB Debugging ON
3. **Connect USB**: Plug phone into computer
4. **Install**: `adb install ./app/build/outputs/apk/debug/app-debug.apk`

**This should resolve the "invalid package" error!** 📱✨
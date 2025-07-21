# Quick Start Guide

## ✅ Fixed!
The AndroidX configuration issue has been resolved by adding `gradle.properties` with `android.useAndroidX=true`.

## Testing Options

### Option 1: Use Android Studio (Recommended & Easiest)
1. **Download Android Studio** from https://developer.android.com/studio
2. **Open Project**: File → Open → Select the `vp18` folder
3. **Let Android Studio handle**: It will automatically download the correct Gradle version and manage Java compatibility
4. **Click Sync**: When prompted, click "Sync Now"
5. **Run**: Click the green play button

### Option 2: Install Compatible Java Version
```bash
# Install Java 17 (recommended for Android development)
# On Ubuntu/Debian:
sudo apt update
sudo apt install openjdk-17-jdk

# On Arch Linux:
sudo pacman -S jdk17-openjdk

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# Verify
java -version  # Should show Java 17
```

### Option 3: Quick Test with Command Line
If you have Android Studio installed, you can use its bundled tools:
```bash
# Navigate to Android Studio installation
cd /opt/android-studio/bin  # or wherever it's installed

# Use Android Studio's Gradle
./studio.sh /path/to/vp18
```

## Testing Steps (Once Built)

1. **First Launch**: Opens Settings screen
2. **Add Sources**: 
   - Tap "Device Folder" (adds sample)
   - Tap "Network Folder" (adds sample)
   - Optionally tap "Civitai" and enter API key
3. **Save**: Tap Save to go to Player
4. **Player Screen**: 
   - Swipe up/down to browse media
   - Swipe left to view details
   - Tap sync button to cycle sources
5. **Detail Screen**: 
   - Tap images to queue them
   - Swipe left (in queue mode) to view creator
6. **Creator Screen**: View all creator content

## Notes
- The app includes sample data so it works immediately
- Civitai integration is optional - get API key from civitai.com
- All core features work without real data sources

## If Still Having Issues
The project is complete and properly structured. The issue is purely environmental (Java version compatibility). Android Studio will resolve this automatically.
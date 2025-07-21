# VP18 - TikTok-Style Media Player

A TikTok-inspired media player for Android built with Jetpack Compose that supports local folders, network folders, and Civitai API integration.

## Features

- 📱 **TikTok-like Interface**: Vertical scrolling with full-screen media
- 🎬 **Multiple Sources**: Device folders, network folders, and Civitai API
- 👆 **Gesture Navigation**: Swipe up/down to browse, swipe left for details
- 🖼️ **Creator Pages**: View all content from specific creators
- 🔄 **Source Cycling**: Switch between different media sources
- 📋 **Queue Mode**: Focus on specific model/creator content

## Setup & Testing

### Prerequisites
- Android Studio (latest stable)
- Android SDK API 24+
- Android device or emulator (Android 7.0+)

### Build Instructions

1. **Clone and Open**
   ```bash
   # Open Android Studio
   # File → Open → Select vp18 folder
   ```

2. **Sync Project**
   ```bash
   # Android Studio will prompt to sync Gradle
   # Click "Sync Now"
   ```

3. **Build & Run**
   ```bash
   # Click "Run" button or use Ctrl+R
   # Select target device/emulator
   ```

### Testing Steps

#### 1. **Initial Setup**
- App launches to Settings screen (first time)
- Add test sources:
  - **Device Folder**: Adds sample local path
  - **Network Folder**: Adds sample network path
  - **Civitai**: Enter API key (get from civitai.com)

#### 2. **Player Screen Testing**
- **Vertical Swipe**: Navigate between media items
- **Horizontal Swipe Left**: Open detail view
- **Source Cycling**: Tap sync button to switch sources
- **Settings**: Tap gear icon to return to settings

#### 3. **Detail Screen Testing**
- View model/creator information
- **Thumbnail Grid**: Tap any image to queue and return to player
- **Queue Mode**: Player now shows only that model's images
- **Swipe Left (Queue Mode)**: Navigate to creator page

#### 4. **Creator Screen Testing**
- View all creator content
- **Thumbnail Tap**: Return to player
- **Back Navigation**: Return to detail screen

### API Testing

#### Civitai Integration
1. Get API key from [civitai.com](https://civitai.com)
2. Add in Settings → Civitai
3. Source will load real models and images

#### Network Permissions
- Internet access for Civitai API
- Storage access for local files

### Common Issues

#### Build Errors
```bash
# Clean and rebuild
./gradlew clean
./gradlew build
```

#### Dependency Issues
```bash
# Update dependencies in build.gradle
# Sync project
```

#### API Issues
- Check internet connection
- Verify Civitai API key is valid
- Check API rate limits

### Development Testing

#### Unit Testing
```bash
./gradlew test
```

#### UI Testing
```bash
./gradlew connectedAndroidTest
```

#### Manual Testing Checklist
- [ ] Settings screen loads
- [ ] Can add/remove sources
- [ ] Player screen navigation works
- [ ] Vertical pager functions
- [ ] Horizontal swipe detection
- [ ] Detail screen displays correctly
- [ ] Image thumbnails load
- [ ] Queue mode activates
- [ ] Creator page navigation
- [ ] Back navigation works
- [ ] Source cycling functions
- [ ] Civitai API integration
- [ ] Image loading with Coil
- [ ] Gesture handling smooth

## Architecture

- **Single Activity**: MainActivity with Compose navigation
- **MVVM Pattern**: ViewModels with StateFlow
- **Repository Pattern**: Clean data layer
- **Jetpack Compose**: Modern UI toolkit
- **Retrofit**: Network layer
- **Coil**: Image loading
- **DataStore**: Preferences storage
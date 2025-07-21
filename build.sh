#!/bin/bash

echo "🔧 VP18 TikTok Media Player - Build Script"
echo "=========================================="

# Check if Android Studio is available
if command -v android-studio &> /dev/null; then
    echo "✅ Android Studio found"
    echo "💡 Recommended: Open this project in Android Studio"
    echo "   File → Open → Select this folder"
    echo "   Then click 'Sync' and 'Run'"
    exit 0
fi

# Check if gradle is available
if command -v gradle &> /dev/null; then
    echo "✅ Gradle found, attempting to build..."
    gradle assembleDebug
    exit $?
fi

echo "❌ Neither Android Studio nor Gradle found"
echo ""
echo "📱 To test this app, please:"
echo "1. Install Android Studio from: https://developer.android.com/studio"
echo "2. Open this project folder in Android Studio"
echo "3. Let Android Studio sync and download dependencies"
echo "4. Click 'Run' to build and test"
echo ""
echo "📋 What you'll be testing:"
echo "• TikTok-style vertical scrolling"
echo "• Swipe gestures for navigation"
echo "• Media source management"
echo "• Detail views and creator pages"
echo ""
echo "🎯 The app works immediately with sample data!"
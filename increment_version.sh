#!/bin/bash

# VP18 TikTok Media Player - Version Increment Script

echo "🔧 VP18 Version Increment"
echo "========================"

# Get current version from build.gradle
CURRENT_VERSION_CODE=$(grep "versionCode" app/build.gradle | grep -o '[0-9]\+')
CURRENT_VERSION_NAME=$(grep "versionName" app/build.gradle | sed 's/.*"\(.*\)".*/\1/')

echo "📋 Current Version:"
echo "   Code: $CURRENT_VERSION_CODE"
echo "   Name: $CURRENT_VERSION_NAME"

# Increment version code
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# Extract major.minor from version name and increment patch
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION_NAME"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

NEW_PATCH=$((PATCH + 1))
NEW_VERSION_NAME="$MAJOR.$MINOR.$NEW_PATCH"

echo ""
echo "🚀 New Version:"
echo "   Code: $NEW_VERSION_CODE" 
echo "   Name: $NEW_VERSION_NAME"

# Update build.gradle
sed -i "s/versionCode $CURRENT_VERSION_CODE/versionCode $NEW_VERSION_CODE/" app/build.gradle
sed -i "s/versionName \"$CURRENT_VERSION_NAME\"/versionName \"$NEW_VERSION_NAME\"/" app/build.gradle

echo ""
echo "✅ Updated app/build.gradle"
echo "📝 Changes:"
echo "   versionCode: $CURRENT_VERSION_CODE → $NEW_VERSION_CODE"
echo "   versionName: $CURRENT_VERSION_NAME → $NEW_VERSION_NAME"
echo ""
echo "🔄 Ready to build with new version!"

# Optional: Build automatically
read -p "🏗️  Build APK now? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔨 Building APK..."
    ./gradlew assembleDebug
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "🎉 Build Successful!"
        echo "📱 APK: ./app/build/outputs/apk/debug/app-debug.apk"
        echo "📊 Version: v$NEW_VERSION_NAME (Build $NEW_VERSION_CODE)"
        
        # Show APK size
        APK_SIZE=$(du -h ./app/build/outputs/apk/debug/app-debug.apk | cut -f1)
        echo "💾 Size: $APK_SIZE"
    else
        echo "❌ Build Failed"
        
        # Revert version changes on build failure
        echo "🔄 Reverting version changes..."
        sed -i "s/versionCode $NEW_VERSION_CODE/versionCode $CURRENT_VERSION_CODE/" app/build.gradle
        sed -i "s/versionName \"$NEW_VERSION_NAME\"/versionName \"$CURRENT_VERSION_CODE\"/" app/build.gradle
        echo "↩️  Reverted to previous version"
    fi
else
    echo "⏭️  Skipped build. Run './gradlew assembleDebug' when ready."
fi

echo ""
echo "📋 Usage for next time:"
echo "   ./increment_version.sh    # Increment and optionally build"
echo "   ./gradlew assembleDebug   # Build with current version"
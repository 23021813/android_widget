#!/bin/bash

# Configuration
AVD_NAME="CarLauncherTest"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo "🚀 Starting CarFloat Test Script..."

# 1. Close all running emulators
echo "🛑 Closing existing emulators..."
pkill -f qemu-system
pkill -f emulator
sleep 2

# 2. Start emulator
echo "📱 Starting emulator: $AVD_NAME..."
# Using -no-snapshot-save to ensure a fresh state if needed, but keeping standard boot
emulator -avd "$AVD_NAME" -no-audio -no-snapshot-save > /dev/null 2>&1 &

# 3. Wait for device to be ready
echo "⏳ Waiting for device to boot..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 2
done

echo "✅ Emulator is ready!"

# 4. Install APK
if [ -f "$APK_PATH" ]; then
    echo "📦 Installing APK: $APK_PATH..."
    adb install -r "$APK_PATH"
    
    echo "🔓 Granting Overlay Permission..."
    adb shell appops set com.carlauncher SYSTEM_ALERT_WINDOW allow
    
    echo "🏃 Starting CarFloat..."
    adb shell am start -n com.carlauncher/.LauncherActivity
    
    echo "🎉 Done! CarFloat is running."
else
    echo "❌ Error: APK not found at $APK_PATH. Please run './gradlew assembleDebug' first."
fi

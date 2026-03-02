@echo off
set ANDROID_SDK_ROOT=D:\Android\Sdk
set ANDROID_HOME=D:\Android\Sdk
echo Starting Android Automotive Emulator...
D:\Android\Sdk\emulator\emulator.exe -avd CarLauncher_Test -no-audio -gpu auto -memory 2048

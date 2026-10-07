name: Build Myraa APK
on:
 workflow_dispatch:
 push:
  branches: [main, master]
permissions:
 contents: write
jobs:
 build:
  runs-on: ubuntu-latest
  steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
     distribution: temurin
     java-version: '17'
  - uses: gradle/actions/setup-gradle@v4
    with:
     gradle-version: '8.7'
  - name: Write files
    run: |
     mkdir -p myraa-app/app/src/main/java/com/myraa/app myraa-app/app/src/main/res/drawable
     cat > myraa-app/settings.gradle.kts <<'EOF'
     pluginManagement {
     repositories {
     google()
     mavenCentral()
     gradlePluginPortal()
     }
     }
     dependencyResolutionManagement {
     repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
     repositories {
     google()
     mavenCentral()
     }
     }
     rootProject.name = "Myraa"
     include(":app")
     EOF
     cat > myraa-app/build.gradle.kts <<'EOF'
     plugins {
     id("com.android.application") version "8.5.2" apply false
     id("org.jetbrains.kotlin.android") version "1.9.24" apply false
     }
     EOF
     cat > myraa-app/gradle.properties <<'EOF'
     org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
     android.nonTransitiveRClass=true
     kotlin.code.style=official
     EOF
     cat > myraa-app/app/build.gradle.kts <<'EOF'
     plugins {
     id("com.android.application")
     id("org.jetbrains.kotlin.android")
     }
     android {
     namespace = "com.myraa.app"
     compileSdk = 34
     defaultConfig {
     applicationId = "com.myraa.app"
     minSdk = 24
     targetSdk = 34
     versionCode = 1
     versionName = "1.0"
     }
     buildTypes {
     release {
     isMinifyEnabled = false
     }
     }
     compileOptions {
     sourceCompatibility = JavaVersion.VERSION_17
     targetCompatibility = JavaVersion.VERSION_17
     }
     kotlinOptions {
     jvmTarget = "17"
     }
     }
     EOF
     cat > myraa-app/app/src/main/AndroidManifest.xml <<'EOF'
     <?xml version="1.0" encoding="utf-8"?>
     <manifest xmlns:android="http://schemas.android.com/apk/res/android">
     <uses-permission android:name="android.permission.INTERNET" />
     <uses-permission android:name="android.permission.RECORD_AUDIO" />
     <uses-permission android:name="android.permission.READ_CONTACTS" />
     <uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
     <queries>
     <intent>
     <action android:name="android.intent.action.MAIN" />
     <category android:name="android.intent.category.LAUNCHER" />
     </intent>
     <intent>
     <action android:name="android.speech.RecognitionService" />
     </intent>
     </queries>
     <application
     android:label="Myraa"
     android:icon="@drawable/ic_launcher"
     android:allowBackup="false"
     android:theme="@android:style/Theme.Material.NoActionBar">
     <activity
     android:name=".MainActivity"
     android:exported="true"
     android:screenOrientation="portrait"
     android:windowSoftInputMode="adjustResize">
     <intent-filter>
     <action android:name="android.intent.action.MAIN" />
     <category android:name="android.intent.category.LAUNCHER" />
     </intent-filter>
     </activity>
     </application>
     </manifest>
     EOF
     cat > myraa-app/app/src/main/res/drawable/ic_launcher.xml <<'EOF'
     <?xml version="1.0" encoding="utf-8"?>
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
     android:width="108dp"
     android:height="108dp"
     android:viewportWidth="108"
     android:viewportHeight="108">
     <path
     android:fillColor="#0E0B1A"
     android:pathData="M0,0h108v108h-108z" />
     <path
     android:fillColor="#FF5FA2"
     android:pathData="M54,54m-32,0a32,32 0,1 1,64 0a32,32 0,1 1,-64 0" />
     <path
     android:pathData="M40,66L40,42L54,56L68,42L68,66"
     android:strokeColor="#0E0B1A"
     android:strokeWidth="7"
     android:strokeLineCap="round"
     android:strokeLineJoin="round" />
     </vector>
     EOF
     cp MainActivity.kt myraa-app/app/src/main/java/com/myraa/app/MainActivity.kt
  - name: Licenses
    run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
  - name: Build
    working-directory: myraa-app
    run: gradle assembleDebug --no-daemon --stacktrace
  - uses: actions/upload-artifact@v4
    with:
     name: Myraa-apk
     path: myraa-app/app/build/outputs/apk/debug/app-debug.apk
  - name: Release
    env:
     GH_TOKEN: ${{ github.token }}
    run: |
     cp myraa-app/app/build/outputs/apk/debug/app-debug.apk Myraa.apk
     gh release create "v${{ github.run_number }}" Myraa.apk --repo "${{ github.repository }}" --title "Myraa ${{ github.run_number }}" --notes "Install Myraa.apk on your phone."

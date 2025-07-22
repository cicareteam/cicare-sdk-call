
# 📞 CiCare SDK Call for Android Kotlin

A lightweight call UI & notification SDK for Android, built on top of a signaling system and WebRTC call engine.

### Features

* 📱 Incoming & Outgoing Call Screens
* 🔔 Full Notification Handling (incoming, ongoing)
* ⏱ Call Timer & State Handling
* ⚙️ Easy Integration via JitPack

---

## 📦 Installation

Add JitPack to `settings.gradle`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the SDK to your app-level `build.gradle`:

```kotlin
dependencies {
    implementation("com.github.cicareteam:cicare-sdk-call:v1.2.0-alpha.1")
}
```

---

## 🚀 Usage

### 1. Init SDK

```kotlin
CiCareSdkCall.init(context).checkAndRequestPermissions()
```

---

### 2. Show Incoming Call

```kotlin
CiCareSdkCall.init(context).showIncoming(
    callerId = "user123",
    callerName = "John Doe",
    callerAvatar = "https://url.com/avatar.jpg",
    calleeId = "user789",
    calleeName = "Alice",
    calleeAvatar = "https://url.com/avatar2.jpg",
    checkSum = "secure-checksum",
    metaData = mapOf("key" to "value"),
    tokenCall = "authTokenHere",
    server = "https://your-signaling-server.com",
    isFromPhone = false
)
```

---

### 3. Make Outgoing Call

```kotlin
CiCareSdkCall.init(context).makeCall(
    callerId = "user123",
    callerName = "John Doe",
    callerAvatar = "https://url.com/avatar.jpg",
    calleeId = "user789",
    calleeName = "Alice",
    calleeAvatar = "https://url.com/avatar2.jpg",
    checkSum = "secure-checksum",
    metaData = mapOf("key" to "value")
)
```

> ⚠️ `makeCall` is a `suspend` function — use in coroutine.

---

## 🛠 Manifest Setup

```xml
<service
    android:name="cc.cicare.sdkcall.services.CiCareCallService"
    android:foregroundServiceType="microphone|phoneCall"
    android:exported="false" />

<activity
    android:name="cc.cicare.sdkcall.notifications.ui.ScreenCallActivity"
    android:launchMode="singleTop"
    android:showWhenLocked="true"
    android:exported="false" />
```

---

## 🔐 Permissions

Automatically requested:

* `RECORD_AUDIO`
* `FOREGROUND_SERVICE`
* `POST_NOTIFICATIONS` (Android 13+)
* `FOREGROUND_SERVICE_PHONE_CALL` (Android 14+)
* `FOREGROUND_SERVICE_MICROPHONE` (Android 14+)

---

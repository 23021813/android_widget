# 📋 Implementation Plan: Schedule Automation & Voice Command

**Version**: v1.4.0  
**Date**: 2026-03-02  
**Status**: In Progress

---

## 🔵 Feature 1: Schedule Automation (Lịch trình tự động)

### Mô tả
Cho phép user cấu hình lịch trình tự động theo ngày trong tuần và khung giờ. Khi alarm kích hoạt:
1. Chia đôi màn hình (split-screen) với 2 app đã cài đặt
2. (Tùy chọn) Mở Google Maps dẫn đường đến địa chỉ preset
3. (Tùy chọn) Mở YouTube Music tìm kiếm theo từ khóa preset

### Lưu ý kỹ thuật
- **Head unit tắt khi xe tắt máy** → Alarm sẽ bị mất. Cần re-register alarm trong `BootReceiver` khi xe khởi động lại.
- Dùng `AlarmManager.setExactAndAllowWhileIdle()` cho độ chính xác cao.
- Mỗi lần boot → đọc ScheduleConfig từ DataStore → đặt lại alarm nếu enabled.

### Data Model
```kotlin
// Thêm vào LauncherSettings
val scheduleEnabled: Boolean = false,
val scheduleDays: Set<Int> = setOf(2,3,4,5,6), // Calendar.MONDAY(2)..FRIDAY(6)
val scheduleHour: Int = 7,
val scheduleMinute: Int = 30,
val scheduleAutoNavigate: Boolean = false,
val scheduleNavigationAddress: String = "",
val scheduleAutoMusic: Boolean = false,
val scheduleMusicKeyword: String = "",
```

### Files

| File | Action | Description |
|------|--------|-------------|
| `data/models/LauncherSettings.kt` | MODIFY | Thêm schedule fields |
| `data/SettingsDataStore.kt` | MODIFY | Thêm DataStore keys + mapping |
| `service/ScheduleManager.kt` | CREATE | Quản lý AlarmManager (set/cancel alarm) |
| `receiver/ScheduleReceiver.kt` | CREATE | BroadcastReceiver xử lý alarm trigger |
| `receiver/BootReceiver.kt` | MODIFY | Re-register alarm khi boot |
| `ui/screens/SettingsScreen.kt` | MODIFY | Thêm Schedule section UI |
| `AndroidManifest.xml` | MODIFY | Đăng ký ScheduleReceiver + quyền |
| `res/values*/strings.xml` | MODIFY | 8 ngôn ngữ |

### Luồng thực thi
```
BootReceiver → ScheduleManager.registerAlarm()
                    ↓
AlarmManager fires at scheduled time
                    ↓
ScheduleReceiver.onReceive()
    → Check: is today in scheduleDays?
    → YES:
        1. SplitScreenLauncher.launchSplitScreen(frame1, frame2)
        2. delay(3000) → if autoNavigate: Google Maps navigation intent
        3. delay(2000) → if autoMusic: YouTube Music search intent
    → Re-schedule alarm for next day
```

---

## 🔴 Feature 2: Voice Command Overlay

### Mô tả
User gán action "Voice Command" vào một gesture (Tap/Long Press/Double Tap) của nút Assistant trong Settings. Khi kích hoạt:
1. Voice Overlay nổi lên với animation
2. SpeechRecognizer nhận dạng giọng nói (Tiếng Việt + English)
3. Parse lệnh: Dẫn đường / Tìm nhạc / Mở video
4. Thực thi lệnh tương ứng

### Cách gán trong Settings
- Thêm "app ảo" `com.carlauncher.ACTION_VOICE_COMMAND` vào danh sách App Picker (tương tự `com.carlauncher.ACTION_HOME` hiện tại)
- User chọn action này cho bất kỳ gesture nào (Tap, Long Press, Double Tap)

### Permission Strategy: Lazy (xin khi cần)
- Khi user chọn "Voice Command" trong App Picker → kiểm tra RECORD_AUDIO permission
- Nếu chưa có → hiện dialog giải thích → xin quyền
- Khi kích hoạt voice command mà chưa có quyền → Toast thông báo cần vào Settings cấp quyền

### Data Model
- Không cần thêm field mới vào LauncherSettings
- Dùng magic string `com.carlauncher.ACTION_VOICE_COMMAND` (tương tự `ACTION_HOME`)

### Command Patterns (Regex-based)

| Intent | Tiếng Việt | English |
|--------|-----------|---------|
| Navigation | dẫn đường đến X, chỉ đường đến X, đưa tôi đến X, đi đến X | navigate to X, directions to X, take me to X, go to X |
| Music | tìm bài hát X, phát nhạc X, nghe bài X, mở bài X | play song X, find song X, play music X |
| Video | mở video X, xem video X, phát video X | open video X, play video X, watch X |

### Voice Overlay UI
```
┌─────────────────────────────────┐
│  🎙️ Đang nghe...                │  ← Glassmorphism background
│                                  │
│  "Dẫn đường đến công ty"        │  ← Real-time partial results
│                                  │
│      [ 🔴 Pulse Animation ]     │  ← Ripple effect khi đang nghe
│                                  │
│         [ ✕ Hủy ]               │
└─────────────────────────────────┘
```

### Files

| File | Action | Description |
|------|--------|-------------|
| `service/voice/VoiceCommandParser.kt` | CREATE | Parse text → command type + parameter |
| `service/voice/VoiceCommandExecutor.kt` | CREATE | Thực thi command (Maps/Music/Video intent) |
| `service/voice/VoiceOverlayManager.kt` | CREATE | Quản lý SpeechRecognizer + overlay UI (ComposeView) |
| `service/OverlayService.kt` | MODIFY | Thêm xử lý ACTION_VOICE_COMMAND trong gesture handlers |
| `ui/screens/SettingsScreen.kt` | MODIFY | Thêm Voice Command vào assistantApps list |
| `AndroidManifest.xml` | MODIFY | Thêm RECORD_AUDIO permission |
| `res/values*/strings.xml` | MODIFY | 8 ngôn ngữ |

### Luồng thực thi
```
User Long Press (hoặc gesture đã gán) nút Assistant
    ↓
OverlayService detect action = "com.carlauncher.ACTION_VOICE_COMMAND"
    ↓
VoiceOverlayManager.show()
    → Hiện floating overlay với mic animation
    → Khởi tạo SpeechRecognizer
    → Bắt đầu listening
    ↓
SpeechRecognizer callback (onPartialResults / onResults)
    → Hiển thị text real-time trên overlay
    ↓
VoiceCommandParser.parse(text) → ParsedCommand
    ↓
VoiceCommandExecutor.execute(command)
    → Maps / YouTube Music / YouTube intent
    ↓
Auto-dismiss overlay sau 2s
```

---

## 🔧 Permission Strategy

| Permission | Khi nào xin | Cách xin |
|-----------|-------------|----------|
| `RECORD_AUDIO` | Khi user lần đầu kích hoạt Voice Command | Runtime permission dialog |
| `SCHEDULE_EXACT_ALARM` | Khi user bật Schedule toggle | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` intent (Android 12+) |
| `SYSTEM_ALERT_WINDOW` | Đã có (hiện tại) | Giữ nguyên |

---

## 📊 Task Breakdown

### Phase 1: Data Layer
1. ✅ Mở rộng `LauncherSettings` data class
2. ✅ Cập nhật `SettingsDataStore` keys

### Phase 2: Schedule Automation
3. ✅ Tạo `ScheduleManager.kt`
4. ✅ Tạo `ScheduleReceiver.kt`  
5. ✅ Cập nhật `BootReceiver.kt`

### Phase 3: Voice Command
6. ✅ Tạo `VoiceCommandParser.kt`
7. ✅ Tạo `VoiceCommandExecutor.kt`
8. ✅ Tạo `VoiceOverlayManager.kt`

### Phase 4: Integration
9. ✅ Cập nhật `OverlayService.kt` (Voice Command handler)
10. ✅ Cập nhật `SettingsScreen.kt` (Schedule UI + Voice option)

### Phase 5: Platform
11. ✅ Cập nhật `AndroidManifest.xml`
12. ✅ Cập nhật strings.xml (8 ngôn ngữ)

### Phase 6: Testing
13. ✅ Setup emulator & build test

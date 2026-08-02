# Design: Chế độ nghỉ ngơi (Rest Mode) — CarFloat

- **Ngày:** 2026-08-02
- **Trạng thái:** Đã duyệt bởi user (2026-08-02)
- **App:** CarFloat (carlauncher), v1.5.7 / Android 13, Kotlin + Compose + OverlayService

## 1. Mục đích

Người dùng muốn một chế độ "nghỉ ngơi": khi ngồi chờ/nghỉ trong xe, kích hoạt chế độ để màn hình tối đi nhưng **nhạc vẫn tiếp tục phát**. Kích hoạt từ QuickMenu.

## 2. Phát hiện nghiên cứu (tóm tắt)

- **Tbox (S2P/Ambient Led, QC6115, Android 13)** có firmware hỗ trợ tắt màn hình gốc (changelog OTA Tbox Plus item 97: tắt màn hình bằng remote Bluetooth), nhưng cơ chế nội bộ chưa rõ — không dựa vào được.
- Android không cho phép app thường (không root, không phải app hệ thống) tắt màn hình qua API công khai (`goToSleep` cần `DEVICE_POWER`; `KEYCODE_SLEEP` cần `INJECT_EVENTS`).
- Các phương án khác: hack `SCREEN_OFF_TIMEOUT` + `WRITE_SETTINGS` (rủi ro firmware chặn stay-awake), Shizuku + SurfaceControl (cần cài Shizuku + ADB — nặng), root (người dùng không root).
- **Giới hạn Vinfast:** người dùng chạy xe Vinfast — màn hình MDU chia nhiều frame, CarPlay/AA chỉ chạy trong 1 cửa sổ. App trên Tbox **không thể tắt toàn bộ màn hình xe** (2 hệ thống độc lập, không có API). Nap Mode của Vinfast yêu cầu gói trả phí — người dùng không dùng.
- **Kết luận:** phương án khả thi nhất, miễn phí, không cần quyền mới: **overlay đen phủ toàn bộ cửa sổ CarPlay/AA** bằng chính hạ tầng overlay đã có. Trên xe CarPlay toàn màn hình (không phải Vinfast) hiệu quả tương đương tắt màn hình thật.

## 3. Thiết kế

### 3.1 Luồng tương tác

```
Kích hoạt (QuickMenu → "Nghỉ ngơi")
  → Overlay đen full-screen (che toàn bộ cửa sổ CarPlay/AA)
  → Ẩn toàn bộ overlay CarFloat: status widget, nút trợ lý, drag handle
  → Nhạc vẫn phát (không đụng tới audio)
  → Tùy chọn: hẹn giờ tự thoát (15/30/60 phút hoặc không)

Thoát:
  1. Chạm bất kỳ đâu trên màn hình đen
  2. Hết giờ hẹn tự thoát (nếu bật)
  → Gỡ overlay đen, khôi phục overlay CarFloat theo cài đặt hiện tại
```

### 3.2 Thành phần

| Thành phần | File | Nội dung |
|---|---|---|
| Hằng số action mới | `data/models/LauncherSettings.kt` | `VirtualActions.ACTION_REST_MODE = "com.carlauncher.ACTION_REST_MODE"` |
| Mục QuickMenu | `ui/screens/QuickMenuActivity.kt` | Thêm mục ảo "Nghỉ ngơi" cạnh Home/Split/WiFi (dòng ~54-70); trong `handleAppSelected` gọi `OverlayService.instance?.enterRestMode()` (pattern companion var như `splitInProgress`, OverlayService.kt:78) |
| Trạng thái + hành vi | `service/OverlayService.kt` | Companion thêm `@Volatile var instance: OverlayService? = null` (gán trong `onCreate`, null trong `onDestroy`); thêm `restView: View?`, `isRestModeActive: Boolean`; `enterRestMode()`, `exitRestMode()`, `removeRestOverlay()` |
| Overlay đen | `service/OverlayService.kt` | View màu đen tuyệt đối, `TYPE_APPLICATION_OVERLAY`, MATCH_PARENT, flags: `FLAG_NOT_FOCUSABLE \| FLAG_NOT_TOUCH_MODAL \| FLAG_LAYOUT_NO_LIMITS` (phủ cả status bar), `PixelFormat.OPAQUE`; `setOnClickListener { exitRestMode() }` |
| Cài đặt | `data/SettingsDataStore.kt` | Key mới: `restModeAutoExitMinutes` (int, mặc định 0 = không) |
| UI cài đặt | `ui/screens/SettingsScreen.kt` | Section "Nghỉ ngơi": chọn thời gian tự thoát (Không/15/30/60 phút) + hiển thị `rest_mode_hint` |
| Chuỗi | `res/values/strings.xml` + `res/values-vi/strings.xml` | Xem §3.5 |

### 3.3 Chi tiết hành vi OverlayService

- **`enterRestMode()`:**
  1. Nếu đã active → return.
  2. Ẩn overlay hiện có: `removeStatusOverlay()`, `removeAssistantOverlay()`, `removeDragHandle()`.
  3. Tạo view đen, add vào `windowManager`.
  4. `isRestModeActive = true`. Đọc `restModeAutoExitMinutes` từ `settingsDataStore.settingsFlow.first()`; nếu `> 0` → lên lịch `serviceScope.launch { delay(...); exitRestMode() }` (huỷ lịch cũ nếu có, lưu `Job`).
  5. Log `RestMode entered`.
- **`exitRestMode()`:**
  1. Nếu không active → return.
  2. Gỡ view đen (`removeRestOverlay()`), `isRestModeActive = false`, huỷ Job hẹn giờ.
  3. Khôi phục overlay theo settings hiện tại: `showStatusOverlay()` / `showAssistantOverlay()` (hàm đã có, tự kiểm tra `showStatusWidget`/`showAssistantWidget`). Drag handle phục hồi theo logic click-through hiện có.
  4. Log `RestMode exited`.
- **Giao tiếp QuickMenu ↔ Service:** QuickMenuActivity gọi trực tiếp `OverlayService.instance?.enterRestMode()` qua companion var (pattern có sẵn trong codebase — `splitInProgress`). Không cần broadcast/`onStartCommand` mới. Nếu `instance == null` (service chưa chạy — không có overlay nền nào hoạt động): không làm gì (rest mode vô nghĩa khi service tắt).
- **Ẩn/hiện khi có activity phía trên:** overlay đen vẫn nằm dưới activity toàn màn hình (QuickMenu/Settings). Nếu người dùng mở Settings → vẫn hiện phía trên màn đen (bình thường). Khi thoát rest mode mà có activity hiển thị → `exitRestMode()` vẫn chạy bình thường, overlay nổi lên theo z-order vốn có.
- **onDestroy của service** (dòng ~926): thêm `removeRestOverlay()` vào nhóm dọn dẹp — tránh view leak; `instance = null`.

### 3.4 Dữ liệu & cài đặt

- `restModeAutoExitMinutes: Int = 0` — 0 = không tự thoát; 15/30/60 = tự thoát sau N phút.
- Không cần quyền mới, không sửa AndroidManifest (overlay đen dùng chung quyền `SYSTEM_ALERT_WINDOW` đã có).

### 3.5 Chuỗi (EN + VI)

| Key | EN | VI |
|---|---|---|
| `action_rest_mode_label` | Rest Mode | Nghỉ ngơi |
| `section_rest_mode` | 😴 Rest Mode | 😴 Nghỉ ngơi |
| `rest_mode_auto_exit` | Auto exit after | Tự thoát sau |
| `rest_mode_auto_exit_none` | Never | Không |
| `rest_mode_auto_exit_15m` | 15 minutes | 15 phút |
| `rest_mode_auto_exit_30m` | 30 minutes | 30 phút |
| `rest_mode_auto_exit_60m` | 60 minutes | 60 phút |
| `rest_mode_hint` | Screen turns black, music keeps playing. Tap to wake. | Màn hình tối đen, nhạc vẫn chạy. Chạm để thức dậy. |

## 4. Xử lý lỗi & biên

- **`windowManager.addView` fail** (hiếm gặp, thiếu quyền overlay): bắt exception, log, không crash — giữ nguyên trạng thái, thông báo qua log.
- **QuickMenu bị phủ:** QuickMenu tự `finish()` sau khi chọn action (pattern có sẵn `handleAppSelected`).
- **Trùng lặp:** `isRestModeActive` ngăn enter 2 lần / exit khi chưa enter.
- **Restore không đúng cấu hình:** luôn dựa trên settings mới nhất từ `settingsDataStore.settingsFlow.first()` khi exit.

## 5. Kiểm thử

- **Build:** `./gradlew assembleDebug` (hoặc `run_test.sh` nếu cần emulator).
- **Test tay trên thiết bị thật (Vinfast + Tbox S2P):**
  1. Bật app, cấp overlay permission → mở QuickMenu → bấm "Nghỉ ngơi".
  2. Xác nhận: toàn bộ cửa sổ CarPlay/AA đen, widget/nút trợ lý biến mất, nhạc (YouTube Music) vẫn phát.
  3. Chạm màn hình → thoát, các overlay khôi phục đúng trạng thái trước đó.
  4. Bật hẹn giờ 15 phút → chờ → tự thoát đúng lúc (log kiểm chứng).
  5. Bật/tắt lặp lại 10 lần liên tục → không crash, không view leak (logcat).
  6. Tắt app giữa chừng (khi đang rest) → service dừng sạch, không sót overlay.
- **Kiểm thử tự động:** dự án không có test hạ tầng overlay (service/WindowManager); không viết unit test cho phần này.

## 6. Ngoài phạm vi

- Tắt màn hình thật sự (backlight xe) — bất khả thi từ app trên Vinfast (§2).
- Tích hợp Shizuku / WRITE_SETTINGS / root — loại bỏ ở vòng brainstorm.
- Kích hoạt bằng cử chỉ nút trợ lý / tự động sau thời gian chờ — user chỉ chọn QuickMenu.
- Nap Mode Vinfast (trả phí) — không liên quan.

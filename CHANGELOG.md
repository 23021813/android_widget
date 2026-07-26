# Changelog

Tất cả các thay đổi đáng chú ý đối với dự án CarFloat sẽ được ghi lại trong tệp này.

## [1.5.6] - 2026-07-26
### Changed
- **Wi-Fi Popup Action**: Thay đổi cơ chế "Ép kết nối Wi-Fi" ngầm (background) vốn không tương thích trên một số hệ điều hành (như Tbox S2P Android 13) bằng một Activity trong suốt (Trampoline Activity) có tên **Bảng Wi-Fi**. Khi gán tính năng này vào phím tắt Assistant, popup cài đặt mạng của hệ thống sẽ nổi lên để người dùng thao tác trực tiếp, vượt qua hạn chế khởi chạy Panel từ Background của Android 12+.
- `versionCode` 27 → 28, `versionName` 1.5.5 → 1.5.6.

## [1.5.5] - 2026-06-25

## [1.5.1] - 2026-06-23
### Added
- **Pre-split app timing fix**: Khi auto-split-on-boot, app pre-split được detect khi đã vào màn hình chính (qua UsageStatsManager polling) thay vì delay cố định, khắc phục split-screen chạy trước khi app sẵn sàng.
- **Pre-split delay max 5 phút**: Slider cấu hình delay từ 5 giây lên 5 phút, phù hợp với app khởi động chậm.

### Removed
- **Parking Alert**: Gỡ bỏ hoàn toàn tính năng cảnh báo xe đứng yên (Telegram/Email). Code + dependencies (security-crypto, android-mail) đã xoá sạch.

### Changed
- `versionCode` 22 → 23, `versionName` 1.5.0 → 1.5.1.

## [1.5.0] - 2026-06-18
### Added
- **Parking Alert**: Tính năng cảnh báo khi xe đứng yên quá lâu. Theo dõi vị trí GPS mỗi 60s; nếu khoảng cách di chuyển < ngưỡng (mặc định 50m) trong suốt khoảng thời gian cấu hình (mặc định 15 phút), hệ thống sẽ gửi cảnh báo qua **Telegram** và/hoặc **Email (SMTP)**.
- **Encrypted secrets**: Bot token Telegram và mật khẩu SMTP được mã hoá bằng `EncryptedSharedPreferences` (AndroidKeyStore AES-256-GCM).
- **Cooldown + persistent state**: Thời gian nghỉ giữa 2 lần gửi cảnh báo có thể cấu hình (mặc định 30 phút). Trạng thái được lưu vào DataStore.
- **Test Alert button**: Nút "Gửi thử ngay" trong Settings.
- **Settings UI**: Thêm section "🅿️ Cảnh báo đỗ xe".

### Dependencies
- `androidx.security:security-crypto-ktx:1.1.0-alpha06` (mã hoá secrets)
- `com.sun.mail:android-mail:1.6.7` + `com.sun.mail:android-activation:1.6.7` (SMTP)

### Changed
- `versionCode` 21 → 22, `versionName` 1.4.6 → 1.5.0.

## [1.4.3] - 2026-03-26
### Changed
- Comprehensive version upgrade and system stabilization.
- Optimization of build system for multi-machine synchronization.

## [1.4.23] - 2026-03-26
### Added
- Intermediate build for feature verification and key synchronization.

<SAME%>
### Added
- **TimeSyncMonitor**: Cơ chế mới chờ kết nối mạng và xác thực giờ chuẩn từ máy chủ Google trước khi xếp lịch (Sync Alarms). Đảm bảo trigger đúng giờ ngay cả khi Android Box bị mất giờ hệ thống (reset về 1970).
- **Robustness Improvements**: Tự động thử lại (Retry) khi WiFi mới kết nối và chống xung đột logic khi OS cập nhật giờ.

## [1.3.8] - 2026-03-07
### Fixed
- **Schedule Selection Logic**: Sửa lỗi luôn chọn lịch trình buổi sáng. Giờ đây app ưu tiên chọn lịch trình khớp với khung giờ hiện tại hoặc gần nhất (Closest-to-current logic).
- **OTA Ghost Notification**: Thêm cơ chế cache-buster (timestamp query) khi kiểm tra bản cập nhật từ GitHub để tránh thông báo ảo do cache.
- **Overlay Boot Trigger**: Đảm bảo vừa chia đôi màn hình vừa thực hiện action (Nav/Music) chính xác khi mở app trong khung giờ lịch.

### Added
- **Detailed Logging**: Bổ sung log hệ thống chuyên sâu cho `ScheduleManager`, `ScheduleReceiver` và `OtaUpdateManager` để dễ dàng chẩn đoán lỗi vận hành.

## [1.3.7] - 2026-03-06
### Changed
- **Voice Removal**: Gỡ bỏ hoàn toàn tính năng lắng nghe giọng nói chủ động và quyền micro (`RECORD_AUDIO`) để tăng tính riêng tư.
- **Language Optimization**: Rút gọn chỉ còn 2 ngôn ngữ chính (Tiếng Việt, Tiếng Anh). Tự động nhận diện hệ thống Việt Nam để ưu tiên hiển thị.
- **Version Display**: Thống nhất hiển thị version bằng `BuildConfig.VERSION_NAME` xuyên suốt ứng dụng.

## [1.3.6] - 2026-03-04
### Added
- **CarFloat (Split View) Action**: Cho phép gán phím tắt mở nhanh 2 ứng dụng chia đôi màn hình vào nút Assistant (Tap/Double Tap/Long Press) và chấm trắng Drag Handle.
- **Sample Schedule Button**: Thêm nút "+ Thêm Lịch trình mẫu" trong Settings để nhanh chóng tạo kịch bản thử nghiệm.

### Fixed
- **Schedule Boot Race Condition**: Khắc phục lỗi kịch bản tự động bị gián đoạn khi khởi động lại thiết bị (Race condition giữa OverlayService và ScheduleReceiver).
- **Voice Assistant Improvements**: 
    - Tự động nhận diện và yêu cầu quyền Micro (`RECORD_AUDIO`) khi khởi động app.
    - Cơ chế **Fallback tự động**: Tự động chuyển từ On-Device sang Network recognition nếu gặp lỗi hỗ trợ ngôn ngữ (Error 12).
    - Cải thiện logic kích hoạt: Nút Assistant luôn ưu tiên mở Voice Overlay tích hợp nếu chưa gán app cụ thể.
    - Sửa lỗi build do thiếu import `Log` và `Build` trong `OverlayService`.

## [1.3.5] - 2026-03-01
### Added
- **3-Tier Connectivity States**: Hiển thị trạng thái kết nối Wifi, Bluetooth, GPS theo 3 màu: Xám (Tắt), Trắng (Bật/Chưa kết nối), Xanh Cyan (Đã kết nối).
- **Home Button Assignment**: Thêm "ứng dụng ảo" Home vào App Picker, cho phép gán phím Home vật lý vào nút Assistant mà không cần quyền Accessibility.
- **Auto-Mirrored Icons**: Cập nhật hệ thống Icon Pack Material 3 để tự động đối xứng (mirrored) cho các ngôn ngữ RTL (nếu có).

### Fixed
- Lỗi không nhận diện được trạng thái kết nối Wifi/Bluetooth trên các thiết bị giới hạn quyền runtime hoặc Emulator (Sử dụng ConnectivityManager & AudioManager fallback).
- Sửa lỗi deprecation warnings trong code của `OverlayService`.

## [1.3.0] - 2026-03-01
### Added
- **Chế độ Click-Through**: Phép widget đồng hồ tàng hình với cảm ứng.
- **Cơ chế Kéo thả mới**: Nhấn giữ (Long press) vào chấm nhỏ 12dp để kéo widget trong chế độ click-through.
- **Nút Voice Đa năng**: Hỗ trợ 3 cử chỉ (Tap, Long Press, Double Tap) gán cho các app khác nhau.
- **Tùy chọn Icon Voice**: Cho phép chọn icon mic từ Icon Pack trong Settings.
- **Kích thước Độc lập**: Thanh trượt scale riêng cho Status Widget và Voice Widget.
- **Ghi nhớ Vị trí**: Tự động lưu tọa độ x, y vào DataStore.
- **Hệ thống OTA**: Tự động kiểm tra cập nhật và cài đặt APK từ GitHub Releases.
- **Nút Check Update**: Cho phép kiểm tra cập nhật thủ công trong menu About.
- **Help Center**: Bảng hướng dẫn cử chỉ (Gesture Guide) chi tiết.
- **Mở rộng Thanh Hệ thống**: Tùy chọn `Layout No Limits` để đè lên Notification/Navigation bar.
- **Auto Split-View**: Tự khởi động chế độ chia đôi màn hình khi boot máy (yêu cầu cấu hình sẵn).

### Fixed
- Lỗi widget không nhận cảm ứng kéo thả trên màn hình DPI cao (chuyển từ pixel sang dp).
- Lỗi Hardcoded strings trong `OverlayService`.
- Sửa lỗi thiếu import và crash khi khởi động Service.
- Tối ưu hóa độ trong suốt của chấm nhỏ kéo thả (15% alpha).

### Changed
- Cập nhật bộ ngôn ngữ (8 quốc gia) đầy đủ và chính xác hơn.
- Cập nhật App Icon sang dạng legacy để hỗ trợ các đầu Android cũ tốt hơn.

## [1.1.0] - 2026-02-15
### Added
- Hỗ trợ đa ngôn ngữ cơ bản.
- Giao diện Glassmorphism ban đầu.
- Tính năng Split Screen Proxy.

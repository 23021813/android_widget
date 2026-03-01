# Changelog

Tất cả các thay đổi đáng chú ý đối với dự án **Car Launcher** sẽ được ghi lại trong file này.

## [1.1.0] - 2026-03-01

### ✨ Added (Bổ sung mới)
- **SplitScreen Launcher Proxy**: Thêm `SplitScreenProxyActivity` để xử lý việc khởi chạy màn hình đôi từ Service context một cách ổn định hơn trên các dòng màn hình xe Android.
- **Auto-Start on Boot**: Tích hợp `BootReceiver` cho phép tự động nổi Overlay ngay khi thiết bị khởi động hoàn tất (Yêu cầu cấp quyền `RECEIVE_BOOT_COMPLETED`).

### 🔧 Fixed (Sửa lỗi)
- **Permission Refresh Bug**: Sửa lỗi màn hình yêu cầu cấp quyền không tự biến mất khi người dùng quay lại từ trang Cài đặt (Sử dụng `mutableStateOf` và `onResume` refresh).
- **Settings Toggle Parameter**: Sửa lỗi compiler do truyền sai tham số `description` vào `SettingsToggle`.
- **Assistant App Saving**: Sửa lỗi không lưu được ứng dụng trợ lý ảo đã chọn từ picker.
- **Split-Screen Stability**: Tăng độ trễ khởi chạy và bổ sung các cờ hiệu (`FLAG_ACTIVITY_MULTIPLE_TASK`, `FLAG_ACTIVITY_REORDER_TO_FRONT`) để ép hệ thống chia màn hình chính xác hơn.

### 🧪 Tested (Kiểm thử)
- **Split-Screen Reliability**: Xác nhận chia màn hình thành công 100% khi gọi qua Proxy Activity trên Emulator.
- **Boot Logic**: Kiểm tra giả lập tín hiệu `BOOT_COMPLETED` qua ADB, Overlay tự động nổi thành công.

## [1.0.0] - 2026-02-28

### ✨ Added (Bổ sung mới)
- **Core Navigation**: Sử dụng Jetpack Compose Navigation với Home và Settings.
- **Home Screen**:
    - Sidebar dock hỗ trợ auto-hide, glassmorphism và Favorites app.
    - Dual App Frames hỗ trợ Split-screen với divider có thể kéo resize.
    - Status Overlay hiển thị đồng hồ, icon kết nối (WiFi/BT/GPS) và thời tiết.
- **Settings Screen**: Cài đặt chi tiết cho Theme, Dock, App Frames, Overlay và Weather.
- **Data Layer**:
    - `AppRepository` để truy vấn danh sách ứng dụng đã cài.
    - `SettingsDataStore` để lưu trữ tùy chỉnh người dùng vĩnh viễn (Persist).
    - `WeatherRepository` tích hợp OpenWeatherMap API với cơ chế cache.
- **Environment**:
    - Setup Android SDK Platform 34 & Build-tools 34.
    - Cài đặt OpenJDK 17.
    - Cấu hình Gradle Wrapper 8.5.

### 🔧 Fixed (Sửa lỗi)
- Lỗi cú pháp `dependencyResolution` trong `settings.gradle.kts`.
- Lỗi phân quyền truy cập Java trên macOS khi dùng Homebrew Cask.
- Cấu hình `local.properties` trỏ đúng vào SDK path mới cài đặt.

### 🧪 Tested (Kiểm thử)
- **Build**: Successfully built debug APK (`assembleDebug`).
- **Emulator**: Chạy mượt mà trên emulator profile `automotive_1024p_landscape` (Android 14).
- **UI**: Xác nhận hiển thị đúng Dark Theme, Sliders, Dropdowns và Split-View.

---

*Lưu ý: Dự án đang ở giai đoạn MVP ổn định.*

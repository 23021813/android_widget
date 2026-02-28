# Changelog

Tất cả các thay đổi đáng chú ý đối với dự án **Car Launcher** sẽ được ghi lại trong file này.

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

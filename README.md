# CarFloat (Formerly Car Launcher Overlay)

CarFloat là một ứng dụng tiện ích độc đáo dành cho màn hình xe hơi (Automotive Android/Android Box) với giao diện tinh tế chuẩn "Glassmorphism". Ứng dụng vẽ đè (Float Overlay) các widget thông minh lên mọi màn hình khác, mang lại các điều khiển tiện ích ngay lập tức mà không phá vỡ app đang hiển thị bên dưới.

## 🚀 Tính Năng Nổi Bật

- **Chế Độ Chia Đôi Ứng Dụng (Split-Screen)**: Chạm vào đồng hồ để ngay lập tức khởi chạy hai ứng dụng chia màn hình song song.
- **Microphone Trợ Lý Ảo Độc Lập**: Nút mic nổi có thể gán cho bất kỳ trợ lý ảo nào (Kiki, Google Assistant, Voice Search).
- **Đa Ngôn Ngữ (Internationalization)**: Hỗ trợ 8 ngôn ngữ phổ biến (EN, VI, ZH, JA, KO, FR, DE) với khả năng tự động nhận diện hoặc chọn thủ công trong App.
- **Biểu Tượng Clickable Linh Hoạt**:
    - **Chạm 1 lần vào đồng hồ**: Chạy Split View.
    - **Chạm giữ vào đồng hồ / Chạm thời tiết**: Vào cài đặt CarFloat.
    - **Chạm icon WiFi/BT/GPS**: Mở ngay cài đặt hệ thống tương ứng.
- **Trạng Thái Kết Nối Thời Gian Thực**: Icons WiFi, Bluetooth, GPS thay đổi màu sắc và trạng thái dựa trên kết nối thực tế của hệ thống.
- **Trải Nghiệm Tùy Chỉnh Sâu**: Kéo thả linh hoạt mọi vị trí. Tùy chỉnh Kích thước, Độ mờ (Opacity) và Ẩn/Hiện riêng biệt từng widget.
- **Tự Khởi Động (Auto-Start)**: Hỗ trợ nạp Overlay chạy nền tự động ngay khi khởi động xe (Hỗ trợ cả QuickBoot cho các đầu Android chuyên dụng).

## 🛠 Cách Cài Đặt và Cấp Quyền
1. Cài đặt file APK `CarFloat_v1.1_i18n_Icon.apk`.
2. Mở App **CarFloat**. Cấp quyền "Hiển thị trên ứng dụng khác" (System Alert Window) khi được yêu cầu.
3. Quay lại, Overlay sẽ lập tức hiển thị trên cùng.

## ⚙️ Cấu Hình & Thao Tác

- **Click Đồng hồ**: Chạy Split View.
- **Giữ Đồng hồ / Click Weather**: Mở Settings.
- **Click Icons**: Mở cài đặt hệ thống (WiFi/BT/GPS).
- **Kéo thả**: Chạm giữ vào widget để di chuyển tự do.

## 💻 Tech Stack & Kiến Trúc
- **Ngôn ngữ**: Kotlin / Jetpack Compose
- **Target SDK**: 34 (Android 14)
- **Persistent Storage**: DataStore Preferences
- **i18n**: Android String Resources with Runtime Locale Switching.
- **Connectivity Monitoring**: System Service polling (3s intervals).

## 💡 Notes Dành Cho Dev
- Project sử dụng `attachBaseContext` để override ngôn ngữ tại runtime.
- Icon app được đóng gói dưới dạng Legacy PNG (`mipmap-xhdpi`) để đảm bảo hiển thị đồng nhất trên các đầu xe đời cũ.
- `SplitScreenProxyActivity` đóng vai trò là "trampoline" để vượt qua giới hạn khởi chạy Activity từ Service context.

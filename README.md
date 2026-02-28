# Car Overlay App (Android)

Car Overlay là một ứng dụng tiện ích độc đáo dành cho màn hình xe hơi (Automotive Android/Android Box) với giao diện tinh tế chuẩn "Glassmorphism". Ứng dụng vẽ đè (Float Overlay) các widget thông minh lên mọi màn hình khác, mang lại các điều khiển tiện ích ngay lập tức mà không phá vỡ app đang hiển thị bên dưới.

## 🚀 Tính Năng Nổi Bật

- **Chế Độ Chia Đôi Ứng Dụng Nhanh (Split-Screen)**: Chạm vào đồng hồ để ngay lập tức ép buộc hai ứng dụng khởi chạy dưới chế độ chia màn hình song song bằng công nghệ Android Reflection.
- **Microphone Trợ Lý Ảo Độc Lập**: Gán nút trợ lý ảo nổi đến bất kỳ app trợ lý nào (Kiki, Google Assistant).
- **Trải Nghiệm Tùy Chỉnh Sâu**: Xoay, Kéo Thả linh hoạt mọi vị trí. Vuốt thanh trượt để chỉnh Size và Độ Mờ của kính (Opacity) cho mọi widget.
- **Tự Khởi Động (Auto-Start)**: Hỗ trợ nạp Overlay chạy nền tự động ngay khi gắn điện xe.
- **Glassmorphism Design chuẩn**: Toàn bộ UI widget được thiết kế với Background trong suốt, viền mượt, cùng bộ icon Monotone (đơn sắc Trắng) Flat tuyệt đẹp và phù hợp với nội thất bóng bẩy của các dòng xe hiện đại.

## 🛠 Cách Cài Đặt và Cấp Quyền
1. Cài đặt file APK bình thường qua USB hoặc ADB.
2. Mở App **Car Overlay**. Ở lần vào đầu tiên, App sẽ dẫn bạn tới "Settings > Cấp quyền Vẽ Hiển Thị Trên Ứng Dụng Khác (Draw Over Other Apps / System Alert Window)". Gạt sang On \(`Cho Phép`\).
3. Quay lại, Overlay sẽ lập tức hiển thị trên cùng.

## ⚙️ Cấu Hình Widget Nổi

- **Click phần Đồng hồ (Trái)**: Mở tính năng chia đôi màn hình.
- **Click phần Icon Trạng thái (Phải)**: Mở Trang Cài Đặt Ứng Dụng (Settings của Car Overlay).
- **Click Botton Trợ Lý**: Mở app Trợ lý (cần chọn trong Settings).
- **Kéo thả**: Chạm giữ vào bất kì đâu trên thanh Widget hoặc nút Trợ lý để tự do di chuyển nó khắp màn hình xe.

## 💻 Tech Stack & Kiến Trúc
- Ngôn ngữ: Kotlin
- UI Framework: Jetpack Compose
- Target SDK: 34 (Android 14)
- Persistent Storage: WebDataStore Preferences
- Công nghệ Background: `Foreground Service`, `BroadcastReceiver` (cho Boot start), `WindowManager` (cho Window Tokens/Overlay).

## 💡 Notes Dành Cho Dev
- Project không còn là một Launcher. Không có Intent Filter `HOME`.
- Bóng mờ của Compose (`Modifier.shadow()`) có thể gây rách hình trên một số chip đồ họa yếu trên xe, nên app đang dùng `Modifier.border()` để render các khung cảnh Flat.
- Không phải mọi màn hình Android đều ưu tiên flag SplitScreenActivity theo chuẩn, vì vậy `SplitScreenLauncher.kt` sử dụng kĩ thuật Java Reflection đâm thẳng vào `ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)` (3 Primary & 4 Secondary).

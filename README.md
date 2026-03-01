# CarFloat (Formerly Car Launcher Overlay) - v1.3.5

CarFloat là một ứng dụng tiện ích chuyên dụng dành cho màn hình xe hơi (Android Automotive / Android Box) với giao diện **Glassmorphism** hiện đại. Ứng dụng cung cấp các Widget nổi (Floating Widgets) thông minh, cho phép điều khiển và truy cập nhanh mà không cần thoát ứng dụng đang chạy.

---

## 🌟 Có gì mới trong bản v1.3.5?

Bản cập nhật v1.3.5 mang đến những cải tiến mạnh mẽ về khả năng nhận diện trạng thái và tùy biến nút bấm thông minh:

### 📡 3-Tier Connectivity States (Nhận diện kết nối 3 cấp độ)
- **Hệ thống màu thông minh**: 
    - **Màu Xám mờ**: Đang Tắt (Off).
    - **Màu Trắng**: Đang Bật nhưng chưa kết nối (Enabled, Disconnected).
    - **Màu Xanh Cyan**: Đã kết nối thành công (Connected).
- **Độ chính xác cao**: Sử dụng `ConnectivityManager` và `AudioManager` fallback để nhận diện chính xác trạng thái Wifi/Bluetooth ngay cả trên Emulator hoặc thiết bị thiếu quyền Location.

### 🏠 Phím Hệ Thống & Assistant Mapping
- **Gán phím Home**: Cho phép gán chức năng **Trở về màn hình chính (Home)** vào nút Assistant (hành động Chạm, Nhấn giữ, hoặc Chạm đúp). 
- **Không yêu cầu Trợ năng**: Tối ưu hóa lệnh để không cần quyền Accessibility Service phức tạp.
- **Icon Pack**: Sửa lỗi hiển thị và cập nhật phím Chat theo chuẩn Material 3 mới nhất.

### 🎮 Thao Tác & Gestures (v1.3.4)
- **Chế Độ Click-Through (Chạm Xuyên Thấu)**: Widget đồng hồ có thể trở nên tàng hình với cảm ứng, cho phép bạn thao tác với các ứng dụng bên dưới nó. 
- **Nút Voice Đa Năng**: Hỗ trợ 3 loại hành động (Tap, Long Press, Double Tap) riêng biệt.

### 🛠 Tính Năng Hệ Thống
- **Ghi Nhớ Vị Trí**: Tự động lưu và khôi phục vị trí widget.
- **Cập Nhật OTA**: Tích hợp hệ thống tự động kiểm tra và tải bản cập nhật từ GitHub.

### 📖 Hỗ Trợ & Bản Bản Địa Hóa
- **8 Ngôn Ngữ**: Hỗ trợ hoàn hảo Tiếng Anh, Tiếng Việt, Trung Quốc, Nhật Bản, Hàn Quốc, Pháp, Đức, và Ý.

---

## 🚀 Hệ Thống Cập Nhật OTA (GitHub Releases)

CarFloat hỗ trợ cập nhật từ xa mà không cần CH Play. Để cài đặt hệ thống này cho Repo của bạn, hãy xem hướng dẫn chi tiết tại:
👉 [Hướng dẫn cấu hình OTA](brain/3bc9daad-ef9b-40e4-91fb-255d577e3e92/ota_setup_guide.md)

**Cách hoạt động:**
1. App kiểm tra file `version.json` trên nhánh main của GitHub.
2. Nếu bản trên Web có `versionCode` cao hơn bản hiện tại, một **Badge thông báo** sẽ xuất hiện gọn gàng trong màn hình Cài đặt (Settings), thay vì liên tục hiện Popup làm phiền người dùng.
3. Người dùng có thể nhấn **Update Now** trong mục *About* để cài đặt.
4. App được tối ưu để lưu file vào bộ nhớ riêng tư (`getExternalFilesDir`) để tránh lỗi Permission Denied trên các màn hình ô tô cũ, đồng thời chủ động xin quyền "Cài đặt ứng dụng không rõ nguồn gốc".

---

## 🛠 Cách Cài Đặt và Cấp Quyền

1. Cài đặt file APK `CarFloat_v1.3.apk`.
2. Cấp quyền **"Hiển thị trên ứng dụng khác" (System Alert Window)**.
3. (Tùy chọn) Cấp quyền **"Cài đặt ứng dụng không rõ nguồn gốc"** để tính năng OTA hoạt động mượt mà.

---

## 💻 Dành Cho Nhà Phát Triển

### Build & Test
- Dự án sử dụng **Jetpack Compose** cho UI và **DataStore** cho lưu trữ.
- **Automation Script**: Sử dụng `./run_test.sh` để:
    - Đóng các Emulator đang kẹt.
    - Khởi động máy ảo.
    - Tự động build, install và cấp quyền Overlay qua ADB.

### Cấu trúc dự án
- `com.carlauncher.service.OverlayService`: Trái tim của ứng dụng, quản lý WindowManager và các Overlay.
- `com.carlauncher.update.OtaUpdateManager`: Quản lý logic tải và cài đặt APK.
- `res/values-*/strings.xml`: Quản lý bản dịch cho 8 ngôn ngữ.

---

## 📝 Changelog
Chi tiết các thay đổi qua từng phiên bản có thể xem tại [CHANGELOG.md](CHANGELOG.md).

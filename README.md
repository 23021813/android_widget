# CarFloat (Formerly Car Launcher Overlay) - v1.3.0

CarFloat là một ứng dụng tiện ích chuyên dụng dành cho màn hình xe hơi (Android Automotive / Android Box) với giao diện **Glassmorphism** hiện đại. Ứng dụng cung cấp các Widget nổi (Floating Widgets) thông minh, cho phép điều khiển và truy cập nhanh mà không cần thoát ứng dụng đang chạy.

---

## 🌟 Có gì mới trong bản v1.3.0?

Bản cập nhật v1.3.0 mang đến những cải tiến mạnh mẽ về khả năng tùy biến và tính ổn định:

### 🎮 Thao Tác & Gestures Nâng Cao
- **Chế Độ Click-Through (Chạm Xuyên Thấu)**: Widget đồng hồ có thể trở nên tàng hình với cảm ứng, cho phép bạn thao tác với các ứng dụng bên dưới nó. 
    - **Cơ chế kéo thả mới**: Để di chuyển widget trong chế độ này, bạn cần **Nhấn Giữ (Long Press)** vào chấm nhỏ (12dp, opacity 15%) ở góc trước khi kéo.
- **Nút Voice Đa Năng**: 
    - Hỗ trợ gán 3 hành động khác nhau: **Chạm (Tap)**, **Nhấn Giữ (Long Press)**, và **Chạm Đúp (Double Tap)** cho các ứng dụng khác nhau.
    - Cho phép thay đổi Icon Mic từ danh sách Icon Pack tích hợp sẵn.
- **Kích Thước Độc Lập**: Tùy chỉnh tỉ lệ (Scale) riêng biệt cho Widget Trạng thái và Nút Voice.

### 🛠 Tính Năng Hệ Thống
- **Ghi Nhớ Vị Trí**: Tự động lưu và khôi phục vị trí chính xác của các widget sau khi khởi động lại.
- **Ghi Đè Thanh Hệ Thống (Layout No Limits)**: Cho phép kéo widget đè lên cả thanh thông báo (Notification Bar) và thanh điều hướng (Navigation Bar).
- **Tự Động Chia Màn Hình (Auto Split-View)**: Tùy chọn tự động kích hoạt chế độ chia đôi màn hình ngay khi hệ thống khởi động hoàn tất (Yêu cầu đã cấu hình 2 App).
- **Cập Nhật OTA**: Tích hợp hệ thống tự động kiểm tra và tải bản cập nhật từ GitHub.

### 📖 Hỗ Trợ & Bản Địa Hóa
- **Help Center**: Mục hướng dẫn trực quan trong Settings giải thích chi tiết các cử chỉ Tap/Long Press/Double Tap.
- **Internationalization (8 Ngôn Ngữ)**: Hỗ trợ hoàn hảo Tiếng Anh, Tiếng Việt, Trung Quốc, Nhật Bản, Hàn Quốc, Pháp, Đức, và Ý. Tự động nhận diện theo hệ thống hoặc chọn thủ công.

---

## 🚀 Hệ Thống Cập Nhật OTA (GitHub Releases)

CarFloat hỗ trợ cập nhật từ xa mà không cần CH Play. Để cài đặt hệ thống này cho Repo của bạn, hãy xem hướng dẫn chi tiết tại:
👉 [Hướng dẫn cấu hình OTA](brain/3bc9daad-ef9b-40e4-91fb-255d577e3e92/ota_setup_guide.md)

**Cách hoạt động:**
1. App kiểm tra file `version.json` trên nhánh main của GitHub.
2. Nếu bản trên Web có `versionCode` cao hơn bản hiện tại, thông báo cập nhật sẽ hiện ra.
3. Người dùng có thể nhấn **Update Now** trong mục *About* để kiểm tra thủ công.

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

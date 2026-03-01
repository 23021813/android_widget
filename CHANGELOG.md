# Changelog

Tất cả các thay đổi đáng chú ý đối với dự án CarFloat sẽ được ghi lại trong tệp này.

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

# Thông tin mặc định cho lịch khởi hành

Admin dùng `PUT /api/v1/tours/{tourId}/schedule-defaults` để lưu số chỗ, giá, phụ phí, tỷ lệ cọc, điểm tập trung và số liên hệ khi chưa chốt ngày. `GET` trả 204 khi chưa có thông tin. Đây không phải lịch khởi hành, không tạo chỗ hoặc cho phép booking. Khi tạo lịch thật, frontend ưu tiên thông tin từ lịch sửa gần nhất; nếu chưa có lịch, dùng thông tin mặc định. Cả hai API yêu cầu quyền quản lý lịch.

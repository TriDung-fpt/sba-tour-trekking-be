# Chọn thiết bị khi đặt tour

Khách lấy các lựa chọn đang được tour cung cấp từ `GET /api/v1/tours/{tourId}/equipment`. Danh sách `packages` chứa combo, `individualItems` chứa món thuê lẻ; id của từng phần tử được gửi lại trong request booking. Các gói cần admin rà soát không xuất hiện ở API công khai và không thể được đặt.

`POST /api/v1/bookings` nhận số lượng theo ID cấu hình:

```json
{
  "idempotencyKey": "checkout-9a123",
  "scheduleId": 42,
  "equipmentChoice": "TOUR_PACKAGE",
  "packageQuantities": {"12": 1, "18": 2},
  "itemQuantities": {"89": 1},
  "passengers": [
    {"fullName": "Nguyen Van A", "bookingCustomer": true},
    {"fullName": "Tran Thi B"}
  ]
}
```

Nếu khách tự chuẩn bị, gửi `equipmentChoice: "SELF_PROVIDED"` và bỏ hai map lựa chọn hoặc gửi map rỗng. Số lượng phải là số nguyên dương; giá luôn được tra từ cấu hình tour ở backend.

Tổng mới được tính theo `(giá tour/người × số khách) + phí combo + phí món lẻ`. Phí thuê thiết bị tính một lần cho booking. Ví dụ hai khách giá 1.000.000 VND/người, combo 300.000 VND và món lẻ 70.000 VND có tổng 2.370.000 VND. Tiền cọc dùng tỷ lệ của lịch khởi hành và làm tròn lên VND, giống response booking và điều kiện xác nhận thanh toán.

Tạo booking, giữ ghế và giữ kho nằm trong cùng transaction. Nếu thiếu kho, API trả lỗi `EQUIPMENT_UNAVAILABLE` cùng tên món để khách điều chỉnh. Gửi lại cùng idempotency key và cùng nội dung trả booking đã tạo; cùng key nhưng payload khác trả `IDEMPOTENCY_KEY_REUSED`.

Migration `022_booking_equipment_pricing.sql` bỏ cột phiên bản giá và áp dụng công thức chung cho booking: giá tour theo số khách cộng phí thiết bị một lần cho toàn booking.

Mỗi khách chỉ được có một hold `PENDING_PAYMENT` còn hạn tại một thời điểm. FE có thể kiểm tra trước qua `GET /api/v1/bookings/my/active-holds` (trả danh sách `BookingSummaryResponse`, rỗng nếu không có hold). Nếu `POST /api/v1/bookings` gặp hold đang hiệu lực, API trả `409` với `code: ACTIVE_BOOKING_HOLD_EXISTS` và `activeHolds` chứa các booking cần tiếp tục thanh toán. Việc kiểm tra được khóa theo khách hàng để ngăn request đồng thời từ nhiều tab tạo nhiều hold.

## Guide giao và thu hồi thiết bị

Guide được phân công đúng lịch đọc thiết bị theo từng booking bằng `GET /api/v1/bookings/{bookingId}/equipment/reservation`. Khi bàn giao, guide gọi `POST /api/v1/bookings/{bookingId}/equipment/issue` với số lượng thực giao. Cuối hành trình, guide gọi `POST /api/v1/bookings/{bookingId}/equipment/return` để ghi nhận số lượng thực thu hồi và tình trạng của từng món, ví dụ:

```json
{
  "note": "Đã kiểm kê cuối chuyến",
  "items": [
    {"equipmentId": 89, "quantity": 1, "condition": "GOOD"},
    {"equipmentId": 90, "quantity": 1, "condition": "NEEDS_INSPECTION"}
  ]
}
```

Mỗi lần trả ghi lại người thao tác, thời gian và số lượng; có thể thu hồi nhiều lần. Khi còn thiết bị chưa trả, trạng thái là `return_required`; khi số lượng đã giao được trả đủ, trạng thái là `returned`. Thiết bị `NEEDS_INSPECTION` được cộng vào số lượng cần bảo trì. Hệ thống từ chối số lượng trả vượt số đã giao. Admin có quyền quản lý tour vẫn có thể thực hiện thao tác này.

Việc trả là bước vận hành cuối hành trình; API hiện chưa kiểm tra chuyến đã kết thúc vì chưa có thao tác kết thúc chuyến trong backend. Không dùng giờ kết thúc dự kiến làm điều kiện khóa vì chuyến có thể kết thúc sớm hoặc muộn.

# Cấu hình booking

Các giá trị dưới đây được đọc qua `BookingProperties` (`app.booking`), có thể ghi đè trong `.env` hoặc biến môi trường. Khởi động lại ứng dụng sau khi đổi cấu hình.

| Biến môi trường | Property | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `BOOKING_MAX_PAGE_SIZE` | `app.booking.max-page-size` | `100` | Số booking tối đa mỗi trang; phải lớn hơn 0. |
| `BOOKING_CODE_ALPHABET` | `app.booking.code-alphabet` | `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` | Tập ký tự sinh mã, gồm chữ hoa A–Z và chữ số 0–9; không được rỗng. |
| `BOOKING_CODE_LENGTH` | `app.booking.code-length` | `10` | Độ dài phần mã ngẫu nhiên sau tiền tố `BK`, từ 1 đến 38 ký tự. |

Giá trị mặc định nằm trong `application.properties`, không nằm trong service. Các booking đã tạo giữ nguyên mã khi cấu hình thay đổi.

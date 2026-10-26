# PRD - Phan Admin (trich tu PRD V3.4). Stack: Spring Boot, PostgreSQL, Redis, JWT
 | RV-01 | Mỗi booking tối đa một đánh giá tour; người gửi phải là hành khách đã check-in và chuyến đã hoàn thành.

 | RV-02 | Staff kiểm duyệt nội dung vi phạm, lưu lý do; không xóa đánh giá tiêu cực chỉ vì điểm thấp.
# 18. Bổ sung V3.3 -- Admin Management & Review

Mục tiêu: tách rõ Admin Management thành module riêng; Member 5 chịu trách nhiệm Admin & Review, Member 4 tiếp nhận toàn bộ EQ-01 đến EQ-08 cùng Payment & Refund. Admin giám sát, cấu hình và phân quyền; các module nghiệp vụ giữ API do thành viên tương ứng phụ trách.

## 18.1. Luồng Admin Management

- | **Admin User & Role Management:** Admin đăng nhập → danh sách nhân viên/guide → tạo/sửa/khóa tài khoản → gán role/permission → lưu audit log. Customer tự đăng ký; Admin không chỉnh sửa mật khẩu trực tiếp.

- | **Admin Tour & Schedule Oversight:** Admin xem tất cả tour/lịch → xem chi tiết, kiểm tra trạng thái → chỉnh sửa hoặc ẩn tour theo quyền → hệ thống ghi lịch sử. API CRUD Tour/Schedule do Member 2 sở hữu.

- | **Admin Equipment & Pricing Oversight:** Admin xem Equipment/Package, tồn khả dụng, giá thuê → cấu hình/duyệt thay đổi theo quyền → xem các reservation/cấp phát/trả. API EQ-01 đến EQ-08 do Member 4 sở hữu.

- | **Admin Booking & Payment Monitoring:** Admin xem booking, giao dịch và trạng thái hoàn tiền → lọc giao dịch lỗi/ngoại lệ → chuyển người có quyền tài chính xử lý; không tự đánh dấu thanh toán thành công nếu thiếu xác minh.

- | **Admin Policy & System Settings:** Admin cập nhật chính sách hủy, tỷ lệ cọc, thời gian giữ chỗ, thông báo và cấu hình AI → lưu phiên bản, thời gian áp dụng, người sửa; booking đã tạo giữ snapshot cũ.

- | **Admin Dashboard & Reports:** Admin chọn thời gian → xem tổng booking, doanh thu thực thu, hoàn tiền, hủy, tour phổ biến, tỷ lệ sử dụng gói dụng cụ; có phân quyền và bộ lọc.

- | **Admin Review Moderation:** Admin xem đánh giá → lọc/báo cáo nội dung vi phạm → ẩn/khôi phục có lý do và audit log; không thay đổi rating gốc hoặc xóa vì đánh giá tiêu cực.

## 18.2. Yêu cầu chức năng Admin (AD-01 đến AD-10)

 | Mã | Yêu cầu
 | AD-01 | Quản lý tài khoản Staff, Accountant, Tour Guide: tạo, cập nhật, khóa/mở; xem nhật ký thay đổi.

 | AD-02 | Quản lý role/permission; mọi API kiểm tra RBAC và ownership; ngăn tự cấp quyền vượt mức.

 | AD-03 | Giám sát tour/lịch, ẩn hoặc ngừng bán tour; tái sử dụng API Tour/Schedule.

 | AD-04 | Giám sát Equipment/Package, số lượng và bảng giá; tái sử dụng API EQ do Member 4 phát triển.

 | AD-05 | Tra cứu mọi booking, trạng thái giữ chỗ, hủy, check-in; xử lý ngoại lệ có audit.

 | AD-06 | Theo dõi thanh toán và hoàn tiền; chỉ người được cấp quyền tài chính mới xác nhận giao dịch.

 | AD-07 | Quản lý chính sách hủy/hoàn, tỷ lệ cọc, thời hạn thanh toán có version/effective date.

 | AD-08 | Dashboard và báo cáo: booking, tiền thực thu, tiền hoàn, tour, khách, tỷ lệ thuê gói.

 | AD-09 | Quản lý Review và giám sát SupportTicket; ẩn/khôi phục đánh giá với lý do.

 | AD-10 | Cấu hình hệ thống: hold timeout, thông báo, AI; kiểm soát quyền và ghi lịch sử.

## 18.3. Review Management -- quy trình chi tiết

- | Customer chỉ được gửi review khi booking COMPLETED và chính Customer có attendance hợp lệ; mỗi booking tối đa một review.

- | Admin/Staff được cấp quyền xem danh sách review, nội dung báo cáo và trạng thái hiển thị.

- | Admin ẩn hoặc khôi phục review vi phạm có lý do; rating gốc và lịch sử thao tác được bảo toàn.

- | Khách không được tạo review mới để vượt giới hạn một review/booking.

## 18.4. Business Rules bổ sung

 | Mã | Quy tắc
 | BR-A01 | Admin có quyền giám sát toàn hệ thống nhưng quyền thực thi thao tác tài chính phải được phân quyền rõ.

 | BR-A02 | Thay đổi giá tour/gói và chính sách không hồi tố booking đã chốt; dùng snapshot.

 | BR-A03 | Tất cả thay đổi role, giá, chính sách, trạng thái review và booking ngoại lệ phải có audit log.

 | BR-A04 | Không xóa cứng nhân viên hoặc dụng cụ đã phát sinh giao dịch; ưu tiên vô hiệu hóa.

 | BR-A05 | Admin Review không được sửa điểm đánh giá gốc hoặc xóa đánh giá chỉ vì tiêu cực.

 | BR-A06 | Module Admin dùng lại service/API nghiệp vụ; không xây thêm quy trình thanh toán, đặt tour hay quản lý kho song song.

## 18.5. Acceptance Criteria bổ sung

 | Mã | Kết quả mong đợi
 | AC-A01 | Admin tạo Staff và gán role; Staff không thể truy cập màn hình/phương thức chỉ dành cho Admin.

 | AC-A02 | Thay đổi giá gói từ 200.000đ lên 250.000đ không đổi tổng tiền booking cũ.

 | AC-A03 | Admin xem giao dịch lỗi nhưng không thể ghi nhận đã thu tiền nếu thiếu quyền tài chính.

 | AC-A04 | Admin cập nhật chính sách hủy; booking cũ vẫn tính theo snapshot trước đó.

 | AC-A05 | Review chỉ xuất hiện khi khách đã check-in và tour hoàn thành; mỗi booking tối đa một review.

 | AC-A06 | Admin ẩn review vi phạm có lý do; review/rating gốc vẫn còn và có audit log.

# 20. V3.3 -- Admin quản lý toàn bộ hệ thống

Admin là vai trò quản trị cao nhất về phạm vi giám sát và cấu hình hệ thống của một doanh nghiệp tổ chức tour trekking/camping. Admin được xem dữ liệu xuyên suốt từ tài khoản, tour, lịch, booking, gói dụng cụ, check-in, giao dịch, hoàn tiền, đánh giá, hỗ trợ đến báo cáo. Các thao tác ghi nhận thu tiền/hoàn tiền vẫn phải tuân theo permission tài chính và kiểm soát chứng từ; quyền giám sát không đồng nghĩa được tự ý thay đổi kết quả giao dịch.

## 20.1. Admin Dashboard -- màn hình tổng quan

Luồng: Admin Login → Admin Dashboard → chọn khoảng thời gian (tháng/quý/năm hoặc tùy chỉnh) → xem KPI/biểu đồ → nhấn chỉ số để mở danh sách chi tiết → lọc/xuất báo cáo.

 | Nhóm KPI | Thông tin hiển thị
 | Booking | Tổng đơn; đơn đã xác nhận; đang chờ thanh toán; hủy; hết hạn; hoàn thành.

 | Doanh thu | Tổng tiền thu thực tế; tiền hoàn thành công; doanh thu thuần = thu thực tế -- hoàn thành công.

 | Giao dịch | Số giao dịch thành công/thất bại/đang xử lý; tổng giá trị theo phương thức và trạng thái.

 | Tour | Tour/lịch đang mở bán; số lượt khách; tour được đặt nhiều nhất; tỷ lệ lấp đầy.

 | Equipment | Số booking thuê gói; doanh thu gói thuê; tỷ lệ chọn gói; số lượng đang cấp phát.

 | Khách hàng & vận hành | Khách mới, khách quay lại (nếu đủ dữ liệu); check-in, no-show; ticket đang mở; review cần kiểm duyệt.

## 20.2. Revenue Reports -- tháng, quý, năm

Luồng: Admin → Reports → Revenue Report → chọn Monthly / Quarterly / Yearly → chọn kỳ → hệ thống tổng hợp từ giao dịch hợp lệ → hiển thị KPI, biểu đồ và bảng chi tiết → lọc theo tour/lịch/phương thức → xuất CSV hoặc Excel.

 | Mã | Yêu cầu
 | RP-02 | Báo cáo doanh thu theo THÁNG: chọn tháng/năm; hiển thị tổng tiền thu, hoàn thành công, doanh thu thuần; biểu đồ theo ngày.

 | RP-03 | Báo cáo doanh thu theo QUÝ: Q1 (01--03), Q2 (04--06), Q3 (07--09), Q4 (10--12); biểu đồ theo tháng trong quý.

 | RP-04 | Báo cáo doanh thu theo NĂM: chọn năm; tổng hợp 12 tháng và so sánh với năm trước nếu có dữ liệu.

 | RP-05 | Phân tách tiền tour và tiền thuê gói theo snapshot từng booking; thể hiện doanh thu theo tour, lịch và nhóm sản phẩm.

 | RP-06 | Bộ lọc: thời gian, tour, lịch khởi hành, phương thức thanh toán, trạng thái giao dịch; xem chi tiết booking liên quan.

 | RP-07 | Xuất báo cáo với cùng bộ lọc và số liệu đang xem; chỉ người có quyền mới được xuất.

## 20.3. Transaction Reports -- tháng, quý, năm

Luồng: Admin → Transactions → chọn Monthly / Quarterly / Yearly → xem danh sách và tổng hợp → lọc trạng thái/payment method → mở giao dịch để xem booking, mã cổng thanh toán, thời điểm và số tiền → xuất báo cáo.

 | Mã | Yêu cầu
 | TR-01 | Tra cứu mọi payment attempt, payment transaction và refund transaction trên toàn hệ thống.

 | TR-02 | Thống kê số lượng và tổng tiền giao dịch theo tháng, quý, năm; phân nhóm SUCCESS, FAILED, PENDING và REFUNDED.

 | TR-03 | Lọc theo mã giao dịch, mã booking, khách hàng, tour, cổng thanh toán, ngày tạo, ngày thanh toán thành công.

 | TR-04 | Chi tiết giao dịch gồm transaction ID, booking ID, amount, type (DEPOSIT/BALANCE/FULL/REFUND), method, status, gateway reference, timestamps.

 | TR-05 | Báo cáo thu và hoàn phải thể hiện riêng; không cộng payment attempt thất bại vào doanh thu.

 | TR-06 | Export CSV/Excel, kiểm soát quyền xem dữ liệu cá nhân và audit log thao tác xuất.

## 20.4. Các khu vực quản trị toàn hệ thống

 | Menu Admin | Phạm vi quản lý
 | Dashboard | KPI, biểu đồ và drill-down theo tháng/quý/năm.

 | Users & Roles | Tạo/khóa nhân viên, guide, phân quyền; xem khách hàng và trạng thái tài khoản.

 | Tours & Schedules | Toàn bộ tour, lịch, hướng dẫn viên; tạo/sửa/ẩn và theo dõi hoạt động.

 | Equipment & Packages | Danh mục dụng cụ, gói thuê, giá, tồn khả dụng, reservation và bàn giao/trả.

 | Bookings & Attendance | Mọi booking, hành khách, check-in, hoàn thành, hủy và ngoại lệ.

 | Payments & Refunds | Theo dõi tất cả giao dịch, yêu cầu hoàn, đối soát; hành động tài chính theo permission.

 | Revenue & Transaction Reports | Báo cáo doanh thu và giao dịch theo tháng, quý, năm, lọc và xuất.

 | Reviews & Support | Kiểm duyệt đánh giá, giám sát ticket và xử lý vi phạm.

 | Policies & Settings | Chính sách hủy, cọc, hold timeout, cấu hình hệ thống/AI và audit log.

## 20.5. Business Rules cho báo cáo Admin

 | Mã | Quy tắc
 | BR-R01 | Mặc định kỳ báo cáo tính theo múi giờ Asia/Ho_Chi_Minh; biên kỳ \[start, end) để không đếm trùng.

 | BR-R02 | Doanh thu tiền mặt (cash collected) chỉ tính các khoản thu đã xác nhận thành công trong kỳ; không tính booking chưa thu hoặc payment attempt thất bại.

 | BR-R03 | Tiền hoàn chỉ trừ khi refund thành công; yêu cầu hoàn đang chờ không trừ khỏi số thu thực tế.

 | BR-R04 | Doanh thu thuần kỳ = tổng thu thành công trong kỳ -- tổng hoàn thành công trong kỳ. Đây là chỉ số dòng tiền thuần, không mặc định là doanh thu kế toán được ghi nhận.

 | BR-R05 | Một giao dịch chỉ được tính một lần bằng transaction ID duy nhất; webhook lặp không làm tăng số liệu.

 | BR-R06 | Tiền tour và tiền thuê gói được phân bổ theo giá snapshot và số tiền thực thu; cách phân bổ cọc/hoàn theo thành phần phải thống nhất trong triển khai.

 | BR-R07 | Admin xem toàn hệ thống; chỉ role/permission được cấp mới có thể thực hiện thao tác nhạy cảm như xác nhận tiền hoặc hoàn tiền.

 | BR-R08 | Báo cáo tháng/quý/năm phải khớp khi cộng các kỳ nhỏ tương ứng, nếu dùng cùng cơ sở ngày giao dịch và bộ lọc.

## 20.6. Acceptance Criteria cho Dashboard & Reports

 | Mã | Kịch bản nghiệm thu
 | AC-R01 | Chọn tháng 03/2027: chỉ giao dịch thu/hoàn thành công có thời điểm nằm trong tháng 03 theo giờ Việt Nam được tổng hợp.

 | AC-R02 | Chọn Q2/2027: tổng hợp tháng 04, 05, 06; tổng quý bằng tổng ba tháng cùng bộ lọc.

 | AC-R03 | Chọn năm 2027: tổng năm bằng tổng 12 tháng; biểu đồ thể hiện đủ 12 tháng kể cả tháng không có giao dịch.

 | AC-R04 | Payment webhook bị gửi 2 lần: doanh thu và số lượng giao dịch thành công chỉ tăng một lần.

 | AC-R05 | Một refund PENDING không giảm doanh thu thuần; khi refund COMPLETED mới được tính vào kỳ hoàn thành.

 | AC-R06 | Admin lọc theo tour và xuất Excel/CSV: số liệu file khớp bảng trên màn hình.

 | AC-R07 | Staff không có quyền báo cáo toàn hệ thống không xem hoặc xuất được dữ liệu toàn hệ thống.

 | AC-R08 | Admin mở giao dịch từ biểu đồ sẽ thấy booking, loại thanh toán, mã tham chiếu và trạng thái tương ứng.

## 20.7. Trách nhiệm triển khai Member 5

- | Xây dựng Admin Dashboard: KPI cards, biểu đồ doanh thu/giao dịch, thống kê booking, tour, equipment.

- | Xây dựng Reports UI/API tổng hợp theo tháng, quý, năm; lọc, drill-down và xuất CSV/Excel.

- | Xây dựng màn hình quản trị toàn hệ thống, tích hợp các API của Member 1--4 thay vì viết lại logic nghiệp vụ.

- | Xây dựng Review Management: xem, lọc, ẩn/khôi phục review vi phạm, lưu lý do và audit log.

- | Phối hợp Member 4 về định nghĩa giao dịch thành công, refund và cách phân bổ tiền tour/gói; phối hợp Member 1 về phân quyền.

Ghi chú scope: Đây là phần mở rộng yêu cầu Admin theo quyết định mới của nhóm. Các chỉ số "doanh thu" trong dashboard được xác định theo tiền thực thu trừ tiền hoàn, không tự suy diễn thành lợi nhuận vì hệ thống chưa có module chi phí vận hành.

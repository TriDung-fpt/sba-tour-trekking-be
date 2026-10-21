# Phân công công việc cho 6 thành viên – Sàn kết nối Tour Trekking & Camping

**Căn cứ:** PRD V2.0 – Sàn kết nối Tour Trekking và Camping (03/10/2026).  
**Nguyên tắc:** Phân theo module nghiệp vụ, mỗi người phụ trách Backend API, Frontend và kiểm thử phần được giao. Ưu tiên các yêu cầu **Bắt buộc**; phần **Mở rộng** triển khai sau. Đây là **đề xuất phân công**, không phải phân công có sẵn trong PRD.

## 1. Tổng quan

| Thành viên | Module chính | Độ khó ước tính | Khối lượng ước tính |
|---|---|---|---|
| Member 1 | Authentication, Account & Authorization | Khá | 15% |
| Member 2 | Partner, Tour & Schedule Management | Cao | 18% |
| Member 3 | Customer, Booking & Attendance | Rất cao | 20% |
| Member 4 | Payment, Refund & Settlement | Rất cao | 22% |
| Member 5 | Review, Support & Admin | Cao | 15% |
| Member 6 | AI Assistant, RAG & Integration | Cao | 10% |

> Tỷ lệ trên là ước lượng công sức để lập kế hoạch, không phải số liệu trong PRD. Module thanh toán/đối soát có rủi ro lớn nên các thành viên khác cần hỗ trợ khi tích hợp.

## 2. Phân công chi tiết

### Member 1 – Authentication & Account

| Mã | Nội dung |
|---|---|
| AU-01 | Đăng ký, đăng nhập, xác thực email, lấy lại mật khẩu; kiểm quyền thanh toán/đánh giá |
| AU-02 | Quản lý phiên, JWT/refresh, đăng xuất, phân quyền, khóa tài khoản có lý do |
| AU-03 | Hồ sơ và trạng thái duyệt đối tác |
| AU-04 | Chấp nhận điều khoản bằng mã email; lưu phiên bản, thời điểm, địa chỉ mạng, chính sách |
| AU-05 | Tạo tài khoản hướng dẫn viên/nhân viên; đổi mật khẩu lần đầu và giới hạn phạm vi |

**Phụ trách thêm:** Spring Security, role/permission cho 7 vai trò, kiểm tra quyền sở hữu dữ liệu và hỗ trợ phân quyền kế toán.  
**Đầu ra:** Hệ thống tài khoản và quyền truy cập dùng chung cho toàn nền tảng.

### Member 2 – Partner & Tour Management

| Mã | Nội dung |
|---|---|
| PA-01 | CRUD tour: mô tả, ảnh, độ khó, lộ trình GeoJSON/GPX, dịch vụ, đồ cần mang, chính sách hủy |
| PA-02 | Quy trình nháp → chờ duyệt → công khai/từ chối; duyệt lại thay đổi giá/chính sách |
| PA-03 | Tạo lịch khởi hành, số chỗ, giá cơ bản, phụ thu dụng cụ, tỷ lệ cọc, hạn trả đủ, hướng dẫn viên |
| PA-04 | Xem đơn/hành khách theo lịch và xuất Excel đúng quyền |
| PA-10 | Gói dụng cụ đi kèm và phụ thu theo người/chuyến, không quản lý tồn kho |
| PA-11 | Xác nhận/hủy lịch, thông tin liên hệ vận hành, xác nhận kết thúc theo quyền |
| CU-01 | Tìm/lọc/sắp xếp tour theo địa điểm, độ khó, ngày, giá, xếp hạng và lựa chọn dụng cụ |
| CU-02 | Trang chi tiết tour, lịch, chỗ còn, hai mức giá, chính sách, đánh giá và đối tác |

**Đầu ra:** Đối tác có thể đăng tour, lịch và mở bán sau khi được duyệt; khách xem và tìm kiếm tour.

### Member 3 – Booking & Tour Attendance

| Mã | Nội dung |
|---|---|
| CU-03 | Giỏ hàng; kiểm tra lại giá và chỗ khi đặt |
| CU-04 | Đặt tour nhóm, danh sách hành khách, người đặt có đi hay không; giữ chỗ 15 phút |
| CU-08 | Chọn một gói dụng cụ chung cho đơn; tính lại tổng tiền trước khi thanh toán |
| CU-14 | Mã/QR đơn, danh sách khách, trạng thái có mặt, liên hệ sau xác nhận |
| GU-01 | Hướng dẫn viên xem lịch được giao và hành khách |
| GU-02 | Tìm đơn bằng mã/QR, xác nhận có mặt từng hành khách, lưu lịch sử |
| GU-04 | Xác nhận chuyến thực sự kết thúc, ghi người không đến |
| GU-06 | Sửa xác nhận có mặt sai có lý do và lịch sử; chuyển nhân viên xử lý sau hoàn thành |

**Phụ trách thêm:** Trạng thái đơn/chuyến, chống giữ chỗ trùng, chống bán vượt chỗ, nhả chỗ hết hạn, phối hợp Member 4 khi nhận thanh toán.  
**Đầu ra:** Đặt tour nhóm và check-in từng người chính xác, không bán vượt chỗ.

### Member 4 – Payment, Refund & Settlement

| Mã | Nội dung |
|---|---|
| CU-05 | Thanh toán online qua VNPay thử nghiệm/payOS; hiển thị đã trả/còn lại |
| CU-06 | Chuyển khoản thủ công; kế toán đối chiếu và xác nhận |
| CU-07 | Cọc theo tỷ lệ, trả phần còn lại đúng hạn, nhắc email |
| CU-10 | Xem tiền hoàn dự kiến, hủy và theo dõi hoàn tiền |
| PA-08 | Đối tác xem đối soát từng đơn, hoa hồng, điều chỉnh, khiếu nại |
| ST-05 | Kế toán xác nhận tiền, duyệt/ghi hoàn, lập/duyệt đối soát, chứng từ chi |
| AD-02 | Cấu hình hoa hồng, ngoại lệ, 72 giờ, kỳ tuần, giữ chỗ, chính sách có phiên bản |
| AD-03 | Thống kê thu/hoàn, hoa hồng, phí thanh toán, khoản phải trả/đã trả |

**Phụ trách thêm:** Sổ thu chi, chống webhook/giao dịch trùng, thanh toán muộn/sai, điều chỉnh sau chi và kiểm soát khoản bị giữ.  
**Đầu ra:** Luồng thu → hoàn → đối soát → chi đối tác minh bạch, không ghi tiền hoặc chi trùng.

### Member 5 – Review, Support & Administration

| Mã | Nội dung |
|---|---|
| CU-11 | Một bài đánh giá/đơn gồm điểm tour và đối tác; chỉ người thực sự tham gia được gửi |
| CU-13 | Khách gửi yêu cầu hỗ trợ về tiền theo đơn, kèm tài liệu và lịch sử |
| ST-03 | Quản lý trạng thái, phân công, lịch sử yêu cầu tài chính |
| ST-04 | Duyệt đối tác/tour, kiểm duyệt đánh giá, xử lý vi phạm và khóa tài khoản |
| ST-07 | Sửa xác nhận có mặt/hoàn thành có bằng chứng, lưu lý do và chặn chi khi cần |
| AD-01 | Quản lý nhân viên, tách quyền xem/lập/duyệt/chi |

**Phụ trách thêm:** Admin Dashboard, giao diện kiểm duyệt, giao diện nhân viên/kế toán tích hợp API Member 4.  
**Đầu ra:** Quy trình kiểm duyệt, hỗ trợ khách và đánh giá có xác minh.

### Member 6 – AI Assistant & RAG

| Mã | Nội dung |
|---|---|
| ST-01 | Tiếp nhận hội thoại AI chuyển nhân viên, xem tóm tắt và đơn theo quyền, chuyển kế toán |
| ST-02 | Tải PDF/DOCX/TXT vào kho tài liệu AI, xử lý/cập nhật/xóa dữ liệu, chính sách có phiên bản |
| AD-04 | Cấu hình mức tin cậy, chuyển nhân viên, quyền thao tác, bật/tắt AI và nhật ký |

**Theo mục 6 PRD:** Xây chatbot tra cứu FAQ/chính sách/tour bằng PostgreSQL + pgvector; lấy giá, chỗ, trạng thái đơn/tiền từ API mới nhất; gợi ý tour có lý do; chỉ chuẩn bị giỏ/yêu cầu hỗ trợ khi khách xác nhận; không tự hoàn tiền hoặc duyệt chi; chống vượt quyền và lưu lịch sử.  
**Đầu ra:** AI hỗ trợ theo ngữ cảnh trang/tour/đơn, có nguồn và có khả năng chuyển nhân viên.

## 3. Task mở rộng (sau luồng chính)

| Thành viên | Công việc |
|---|---|
| Member 1 | Tăng cường kiểm thử bảo mật, phân quyền và hỗ trợ tích hợp |
| Member 2 | PA-09 – Báo cáo doanh thu/số đơn/tỷ lệ hủy/đánh giá đối tác |
| Member 3 | CU-15 – So sánh tour |
| Member 4 | Báo cáo đối soát nâng cao và thêm kịch bản kiểm thử tài chính |
| Member 5 | PA-12 – Trả lời đánh giá; CU-12 – Hỏi đáp trước khi mua |
| Member 6 | Tối ưu gợi ý tour và trải nghiệm AI |

## 4. Phối hợp và thứ tự triển khai

1. **Cả nhóm:** Thống nhất ERD, quy ước API, mã lỗi, quy tắc quyền, trạng thái và thời gian UTC/VN.
2. **Member 1:** Hoàn thiện auth/permission sớm; cung cấp cơ chế kiểm quyền dùng chung.
3. **Member 2 + Member 5:** Hoàn thiện quy trình tạo/duyệt đối tác, tour và lịch.
4. **Member 3:** Tích hợp tour/lịch để triển khai đặt chỗ, check-in, kết thúc chuyến.
5. **Member 4:** Phát triển API thanh toán song song bằng hợp đồng API thống nhất; tích hợp booking và completion khi sẵn sàng.
6. **Member 6:** Phát triển pipeline tài liệu và AI song song; kết nối API thực sau khi có dữ liệu.
7. **Cả nhóm:** Kiểm thử liên module, đặc biệt giữ chỗ đồng thời, callback trùng, hoàn tiền, quyền đánh giá và kỳ chi.

> Đây là **thứ tự phụ thuộc tích hợp**, không phải yêu cầu chờ module trước hoàn thành mới bắt đầu lập trình.

## 5. Quy tắc bắt buộc khi triển khai

- Không xây dịch vụ thuê dụng cụ độc lập, kho, giao nhận hoặc cọc đồ.
- Một đơn chỉ đặt **một tour/một lịch/một đối tác**, nhiều hành khách và **một lựa chọn dụng cụ chung**.
- Tính giá, cọc và hoa hồng ở server; lưu snapshot giá/chính sách tại thời điểm đặt.
- Giữ chỗ mặc định 15 phút; chống bán vượt chỗ và chống tạo đơn trùng.
- Chỉ webhook đã xác thực hoặc kế toán đối chiếu thực tế mới xác nhận tiền; không tin trang redirect/ảnh chuyển khoản.
- Mỗi hành khách được xác nhận có mặt riêng; QR chỉ dùng tìm đơn.
- Đánh giá chỉ khi chuyến thực sự hoàn thành và chính tác giả đã được xác nhận có mặt; mỗi đơn tối đa một bài.
- Hoa hồng mặc định 10% trên doanh thu giữ lại, gồm phụ thu dụng cụ; phí cổng thanh toán do nền tảng chịu.
- Đối tác đủ điều kiện xét trả tiền sau **72 giờ kể từ khi chuyến hoàn thành thực tế** và chi theo kỳ tuần; bản đầu chuyển khoản thủ công.
- Không hoàn trùng, ghi tiền trùng hoặc chi trùng; giữ lịch sử điều chỉnh, chứng từ và người thao tác.

## 6. Cách tách task trên Jira/Trello

Với mỗi mã PRD, tạo các subtask khi phù hợp:

- **Database:** entity, quan hệ, constraint, migration.
- **Backend:** API, validation, phân quyền, business rule, xử lý lỗi.
- **Frontend:** màn hình, form, trạng thái tải/lỗi, tích hợp API.
- **Testing:** unit/integration test, tình huống hợp lệ và bất hợp lệ.

**Các luồng nghiệm thu cần ưu tiên:** duyệt đối tác → đăng tour/lịch → đặt nhóm → cọc/trả đủ → xác nhận từng người → hoàn thành chuyến → đánh giá → đối soát và trả đối tác; cùng các tình huống đặt đồng thời, callback trùng/sai, thanh toán muộn và hoàn tiền.

---

*Tài liệu tham chiếu: PRD V2.0, mục 4 (yêu cầu chức năng), mục 5 (quy tắc nghiệp vụ), mục 6 (AI), mục 7–9 (phi chức năng, dữ liệu, nghiệm thu).*

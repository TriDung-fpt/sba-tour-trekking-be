# Kịch bản test API bằng Apidog

[Tài liệu API tiếng Việt](../../API_DOCS_VI.md) · [Danh sách từng request và kết quả kỳ vọng](CASE_INDEX.md)

Bộ test dành cho backend Docker local, bao phủ 41 thao tác API hiện có cùng các tình huống thành công, validation, xác thực, phân quyền và chuyển trạng thái. Mỗi request có URL, JSON body và script kiểm tra kết quả trong [collection](Trekking.postman_collection.json). File [environment local](Trekking.local.postman_environment.json) chứa các biến để bạn điền.

Đây là kịch bản để bạn chạy thử. Việc kiểm tra cấu trúc collection không đồng nghĩa đã chạy thành công toàn bộ nghiệp vụ trên Docker hoặc đã thử import thực tế vào Apidog. Đăng ký, gửi OTP, tạo công ty, mời nhân viên và duyệt hồ sơ sẽ thay đổi dữ liệu khi bạn bấm Send.

## 1. Import và chuẩn bị

1. Mở **Apidog Desktop**, chọn project dùng để test.
2. Vào **Settings → Import Data → Postman**, chọn `Trekking.postman_collection.json`. Collection dùng định dạng Postman v2.1 để Apidog đọc request, biến và script.
3. Import riêng `Trekking.local.postman_environment.json` qua phần quản lý/import environment của Apidog, rồi chọn environment này làm môi trường đang dùng. Nếu giao diện import khác, đối chiếu và bổ sung các biến từ JSON; giữ nguyên tên biến.
4. Kiểm tra `baseUrl = http://localhost:8080`, không thêm `/api/v1` hoặc dấu `/` cuối. Chỉ đổi cổng khi Docker publish cổng khác.
5. Điền thông tin trong bảng dưới. Trước khi Send, URL/body không được còn biến `{{...}}` chưa resolve.
6. Chạy lần lượt từng request trong từng folder, từ `00` trở đi; đọc mô tả request và kết quả script sau mỗi lần Send.

Hướng dẫn chính thức: [import Postman vào Apidog](https://share.apidog.com/apidoc/docs-site/544525/import-from-postman-635043m0), [chuyển environment từ công cụ khác](https://docs.apidog.com/en/how-to-migrate-environments-from-other-tools-to-apidog-748056m0), [script tương thích Postman](https://docs.apidog.com/postman-scripts-reference-593586m0). Giao diện có thể khác theo phiên bản; collection và environment là hai file cần nhập.

| Biến cần điền | Dùng cho việc gì? |
| --- | --- |
| `customerEmail`, `customerPassword` | Email mới, hộp thư bạn đọc được; customer này sẽ làm owner công ty test. |
| `otherEmail`, `otherPassword` | Email mới thứ hai, có hộp thư; kiểm tra người ngoài không đọc được công ty của owner. Đây cũng là tài khoản test block/unblock cuối luồng. |
| `adminEmail`, `adminPassword` | Admin **đã tồn tại, đã xác minh và đăng nhập được**. API đăng ký không tạo admin, API thay role cũng không cấp được `admin`. |
| `accountantEmail`, `accountantPassword` | Email mới để admin mời kế toán; mật khẩu đặt khi kích hoạt. |
| `guideEmail`, `guidePassword` | Email mới để owner mời hướng dẫn viên; mật khẩu đặt khi kích hoạt. |
| `verificationOtp`, `otherVerificationOtp` | Mã xác minh email customer và khách hàng thứ hai, nhập sau khi nhận mail. |
| `activationOtp`, `guideActivationOtp` | Mã kích hoạt lời mời accountant và guide. |
| `agreementOtp` | Mã ký thỏa thuận gửi tới email owner. |
| `resetOtp`, `newCustomerPassword` | Mã đặt lại mật khẩu và mật khẩu mới ở nhánh reset tùy chọn. |
| `googleIdToken` | Chỉ điền khi chọn kiểm tra Google login thành công. |

Environment có sẵn mật khẩu test minh họa; có thể thay bằng mật khẩu riêng trước khi chạy. Mật khẩu tối thiểu 8 ký tự và tối đa 72 byte UTF-8. Bốn email mới phải khác nhau và khác admin; không dùng email ví dụ nếu bạn không nhận được thư. OTP là **chuỗi 6 chữ số**, ví dụ `"012345"`; giữ số 0 ở đầu và lấy mã từ email đúng mục đích.

Collection tự lưu token/ID từ response thành công: `customerToken`, `otherToken`, `adminToken`, `accountantToken`, `guideToken` cùng ID tài khoản/công ty/chính sách/receipt. Không cần chép Bearer vào từng request. Các refresh token được lưu riêng như `customerRefreshToken`, `adminRefreshToken`. Không dùng token của vai trò này thay cho vai trò khác.

`runId`, `taxCode` và `effectiveFrom` được script khởi tạo khi chưa có. Giữ các giá trị này ổn định trong cùng lượt chạy. Khi dùng bộ email mới cho lượt khác, xóa ID/token/OTP cũ và các giá trị khởi tạo này trước khi bắt đầu lại, để không tái sử dụng mã số thuế/policyCode hoặc tài nguyên cũ. Đối chiếu đầy đủ biến trong file environment.

Đã có [ảnh giấy phép giả lập](fixtures/sample-license.png) để chọn tại bước upload; đây là ảnh test, không phải giấy tờ pháp lý thật. Bạn cũng có thể chuẩn bị PDF, PNG hoặc JPEG hợp lệ khác. JSON collection không nhúng file trên máy bạn; vẫn phải chọn file thủ công.

## 2. CSRF, cookie và cách chạy

**Không chạy toàn bộ collection một lượt.** OTP phải dừng để đọc email, resend có cooldown; đổi mật khẩu/block/suspend ảnh hưởng request sau. Chạy từng request dễ theo dõi nhất. Nếu dùng runner, chỉ chọn đoạn đã đủ dữ liệu và dừng tại mỗi chỗ cần OTP/file.

Trước auth POST thông thường, pre-request script dùng `pm.sendRequest` gọi `GET {{baseUrl}}/api/v1/auth/csrf`, lấy token rồi gửi cặp cookie/header tương ứng. Request public không kế thừa Bearer. Case thử CSRF sai cố ý gửi header không hợp lệ; giữ nguyên case này để kiểm tra server từ chối.

Refresh token nằm trong `Set-Cookie`, không có trong JSON response. Collection giữ refresh cookie theo từng vai trò và đặt header `Cookie` rõ ràng cho refresh/logout, tránh lẫn phiên customer với admin khi đăng nhập nhiều tài khoản. Header Cookie tường minh cũng phục vụ HTTP local khi backend đặt cookie `Secure`.

Collection đặt `disableCookies` cho mỗi request. Nếu Apidog không giữ tùy chọn này khi import, tắt tự gửi cookie từ cookie jar để Cookie do script đặt không bị thêm/trộn cookie cũ. Kiểm tra header thực tế: auth POST có đúng cặp CSRF; refresh/logout chỉ thêm refresh token của đúng vai trò. Không chép thuộc tính `Path`, `HttpOnly`, `Secure` từ Set-Cookie vào Cookie. Nếu script không chạy hoặc client không cho đặt Cookie thủ công, dừng nhánh auth để sửa cấu hình client; không bỏ qua `403 INVALID_CSRF` hay `401` để chạy tiếp.

Mỗi request kiểm tra HTTP và field/code liên quan. JSON nhìn hợp lý chưa đủ để kết luận PASS: xem cả kết quả script. Negative case là PASS khi server trả đúng lỗi kỳ vọng, ví dụ `400 VALIDATION_ERROR` hoặc `403 FORBIDDEN`.

## 3. Thứ tự test và điểm dừng

Tên/ID request, method, URL và từng kỳ vọng chính xác nằm trong [CASE_INDEX.md](CASE_INDEX.md). Bảng dưới giải thích luồng; chạy theo thứ tự hiển thị của collection.

| Giai đoạn | Bạn làm gì? | Kết quả cần quan sát / khi nào dừng |
| --- | --- | --- |
| `00` — Smoke | Gọi CSRF và API cần đăng nhập khi chưa có token. | CSRF `200`; API bảo vệ `401`. Nếu timeout/connection refused/`5xx`, sửa Docker và dependency trước. |
| `01` — Validation, auth âm | Gửi dữ liệu thiếu/sai định dạng, sai CSRF, thông tin đăng nhập sai theo case. | Đúng HTTP và code ở mỗi case. Không lặp login sai hàng loạt vì có rate limit. |
| `02` — Customer | Register → **dừng lấy `verificationOtp`** → verify → login → me → authorization → refresh → thử quản trị trái quyền/đăng ký trùng. | Register `201`; verify `200`; login/refresh cấp token/cookie; me đúng email/ID. Sau refresh dùng cookie mới. API admin từ chối customer `403`; đăng ký trùng trả `409`. |
| `03` — Khách hàng khác | Register email thứ hai → **dừng lấy `otherVerificationOtp`** → verify → login. | Phiên khác owner; biến `otherToken`/ID không ghi đè customer. Dùng phiên này cho case phạm vi ở giai đoạn sau. |
| `04` — Hồ sơ công ty | Đăng nhập admin ở đầu folder → customer tạo công ty → đọc danh sách/chi tiết → update đủ hồ sơ → chọn file upload → tải license; thử dữ liệu/phạm vi sai. | Tạo `201`, pending; owner được role company_manager và membership owner. Upload `200`, licenseAvailable=true; download binary. Khách hàng thứ hai không đọc được công ty vừa tạo (`404 RESOURCE_NOT_FOUND`); admin cũng không dùng được URL thành viên khi không có membership. |
| `05` — Admin, duyệt, policy | Admin login → xem hồ sơ/license → approve → tạo policy → publish version. | Admin dùng `/admin/companies/...`; approve `200`, approved; policy/version `201`. Version ký phải đã tới effectiveFrom và có điều khoản tài chính. |
| `06` — Thỏa thuận | Owner đọc policy → xin OTP → **dừng lấy `agreementOtp`** → chấp nhận → đọc receipt → thử ký trùng. | OTP `202`; ký `201`; receipt đúng company/version, người ký và snapshot điều khoản. Ký lại version nhận `409 AGREEMENT_ALREADY_ACCEPTED`. |
| `07` — Accountant | Admin mời kế toán → kiểm tra chưa kích hoạt/chưa đủ quyền → resend theo case → **dừng lấy `activationOtp` mới nhất** → activate → login → admin cấp/xem quyền → accountant xem authorization. | Invitation `201` có accountId; activate `200`, rồi mới login. Quyền xuất hiện sau cấp; xung đột nhận `400 CONFLICTING_FINANCIAL_DUTIES`; accountant không tự cấp quyền qua API admin. |
| `08` — Guide | Owner công ty approved mời guide → resend theo case → **dừng lấy `guideActivationOtp` mới nhất** → activate → login → thử đọc công ty bằng token guide. | Guide có role guide, companyId trong invitation đúng. Guide không có membership quản lý; API đọc công ty bị từ chối `404`. |
| `09` — Auth tùy chọn | Chọn nhánh Google/resend xác minh/forgot-reset theo mô tả request. | SKIP nếu thiếu Google token hoặc không muốn đổi mật khẩu. Reset cần dừng lấy resetOtp; thành công phải login bằng newCustomerPassword và cập nhật customerPassword cho lần sau. |
| `10` — Quản trị tài khoản | Admin thay role other → thử cấp admin bị cấm → block → thử phiên cũ → unblock → thử lại token cũ → login. | Cấp admin bị từ chối; block/unblock đúng trạng thái; phiên cũ nhận `401`. Unblock không khôi phục token cũ. Đích block là otherId; giữ owner cho thao tác cuối. |
| `11` — Vòng đời công ty tùy chọn | Tạo công ty thứ hai → reject → resubmit → thử resubmit lặp; suspend công ty chính → thử update và đọc receipt. | Công ty thứ hai chuyển rejected → pending; resubmit lặp bị `409`. Công ty chính suspended không được update, nhưng vẫn đọc receipt đã ký. Chạy sau guide/agreement. |
| `12` — Logout | Đăng nhập customer nếu cần → logout → thử token phiên vừa logout. | Logout `204`, không có JSON; phiên đã logout bị từ chối khi gọi API bảo vệ. |

### Dừng đúng lúc để lấy OTP

OTP hiện hết hạn sau khoảng 5 phút. Nhập ngay sau khi nhận mail; xin mã mới sau cooldown khoảng 60 giây. Giới hạn gửi thường là 5 lần/giờ cho mỗi email/mục đích. Không chạy lại cả folder chỉ để gửi lại một request.

- **Register:** sau `201`, nhập verificationOtp/otherVerificationOtp rồi verify. Nếu email đã có từ lượt trước, không register lại: dùng resend-verification khi chưa xác minh, hoặc login nếu đã xác minh.
- **Invitation:** xem deliveryStatus. `sent` nghĩa là gửi không báo lỗi; `retry_required` nghĩa là tài khoản đã tạo nhưng cần gửi lại mail. Đợi cooldown rồi resend bằng accountId; không create invitation lại. Các request resend phải chạy **ít nhất 60 giây** sau lần gửi trước, trước activate. Mã mới thay mã cũ: chỉ nhập mã mới nhất. Sau activate thành công, invitation không còn pending để gửi lại.
- **Thỏa thuận:** mail tới tài khoản owner, không phải contactEmail công ty. Giữa xin mã và ký, giữ nguyên company/policy/quyền/phiên. Không thử mã sai liên tục vì sẽ hủy mã. Nếu ký lỗi sau khi mã đã bị tiêu thụ, xem receipt trước khi xin mã mới.
- **Reset:** forgot-password `202` chung không chứng minh email tồn tại hoặc mail đã tới. Đợi mail thật; OTP xác minh/kích hoạt không thay được OTP reset. Reset thu hồi phiên cũ: login lại bằng newCustomerPassword, cập nhật customerPassword và token trước bước phụ thuộc tiếp theo.

### Upload giấy phép

Mở request upload `CO-15` → **Body → form-data** → field `file`, chọn kiểu File và chọn `docs/api/testing/fixtures/sample-license.png` trong repository, hoặc file hợp lệ của bạn. Để Apidog tự đặt multipart boundary; không giữ `Content-Type: application/json` và không gửi JSON chứa đường dẫn file.

File tối đa **5 MiB**, MIME và nội dung phải khớp PDF/PNG/JPEG. Đổi tên `.txt` thành `.pdf` không tạo file hợp lệ. Download thành công trả binary với `Content-Disposition`, không phải JSON/base64; kiểm tra nội dung tải về mở được.

Update/upload khi công ty approved đưa về pending. Hoàn tất các bước này trước approve ở `05`; không chạy lại upload ngay trước ký hoặc mời guide.

### Các nhánh tùy chọn và thao tác cuối

Google login cần **Google ID token thật** với audience đúng cấu hình backend; backend access token không thay được. Điền googleIdToken hoặc SKIP nhánh thành công. Token giả chỉ kiểm tra nhánh từ chối, không xác nhận Google login đã hoạt động.

API roles và financial-permissions **thay toàn bộ tập**, không phải cộng thêm. Chỉ dùng ID fixture của lượt test làm đích thay đổi. Gỡ role accountant xóa financial grant; cấp lại role không tự khôi phục grant. Quyền FINANCE_READ không đồng nghĩa đã có endpoint tài chính ngoài 41 operation hiện tại.

Resubmit thành công cần công ty rejected. Muốn test reject trên công ty đang approved, phải update/upload hợp lệ đưa về pending trước; không reject trực tiếp approved. Khi suspended, chưa có API quyết định khôi phục trực tiếp. Chạy suspend sau guide/agreement hoặc dùng fixture riêng; đọc prerequisite của từng case trước Send.

## 4. Đọc lỗi và ghi kết quả

| Hiện tượng | Kiểm tra tiếp |
| --- | --- |
| Không kết nối localhost | Kiểm tra cổng publish và backend sẵn sàng bằng `docker compose ps`. |
| `403 INVALID_CSRF` ở auth POST | Script có chạy, GET csrf có `200`, cặp cookie/header đồng bộ, Cookie không bị client ghi đè? Case CSRF âm cố ý trả lỗi này. |
| `401` ở API cần token | Đúng token vai trò, chưa hết hạn/logout/block/reset? Token mặc định ngắn hạn; login lại sau khi dừng lâu. |
| `403 FORBIDDEN` | Đúng vai trò? Login được không có nghĩa có quyền admin. Negative case có thể kỳ vọng lỗi này. |
| `404 RESOURCE_NOT_FOUND` dù ID có thật | Kiểm tra membership, company_manager và companyId; backend có thể che tài nguyên ngoài phạm vi. |
| `409 EMAIL_ALREADY_REGISTERED` | Dùng login/resend hoặc email mới; không coi register lặp là lỗi backend. |
| `409 COMPANY_TAX_CODE_EXISTS` / `POLICY_CODE_EXISTS` | Mã từ lượt trước còn trong database; dùng mã duy nhất cho lượt mới. |
| `400 INVALID_OTP` / `INVALID_AGREEMENT_OTP` | Mã sai mục đích/cũ/hết hạn/đã dùng, quá lần thử hoặc bối cảnh đã đổi. |
| `429`, không có mail sau `202` | Đợi cooldown, xem quota/SMTP. `202` chung không bảo đảm mail tới; không retry liên tục. |
| Invitation retry_required, `503 MAIL_UNAVAILABLE` | Kiểm tra SMTP. Tài khoản có thể đã commit; sau sửa dùng resend tương ứng. |
| Không ký/mời guide được | Công ty phải approved; update/upload có thể đưa về pending. Owner cần phiên/membership hợp lệ. |
| Upload `400`/`413`/`415` | Có part file, đúng định dạng/nội dung/kích thước/MIME và multipart boundary? |
| Script lỗi/biến vẫn trống | Xem response bước trước, assertion/console và environment được chọn; dừng chuỗi phụ thuộc, không điền ID tùy ý. |

Mẫu ghi kết quả cho từng case trong [CASE_INDEX](CASE_INDEX.md):

| Case | Kỳ vọng | Thực tế | Kết luận | Ghi chú |
| --- | --- | --- | --- | --- |
| Tên/ID request | HTTP + code/trạng thái/field trong mô tả | HTTP + response + script | PASS / FAIL / SKIP / BLOCKED | Ví dụ BLOCKED do SMTP chưa gửi được OTP |

**PASS** khi HTTP và assertion nghiệp vụ đúng. **FAIL** khi trả khác contract dù đầu vào/tiền điều kiện hợp lệ. **SKIP** khi chủ động bỏ nhánh tùy chọn. **BLOCKED** khi thiếu admin, SMTP/hộp thư, Google token hoặc tiền điều kiện. Khi báo lỗi, ghi case, method/URL, trạng thái dữ liệu trước gọi và response đã che bí mật; không gửi environment có token/mật khẩu/OTP thật.

Nếu bước tạo/cấp quyền/ký lỗi bất ngờ, dừng các request phụ thuộc rồi đọc lại trạng thái trước khi retry. Một phần dữ liệu có thể đã commit dù response báo lỗi.

## 5. Kết thúc và chạy lại

Logout ở folder cuối; đăng xuất thêm các vai trò test khác nếu còn sử dụng. Giữ bảng ID/email/taxCode/policyCode để đối chiếu. Nếu đổi mật khẩu, dùng giá trị mới cho lần login tiếp theo. Không export/chia sẻ environment có token, cookie, mật khẩu hoặc OTP thật.

Bộ API hiện chưa có thao tác xóa công ty/chính sách/receipt; receipt và version được bảo vệ khỏi sửa/xóa. Dữ liệu test còn trong database. Lượt mới dùng email/mã duy nhất; không xóa Docker volume hoặc reset database để làm collection chạy lại. Nếu cần dọn, thực hiện riêng trên môi trường test có kế hoạch sao lưu.

Response ví dụ trong API docs minh họa contract. Chỉ bảng kết quả sau khi bạn chạy thực tế mới xác nhận luồng local của bạn đã PASS.

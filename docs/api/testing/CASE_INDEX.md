# Danh sách request và kết quả kỳ vọng

Sinh từ `tools/generate_apidog_collection.py`; xem [hướng dẫn chạy](TEST_SCENARIOS.md).

## 00 | Kiểm tra backend

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| SM-01 | Lấy CSRF token | `GET /api/v1/auth/csrf` | 200 |
| SM-02 | Chưa đăng nhập không được xem me | `GET /api/v1/auth/me` | 401 `UNAUTHORIZED` |

## 01 | Kiểm tra dữ liệu sai và CSRF

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| NEG-01 | Auth POST sai CSRF | `POST /api/v1/auth/login` | 403 `INVALID_CSRF` |
| NEG-02 | Email đăng ký sai định dạng | `POST /api/v1/auth/register` | 400 `VALIDATION_ERROR` |
| NEG-03 | Mật khẩu đăng ký quá ngắn | `POST /api/v1/auth/register` | 400 `VALIDATION_ERROR` |
| NEG-04 | Sai thông tin đăng nhập | `POST /api/v1/auth/login` | 401 `INVALID_CREDENTIALS` |
| NEG-05 | OTP xác minh sai định dạng | `POST /api/v1/auth/verify-email` | 400 `VALIDATION_ERROR` |

## 02 | customer đăng ký → DỪNG OTP → đăng nhập

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| CUS-01 | Đăng ký customer | `POST /api/v1/auth/register` | 201 |
| CUS-02 | Xác minh email — nhập OTP trước | `POST /api/v1/auth/verify-email` | 200 |
| CUS-03 | Đăng nhập customer | `POST /api/v1/auth/login` | 200 |
| CUS-04 | Thông tin customer | `GET /api/v1/auth/me` | 200 |
| CUS-05 | Xem quyền hiện tại | `GET /api/v1/accounts/me/authorization` | 200 |
| CUS-06 | Làm mới phiên customer | `POST /api/v1/auth/refresh` | 200 |
| CUS-07 | Customer không được quản trị tài khoản | `GET /api/v1/admin/accounts/{{customerId}}` | 403 `FORBIDDEN` |
| CUS-08 | Đăng ký trùng email | `POST /api/v1/auth/register` | 409 `EMAIL_ALREADY_REGISTERED` |

## 03 | other đăng ký → DỪNG OTP → đăng nhập

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| OTHER-01 | Đăng ký other | `POST /api/v1/auth/register` | 201 |
| OTHER-02 | Xác minh email — nhập OTP trước | `POST /api/v1/auth/verify-email` | 200 |
| OTHER-03 | Đăng nhập other | `POST /api/v1/auth/login` | 200 |
| OTHER-04 | Thông tin other | `GET /api/v1/auth/me` | 200 |

## 04 — Công ty trước duyệt (chọn file giấy phép thủ công)

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| ADM-00 | Đăng nhập admin | `POST /api/v1/auth/login` | 200 |
| CO-01 | Tạo công ty pending và lưu companyId | `POST /api/v1/companies` | 201 |
| SCOPE-01 | Customer khác không xem được công ty của owner | `GET /api/v1/companies/{{companyId}}` | 404 `RESOURCE_NOT_FOUND` |
| CO-02 | Tạo trùng mã số thuế bị chặn | `POST /api/v1/companies` | 409 `COMPANY_TAX_CODE_EXISTS` |
| CO-03 | Hồ sơ không hợp lệ trả fieldErrors | `POST /api/v1/companies` | 400 `VALIDATION_ERROR` |
| CO-04 | Danh sách công ty mình quản lý | `GET /api/v1/companies?page=0&size=20` | 200 |
| CO-05 | Phân trang công ty size=101 bị chặn | `GET /api/v1/companies?page=0&size=101` | 400 `INVALID_REQUEST` |
| CO-06 | Đọc hồ sơ công ty mình | `GET /api/v1/companies/{{companyId}}` | 200 |
| CO-07 | Ẩn công ty ngoài phạm vi bằng 404 | `GET /api/v1/companies/-1` | 404 `RESOURCE_NOT_FOUND` |
| CO-08 | Admin không có membership không đọc bằng URL thành viên | `GET /api/v1/companies/{{companyId}}` | 404 `RESOURCE_NOT_FOUND` |
| CO-09 | Cập nhật đầy đủ hồ sơ trước khi duyệt | `PUT /api/v1/companies/{{companyId}}` | 200 |
| CO-10 | Cập nhật thiếu tên bị chặn | `PUT /api/v1/companies/{{companyId}}` | 400 `VALIDATION_ERROR` |
| CO-11 | Không resubmit công ty đang pending | `POST /api/v1/companies/{{companyId}}/resubmit` | 409 `COMPANY_STATE_CONFLICT` |
| CO-12 | Chưa có giấy phép thì download trả 404 | `GET /api/v1/companies/{{companyId}}/license` | 404 `COMPANY_LICENSE_NOT_FOUND` |
| CO-13 | Admin không được approve khi thiếu giấy phép | `POST /api/v1/admin/companies/{{companyId}}/decision` | 409 `COMPANY_LICENSE_REQUIRED` |
| AGR-01 | Không xin OTP ký khi công ty chưa được duyệt | `POST /api/v1/companies/{{companyId}}/agreements/otp` | 409 `COMPANY_NOT_APPROVED` |
| CO-15 | MANUAL — chọn file rồi upload giấy phép | `POST /api/v1/companies/{{companyId}}/license` | 200 |
| CO-16 | Download giấy phép bằng token owner | `GET /api/v1/companies/{{companyId}}/license` | 200 |

## 05 — Admin duyệt và xuất bản chính sách

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| ADM-01 | Đăng nhập admin | `POST /api/v1/auth/login` | 200 |
| CO-17 | Customer không được xem danh sách duyệt admin | `GET /api/v1/admin/companies?page=0&size=20` | 403 `FORBIDDEN` |
| CO-18 | Admin xem danh sách công ty pending | `GET /api/v1/admin/companies?status=pending&page=0&size=20` | 200 |
| CO-19 | Admin lọc trạng thái sai bị chặn | `GET /api/v1/admin/companies?status=UNKNOWN&page=0&size=20` | 400 `INVALID_REQUEST` |
| CO-20 | Admin phân trang âm bị chặn | `GET /api/v1/admin/companies?page=-1&size=20` | 400 `INVALID_REQUEST` |
| CO-21 | Admin đọc chi tiết hồ sơ | `GET /api/v1/admin/companies/{{companyId}}` | 200 |
| CO-22 | Admin download giấy phép | `GET /api/v1/admin/companies/{{companyId}}/license` | 200 |
| CO-23 | Admin đọc công ty không tồn tại | `GET /api/v1/admin/companies/-1` | 404 `COMPANY_NOT_FOUND` |
| CO-24 | Customer không được tự duyệt | `POST /api/v1/admin/companies/{{companyId}}/decision` | 403 `FORBIDDEN` |
| CO-25 | Quyết định thiếu lý do bị chặn | `POST /api/v1/admin/companies/{{companyId}}/decision` | 400 `VALIDATION_ERROR` |
| CO-26 | Admin duyệt công ty thành approved | `POST /api/v1/admin/companies/{{companyId}}/decision` | 200 |
| CO-27 | Không duyệt lặp công ty đã approved | `POST /api/v1/admin/companies/{{companyId}}/decision` | 409 `COMPANY_STATE_CONFLICT` |
| POL-01 | Customer không được tạo chính sách | `POST /api/v1/admin/policies` | 403 `FORBIDDEN` |
| POL-02 | Admin tạo policy riêng cho công ty và lưu policyId | `POST /api/v1/admin/policies` | 201 |
| POL-03 | Mã policy trùng bị chặn | `POST /api/v1/admin/policies` | 409 `POLICY_CODE_EXISTS` |
| POL-04 | Phí nền tảng trên 100 bị chặn | `POST /api/v1/admin/policies/{{policyId}}/versions` | 400 `VALIDATION_ERROR` |
| POL-05 | Phí có ba chữ số thập phân bị chặn | `POST /api/v1/admin/policies/{{policyId}}/versions` | 400 `VALIDATION_ERROR` |
| POL-06 | Thời gian thanh toán âm bị chặn | `POST /api/v1/admin/policies/{{policyId}}/versions` | 400 `VALIDATION_ERROR` |
| POL-07 | Publish version có hiệu lực và lưu policyVersionId | `POST /api/v1/admin/policies/{{policyId}}/versions` | 201 |

## 06 — Thỏa thuận (DỪNG sau xin OTP, nhập agreementOtp)

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| AGR-02 | Đọc chính sách đủ điều kiện ký | `GET /api/v1/companies/{{companyId}}/agreement-policies` | 200 |
| AGR-03 | ID policy bằng 0 bị chặn trước khi gửi OTP | `POST /api/v1/companies/{{companyId}}/agreements/otp` | 400 `VALIDATION_ERROR` |
| AGR-04 | OTP sai định dạng bị chặn trước khi tiêu thụ | `POST /api/v1/companies/{{companyId}}/agreements` | 400 `VALIDATION_ERROR` |
| AGR-05 | Xem lịch sử thỏa thuận trước khi ký | `GET /api/v1/companies/{{companyId}}/agreements` | 200 |
| AGR-06 | Xin OTP ký — DỪNG để đọc email và nhập agreementOtp | `POST /api/v1/companies/{{companyId}}/agreements/otp` | 202 |
| AGR-07 | MANUAL — nhập OTP rồi ký và lưu agreementId | `POST /api/v1/companies/{{companyId}}/agreements` | 201 |
| AGR-08 | Xem lịch sử có receipt vừa ký | `GET /api/v1/companies/{{companyId}}/agreements` | 200 |
| AGR-09 | Đọc chi tiết receipt | `GET /api/v1/companies/{{companyId}}/agreements/{{agreementId}}` | 200 |
| AGR-10 | Receipt không tồn tại bị ẩn bằng 404 | `GET /api/v1/companies/{{companyId}}/agreements/-1` | 404 `RESOURCE_NOT_FOUND` |
| AGR-11 | Không ký lặp cùng version | `POST /api/v1/companies/{{companyId}}/agreements` | 409 `AGREEMENT_ALREADY_ACCEPTED` |

## 07 | Mời kế toán → DỪNG OTP → cấp quyền

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| INV-01 | Admin mời kế toán | `POST /api/v1/admin/staff/invitations` | 201 |
| INV-02 | Gửi lại lời mời kế toán — đợi 60 giây | `POST /api/v1/admin/staff/invitations/{{accountantId}}/resend` | 202 |
| FIN-01 | Không cấp quyền cho kế toán chưa kích hoạt | `PUT /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 409 `FINANCIAL_ACCOUNT_INELIGIBLE` |
| INV-03 | Kích hoạt kế toán — nhập OTP trước | `POST /api/v1/auth/activate-account` | 200 |
| INV-04 | Đăng nhập accountant | `POST /api/v1/auth/login` | 200 |
| FIN-02 | Admin xem tài khoản kế toán | `GET /api/v1/admin/accounts/{{accountantId}}` | 200 |
| FIN-03 | Kế toán mới chưa có financial grant | `GET /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 200 |
| FIN-04 | Admin cấp quyền đọc và chuẩn bị quyết toán | `PUT /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 200 |
| FIN-05 | Kế toán thấy quyền được cấp | `GET /api/v1/accounts/me/authorization` | 200 |
| FIN-06 | Không gộp quyền chuẩn bị và duyệt quyết toán | `PUT /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 400 `CONFLICTING_FINANCIAL_DUTIES` |
| FIN-07 | Không gộp duyệt hoàn và ghi hoàn tiền | `PUT /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 400 `CONFLICTING_FINANCIAL_DUTIES` |
| FIN-08 | Request lỗi không thay grant đã lưu | `GET /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 200 |
| FIN-09 | Kế toán không được tự quản trị grant | `GET /api/v1/admin/accounts/{{accountantId}}/financial-permissions` | 403 `FORBIDDEN` |

## 08 | Mời guide → DỪNG OTP → đăng nhập

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| GUI-01 | Owner mời hướng dẫn viên | `POST /api/v1/companies/{{companyId}}/guides/invitations` | 201 |
| GUI-02 | Gửi lại lời mời guide — đợi 60 giây | `POST /api/v1/companies/{{companyId}}/guides/invitations/{{guideId}}/resend` | 202 |
| GUI-03 | Kích hoạt guide — nhập OTP trước | `POST /api/v1/auth/activate-account` | 200 |
| GUI-04 | Đăng nhập guide | `POST /api/v1/auth/login` | 200 |
| GUI-05 | Guide không phải người quản lý công ty | `GET /api/v1/companies/{{companyId}}` | 404 `RESOURCE_NOT_FOUND` |

## 09 | TÙY CHỌN Google, gửi lại mã và reset password

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| OPT-01 | Gửi lại mã cho email đã xác minh vẫn trả chung | `POST /api/v1/auth/resend-verification` | 202 |
| OPT-02 | Google ID token sai | `POST /api/v1/auth/google` | 401 `INVALID_GOOGLE_TOKEN` |
| OPT-03 | Google login thật | `POST /api/v1/auth/google` | 200 |
| OPT-04 | Yêu cầu OTP đặt lại mật khẩu customer | `POST /api/v1/auth/forgot-password` | 202 |
| OPT-05 | Đặt lại mật khẩu — nhập resetOtp trước | `POST /api/v1/auth/reset-password` | 200 |
| OPT-06 | Đăng nhập bằng mật khẩu mới | `POST /api/v1/auth/login` | 200 |

## 10 | CUỐI LUỒNG — quản trị tài khoản other

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| ADM-02 | Thay toàn bộ role tài khoản other | `PUT /api/v1/admin/accounts/{{otherId}}/roles` | 200 |
| ADM-03 | Không được cấp role admin | `PUT /api/v1/admin/accounts/{{otherId}}/roles` | 400 `INVALID_ROLES` |
| ADM-04 | Khóa tài khoản other | `POST /api/v1/admin/accounts/{{otherId}}/block` | 200 |
| ADM-05 | Token cũ bị thu hồi sau khóa | `GET /api/v1/auth/me` | 401 `UNAUTHORIZED` |
| ADM-06 | Mở khóa tài khoản other | `POST /api/v1/admin/accounts/{{otherId}}/unblock` | 200 |
| ADM-07 | Token cũ không hồi sinh sau mở khóa | `GET /api/v1/auth/me` | 401 `UNAUTHORIZED` |
| ADM-08 | Other đăng nhập lại sau mở khóa | `POST /api/v1/auth/login` | 200 |

## 11 — TÙY CHỌN sau ký: reject, resubmit, suspend

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| CO-28 | Tạo công ty thứ hai để thử reject/resubmit | `POST /api/v1/companies` | 201 |
| CO-29 | Admin từ chối công ty thứ hai | `POST /api/v1/admin/companies/{{rejectedCompanyId}}/decision` | 200 |
| CO-30 | Customer gửi lại hồ sơ rejected | `POST /api/v1/companies/{{rejectedCompanyId}}/resubmit` | 200 |
| CO-31 | Không gửi lại hai lần khi đã pending | `POST /api/v1/companies/{{rejectedCompanyId}}/resubmit` | 409 `COMPANY_STATE_CONFLICT` |
| CO-32 | CUỐI CÙNG — admin đình chỉ công ty chính | `POST /api/v1/admin/companies/{{companyId}}/decision` | 200 |
| CO-33 | Công ty suspended không được sửa hồ sơ | `PUT /api/v1/companies/{{companyId}}` | 409 `COMPANY_STATE_CONFLICT` |
| AGR-12 | Vẫn đọc được receipt sau khi đình chỉ | `GET /api/v1/companies/{{companyId}}/agreements/{{agreementId}}` | 200 |

## 12 | Kết thúc phiên customer

| ID | Kịch bản | Method và URL | Kỳ vọng |
| --- | --- | --- | --- |
| OUT-01 | Đăng xuất customer | `POST /api/v1/auth/logout` | 204 |
| OUT-02 | Token của phiên vừa logout bị thu hồi | `GET /api/v1/auth/me` | 401 `UNAUTHORIZED` |

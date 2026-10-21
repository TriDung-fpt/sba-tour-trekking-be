# Tài liệu API Trekking — hướng dẫn tiếng Việt

Tài liệu đối chiếu Swagger tại `http://localhost:8080/v3/api-docs` với controller,
DTO, service và cấu hình bảo mật trong repository ngày **21/10/2026**.
Hiện có **41 thao tác API trên 36 đường dẫn**. Một URL có thể hỗ trợ nhiều method,
ví dụ `GET /companies` để xem danh sách và `POST /companies` để tạo công ty.

## 1. Mở tài liệu và chọn nhóm API

| Tài liệu | Dùng để làm gì? |
| --- | --- |
| [Đăng nhập và xác thực — 11 API](api/AUTH.md) | Đăng ký, xác minh email, đăng nhập bằng mật khẩu/Google, lấy thông tin tài khoản, làm mới phiên, đăng xuất và đặt lại mật khẩu. |
| [Tài khoản và lời mời — 12 API](api/ACCOUNTS_INVITATIONS.md) | Xem quyền hiện tại, admin quản lý tài khoản và quyền tài chính, mời nhân viên/hướng dẫn viên, kích hoạt tài khoản được mời. |
| [Công ty, chính sách và thỏa thuận — 18 API](api/COMPANIES_POLICIES.md) | Tạo hồ sơ và tải giấy phép công ty, admin xét duyệt, phát hành chính sách, gửi OTP và ký thỏa thuận hợp tác. |
| [OpenAPI JSON đã xuất](api/openapi.json) | Bản mô tả máy đọc được, có thể import vào Postman hoặc công cụ hỗ trợ OpenAPI. |
| [Swagger đang chạy](http://localhost:8080/swagger-ui/index.html) | Xem schema và dùng **Try it out → Execute** để gọi API. |
| [Kịch bản test và file import Apidog](api/testing/TEST_SCENARIOS.md) | Chạy 106 request theo luồng, nhập OTP đúng bước và kiểm tra kết quả tự động. |

Mỗi nhóm có **method + URL, mục đích, quyền gọi, request, response, HTTP status,
ràng buộc dữ liệu và lỗi**. Ví dụ dùng dữ liệu giả; ID, OTP, token, thời gian và
trạng thái phải lấy từ kết quả thực tế của hệ thống.

Đây là phạm vi các API đã có controller và được Swagger hiện tại công bố.
Các tên quyền liên quan booking, payment hay tour không có nghĩa đã có API công
khai tương ứng trong bản backend này.

## 2. Hiểu URL, request và response

**Base URL trên Docker local:** `http://localhost:8080`.
Ghép base URL với đường dẫn, ví dụ:
`http://localhost:8080/api/v1/auth/login`.

| Thuật ngữ | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| Method | Loại thao tác HTTP. `GET` đọc; `POST` thực hiện thao tác/tạo mới; `PUT` cập nhật theo contract của API. | `POST` đăng nhập. |
| URL/path | Địa chỉ tài nguyên hoặc thao tác. | `/api/v1/companies/{id}` |
| Path parameter | Giá trị thay vào phần `{...}` trong URL; không gửi nguyên dấu ngoặc. | `{id}=10` → `/api/v1/companies/10` |
| Query parameter | Tham số sau dấu `?`, thường dùng lọc hoặc phân trang. | `?page=0&size=20` |
| Request / req / rsq | Dữ liệu client gửi: headers, query, path và body nếu có. | JSON email và password. |
| Request body | Nội dung gửi trong request. Hầu hết dùng JSON; upload giấy phép dùng multipart. | `{"email":"customer@example.com","password":"StrongPassword123!"}` |
| Header | Thông tin đi kèm request. | `Authorization: Bearer <accessToken>` |
| Response / res / rsp | Kết quả server trả về, gồm HTTP status, headers và body nếu có. | `200` cùng JSON tài khoản/token. |
| DTO/schema | Danh sách field và kiểu dữ liệu cho request/response. | `expiresIn` là số giây. |
| Không có body | Bỏ phần body; không tự thêm field theo suy đoán. | `POST /api/v1/auth/refresh` |

## 3. Đăng nhập và gọi thử trên Swagger

1. Mở [Swagger UI](http://localhost:8080/swagger-ui/index.html).
2. Nếu chưa có tài khoản, gọi `POST /api/v1/auth/register`, rồi dùng OTP nhận qua
   email gọi `POST /api/v1/auth/verify-email`. Có tài khoản đã xác minh thì sang bước 3.
3. Mở `POST /api/v1/auth/login` → **Try it out**, nhập email/password thật → **Execute**.
4. Khi trả `200`, sao chép giá trị `accessToken` trong response.
5. Bấm **Authorize**, dán **chỉ giá trị token** vào ô `bearerAuth`, rồi xác nhận.
   Swagger tự thêm tiền tố `Bearer` vào header.
6. Gọi `GET /api/v1/auth/me` để xem tài khoản và
   `GET /api/v1/accounts/me/authorization` để xem quyền/phạm vi công ty.
7. Chọn API nghiệp vụ phù hợp với quyền của tài khoản. Đăng nhập thành công
   không đồng nghĩa có quyền admin hoặc quyền quản lý mọi công ty.

Swagger có phần tự lấy CSRF token trước mỗi `POST /api/v1/auth/...` và gửi cookie
cùng header tương ứng. Nếu đã bấm Authorize nhưng token hết hạn, hãy bỏ Authorize
trước khi gọi login/refresh; bearer token không hợp lệ vẫn có thể bị chặn trên API công khai.

Việc nhận OTP cần cấu hình mail hoạt động. Google login cần Google ID token thật
có audience phù hợp cấu hình backend; access token của ứng dụng không thay thế được Google ID token.

## 4. Headers, cookie và bảo mật dùng chung

### API cần đăng nhập

```http
Authorization: Bearer <accessToken>
Accept: application/json
Content-Type: application/json
```

`Content-Type: application/json` dùng khi có JSON body. Với upload file, client
tự tạo `multipart/form-data` kèm boundary. Không đặt Content-Type JSON cho upload.

Các API ngoài `/api/v1/auth/**` đều cần đăng nhập. Trong nhóm auth,
`GET /api/v1/auth/me` cần bearer token; các endpoint còn lại không bắt buộc bearer.
Swagger và `/v3/api-docs/**` truy cập được khi chưa đăng nhập.

### CSRF cho các POST trong `/api/v1/auth/`

Khi dùng frontend hoặc Postman, thực hiện:

1. Gọi `GET /api/v1/auth/csrf`; giữ cookie `XSRF-TOKEN` trong cookie jar.
2. Đọc `headerName` và `token` từ response, ví dụ:

   ```json
   {
     "headerName": "X-XSRF-TOKEN",
     "token": "<csrf-token>"
   }
   ```

3. Khi POST auth, gửi cả cookie và `X-XSRF-TOKEN: <csrf-token>`.
   Quy tắc áp dụng cả `/auth/activate-account`, login, register, refresh và logout.
   Nếu cookie/token thay đổi, lấy lại CSRF token.

API nghiệp vụ ngoài `/api/v1/auth/` dùng bearer và hiện không yêu cầu CSRF header.

### Access token và refresh cookie

| Thành phần | Cách dùng |
| --- | --- |
| `accessToken` | Có trong JSON login/Google/refresh; gửi qua bearer cho API cần đăng nhập. TTL mặc định 15 phút; đọc `expiresIn` thực tế. |
| `trekking_refresh` | Cookie `HttpOnly`, path `/api/v1/auth`, `SameSite=Lax`; backend đặt qua `Set-Cookie`. Không nằm trong JSON response. Phiên mặc định 7 ngày. |
| Refresh | Gọi `POST /api/v1/auth/refresh` với refresh cookie và CSRF; không có JSON body. Mỗi lần thành công trả access token và thay refresh cookie. |
| Gọi đồng thời | Client cần tuần tự hóa refresh. Dùng lại refresh token cũ có thể làm phiên bị thu hồi. |
| Frontend khác origin | Gửi cookie với `credentials: "include"`; origin phải được cho phép trong `AUTH_ALLOWED_ORIGINS`. |
| Cookie local | `AUTH_COOKIE_SECURE` mặc định `true`. Nếu client không giữ/gửi cookie khi dùng HTTP local, kiểm tra cấu hình này; môi trường HTTPS dùng cookie Secure. |

## 5. Vai trò và phạm vi dữ liệu

Role trong JSON là chuỗi chữ thường; permission là chuỗi chữ hoa.

| Role | Ý nghĩa trong các API hiện có |
| --- | --- |
| `customer` | Tài khoản khách hàng, đăng ký và sử dụng tính năng được cấp cho mình. |
| `admin` | Quản trị tài khoản, cấp role/quyền tài chính, mời nhân sự nội bộ, xét duyệt công ty và quản lý chính sách. |
| `company_manager` | Quản lý công ty khi đồng thời có membership `owner` hoặc `manager` đang hoạt động tại công ty đó. |
| `guide` | Hướng dẫn viên; không mặc nhiên có quyền sửa hồ sơ công ty. |
| `support_staff` | Nhân viên hỗ trợ; có thể được admin mời qua API nhân sự nội bộ. |
| `content_staff` | Nhân viên nội dung; có thể được admin mời qua API nhân sự nội bộ. |
| `accountant` | Kế toán; cần được admin cấp từng quyền tài chính cụ thể. |

Quyền quản lý công ty phải kiểm tra theo `companyId`. Admin xét duyệt qua API
`/api/v1/admin/companies/...`; role admin riêng lẻ không bỏ qua các điều kiện của
API phía công ty. Ký thỏa thuận yêu cầu owner; mời guide yêu cầu công ty được duyệt.

API có thể trả `404 RESOURCE_NOT_FOUND` khi caller không có phạm vi truy cập tài
nguyên đó. Vì vậy `404` không luôn có nghĩa ID không tồn tại trong database.

## 6. Định dạng lỗi và HTTP status

Lỗi do exception handler/security của ứng dụng xử lý có dạng:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  }
}
```

Đây là ví dụ minh họa. Nội dung `fieldErrors` phụ thuộc field sai và validator;
đa số lỗi nghiệp vụ trả `fieldErrors: {}`. Client nên dựa vào HTTP status và `code`,
không so sánh chuỗi `message` để quyết định luồng.

| HTTP status | Ý nghĩa thường gặp | Ví dụ code |
| --- | --- | --- |
| `200 OK` | Thao tác thành công, thường có JSON response. | — |
| `201 Created` | Đã tạo tài nguyên theo contract từng endpoint. | — |
| `202 Accepted` | Request đã được xử lý theo cơ chế gửi mã có phản hồi chung; không xác nhận email tồn tại hay email đã đến. | — |
| `204 No Content` | Thành công, không có body; không gọi JSON parser cho response này. | — |
| `400 Bad Request` | Dữ liệu không hợp lệ, JSON sai, thiếu/sai tham số. | `VALIDATION_ERROR`, `INVALID_REQUEST` |
| `401 Unauthorized` | Thiếu bearer, token/phiên không hợp lệ hoặc hết hạn. | `UNAUTHORIZED`; các code cụ thể xem nhóm auth. |
| `403 Forbidden` | Không đủ quyền hoặc CSRF không hợp lệ. | `FORBIDDEN`, `INVALID_CSRF` |
| `404 Not Found` | Không thấy tài nguyên hoặc ngoài phạm vi cho phép. | `RESOURCE_NOT_FOUND` |
| `409 Conflict` | Xung đột trạng thái/nghiệp vụ; xem từng endpoint. | Code tùy nghiệp vụ. |
| `413 Payload Too Large` | Upload vượt giới hạn. | `MAX_UPLOAD_SIZE` |
| `429 Too Many Requests` | Vượt giới hạn OTP/đăng nhập hoặc thao tác bị giới hạn. | Code cụ thể xem nhóm API. |
| `503 Service Unavailable` | Database/Redis hoặc dịch vụ liên quan tạm thời không khả dụng. | `AUTH_UNAVAILABLE` |

Các status trong bảng là quy ước tổng quát, không có nghĩa mọi endpoint đều trả
tất cả các status. Xem phần lỗi riêng của từng API.

## 7. Chọn API theo công việc

| Bạn muốn làm gì? | Thứ tự API chính |
| --- | --- |
| Đăng ký tài khoản khách hàng | `register` → nhận OTP email → `verify-email` → `login` → `me`. |
| Quên mật khẩu | `forgot-password` → nhận OTP → `reset-password` → đăng nhập lại. |
| Khôi phục phiên frontend | Lấy CSRF → `refresh` bằng cookie → cập nhật access token. |
| Tạo hồ sơ đối tác | Tài khoản có role `customer` tạo company → hệ thống cấp role `company_manager` và membership `owner` → tải license → chờ admin xét duyệt. |
| Xét duyệt công ty | Admin liệt kê hồ sơ → xem chi tiết/license → gửi `decision`. |
| Phát hành và ký chính sách | Admin tạo policy/phát hành version → owner xem chính sách và version → yêu cầu OTP → chấp nhận thỏa thuận → đọc receipt. |
| Mời nhân sự nội bộ | Admin mời `support_staff`/`content_staff`/`accountant` → người nhận dùng `activate-account` để xác minh OTP và đặt mật khẩu. |
| Mời hướng dẫn viên | Quản lý công ty được duyệt mời guide → người nhận kích hoạt tài khoản. |
| Cấp quyền kế toán | Admin bảo đảm tài khoản accountant đã active/xác minh → đọc grant hiện tại → PUT toàn bộ tập quyền mong muốn. |

Các dòng trên mô tả luồng; đường dẫn đầy đủ, field và response nằm trong ba tài
liệu chi tiết. Không gửi request thay đổi dữ liệu chỉ để khám phá ý nghĩa endpoint.

## 8. Nguồn dữ liệu và giới hạn

- [openapi.json](api/openapi.json) là snapshot từ backend đang chạy, không tự cập nhật khi sửa code.
- DTO xác định field/kiểu/validation; controller xác định URL và HTTP status;
  service và security xác định quyền, trạng thái và lỗi nghiệp vụ.
- Swagger hiện chưa mô tả hết lỗi, CSRF và cookie. Tài liệu tiếng Việt bổ sung
  những phần đó từ code.
- `POST /api/v1/companies` đang được Swagger ghi là `200`, trong khi controller
  trả `201 Created`. Tài liệu chi tiết ghi `201` theo controller.
- Chưa gửi request nghiệp vụ ghi dữ liệu trên database local để tạo các ví dụ.
  Các JSON trong tài liệu là ví dụ minh họa contract, không phải dữ liệu người dùng thật.

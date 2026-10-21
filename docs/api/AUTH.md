# API xác thực: đăng ký, đăng nhập và phiên làm việc

[← Mục lục API](../API_DOCS_VI.md)

Tài liệu này mô tả 11 API xác thực, đối chiếu [OpenAPI đang chạy được lưu lại](openapi.json) với controller, DTO, service và Spring Security trong mã nguồn. URL gốc khi chạy Docker hiện tại là `http://localhost:8080`. API kích hoạt tài khoản được mời (`POST /api/v1/auth/activate-account`) nằm trong [tài liệu tài khoản](ACCOUNTS_INVITATIONS.md).

`Request` (req/rsq) là dữ liệu client gửi; `Response` (rsp) là dữ liệu server trả. Tất cả email, tên, ID, mật khẩu, OTP và token dưới đây là **ví dụ giả lập**, không phải tài khoản hay mã đã lấy từ database. Hãy thay OTP bằng mã từ email và token bằng kết quả thực tế. Các mã HTTP và tên trường được lấy từ mã nguồn; các ví dụ thành công không phải kết quả gọi API thay đổi dữ liệu trong lần viết tài liệu này.

## Danh sách nhanh

| Method | URL | Mục đích | Thành công |
| --- | --- | --- | --- |
| GET | `http://localhost:8080/api/v1/auth/csrf` | Lấy token chống giả mạo request | 200 |
| POST | `http://localhost:8080/api/v1/auth/register` | Khách hàng tự đăng ký, gửi OTP xác minh email | 201 |
| POST | `http://localhost:8080/api/v1/auth/verify-email` | Xác minh email bằng OTP | 200 |
| POST | `http://localhost:8080/api/v1/auth/resend-verification` | Yêu cầu gửi lại OTP xác minh | 202 |
| POST | `http://localhost:8080/api/v1/auth/login` | Đăng nhập bằng email và mật khẩu | 200 |
| POST | `http://localhost:8080/api/v1/auth/google` | Đăng nhập bằng Google ID token | 200 |
| POST | `http://localhost:8080/api/v1/auth/refresh` | Đổi refresh cookie lấy access token mới | 200 |
| POST | `http://localhost:8080/api/v1/auth/logout` | Thu hồi phiên và xóa refresh cookie | 204 |
| POST | `http://localhost:8080/api/v1/auth/forgot-password` | Yêu cầu OTP đặt lại mật khẩu | 202 |
| POST | `http://localhost:8080/api/v1/auth/reset-password` | Đặt mật khẩu mới bằng OTP | 200 |
| GET | `http://localhost:8080/api/v1/auth/me` | Lấy thông tin người đang đăng nhập | 200 |

## Quy tắc request, token và lỗi chung

Không API nào trong 11 API này có path parameter hoặc query parameter. Các POST có JSON dùng `Content-Type: application/json`; `/refresh` và `/logout` không có request body. Chỉ `/me` bắt buộc `Authorization: Bearer <accessToken>`. Những endpoint còn lại cho phép người chưa đăng nhập, nhưng **mọi POST dưới `/api/v1/auth/` vẫn bắt buộc CSRF**. Bearer token không thay thế được CSRF.

Trước khi gọi POST:

1. Gọi `GET /api/v1/auth/csrf`, giữ cookie `XSRF-TOKEN` server trả.
2. Lấy trường `token` trong JSON, gửi trong header có tên ở `headerName` (hiện là `X-XSRF-TOKEN`).
3. Gửi kèm cookie `XSRF-TOKEN` tương ứng. Cookie và token phải cùng cặp.

Ví dụ header POST, thay các giá trị giả lập bằng giá trị thật:

```http
Content-Type: application/json
X-XSRF-TOKEN: 8b050e51-a49d-460b-a6f8-b19a6a32f667
Cookie: XSRF-TOKEN=8b050e51-a49d-460b-a6f8-b19a6a32f667
```

Trình duyệt cần giữ cookie; khi gọi từ frontend khác origin, dùng `credentials: "include"` và origin phải được cấu hình trong `AUTH_ALLOWED_ORIGINS`. Swagger của dự án đã cấu hình tự lấy CSRF trước auth POST. Không gửi Bearer hết hạn lên endpoint công khai hoặc `/refresh`: Spring vẫn có thể từ chối Bearer không hợp lệ dù endpoint cho phép truy cập công khai.

Hai cookie có vai trò khác nhau:

| Cookie | Mục đích | Thuộc tính |
| --- | --- | --- |
| `XSRF-TOKEN` | So khớp với header CSRF | Path `/`, SameSite=Lax, JavaScript có thể đọc, Secure theo cấu hình |
| `trekking_refresh` | Duy trì phiên và cấp lại access token | HttpOnly, Path `/api/v1/auth`, SameSite=Lax, Secure theo `AUTH_COOKIE_SECURE` |

Refresh token không nằm trong JSON. `/login`, `/google`, `/refresh` trả `Set-Cookie` để đặt/thay refresh cookie và `Cache-Control: no-store`. Phiên có tuổi thọ tuyệt đối 7 ngày; refresh không kéo dài mốc hết hạn. Access token mặc định sống 15 phút (`expiresIn` là số giây, tối đa 900 và bị giới hạn bởi thời gian phiên còn lại). Refresh cookie có Max-Age bằng thời gian phiên còn lại, không phải lúc nào cũng đủ 7 ngày. Local HTTP cần cấu hình cookie phù hợp như hướng dẫn trong [AUTH gốc](../AUTH.md).

Dạng JSON lỗi:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Email or password is incorrect",
  "fieldErrors": {}
}
```

Các lỗi chung có thể xảy ra trước hoặc trong xử lý endpoint:

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Trường đầu vào vi phạm ràng buộc; `fieldErrors` chứa lỗi theo tên trường |
| 400 | `INVALID_REQUEST` | Không đọc được JSON hoặc request body sai cấu trúc |
| 403 | `INVALID_CSRF` | POST thiếu/sai cặp CSRF cookie và header |
| 401 | `UNAUTHORIZED` | Thiếu xác thực ở `/me`, hoặc gửi Bearer không hợp lệ |
| 503 | `AUTH_UNAVAILABLE` | Lỗi truy cập PostgreSQL/Redis hoặc hạ tầng xác thực không sẵn sàng |

Ví dụ validation cho mật khẩu đăng ký (giá trị thông báo do validator quyết định):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "fieldErrors": {
    "password": "Password must contain at least 8 characters and at most 72 UTF-8 bytes"
  }
}
```

## 1. `GET /api/v1/auth/csrf`

**Mục đích:** bắt đầu hoặc lấy lại cặp cookie/header để client gọi auth POST. Không cần Bearer, CSRF, body, path hoặc query. Không tạo phiên đăng nhập.

Request:

```http
GET http://localhost:8080/api/v1/auth/csrf
```

Response **200 OK**, ví dụ:

```json
{
  "headerName": "X-XSRF-TOKEN",
  "token": "8b050e51-a49d-460b-a6f8-b19a6a32f667"
}
```

Server đặt cookie `XSRF-TOKEN` khi cần tạo token. Nếu client đã có cookie, hãy giữ và gửi lại. Token CSRF này không dùng được trong `Authorization` và không chứng minh người dùng đã đăng nhập.

## 2. `POST /api/v1/auth/register`

**Mục đích:** tạo tài khoản khách hàng với role `customer` và gửi OTP xác minh email. Client không được chọn role qua API này. Chưa đăng nhập được cho đến khi xác minh email.

**Header/cookie:** JSON và CSRF như phần chung; không cần Bearer. Thành công không cấp access token hay refresh cookie.

| Trường body | Bắt buộc | Kiểm tra |
| --- | --- | --- |
| `email` | Có | Email hợp lệ, không trống, tối đa 254 ký tự; trim và đổi thành chữ thường |
| `password` | Có | Không trống, ít nhất 8 Unicode code point, tối đa 72 byte UTF-8; không có yêu cầu bắt buộc chữ hoa/số/ký tự đặc biệt trong validator hiện tại |
| `fullName` | Có | Trim, không trống, tối đa 200 ký tự |
| `phone` | Không | Tối đa 30 ký tự; DTO không áp regex số điện thoại |

Request:

```http
POST http://localhost:8080/api/v1/auth/register
```

```json
{
  "email": "khach@example.com",
  "password": "DemoPassword123!",
  "fullName": "Nguyễn Văn An",
  "phone": "0900000000"
}
```

Response **201 Created**:

```json
{"message":"Account created; verify your email before logging in"}
```

Lỗi riêng: **409 `EMAIL_ALREADY_REGISTERED`** khi email đã tồn tại; **503 `MAIL_UNAVAILABLE`** nếu tạo tài khoản thành công nhưng gửi mail thất bại. Với `MAIL_UNAVAILABLE`, tài khoản đã lưu: sửa cấu hình mail rồi dùng `/resend-verification` sau cooldown. Không đăng ký lại cùng email.

## 3. `POST /api/v1/auth/verify-email`

**Mục đích:** sử dụng OTP xác minh email của tài khoản tự đăng ký. Không cấp phiên đăng nhập; sau thành công gọi `/login`.

**Header/cookie:** JSON + CSRF; không cần Bearer, không cấp refresh cookie.

| Trường body | Bắt buộc | Kiểm tra |
| --- | --- | --- |
| `email` | Có | Email hợp lệ, không trống, tối đa 254 ký tự; trim và lowercase |
| `otp` | Có | Chuỗi đúng 6 chữ số `[0-9]{6}`; giữ dạng chuỗi để không mất số 0 đầu |

Request:

```http
POST http://localhost:8080/api/v1/auth/verify-email
```

```json
{"email":"khach@example.com","otp":"123456"}
```

Response **200 OK**:

```json
{"message":"Email verified"}
```

Lỗi riêng: **400 `INVALID_OTP`** với message `OTP is invalid or expired` khi mã sai/hết hạn/đã dùng, không đúng mục đích hoặc tài khoản không đủ điều kiện. OTP dùng một lần, hết hạn sau 5 phút và bị hủy sau lần nhập sai thứ 5. Tài khoản mời chưa kích hoạt phải dùng `/activate-account`.

## 4. `POST /api/v1/auth/resend-verification`

**Mục đích:** yêu cầu OTP xác minh mới khi chưa xác minh email hoặc mail trước không đến.

**Header/cookie:** JSON + CSRF; không cần Bearer, không cấp refresh cookie.

Body chỉ có `email` bắt buộc: email hợp lệ, không trống, tối đa 254 ký tự; trim và lowercase.

Request:

```http
POST http://localhost:8080/api/v1/auth/resend-verification
```

```json
{"email":"khach@example.com"}
```

Response **202 Accepted**:

```json
{"message":"If the account is eligible, a verification code will be sent"}
```

202 không đảm bảo mail đã gửi hoặc email tồn tại. Server trả cùng thông báo cho email không tồn tại, tài khoản không đủ điều kiện, đã xác minh, yêu cầu quá nhanh hoặc lỗi gửi mail được xử lý trong service. Giới hạn mỗi email/mục đích: 1 yêu cầu/60 giây và 5 yêu cầu/giờ. Giới hạn áp dụng cả email chưa tồn tại; trường hợp này không trả 429. Mã mới thay mã cũ cho cùng mục đích.

## 5. `POST /api/v1/auth/login`

**Mục đích:** xác thực email/mật khẩu, tạo phiên, trả access JWT và đặt refresh cookie.

**Header/cookie:** JSON + CSRF; không cần Bearer.

| Trường body | Bắt buộc | Kiểm tra |
| --- | --- | --- |
| `email` | Có | Email hợp lệ, không trống, tối đa 254 ký tự; trim và lowercase |
| `password` | Có | Không trống, tối đa 256 ký tự theo DTO; mật khẩu trên 72 byte UTF-8 bị coi là sai thông tin đăng nhập trong service |

Request:

```http
POST http://localhost:8080/api/v1/auth/login
```

```json
{"email":"khach@example.com","password":"DemoPassword123!"}
```

Response **200 OK**, ví dụ (token là placeholder):

```json
{
  "accessToken": "<JWT-cua-ung-dung>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "id": 101,
    "email": "khach@example.com",
    "fullName": "Nguyễn Văn An",
    "phone": "0900000000",
    "roles": ["customer"]
  }
}
```

Server đặt `trekking_refresh`; client giữ cookie và dùng `accessToken` cho `/me` cùng các API bảo vệ khác. `account.id` là ID tài khoản, `roles` là danh sách role hiện tại dạng chữ thường; `phone` có thể `null`. JSON không trả password hoặc refresh token.

| HTTP | `code` | Ý nghĩa |
| --- | --- | --- |
| 401 | `INVALID_CREDENTIALS` | Sai email/mật khẩu |
| 403 | `ACCOUNT_UNAVAILABLE` | Tài khoản không ACTIVE |
| 403 | `EMAIL_NOT_VERIFIED` | Chưa xác minh email |
| 403 | `PASSWORD_RESET_REQUIRED` | Bắt buộc đổi/đặt lại mật khẩu trước khi đăng nhập |
| 429 | `TOO_MANY_ATTEMPTS` | Đã đạt 10 lần sai/email hoặc 100 lần sai/IP trong cửa sổ 15 phút |

## 6. `POST /api/v1/auth/google`

**Mục đích:** nhận Google ID token do frontend lấy từ Google Identity Services, xác minh và tạo phiên của ứng dụng. API nhận JSON, không phải URL callback chuyển hướng Google. Chỉ dùng access JWT của ứng dụng để gọi các API được bảo vệ.

**Header/cookie:** JSON + CSRF; không cần Bearer.

Body `idToken` bắt buộc, không trống, tối đa 16.384 ký tự. Phải là ID token Google thật, còn hiệu lực và có audience thuộc `GOOGLE_CLIENT_IDS` đã cấu hình.

Request:

```http
POST http://localhost:8080/api/v1/auth/google
```

```json
{"idToken":"<Google-ID-token-thuc-te-do-frontend-nhan>"}
```

Response **200 OK**, ví dụ:

```json
{
  "accessToken": "<JWT-cua-ung-dung>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "id": 102,
    "email": "khach.demo@gmail.com",
    "fullName": "Nguyễn Văn An",
    "phone": null,
    "roles": ["customer"]
  }
}
```

Đặt refresh cookie giống `/login`. Người dùng Google đã liên kết được tìm bằng `sub`, nên đổi email Google không tự đổi email/profile/role tại ứng dụng. Danh tính mới có thể tạo khách hàng đã xác minh hoặc liên kết tài khoản local cùng email đã xác minh, ACTIVE, không bị bắt đổi mật khẩu. Tự tạo/liên kết yêu cầu Gmail hoặc Google Workspace có claim `hd`; không phải mọi email có `email_verified=true` đều được chấp nhận.

| HTTP | `code` | Ý nghĩa |
| --- | --- | --- |
| 401 | `INVALID_GOOGLE_TOKEN` | ID token sai, hết hạn, sai issuer/audience/chữ ký hoặc thiếu claim hợp lệ |
| 403 | `GOOGLE_EMAIL_VERIFICATION_REQUIRED` | Danh tính chưa liên kết không có email Gmail/Workspace đủ điều kiện tự liên kết |
| 403 | `ACCOUNT_UNAVAILABLE`, `EMAIL_NOT_VERIFIED`, `PASSWORD_RESET_REQUIRED` | Tài khoản local không đủ điều kiện; Google không bỏ qua các kiểm tra này |
| 409 | `GOOGLE_ACCOUNT_CONFLICT` | Email đã liên kết với Google subject khác |
| 429 | `TOO_MANY_ATTEMPTS` | Email/IP đã đạt ngưỡng kiểm tra đăng nhập |
| 503 | `GOOGLE_AUTH_UNAVAILABLE` | Chưa cấu hình `GOOGLE_CLIENT_IDS` |
| 503 | `AUTH_UNAVAILABLE` | Không lấy được public key Google hoặc hạ tầng xác thực lỗi |

## 7. `POST /api/v1/auth/refresh`

**Mục đích:** dùng refresh cookie còn hiệu lực để lấy access token mới và xoay refresh token.

**Header/cookie:** CSRF bắt buộc; không cần Bearer, không có JSON body. Gửi cả `XSRF-TOKEN` và `trekking_refresh`. Không gửi refresh token dưới dạng JSON/header Authorization.

Request ví dụ:

```http
POST http://localhost:8080/api/v1/auth/refresh
X-XSRF-TOKEN: 8b050e51-a49d-460b-a6f8-b19a6a32f667
Cookie: XSRF-TOKEN=8b050e51-a49d-460b-a6f8-b19a6a32f667; trekking_refresh=<refresh-cookie-hien-tai>
```

Response **200 OK**, ví dụ:

```json
{
  "accessToken": "<JWT-moi-cua-ung-dung>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "id": 101,
    "email": "khach@example.com",
    "fullName": "Nguyễn Văn An",
    "phone": "0900000000",
    "roles": ["customer"]
  }
}
```

Server thay `trekking_refresh` bằng giá trị mới, giữ mốc hết hạn tuyệt đối của phiên. Refresh cookie nhận vào phải là chuỗi token server đã cấp; service kiểm tra định dạng 43 ký tự URL-safe và tra phiên trong Redis.

Lỗi riêng: **401 `INVALID_SESSION`** khi thiếu/sai cookie, phiên hết hạn/đã thu hồi, mật khẩu hoặc trạng thái tài khoản đã thay đổi, hoặc token đã bị sử dụng lại. Khi gặp AuthException 401, controller xóa refresh cookie bằng `Max-Age=0`. Chạy refresh tuần tự và lưu cookie mới sau mỗi lần thành công: dùng lại token cũ hoặc hai request đồng thời với cùng token có thể thu hồi cả phiên, buộc đăng nhập lại.

## 8. `POST /api/v1/auth/logout`

**Mục đích:** thu hồi phiên gắn với refresh cookie và xóa cookie ở client. Access token của phiên đã thu hồi cũng mất hiệu lực khi được kiểm tra ở request tiếp theo.

**Header/cookie:** CSRF bắt buộc; không cần Bearer, không có request body. Cookie `trekking_refresh` được dùng để xác định phiên cần thu hồi; cookie CSRF vẫn phải gửi ngay cả khi không có refresh cookie.

Request ví dụ:

```http
POST http://localhost:8080/api/v1/auth/logout
X-XSRF-TOKEN: 8b050e51-a49d-460b-a6f8-b19a6a32f667
Cookie: XSRF-TOKEN=8b050e51-a49d-460b-a6f8-b19a6a32f667; trekking_refresh=<refresh-cookie-hien-tai>
```

Response **204 No Content**, **không có JSON body**. Trả `Set-Cookie` xóa `trekking_refresh` với `Max-Age=0`. Thiếu hoặc sai định dạng refresh cookie vẫn có thể trả 204; đây không phải API kiểm tra token hợp lệ. Lỗi CSRF và lỗi hạ tầng vẫn theo phần chung.

## 9. `POST /api/v1/auth/forgot-password`

**Mục đích:** yêu cầu mã OTP riêng cho việc đặt lại mật khẩu. Không đặt lại mật khẩu ngay và không cấp phiên.

**Header/cookie:** JSON + CSRF; không cần Bearer, không cấp refresh cookie.

Body chỉ có `email` bắt buộc: email hợp lệ, không trống, tối đa 254 ký tự; trim và lowercase.

Request:

```http
POST http://localhost:8080/api/v1/auth/forgot-password
```

```json
{"email":"khach@example.com"}
```

Response **202 Accepted**:

```json
{"message":"If the account is eligible, a verification code will be sent"}
```

202 không tiết lộ email tồn tại hay không và không đảm bảo gửi mail thành công. Cooldown 60 giây và 5 yêu cầu/giờ/email/mục đích giống gửi lại xác minh; bị giới hạn vẫn trả 202. Tài khoản mời chưa xác minh và đang bắt đổi mật khẩu không thể dùng luồng này để bỏ qua kích hoạt. Tài khoản Google đã tạo có thể dùng luồng này để đặt mật khẩu local.

## 10. `POST /api/v1/auth/reset-password`

**Mục đích:** dùng OTP đặt lại mật khẩu từ `/forgot-password`, xóa yêu cầu đổi mật khẩu và làm mất hiệu lực các phiên cũ của tài khoản. Không tự đăng nhập sau khi đặt lại.

**Header/cookie:** JSON + CSRF; không cần Bearer. Thành công xóa `trekking_refresh` trên client bằng `Max-Age=0`.

| Trường body | Bắt buộc | Kiểm tra |
| --- | --- | --- |
| `email` | Có | Email hợp lệ, không trống, tối đa 254 ký tự; trim và lowercase |
| `otp` | Có | Chuỗi đúng 6 chữ số, từ email đặt lại mật khẩu |
| `newPassword` | Có | Không trống, ít nhất 8 Unicode code point và tối đa 72 byte UTF-8 |

Request:

```http
POST http://localhost:8080/api/v1/auth/reset-password
```

```json
{"email":"khach@example.com","otp":"123456","newPassword":"NewDemoPassword123!"}
```

Response **200 OK**:

```json
{"message":"Password reset; please log in again"}
```

Lỗi riêng: **400 `INVALID_OTP`** khi mã sai/hết hạn/đã dùng/sai mục đích hoặc tài khoản không đủ điều kiện. OTP xác minh email không dùng để reset mật khẩu. OTP có thời hạn 5 phút, dùng một lần và tối đa 5 lần nhập sai. Sau thành công, dùng mật khẩu mới gọi `/login`. Việc reset không tự xác minh email chưa được xác minh.

## 11. `GET /api/v1/auth/me`

**Mục đích:** lấy hồ sơ tối thiểu và role hiện tại của tài khoản đã xác thực, thường dùng để khôi phục thông tin người dùng trên giao diện.

**Header/cookie:** bắt buộc Bearer access token do `/login`, `/google` hoặc `/refresh` của ứng dụng cấp. Không cần body, CSRF hoặc refresh cookie.

Request:

```http
GET http://localhost:8080/api/v1/auth/me
Authorization: Bearer <accessToken-cua-ung-dung>
```

Response **200 OK**, ví dụ:

```json
{
  "id": 101,
  "email": "khach@example.com",
  "fullName": "Nguyễn Văn An",
  "phone": "0900000000",
  "roles": ["customer"]
}
```

Khác response đăng nhập, đây là trực tiếp `AccountResponse`, không có lớp `account` bao ngoài, `accessToken` hay `expiresIn`. Role có thể gồm `customer`, `company_manager`, `guide`, `support_staff`, `content_staff`, `accountant`, `admin`; ví dụ không có nghĩa tài khoản được cấp mọi role. Server tra trạng thái phiên/account và role hiện tại, không chỉ kiểm chữ ký JWT. Thiếu/hết hạn token hoặc phiên không hợp lệ trả **401 `UNAUTHORIZED`** tại lớp bảo mật; hạ tầng xác thực không sẵn sàng trả **503 `AUTH_UNAVAILABLE`**.

## Luồng dùng API

| Trường hợp | Thứ tự |
| --- | --- |
| Khách hàng mới | `/csrf` → `/register` → lấy mã trong email → `/verify-email` → `/login` → `/me` |
| Không nhận mã xác minh | `/csrf` → `/resend-verification` sau cooldown → lấy OTP mới → `/verify-email` |
| Đăng nhập Google | Frontend lấy Google ID token → `/csrf` → `/google` → dùng app access token gọi `/me` |
| Access token hết hạn | `/csrf` nếu cần → `/refresh` với refresh cookie hiện tại → lưu access token và cookie mới → gọi lại API |
| Quên mật khẩu | `/csrf` → `/forgot-password` → lấy OTP reset → `/reset-password` → `/login` bằng mật khẩu mới |
| Đăng xuất | `/csrf` nếu cần → `/logout` → client xóa access token trong bộ nhớ |

CSRF dùng cho từng POST trong các luồng trên; không chỉ POST đầu tiên. Không cần gửi lại `/csrf` trước mọi request nếu vẫn giữ đúng cặp cookie/token; Swagger tự thực hiện bước đó. Nên dùng cookie jar trong Postman để giữ cả cookie CSRF và refresh.

## Phần OpenAPI chưa mô tả đầy đủ

Snapshot OpenAPI hiện có đủ 11 endpoint này và đúng success status/body. Tuy nhiên schema tự sinh chưa phản ánh `@ValidPassword`: `Register.password` và `ResetPassword.newPassword` trông như trường tùy chọn, không có độ dài trong spec, nhưng thực tế bắt buộc theo validator. Spec cũng không liệt kê đủ yêu cầu CSRF, `Set-Cookie`, cookie attributes và các lỗi nghiệp vụ/bảo mật. Các nội dung này ở trên được bổ sung từ mã nguồn, không phải thay đổi API.

Nguồn đối chiếu: `features/auth/controller/AuthController.java`, các record trong `features/auth/dto`, `AuthServiceImpl`, `AccountTransactions`, `GoogleAccountTransactions`, `GoogleTokenVerifierImpl`, `AuthValidation`, Redis Lua scripts, `core/config/SecurityConfig.java`, `GlobalExceptionHandler` và `SecurityErrorHandler`. Cấu hình mặc định TTL nằm trong `src/main/resources/application.properties`.

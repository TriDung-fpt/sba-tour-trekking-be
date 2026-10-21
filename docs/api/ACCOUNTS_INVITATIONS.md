# API tài khoản, phân quyền và lời mời

[Về trang tài liệu API](../API_DOCS_VI.md) · [OpenAPI đã xuất từ backend](openapi.json)

Tài liệu gồm **12 thao tác API**. URL gốc local: `http://localhost:8080`; các URL bên dưới đã có tiền tố `/api/v1`. Ví dụ dùng tài khoản, email, OTP và ID giả để minh họa; response thực tế phụ thuộc dữ liệu của bạn. Đối chiếu controller, DTO, service và OpenAPI ngày 21/10/2026; không gọi các API thay đổi dữ liệu để tạo ví dụ.

## Quy ước chung

**Nhóm header A — API cần đăng nhập:** `Authorization: Bearer <accessToken>`, `Accept: application/json`; thêm `Content-Type: application/json` khi có JSON body. Tài khoản gọi phải `active`, đã xác minh email, `mustChangePassword=false` và có phiên hợp lệ. Role và quyền được kiểm tra lại từ database. Các POST/PUT của nhóm này trong tài liệu không nằm dưới `/auth/`, nên cấu hình hiện tại không yêu cầu CSRF.

**Nhóm header B — kích hoạt tài khoản:** không cần bearer; cần `Content-Type: application/json`, cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN` tương ứng. Trước đó gọi `GET /api/v1/auth/csrf`, giữ cookie được trả về và lấy `token` trong response làm header. Public API vẫn chịu kiểm tra CSRF. Xem chi tiết đăng nhập và CSRF ở tài liệu nhóm Auth.

Các endpoint trong tài liệu này **không có query parameter**. Path `id`, `accountId`, `companyId` là số nguyên 64 bit bắt buộc. Controller không khai báo ràng buộc `> 0`; số không khớp tài nguyên thường trả `404`, chuỗi không chuyển được thành số trả `400 INVALID_REQUEST`. Body JSON là object, không bọc thêm `request`, `data` hay `payload`.

Lỗi chung có dạng:

```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account was not found",
  "fieldErrors": {}
}
```

| HTTP / code | Ý nghĩa |
|---|---|
| `400 VALIDATION_ERROR` | Thiếu/sai field DTO; `fieldErrors` chứa tên field và thông báo, ví dụ `{"reason":"must not be blank"}`. |
| `400 INVALID_REQUEST` | JSON hỏng, thiếu body bắt buộc hoặc path sai kiểu. |
| `401 UNAUTHORIZED` | Thiếu bearer hoặc token không hợp lệ ở API cần đăng nhập. |
| `401 INVALID_SESSION` | Phiên hết hiệu lực, tài khoản không còn đủ điều kiện. |
| `403 FORBIDDEN` | Không có quyền thực hiện; thường áp dụng cho endpoint admin. |
| `503 AUTH_UNAVAILABLE` | Database/Redis hoặc dịch vụ xác thực không khả dụng; gửi invitation có cách xử lý riêng bên dưới. |

Mỗi endpoint dưới đây liệt kê thêm lỗi nghiệp vụ cụ thể; các lỗi chung trên cũng áp dụng khi thích hợp.

## 1. `GET /api/v1/accounts/me/authorization`

**Mục đích:** lấy role, quyền được cấp trực tiếp và các công ty mà người đăng nhập có ngữ cảnh quản lý. Frontend dùng để hiển thị chức năng phù hợp; backend vẫn kiểm tra quyền khi gọi từng thao tác.

**Ai dùng:** mọi tài khoản đăng nhập hợp lệ, không yêu cầu role cụ thể. **Header:** A. Không path/query/body.

Request ví dụ:

```http
GET http://localhost:8080/api/v1/accounts/me/authorization
Authorization: Bearer <accessToken>
Accept: application/json
```

Response **`200 OK`**, ví dụ một company manager:

```json
{
  "accountId": 101,
  "roles": ["company_manager"],
  "permissions": [],
  "companies": [{"companyId": 12, "status": "approved", "memberRole": "owner"}]
}
```

| Field response | Ý nghĩa |
|---|---|
| `accountId` | ID người đang gọi. |
| `roles` | Mảng role hiện tại, xếp theo tên. |
| `permissions` | Mảng tên quyền toàn cục, xếp theo tên. Admin có `ACCOUNT_READ`, `ACCOUNT_BLOCK`, `ACCOUNT_ROLES_WRITE`, `COMPANY_REVIEW`, `POLICY_MANAGE`. Accountant chỉ có quyền tài chính đã được cấp riêng. |
| `companies[].companyId` | Công ty có membership đang hoạt động của company manager. |
| `companies[].status` | Trạng thái công ty; chỉ `approved` mới đủ điều kiện vận hành. |
| `companies[].memberRole` | `owner` hoặc `manager`. |

`COMPANY_OPERATE` không được liệt kê trong mảng quyền toàn cục; dùng `companies` và điều kiện của API công ty để xác định phạm vi. Role `admin` tự nó không cho phép thao tác thay người quản lý công ty hoặc tự có quyền tài chính. Lỗi thường gặp: `401 UNAUTHORIZED`, `401 INVALID_SESSION`.

## 2. `GET /api/v1/admin/accounts/{id}`

**Mục đích:** quản trị viên xem thông tin và trạng thái một tài khoản. **Ai dùng:** `admin` (`ACCOUNT_READ`). **Header:** A. Path `id`: ID tài khoản cần xem; không query/body.

```http
GET http://localhost:8080/api/v1/admin/accounts/205
Authorization: Bearer <adminAccessToken>
Accept: application/json
```

Response **`200 OK`**:

```json
{
  "id": 205,
  "email": "customer@example.com",
  "fullName": "Nguyễn An",
  "phone": "0900000205",
  "roles": ["customer"],
  "status": "active",
  "blockedReason": null,
  "blockedBy": null,
  "mustChangePassword": false,
  "emailVerifiedAt": "2026-10-20T08:30:00Z"
}
```

Đây là schema **AdminAccountResponse**, cũng dùng cho block, unblock và replace roles:

| Field | Ý nghĩa |
|---|---|
| `id`, `email`, `fullName`, `phone` | ID, email, tên và điện thoại; `phone` có thể null. |
| `roles` | Toàn bộ role, xếp theo tên. |
| `status` | `active`, `blocked` hoặc `disabled`. |
| `blockedReason`, `blockedBy` | Lý do chặn và ID người chặn, có thể null. |
| `mustChangePassword` | Có bắt buộc đổi/đặt mật khẩu không. Invitation mới có giá trị true. |
| `emailVerifiedAt` | Thời điểm xác minh ISO 8601, có thể null. |

Lỗi: `403 FORBIDDEN` nếu không phải admin; `404 ACCOUNT_NOT_FOUND` nếu ID không tồn tại. API này xem được tài khoản admin và chính người gọi; giới hạn tự sửa chỉ áp dụng thao tác thay đổi.

## 3. `POST /api/v1/admin/accounts/{id}/block`

**Mục đích:** chặn tài khoản đang `active`; thu hồi hiệu lực các phiên cũ bằng cách tăng phiên bản phiên. **Ai dùng:** `admin` (`ACCOUNT_BLOCK`). **Header:** A. Path `id`: tài khoản bị chặn; không query.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `reason` | Có | Chuỗi không trắng sau `strip()`, tối đa 2.000 ký tự; lưu audit. |

```http
POST http://localhost:8080/api/v1/admin/accounts/205/block
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"reason":"Tạm chặn để xác minh hành vi vi phạm"}
```

Response **`200 OK`**:

```json
{
  "id": 205,
  "email": "customer@example.com",
  "fullName": "Nguyễn An",
  "phone": "0900000205",
  "roles": ["customer"],
  "status": "blocked",
  "blockedReason": "Tạm chặn để xác minh hành vi vi phạm",
  "blockedBy": 1,
  "mustChangePassword": false,
  "emailVerifiedAt": "2026-10-20T08:30:00Z"
}
```

Lỗi: `403 SELF_ACCOUNT_MUTATION_FORBIDDEN` nếu tự chặn; `403 ADMIN_ACCOUNT_PROTECTED` nếu đích có role admin; `404 ACCOUNT_NOT_FOUND`; `409 INVALID_ACCOUNT_TRANSITION` nếu đích không `active`; `400 VALIDATION_ERROR` với reason sai DTO (`INVALID_REASON` là kiểm tra bổ sung tại service); `403 FORBIDDEN` nếu người gọi thiếu quyền.

## 4. `POST /api/v1/admin/accounts/{id}/unblock`

**Mục đích:** chuyển tài khoản `blocked` về `active`, xóa `blockedReason` và `blockedBy`; người dùng phải đăng nhập lại vì phiên cũ đã bị vô hiệu lúc chặn. **Ai dùng:** `admin` (`ACCOUNT_BLOCK`). **Header:** A. Path `id`: tài khoản cần bỏ chặn; không query.

Body `reason`: bắt buộc, chuỗi không trắng sau `strip()`, tối đa 2.000 ký tự, dùng cho audit.

```http
POST http://localhost:8080/api/v1/admin/accounts/205/unblock
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"reason":"Đã xác minh và cho phép hoạt động trở lại"}
```

Response **`200 OK`**:

```json
{
  "id": 205,
  "email": "customer@example.com",
  "fullName": "Nguyễn An",
  "phone": "0900000205",
  "roles": ["customer"],
  "status": "active",
  "blockedReason": null,
  "blockedBy": null,
  "mustChangePassword": false,
  "emailVerifiedAt": "2026-10-20T08:30:00Z"
}
```

Lỗi: `403 SELF_ACCOUNT_MUTATION_FORBIDDEN`, `403 ADMIN_ACCOUNT_PROTECTED`, `404 ACCOUNT_NOT_FOUND`, `409 INVALID_ACCOUNT_TRANSITION` nếu đích không `blocked`, `400 VALIDATION_ERROR` / kiểm tra service `400 INVALID_REASON`, `403 FORBIDDEN`.

## 5. `PUT /api/v1/admin/accounts/{id}/roles`

**Mục đích:** **thay toàn bộ tập role**. Role hiện có mà không có trong request sẽ bị gỡ; đây không phải thao tác cộng thêm role. Ví dụ đích có `customer` và `accountant`, gửi chỉ `customer` sẽ gỡ `accountant`.

**Ai dùng:** `admin` (`ACCOUNT_ROLES_WRITE`). **Header:** A. Path `id`: tài khoản cần sửa; không query.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `roles` | Có | Mảng 1–6 chuỗi khác nhau, không trắng; đúng chữ thường trong `customer`, `company_manager`, `guide`, `support_staff`, `content_staff`, `accountant`. Không được cấp `admin`. |
| `reason` | Có | Không trắng sau `strip()`, tối đa 2.000 ký tự. |

```http
PUT http://localhost:8080/api/v1/admin/accounts/205/roles
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"roles":["customer","support_staff"],"reason":"Phân công hỗ trợ khách hàng"}
```

Response **`200 OK`**:

```json
{
  "id": 205,
  "email": "customer@example.com",
  "fullName": "Nguyễn An",
  "phone": "0900000205",
  "roles": ["customer", "support_staff"],
  "status": "active",
  "blockedReason": null,
  "blockedBy": null,
  "mustChangePassword": false,
  "emailVerifiedAt": "2026-10-20T08:30:00Z"
}
```

Cấp role `company_manager` hoặc `guide` không tự tạo membership/hồ sơ guide; các API có phạm vi công ty vẫn kiểm tra dữ liệu liên kết. Cấp `accountant` không tự cấp quyền tài chính; gỡ role `accountant` sẽ xóa các financial grant qua trigger database của migration 004. Lỗi: `400 INVALID_ROLES` khi tên role sai, trùng hoặc có `admin`; `400 VALIDATION_ERROR` khi thiếu field/vi phạm kích thước; `400 INVALID_REASON` tại service; `403 SELF_ACCOUNT_MUTATION_FORBIDDEN`, `403 ADMIN_ACCOUNT_PROTECTED`, `404 ACCOUNT_NOT_FOUND`, `403 FORBIDDEN`.

## 6. `GET /api/v1/admin/accounts/{accountId}/financial-permissions`

**Mục đích:** xem những quyền tài chính đã cấp riêng cho một tài khoản. **Ai dùng:** `admin` (`ACCOUNT_READ`), không phải mọi accountant. **Header:** A. Path `accountId`: ID tài khoản; không query/body.

```http
GET http://localhost:8080/api/v1/admin/accounts/301/financial-permissions
Authorization: Bearer <adminAccessToken>
Accept: application/json
```

Response **`200 OK`**:

```json
{"accountId":301,"permissions":["FINANCE_READ","SETTLEMENT_PREPARE"]}
```

`accountId` là tài khoản được xem; `permissions` là mảng quyền đã lưu, xếp theo tên, có thể rỗng. Lỗi: `403 FORBIDDEN`, `404 ACCOUNT_NOT_FOUND`.

## 7. `PUT /api/v1/admin/accounts/{accountId}/financial-permissions`

**Mục đích:** **thay toàn bộ tập quyền tài chính** và ghi audit; quyền vắng trong request bị thu hồi. Gửi `[]` để thu hồi hết.

**Ai dùng:** `admin` (`ACCOUNT_ROLES_WRITE`). **Header:** A. Path `accountId`: tài khoản được cấp/thu hồi; không query. Không tự sửa chính mình và không sửa đích có role admin. Khi cấp tập không rỗng, đích phải có role `accountant`, `status=active`, đã xác minh email và `mustChangePassword=false`; invitation chưa kích hoạt chưa được nhận quyền. Tập rỗng không yêu cầu đích đủ điều kiện accountant.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `permissions` | Có | Mảng tối đa 7 chuỗi không trắng, khác nhau, đúng tên chữ hoa trong bảng dưới; cho phép `[]`, không cho null. |
| `reason` | Có | Không trắng sau `strip()`, tối đa 2.000 ký tự. |

| Tên quyền hỗ trợ | Ý nghĩa nghiệp vụ của grant |
|---|---|
| `FINANCE_READ` | Quyền xem dữ liệu tài chính trong các luồng tài chính có kiểm tra quyền. |
| `PAYMENT_RECORD` | Ghi nhận thanh toán. |
| `REFUND_APPROVE` | Phê duyệt hoàn tiền. |
| `REFUND_RECORD` | Ghi nhận đã hoàn tiền. |
| `SETTLEMENT_PREPARE` | Chuẩn bị đối soát/quyết toán. |
| `SETTLEMENT_APPROVE` | Phê duyệt đối soát/quyết toán. |
| `PAYOUT_RECORD` | Ghi nhận chi trả. |

**Phân tách trách nhiệm:** một tài khoản chỉ có tối đa **một** trong `SETTLEMENT_PREPARE`, `SETTLEMENT_APPROVE`, `PAYOUT_RECORD`; không có đồng thời `REFUND_APPROVE` và `REFUND_RECORD`. Có quyền trong bảng không có nghĩa mọi luồng ghi nhận tiền đã có endpoint HTTP; bảng mô tả các giá trị grant backend hỗ trợ.

```http
PUT http://localhost:8080/api/v1/admin/accounts/301/financial-permissions
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"permissions":["FINANCE_READ","SETTLEMENT_PREPARE"],"reason":"Phân công chuẩn bị đối soát"}
```

Response **`200 OK`**:

```json
{"accountId":301,"permissions":["FINANCE_READ","SETTLEMENT_PREPARE"]}
```

Lỗi: `400 INVALID_FINANCIAL_PERMISSIONS` khi sai tên/trùng/tên không thuộc nhóm tài chính; `400 CONFLICTING_FINANCIAL_DUTIES` khi vi phạm phân tách; `400 VALIDATION_ERROR` hoặc kiểm tra service `400 INVALID_REASON`; `409 FINANCIAL_ACCOUNT_INELIGIBLE` khi đích không đủ điều kiện; `403 SELF_ACCOUNT_MUTATION_FORBIDDEN`, `403 ADMIN_ACCOUNT_PROTECTED`, `404 ACCOUNT_NOT_FOUND`, `403 FORBIDDEN`.

## Cách hoạt động của lời mời

Admin tạo nhân viên, hoặc quản lý công ty tạo guide → backend tạo tài khoản có mật khẩu ngẫu nhiên, `active`, chưa xác minh email, `mustChangePassword=true` → gửi OTP kích hoạt qua email → người nhận gọi `activate-account` để đặt mật khẩu → đăng nhập bằng mật khẩu mới. Không có mật khẩu tạm/OTP trong response API. OTP invitation là luồng riêng; không dùng verify-email hoặc reset-password thay cho kích hoạt lời mời.

OTP gồm 6 chữ số, hết hạn sau **300 giây**, dùng một lần, tối đa **5 lần kiểm tra** cho mỗi mã. Mỗi email có cooldown gửi **60 giây**, tối đa **5 lần gửi trong cửa sổ 3.600 giây** kể từ lượt đầu. Gửi lại thành công thay thế mã cũ; thay đổi dữ liệu ngữ cảnh tài khoản/role/công ty có thể làm challenge cũ không còn hợp lệ. Guide cần hồ sơ active và công ty approved lúc resend/kích hoạt.

Tạo tài khoản được commit **trước** gửi email/Redis. Nếu SMTP/Redis lỗi khi phát mã, API vẫn có thể thành công với `deliveryStatus="retry_required"`; tài khoản đã tồn tại, nên dùng **resend**, không tạo lại. `deliveryStatus="sent"` chỉ xác nhận bước gửi mail không báo lỗi, không bảo đảm email đã vào inbox. Khi giới hạn gửi bị chạm, `429 INVITATION_RATE_LIMITED` cũng có thể xảy ra sau khi tài khoản mới đã được tạo. SMTP lỗi vẫn giữ quota/cooldown gửi.

Response lời mời dùng cùng schema:

| Field | Ý nghĩa |
|---|---|
| `accountId` | ID tài khoản mới/đang chờ kích hoạt; dùng ID này cho resend, không phải guide ID. |
| `email` | Email người được mời, chuẩn hóa trim + chữ thường. |
| `role` | Role nhân viên được chọn hoặc `guide`. |
| `companyId` | ID công ty của guide; null với nhân viên hệ thống. |
| `activationRequired` | Luôn true trong các response invitation này. |
| `deliveryStatus` | `sent` hoặc `retry_required`. |

## 8. `POST /api/v1/admin/staff/invitations`

**Mục đích:** tạo tài khoản nhân viên và gửi mã kích hoạt. **Ai dùng:** `admin` (`ACCOUNT_ROLES_WRITE`). **Header:** A. Không path/query.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `email` | Có | Email hợp lệ, không trắng, tối đa 254 ký tự; trim, chuyển chữ thường; chưa có tài khoản dùng email này. |
| `fullName` | Có | Không trắng, tối đa 200 ký tự sau trim. |
| `phone` | Không | Chuỗi tối đa 30 ký tự hoặc null; không có regex số điện thoại. |
| `role` | Có | Chính xác `support_staff`, `content_staff` hoặc `accountant`. |

```http
POST http://localhost:8080/api/v1/admin/staff/invitations
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{"email":"finance@example.com","fullName":"Trần Bình","phone":"0900000301","role":"accountant"}
```

Response **`201 Created`**:

```json
{"accountId":301,"email":"finance@example.com","role":"accountant","companyId":null,"activationRequired":true,"deliveryStatus":"sent"}
```

Lỗi: `409 EMAIL_ALREADY_REGISTERED` khi email đã tồn tại (kể cả invitation cũ); `400 VALIDATION_ERROR` nếu sai field/role (service cũng có `400 INVALID_INVITATION_ROLE`); `403 FORBIDDEN`; `429 INVITATION_RATE_LIMITED`. Lỗi gửi mã được biểu diễn bằng `deliveryStatus="retry_required"` khi được xử lý; lỗi truy cập dữ liệu khác có thể trả `503 AUTH_UNAVAILABLE`.

## 9. `POST /api/v1/admin/staff/invitations/{accountId}/resend`

**Mục đích:** phát mã kích hoạt mới cho nhân viên đã có lời mời. **Ai dùng:** `admin` (`ACCOUNT_ROLES_WRITE`). **Header:** A. Path `accountId`: ID tài khoản nhân viên; không query/body.

Đích phải `active`, chưa xác minh email, `mustChangePassword=true`, có audit tạo invitation và đúng **một** role thuộc `support_staff`, `content_staff`, `accountant`.

```http
POST http://localhost:8080/api/v1/admin/staff/invitations/301/resend
Authorization: Bearer <adminAccessToken>
Accept: application/json
```

Response **`202 Accepted`**:

```json
{"accountId":301,"email":"finance@example.com","role":"accountant","companyId":null,"activationRequired":true,"deliveryStatus":"sent"}
```

Lỗi: `409 INVITATION_NOT_PENDING` khi đã kích hoạt/bị chặn/không có invitation đang chờ; `404 RESOURCE_NOT_FOUND` khi không có tài khoản hoặc role/phạm vi không khớp (ví dụ dùng endpoint nhân viên để resend guide); `429 INVITATION_RATE_LIMITED`; `403 FORBIDDEN`. Gửi lỗi có thể trả `202` với `retry_required`; lỗi dữ liệu khác có thể trả `503 AUTH_UNAVAILABLE`.

## 10. `POST /api/v1/companies/{companyId}/guides/invitations`

**Mục đích:** tạo tài khoản có role `guide`, hồ sơ guide gắn với công ty và gửi OTP kích hoạt. **Ai dùng:** có role toàn cục `company_manager`, membership active tại đúng `companyId` với `memberRole=owner` hoặc `manager`, công ty `approved` (`COMPANY_OPERATE`). Admin đơn thuần không có quyền thay thế các điều kiện này. **Header:** A. Path `companyId`: công ty nhận guide; không query.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `email` | Có | Không trắng, email hợp lệ, tối đa 254 ký tự; trim + chữ thường; chưa tồn tại. |
| `fullName` | Có | Không trắng, tối đa 200 ký tự sau trim. |
| `phone` | Không | Tối đa 30 ký tự hoặc null; không có regex điện thoại. |
| `bio` | Không | Tiểu sử guide, tối đa 10.000 ký tự hoặc null. |

Không gửi `role`/`companyId` trong body: role được ấn định `guide`, công ty lấy từ path.

```http
POST http://localhost:8080/api/v1/companies/12/guides/invitations
Authorization: Bearer <companyManagerAccessToken>
Content-Type: application/json

{"email":"guide@example.com","fullName":"Lê Chi","phone":"0900000401","bio":"Hướng dẫn viên trekking tại Đà Lạt"}
```

Response **`201 Created`**:

```json
{"accountId":401,"email":"guide@example.com","role":"guide","companyId":12,"activationRequired":true,"deliveryStatus":"sent"}
```

Lỗi: `404 RESOURCE_NOT_FOUND` khi công ty không tồn tại/chưa approved, không có membership phù hợp hoặc người gọi không có role company_manager; `409 EMAIL_ALREADY_REGISTERED`; `400 VALIDATION_ERROR`; `429 INVITATION_RATE_LIMITED`. Lỗi gửi mã có thể trả `201` với `retry_required`; lỗi dữ liệu khác có thể trả `503 AUTH_UNAVAILABLE`.

## 11. `POST /api/v1/companies/{companyId}/guides/invitations/{accountId}/resend`

**Mục đích:** gửi mã mới cho tài khoản guide được mời vào công ty. **Ai dùng:** `company_manager` + membership active owner/manager tại công ty approved (`COMPANY_OPERATE`). **Header:** A. Path `companyId`: công ty; `accountId`: ID tài khoản guide, không phải ID hồ sơ guide. Không query/body.

Đích phải `active`, chưa xác minh, `mustChangePassword=true`, có audit invitation; đúng một role `guide`, hồ sơ guide active thuộc đúng công ty approved trong path.

```http
POST http://localhost:8080/api/v1/companies/12/guides/invitations/401/resend
Authorization: Bearer <companyManagerAccessToken>
Accept: application/json
```

Response **`202 Accepted`**:

```json
{"accountId":401,"email":"guide@example.com","role":"guide","companyId":12,"activationRequired":true,"deliveryStatus":"sent"}
```

Lỗi: `404 RESOURCE_NOT_FOUND` khi thiếu phạm vi công ty, sai tài khoản/role, guide không active hoặc không thuộc công ty; `409 INVITATION_NOT_PENDING` khi tài khoản không còn chờ kích hoạt; `429 INVITATION_RATE_LIMITED`. Gửi lỗi có thể trả `202` với `retry_required`; lỗi dữ liệu khác có thể trả `503 AUTH_UNAVAILABLE`.

## 12. `POST /api/v1/auth/activate-account`

**Mục đích:** người được mời xác nhận OTP, đặt mật khẩu, xác minh email, bỏ cờ bắt đổi mật khẩu và làm mất hiệu lực phiên cũ. Không tự đăng nhập; sau thành công gọi login.

**Ai dùng:** public, người giữ OTP email invitation; không cần đăng nhập. **Header:** B (cookie + header CSRF bắt buộc). Không path/query. Đích phải là invitation đang chờ như trên; guide còn phải active và công ty approved. Người chưa có invitation không thể dùng API này để tự tạo tài khoản.

| Body field | Bắt buộc | Ràng buộc |
|---|---|---|
| `email` | Có | Email hợp lệ, không trắng, tối đa 254 ký tự; trim + chữ thường. |
| `otp` | Có | Chuỗi đúng 6 chữ số `[0-9]{6}`; giữ kiểu string để không mất số 0 đầu. |
| `newPassword` | Có | Không null/trắng, ít nhất 8 Unicode code point, tối đa 72 byte UTF-8; backend không yêu cầu chữ hoa/số/ký tự đặc biệt bằng regex. |

Trước request dưới đây, gọi `GET http://localhost:8080/api/v1/auth/csrf`, giữ cookie và thay `<csrfToken>` bằng token được trả về. Mã `012345` chỉ là ví dụ, phải thay bằng OTP mới nhất trong email.

```http
POST http://localhost:8080/api/v1/auth/activate-account
Content-Type: application/json
Cookie: XSRF-TOKEN=<csrfToken>
X-XSRF-TOKEN: <csrfToken>

{"email":"finance@example.com","otp":"012345","newPassword":"ExamplePass2026!"}
```

Response **`200 OK`**:

```json
{"message":"Account activated; sign in with your new password"}
```

Lỗi: `403 INVALID_CSRF` khi thiếu/sai cookie/header; `400 VALIDATION_ERROR` khi sai định dạng field hoặc mật khẩu; `400 INVALID_OTP` khi mã sai/hết hạn/đã dùng, quá số lần thử, email không có tài khoản, không còn invitation pending hoặc ngữ cảnh invitation không hợp lệ; `503 AUTH_UNAVAILABLE` khi không truy cập được Redis/database. API cố ý trả chung `INVALID_OTP` thay vì tiết lộ tài khoản nào tồn tại/chưa kích hoạt.

## Nguồn và phạm vi đối chiếu

Các method, URL, DTO và mã thành công khớp bản [OpenAPI](openapi.json): 7 thao tác account/authorization và 5 thao tác invitation. OpenAPI hiện chủ yếu mô tả success schema; chưa thể hiện đầy đủ điều kiện role/phạm vi, lỗi nghiệp vụ, CSRF cho kích hoạt, giới hạn OTP và các ràng buộc nghiệp vụ quyền tài chính. Tài liệu bổ sung các chi tiết này từ code, không coi việc OpenAPI thiếu mô tả là endpoint có quyền public.

Nguồn chính: `features/account/controller`, `features/account/dto`, `AccountAdministrationServiceImpl`, `FinancialPermissionServiceImpl`, `features/invitation/controller`, `features/invitation/dto`, `InvitationServiceImpl`, `core/security/authorization/impl/AuthorizationServiceImpl`, `core/config/SecurityConfig`, `core/exception/GlobalExceptionHandler`, `redis/invitation-*.lua`. Ví dụ JSON là dữ liệu tổng hợp; không phải response từ thao tác thay đổi tài khoản thật.

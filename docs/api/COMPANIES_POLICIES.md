# API công ty, chính sách và thỏa thuận hợp tác

[Về mục lục](../API_DOCS_VI.md) · [OpenAPI đang chụp](openapi.json)

Tài liệu gồm **18 API**: 7 API quản lý hồ sơ công ty, 4 API duyệt công ty của admin, 2 API chính sách và 5 API thỏa thuận. Lời mời hướng dẫn viên được mô tả trong tài liệu tài khoản/lời mời ở mục lục.

Base URL local: `http://localhost:8080`. Ghép URL này với đường dẫn `/api/v1/...` bên dưới. Toàn bộ ID, email, mã OTP và nội dung trong ví dụ là dữ liệu minh họa, không phải tài khoản hay kết quả gọi thật. Ngày giờ dùng ISO 8601 có múi giờ; giá trị `null` trong response là hợp lệ. Request/response JSON dùng `Content-Type: application/json`, trừ API upload/download giấy phép.

## Cách sử dụng và quyền truy cập

Mọi API trong tài liệu này cần header `Authorization: Bearer <accessToken>`. Tài khoản phải `active`, đã xác minh email và không còn bị yêu cầu đổi mật khẩu. Tên role trả về API viết thường (`customer`, `company_manager`, `admin`); mã permission viết hoa.

| Ký hiệu quyền trong tài liệu | Điều kiện thực tế phía server |
| --- | --- |
| Khách hàng | Role `customer`; có thể đồng thời có role khác |
| Đăng nhập | Bất kỳ tài khoản hợp lệ theo điều kiện chung ở trên |
| Đọc công ty | Role `company_manager` **và** membership đang active, `owner` hoặc `manager`, đúng công ty; permission `COMPANY_READ` |
| Quản lý công ty | Cùng điều kiện membership trên; permission `COMPANY_MANAGE` |
| Duyệt công ty | Role `admin`; permission `COMPANY_REVIEW` |
| Quản lý chính sách | Role `admin`; permission `POLICY_MANAGE` |
| Chủ công ty ký | Quyền quản lý công ty, membership phải là `owner`, công ty phải `approved` |

Admin không tự động được truy cập đường dẫn quản lý công ty của thành viên; dùng `/api/v1/admin/companies/...` để duyệt. Tài khoản không có role/membership phù hợp với công ty thường nhận `404 RESOURCE_NOT_FOUND` nhằm giới hạn lộ thông tin. Thành viên `manager` có quyền quản lý hồ sơ nhưng khi ký sẽ nhận `403 AGREEMENT_OWNER_REQUIRED`.

Các tham số path `id`, `companyId`, `policyId`, `agreementId` là số nguyên 64 bit. Controller không gắn validation dương cho path; ID không tồn tại trả lỗi tìm kiếm tương ứng; path không chuyển được thành số trả `400 INVALID_REQUEST`. Body có `policyVersionId`/`companyId` có quy tắc số dương riêng được ghi bên dưới.

Luồng công ty: khách hàng tạo hồ sơ → tải giấy phép lên → admin duyệt → chủ công ty xem chính sách có hiệu lực → xin OTP → xác nhận thỏa thuận. Cập nhật hồ sơ hoặc giấy phép của công ty đang `approved` đưa công ty về `pending` và xóa quyết định duyệt cũ. Công ty `rejected` cần gọi resubmit sau khi chỉnh sửa; công ty `suspended` không được sửa hoặc tải giấy phép lên. Người có phạm vi truy cập vẫn có thể tải giấy phép hiện có về qua GET.

## Lỗi dùng chung

Lỗi ứng dụng có cấu trúc `ApiError`, không bọc trong `data`:

```json
{"code":"COMPANY_STATE_CONFLICT","message":"Company state does not allow this action","fieldErrors":{}}
```

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Thiếu bearer token hoặc token không hợp lệ ở security filter |
| 401 | `INVALID_SESSION` | Phiên/tài khoản không còn hợp lệ khi kiểm tra trong service |
| 403 | `FORBIDDEN` | Thiếu role admin hoặc role customer ở hành động yêu cầu role đó |
| 404 | `RESOURCE_NOT_FOUND` | Không có phạm vi công ty hoặc tài nguyên chính sách/thỏa thuận không truy cập được |
| 400 | `VALIDATION_ERROR` | Body vi phạm constraint; `fieldErrors` ánh xạ tên field sang thông báo |
| 400 | `INVALID_REQUEST` | JSON sai, thiếu body, query/path sai kiểu, thiếu trường multipart bắt buộc hoặc phân trang sai |
| 503 | `AUTH_UNAVAILABLE` | Lỗi truy cập dữ liệu/dịch vụ xác thực |

Ví dụ lỗi validation; thông báo có thể thay đổi theo locale của Bean Validation:

```json
{"code":"VALIDATION_ERROR","message":"Invalid request","fieldErrors":{"name":"must not be blank"}}
```

Các mục lỗi ở từng endpoint bổ sung cho bảng lỗi chung. Swagger snapshot chủ yếu chỉ mô tả response thành công, chưa liệt kê đầy đủ các lỗi này.

## Hồ sơ công ty

### Body dùng cho tạo/cập nhật: `CompanyProfileRequest`

| Field | Bắt buộc | Validation/ý nghĩa |
| --- | --- | --- |
| `name` | Có | Không trắng, tối đa 250 ký tự; tên công ty |
| `taxCode` | Có | Không trắng, tối đa 50 ký tự, chỉ `[A-Za-z0-9-]+`; mã số thuế duy nhất |
| `representativeName` | Có | Không trắng, tối đa 200 ký tự; người đại diện |
| `contactEmail` | Có | Không trắng, đúng định dạng email, tối đa 254 ký tự |
| `contactPhone` | Có | Không trắng, tối đa 30 ký tự; không có regex số điện thoại bổ sung |
| `address` | Có | Không trắng, tối đa 2.000 ký tự |
| `description` | Không | `null` hoặc tối đa 10.000 ký tự |

Server trim các chuỗi, đổi `taxCode` thành chữ hoa và `contactEmail` thành chữ thường. Validation thực hiện trước khi trim. Body không nhận trạng thái, người duyệt hoặc URL giấy phép. Dùng API multipart riêng để cung cấp giấy phép.

Response `CompanyResponse` gồm toàn bộ hồ sơ, `status`, `licenseAvailable`, `licenseUrl`, `approvedBy`, `approvedAt`, `decisionReason`, `createdAt`, `updatedAt`. `approvedBy`/`approvedAt` được dùng cho quyết định approve/reject/suspend, nên không được hiểu rằng cứ có hai field này thì công ty đã được chấp thuận. `licenseAvailable: true` phản ánh đang có tham chiếu file; tải file vẫn có thể trả 404 nếu file lưu trữ bị mất.

### POST /api/v1/companies

**Mục đích:** khách hàng đăng ký công ty mới. **Quyền:** Khách hàng. Tạo membership `owner` active cho người gọi và bổ sung role `company_manager` nếu chưa có. Trạng thái ban đầu `pending`. Không có query/path.

Request JSON:

```json
{
  "name": "Công ty Trek Mẫu",
  "taxCode": "0312345678",
  "representativeName": "Nguyễn Minh An",
  "contactEmail": "contact@trek.example",
  "contactPhone": "0900000000",
  "address": "10 Đường Mẫu, TP.HCM",
  "description": "Tổ chức tour trekking."
}
```

Response **201 Created**:

```json
{
  "id": 12,
  "name": "Công ty Trek Mẫu",
  "taxCode": "0312345678",
  "representativeName": "Nguyễn Minh An",
  "contactEmail": "contact@trek.example",
  "contactPhone": "0900000000",
  "address": "10 Đường Mẫu, TP.HCM",
  "description": "Tổ chức tour trekking.",
  "status": "pending",
  "licenseAvailable": false,
  "licenseUrl": null,
  "approvedBy": null,
  "approvedAt": null,
  "decisionReason": null,
  "createdAt": "2026-10-21T10:00:00Z",
  "updatedAt": "2026-10-21T10:00:00Z"
}
```

**Lỗi riêng:** `409 COMPANY_TAX_CODE_EXISTS` khi mã số thuế đã đăng ký. Thiếu role customer trả `403 FORBIDDEN`. **Sai khác OpenAPI:** snapshot ghi 200 cho API này; controller thực sự dùng `ResponseEntity.status(CREATED)`, nên status đúng là **201**.

### GET /api/v1/companies

**Mục đích:** xem các công ty mà mình quản lý, không phải danh bạ công khai. **Quyền:** Đăng nhập; chỉ role `company_manager` mới có kết quả thuộc membership active owner/manager. Người hợp lệ chưa có role này nhận trang rỗng, không nhận 403.

Query: `page` mặc định `0`, phải ≥ 0; `size` mặc định `20`, từ 1 đến 100. Trang bắt đầu từ 0; sắp xếp `id` giảm dần. Không có body.

Request: `GET http://localhost:8080/api/v1/companies?page=0&size=20`.

Response **200 OK**, ví dụ người gọi chưa quản lý công ty nào:

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Khi có dữ liệu, mỗi phần tử `items` có đầy đủ `CompanyResponse` như response tạo công ty. `totalElements` là tổng số bản ghi, `totalPages` là tổng số trang; vượt quá trang cuối trả `items: []`. **Lỗi riêng:** phân trang ngoài giới hạn trả `400 INVALID_REQUEST` với message `Page must be nonnegative and size must be between 1 and 100`.

### GET /api/v1/companies/{id}

**Mục đích:** xem hồ sơ công ty thuộc phạm vi mình. **Quyền:** Đọc công ty. Path `id` là ID công ty; không có query/body.

Request: `GET http://localhost:8080/api/v1/companies/12`.

Response **200 OK**:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:05:00Z"}
```

**Lỗi riêng:** thường `404 RESOURCE_NOT_FOUND` do guard kiểm tra phạm vi; nếu không còn tìm thấy công ty ở bước đọc tiếp theo có thể nhận `404 COMPANY_NOT_FOUND`.

### PUT /api/v1/companies/{id}

**Mục đích:** thay thế các trường hồ sơ công ty. **Quyền:** Quản lý công ty. Path `id`; không có query. Đây là PUT với đủ các field bắt buộc của `CompanyProfileRequest`; không phải cập nhật từng phần.

Request: `PUT http://localhost:8080/api/v1/companies/12`.

```json
{"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000001","address":"20 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking và dã ngoại."}
```

Response **200 OK**, ví dụ công ty đang approved được đưa về pending:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000001","address":"20 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking và dã ngoại.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:10:00Z"}
```

`rejected` vẫn giữ rejected sau sửa; phải resubmit. **Lỗi riêng:** `409 COMPANY_STATE_CONFLICT` nếu suspended; `409 COMPANY_TAX_CODE_EXISTS`; `404 RESOURCE_NOT_FOUND`/`COMPANY_NOT_FOUND`.

### POST /api/v1/companies/{id}/resubmit

**Mục đích:** gửi lại hồ sơ bị từ chối để admin xem xét. **Quyền:** Quản lý công ty. Chỉ chấp nhận trạng thái `rejected`; chuyển về `pending`, xóa quyết định cũ. Path `id`; không có query/body.

Request: `POST http://localhost:8080/api/v1/companies/12/resubmit`.

Response **200 OK**:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:15:00Z"}
```

**Lỗi riêng:** `409 COMPANY_STATE_CONFLICT` nếu không phải rejected; `404 RESOURCE_NOT_FOUND`/`COMPANY_NOT_FOUND`. Endpoint không tự kiểm tra có giấy phép; admin chỉ có thể approve khi đã có bằng chứng giấy phép hợp lệ.

### POST /api/v1/companies/{id}/license

**Mục đích:** tải mới/thay thế bằng chứng giấy phép kinh doanh. **Quyền:** Quản lý công ty. Path `id`; không có query. Content type bắt buộc `multipart/form-data`, field **`file`**, kiểu binary. Không gửi JSON/base64 và không truyền đường dẫn file trong JSON.

Request mẫu bằng PowerShell (`curl.exe` tự tạo multipart boundary):

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/companies/12/license" -H "Authorization: Bearer <accessToken>" -F "file=@C:/docs/license.pdf;type=application/pdf"
```

File không được rỗng, tối đa **5 MiB = 5.242.880 byte**. Chỉ chấp nhận `application/pdf`, `image/png`, `image/jpeg` có nội dung khớp. PDF phải có header `%PDF-` và dấu `%%EOF` ở cuối; PNG/JPEG phải giải mã được, kích thước ảnh tối đa 20.000.000 pixel. Server kiểm tra nội dung, không chỉ phần mở rộng. Cấu hình multipart giới hạn một file 5MB và tổng request 6MB.

Response **200 OK** là hồ sơ công ty, không phải JSON chứa byte file:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:05:00Z"}
```

Tải giấy phép cho công ty approved cũng đưa về pending, xóa quyết định cũ. File được lưu riêng tư; URL trả về vẫn cần bearer token.

**Lỗi riêng:** `400 INVALID_COMPANY_LICENSE` (rỗng/sai MIME/nội dung/kích thước ở service); `413 MAX_UPLOAD_SIZE` (multipart vượt giới hạn trước service); `400 INVALID_REQUEST` (thiếu part file); `409 COMPANY_STATE_CONFLICT` (suspended); `503 COMPANY_LICENSE_UNAVAILABLE` (lưu file lỗi); lỗi phạm vi 404. API trả `415` khi gửi media type không được hỗ trợ bởi mapping; không giả định lỗi framework 415 có cùng JSON `ApiError`.

### GET /api/v1/companies/{id}/license

**Mục đích:** tải file giấy phép của công ty thuộc phạm vi mình. **Quyền:** Đọc công ty. Path `id`; không có query/body.

Request: `GET http://localhost:8080/api/v1/companies/12/license` với bearer token.

Response **200 OK** là **byte file thô**, không phải JSON/base64. Ví dụ header và body minh họa:

```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Cache-Control: no-store
Content-Disposition: attachment; filename="license.pdf"
X-Content-Type-Options: nosniff

<byte nội dung file PDF>
```

Tên tải về được server tạo là `license.pdf`, `license.png` hoặc `license.jpg`, không phải tên gốc đã upload. **Lỗi riêng:** `404 COMPANY_LICENSE_NOT_FOUND` (chưa có hoặc file mất/tham chiếu không hợp lệ); `503 COMPANY_LICENSE_UNAVAILABLE`; lỗi phạm vi 404. Schema `string/byte` trong Swagger không có nghĩa endpoint này trả JSON chứa field `content`.

## Admin duyệt công ty

### GET /api/v1/admin/companies

**Mục đích:** xem danh sách tất cả công ty để duyệt. **Quyền:** Duyệt công ty. Không có body.

| Query | Mặc định | Quy tắc |
| --- | --- | --- |
| `status` | Bỏ lọc | Không bắt buộc; bỏ qua hoặc trắng → tất cả; giá trị chính xác `pending`, `approved`, `rejected`, `suspended` |
| `page` | `0` | Số nguyên ≥ 0 |
| `size` | `20` | Số nguyên 1–100 |

Sắp xếp `id` giảm dần. Request: `GET http://localhost:8080/api/v1/admin/companies?status=pending&page=0&size=20`.

Response **200 OK**:

```json
{"items":[{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/admin/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:05:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
```

Response admin dùng `licenseUrl` có tiền tố `/admin`. **Lỗi riêng:** `400 INVALID_REQUEST` với message `Unknown company status` khi lọc sai (phân biệt hoa thường), hoặc lỗi phân trang. Không phải admin nhận `403 FORBIDDEN`.

### GET /api/v1/admin/companies/{id}

**Mục đích:** admin xem đầy đủ hồ sơ cần duyệt. **Quyền:** Duyệt công ty. Path `id`; không có query/body.

Request: `GET http://localhost:8080/api/v1/admin/companies/12`.

Response **200 OK**:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"pending","licenseAvailable":true,"licenseUrl":"/api/v1/admin/companies/12/license","approvedBy":null,"approvedAt":null,"decisionReason":null,"createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:05:00Z"}
```

**Lỗi riêng:** `404 COMPANY_NOT_FOUND`; thiếu quyền `403 FORBIDDEN`.

### GET /api/v1/admin/companies/{id}/license

**Mục đích:** admin tải bằng chứng giấy phép để thẩm định. **Quyền:** Duyệt công ty. Path `id`; không có query/body.

Request: `GET http://localhost:8080/api/v1/admin/companies/12/license` với bearer token admin.

Response **200 OK**, binary như endpoint thành viên:

```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Cache-Control: no-store
Content-Disposition: attachment; filename="license.pdf"
X-Content-Type-Options: nosniff

<byte nội dung file PDF>
```

**Lỗi riêng:** `404 COMPANY_NOT_FOUND`, `404 COMPANY_LICENSE_NOT_FOUND`, `503 COMPANY_LICENSE_UNAVAILABLE`, hoặc `403 FORBIDDEN`.

### POST /api/v1/admin/companies/{id}/decision

**Mục đích:** phê duyệt, từ chối hoặc đình chỉ công ty. **Quyền:** Duyệt công ty. Path `id`; không có query.

| Body field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `decision` | Có | Không trắng; đúng một trong `approve`, `reject`, `suspend`, phân biệt hoa thường |
| `reason` | Có | Không trắng, tối đa 2.000 ký tự; server trim trước khi lưu |

| Quyết định | Trạng thái đầu vào | Kết quả |
| --- | --- | --- |
| `approve` | `pending`, đã upload giấy phép còn đọc được | `approved` |
| `reject` | `pending` | `rejected` |
| `suspend` | `approved` | `suspended` |

Không có quyết định khôi phục trực tiếp `suspended` trong API hiện tại. Mọi quyết định đều lưu actor, thời điểm, lý do và audit.

Request: `POST http://localhost:8080/api/v1/admin/companies/12/decision`.

```json
{"decision":"approve","reason":"Đã kiểm tra hồ sơ và giấy phép hợp lệ."}
```

Response **200 OK**:

```json
{"id":12,"name":"Công ty Trek Mẫu","taxCode":"0312345678","representativeName":"Nguyễn Minh An","contactEmail":"contact@trek.example","contactPhone":"0900000000","address":"10 Đường Mẫu, TP.HCM","description":"Tổ chức tour trekking.","status":"approved","licenseAvailable":true,"licenseUrl":"/api/v1/admin/companies/12/license","approvedBy":1,"approvedAt":"2026-10-21T10:20:00Z","decisionReason":"Đã kiểm tra hồ sơ và giấy phép hợp lệ.","createdAt":"2026-10-21T10:00:00Z","updatedAt":"2026-10-21T10:20:00Z"}
```

**Lỗi riêng:** `409 COMPANY_STATE_CONFLICT` (chuyển trạng thái sai); `409 COMPANY_LICENSE_REQUIRED` (approve chưa upload giấy phép); `404 COMPANY_LICENSE_NOT_FOUND` (có tham chiếu nhưng file không đọc được vì không tồn tại/hợp lệ); `503 COMPANY_LICENSE_UNAVAILABLE`; `404 COMPANY_NOT_FOUND`; thiếu quyền `403 FORBIDDEN`.

## Chính sách hợp tác và xuất bản phiên bản

`Policy` là danh tính chính sách (code/title/phạm vi). `PolicyVersion` chứa nội dung, thời điểm có hiệu lực và điều khoản tài chính của từng phiên bản. API tạo chính sách hiện chỉ tạo loại `cooperation`, active mặc định `true`; client không truyền `kind` hoặc `active`. Không có API sửa/xóa chính sách hay phiên bản trong nhóm này.

### POST /api/v1/admin/policies

**Mục đích:** admin tạo danh tính chính sách hợp tác áp dụng toàn hệ thống hoặc riêng một công ty. **Quyền:** Quản lý chính sách. Không có path/query.

| Body field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `code` | Có | Không trắng, tối đa 80 ký tự; server trim, duy nhất |
| `title` | Có | Không trắng, tối đa 250 ký tự; server trim |
| `companyId` | Không | `null`/bỏ qua → chính sách chung; nếu có phải là số nguyên dương, công ty phải tồn tại |

Request: `POST http://localhost:8080/api/v1/admin/policies`.

```json
{"code":"COOPERATION-2026","title":"Điều khoản hợp tác trekking","companyId":null}
```

Response **201 Created**:

```json
{"id":3,"code":"COOPERATION-2026","title":"Điều khoản hợp tác trekking","kind":"cooperation","companyId":null,"active":true}
```

Tạo danh tính chưa có nội dung để ký; cần publish version. **Lỗi riêng:** `409 POLICY_CODE_EXISTS`; `404 RESOURCE_NOT_FOUND` nếu `companyId` không tồn tại; thiếu quyền `403 FORBIDDEN`.

### POST /api/v1/admin/policies/{policyId}/versions

**Mục đích:** xuất bản và phê duyệt ngay phiên bản chính sách mới, tăng `versionNumber` tự động cho policy. **Quyền:** Quản lý chính sách. Path `policyId`; không có query.

| Body field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `content` | Có | Không trắng, tối đa 100.000 ký tự; toàn văn nội dung |
| `effectiveFrom` | Có | Ngày giờ ISO 8601 có offset; cho phép quá khứ/hiện tại/tương lai |
| `platformFeePercent` | Có | JSON number 0–100, tối đa 3 chữ số phần nguyên và 2 chữ số thập phân; phí nền tảng theo phần trăm |
| `payoutWaitHours` | Có | Số nguyên ≥ 0; thời gian chờ thanh toán cho công ty tính bằng giờ |
| `payoutTermsSnapshot` | Có | Không trắng, tối đa 100.000 ký tự; nội dung điều khoản thanh toán |

Request: `POST http://localhost:8080/api/v1/admin/policies/3/versions`.

```json
{
  "content": "Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này.",
  "effectiveFrom": "2026-10-21T10:25:00Z",
  "platformFeePercent": 10.00,
  "payoutWaitHours": 48,
  "payoutTermsSnapshot": "Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ."
}
```

Response **201 Created**:

```json
{
  "id": 7,
  "policyId": 3,
  "policyCode": "COOPERATION-2026",
  "title": "Điều khoản hợp tác trekking",
  "versionNumber": 1,
  "content": "Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này.",
  "effectiveFrom": "2026-10-21T10:25:00Z",
  "approvedBy": 1,
  "approvedAt": "2026-10-21T10:25:00Z",
  "platformFeePercent": 10.00,
  "payoutWaitHours": 48,
  "payoutTermsSnapshot": "Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ."
}
```

`approvedBy` và `approvedAt` do server lấy từ admin/thời gian hiện tại; không truyền từ client. Phiên bản tương lai chỉ đủ điều kiện ký khi tới `effectiveFrom`. Phiên bản đã được phê duyệt/tham chiếu được migration 003 bảo vệ khỏi sửa/xóa; muốn đổi điều khoản phải xuất bản phiên bản mới.

**Lỗi riêng:** `404 RESOURCE_NOT_FOUND` nếu policy không tồn tại; `409 POLICY_NOT_PUBLISHABLE` nếu policy không active hoặc không phải cooperation; `400 VALIDATION_ERROR` nếu thiếu/sai điều khoản; thiếu quyền `403 FORBIDDEN`.

## Xem và chấp nhận thỏa thuận

### GET /api/v1/companies/{companyId}/agreement-policies

**Mục đích:** lấy danh sách phiên bản chính sách hợp tác đang đủ điều kiện áp dụng cho công ty để người dùng đọc trước khi ký. **Quyền:** Đọc công ty. Path `companyId`; không có query/body; không phân trang.

Lấy chính sách active loại cooperation, phạm vi chung hoặc đúng công ty. Mỗi chính sách chọn một bản đã phê duyệt và có hiệu lực: ưu tiên `effectiveFrom` mới nhất, sau đó `versionNumber` cao nhất. Bản phải có đủ phí nền tảng, thời gian chờ, điều khoản thanh toán. Bản chưa có hiệu lực không được trả về. API này không yêu cầu công ty đã approved và không loại bỏ phiên bản đã ký; bước xin OTP/ký kiểm tra điều kiện đó riêng.

Request: `GET http://localhost:8080/api/v1/companies/12/agreement-policies`.

Response **200 OK**:

```json
[{"id":7,"policyId":3,"policyCode":"COOPERATION-2026","title":"Điều khoản hợp tác trekking","versionNumber":1,"content":"Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này.","effectiveFrom":"2026-10-21T10:25:00Z","approvedBy":1,"approvedAt":"2026-10-21T10:25:00Z","platformFeePercent":10.00,"payoutWaitHours":48,"payoutTermsSnapshot":"Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ."}]
```

Không có bản đủ điều kiện trả `[]`. **Lỗi riêng:** `404 RESOURCE_NOT_FOUND` khi không có phạm vi công ty.

### POST /api/v1/companies/{companyId}/agreements/otp

**Mục đích:** gửi mã xác nhận ký thỏa thuận tới email tài khoản chủ công ty đang đăng nhập. **Quyền:** Chủ công ty ký. Email nhận **không lấy từ `contactEmail` của hồ sơ công ty**. Path `companyId`; không có query.

Body `policyVersionId` bắt buộc, số nguyên 64 bit **> 0**. Phiên bản phải là bản hiện tại đã approved, đã effective, có đủ điều khoản tài chính, policy active cooperation và áp dụng cho công ty. Chưa được ký cùng phiên bản trước đó.

Request: `POST http://localhost:8080/api/v1/companies/12/agreements/otp`.

```json
{"policyVersionId":7}
```

Response **202 Accepted**:

```json
{"expiresIn":300,"retryAfter":60}
```

Hai giá trị đều tính bằng **giây**, không có OTP trong response. Mã có hiệu lực 5 phút; cooldown gửi lại 60 giây; tối đa 5 lần cấp mã trong cửa sổ 1 giờ theo email (dùng chung giữa các công ty/phiên bản trong mục đích ký). Mỗi cặp tài khoản/công ty chỉ có một challenge; xin mã mới sau cooldown thay mã cũ. Mã gắn với người ký, công ty, phiên bản và bối cảnh lúc xin mã.

**Lỗi riêng:** `403 AGREEMENT_OWNER_REQUIRED`; `409 COMPANY_NOT_APPROVED`; `404 RESOURCE_NOT_FOUND` (phiên bản/policy/phạm vi sai); `409 POLICY_VERSION_INELIGIBLE` (không còn là bản hiện tại có hiệu lực hoặc thiếu điều khoản); `409 AGREEMENT_ALREADY_ACCEPTED`; `429 AGREEMENT_OTP_THROTTLED`; `503 MAIL_UNAVAILABLE` nếu SMTP không gửi được. Khi lỗi mail, challenge bị hủy nhưng cooldown vẫn áp dụng.

### POST /api/v1/companies/{companyId}/agreements

**Mục đích:** xác nhận OTP và ghi nhận bằng chứng công ty chấp nhận một phiên bản điều khoản cụ thể. **Quyền:** Chủ công ty ký, cùng điều kiện như xin OTP. Path `companyId`; không có query.

| Body field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `policyVersionId` | Có | Số nguyên 64 bit > 0, đúng phiên bản đã xin OTP |
| `otp` | Có | Chuỗi đúng 6 chữ số `[0-9]{6}`; dùng chuỗi để giữ số 0 đầu |

Request: `POST http://localhost:8080/api/v1/companies/12/agreements`.

```json
{"policyVersionId":7,"otp":"012345"}
```

Response **201 Created**, `AgreementReceiptResponse`:

```json
{
  "id": 21,
  "companyId": 12,
  "policyVersionId": 7,
  "acceptedBy": 100,
  "acceptedAt": "2026-10-21T10:30:00Z",
  "acceptedIp": "172.18.0.1",
  "emailVerifiedAt": "2026-10-21T10:30:00Z",
  "platformFeePercent": 10.00,
  "payoutWaitHours": 48,
  "payoutTermsSnapshot": "Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ.",
  "versionNumber": 1,
  "policyContent": "Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này."
}
```

`acceptedBy` là ID tài khoản ký; `acceptedAt` là thời điểm server; `emailVerifiedAt` là thời điểm xác nhận email bằng OTP cho **lần ký này**, không phải thời điểm xác minh tài khoản ban đầu. `acceptedIp` lấy từ `HttpServletRequest.getRemoteAddr()`, không nhận từ body; khi chạy Docker/proxy có thể là IP mạng nội bộ mà server nhìn thấy.

Mã chỉ dùng một lần, tối đa 5 lần nhập sai trước khi bị hủy. Thay đổi hồ sơ công ty, trạng thái duyệt, membership, quyền/phiên/email người ký hoặc chính sách có thể làm mã cũ không còn khớp; xin mã mới sau khi đọc lại điều khoản. Server tiêu thụ OTP trước khi ghi database, nên nếu bước ghi/ký sau đó thất bại, mã đã dùng không được phục hồi; kiểm tra danh sách receipt rồi xin mã mới nếu chưa có receipt. Không coi retry là thành công mặc định.

Receipt lưu bản chụp phí, thời gian chờ và điều khoản thanh toán. Nội dung chính sách lấy từ version được bảo vệ bất biến; receipt được trigger database bảo vệ khỏi UPDATE/DELETE. Mỗi công ty chỉ ký một lần cho cùng phiên bản (`companyId`, `policyVersionId` duy nhất); không có API sửa/xóa receipt.

**Lỗi riêng:** cùng lỗi điều kiện/phạm vi như xin OTP; `400 INVALID_AGREEMENT_OTP` (sai/hết hạn/đã dùng/bối cảnh không khớp); `409 AGREEMENT_CONTEXT_CHANGED` (bối cảnh thay đổi trong khi thực hiện ký); `400 INVALID_PEER_ADDRESS` (địa chỉ peer không hợp lệ); `409 AGREEMENT_ALREADY_ACCEPTED` nếu đã ký. Lỗi truy cập DB trả `503 AUTH_UNAVAILABLE`.

### GET /api/v1/companies/{companyId}/agreements

**Mục đích:** xem lịch sử bằng chứng chấp nhận điều khoản của công ty. **Quyền:** Đọc công ty; manager cũng được xem. Không yêu cầu công ty hiện vẫn approved. Path `companyId`; không có query/body, không phân trang. Sắp xếp `acceptedAt` giảm dần, sau đó `id` giảm dần.

Request: `GET http://localhost:8080/api/v1/companies/12/agreements`.

Response **200 OK**:

```json
[{"id":21,"companyId":12,"policyVersionId":7,"acceptedBy":100,"acceptedAt":"2026-10-21T10:30:00Z","acceptedIp":"172.18.0.1","emailVerifiedAt":"2026-10-21T10:30:00Z","platformFeePercent":10.00,"payoutWaitHours":48,"payoutTermsSnapshot":"Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ.","versionNumber":1,"policyContent":"Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này."}]
```

Chưa ký gì trả `[]`. **Lỗi riêng:** `404 RESOURCE_NOT_FOUND` khi không có phạm vi công ty hoặc thiếu version được receipt tham chiếu.

### GET /api/v1/companies/{companyId}/agreements/{agreementId}

**Mục đích:** lấy chi tiết một receipt để hiển thị bằng chứng và điều khoản đã ký. **Quyền:** Đọc công ty. Path `companyId` và `agreementId`; receipt phải thuộc đúng công ty. Không có query/body.

Request: `GET http://localhost:8080/api/v1/companies/12/agreements/21`.

Response **200 OK**:

```json
{"id":21,"companyId":12,"policyVersionId":7,"acceptedBy":100,"acceptedAt":"2026-10-21T10:30:00Z","acceptedIp":"172.18.0.1","emailVerifiedAt":"2026-10-21T10:30:00Z","platformFeePercent":10.00,"payoutWaitHours":48,"payoutTermsSnapshot":"Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ.","versionNumber":1,"policyContent":"Công ty đồng ý thực hiện dịch vụ theo điều khoản hợp tác này."}
```

**Lỗi riêng:** `404 RESOURCE_NOT_FOUND` nếu receipt không tồn tại, thuộc công ty khác hoặc không có phạm vi công ty.

## Đối chiếu nguồn và giới hạn xác minh

Tài liệu được đối chiếu controller, request/response records, service và guard phân quyền trong `src/main/java/com/trekking/app/features/company`, `core/security/authorization`, `GlobalExceptionHandler`, storage giấy phép và challenge OTP; so với snapshot [openapi.json](openapi.json). 18 operation có trong snapshot. Sai khác status xác định được: tạo công ty ghi 200 trong Swagger nhưng code trả 201. Download license được tài liệu hóa theo binary response thực tế của controller thay vì diễn giải `string/byte` của OpenAPI thành JSON/base64. OpenAPI ghi minimum 0 cho một số field `@Positive`; validation runtime yêu cầu **lớn hơn 0**.

Không gọi API thay đổi dữ liệu, không gửi mail/OTP thật để viết ví dụ. Các response và mã lỗi nghiệp vụ trên được suy ra trực tiếp từ mã nguồn; ví dụ JSON là dữ liệu tổng hợp.

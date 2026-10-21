# Module 1 — API và kiểm chứng backend

Phạm vi: phần Member 1 trong `phan_cong_sba.md` (AU-01…AU-05), backend và kiểm thử.
Không triển khai hoặc nghiệm thu frontend trong đợt này. Tài liệu này bổ sung
contract ngày 15/10 trong [MODULE_1_IMPLEMENTATION.md](MODULE_1_IMPLEMENTATION.md).

## API phục vụ AU-01…AU-05

Backend có **41 operation** trong chín controller. Mọi route dưới đây có tiền tố
`/api/v1`; đếm cặp method/path, không tính Swagger/framework hoặc route chỉ có
trong test. Các nghiệp vụ bảo vệ dùng app Bearer JWT; POST public dưới `/auth`
vẫn cần handshake CSRF theo [AUTH.md](AUTH.md).

| Nhóm | API |
|---|---|
| Auth — 11 | `POST /auth/register`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/login`, `/auth/google`, `/auth/refresh`, `/auth/logout`; `GET /auth/me`, `/auth/csrf` |
| Authorization — 1 | `GET /accounts/me/authorization` |
| Account admin — 4 | `GET /admin/accounts/{id}`; `POST /admin/accounts/{id}/block`, `/admin/accounts/{id}/unblock`; `PUT /admin/accounts/{id}/roles` |
| Financial permission admin — 2 | `GET`, `PUT /admin/accounts/{accountId}/financial-permissions` |
| Company — 7 | `POST /companies`; `GET /companies`, `/companies/{id}`; `PUT /companies/{id}`; `POST /companies/{id}/resubmit`, `/companies/{id}/license`; `GET /companies/{id}/license` |
| Company admin — 4 | `GET /admin/companies`, `/admin/companies/{id}`, `/admin/companies/{id}/license`; `POST /admin/companies/{id}/decision` |
| Policy admin — 2 | `POST /admin/policies`, `/admin/policies/{policyId}/versions` |
| Agreement — 5 | `GET /companies/{companyId}/agreement-policies`; `POST /companies/{companyId}/agreements/otp`, `/companies/{companyId}/agreements`; `GET /companies/{companyId}/agreements`, `/companies/{companyId}/agreements/{agreementId}` |
| Invitation — 5 | `POST /admin/staff/invitations`, `/admin/staff/invitations/{accountId}/resend`, `/companies/{companyId}/guides/invitations`, `/companies/{companyId}/guides/invitations/{accountId}/resend`, `/auth/activate-account` |

Request/response company/policy/invitation giữ contract bàn giao 15/10. Error giữ
`{code,message,fieldErrors}`. Đổi ID không thuộc company/guide/booking scope trả 404;
thiếu quyền action trả 403; không có session hợp lệ trả 401.

| Yêu cầu Member 1 | API/contract backend đã có | Giới hạn nghiệm thu |
|---|---|---|
| AU-01 | Register/login/verify/resend/forgot/reset/Google; guard chủ booking và điều kiện payment/review | M3/M4/M5 cần gọi guard khi ghi nghiệp vụ thật; Google/SMTP thật cần cấu hình môi trường |
| AU-02 | JWT/refresh/logout, account status/session revocation, role/company/guide scope, block/unblock/reason/audit, grant tài chính | Grant không tự thực hiện thu/hoàn/chi; nghiệp vụ tiền do M4 tích hợp |
| AU-03 | Nộp/sửa/xem/nộp lại hồ sơ, private license, approve/reject/suspend và audit | Chưa deploy/migrate DB môi trường hiện hữu |
| AU-04 | Policy/version đủ điều kiện, OTP ký, receipt có actor/time/IP/financial snapshot và immutable evidence | M3/M4 cần sử dụng receipt khi tạo booking/đối soát |
| AU-05 | Mời/gửi lại guide hoặc staff, activation/password lần đầu, scope guide và chặn bypass | Assignment/check-in thực tế do M2/M3 tích hợp |

Với các luồng account/auth/company/agreement/invitation do Module 1 trực tiếp sở
hữu, đã có đủ nhóm API theo tài liệu. Phần authorization giao nhau đã có service
guard và contract; chưa đồng nghĩa hoàn thành E2E của những nghiệp vụ chưa tồn tại.

## Quyền tài chính cấp riêng

Chỉ ADMIN được đọc/gán/thu hồi grant bằng hai API mới. `AdminAccountResponse`
không đổi. Response GET/PUT:

```json
{"accountId":42,"permissions":["FINANCE_READ","SETTLEMENT_PREPARE"]}
```

PUT thay toàn bộ tập grant, bắt buộc reason; danh sách rỗng thu hồi hết:

```json
{"permissions":["FINANCE_READ","SETTLEMENT_PREPARE"],"reason":"Phân công lập kỳ đối soát"}
```

| Action | Mục đích cho service M4/M5 |
|---|---|
| `FINANCE_READ` | Xem dữ liệu tài chính được phép |
| `PAYMENT_RECORD` | Đối chiếu và ghi nhận khoản thu thủ công |
| `REFUND_APPROVE` | Duyệt yêu cầu hoàn |
| `REFUND_RECORD` | Ghi giao dịch hoàn đã thực hiện |
| `SETTLEMENT_PREPARE` | Lập kỳ đối soát |
| `SETTLEMENT_APPROVE` | Duyệt kỳ đối soát |
| `PAYOUT_RECORD` | Ghi chứng từ chi đối tác |

ACCOUNTANT và ADMIN không tự có quyền tiền chỉ nhờ role. Grant mới yêu cầu account
ACTIVE, email đã verify, đã đặt password lần đầu và có role ACCOUNTANT. Không cho
gán cho chính ADMIN đang gọi hoặc account có ADMIN. Có thể thu hồi hết quyền trên
account blocked/chưa kích hoạt. Bỏ role ACCOUNTANT xóa grant; thêm lại role không
khôi phục grant cũ. Đọc quyền từ DB trên mỗi request, nên thu hồi có hiệu lực với
JWT còn hạn.

Tại cùng một thời điểm, account chỉ có tối đa một action trong
`SETTLEMENT_PREPARE`/`SETTLEMENT_APPROVE`/`PAYOUT_RECORD`, và không có đồng thời
`REFUND_APPROVE` với `REFUND_RECORD`. API trả 400 khi tập quyền xung đột, chứa
action ngoài whitelist hoặc trùng. DB cũng bảo vệ các tập xung đột. Mutation và
audit `ACCOUNT_FINANCIAL_PERMISSIONS_CHANGED` chung transaction.

Đây là contract cấp quyền cụ thể phục vụ tích hợp. Chủ nghiệp vụ M4/M5 cần xác
nhận phân công nhân sự trước cấp grant trong môi trường thật.

## Guard gọi từ service nghiệp vụ

`BusinessAuthorizationService` dùng danh tính hiện tại từ server:

- `requirePayment(bookingId)`: CUSTOMER là chủ đơn; PENDING_PAYMENT chưa hết giữ
  chỗ hoặc CONFIRMED; chưa quá hạn trả, lịch SCHEDULED chưa khởi hành; còn thiếu
  tiền theo tổng snapshot server trừ khoản thu allocation `accepted`. Payment
  attempt thành công hoặc khoản thu chưa được accepted không tự tính là đã trả.
- `requireReview(bookingId)`: chủ đơn là participant đã check-in thực tế; booking
  và lịch COMPLETED, có actual completion đã xảy ra; chưa có review kể cả bài ẩn.

`FinancialOperationAuthorizationService` đọc resource thật từ DB:

- `requireSettlementApproval(statementId)`: SETTLEMENT_APPROVE, DRAFT và người
  duyệt khác người lập.
- `requirePayout(statementId)`: PAYOUT_RECORD, APPROVED có bằng chứng duyệt và
  người chi khác cả người lập lẫn người duyệt.
- `requireRefundApproval(refundId)`: REFUND_APPROVE, REQUESTED; người duyệt khác
  người yêu cầu nếu có.
- `requireRefundRecording(refundId)`: REFUND_RECORD, APPROVED có người/thời điểm
  duyệt; người ghi khác người yêu cầu và người duyệt.

Các guard bắt buộc transaction **ghi đang mở** (`MANDATORY`), không chấp nhận
transaction read-only. Consumer phải gọi guard trước khi thay dữ liệu trong
cùng transaction, vì guard refresh entity về trạng thái DB dưới khóa và giữ khóa
đến commit/rollback. Business guard khóa theo thứ tự booking → schedule →
participant; các service cập nhật tiền/completion/check-in/review phải tuân thủ
cùng thứ tự. Finance guard khóa statement/refund request. Đây không phải API
eligibility để client tự chứng minh quyền.

Guard kiểm tra người đã thao tác trong resource nên việc đổi grant sau khi lập/
duyệt không cho phép cùng người thực hiện bước bị cấm trên resource cũ. M4 vẫn
phải kiểm tra số tiền, trạng thái retry/completion, idempotency và evidence khi
ghi giao dịch. Webhook cần xác thực riêng, không gọi guard dựa session customer.

Các service nghiệp vụ payment/review/settlement của M3–M5 chưa có trong repo.
Guard và contract M1 đã sẵn để tích hợp; chưa thể coi E2E nghiệp vụ liên module
đã nghiệm thu từ test guard hoặc từ việc Swagger liệt kê đủ endpoint.

## Sửa lỗi và migration

- Bổ sung ba Lua resource invitation bị thiếu: issue/consume/cancel. Challenge
  TTL 300 giây, cooldown 60 giây, năm lần gửi/giờ, năm lần thử, consume atomic;
  hủy delivery cũ không xóa challenge mới hoặc reset quota.
- Refresh và khóa lại entity dùng để quyết định quyền, xử lý trường hợp caller
  đã load entity trước khi transaction khác đổi booking/schedule/check-in.
- Role revision ngăn OTP ký cũ hợp lệ trở lại sau khi role bị thu hồi rồi cấp lại.
- Khai báo Bearer trong OpenAPI cho nghiệp vụ bảo vệ để Swagger gửi token khi
  Try it out; activation và các auth route public giữ đúng contract CSRF.

Thứ tự database: baseline → 001 → 002 → 003 →
`004_add_financial_permissions.sql` → app mới. Migration 004 thêm bảng grant rỗng,
`authorization_version` và trigger đổi role; không tự cấp quyền tiền, không sửa
baseline/migration cũ và không thu hồi JWT/refresh đang hợp lệ chỉ vì nâng cấp.
Compose volume mới và Testcontainers chạy đủ chuỗi. Volume cũ phải áp dụng 004
chủ động theo AUTH.md trước deploy. Trong task chỉ chạy migration trên DB test.

Nếu rollback app, giữ bảng/cột mới và đóng luồng cấp quyền/chấp nhận OTP ở app cũ;
phiên bản cũ không thực thi được contract grant và revision mới. Không xóa bảng
grant hoặc rollback dữ liệu bằng truncate.

## Kiểm chứng

Lệnh từ root, Docker Linux engine hoạt động và JDK21:

```powershell
$env:JAVA_HOME = 'C:\Users\Windows\.jdks\ms-21.0.8'
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
docker compose config --quiet
```

Các suite kiểm tra auth/Google/JWT/validation/CSRF/session, account/role/scope,
company/license/approval, agreement, invitation, grant tài chính, guard theo
resource, upgrade database và contract OpenAPI. Integration tests dùng
PostgreSQL/Redis riêng; mail/Google credential được mock hoặc dùng JWKS test.

Kết quả ngày **21/10/2026**, hoàn tất **19:53:50 giờ Việt Nam**: Maven wrapper
3.9.16, JDK Microsoft 21.0.8, PostgreSQL 18.6, Redis 8.2-alpine;
`clean verify` **exit 0**, **307 test / 17 suite, 0 failures/errors/skipped**,
thời gian Maven 5 phút 16 giây. JAR `target/treeking-0.0.1-SNAPSHOT.jar` đã build
và repackage thành công. Ba invitation Lua resource có trong JAR.

| Nhóm kiểm chứng | Số test | Kết quả |
|---|---:|---|
| Auth, CSRF/security, validation, Google và JWT | 80 | Pass |
| Account administration và role/company/guide scope | 46 | Pass |
| Company/license và agreement/policy | 35 | Pass |
| Invitation/activation, resend, quota và concurrency | 30 | Pass |
| Quyền payment/review, dữ liệu cũ và thời gian giữ khóa | 39 | Pass |
| Quyền tài chính theo resource và cấp/thu hồi grant | 68 | Pass |
| OpenAPI inventory/security, migration và context | 9 | Pass |
| **Tổng** | **307** | **Pass** |

OpenAPI runtime xác nhận đúng **41 operation: 30 protected, 11 public**. Tất cả
operation được bảo vệ đều khai báo Bearer; auth public/activation không yêu cầu
Bearer. Test dùng chính MockMvc/controller/service và PostgreSQL/Redis riêng,
không chỉ kiểm tra chuỗi annotation trong source.

`docker compose config --quiet` và `git diff --check`: exit 0. Review độc lập đã
đối chiếu API, guard, migration và tài liệu. Các regression khóa kiểm tra
SQLSTATE PostgreSQL `55P03` thay vì phụ thuộc lớp exception translator của Spring.
Không còn failure/error trong bộ kiểm thử đã chạy; kết quả này không khẳng định
mọi tình huống ngoài phạm vi suite đều không có lỗi.

Chưa kiểm chứng SMTP nhà cung cấp, Google credential thật, hosted CI hoặc deploy/
migration trên DB môi trường hiện hữu. Không kết luận các bước đó thành công từ
automated test. Runbook HTTP demo `tools/run_module_one_http_test.ps1` được tài
liệu cũ tham chiếu vẫn chưa có trong checkout; lệnh kiểm chứng thực tế ở đợt này
là Maven/Testcontainers phía trên.

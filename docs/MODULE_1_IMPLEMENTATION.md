# Module 1 — bàn giao đợt hoàn thiện 15/10/2026

> Cập nhật backend sau đợt này: xem [MODULE_1_BACKEND.md](MODULE_1_BACKEND.md)
> cho migration 004, API quyền tài chính, guard tích hợp và kiểm chứng hiện tại.
> Các kết quả ngày 15/10 bên dưới được giữ làm lịch sử bàn giao.

Phạm vi là AU-01…AU-05 trong `phan_cong_sba.md`, phần backend và giao diện Module 1.
Không bao gồm viết thay nghiệp vụ tour, booking, thu/hoàn/đối soát tiền, review hoặc AI
của Member 2…6. Tài liệu này bổ sung contract đợt đầu trong
[MODULE_1_AUTHORIZATION.md](MODULE_1_AUTHORIZATION.md).

## Quyết định triển khai

Người dùng đã xác nhận trong phiên làm việc ngày 15/10/2026:

- ADMIN duyệt đối tác; chỉ OWNER active được ký điều khoản.
- Company phải APPROVED trước khi ký.
- Sửa hồ sơ hoặc thay giấy phép của company APPROVED đưa hồ sơ về PENDING để duyệt lại.

M1 sở hữu state machine company, invitation và bằng chứng ký. API policy hợp tác
do ADMIN vận hành: tạo policy rồi phát hành version có các điều khoản tài chính
tường minh. Đây là nguồn snapshot cho ký; không suy ra phí/thời gian chi từ default
của bảng agreement. M4 tiếp tục sở hữu nghiệp vụ tiền và xác nhận nội dung policy
trước khi sử dụng trên môi trường thật. ACCOUNTANT chưa được cấp quyền duyệt/chi.
Quy tắc review giữ constraint schema hiện hữu: tác giả là chủ đơn đồng thời là
participant đã check-in trong booking/chuyến hoàn thành; không mở rộng sang mọi participant.

Mọi endpoint mới lấy actor từ phiên server. Giữ error `{code,message,fieldErrors}`,
DTO record, service interface/implementation và Bearer cho nghiệp vụ. Các POST dưới
`/auth/` vẫn bắt CSRF. ADMIN thêm capability `COMPANY_REVIEW`, `POLICY_MANAGE`;
không tự có membership hoặc quyền guide. Company/schedule sai scope trả 404.

## Hồ sơ đối tác — P3

Tiền tố `/api/v1`:

| API | Contract |
|---|---|
| POST /companies | Customer đã xác minh tạo company PENDING, OWNER membership và bổ sung role COMPANY_MANAGER cùng transaction; 201 |
| GET /companies?page=0&size=20 | Company trong membership active; `{items,page,size,totalElements,totalPages}` |
| GET /companies/{id} | Chi tiết trong scope |
| PUT /companies/{id} | OWNER/MANAGER active sửa profile; APPROVED → PENDING |
| POST /companies/{id}/resubmit | REJECTED → PENDING; status khác trả 409 |
| POST /companies/{id}/license | Multipart part `file`; private evidence PDF/PNG/JPEG tối đa 5 MiB |
| GET /companies/{id}/license | Tải giấy phép trong scope, trả attachment |
| GET /admin/companies?status=pending&page=0&size=20 | ADMIN liệt kê hồ sơ; status optional |
| GET /admin/companies/{id} | ADMIN đọc hồ sơ |
| GET /admin/companies/{id}/license | ADMIN tải giấy phép để duyệt |
| POST /admin/companies/{id}/decision | `{decision:"approve"|"reject"|"suspend",reason}` |

Profile: `name,taxCode,representativeName,contactEmail,contactPhone,address,description`;
description optional. Không nhận status, actor, approvedAt hoặc URL giấy phép từ client.
Tax code được chuẩn hóa uppercase, unique; danh sách giới hạn size 1…100, page ≥0.
Approve/reject chỉ từ PENDING; suspend chỉ từ APPROVED. SUSPENDED không tự sửa,
resubmit hoặc thay giấy phép. Mutation và audit chung transaction, khóa hàng trước đổi trạng thái.
Approve yêu cầu file hợp lệ trong private storage. UUID reference gắn company trong DB;
file không được xuất trực tiếp qua web root. Thay file dọn reference cũ sau commit,
rollback dọn file mới. Lỗi dọn file được ghi log, có thể cần dọn orphan khi vận hành.

## Điều khoản hợp tác — P4

| API | Contract |
|---|---|
| POST /admin/policies | ADMIN tạo cooperation policy: `{code,title,companyId?}`; bỏ companyId cho global; 201 |
| POST /admin/policies/{id}/versions | ADMIN phát hành version đã approved: `{content,effectiveFrom,platformFeePercent,payoutWaitHours,payoutTermsSnapshot}`; 201 |
| GET /companies/{id}/agreement-policies | Các version hiện hành áp dụng cho company, có terms đầy đủ |
| POST /companies/{id}/agreements/otp | `{policyVersionId}`; 202 `{expiresIn:300,retryAfter:60}` |
| POST /companies/{id}/agreements | `{policyVersionId,otp}`; 201 receipt |
| GET /companies/{id}/agreements | Danh sách receipt của company, mới nhất trước; JSON array |
| GET /companies/{id}/agreements/{receiptId} | Receipt bất biến trong scope |

Policy phải active, cooperation, đúng company/global; chọn version approved/effective mới
nhất theo effectiveFrom rồi versionNumber. Version thiếu financial terms không được ký;
không fallback về bản cũ. ADMIN phải nhập phí 0…100, wait hours ≥0 và nội dung terms.
Version đã approved hoặc đã được tham chiếu không sửa/xóa; phát hành version mới.

OTP purpose riêng, HMAC, TTL 5 phút, tối đa 5 lần sai, cooldown 60 giây, tối đa
5 lần gửi/giờ/email cho purpose ký, dùng chung giữa company/version. Challenge ràng buộc
actor/company/version và dấu vết trạng thái duyệt, membership, policy. Khi xác nhận,
consume Redis atomic rồi kiểm tra lại DB dưới khóa trước insert. Mất quyền, policy mới,
đổi hồ sơ hoặc suspend làm challenge cũ không sử dụng được. Receipt lấy phí/wait/terms
từ version server, acceptedBy/time/IP từ server; `emailVerifiedAt` là thời điểm xác nhận
mã ký. Không tin X-Forwarded-For; hiện dùng direct peer IP. Cần thiết kế trusted proxy
trước khi dùng IP khách qua reverse proxy. Lỗi DB sau consume cần xin mã mới;
retry/concurrent không tạo hai receipt nhờ khóa và unique(company,version).

## Invitation/activation — P5

| API | Contract |
|---|---|
| POST /admin/staff/invitations | ADMIN: `{email,fullName,phone?,role}`; role chỉ support_staff/content_staff/accountant; 201 |
| POST /admin/staff/invitations/{accountId}/resend | ADMIN; 202 |
| POST /companies/{id}/guides/invitations | Manager trong company APPROVED: `{email,fullName,phone?,bio?}`; 201 |
| POST /companies/{id}/guides/invitations/{accountId}/resend | Manager đúng scope; 202 |
| POST /auth/activate-account | Public + CSRF: `{email,otp,newPassword}`; 200, chưa tạo phiên |

Kết quả mời/gửi lại: `{accountId,email,role,companyId,activationRequired,deliveryStatus}`.
`sent` là mail sender chấp nhận gửi, không phải bằng chứng hộp thư đã nhận;
`retry_required` nghĩa account đã lưu nhưng SMTP lỗi, có thể gửi lại sau cooldown.
Account/role/guide/audit tạo chung transaction trước khi gửi mail; email có sẵn luôn
409, không tự liên kết hay reset password. Chỉ invitation có provenance audit hợp lệ
mới kích hoạt được. Guide thuộc một company theo constraint hiện tại.

Credential ban đầu ngẫu nhiên BCrypt, không gửi password. Account chưa kích hoạt
không login/Google/refresh/nghiệp vụ, và không dùng verify-email/reset-password để
bỏ qua activation. Activation OTP riêng có TTL/attempt/send limits như trên,
ràng buộc account/email/role/company/password/session version, consume một lần;
đặt password mới, xác minh email và bỏ first-password flag cùng transaction.
Sau đó đăng nhập thông thường. Resend không làm thay đổi password account đã active.

## Migration và vận hành — P7

Thứ tự release: backup DB + private license storage → migration 001 → 002 → 003 →
app mới. Baseline và migration đã phát hành không sửa. Compose volume mới và
Testcontainers chạy đủ chuỗi; volume tồn tại phải apply 003 có chủ đích theo AUTH.md.
Không tự chạy migration vào DB dev/production trong task này.

Migration 003 thêm nullable financial terms trên policy_versions và constraint đầy đủ;
giữ nguyên account, company và receipt cũ. Legacy version thiếu fields vẫn đọc được,
không đủ điều kiện ký mới; phát hành version mới qua API. Rollback app giữ cột mới và
trigger, đóng luồng ký/publish nếu app cũ không hiểu contract. Không cố xóa receipt
hoặc rollback dữ liệu bằng truncate. Session version của 002 vẫn phải được kiểm tra.

Compose dùng PostgreSQL 18.6, Redis 8.2-alpine. `COMPANY_LICENSE_DIRECTORY` ở host
mặc định `data/company-licenses`, được bỏ qua bởi Git và nằm ngoài thư mục build.
Compose override `/app/company-licenses`
gắn named volume riêng, runtime user có quyền ghi. Backup volume cùng DB;
không dùng `docker compose down -v` trên môi trường cần giữ dữ liệu.

CI `.github/workflows/verify.yml` chạy wrapper clean verify trên JDK21/Docker,
kiểm Compose rồi mới build image; không publish/deploy. Log test được lưu làm artifact.
Host run dùng JDK21, không dùng Java mặc định khác phiên bản.

## Phần tích hợp còn phụ thuộc thành viên khác

Frontend đã được cập nhật tại `D:\projects\abc\sba-tour-trekking-fe`, gồm auth thật
và workspace “Tài khoản & đối tác”. Hướng dẫn FE nằm trong `docs/MODULE_1.md` của repo FE.
Access token chỉ trong bộ nhớ; refresh cookie HttpOnly, CSRF đúng auth contract.
Refresh, login, Google và logout dùng Web Locks cùng origin để tránh refresh replay
giữa các tab. Tự khôi phục/refresh cần browser có Web Locks trên HTTPS/localhost;
browser thiếu tính năng này nhận thông báo yêu cầu đăng nhập lại thay vì gửi refresh
thiếu khóa. Response JSON/blob của phiên cũ bị loại bỏ khi logout/đổi tài khoản.

Trạng thái nghiệm thu hiện tại:

| Phase | Kết quả đợt này | Phần còn lại |
|---|---|---|
| P0 | Đã ghi quyết định P3/P4 được người dùng xác nhận và contract API | Quyền tài chính chi tiết cần M4/5; không tự cấp cho ACCOUNTANT |
| P1 | FE auth thật, OTP/reset/activation và refresh/reload/logout | Google credential thật và SMTP nhà cung cấp thật |
| P2 | Guard DB, account administration/audit và UI quản trị | Action tài chính và enforcement tại service liên module chưa tồn tại |
| P3 | Backend + FE + integration/UI smoke hồ sơ/giấy phép/approval | Áp dụng vào môi trường thật |
| P4 | Backend + FE + OTP/receipt bất biến + migration/test | M4 xác nhận nội dung policy thật và tiêu thụ receipt vào booking/đối soát |
| P5 | Backend + FE + invite/activate staff/guide và kiểm scope | Assignment/check-in do M2/3 triển khai tiếp |
| P6 | FE điều hướng/quyền Module1; contract dùng chung | Nghiệp vụ payment/review/accountant/AI và E2E liên module |
| P7 | Tests, build image, CI và runbook cục bộ đã chuẩn bị | Chạy CI trên hosted runner, migration/deploy môi trường thật và nghiệm thu P6 |

### Kiểm chứng và demo

Ngày 15/10/2026: toàn bộ `.\mvnw.cmd test` và lượt cuối `.\mvnw.cmd clean verify`
(hoàn tất khoảng 16:52 giờ Việt Nam) exit0 với **191 tests / 13 suites,
0 failures/errors/skipped**; JAR package và Docker image `trekking-module-one:verified`
build thành công. `docker compose config --quiet` cho cả Compose chính và test exit0.
Repo FE gốc sau đồng bộ: `npm.cmd run lint`,
`node --experimental-strip-types --test tests/auth-client.test.ts` (**16/16**) và
`npm.cmd run build` exit0. Build FE còn cảnh báo chunk >500KB; không chặn build.
CI mới chưa được chạy trên GitHub trong phiên này.

Kiểm tra browser trực tiếp với backend đóng gói, PostgreSQL/Redis/Mailpit tách riêng:
register → nhận mã → verify → login; create company → upload PDF → ADMIN download
→ approve; publish policy với 12.5%/48 giờ tường minh → OWNER OTP accept → receipt;
staff và guide invite → activate → login; forgot/reset → login password mới;
hai tab cùng restore và gọi protected list không mất session. Viewport390px không
tràn ngang. Review độc lập đã phát hiện và kiểm tra lại các sửa lỗi URL giấy phép,
lưu trữ ngoài target, legacy password reset, Redis delivery recovery, response cũ
sau đổi account và refresh nhiều tab. Automated tests không gửi mail thật.

Chạy lại môi trường HTTP test với Docker và JDK21:

```powershell
.\mvnw.cmd clean verify
.\tools\run_module_one_http_test.ps1
```

Ở terminal khác trong repo FE:

```powershell
$env:VITE_API_BASE_URL='http://127.0.0.1:8094/api/v1'
npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Script dùng DB `module_one_test` riêng (tmpfs), Redis riêng và Mailpit8027;
ADMIN giả `admin@module-one.invalid` / `SmokePassword123!` chỉ dùng trong test.
Các email `.invalid` được Mailpit giữ tại máy, không gửi ra ngoài. Dừng Java/FE
bằng Ctrl+C rồi `.\tools\run_module_one_http_test.ps1 -Stop` để dọn container test;
DB tmpfs sẽ mất. Script không áp dụng migration/fixture vào DB dự án.

Payment/review/tour/check-in business services chưa có trong backend tại thời điểm
đối chiếu. M2/3/4/5 cần gọi guard **tại service ghi dữ liệu thật**, không dựa capability
API hoặc ẩn nút FE. Webhook payment dùng xác thực riêng của M4, không user session.
M4/5 cần quyết định quyền xem/lập/duyệt/chi và phân tách người thao tác trước khi cấp
action tài chính. M6 truyền nguyên actor/scope. Vì vậy P6 liên module chưa thể được
nghiệm thu chỉ bằng các test Module 1. Live Google/SMTP và migration môi trường thật
là bước bàn giao riêng, không xem mail/token mocks là kết quả chạy thật.

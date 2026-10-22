# Đối chiếu tài liệu với API và code — 19/10/2026

> Đây là snapshot trước sửa lỗi. Tình trạng backend sau sửa và kết quả chạy lại
> được cập nhật tại [MODULE_1_BACKEND.md](MODULE_1_BACKEND.md).

## Kết luận

**Chưa đủ để nghiệm thu toàn hệ thống theo `phan_cong_sba.md`.** Backend hiện có
39 endpoint trong 8 controller, tập trung ở Module 1. Tour, booking, attendance,
payment, settlement, review, support và AI chưa có service/controller nghiệp vụ.
Entity, enum, constraint và trigger trong schema chỉ là nền dữ liệu.

**Riêng contract API Module 1: đã có đủ mapping được bàn giao, nhưng chưa hoàn
thành về hành vi.** Luồng invitation/activation thiếu ba Redis Lua resource bắt
buộc. Quyền payment/review/accountant và E2E liên module vẫn còn thiếu, đúng như
phần P6 mà tài liệu bàn giao đã ghi nhận.

Đây là báo cáo kiểm tra; không sửa code, schema, cấu hình hay dữ liệu nghiệp vụ.

## Phạm vi và cách đọc tài liệu

Đã đọc cả năm tài liệu đầu vào trong `docs`:

| Tài liệu | Cách dùng khi đối chiếu |
|---|---|
| [phan_cong_sba.md](phan_cong_sba.md) | Phạm vi chức năng của sáu module và quy tắc nghiệp vụ bắt buộc |
| [module_1_auth_account_authorization_plan.md](module_1_auth_account_authorization_plan.md) | Các phase P0–P7, task và gate nghiệm thu Module 1; các bảng hiện trạng 11–12/10 là lịch sử |
| [MODULE_1_AUTHORIZATION.md](MODULE_1_AUTHORIZATION.md) | Contract authorization/account administration đợt đầu |
| [MODULE_1_IMPLEMENTATION.md](MODULE_1_IMPLEMENTATION.md) | Contract mới hơn ngày 15/10; dùng quyết định và API tại đây cho P3/P4/P5 |
| [AUTH.md](AUTH.md) | Luồng auth, CSRF/cookie/session/Google, migration và kiểm thử |

PRD V2.0 đầy đủ được các tài liệu dẫn chiếu không có trong repository. Kết luận
chỉ áp dụng cho nội dung thực sự có trong năm tài liệu này, không xác nhận toàn
bộ PRD chưa được cung cấp. Các endpoint ở phần "Draft API" của kế hoạch không
được coi là tên route bắt buộc nếu contract bàn giao mới đã thay thế.

Ví dụ: route policy đủ điều kiện hiện là
`GET /companies/{id}/agreement-policies`; không báo thiếu route draft cũ
`/companies/{id}/agreements/eligible-policy`.

## Các thiếu sót cần xử lý

### 1. Mức cao: invitation/activation thiếu ba Lua resource

[RedisInvitationStore](../src/main/java/com/trekking/app/features/invitation/repository/RedisInvitationStore.java)
dòng 28 tải resource `redis/invitation-<name>.lua`. Các lời gọi dòng 37, 41, 44
yêu cầu:

- `src/main/resources/redis/invitation-issue.lua`
- `src/main/resources/redis/invitation-consume.lua`
- `src/main/resources/redis/invitation-cancel.lua`

Cả ba file đều không có trong source, Git index và `target/classes/redis` khi kiểm
tra. Thư mục source chỉ có bảy script phục vụ auth cũ.

Kịch bản ảnh hưởng: ADMIN mời staff hoặc manager mời guide. Transaction tạo
account/role/guide/audit đã commit trước khi
[InvitationServiceImpl](../src/main/java/com/trekking/app/features/invitation/service/impl/InvitationServiceImpl.java)
dòng 163 gọi `store.issue`; resource không tải được nên không đi tới gửi mail ở
dòng 166. Resend và activation cũng phụ thuộc các script bị thiếu. Có mapping
API không bảo đảm AU-05 sử dụng được.

Cần bàn giao ba script với TTL 300 giây, cooldown 60 giây, tối đa năm lần gửi/giờ,
năm lần nhập sai, context đúng account/email/role/company và consume atomic,
rồi chạy lại toàn bộ test trên checkout có đủ resource.

### 2. Chưa có các API và service của Module 2–6

Các yêu cầu thiếu được liệt kê trong ma trận phía dưới. Chưa thể chạy luồng
duyệt đối tác → đăng tour/lịch → đặt nhóm → thanh toán → check-in → hoàn thành
→ review → đối soát/chi. Đây là phạm vi chưa triển khai của hệ thống, không phải
khẳng định các endpoint chưa tồn tại đang bị truy cập trái phép.

### 3. Authorization liên module và phân quyền kế toán chưa hoàn tất

[Permission.java](../src/main/java/com/trekking/app/core/security/authorization/Permission.java)
dòng 3–5 mới có action account, company, policy và đọc lịch của guide. Chưa có
action tài chính xem/lập/duyệt/chi hay quy tắc phân tách người thao tác.

Guard guide/company đã có và có test. Tuy nhiên,
[AuthorizationIntegrationTests](../src/test/java/com/trekking/app/AuthorizationIntegrationTests.java)
dòng 268 dùng controller dành riêng cho test. Kết quả này chưa chứng minh guard
được gọi tại service payment, review, check-in hoặc AI thực tế vì các service đó
chưa tồn tại. Đây là phần P6 còn lại của AU-01/AU-02/AU-05.

### 4. Runbook HTTP demo tham chiếu script không có

[MODULE_1_IMPLEMENTATION.md](MODULE_1_IMPLEMENTATION.md) dòng 177 và 190 hướng
dẫn chạy/dừng `tools/run_module_one_http_test.ps1`, nhưng file không tồn tại và
thư mục `tools` hiện rỗng. Người nhận checkout chưa thể dựng lại môi trường demo
theo hướng dẫn. Cần bổ sung script cùng cấu hình phụ thuộc hoặc cập nhật bằng
các lệnh thực sự có trong repository.

### 5. Chuẩn kiến trúc vẫn có điểm chưa theo AGENTS.md

Các controller nghiệp vụ đang dùng service interface; DTO request/response được
viết bằng record và không trả entity trực tiếp. Tuy nhiên:

- `OtpMailSender` và `SmtpOtpMailSender` nằm trong `features/auth/service` và
  `features/auth/service/impl`, trong khi quy ước yêu cầu tách feature `otp`.
- `AuditWriter` là `@Service` cụ thể trong `features/account/service/audit`, chưa
  có interface trực tiếp dưới `service` và implementation dưới `service/impl`.
- `AccountTransactions`, `GoogleAccountTransactions`, `SessionAccounts` là các
  `@Service` cụ thể chưa có interface tương ứng.

Đây là sai khác với chuẩn tổ chức code được cung cấp, tách biệt với lỗi runtime
invitation và phần thiếu chức năng nghiệp vụ.

## Ma trận Module 1

"Đã có" dưới đây nghĩa là đã đối chiếu controller → service → persistence và
test liên quan; không thay thế nghiệm thu môi trường thật.

| Yêu cầu | Trạng thái backend | Bằng chứng / phần còn lại |
|---|---|---|
| AU-01: register/login/email OTP/forgot/reset | Đã có | `AuthController`, `AuthServiceImpl`, `AccountTransactions`; thêm Google ID-token verification; quyền thanh toán/đánh giá chưa tích hợp |
| AU-02: JWT/refresh/logout/authorization/block có lý do | Đã có phần Module 1 | Đọc role/status từ DB; session fingerprint và session version; block/unblock/role whitelist/audit; thiếu action tài chính và enforcement ở nghiệp vụ Module 2–6 |
| AU-03: hồ sơ/trạng thái duyệt đối tác | Đã có | Create/list/detail/update/resubmit, giấy phép private, admin approval/reject/suspend; membership, company scope, audit/locking |
| AU-04: chấp nhận bằng mã email, version/time/IP/snapshot | Đã có | OWNER active, company APPROVED; OTP riêng; policy eligibility; snapshot tài chính từ server; receipt bất biến và chống tạo trùng; booking/settlement chưa tiêu thụ receipt |
| AU-05: cấp guide/staff, first password, giới hạn scope | Có code/API nhưng đang bị chặn | Whitelist/quyền/provenance/transaction/activation có code; thiếu ba Lua resource nên không hoàn thành luồng gửi mã/kích hoạt |

Điểm biên cần bổ sung kiểm chứng riêng: OWNER nhận OTP ký → ADMIN bỏ
`COMPANY_MANAGER` → cấp lại role trong TTL → gửi OTP cũ. `replaceRoles` không
đổi account session version, còn fingerprint ký ở `AgreementServiceImpl` dòng
166–171 không chứa revision của role. Đọc mã cho thấy challenge có thể hợp lệ
trở lại sau cấp lại quyền. Chưa chạy tái hiện kịch bản này; cần làm rõ và kiểm thử
nếu yêu cầu "mất quyền làm challenge cũ không sử dụng được" bao gồm thu hồi rồi
cấp lại quyền. Không coi đây là lỗi runtime đã được bộ test hiện tại xác nhận.

## Ma trận các yêu cầu còn lại

| Nhóm / mã yêu cầu | Trạng thái | Phần API và xử lý còn thiếu |
|---|---|---|
| M2 — PA-01 | Chưa có API | CRUD tour, ảnh, route GeoJSON/GPX, dịch vụ, packing items, chính sách hủy |
| M2 — PA-02 | Chưa có API | Submit/review/publish/reject tour, duyệt lại giá/chính sách |
| M2 — PA-03 | Chưa có API | Lịch, capacity, giá/phụ thu, cọc, deadline, assignment guide |
| M2 — PA-04 | Chưa có API | Đơn/hành khách theo lịch, export Excel có phân quyền |
| M2 — PA-10 | Chưa có API | Gói dụng cụ và phụ thu theo người/chuyến |
| M2 — PA-11 | Chưa có API | Confirm/cancel/complete schedule và liên hệ vận hành |
| M2 — CU-01 | Chưa có API | Search/filter/sort tour |
| M2 — CU-02 | Chưa có API | Public tour detail/lịch/chỗ/giá/policy/review/partner |
| M3 — CU-03 | Chưa có API | Cart và kiểm tra lại giá/chỗ |
| M3 — CU-04 | Chưa có API/service | Group booking, participants, giữ chỗ 15 phút, idempotency, chống oversell và nhả chỗ |
| M3 — CU-08 | Chưa có API | Một gói dụng cụ chung, server tính lại tổng tiền |
| M3 — CU-14 | Chưa có API | Mã/QR booking, passenger status và contact sau xác nhận |
| M3 — GU-01 | Chỉ có guard nền | Chưa có endpoint lịch được giao và hành khách |
| M3 — GU-02 | Chưa có API | Lookup booking bằng mã/QR, check-in từng người, lịch sử |
| M3 — GU-04 | Chưa có API | Actual completion và no-show |
| M3 — GU-06 | Chưa có API | Sửa check-in có reason/history và chuyển staff sau completion |
| M4 — CU-05 | Chưa có API/integration | VNPay/payOS, callback/webhook xác thực, hiển thị paid/remaining |
| M4 — CU-06 | Chưa có API | Manual payment notice, đối chiếu và xác nhận kế toán |
| M4 — CU-07 | Chưa có API/worker | Cọc/trả đủ, deadline, nhắc email |
| M4 — CU-10 | Chưa có API | Refund estimate, cancel/refund tracking |
| M4 — PA-08 | Chưa có API | Partner settlement theo đơn, điều chỉnh/khiếu nại |
| M4 — ST-05 | Chưa có API | Xác nhận thu, duyệt/ghi hoàn, lập/duyệt đối soát và chứng từ chi |
| M4 — AD-02 | Có một phần nguồn policy | Admin tạo/publish cooperation policy với fee/wait/terms; chưa có đầy đủ cấu hình nghiệp vụ tiền, kỳ chi, exception, booking hold và engine áp dụng |
| M4 — AD-03 | Chưa có API | Báo cáo thu/hoàn/commission/fee/payable/paid |
| M5 — CU-11 | Chỉ có entity/schema | Trigger/unique bảo vệ một review/booking và eligibility; chưa có review API/service |
| M5 — CU-13 | Chưa có API | Support request theo booking, attachment và history |
| M5 — ST-03 | Chưa có API | Assignment/status/history của financial support |
| M5 — ST-04 | Có một phần | Có company approval và account block; chưa có tour approval/review moderation và toàn bộ xử lý vi phạm |
| M5 — ST-07 | Chưa có API | Attendance/completion correction có evidence/reason, giữ chi |
| M5 — AD-01 | Có một phần, invitation bị chặn | Có account đọc/block/unblock/replace roles và code staff invitation; thiếu phân tách action tài chính; invite thiếu Lua |
| M6 — ST-01 | Chưa có API | AI handoff, staff summary/context theo scope, chuyển kế toán |
| M6 — ST-02 | Chỉ có entity/schema | Chưa có upload/extract/index/update/delete tài liệu AI và pipeline RAG |
| M6 — AD-04 | Chưa có API | AI confidence/config/actions/logging, bật/tắt AI |
| M6 — chatbot theo mục 6 | Chưa có service/integration | Chưa có chat/RAG, vector retrieval, lấy dữ liệu sống, source citation và xác nhận trước action |

Các task được ghi rõ là mở rộng trong `phan_cong_sba.md` — PA-09, CU-15, PA-12,
CU-12 và tối ưu AI — cũng chưa có API; không dùng chúng làm điều kiện chặn
nghiệm thu phạm vi bắt buộc.

## Danh mục 39 endpoint hiện có

Mọi path dưới đây có tiền tố `/api/v1`. Đếm theo cặp HTTP method/path khai báo
trong production controller; không tính route framework, Swagger, HEAD/OPTIONS
ngầm định hoặc controller dành riêng cho test.

| Controller | Endpoint |
|---|---|
| `AuthController` — 11 | `POST /auth/register`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/login`, `/auth/google`, `/auth/refresh`, `/auth/logout`; `GET /auth/me`, `/auth/csrf` |
| `AccountAuthorizationController` — 1 | `GET /accounts/me/authorization` |
| `AdminAccountController` — 4 | `GET /admin/accounts/{id}`; `POST /admin/accounts/{id}/block`, `/admin/accounts/{id}/unblock`; `PUT /admin/accounts/{id}/roles` |
| `CompanyController` — 7 | `POST /companies`; `GET /companies`, `/companies/{id}`; `PUT /companies/{id}`; `POST /companies/{id}/resubmit`, `/companies/{id}/license`; `GET /companies/{id}/license` |
| `CompanyAdministrationController` — 4 | `GET /admin/companies`, `/admin/companies/{id}`, `/admin/companies/{id}/license`; `POST /admin/companies/{id}/decision` |
| `PolicyController` — 2 | `POST /admin/policies`, `/admin/policies/{policyId}/versions` |
| `AgreementController` — 5 | `GET /companies/{companyId}/agreement-policies`; `POST /companies/{companyId}/agreements/otp`, `/companies/{companyId}/agreements`; `GET /companies/{companyId}/agreements`, `/companies/{companyId}/agreements/{agreementId}` |
| `InvitationController` — 5 | `POST /admin/staff/invitations`, `/admin/staff/invitations/{accountId}/resend`, `/companies/{companyId}/guides/invitations`, `/companies/{companyId}/guides/invitations/{accountId}/resend`, `/auth/activate-account` |

## Frontend và bằng chứng bàn giao

Bản frontend local `.frontend-work` có auth client thực, Web Locks/CSRF/cookie/
Bearer và màn hình company, moderation, account admin, policy, agreement,
invitation. Tuy nhiên, thư mục này bị Git ignore, không nằm trong checkout backend
được theo dõi. Đường dẫn repo FE ngoài workspace do tài liệu dẫn chiếu tồn tại;
chưa kiểm tra nội dung hoặc đồng bộ ở repo đó trong lượt này.

Các feature ngoài Module 1 trong bản local vẫn dùng demo data; ví dụ booking dùng
localStorage và payment tự tạo trạng thái client-side. Đây không phải bằng chứng
đã có payment backend. Không chạy lại lint/build/test FE hoặc browser E2E.

## Kiểm chứng ngày 19/10/2026

Đã kiểm tra bằng Maven wrapper 3.9.16, JDK Microsoft 21.0.8, Docker Desktop,
PostgreSQL 18.6 và Redis 8.2-alpine trên container test riêng. Mail và Google token
ở integration test được mock, không gửi mail thật hoặc dùng credential Google thật.

Lệnh chạy:

```powershell
$env:JAVA_HOME = 'C:\Users\Windows\.jdks\ms-21.0.8'
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

Hoàn tất lúc **13:35:45 giờ Việt Nam ngày 19/10/2026**, thời gian Maven khoảng
3 phút 48 giây: **exit 1; 191 tests / 13 suites; 0 assertion failures, 14 errors,
0 skipped**. Có 177 test qua; cả 14 errors đều trong `InvitationIntegrationTests`
(suite này có 27 test). Mười hai suite còn lại không có failure/error.

Lỗi quan sát được: `ScriptingException: Error reading script text`, nguyên nhân
resource `redis/invitation-issue.lua` không tồn tại. Kết quả thực thi xác nhận
thiếu resource chặn luồng invitation; không coi đây là SMTP/Google chưa cấu hình.
Chi tiết ở `target/surefire-reports/TEST-com.trekking.app.InvitationIntegrationTests.xml`.

Tài liệu bàn giao ghi nhận **191 tests pass ngày 15/10**; đó là kết quả lịch sử,
không còn đại diện cho checkout hiện tại. Lượt audit này chạy `test`, không chạy
lại `clean verify`/package sau khi đã xác nhận lỗi. Source và `target/classes`
đều được kiểm tra không có ba invitation Lua resource nên kết luận thiếu file
không phụ thuộc vào resource cũ còn sót trong `target`.

`docker compose config --quiet`: **exit 0**. Đây là kiểm tra cú pháp/cấu hình,
không chứng minh deployment hay các nghiệp vụ còn thiếu đã hoạt động.

Fresh database test chạy baseline → 001 → 002 → 003. Migration tests có synthetic
legacy account/company/receipt và kiểm tra bảo toàn dữ liệu/constraint. CI đã có
gate JDK21 `clean verify` trước Compose validation và Docker build; chưa chạy lại
hosted GitHub CI hoặc build/deploy image trong lượt audit này.

## Thứ tự hoàn thiện đề xuất

1. Bổ sung Lua resource invitation, chạy toàn bộ test và tái hiện invite → nhận
   mã → activate → login cho cả staff và guide.
2. Bàn giao lại runbook HTTP demo có đủ script/cấu hình; cập nhật bằng chứng test
   theo checkout hiện tại.
3. Nếu chỉ nghiệm thu Module 1: chốt các phần P6 với M2–M6, action kế toán, quyền
   payment/review và phạm vi FE/E2E; kiểm chứng Google/SMTP và migration trên môi
   trường bàn giao.
4. Nếu nghiệm thu cả hệ thống: triển khai API/service Module 2–6 theo ma trận,
   thống nhất contract/state/ownership trước tích hợp, rồi kiểm thử toàn luồng và
   các tình huống đồng thời/duplicate callback/late payment/refund.

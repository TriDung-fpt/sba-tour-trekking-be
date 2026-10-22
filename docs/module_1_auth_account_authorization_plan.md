# Kế hoạch hoàn thành Module 1 — Authentication, Account & Authorization

> Backend bổ sung và kiểm chứng hiện tại: [MODULE_1_BACKEND.md](MODULE_1_BACKEND.md).
> Guard P6 đã có contract dùng chung; việc gọi guard từ service nghiệp vụ M3–M5
> và E2E toàn hệ thống vẫn phụ thuộc các module tương ứng.

> Tiến độ cập nhật 15/10/2026 ở [bàn giao triển khai](MODULE_1_IMPLEMENTATION.md).
> Các bảng hiện trạng và checklist lịch sử phía dưới mô tả snapshot 11–12/10.
> P3/P4/P5 đã có backend, frontend và test; P6/payment/review liên module cùng
> live Google/SMTP/migration môi trường thật vẫn cần nghiệm thu riêng.

**Người phụ trách:** Member 1.  
**Ngày review:** 11/10/2026.  
**Nguồn phạm vi:** [Phân công SBA](phan_cong_sba.md), mục Member 1, AU-01 đến AU-05 và phần phối hợp liên module.  
**Cách lập kế hoạch:** workflow-team; review độc lập backend, dữ liệu và kiểm thử, sau đó tổng hợp thành các phase.

Tài liệu này là backlog triển khai tiếp theo dựa trên source hiện có, bao gồm cả thay đổi auth/Google chưa commit trong workspace. Các endpoint, tên service và quy tắc được ghi là **đề xuất** chưa phải API đã triển khai. Chưa tìm thấy bản PRD V2.0 đầy đủ trong repository; các điểm chưa rõ phải chốt ở Phase 0, không xem suy luận từ file phân công là yêu cầu đã được duyệt.

Theo file phân công, Member 1 chịu trách nhiệm **Backend + Frontend + kiểm thử** phần được giao. Repository hiện tại là backend Spring Boot; chưa thấy frontend manifest. Công việc FE dưới đây thực hiện tại repository giao diện của nhóm sau khi xác định nơi tích hợp, không mặc định thêm React hoặc Flutter vào repository này.

## 1. Kết quả review hiện trạng

Stack thực tế: Java 21, Spring Boot 4.1.1, Maven wrapper, Spring Security, PostgreSQL, Redis, Jakarta Validation, JUnit/MockMvc/Testcontainers. Phần lớn feature ngoài account mới có entity/enums; controller nghiệp vụ hiện có là `AuthController`.

| Phạm vi | Đã có trong source | Còn thiếu để nghiệm thu Module 1 |
|---|---|---|
| AU-01: xác thực | Đăng ký customer, OTP xác minh email, login, resend, forgot/reset password; BCrypt; validation đầu vào | FE và kiểm thử tích hợp giao diện; quyền thanh toán/đánh giá gắn với dữ liệu thật |
| Google login — yêu cầu bổ sung | Xác minh Google ID token, lưu `sub`, tạo/liên kết tài khoản có kiểm tra, dùng chung session của app | Cấu hình client ID, migration môi trường, kiểm thử credential thật; chốt phạm vi email bên thứ ba |
| AU-02: phiên đăng nhập | JWT interface/implementation; access token; refresh cookie; Redis session; rotation/replay protection; logout; thu hồi hiệu lực sau reset mật khẩu | Authorization cho bảy role và ownership; API khóa/mở khóa có lý do và audit |
| AU-02: role | Có đủ bảy giá trị `RoleType`; mỗi request đọc lại role và trạng thái tài khoản từ DB | Chưa có guard nghiệp vụ theo permission/company/schedule; chưa có API quản trị quyền |
| AU-03: đối tác | Entity/schema `Company`, `CompanyMember`; trạng thái pending/approved/suspended/rejected; trường người duyệt, thời điểm, lý do | Repository, service, validation, API và FE nộp/sửa/xem hồ sơ, chuyển trạng thái, phối hợp duyệt |
| AU-04: điều khoản | `Policy`, `PolicyVersion`, `CompanyAgreement`; version, người ký, thời điểm, IP và snapshot điều khoản | OTP riêng cho chấp nhận điều khoản, ràng buộc đúng company/version/người ký, API ký và biên nhận |
| AU-05: tài khoản được cấp | `Guide`, membership, role và `must_change_password`; login/Google/session đều chặn tài khoản phải đổi mật khẩu | Cấp/mời tài khoản guide và staff, hoàn tất mật khẩu lần đầu, xác minh email, giới hạn người cấp và phạm vi dữ liệu |
| Audit | Có entity và bảng `audit_logs`; DB chặn sửa/xóa audit | Chưa có audit writer/service và luồng ghi audit cùng giao dịch nghiệp vụ |

**Bằng chứng chính trong source:**

- [AuthController](../src/main/java/com/trekking/app/features/auth/controller/AuthController.java), [AuthServiceImpl](../src/main/java/com/trekking/app/features/auth/service/impl/AuthServiceImpl.java), [AccountTransactions](../src/main/java/com/trekking/app/features/auth/service/impl/AccountTransactions.java).
- [SecurityConfig](../src/main/java/com/trekking/app/core/config/SecurityConfig.java), [RoleType](../src/main/java/com/trekking/app/features/account/enums/RoleType.java), [SessionJwtAuthenticationConverter](../src/main/java/com/trekking/app/core/security/SessionJwtAuthenticationConverter.java).
- [AuthValidation](../src/main/java/com/trekking/app/features/auth/validation/AuthValidation.java), [RedisAuthStore](../src/main/java/com/trekking/app/features/auth/repository/RedisAuthStore.java), [OtpPurpose](../src/main/java/com/trekking/app/features/auth/enums/OtpPurpose.java).
- [Company](../src/main/java/com/trekking/app/features/company/entity/Company.java), [CompanyAgreement](../src/main/java/com/trekking/app/features/company/entity/CompanyAgreement.java), [Guide](../src/main/java/com/trekking/app/features/company/entity/Guide.java), [AuditLog](../src/main/java/com/trekking/app/features/account/entity/AuditLog.java).
- [Schema hiện tại](../schema/schema.sql), [migration Google](../schema/migrations/001_add_google_subject.sql), [hướng dẫn auth](AUTH.md).

**Bằng chứng kiểm thử:** lần `clean verify` trước trong cùng phiên làm việc dùng JDK 21 đã thành công. Báo cáo `target/surefire-reports` ngày 11/10/2026, khoảng 20:41 giờ Việt Nam, gồm **81 tests, 0 failures, 0 errors, 0 skipped** trong tám suite. Bao gồm auth cũ, JWT, validation, Google verifier, Google integration và migration PostgreSQL. Lượt lập tài liệu này đọc lại báo cáo và source, không chạy lại Maven. Kết quả trên chưa chứng minh AU-03/04/05 hoặc authorization liên module đã hoàn thành.

## 2. Những điểm phải xử lý hoặc chốt trước khi mở rộng

| Mã review | Nhận định có bằng chứng | Hành động / phase |
|---|---|---|
| R-01 | Có `@EnableMethodSecurity`, nhưng chưa có `@PreAuthorize`/guard nghiệp vụ; route ngoài auth hiện chỉ yêu cầu authenticated | Xây permission và ownership trước các API mới — P2. Đây là thiếu chức năng cần làm, chưa phải bằng chứng API tour/payment đang bị truy cập trái phép vì chúng chưa tồn tại |
| R-02 | `/api/v1/auth/**` đang permitAll | Endpoint quản trị mới đặt dưới namespace có guard; nếu thêm endpoint nhạy cảm dưới auth phải sửa matcher/guard rõ ràng — P2/P5 |
| R-03 | `must_change_password` chặn login đúng; reset password chỉ bỏ flag, không tự đặt `email_verified_at` | Thiết kế activation xác minh hộp thư + đặt mật khẩu lần đầu đầy đủ — P5 |
| R-04 | OTP chỉ có VERIFY_EMAIL và RESET_PASSWORD; key/context hiện phục vụ hai luồng này | Thêm purpose/challenge riêng cho AU-04/AU-05; không tái sử dụng mã xác minh email để ký điều khoản — P4/P5 |
| R-05 | FK của reviews yêu cầu tác giả là chủ booking và là participant; trigger yêu cầu booking/chuyến completed và có check-in | Chốt với Member 3/5: chỉ chủ đơn đã tham gia hay mọi người tham gia được đánh giá? Nếu đổi quy tắc, Member 5 chủ trì migration tương thích — P0/P6 |
| R-06 | `ACCOUNTANT` là một role, chưa có biểu diễn quyền xem/lập/duyệt/chi riêng | Chốt action permission và quy tắc người lập/người duyệt/người chi với Member 4/5 — P0/P2/P6 |
| R-07 | Có schema company/agreement/guide nhưng chưa có nghiệp vụ tương ứng | Tận dụng bảng/entity hiện có, bổ sung migration chỉ khi thiếu ràng buộc thực sự — P3/P4/P5 |
| R-08 | `docs/AUTH.md` tham chiếu `tools/check_smtp.py`, `tools/test_api.py`, `tools/test_swagger.js`, `docker-compose.api-test.yml` chưa có trong snapshot repository | Sửa tài liệu hoặc bổ sung công cụ trước khi dùng các lệnh đó làm nghiệm thu — P1 |
| R-09 | Compose dùng `postgres:latest`, test dùng `postgres:18.6`; Dockerfile build với skipTests; chưa thấy CI | Chốt version môi trường và gate kiểm thử trước release — P7 |

Query `lower(trim(email))` ở repository phục vụ đọc dữ liệu email cũ; validation request và quyết định auth đã nằm ở DTO/validation/service. Tiếp tục giữ constraint/unique index của DB để bảo vệ tính toàn vẹn khi có request đồng thời.

## 3. Phân chia trách nhiệm với các thành viên khác

Member 1 là người chịu trách nhiệm hoàn thành các task M1 bên dưới. Vai trò BE/DB/FE/QA dùng để tách subtask, không có nghĩa nhóm phải có thêm người. Khi dùng agent, mỗi file có một người/agent ghi; Member 1 quản lý contract chung và thứ tự migration.

| Công việc giao nhau | Member 1 chịu trách nhiệm | Thành viên phối hợp / ranh giới |
|---|---|---|
| Hồ sơ và duyệt đối tác | Account/membership, hồ sơ, state machine và API dùng chung; đề xuất M1 là owner code luồng chuyển trạng thái company | M2 dùng trạng thái để cho phép vận hành tour; M5 làm màn hình/thao tác kiểm duyệt. Chốt owner ở P0 để không viết hai service duyệt |
| Khóa tài khoản | Service/API kiểm quyền, reason, audit, hiệu lực với session | M5 dùng API trong xử lý vi phạm, không tự cập nhật DB ngoài service |
| Nhân viên nền tảng | API cấp tài khoản, role/permission và activation | M5 sở hữu màn hình AD-01; M4 xác nhận quyền kế toán; M1 không làm lại toàn bộ Admin Dashboard |
| Hướng dẫn viên | Account/activation và kiểm tra scope guide/company | M2 quản lý gán lịch; M3 dùng assignment để xem khách/check-in; M1 không triển khai nghiệp vụ check-in |
| Điều khoản hợp tác | Chấp nhận bằng OTP, lưu bằng chứng/version/snapshot và cung cấp receipt | M4 phối hợp nguồn policy/commission/payout; M2/3 dùng agreement khi vận hành/đặt tour. M1 không thực hiện tính đối soát hoặc chi tiền |
| Quyền thanh toán | Actor/account guard dùng chung, contract ownership; hỗ trợ test quyền | M3 cung cấp booking owner/state; M4 enforce tại service tạo/xác nhận thanh toán và sở hữu nghiệp vụ tiền |
| Quyền đánh giá | Actor/account guard và contract xác định người được phép | M3 cung cấp participant/check-in/completion; M5 enforce tại service tạo review và sở hữu rule một bài/đơn |
| Truy cập qua AI | Contract actor và scope hiện tại | M6 truyền danh tính người dùng và dùng cùng guard; không có đặc quyền vượt scope chỉ vì request đến từ AI |

## 4. Tổng quan các phase

| Phase | Mục tiêu | Phụ thuộc chính | Gate hoàn thành |
|---|---|---|---|
| P0 | Chốt phạm vi, contract, quyền và owner | File phân công + source review | Các quyết định ảnh hưởng code đã được ghi rõ |
| P1 | Bàn giao auth hiện hữu và FE auth | P0 | Đăng nhập/OTP/refresh/reset chạy được trên giao diện, cấu hình và tài liệu khớp |
| P2 | Authorization, quản trị trạng thái account, audit | P0; tận dụng auth hiện hữu | Bảy role có kiểm tra action/scope; block/unblock có audit và test |
| P3 | Hồ sơ và trạng thái duyệt đối tác | P2 | Nộp → duyệt/từ chối → xem trạng thái đúng quyền |
| P4 | Chấp nhận điều khoản bằng OTP | P2 + P3 + contract policy | Bằng chứng chấp nhận đúng version, một lần, có IP/time/snapshot |
| P5 | Cấp tài khoản guide/staff, mật khẩu lần đầu | P2; phần guide cần company scope P3 | Tài khoản được cấp kích hoạt an toàn và chỉ vào đúng phạm vi |
| P6 | Tích hợp quyền thanh toán/đánh giá/kế toán và FE liên module | P2–P5 + contract M2/3/4/5/6 | Guard được gọi tại thao tác nghiệp vụ thật; các luồng E2E đúng quyền |
| P7 | Nghiệm thu, migration và bàn giao | P1–P6 | AU-01…AU-05 đủ bằng chứng BE + FE + test + vận hành |

P1 và P2 có thể thực hiện song song sau P0. P4 và phần staff của P5 có thể song song khi contract và file ownership đã ổn định. Các thành viên khác có thể phát triển bằng contract/fixture từ P0; không cần đợi toàn bộ Module 1 xong mới bắt đầu.

## 5. Công việc chi tiết

### Phase 0 — Chốt phạm vi và hợp đồng tích hợp

**Mục tiêu:** tránh xây xong auth nhưng thiếu phần account/authorization được phân công.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P0-01 | Contract | Chốt ma trận bảy role × action × scope với M2/3/4/5. Tách quyền tài khoản toàn nền tảng khỏi membership company và assignment schedule |
| M1-P0-02 | Contract | Chốt owner code cho company approval, account blocking, staff administration, policy/version và audit; ghi rõ service dùng chung, người review và migration owner |
| M1-P0-03 | Contract | Chốt R-05 quyền đánh giá; quyền ký company owner hay cả manager; trạng thái company được phép ký; cần ký trước hay sau approval; guide thuộc một company theo schema hiện tại |
| M1-P0-04 | Contract | Chốt API fields/status/error/pagination: giữ `{code,message,fieldErrors}`, 401/403/404/409; `page/size` và giới hạn size cho API danh sách; quy tắc ẩn resource không thuộc scope |
| M1-P0-05 | FE/QA | Xác định repo FE, nền tảng và người tích hợp. Chốt Google là phạm vi bổ sung nào: Gmail/Workspace đã có; email bên thứ ba cần luồng chứng minh sở hữu riêng nếu được yêu cầu |

**Nghiệm thu:** có decision log cho các điểm trên, draft API và fixture giả cho các thành viên; không mặc định mọi accountant có đủ quyền duyệt/chi hoặc mọi manager có quyền tạo staff.

### Phase 1 — Hoàn thiện bàn giao auth đang có

**Phạm vi:** phần đăng nhập của AU-01 và phần session của AU-02. Tái sử dụng code đã hoàn thành; không lập lại task tách JWT interface hoặc viết lại Google login.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P1-01 | BE/DB | Áp dụng migration Google đúng thứ tự trên môi trường dev/test cần dùng; cấu hình client ID, mail, CORS, cookie HTTPS. Giữ secret ngoài Git; migration DB hiện hữu là bước có chủ đích |
| M1-P1-02 | FE | Làm auth client dùng handshake CSRF, Bearer access token và HttpOnly refresh cookie; serialize refresh để tránh tự kích hoạt replay detection; xử lý 401/403/429/503 |
| M1-P1-03 | FE | Màn hình register/verify/resend/login/Google/forgot/reset/logout; trạng thái loading, lỗi field, OTP hết hạn và email chưa verify; `/me` dùng làm dữ liệu tài khoản hiện tại |
| M1-P1-04 | Docs/QA | Sửa các đường dẫn/lệnh tooling thiếu ở R-08; cập nhật OpenAPI, ví dụ body, lỗi và cách cấu hình. Test credential Google thật trên môi trường test sau khi có client ID |
| M1-P1-05 | QA | Giữ regression auth hiện có; bổ sung test FE cho reload, refresh đồng thời, logout, reset trên nhiều thiết bị, hết hạn và mất Redis/SMTP theo phạm vi thực tế |

**Nghiệm thu:** giao diện hoàn thành luồng register → verify → login → refresh → logout/reset; không lộ refresh token trong JSON. Google thất bại không tạo phiên; có ghi nhận kết quả kiểm thử thật hoặc nêu rõ bước còn thiếu. JWT/OTP/session tests hiện có tiếp tục pass.

### Phase 2 — Authorization, trạng thái tài khoản và audit

**Phạm vi:** AU-02, nền cho tất cả AU còn lại.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P2-01 | BE | Tạo bộ permission/guard theo ma trận P0. Enforce tại service thao tác dữ liệu; kết hợp role, account active, membership active, company status và assignment khi action cần. Không tin `accountId/companyId/roles` do client tự khai |
| M1-P2-02 | BE/DB | Thêm repository/service đọc membership/guide scope và contract authorization dùng chung. Chốt biểu diễn quyền kế toán chi tiết; chỉ thêm migration nếu mô hình role hiện tại không đáp ứng |
| M1-P2-03 | BE | API quản trị đọc account phù hợp quyền, block/unblock và gán/thu hồi role/permission theo whitelist. Bắt reason khi khóa; lưu actor từ principal, không nhận actor từ body. Chốt bảo vệ tài khoản quản trị cuối cùng nếu áp dụng |
| M1-P2-04 | BE/DB | Tạo audit writer/repository. Ghi actor/action/entity/reason/chi tiết cần thiết cùng transaction thay đổi account/role. Không ghi password, OTP hay token; giữ audit bất biến |
| M1-P2-05 | FE/QA | Cung cấp UI trạng thái/quyền cần thiết, phối hợp M5 cho màn hình quản trị. Test allow/deny theo action, đổi role khi còn JWT cũ, đổi ID cross-company, block giữa phiên và audit rollback |

**Nghiệm thu:** account bị khóa không login/refresh/truy cập tiếp; thay đổi quyền được áp dụng ở request tiếp theo theo cơ chế đọc DB hiện có. Người đúng role nhưng sai company/schedule vẫn bị từ chối. Account/role thay đổi thất bại không để lại audit thành công giả. Route mới được khai báo quyền rõ ràng, đặc biệt nếu nằm dưới `/auth/**`.

### Phase 3 — Hồ sơ và trạng thái duyệt đối tác

**Phạm vi:** AU-03; phối hợp M2 và M5.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P3-01 | DB/BE | Bổ sung company/member repository, DTO và validator trên bảng hiện có. Tạo hồ sơ và owner membership cùng transaction; nếu cấp global role COMPANY_MANAGER thì vẫn bắt buộc scope cho mọi action |
| M1-P3-02 | BE | API nộp/sửa/xem hồ sơ và danh sách company được phép quản lý; validation mã số thuế/contact/hồ sơ theo contract. Whitelist trường ghi: client không tự đặt approved/status/approvedBy |
| M1-P3-03 | BE | Triển khai state machine dựa trên pending/approved/rejected/suspended hiện có; chốt nộp lại sau reject và sửa sau approve. Duyệt/từ chối/đình chỉ có permission, reason, actor, timestamp và audit |
| M1-P3-04 | FE/QA | FE partner profile/status; M5 tích hợp moderation UI từ cùng API. Test hồ sơ trùng, chuyển trạng thái trái phép, duyệt đồng thời, company khác và membership bị vô hiệu hóa |

**Nghiệm thu:** partner nộp và theo dõi hồ sơ; người duyệt hợp lệ ra quyết định; M2 nhận trạng thái và eligibility chính xác. Không cho đăng/vận hành tour chỉ dựa vào global role. Hạ tầng lưu hồ sơ/giấy phép dùng cơ chế upload đã thống nhất với nhóm, không chỉ tin một URL bất kỳ từ client.

### Phase 4 — Chấp nhận điều khoản bằng mã email

**Phạm vi:** AU-04. Phụ thuộc nguồn policy/version được duyệt và company signing scope đã chốt.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P4-01 | BE | API đọc policy version đủ điều kiện ký và receipt đã ký. Kiểm tra đúng loại cooperation, active, approval/effective time và company/global scope theo P0; không chỉ kiểm tra version ID tồn tại |
| M1-P4-02 | BE | Thêm purpose/challenge chấp nhận điều khoản; mã có TTL, giới hạn lần thử/gửi và consume atomic. Context ràng buộc account, company, policyVersion và trạng thái bằng chứng cần thiết; email do server chọn theo người ký đã xác minh |
| M1-P4-03 | BE/DB | Xác nhận OTP rồi kiểm tra lại quyền/trạng thái/version và ghi agreement. Server tạo acceptedBy/acceptedAt/IP/emailVerifiedAt, fee/wait-hours/terms snapshot từ nguồn được duyệt; không lấy snapshot tài chính từ body |
| M1-P4-04 | BE/QA | Tận dụng unique(company,version), trigger immutable và transaction. Xử lý concurrent accept/retry không tạo hai receipt; nếu DB thất bại sau consume OTP, trả lỗi có thể phục hồi bằng yêu cầu mã mới, không báo đã ký |
| M1-P4-05 | FE/QA | FE đọc điều khoản → yêu cầu mã → xác nhận → receipt. Test OTP sai purpose/company/version/account, hết hạn/reuse, mất quyền giữa gửi và xác nhận, version đổi, snapshot bị giả và IP header bị giả |

**Nghiệm thu:** mỗi agreement lưu đủ phiên bản, người ký, thời điểm, IP từ request/proxy tin cậy và snapshot; `email_verified_at` phản ánh lần xác minh mã ký vừa hoàn tất, không chỉ sao chép thời điểm verify tài khoản cũ. Không sửa/xóa bằng chứng đã ký. Mã verify-email/reset không ký được điều khoản. M3/4 lấy đúng agreement/version khi tạo snapshot booking/đối soát. Không hardcode điều khoản tài chính chỉ vì schema có default 10%/72 giờ.

### Phase 5 — Cấp tài khoản hướng dẫn viên và nhân viên

**Phạm vi:** AU-05; phối hợp M2/M3/M5, quyền kế toán phối hợp M4.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P5-01 | BE/DB | Thiết kế invitation/activation và trạng thái vòng đời. Chốt lưu challenge Redis hay bảng nếu cần lịch sử invitation; không mặc định tạo bảng mới. Cấp staff bởi người có quyền quản trị; cấp guide chỉ trong company được phép |
| M1-P5-02 | BE | Tạo account + role + guide/membership cần thiết trong transaction, gửi lời mời sau commit có khả năng resend. Email đã tồn tại không được tự reset password, chiếm account hoặc cấp quyền vượt thẩm quyền; chốt luồng liên kết có xác nhận |
| M1-P5-03 | BE | Endpoint activation dùng challenge hẹp, có hạn, một lần để xác minh hộp thư và đặt mật khẩu ban đầu rồi bỏ must_change_password. Trước khi hoàn tất không cấp session nghiệp vụ; không giả định reset-password hiện tại đã verify email |
| M1-P5-04 | BE | Guard guide cần account active + guide active + company/schedule phù hợp; staff dùng permission P2. Vô hiệu hóa guide/membership hoặc thu hồi role phải ngăn truy cập tiếp |
| M1-P5-05 | FE/QA | FE invitation/first-password; phối hợp M5 staff UI và M2 guide UI. Test OTP/token hết hạn/replay, gửi mail lỗi, concurrent creation, email có sẵn, rollback, manager tự cấp admin/accountant, Google bypass flag và guide sai lịch |

**Nghiệm thu:** có luồng cấp → nhận lời mời → xác minh → đặt mật khẩu → truy cập đúng scope. Không gửi hoặc lưu mật khẩu thô; gửi lại invitation không làm mất password của tài khoản đang hoạt động. Guide thuộc một company theo constraint hiện hành; nếu sản phẩm cần nhiều company phải chốt lại và có migration trước triển khai.

### Phase 6 — Tích hợp quyền nghiệp vụ và hoàn thiện giao diện

**Phạm vi:** phần quyền thanh toán/đánh giá trong AU-01 và phần authorization dùng chung của AU-02/AU-05.

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P6-01 | BE/QA | Cùng M3/4 gắn guard actor + booking ownership/state tại service thanh toán. Phân biệt request khách tạo thanh toán với webhook do M4 xác thực; không bắt webhook dùng session người dùng và không cho client tự xác nhận tiền |
| M1-P6-02 | BE/QA | Cùng M3/5 kiểm quyền review theo quyết định R-05: danh tính participant, check-in, booking/chuyến hoàn thành, một review/booking. Enforce lại lúc ghi dữ liệu, không chỉ dựa API eligibility cho FE |
| M1-P6-03 | BE/QA | Cùng M4/5 tích hợp action xem/lập/duyệt/chi và phân tách người thao tác đã chốt; M4 sở hữu state/transaction tiền, M1 cung cấp identity/permission và test phủ nhận |
| M1-P6-04 | FE | Hoàn thiện điều hướng theo role, company context và trạng thái bị khóa/chờ duyệt/phải đặt password/ký điều khoản. Ẩn nút theo quyền để hỗ trợ UX; BE vẫn enforce mọi action |
| M1-P6-05 | QA/Contract | E2E với M2/3/4/5; contract test với M6 đảm bảo actor/scope không bị mở rộng. Giao fixture/test account từng role trong môi trường test; tài liệu hóa payload/error và quyết định cuối |

**Nghiệm thu:** customer không thao tác đơn người khác; guide không xem/check-in lịch chưa được giao; partner không đọc dữ liệu company khác; account chưa đủ điều kiện không thanh toán/đánh giá. Kế toán không tự có mọi action chỉ nhờ role ACCOUNTANT. Quyền bị thu hồi vẫn có hiệu lực dù trình duyệt còn nút hoặc JWT cũ.

### Phase 7 — Kiểm thử cuối, migration và bàn giao

| ID | Loại | Công việc và đầu ra |
|---|---|---|
| M1-P7-01 | QA | Chạy unit/integration toàn bộ bằng JDK 21 + PostgreSQL/Redis test riêng; thêm test theo từng phase, không coi 81 test hiện tại là đủ cho code mới. Chạy FE/E2E và contract test liên module |
| M1-P7-02 | DB/DevOps | Kiểm migration từ baseline và từ schema trước release với dữ liệu giả có account/company/agreement cũ. Chốt version PostgreSQL/Redis môi trường thay vì phụ thuộc latest; chuẩn bị thứ tự migration → app và phương án khôi phục |
| M1-P7-03 | DevOps | Bổ sung gate CI chạy verify trước build/publish; kiểm `docker compose config --quiet`, cấu hình mail/Google/cookie/CORS/proxy/timezone và healthcheck trên môi trường bàn giao |
| M1-P7-04 | Docs/QA | Bàn giao OpenAPI, ma trận quyền đã chốt, sơ đồ state/activation/agreement, biến môi trường, migration, hướng dẫn test và kết quả demo từng AU. Ghi rõ bước chưa chạy thật; không dùng công cụ không tồn tại làm bằng chứng |

**Lệnh backend nền:** chạy từ root repository với `JAVA_HOME` trỏ JDK 21 và Docker hoạt động.

```powershell
.\mvnw.cmd clean verify
docker compose config --quiet
```

Maven hiện dùng Testcontainers với database riêng. Không chạy reset/truncate test trên database dev hoặc production. Chưa có Flyway/Liquibase; Hibernate đang `ddl-auto=validate`. Thêm SQL migration mới sau `001_add_google_subject.sql`, không sửa baseline hoặc migration đã áp dụng; Member 1 điều phối số migration để tránh trùng. Với volume đã tồn tại, restart Compose không tự chạy SQL nâng cấp. Các bước triển khai/migration môi trường thật là task bàn giao riêng, không được xem là đã thực hiện khi unit test pass.

## 6. Draft API để chia task — chưa phải endpoint hiện có

Các nhóm API sau là đề xuất để chốt trong P0. Giữ tiền tố `/api/v1`, DTO riêng với entity, validation ở DTO/validation/service và repository tập trung vào persistence.

| Nhóm API đề xuất | Chức năng | Kiểm quyền bắt buộc |
|---|---|---|
| `/accounts/me/authorization` | Đọc capability/company context cần cho FE nếu `/auth/me` chưa đủ | Principal hiện tại; dữ liệu từ server. Giữ compatibility response auth cũ |
| `/admin/accounts/{id}/block`, `/unblock` | Khóa/mở khóa có reason và audit | Permission quản trị account theo P0; không cho tự khai actor |
| `/admin/accounts/{id}/roles` hoặc `/permissions` | Quản lý quyền trong tập được cấp | Không tự nâng quyền; cơ chế chi tiết chốt cùng M4/5 |
| `/companies`, `/companies/{id}` | Nộp/sửa/đọc hồ sơ và danh sách trong scope | Creator/active membership hoặc reviewer được phép; field whitelist |
| `/admin/companies/{id}/decision` | Approve/reject/suspend theo state machine | Permission duyệt của M5 đã chốt, reason và transaction audit |
| `/companies/{id}/agreements/eligible-policy` | Lấy phiên bản đủ điều kiện ký | Quyền đọc/ký đúng company và policy scope |
| `/companies/{id}/agreements/otp`, `/agreements` | Gửi mã và chấp nhận điều khoản | Người ký hợp lệ + challenge đúng company/version/account |
| `/companies/{id}/guides/invitations` | Cấp/mời guide | Người quản lý có quyền trong company đó; không cấp role nền tảng |
| `/admin/staff/invitations` | Cấp/mời nhân viên | Permission quản trị staff; whitelist role/action |
| `/auth/activate-account` | Xác minh lời mời và đặt mật khẩu lần đầu | Challenge hẹp, một lần, có hạn; validation + CSRF theo cơ chế client đã chốt |

Payment/review endpoints thuộc Member 4/5. Member 1 không tạo bản sao endpoint đó; cung cấp guard/contract để owner gắn vào service nghiệp vụ.

## 7. Ma trận kiểm thử tối thiểu để nghiệm thu module

| Nhóm | Ca bắt buộc cần có |
|---|---|
| Auth/session | Happy path; sai/hết hạn token; OTP sai/reuse; refresh replay/concurrent; logout; reset nhiều thiết bị; CSRF/CORS; Redis/SMTP lỗi |
| Role/scope | Bảy role; thiếu role; đúng role sai resource; company A/B; guide đúng company sai schedule; đổi quyền hoặc membership giữa phiên |
| Account lifecycle | Block/unblock đúng người, reason bắt buộc, audit; account inactive bị chặn mọi đường login/refresh; lỗi transaction không ghi một phần |
| Partner | Trùng hồ sơ/mã số thuế; sửa trường duyệt qua body; trạng thái sai; duyệt đồng thời; rejected submit lại theo contract; suspended bị chặn vận hành |
| Agreement | OTP ràng buộc đủ context; version hết hiệu lực/đổi sau gửi mã; actor mất quyền; IP giả; replay; concurrent accept; evidence/snapshot bất biến; lỗi sau consume OTP |
| Provisioning | Staff/guide đúng người cấp; không escalation; email đã có; token hết hạn/replay; email chưa verify; Google không bypass first-password; resend và rollback |
| Liên module | Thanh toán đúng booking; review đúng tác giả/check-in/completion; một review/đơn; accountant action đúng quyền; AI giữ nguyên actor/scope |
| Frontend | Giao diện happy/error/loading, token refresh, reload/logout, OTP cooldown, các trạng thái chờ duyệt/bị khóa/activation; thao tác UI trái quyền vẫn bị BE từ chối |
| Migration/release | Cài mới và nâng cấp dữ liệu cũ; constraint/trigger; cấu hình môi trường; build và CI; không gửi mail thật trong automated tests |

Các permission action và ownership cần test ở service/controller thật khi feature được thêm, không chỉ mock một hàm `canAccess()` luôn trả true. Với số lượng role lớn, dùng test theo bảng allow/deny để dễ đối chiếu ma trận P0.

## 8. Checklist hoàn thành và thứ tự bắt đầu

### Tiến độ đợt triển khai đầu tiên (11–12/10/2026)

Contract và quyết định kỹ thuật cho đợt đầu được ghi tại [MODULE_1_AUTHORIZATION.md](MODULE_1_AUTHORIZATION.md). Phạm vi là nền P0 và phần backend P2; không đánh dấu toàn bộ P0/P2 Done vì quyết định sản phẩm kế toán/liên module và FE còn cần nhóm xác nhận.

- M1-P0-01/02/04: đã ghi ma trận quyền đang enforce, ranh giới owner và API/error cho các endpoint mới. Quyền tài chính/policy và owner state machine company còn chờ chốt.
- M1-P2-01/02: guard dùng danh tính server và đọc lại account/role/membership/guide/assignment từ DB; có company context API. Chưa gắn vào các service tour/check-in/payment/review chưa tồn tại.
- M1-P2-03/04: API đọc account, khóa/mở khóa có reason và thay role theo whitelist; audit chung transaction. API bảo vệ bản thân và mọi account ADMIN; không cấp/thu hồi ADMIN.
- Migration mới `002_add_account_session_version.sql`: khóa account thu hồi bền vững mọi session cũ, kể cả sau mở khóa. Migration môi trường dev/production chưa thực hiện.
- M1-P1-04: sửa tài liệu các lệnh trỏ tới tooling thiếu. FE auth và Google credential thật chưa được kiểm thử.
- Kiểm thử mới bao gồm allow/deny, scope, lifecycle, audit rollback và migration trên dữ liệu giả. `clean verify` với Maven wrapper 3.9.16/JDK 21.0.8 hoàn thành lúc 14:18:39 ngày 12/10/2026 (Việt Nam), exit 0: **128 tests, 0 failures, 0 errors, 0 skipped** trên 10 suite. Có 46 ca mới trong hai suite authorization/account administration và một ca migration mới, bên cạnh 81 ca cũ; JAR Spring Boot build thành công.
- `docker compose config --quiet` và `git diff --check`: exit 0. Review độc lập không còn lỗi xác nhận sau khi sửa format lỗi cho ID URL không hợp lệ. Lượt verify đầu không chạy được integration vì Docker Desktop đang dừng; đã khởi động Docker và chạy lại toàn bộ thành công. DB test là `auth_test` trong container riêng, không phải DB dev/production; mail được mock.

- [x] Đã review source/schema/test hiện tại và đối chiếu AU-01…AU-05.
- [x] Đã có nền auth email/password, Google, JWT interface, validation và session; có bằng chứng 81 test ở snapshot review.
- [ ] P0: ma trận quyền, owner giao nhau và các quyết định sản phẩm đã chốt.
- [ ] P1: auth được tích hợp FE, tài liệu/cấu hình và kiểm thử thực tế phù hợp.
- [ ] P2: authorization/scope, account blocking, quyền quản trị và audit hoàn chỉnh.
- [ ] P3: hồ sơ và trạng thái đối tác có API/FE/test, M2/M5 sử dụng được.
- [ ] P4: OTP ký điều khoản và receipt bất biến đủ bằng chứng, M3/M4 đọc được.
- [ ] P5: cấp tài khoản/activation/first password và giới hạn guide/staff hoàn chỉnh.
- [ ] P6: quyền thanh toán/đánh giá/kế toán enforce tại nghiệp vụ thật; FE và tích hợp liên module đã nghiệm thu.
- [ ] P7: tests mới và cũ pass, migration/CI/docs/demo đầy đủ, bước chưa chạy thật được ghi rõ.

**Ưu tiên bắt đầu:** M1-P0-01 → M1-P0-02/03 → M1-P2-01/02/04, đồng thời làm FE auth P1. Đây là phần mở đường cho các thành viên khác; không cần xây lại login/JWT đã có.

**Không tự mở rộng phạm vi bắt buộc:** MFA, danh sách thiết bị, logout-all, self-service đổi email/profile, generic permission editor, Google third-party email linking và các chức năng bảo mật nâng cao chỉ thêm khi nhóm xác nhận cần. Khối lượng trên không bao gồm triển khai tour, booking, payment ledger, review moderation hay AI thay cho các member khác.

Mỗi ID `M1-Px-yy` có thể tạo thành một ticket Jira/Trello. Ticket cần ghi owner, phase/phụ thuộc, API/schema bị ảnh hưởng, tiêu chí nghiệm thu, test cần chạy và bằng chứng hoàn thành. Chỉ chuyển Done sau khi code, validation, authorization, UI liên quan và test tương ứng đều được kiểm tra.

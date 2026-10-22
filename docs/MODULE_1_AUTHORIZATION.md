# Module 1: contract authorization và đợt triển khai đầu tiên

> Contract backend hiện tại: [MODULE_1_BACKEND.md](MODULE_1_BACKEND.md).
> Các giới hạn quyền tài chính của đợt đầu bên dưới đã có phần bổ sung ở migration 004.

> Bổ sung ngày 15/10/2026: xem [bàn giao Module 1](MODULE_1_IMPLEMENTATION.md) cho
> contract P3/P4/P5, quyền ADMIN mới và kết quả kiểm thử. Nội dung dưới giữ lại như
> mốc triển khai đầu, không phản ánh toàn bộ endpoint hiện tại.

Ngày: 12/10/2026. Phạm vi đợt này: nền P0, M1-P2-01/02/03/04 và kiểm thử backend tương ứng. Đây là quyết định kỹ thuật cho phần đã triển khai, chưa thay thế phê duyệt sản phẩm của M2/3/4/5 cho các phase sau. Kế hoạch gốc: [Module 1](module_1_auth_account_authorization_plan.md).

## Ma trận đang enforce

Các role kết hợp theo quyền thực tế trong DB. Mọi guard kiểm tra account active, email đã xác minh và không còn yêu cầu đổi mật khẩu lần đầu. Role và scope được đọc lại từ DB; claim role cũ trong JWT không cấp quyền.

| Role | Action mới được cấp | Scope và điều kiện |
|---|---|---|
| CUSTOMER | Xem authorization của bản thân | Principal hiện tại |
| COMPANY_MANAGER | COMPANY_READ, COMPANY_MANAGE | Company tồn tại, membership OWNER/MANAGER active |
| COMPANY_MANAGER | COMPANY_OPERATE | Điều kiện trên và company APPROVED |
| GUIDE | GUIDE_SCHEDULE_READ | Guide active, company APPROVED và assignment đúng guide/company/schedule |
| SUPPORT_STAFF | Xem authorization của bản thân | Chưa cấp action quản trị trong đợt này |
| CONTENT_STAFF | Xem authorization của bản thân | Chưa cấp action duyệt company/tour trong đợt này |
| ACCOUNTANT | Xem authorization của bản thân | Chưa cấp action tài chính; cần contract action riêng của M4/5 |
| ADMIN | ACCOUNT_READ, ACCOUNT_BLOCK, ACCOUNT_ROLES_WRITE | Account toàn nền tảng; mutation không được nhắm bản thân hoặc account có role ADMIN |

ADMIN không tự động được truy cập dữ liệu company hoặc assignment guide. Người có nhiều role phải thỏa scope tương ứng. `permissions` trong response authorization chỉ là các quyền account toàn nền tảng; company contexts không phải giấy phép vận hành. Service nghiệp vụ phải gọi guard khi thao tác dữ liệu; endpoint authorization chỉ hỗ trợ giao diện.

## API đã triển khai

Tiền tố `/api/v1`. Các endpoint mới dùng Bearer access token, không dùng refresh cookie để xác thực. Giữ nguyên response `/auth/me` và hợp đồng auth cũ.

| Method/path | Request | Kết quả |
|---|---|---|
| GET /accounts/me/authorization | Không body | 200; accountId, roles, permissions và companies trong scope |
| GET /admin/accounts/{id} | Không body | 200; thông tin account an toàn, trạng thái và dữ liệu khóa; không trả hash/token |
| POST /admin/accounts/{id}/block | `{"reason":"Vi phạm quy định"}` | 200; ACTIVE → BLOCKED; tăng session version; audit |
| POST /admin/accounts/{id}/unblock | `{"reason":"Đã xử lý"}` | 200; BLOCKED → ACTIVE; audit |
| PUT /admin/accounts/{id}/roles | `{"roles":["customer","support_staff"],"reason":"Điều chỉnh nhiệm vụ"}` | 200; thay toàn bộ role trong whitelist; audit |

Reason bắt buộc, không chỉ khoảng trắng, tối đa 2000 ký tự. Roles không rỗng, không trùng; whitelist gồm sáu role ngoài ADMIN. Không thể cấp/thu hồi ADMIN hoặc sửa account đã có ADMIN bằng API này. Quy tắc này bảo vệ các account quản trị hiện hữu; bootstrap ADMIN là bước vận hành riêng. DISABLED không được mở khóa bằng endpoint unblock. Chuyển trạng thái lặp/sai trả 409.

Lỗi giữ `{code,message,fieldErrors}`: 400 cho input; 401 nếu không có phiên hợp lệ; 403 thiếu action hoặc mutation bị cấm; 404 account không tồn tại hoặc resource company/schedule không thuộc scope; 409 xung đột trạng thái. Đợt này không thêm API danh sách nên chưa đặt hợp đồng pagination.

Guard dùng chung: `AuthorizationService.require(Permission)`, `requireCompany(Permission, companyId)`, `requireGuideSchedule(scheduleId)`. Actor lấy từ SecurityContext và xác minh DB, không nhận accountId/roles của actor từ body. Nơi sử dụng phải gọi guard trong service trước đọc/ghi dữ liệu nghiệp vụ. M2/3 triển khai API tour/check-in sau này phải tích hợp guard và rule trạng thái của chính nghiệp vụ.

## Account, session và audit

Target account được khóa hàng khi thay trạng thái/role. Thay đổi và audit nằm trong một PostgreSQL transaction; writer audit bắt buộc có transaction hiện hữu. Audit chứa actor/action/entity/reason và before/after được whitelist, không chứa password, OTP hoặc token. Trigger hiện hữu chặn sửa/xóa audit.

Khóa account tăng `session_version` cùng transaction. Session fingerprint ràng buộc password hash và version; mọi access/refresh cũ bị từ chối kể cả khi người dùng không gửi request trong lúc bị khóa rồi account được mở lại. Version 0 giữ tương thích phiên trước migration. Không đổi password hash để thu hồi phiên và không cần transaction phân tán PostgreSQL/Redis.

## Migration và vận hành

Thứ tự: baseline → `001_add_google_subject.sql` → `002_add_account_session_version.sql` → ứng dụng mới. Compose volume mới và Testcontainers tự chạy đủ thứ tự. Volume đang tồn tại phải áp dụng migration 002 có chủ đích trước deploy; restart không tự nâng cấp. Migration 002 thêm BIGINT nonnegative mặc định 0, giữ dữ liệu account cũ. Không chạy đồng thời phiên bản ứng dụng cũ khi sử dụng tính năng block/unblock mới, vì phiên bản cũ chưa kiểm tra session version.

Migration chỉ được chuẩn bị và kiểm thử trên database test riêng trong đợt này; chưa áp dụng vào DB dev/production. Nếu rollback app, giữ cột mới và đóng luồng quản trị; ứng dụng cũ không bảo đảm session đã bị khóa vẫn bị thu hồi sau mở khóa.

## Owner và quyết định còn cần nhóm chốt

M1 sở hữu guard account/scope, account administration, audit, migration 002 và contract này. Theo phân công, M2 sở hữu vận hành tour/schedule và assignment; M3 sở hữu booking/participant/check-in; M4 sở hữu transaction tiền và nguồn policy tài chính; M5 dùng API account trong moderation/admin và sở hữu review; M6 truyền nguyên actor/scope. Owner code state machine company và policy/version cần chốt trước P3/P4.

Các điểm chưa triển khai và chưa xem là đã duyệt:

- R-05: quyền review giữ rule schema hiện tại đến khi M3/5 xác nhận có đổi sang mọi participant hay không.
- R-06: biểu diễn action xem/lập/duyệt/chi và phân tách người thao tác cần M4/5; ACCOUNTANT chưa được cấp các action đó.
- P3/P4: owner hay manager ký; company được ký ở trạng thái nào; thứ tự approval/agreement; sửa/nộp lại hồ sơ và cơ chế upload giấy phép.
- P5: invitation/activation, phạm vi staff/guide và resend.
- Repo FE chưa có trong workspace; không thêm framework vào backend. Google third-party email linking chưa được mở rộng.
- Payment/review/business API, FE/E2E, CI/release và kiểm thử Google credential thật thuộc các phase kế tiếp.

## Kiểm thử

JDK 21 và Docker hoạt động: `.\mvnw.cmd clean verify`. Testcontainers dùng PostgreSQL/Redis riêng và mail mock. Kiểm tra migration cài mới/nâng cấp account cũ; role/scope allow/deny; thay role/membership giữa phiên; block/unblock không hồi sinh session; reason/whitelist; audit và rollback.

Kết quả ngày 12/10/2026, 14:18:39 giờ Việt Nam: Maven wrapper 3.9.16, JDK 21.0.8, PostgreSQL 18.6, Redis 8.2; `clean verify` exit 0, **128 tests, 0 failures/errors/skipped** trong 10 suite, JAR build thành công. Gồm 46 ca integration mới và một ca migration mới. `docker compose config --quiet` và `git diff --check` exit 0. Review độc lập đã kiểm tra guard, audit/transaction, session/migration, DTO/error; lỗi format cho URL ID không hợp lệ được sửa và có regression test. Migration 002 chỉ chạy trên container test, chưa áp dụng DB môi trường hiện hữu.

# SQL migration registry

Repository hiện dùng SQL migration theo thứ tự, chưa dùng Flyway. Không sửa
`schema.sql` hoặc một migration đã áp dụng để nâng cấp database có dữ liệu.

| Thứ tự | File | Nội dung |
| --- | --- | --- |
| Bootstrap | `../schema.sql` | Schema nền, chỉ chạy trên database mới. |
| 001 | `001_add_google_subject.sql` | Google subject. |
| 002 | `002_add_account_session_version.sql` | Thu hồi session theo account version. |
| 003 | `003_add_cooperation_policy_terms.sql` | Policy/agreement snapshot và bất biến version. |
| 004 | `004_add_financial_permissions.sql` | Grant tài chính và revision quyền của mô hình cũ. |
| 005 | `005_single_operator_foundation.sql` | Nền một đơn vị, ba role, archive quyền cũ, nullable company và FK trực tiếp. |

Compose và Testcontainers chạy toàn chuỗi trên database mới. Init scripts không
chạy lại với volume đã tồn tại. Database ở 004 cần backup, dừng ứng dụng cũ,
áp dụng riêng 005 rồi mới chạy ứng dụng P1; không replay bootstrap/001–004.

005 không tự nâng tài khoản thành ADMIN, không xóa tiền/booking/agreement và
không viết lại snapshot đã khóa. Recovery cần backup và app tương thích, không
tự phục hồi quyền từ archive. Kịch bản fresh/upgrade dùng PostgreSQL18 trong
`SchemaMigrationTests`, chỉ trên database test riêng.

Hợp đồng và các quyết định: [P0/P1](../../docs/P0_P1_IMPLEMENTATION.md).

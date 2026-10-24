# SQL migration registry

Repository dùng SQL migration theo thứ tự; chưa dùng Flyway. Không sửa bootstrap hoặc migration đã áp dụng để nâng cấp database có dữ liệu.

| Thứ tự | File | Nội dung |
| --- | --- | --- |
| Bootstrap | [schema.sql](../schema.sql) | Chỉ khởi tạo database mới. |
| 001 | [001](001_add_google_subject.sql) | Google subject. |
| 002 | [002](002_add_account_session_version.sql) | Thu hồi session theo account version. |
| 003 | [003](003_add_cooperation_policy_terms.sql) | Bước lịch sử: snapshot agreement và khóa policy version. |
| 004 | [004](004_add_financial_permissions.sql) | Bước lịch sử: grant tài chính và revision quyền. |
| 005 | [005](005_single_operator_foundation.sql) | Ba role, lưu quyền cũ và scalar FK. |
| 006 | [006](006_remove_company_domain.sql) | Xóa company khỏi schema hiện hành; archive bằng chứng, giữ tiền/snapshot/guide ID. |

Database mới chạy bootstrap → 001 → 002 → 003 → 004 → 005 → 006 qua Compose/Testcontainers.
Database ở 004 chỉ áp dụng 005, 006; ở 005 chỉ áp dụng 006. Volume Docker cũ không chạy lại init scripts. Dừng writer, kiểm tra backup và phiên bản trước khi chạy từng SQL với ON_ERROR_STOP; không replay script đã áp dụng hoặc reset volume.

005 không tự nâng nhân sự cũ thành ADMIN. 006 sao chụp 18 bảng vào legacy_archive và ghi manifest số dòng trước khi bỏ cột/bảng company. Archive không có FK tới schema hiện hành, Java mapping hoặc quyền nghiệp vụ. DML và TRUNCATE vào archive bị chặn; quyền quản trị database/backup vẫn phải được kiểm soát ở môi trường triển khai.

006 giữ customer_payments, refund_requests, customer_refunds, audit_logs và các trường snapshot khách đã khóa; thông tin company/fee cũ của booking còn trong archive. Guide/assignment giữ ID, profile active không bị tự bật. Version có lịch sử tham chiếu giữ bất biến qua locked_for_history.

Cooperation policy đang được booking/tour/AI document dùng làm reference khiến 006 dừng và rollback để rà soát. Không tự đổi policy của booking. Sau commit 006, app cũ không tương thích; recovery dùng backup cùng phiên bản app phù hợp.

Các case fresh/upgrade/rollback dùng PostgreSQL test riêng trong SchemaMigrationTests; không chạy migration trên database thật từ tác vụ mã nguồn này. Contract: [P0/P1](../../docs/P0_P1_IMPLEMENTATION.md).

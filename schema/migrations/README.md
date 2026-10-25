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
| 007 | [007](007_add_tour_equipment_catalog.sql) | Đổi `packing_items` thành `tour_required_items`; thêm danh mục dụng cụ và cấu hình gói/giá thuê. |
| 008 | [008](008_add_tour_cloudinary_public_id.sql) | Lưu mã Cloudinary cho ảnh tour; ảnh cũ giữ nguyên URL. |
| 009 | [009](009_add_rental_package_templates.sql) | Thêm mẫu gói thuê dụng cụ dùng chung cho tour. |
| 010 | [010](010_abstract_tour_media_storage_key.sql) | Đổi cột ID Cloudinary thành khóa storage dùng chung. |
| 011 | [011](011_add_equipment_catalog_images.sql) | Thêm URL ảnh và khóa storage cho dụng cụ và mẫu gói thuê. |
| 012 | [012](012_define_rental_price_units.sql) | Ghi rõ giá gói tính theo mỗi gói, giá món lẻ theo mỗi món; không nhân theo số khách. |
| 013 | [013](013_booking_group_rental_total.sql) | Tính phí thuê là tổng của cả nhóm khi tạo booking. |
| 014 | [014](014_protect_booking_group_rental_snapshot.sql) | Bảo vệ phí thuê nhóm trong snapshot booking đã khóa. |

Database mới chạy bootstrap → 001 → 002 → 003 → 004 → 005 → 006 → 007 → 008 → 009 → 010 → 011 → 012 → 013 → 014 qua Compose/Testcontainers.
Database đã có 012 áp dụng 013 rồi 014; đã có 013 chỉ áp dụng 014. Các phiên bản cũ hơn chạy tiếp các file còn thiếu theo thứ tự. Volume Docker cũ không chạy lại init scripts. Dừng writer, kiểm tra backup và phiên bản trước khi chạy từng SQL với ON_ERROR_STOP; không replay script đã áp dụng hoặc reset volume.

005 không tự nâng nhân sự cũ thành ADMIN. 006 sao chụp 18 bảng vào legacy_archive và ghi manifest số dòng trước khi bỏ cột/bảng company. Archive không có FK tới schema hiện hành, Java mapping hoặc quyền nghiệp vụ. DML và TRUNCATE vào archive bị chặn; quyền quản trị database/backup vẫn phải được kiểm soát ở môi trường triển khai.

006 giữ customer_payments, refund_requests, customer_refunds, audit_logs và các trường snapshot khách đã khóa; thông tin company/fee cũ của booking còn trong archive. Guide/assignment giữ ID, profile active không bị tự bật. Version có lịch sử tham chiếu giữ bất biến qua locked_for_history.

Cooperation policy đang được booking/tour/AI document dùng làm reference khiến 006 dừng và rollback để rà soát. Không tự đổi policy của booking. Sau commit 006, app cũ không tương thích; recovery dùng backup cùng phiên bản app phù hợp.

Các case fresh/upgrade/rollback dùng PostgreSQL test riêng trong SchemaMigrationTests; không chạy migration trên database thật từ tác vụ mã nguồn này. Contract: [P0/P1](../../docs/P0_P1_IMPLEMENTATION.md).

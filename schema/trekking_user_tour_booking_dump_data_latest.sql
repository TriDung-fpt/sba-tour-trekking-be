--
-- PostgreSQL database dump
--

\restrict jRaPWba7WWlxL3FwXBKaZR17SgYwOnRAKtGBSBmM6PINveIL5recKqTf3qvuyug

-- Dumped from database version 18.6 (Debian 18.6-1.pgdg13+2)
-- Dumped by pg_dump version 18.6 (Debian 18.6-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: app_accounts; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.app_accounts OVERRIDING SYSTEM VALUE VALUES (1, 'dungnnt0910@gmail.com', '$2a$12$MsnZkkIalRtX9JB7hd662ej9Hh67gBxJW5PSg5nzCisNZJHs2U5tO', 'Dũng Nguyễn Trịnh Trí', '0865718837', 'active', '2026-10-29 06:37:06.805174+00', false, NULL, NULL, '2026-10-29 06:36:36.766371+00', '2026-10-29 06:59:59.826727+00', NULL, 0, 2);
INSERT INTO trekking.app_accounts OVERRIDING SYSTEM VALUE VALUES (2, 'dnntup1@gmail.com', '$2a$12$xaKdBYax8b2bYgHx38Cd3uV.66dlV9HVu9LLHMHbAMXI7.7//iBJO', 'Dũng Nguyễn Trịnh Trí', '0865718817', 'active', '2026-10-31 14:38:59.131425+00', false, NULL, NULL, '2026-10-31 14:38:35.750182+00', '2026-10-31 14:38:59.125225+00', NULL, 0, 1);


--
-- Data for Name: account_roles; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.account_roles VALUES (1, 'admin', '2026-10-29 06:36:36.766371+00', '2026-10-29 06:59:59.826727+00');
INSERT INTO trekking.account_roles VALUES (2, 'customer', '2026-10-31 14:38:35.750182+00', '2026-10-31 14:38:35.750182+00');


--
INSERT INTO trekking.policies (id, code, title, kind, created_by, active, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (7, 'chinh-sach-doi-huy-7-3', 'Chính sách đổi/hủy tour', 'cancellation', 1, true, '2026-10-31 13:42:37.061621+00', '2026-10-31 13:42:37.061621+00');
INSERT INTO trekking.policy_versions (id, policy_id, version_number, content, effective_from, approved_by, approved_at, created_at, updated_at, locked_for_history) OVERRIDING SYSTEM VALUE VALUES (8, 7, 1, 'CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR

1. Giá tour và thanh toán
Giá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.

Sau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.

Nếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.

2. Khách hàng hủy đặt chỗ
Khách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:

• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.
• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.
• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.

Thời gian được tính theo giờ khởi hành của lịch đã đặt.

3. Trường hợp tour không thể tổ chức
Nếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.', '2026-10-31 13:42:37.061621+00', 1, '2026-10-31 13:42:37.061621+00', '2026-10-31 13:42:37.061621+00', '2026-10-31 13:42:37.061621+00', true);
-- Data for Name: tours; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (1, 'Tour Hà Giang', '', 'hard', 2, '', '', '', '', 'archived', NULL, NULL, NULL, NULL, '2026-10-29 09:21:51.22069+00', '2026-10-29 16:10:07.449026+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (2, 'Tour Hà Giang', 'Hành trình leo núi', 'hard', 2, 'có khả năng đi dài 10-15km', '', '', '', 'archived', NULL, NULL, NULL, NULL, '2026-10-30 11:02:42.84035+00', '2026-10-30 11:07:24.422764+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (3, 'Tour Hà Giang', '', 'moderate', 2, '', '', '', '', 'archived', NULL, NULL, NULL, NULL, '2026-10-30 11:05:12.237303+00', '2026-10-30 11:07:26.243271+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (4, 'Tour Hà Giang', '', 'moderate', 2, '', '', '', '', 'archived', NULL, NULL, NULL, NULL, '2026-10-30 11:13:05.132812+00', '2026-10-30 11:14:01.698374+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (5, 'Tour Hà Giang', '', 'moderate', 2, '', '', '', '', 'archived', NULL, NULL, NULL, NULL, '2026-10-30 11:14:22.184817+00', '2026-10-31 14:16:36.738296+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (12, 'Tour Hiking chinh phục Pha Luông', 'Hành trình hiking mẫu khám phá cung đường Pha Luông, trải nghiệm cảnh quan núi rừng và các điểm ngắm cảnh theo điều kiện thực tế.', 'moderate', 2, 'Cần thể lực khá, có thể đi bộ đường dài trên địa hình dốc.', 'Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.', 'Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:19.486732+00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:23:19.48129+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (9, 'Trekking Tà Năng – Phan Dũng', 'Hành trình mẫu qua những đồi cỏ và rừng thông, với hai ngày trải nghiệm trekking và cắm trại.', 'moderate', 2, 'Cần thể lực cơ bản, có thể đi bộ đường dài và mang đồ dùng cá nhân.', 'Hướng dẫn viên, bữa ăn theo chương trình, nước uống và dụng cụ cắm trại.', 'Chi phí cá nhân, đồ uống ngoài chương trình và chi phí di chuyển đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:15.962099+00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 14:23:15.957483+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (10, '[Mẫu] Khám phá Bidoup – Núi Bà', 'Hành trình mẫu khám phá rừng núi cao nguyên, tìm hiểu hệ sinh thái và trải nghiệm qua đêm giữa thiên nhiên.', 'hard', 2, 'Phù hợp người đã có kinh nghiệm leo núi, thể lực tốt và quen địa hình dốc.', 'Hướng dẫn viên, hỗ trợ hậu cần, vé tham quan, bữa ăn theo chương trình và dụng cụ cắm trại.', 'Chi phí cá nhân, bảo hiểm tự mua và phương tiện đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:17.160124+00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 14:23:17.156119+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (11, 'Tour leo núi Tà Chì Nhù', 'Hành trình trekking mẫu khám phá vùng núi Tà Chì Nhù, băng qua sườn núi và ngắm biển mây khi điều kiện thời tiết phù hợp.', 'hard', 2, 'Cần thể lực tốt, có kinh nghiệm đi bộ đường dài và chuẩn bị trang phục phù hợp thời tiết.', 'Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.', 'Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:18.308574+00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:23:18.303786+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (13, 'Tour khám phá Hoàng Su Phì', 'Hành trình mẫu khám phá cảnh quan ruộng bậc thang và văn hóa vùng cao Hoàng Su Phì; cảnh quan thực tế thay đổi theo mùa.', 'moderate', 3, 'Phù hợp người có sức khỏe cơ bản và có thể đi bộ trên địa hình đồi núi.', 'Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.', 'Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:21.458419+00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:23:21.453778+00', 0);
INSERT INTO trekking.tours OVERRIDING SYSTEM VALUE VALUES (6, 'Tour Hà Giang', 'Hành trình mẫu khám phá cảnh quan núi đá và văn hóa vùng cao Hà Giang theo lịch trình của đoàn.', 'moderate', 2, 'Cần sức khỏe cơ bản, chuẩn bị giày phù hợp và có thể đi bộ trên địa hình đồi núi.', 'Hướng dẫn viên và bữa ăn theo chương trình mẫu.', 'Chi phí cá nhân và phương tiện đến điểm tập trung.', '', 'published', 8, 1, '2026-10-31 14:23:14.37666+00', NULL, '2026-10-30 13:04:46.184144+00', '2026-11-01 06:40:51.525883+00', 1);


--
-- Data for Name: tour_schedules; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (1, 2, '2026-11-01 00:00:00+00', '2026-11-03 00:00:00+00', 15, 0, 0, 250000, 200000, 30.00, '2026-10-30 17:00:00+00', 'bến xe tà xùa', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-30 11:02:42.913351+00', '2026-10-31 07:55:21.238694+00', '2026-10-30 11:02:42.913351+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (2, 3, '2026-11-07 11:04:00+00', '2026-11-09 11:04:00+00', 15, 0, 0, 2500000, 200000, 30.00, '2026-11-06 11:04:00+00', NULL, NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-30 11:05:12.265857+00', '2026-10-31 07:55:21.238694+00', '2026-10-30 11:05:12.265857+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (3, 4, '2026-11-07 11:12:00+00', '2026-11-09 11:12:00+00', 15, 0, 0, 2500000, 200000, 30.00, '2026-11-06 11:12:00+00', NULL, NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-30 11:13:05.159438+00', '2026-10-31 07:55:21.238694+00', '2026-10-30 11:13:05.159438+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (4, 5, '2026-11-07 11:14:00+00', '2026-11-09 11:14:00+00', 15, 0, 0, 2500000, 200000, 30.00, '2026-11-04 11:14:00+00', NULL, NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-30 11:14:22.206964+00', '2026-10-31 07:55:21.238694+00', '2026-10-30 11:14:22.206964+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (5, 6, '2026-10-31 13:04:00+00', '2026-11-02 13:04:00+00', 15, 0, 0, 2500000, 200000, 30.00, '2026-10-30 13:04:00+00', NULL, NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-30 13:04:46.2334+00', '2026-10-31 07:55:21.238694+00', '2026-10-30 13:04:46.2334+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (8, 9, '2026-11-14 00:00:00+00', '2026-11-16 00:00:00+00', 15, 0, 0, 2500000, 200000, 30.00, '2026-11-11 00:00:00+00', 'Điểm tập trung do ban tổ chức thông báo', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 05:57:46.296568+00', '2026-10-31 07:55:21.238694+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (9, 10, '2026-11-14 00:00:00+00', '2026-11-16 00:00:00+00', 15, 0, 0, 3200000, 200000, 30.00, '2026-11-11 00:00:00+00', 'Điểm tập trung tại Đà Lạt do ban tổ chức thông báo', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 05:57:46.296568+00', '2026-10-31 07:55:21.238694+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (11, 11, '2026-11-01 23:00:00+00', '2026-11-03 23:00:00+00', 15, 0, 0, 4950000, 0, 30.00, '2026-11-01 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (12, 13, '2026-11-03 23:00:00+00', '2026-11-06 23:00:00+00', 15, 0, 0, 3950000, 0, 30.00, '2026-11-03 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (13, 9, '2026-11-05 23:00:00+00', '2026-11-07 23:00:00+00', 15, 0, 0, 2500000, 0, 30.00, '2026-11-05 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (14, 12, '2026-11-02 23:00:00+00', '2026-11-04 23:00:00+00', 15, 0, 0, 3300000, 0, 30.00, '2026-11-02 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (15, 10, '2026-11-06 23:00:00+00', '2026-11-08 23:00:00+00', 15, 0, 0, 3200000, 0, 30.00, '2026-11-06 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 0, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_schedules OVERRIDING SYSTEM VALUE VALUES (10, 6, '2026-11-04 23:00:00+00', '2026-11-06 23:00:00+00', 15, 0, 1, 2000, 0, 30.00, '2026-11-04 05:00:00+00', 'Theo thông báo của ban tổ chức', NULL, 'scheduled', NULL, NULL, NULL, 14, '2026-10-31 14:20:07.696539+00', '2026-11-02 05:41:19.66297+00', '2026-11-01 10:27:56.005429+00');


--
-- Data for Name: bookings; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (1, 'BKM2UYZCV7GG', '0d5fc6bb-5384-431c-a61a-e21616eda004', 2, 10, 1, true, 2500000, 300000, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}', 'expired', '2026-11-01 07:06:00.350056+00', '2026-11-04 05:00:00+00', NULL, NULL, NULL, NULL, 2, '2026-11-01 06:51:00.284119+00', '2026-11-01 07:06:22.471437+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (2, 'BKWPDDYP3SMU', '1b037490-d555-4339-b9a2-05b24d72d3da', 2, 10, 1, true, 2500000, 300000, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}', 'expired', '2026-11-01 07:07:03.893764+00', '2026-11-04 05:00:00+00', NULL, NULL, NULL, NULL, 2, '2026-11-01 06:52:03.887601+00', '2026-11-01 07:07:22.495482+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (3, 'BKX883CPH76Y', '371a84f8-f8f2-4c72-adc2-18c5b40c1d7f', 2, 10, 1, false, 2500000, 0, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}', 'expired', '2026-11-01 09:20:50.977645+00', '2026-11-04 05:00:00+00', NULL, NULL, NULL, NULL, 2, '2026-11-01 09:05:50.951676+00', '2026-11-01 09:21:47.006757+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (4, 'BKZ2Y3QXEHN9', '5883df72-39bf-4f1a-9ed4-7a1b38c08be6', 2, 10, 1, false, 2500000, 0, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}', 'expired', '2026-11-01 09:53:08.07058+00', '2026-11-04 05:00:00+00', NULL, NULL, NULL, NULL, 2, '2026-11-01 09:38:08.044433+00', '2026-11-01 09:53:47.080069+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (5, 'BKFVEPHF22LY', '73d36fd7-d860-401d-ba55-d895224b11a1', 2, 10, 1, false, 2000, 0, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}', 'expired', '2026-11-01 10:44:26.578456+00', '2026-11-04 05:00:00+00', '2026-11-01 11:03:01.510685+00', NULL, NULL, NULL, 2, '2026-11-01 10:29:26.56826+00', '2026-11-01 11:03:01.510685+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (6, 'BK7W35NTJZ8N', 'c642e4a1-e68f-4425-b36c-1f952d3f5804', 2, 10, 1, false, 2000, 0, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}', 'confirmed', '2026-11-01 11:00:21.600656+00', '2026-11-04 05:00:00+00', '2026-11-01 10:52:51.673203+00', NULL, NULL, NULL, 2, '2026-11-01 10:45:21.583856+00', '2026-11-01 10:52:51.673203+00', DEFAULT);
INSERT INTO trekking.bookings OVERRIDING SYSTEM VALUE VALUES (7, 'BK6ZXH7MYU47', '3578aab0-0657-42dc-a386-a8e92f50dbfc', 2, 10, 1, true, 2000, 300000, 30.00, 8, '{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\n\n1. Giá tour và thanh toán\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\n\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\n\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\n\n2. Khách hàng hủy đặt chỗ\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\n\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\n\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\n\n3. Trường hợp tour không thể tổ chức\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}', 'expired', '2026-11-02 05:41:17.926781+00', '2026-11-04 05:00:00+00', NULL, NULL, NULL, NULL, 2, '2026-11-02 05:26:17.847784+00', '2026-11-02 05:41:19.66297+00', DEFAULT);


--
-- Data for Name: payment_attempts; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.payment_attempts OVERRIDING SYSTEM VALUE VALUES (1, 1, 'payos', '953924877', 'deposit', 840000, 'pending', '2026-11-01 07:06:00.350056+00', '2026-11-01 06:51:12.673543+00', '2026-11-01 06:51:12.673543+00', 'https://pay.payos.vn/web/3f2fe7740ee54cec94936d8d19eb329d');
INSERT INTO trekking.payment_attempts OVERRIDING SYSTEM VALUE VALUES (3, 5, 'payos', '946844257', 'deposit', 600, 'pending', '2026-11-01 10:44:26.578456+00', '2026-11-01 10:40:21.028696+00', '2026-11-01 10:40:21.028696+00', 'https://pay.payos.vn/web/7fae7b7e2fe34b439ef6989183d72d6a');
INSERT INTO trekking.payment_attempts OVERRIDING SYSTEM VALUE VALUES (4, 6, 'payos', '1431967681', 'full', 2000, 'succeeded', '2026-11-01 11:00:21.600656+00', '2026-11-01 10:45:26.910458+00', '2026-11-01 10:52:51.673203+00', 'https://pay.payos.vn/web/566ed4c503bc4c3a89ef843932703ea7');
INSERT INTO trekking.payment_attempts OVERRIDING SYSTEM VALUE VALUES (2, 5, 'payos', '831844495', 'full', 2000, 'succeeded', '2026-11-01 10:44:26.578456+00', '2026-11-01 10:29:30.407974+00', '2026-11-01 11:03:01.510685+00', 'https://pay.payos.vn/web/0972a5dad4554cb4819d8a0526a4f761');


--
-- Data for Name: customer_payments; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.customer_payments OVERRIDING SYSTEM VALUE VALUES (10, 6, 4, 'payos', 'FT26306122455052', 2000, 0, 'accepted', '2026-11-01 10:52:51.748771+00', NULL, NULL, '2026-11-01 10:52:51.673203+00', '2026-11-01 10:52:51.673203+00');
INSERT INTO trekking.customer_payments OVERRIDING SYSTEM VALUE VALUES (11, 5, 2, 'payos', 'FT26306014696067', 2000, 0, 'pending_review', '2026-11-01 11:03:01.518566+00', NULL, NULL, '2026-11-01 11:03:01.510685+00', '2026-11-01 11:03:01.510685+00');


--
-- Data for Name: refund_requests; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: customer_refunds; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: tour_equipment_packages; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_equipment_packages OVERRIDING SYSTEM VALUE VALUES (1, 6, 'Combo cắm trại 2 người', 'Lều 2 người × 1, Túi ngủ × 2, Thảm cách nhiệt × 2', NULL, NULL, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 300000, NULL, true, false);
INSERT INTO trekking.tour_equipment_packages OVERRIDING SYSTEM VALUE VALUES (2, 6, 'Combo nấu ăn dã ngoại', 'Bếp gas dã ngoại × 1, Bộ nồi dã ngoại × 1', NULL, NULL, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 125000, NULL, true, false);
INSERT INTO trekking.tour_equipment_packages OVERRIDING SYSTEM VALUE VALUES (3, 6, 'Combo trekking cá nhân', 'Balo trekking 40L × 1, Gậy trekking × 1, Đèn đội đầu × 1, Túi chống nước × 1', NULL, NULL, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 150000, NULL, true, false);


--
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (1,'Lều 2 người','Lều cắm trại 2 người', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',180000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (2,'Túi ngủ','Túi ngủ trekking', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',70000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (3,'Thảm cách nhiệt','Thảm cách nhiệt cắm trại', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',30000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (4,'Gậy trekking','Gậy hỗ trợ trekking', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',40000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (5,'Balo trekking 40L','Balo trekking dung tích 40 lít', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',100000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (6,'Đèn đội đầu','Đèn đội đầu dùng khi trekking', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',35000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (7,'Thiết bị thuê lẻ','Tên thiết bị chưa có trong dump nguồn; giữ khóa ngoại ID 7.', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',25000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (8,'Bếp gas dã ngoại','Bếp gas dùng khi cắm trại', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',80000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (9,'Bộ nồi dã ngoại','Bộ nồi dã ngoại', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',60000,NULL,NULL);
INSERT INTO trekking.equipment (id,name,description,created_at,updated_at,individual_rental_price_vnd,image_url,storage_key) OVERRIDING SYSTEM VALUE VALUES (10,'Túi chống nước','Túi chống nước cho thiết bị cá nhân', '2026-11-01 06:40:51.525883+00','2026-11-01 06:40:51.525883+00',25000,NULL,NULL);

-- Data for Name: equipment_reservations; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.equipment_reservations OVERRIDING SYSTEM VALUE VALUES (1, 1, 10, 1, '{"packageQuantities":{"1":1},"itemQuantities":{}}', 'released', 1, 300000, '2026-11-01 07:06:00.350056+00', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}', '2026-11-01 06:51:00.284119+00', '2026-11-01 07:06:22.397873+00');
INSERT INTO trekking.equipment_reservations OVERRIDING SYSTEM VALUE VALUES (2, 2, 10, 1, '{"packageQuantities":{"1":1},"itemQuantities":{}}', 'released', 1, 300000, '2026-11-01 07:07:03.893764+00', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}', '2026-11-01 06:52:03.887601+00', '2026-11-01 07:07:22.467583+00');
INSERT INTO trekking.equipment_reservations OVERRIDING SYSTEM VALUE VALUES (3, 7, 10, 1, '{"packageQuantities":{"1":1},"itemQuantities":{}}', 'released', 1, 300000, '2026-11-02 05:41:17.926781+00', '{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}', '2026-11-02 05:26:17.847784+00', '2026-11-02 05:41:19.647525+00');


--
-- Data for Name: equipment_handover_records; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: equipment_handover_items; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: equipment_reservation_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, 1, 0, 0, '2026-11-01 06:51:00.284119+00', '2026-11-01 06:51:00.284119+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (2, 1, 2, 2, 0, 0, '2026-11-01 06:51:00.284119+00', '2026-11-01 06:51:00.284119+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (3, 1, 3, 2, 0, 0, '2026-11-01 06:51:00.284119+00', '2026-11-01 06:51:00.284119+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (4, 2, 1, 1, 0, 0, '2026-11-01 06:52:03.887601+00', '2026-11-01 06:52:03.887601+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (5, 2, 2, 2, 0, 0, '2026-11-01 06:52:03.887601+00', '2026-11-01 06:52:03.887601+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (6, 2, 3, 2, 0, 0, '2026-11-01 06:52:03.887601+00', '2026-11-01 06:52:03.887601+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (7, 3, 1, 1, 0, 0, '2026-11-02 05:26:17.847784+00', '2026-11-02 05:26:17.847784+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (8, 3, 2, 2, 0, 0, '2026-11-02 05:26:17.847784+00', '2026-11-02 05:26:17.847784+00');
INSERT INTO trekking.equipment_reservation_items OVERRIDING SYSTEM VALUE VALUES (9, 3, 3, 2, 0, 0, '2026-11-02 05:26:17.847784+00', '2026-11-02 05:26:17.847784+00');


--
-- Data for Name: participants; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (1, 1, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 06:51:00.284119+00', '2026-11-01 06:51:00.284119+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (2, 2, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 06:52:03.887601+00', '2026-11-01 06:52:03.887601+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (3, 3, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 09:05:50.951676+00', '2026-11-01 09:05:50.951676+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (4, 4, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 09:38:08.044433+00', '2026-11-01 09:38:08.044433+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (5, 5, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 10:29:26.56826+00', '2026-11-01 10:29:26.56826+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (6, 6, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-01 10:45:21.583856+00', '2026-11-01 10:45:21.583856+00');
INSERT INTO trekking.participants OVERRIDING SYSTEM VALUE VALUES (7, 7, 2, 'Dũng Nguyễn Trịnh Trí', '0865718817', 'dnntup1@gmail.com', NULL, NULL, NULL, '2026-11-02 05:26:17.847784+00', '2026-11-02 05:26:17.847784+00');


--
-- Data for Name: payment_notices; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.payment_notices OVERRIDING SYSTEM VALUE VALUES (10, 4, 'payos', 'FT26306122455052', true, '{"code": "00", "data": {"code": "00", "amount": 2000, "orderCode": "1431967681", "reference": "FT26306122455052"}, "success": true}', '2026-11-01 10:52:51.721031+00', '2026-11-01 10:52:51.794484+00', NULL, '2026-11-01 10:52:51.673203+00', '2026-11-01 10:52:51.673203+00');
INSERT INTO trekking.payment_notices OVERRIDING SYSTEM VALUE VALUES (11, 2, 'payos', 'FT26306014696067', true, '{"code": "00", "data": {"code": "00", "amount": 2000, "orderCode": "831844495", "reference": "FT26306014696067"}, "success": true}', '2026-11-01 11:03:01.517177+00', '2026-11-01 11:03:01.524379+00', 'Payment arrived after the attempt or booking expired; manual review required', '2026-11-01 11:03:01.510685+00', '2026-11-01 11:03:01.510685+00');


--
-- Data for Name: reviews; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: review_votes; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: tour_activities; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (2, 10, 1, 0, 'Tập trung và phổ biến an toàn', 'Gặp hướng dẫn viên, kiểm tra đồ cá nhân và nghe hướng dẫn an toàn.', '07:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (4, 10, 1, 1, 'Bắt đầu trekking', 'Di chuyển theo cung đường và nghỉ theo hướng dẫn của trưởng đoàn.', '09:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (6, 10, 1, 2, 'Dựng trại và ăn tối', 'Nghỉ ngơi, ăn tối và sinh hoạt tại khu cắm trại.', '17:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (8, 10, 2, 0, 'Ăn sáng và chuẩn bị', 'Thu dọn khu trại, dùng bữa sáng và kiểm tra hành lý.', '06:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (10, 10, 2, 1, 'Tiếp tục hành trình', 'Trekking về điểm kết thúc và nghỉ giữa chặng.', '08:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (12, 10, 2, 2, 'Kết thúc chương trình', 'Tập trung, kiểm tra quân số và kết thúc chương trình.', '15:00:00', NULL, '2026-10-31 05:57:46.296568+00', '2026-10-31 05:57:46.296568+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (13, 9, 1, 0, 'Tập trung và phổ biến an toàn', 'Gặp hướng dẫn viên, kiểm tra đồ cá nhân và nghe hướng dẫn an toàn.', '07:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (14, 9, 1, 1, 'Bắt đầu trekking', 'Di chuyển theo cung đường và nghỉ theo hướng dẫn của trưởng đoàn.', '09:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (15, 9, 1, 2, 'Dựng trại và ăn tối', 'Nghỉ ngơi, ăn tối và sinh hoạt tại khu cắm trại.', '17:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (16, 9, 2, 0, 'Ăn sáng và chuẩn bị', 'Thu dọn khu trại, dùng bữa sáng và kiểm tra hành lý.', '06:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (17, 9, 2, 1, 'Tiếp tục hành trình', 'Trekking về điểm kết thúc và nghỉ giữa chặng.', '08:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (18, 9, 2, 2, 'Kết thúc chương trình', 'Tập trung, kiểm tra quân số và kết thúc chương trình.', '15:00:00', NULL, '2026-10-31 13:43:34.161287+00', '2026-10-31 13:43:34.161287+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (19, 11, 1, 0, 'Tập trung và chuẩn bị', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (20, 11, 2, 0, 'Tiếp tục hành trình', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (21, 12, 1, 0, 'Tập trung và chuẩn bị', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (22, 12, 2, 0, 'Tiếp tục hành trình', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (23, 13, 1, 0, 'Tập trung và chuẩn bị', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (24, 13, 2, 0, 'Tiếp tục hành trình', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (25, 13, 3, 0, 'Tiếp tục hành trình', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (26, 6, 1, 0, 'Tập trung và chuẩn bị', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');
INSERT INTO trekking.tour_activities OVERRIDING SYSTEM VALUE VALUES (27, 6, 2, 0, 'Tiếp tục hành trình', 'Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.', '07:00:00', NULL, '2026-10-31 14:20:07.696539+00', '2026-10-31 14:20:07.696539+00');


--
-- Data for Name: tour_equipment_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, 1, 180000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (2, 1, 2, 2, 70000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (3, 1, 3, 2, 30000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (4, 2, 8, 1, 80000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (5, 2, 9, 1, 60000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (6, 3, 5, 1, 100000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (7, 3, 4, 1, 40000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (8, 3, 6, 1, 35000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (9, 3, 10, 1, 25000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (10, NULL, 7, 1, 25000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (11, NULL, 5, 1, 100000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);
INSERT INTO trekking.tour_equipment_items OVERRIDING SYSTEM VALUE VALUES (12, NULL, 8, 1, 80000, '2026-11-01 06:40:51.525883+00', '2026-11-01 06:40:51.525883+00', 6);


--
-- Data for Name: tour_itinerary_days; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (6, 10, 1, 'Khởi hành và khám phá rừng Bidoup', 'Trưa, tối', 'Cắm trại');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (8, 10, 2, 'Trekking về điểm đón', 'Sáng, trưa', 'Không');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (9, 9, 1, 'Tập trung và trekking qua đồi cỏ', 'Trưa, tối', 'Cắm trại');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (10, 9, 2, 'Trekking về điểm kết thúc', 'Sáng, trưa', 'Không');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (11, 11, 1, 'Tập trung và bắt đầu hành trình', 'Theo chương trình của đoàn', 'Theo sắp xếp của ban tổ chức');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (12, 11, 2, 'Hoàn tất hành trình và trở về', 'Theo chương trình của đoàn', 'Không có');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (13, 12, 1, 'Tập trung và bắt đầu hành trình', 'Theo chương trình của đoàn', 'Theo sắp xếp của ban tổ chức');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (14, 12, 2, 'Hoàn tất hành trình và trở về', 'Theo chương trình của đoàn', 'Không có');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (15, 13, 1, 'Tập trung và bắt đầu hành trình', 'Theo chương trình của đoàn', 'Theo sắp xếp của ban tổ chức');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (16, 13, 2, 'Khám phá cung đường', 'Theo chương trình của đoàn', 'Theo sắp xếp của ban tổ chức');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (17, 13, 3, 'Hoàn tất hành trình và trở về', 'Theo chương trình của đoàn', 'Không có');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (18, 6, 1, 'Tập trung và bắt đầu hành trình', 'Theo chương trình của đoàn', 'Theo sắp xếp của ban tổ chức');
INSERT INTO trekking.tour_itinerary_days OVERRIDING SYSTEM VALUE VALUES (19, 6, 2, 'Hoàn tất hành trình và trở về', 'Theo chương trình của đoàn', 'Không có');


--
-- Data for Name: tour_locations; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: tour_media; Type: TABLE DATA; Schema: trekking; Owner: -
--

INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (1, 2, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793358166/trekking/tours/2/e6116327-2f88-429e-8900-23fc5c37b8aa.jpg', 'FitPet_LogoCrop.jpg', 0, true, '2026-10-30 11:02:47.379899+00', '2026-10-30 11:02:47.379899+00', 'trekking/tours/2/e6116327-2f88-429e-8900-23fc5c37b8aa');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (2, 4, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793358787/trekking/tours/4/5c032927-5d2a-40ed-af8a-af57edc38ad1.jpg', 'FitPet_LogoCrop.jpg', 0, true, '2026-10-30 11:13:08.681571+00', '2026-10-30 11:13:08.681571+00', 'trekking/tours/4/5c032927-5d2a-40ed-af8a-af57edc38ad1');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (5, 9, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793427506/trekking/tours/9/dff2aaf8-8acb-43b1-afd6-2bef567eff1d.jpg', 'FitPet_LogoCrop.jpg', 1, true, '2026-10-31 06:18:26.596945+00', '2026-10-31 06:18:35.507352+00', 'trekking/tours/9/dff2aaf8-8acb-43b1-afd6-2bef567eff1d');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (6, 11, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456456/trekking/tours/11/7eaf251d-44cf-4d8a-931e-ecd4d0df95fb.png', 'Ảnh minh họa Tà Chì Nhù', 0, true, '2026-10-31 14:21:07.887736+00', '2026-10-31 14:21:07.887736+00', 'trekking/tours/11/7eaf251d-44cf-4d8a-931e-ecd4d0df95fb');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (7, 12, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456459/trekking/tours/12/6ba76e3a-0690-4925-996d-9648fdda345f.png', 'Ảnh minh họa Pha Luông', 0, true, '2026-10-31 14:21:08.324884+00', '2026-10-31 14:21:08.324884+00', 'trekking/tours/12/6ba76e3a-0690-4925-996d-9648fdda345f');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (8, 13, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456461/trekking/tours/13/90b4a66f-2ea5-4b13-b849-51526348c0de.png', 'Ảnh minh họa Hoàng Su Phì', 0, true, '2026-10-31 14:21:08.789995+00', '2026-10-31 14:21:08.789995+00', 'trekking/tours/13/90b4a66f-2ea5-4b13-b849-51526348c0de');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (9, 10, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456466/trekking/tours/10/0bde2da4-4177-4da9-b5e2-33d7c45fd3cc.png', 'Ảnh minh họa Bidoup – Núi Bà', 0, true, '2026-10-31 14:21:09.23258+00', '2026-10-31 14:21:09.23258+00', 'trekking/tours/10/0bde2da4-4177-4da9-b5e2-33d7c45fd3cc');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (3, 6, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793365489/trekking/tours/6/03331b2e-39a8-44ac-853a-996271172d70.jpg', 'FitPet_LogoCrop.jpg', 0, false, '2026-10-30 13:04:50.450628+00', '2026-10-31 14:21:28.174422+00', 'trekking/tours/6/03331b2e-39a8-44ac-853a-996271172d70');
INSERT INTO trekking.tour_media (id,tour_id,media_type,url,caption,display_order,is_cover,created_at,updated_at,storage_key) OVERRIDING SYSTEM VALUE VALUES (10, 6, 'image', 'https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456487/trekking/tours/6/071910d9-f9de-4257-b45e-958f6971aec0.png', 'Ảnh minh họa Hà Giang', 1, true, '2026-10-31 14:21:28.174422+00', '2026-10-31 14:21:28.174422+00', 'trekking/tours/6/071910d9-f9de-4257-b45e-958f6971aec0');


--
-- Data for Name: tour_required_items; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: tour_schedule_defaults; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Data for Name: tour_terms; Type: TABLE DATA; Schema: trekking; Owner: -
--



--
-- Name: app_accounts_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.app_accounts_id_seq', 2, true);


--
-- Name: bookings_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.bookings_id_seq', 7, true);


--
-- Name: customer_payments_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.customer_payments_id_seq', 11, true);


--
-- Name: customer_refunds_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.customer_refunds_id_seq', 1, false);


--
-- Name: equipment_handover_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_handover_items_id_seq', 1, false);


--
-- Name: equipment_handover_records_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_handover_records_id_seq', 1, false);


--
-- Name: equipment_reservation_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_reservation_items_id_seq', 9, true);


--
-- Name: equipment_reservations_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_reservations_id_seq', 3, true);


--
-- Name: participants_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.participants_id_seq', 7, true);


--
-- Name: payment_attempts_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.payment_attempts_id_seq', 4, true);


--
-- Name: payment_notices_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.payment_notices_id_seq', 11, true);


--
-- Name: refund_requests_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.refund_requests_id_seq', 1, false);


--
-- Name: reviews_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.reviews_id_seq', 1, false);


--
-- Name: tour_activities_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_activities_id_seq', 27, true);


--
-- Name: tour_equipment_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_equipment_items_id_seq', 12, true);


--
-- Name: tour_equipment_packages_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_equipment_packages_id_seq', 3, true);


--
-- Name: tour_itinerary_days_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_itinerary_days_id_seq', 19, true);


--
-- Name: tour_media_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_media_id_seq', 10, true);


--
-- Name: tour_required_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.packing_items_id_seq1', 1, false);


--
-- Name: tour_schedules_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_schedules_id_seq', 15, true);


--
-- Name: tour_terms_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_terms_id_seq', 1, false);


--
-- Name: tours_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tours_id_seq', 13, true);


--
-- PostgreSQL database dump complete
--

SELECT pg_catalog.setval('trekking.policies_id_seq', 7, true);
SELECT pg_catalog.setval('trekking.policy_versions_id_seq', 8, true);

SELECT pg_catalog.setval('trekking.equipment_id_seq', 10, true);
\unrestrict jRaPWba7WWlxL3FwXBKaZR17SgYwOnRAKtGBSBmM6PINveIL5recKqTf3qvuyug


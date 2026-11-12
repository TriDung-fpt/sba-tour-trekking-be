-- Team local/test fixture only; this file contains test accounts and sample payment data.
-- This is a data-only PostgreSQL 18 dump; initialize the current trekking schema first.
-- Import only into a clean local/test database. It is not an upgrade or production restore.
-- Load with: docker compose --profile sample-data run --rm sample-data
-- Local admin: admin@trekking.test / LocalTest123!
-- Local customer: customer@trekking.test / LocalTest123!
--
-- PostgreSQL database dump
--

\restrict yaZDetwFNptgBLwGclFHhlxWeufF5iCjJoKBb1KGjf56alXKMx1npMozKKtorSb

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

COPY trekking.app_accounts (id, email, password_hash, full_name, phone, status, email_verified_at, must_change_password, blocked_reason, blocked_by, created_at, updated_at, google_subject, session_version, authorization_version) FROM stdin;
1	admin@trekking.test	$2a$10$A4RmnGfb7aRzw0YDrRPFn.fNZrtYh0HrPLW3nVoqhkPJh3jCnnKJ6	Local Test Admin	0900000000	active	2026-10-29 06:37:06.805174+00	f	\N	\N	2026-10-29 06:36:36.766371+00	2026-11-05 10:01:37.977251+00	\N	0	3
2	customer@trekking.test	$2a$10$A4RmnGfb7aRzw0YDrRPFn.fNZrtYh0HrPLW3nVoqhkPJh3jCnnKJ6	Local Test Customer	0900000001	active	2026-10-31 14:38:59.131425+00	f	\N	\N	2026-10-31 14:38:35.750182+00	2026-11-05 10:01:37.980292+00	\N	0	2
\.


--
-- Data for Name: account_roles; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.account_roles (account_id, role, created_at, updated_at) FROM stdin;
1	admin	2026-10-29 06:36:36.766371+00	2026-10-29 06:59:59.826727+00
2	customer	2026-10-31 14:38:35.750182+00	2026-10-31 14:38:35.750182+00
\.


--
-- Data for Name: policies; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.policies (id, code, title, kind, created_by, active, created_at, updated_at, deleted_at) FROM stdin;
1	local-general	Local test: general terms	general	1	t	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N
2	local-payment	Local test: payment terms	payment	1	t	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N
3	local-safety	Local test: safety terms	general	1	t	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N
4	local-cancellation	Local test: cancellation policy	cancellation	1	t	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N
\.


--
-- Data for Name: policy_versions; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.policy_versions (id, policy_id, version_number, content, effective_from, approved_by, approved_at, created_at, updated_at, locked_for_history) FROM stdin;
5	1	1	Local test terms. Not a real customer agreement.	2026-10-01 00:00:00+00	1	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	f
6	2	1	Local test payment terms. Not a real customer agreement.	2026-10-01 00:00:00+00	1	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	f
7	3	1	Local test safety terms. Not a real customer agreement.	2026-10-01 00:00:00+00	1	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	f
8	4	1	Local test cancellation policy. Not a real customer agreement.	2026-10-01 00:00:00+00	1	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	f
\.


--
-- Data for Name: ai_reference_documents; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.ai_reference_documents (id, title, file_url, file_type, version_number, previous_version_id, uploaded_by, policy_version_id, status, processing_error, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.audit_logs (id, actor_id, action, entity_type, entity_id, reason, details, occurred_at) FROM stdin;
\.


--
-- Data for Name: tours; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tours (id, title, description, difficulty, duration_days, required_experience, included_services, excluded_services, route_file_url, status, cancellation_policy_version_id, reviewed_by, reviewed_at, review_note, created_at, updated_at, equipment_config_version) FROM stdin;
1	Tour Hà Giang		hard	2					archived	\N	\N	\N	\N	2026-10-29 09:21:51.22069+00	2026-10-29 16:10:07.449026+00	0
2	Tour Hà Giang	Hành trình leo núi	hard	2	có khả năng đi dài 10-15km				archived	\N	\N	\N	\N	2026-10-30 11:02:42.84035+00	2026-10-30 11:07:24.422764+00	0
3	Tour Hà Giang		moderate	2					archived	\N	\N	\N	\N	2026-10-30 11:05:12.237303+00	2026-10-30 11:07:26.243271+00	0
4	Tour Hà Giang		moderate	2					archived	\N	\N	\N	\N	2026-10-30 11:13:05.132812+00	2026-10-30 11:14:01.698374+00	0
5	Tour Hà Giang		moderate	2					archived	\N	\N	\N	\N	2026-10-30 11:14:22.184817+00	2026-10-31 14:16:36.738296+00	0
12	Tour Hiking chinh phục Pha Luông	Hành trình hiking mẫu khám phá cung đường Pha Luông, trải nghiệm cảnh quan núi rừng và các điểm ngắm cảnh theo điều kiện thực tế.	moderate	2	Cần thể lực khá, có thể đi bộ đường dài trên địa hình dốc.	Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.	Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.		published	8	1	2026-10-31 14:23:19.486732+00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:23:19.48129+00	0
9	Trekking Tà Năng – Phan Dũng	Hành trình mẫu qua những đồi cỏ và rừng thông, với hai ngày trải nghiệm trekking và cắm trại.	moderate	2	Cần thể lực cơ bản, có thể đi bộ đường dài và mang đồ dùng cá nhân.	Hướng dẫn viên, bữa ăn theo chương trình, nước uống và dụng cụ cắm trại.	Chi phí cá nhân, đồ uống ngoài chương trình và chi phí di chuyển đến điểm tập trung.		published	8	1	2026-10-31 14:23:15.962099+00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 14:23:15.957483+00	0
10	[Mẫu] Khám phá Bidoup – Núi Bà	Hành trình mẫu khám phá rừng núi cao nguyên, tìm hiểu hệ sinh thái và trải nghiệm qua đêm giữa thiên nhiên.	hard	2	Phù hợp người đã có kinh nghiệm leo núi, thể lực tốt và quen địa hình dốc.	Hướng dẫn viên, hỗ trợ hậu cần, vé tham quan, bữa ăn theo chương trình và dụng cụ cắm trại.	Chi phí cá nhân, bảo hiểm tự mua và phương tiện đến điểm tập trung.		published	8	1	2026-10-31 14:23:17.160124+00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 14:23:17.156119+00	0
11	Tour leo núi Tà Chì Nhù	Hành trình trekking mẫu khám phá vùng núi Tà Chì Nhù, băng qua sườn núi và ngắm biển mây khi điều kiện thời tiết phù hợp.	hard	2	Cần thể lực tốt, có kinh nghiệm đi bộ đường dài và chuẩn bị trang phục phù hợp thời tiết.	Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.	Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.		published	8	1	2026-10-31 14:23:18.308574+00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:23:18.303786+00	0
13	Tour khám phá Hoàng Su Phì	Hành trình mẫu khám phá cảnh quan ruộng bậc thang và văn hóa vùng cao Hoàng Su Phì; cảnh quan thực tế thay đổi theo mùa.	moderate	3	Phù hợp người có sức khỏe cơ bản và có thể đi bộ trên địa hình đồi núi.	Hướng dẫn viên, bữa ăn theo chương trình và hỗ trợ hậu cần cơ bản.	Chi phí cá nhân, đồ uống ngoài chương trình và di chuyển đến điểm tập trung.		published	8	1	2026-10-31 14:23:21.458419+00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:23:21.453778+00	0
6	Tour Hà Giang	Hành trình mẫu khám phá cảnh quan núi đá và văn hóa vùng cao Hà Giang theo lịch trình của đoàn.	moderate	2	Cần sức khỏe cơ bản, chuẩn bị giày phù hợp và có thể đi bộ trên địa hình đồi núi.	Hướng dẫn viên và bữa ăn theo chương trình mẫu.	Chi phí cá nhân và phương tiện đến điểm tập trung.		published	8	1	2026-10-31 14:23:14.37666+00	\N	2026-10-30 13:04:46.184144+00	2026-11-01 06:40:51.525883+00	1
\.


--
-- Data for Name: tour_schedules; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_schedules (id, tour_id, starts_at, ends_at, capacity, held_seats, confirmed_seats, base_price_vnd, equipment_fee_vnd, deposit_percent, balance_due_at, meeting_point, contact_phone, status, actual_completed_at, completed_by, cancellation_reason, lock_version, created_at, updated_at, details_updated_at) FROM stdin;
1	2	2026-11-01 00:00:00+00	2026-11-03 00:00:00+00	15	0	0	250000	200000	30.00	2026-10-30 17:00:00+00	bến xe tà xùa	\N	scheduled	\N	\N	\N	0	2026-10-30 11:02:42.913351+00	2026-10-31 07:55:21.238694+00	2026-10-30 11:02:42.913351+00
2	3	2026-11-07 11:04:00+00	2026-11-09 11:04:00+00	15	0	0	2500000	200000	30.00	2026-11-06 11:04:00+00	\N	\N	scheduled	\N	\N	\N	0	2026-10-30 11:05:12.265857+00	2026-10-31 07:55:21.238694+00	2026-10-30 11:05:12.265857+00
3	4	2026-11-07 11:12:00+00	2026-11-09 11:12:00+00	15	0	0	2500000	200000	30.00	2026-11-06 11:12:00+00	\N	\N	scheduled	\N	\N	\N	0	2026-10-30 11:13:05.159438+00	2026-10-31 07:55:21.238694+00	2026-10-30 11:13:05.159438+00
4	5	2026-11-07 11:14:00+00	2026-11-09 11:14:00+00	15	0	0	2500000	200000	30.00	2026-11-04 11:14:00+00	\N	\N	scheduled	\N	\N	\N	0	2026-10-30 11:14:22.206964+00	2026-10-31 07:55:21.238694+00	2026-10-30 11:14:22.206964+00
5	6	2026-10-31 13:04:00+00	2026-11-02 13:04:00+00	15	0	0	2500000	200000	30.00	2026-10-30 13:04:00+00	\N	\N	scheduled	\N	\N	\N	0	2026-10-30 13:04:46.2334+00	2026-10-31 07:55:21.238694+00	2026-10-30 13:04:46.2334+00
8	9	2026-11-14 00:00:00+00	2026-11-16 00:00:00+00	15	0	0	2500000	200000	30.00	2026-11-11 00:00:00+00	Điểm tập trung do ban tổ chức thông báo	\N	scheduled	\N	\N	\N	0	2026-10-31 05:57:46.296568+00	2026-10-31 07:55:21.238694+00	2026-10-31 05:57:46.296568+00
9	10	2026-11-14 00:00:00+00	2026-11-16 00:00:00+00	15	0	0	3200000	200000	30.00	2026-11-11 00:00:00+00	Điểm tập trung tại Đà Lạt do ban tổ chức thông báo	\N	scheduled	\N	\N	\N	0	2026-10-31 05:57:46.296568+00	2026-10-31 07:55:21.238694+00	2026-10-31 05:57:46.296568+00
11	11	2026-11-01 23:00:00+00	2026-11-03 23:00:00+00	15	0	0	4950000	0	30.00	2026-11-01 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	0	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
12	13	2026-11-03 23:00:00+00	2026-11-06 23:00:00+00	15	0	0	3950000	0	30.00	2026-11-03 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	0	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
13	9	2026-11-05 23:00:00+00	2026-11-07 23:00:00+00	15	0	0	2500000	0	30.00	2026-11-05 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	0	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
14	12	2026-11-02 23:00:00+00	2026-11-04 23:00:00+00	15	0	0	3300000	0	30.00	2026-11-02 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	0	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
15	10	2026-11-06 23:00:00+00	2026-11-08 23:00:00+00	15	0	0	3200000	0	30.00	2026-11-06 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	0	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
10	6	2026-11-04 23:00:00+00	2026-11-06 23:00:00+00	15	0	1	2000	0	30.00	2026-11-04 05:00:00+00	Theo thông báo của ban tổ chức	\N	scheduled	\N	\N	\N	14	2026-10-31 14:20:07.696539+00	2026-11-02 05:41:19.66297+00	2026-11-01 10:27:56.005429+00
\.


--
-- Data for Name: bookings; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.bookings (id, booking_code, idempotency_key, customer_id, schedule_id, participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent, cancellation_policy_version_id, policy_snapshot, equipment_snapshot, status, hold_expires_at, balance_due_at, price_locked_at, cancelled_at, cancellation_reason, cancelled_by, lock_version, created_at, updated_at, group_rental_fee_vnd) FROM stdin;
3	LOCAL-TEST-3	local-test-3	2	10	1	f	2500000	0	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}	expired	2026-11-01 09:20:50.977645+00	2026-11-04 05:00:00+00	\N	\N	\N	\N	2	2026-11-01 09:05:50.951676+00	2026-11-01 09:21:47.006757+00	\N
7	LOCAL-TEST-7	local-test-7	2	10	1	t	2000	300000	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}	expired	2026-11-02 05:41:17.926781+00	2026-11-04 05:00:00+00	\N	\N	\N	\N	2	2026-11-02 05:26:17.847784+00	2026-11-02 05:41:19.66297+00	\N
6	LOCAL-TEST-6	local-test-6	2	10	1	f	2000	0	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}	confirmed	2026-11-01 11:00:21.600656+00	2026-11-04 05:00:00+00	2026-11-01 10:52:51.673203+00	\N	\N	\N	2	2026-11-01 10:45:21.583856+00	2026-11-05 10:01:38.035358+00	\N
1	LOCAL-TEST-1	local-test-1	2	10	1	t	2500000	300000	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}	expired	2026-11-01 07:06:00.350056+00	2026-11-04 05:00:00+00	\N	\N	\N	\N	2	2026-11-01 06:51:00.284119+00	2026-11-01 07:06:22.471437+00	\N
2	LOCAL-TEST-2	local-test-2	2	10	1	t	2500000	300000	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "choice": "TOUR_PACKAGE", "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "requestHash": "ae5f88dbb1606952f0b9ab356986822cd9f4372da7eff1cbd09ded5f567f73a6", "rentalFeeVnd": 300000, "participantCount": 1}	expired	2026-11-01 07:07:03.893764+00	2026-11-04 05:00:00+00	\N	\N	\N	\N	2	2026-11-01 06:52:03.887601+00	2026-11-01 07:07:22.495482+00	\N
4	LOCAL-TEST-4	local-test-4	2	10	1	f	2500000	0	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}	expired	2026-11-01 09:53:08.07058+00	2026-11-04 05:00:00+00	\N	\N	\N	\N	2	2026-11-01 09:38:08.044433+00	2026-11-01 09:53:47.080069+00	\N
5	LOCAL-TEST-5	local-test-5	2	10	1	f	2000	0	30.00	8	{"title": "Chính sách đổi/hủy tour", "content": "CHÍNH SÁCH ĐẶT CHỖ VÀ HỦY TOUR\\n\\n1. Giá tour và thanh toán\\nGiá tour, phụ phí thiết bị và tỷ lệ đặt cọc được hiển thị theo lịch khởi hành mà khách hàng lựa chọn.\\n\\nSau khi xác nhận đặt chỗ, hệ thống giữ chỗ trong 15 phút để khách hàng thực hiện thanh toán. Khách hàng có thể thanh toán tiền cọc hoặc toàn bộ giá trị đặt chỗ theo thông tin được hiển thị.\\n\\nNếu thanh toán tiền cọc, phần còn lại cần được thanh toán trước hạn ghi trong thông tin lịch khởi hành.\\n\\n2. Khách hàng hủy đặt chỗ\\nKhách hàng có thể gửi yêu cầu hủy đặt chỗ theo quy trình của hệ thống. Việc hoàn tiền, nếu có, được xem xét theo chính sách áp dụng tại thời điểm đặt chỗ và số tiền khách hàng đã thanh toán:\\n\\n• Hủy từ 7 ngày trở lên trước giờ khởi hành: hoàn 100% số tiền đã thanh toán.\\n• Hủy từ 3 ngày đến dưới 7 ngày trước giờ khởi hành: hoàn 50% số tiền đã thanh toán.\\n• Hủy dưới 3 ngày trước giờ khởi hành: không hoàn tiền.\\n\\nThời gian được tính theo giờ khởi hành của lịch đã đặt.\\n\\n3. Trường hợp tour không thể tổ chức\\nNếu chuyến đi không thể tổ chức do điều kiện thời tiết, an toàn hoặc các sự kiện bất khả kháng, WeTrek sẽ thông báo cho khách hàng và trao đổi phương án xử lý phù hợp.", "policyId": 7, "versionId": 8, "policyCode": "chinh-sach-doi-huy-7-3", "effectiveFrom": "2026-10-31T13:42:37.061621Z", "versionNumber": 1}	{"mode": "SELF_PROVIDED", "choice": "SELF_PROVIDED", "requestHash": "893b8b909e0ff1bd294004d621cfec8f202343260d1368b9173ff2d633fd471d", "participantCount": 1}	expired	2026-11-01 10:44:26.578456+00	2026-11-04 05:00:00+00	2026-11-01 11:03:01.510685+00	\N	\N	\N	2	2026-11-01 10:29:26.56826+00	2026-11-05 10:01:38.03804+00	\N
\.


--
-- Data for Name: carts; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.carts (customer_id, schedule_id, participant_count, includes_equipment, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: payment_attempts; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.payment_attempts (id, booking_id, provider, merchant_reference, purpose, requested_amount_vnd, status, expires_at, created_at, updated_at, checkout_url) FROM stdin;
1	1	payos	LOCAL-TEST-ATTEMPT-1	deposit	840000	pending	2026-11-01 07:06:00.350056+00	2026-11-01 06:51:12.673543+00	2026-11-01 06:51:12.673543+00	\N
3	5	payos	LOCAL-TEST-ATTEMPT-3	deposit	600	pending	2026-11-01 10:44:26.578456+00	2026-11-01 10:40:21.028696+00	2026-11-01 10:40:21.028696+00	\N
4	6	payos	LOCAL-TEST-ATTEMPT-4	full	2000	succeeded	2026-11-01 11:00:21.600656+00	2026-11-01 10:45:26.910458+00	2026-11-01 10:52:51.673203+00	\N
2	5	payos	LOCAL-TEST-ATTEMPT-2	full	2000	succeeded	2026-11-01 10:44:26.578456+00	2026-11-01 10:29:30.407974+00	2026-11-01 11:03:01.510685+00	\N
\.


--
-- Data for Name: customer_payments; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.customer_payments (id, booking_id, attempt_id, provider, transaction_reference, amount_vnd, gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url, created_at, updated_at) FROM stdin;
10	6	4	payos	LOCAL-TEST-PAY-10	2000	0	accepted	2026-11-01 10:52:51.748771+00	\N	\N	2026-11-01 10:52:51.673203+00	2026-11-01 10:52:51.673203+00
11	5	2	payos	LOCAL-TEST-PAY-11	2000	0	pending_review	2026-11-01 11:03:01.518566+00	\N	\N	2026-11-01 11:03:01.510685+00	2026-11-01 11:03:01.510685+00
\.


--
-- Data for Name: refund_requests; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.refund_requests (id, booking_id, requested_by, reason, requested_amount_vnd, approved_amount_vnd, status, idempotency_key, approved_by, approved_at, decision_note, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: support_requests; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.support_requests (id, requester_id, booking_id, payment_id, refund_request_id, subject, description, category, status, assigned_to, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: chats; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.chats (id, owner_id, assigned_staff_id, current_tour_id, current_booking_id, support_request_id, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: chat_messages; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.chat_messages (id, chat_id, sender_type, sender_id, body, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: chat_message_sources; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.chat_message_sources (message_id, document_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: customer_refunds; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.customer_refunds (id, refund_request_id, booking_id, original_payment_id, amount_vnd, channel, transaction_reference, idempotency_key, refunded_at, recorded_by, evidence_url, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: equipment; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment (id, name, description, created_at, updated_at, individual_rental_price_vnd, image_url, storage_key) FROM stdin;
1	Test tent	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	180000	\N	\N
2	Test sleeping bag	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	70000	\N	\N
3	Test sleeping mat	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	30000	\N	\N
4	Test camping stove	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	40000	\N	\N
5	Test cookware	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	100000	\N	\N
6	Test trekking backpack	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	35000	\N	\N
7	Test waterproof bag	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	25000	\N	\N
8	Test headlamp	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	80000	\N	\N
9	Test trekking poles	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	60000	\N	\N
10	Test camp stove	Local test equipment	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	25000	\N	\N
\.


--
-- Data for Name: equipment_categories; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_categories (id, name, description, is_active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: rental_package_templates; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.rental_package_templates (id, title, shared_items, personal_items_to_bring, rental_price_vnd, created_at, updated_at, image_url, storage_key) FROM stdin;
1	Local test: camping set	Tent and sleeping equipment	Personal clothing and water	300000	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N	\N
2	Local test: cooking set	Portable cooking equipment	Personal food and water	125000	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N	\N
3	Local test: trekking set	Trekking equipment	Personal clothing and water	150000	2026-10-29 06:36:36.766371+00	2026-10-29 06:36:36.766371+00	\N	\N
\.


--
-- Data for Name: tour_equipment_packages; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_equipment_packages (id, tour_id, title, included_items, shared_items, personal_items_to_bring, created_at, updated_at, rental_price_vnd, package_template_id, active, needs_review) FROM stdin;
1	6	Combo cắm trại 2 người	Lều 2 người × 1, Túi ngủ × 2, Thảm cách nhiệt × 2	\N	\N	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	300000	1	t	f
2	6	Combo nấu ăn dã ngoại	Bếp gas dã ngoại × 1, Bộ nồi dã ngoại × 1	\N	\N	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	125000	3	t	f
3	6	Combo trekking cá nhân	Balo trekking 40L × 1, Gậy trekking × 1, Đèn đội đầu × 1, Túi chống nước × 1	\N	\N	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	150000	2	t	f
\.


--
-- Data for Name: equipment_reservations; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_reservations (id, booking_id, schedule_id, package_id, selection_key, status, participant_count, rental_fee_vnd, hold_expires_at, equipment_snapshot, created_at, updated_at) FROM stdin;
1	1	10	1	{"packageQuantities":{"1":1},"itemQuantities":{}}	released	1	300000	2026-11-01 07:06:00.350056+00	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}	2026-11-01 06:51:00.284119+00	2026-11-01 07:06:22.397873+00
2	2	10	1	{"packageQuantities":{"1":1},"itemQuantities":{}}	released	1	300000	2026-11-01 07:07:03.893764+00	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}	2026-11-01 06:52:03.887601+00	2026-11-01 07:07:22.467583+00
3	7	10	1	{"packageQuantities":{"1":1},"itemQuantities":{}}	released	1	300000	2026-11-02 05:41:17.926781+00	{"mode": "SELECTED", "items": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "selectedQuantity": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "selectedQuantity": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "selectedQuantity": 2, "quantityInPackage": 2}], "packages": [{"title": "Combo cắm trại 2 người", "quantity": 1, "packageId": 1, "components": [{"name": "Lều 2 người", "itemId": 1, "equipmentId": 1, "quantityInPackage": 1}, {"name": "Túi ngủ", "itemId": 2, "equipmentId": 2, "quantityInPackage": 2}, {"name": "Thảm cách nhiệt", "itemId": 3, "equipmentId": 3, "quantityInPackage": 2}], "unitPriceVnd": 300000}], "rentalFeeVnd": 300000, "participantCount": 1}	2026-11-02 05:26:17.847784+00	2026-11-02 05:41:19.647525+00
\.


--
-- Data for Name: equipment_handover_records; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_handover_records (id, reservation_id, action, actor_id, note, occurred_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: equipment_handover_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_handover_items (id, handover_record_id, equipment_id, quantity, condition, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: equipment_inventory; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_inventory (equipment_id, category_id, total_quantity, maintenance_quantity, is_active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: equipment_reservation_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.equipment_reservation_items (id, reservation_id, equipment_id, quantity, issued_quantity, returned_quantity, created_at, updated_at) FROM stdin;
1	1	1	1	0	0	2026-11-01 06:51:00.284119+00	2026-11-01 06:51:00.284119+00
2	1	2	2	0	0	2026-11-01 06:51:00.284119+00	2026-11-01 06:51:00.284119+00
3	1	3	2	0	0	2026-11-01 06:51:00.284119+00	2026-11-01 06:51:00.284119+00
4	2	1	1	0	0	2026-11-01 06:52:03.887601+00	2026-11-01 06:52:03.887601+00
5	2	2	2	0	0	2026-11-01 06:52:03.887601+00	2026-11-01 06:52:03.887601+00
6	2	3	2	0	0	2026-11-01 06:52:03.887601+00	2026-11-01 06:52:03.887601+00
7	3	1	1	0	0	2026-11-02 05:26:17.847784+00	2026-11-02 05:26:17.847784+00
8	3	2	2	0	0	2026-11-02 05:26:17.847784+00	2026-11-02 05:26:17.847784+00
9	3	3	2	0	0	2026-11-02 05:26:17.847784+00	2026-11-02 05:26:17.847784+00
\.


--
-- Data for Name: guides; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.guides (id, account_id, bio, active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: locations; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.locations (id, name, province_name, latitude, longitude, created_at, updated_at) FROM stdin;
\.

`r`n

--
-- Data for Name: participants; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.participants (id, booking_id, account_id, full_name, phone, email, checked_in_at, checked_in_by, check_in_note, created_at, updated_at) FROM stdin;
1	1	2	Local Test Participant 1	0900000000	participant1@trekking.test	\N	\N	\N	2026-11-01 06:51:00.284119+00	2026-11-01 06:51:00.284119+00
2	2	2	Local Test Participant 2	0900000000	participant2@trekking.test	\N	\N	\N	2026-11-01 06:52:03.887601+00	2026-11-01 06:52:03.887601+00
3	3	2	Local Test Participant 3	0900000000	participant3@trekking.test	\N	\N	\N	2026-11-01 09:05:50.951676+00	2026-11-01 09:05:50.951676+00
4	4	2	Local Test Participant 4	0900000000	participant4@trekking.test	\N	\N	\N	2026-11-01 09:38:08.044433+00	2026-11-01 09:38:08.044433+00
5	5	2	Local Test Participant 5	0900000000	participant5@trekking.test	\N	\N	\N	2026-11-01 10:29:26.56826+00	2026-11-01 10:29:26.56826+00
6	6	2	Local Test Participant 6	0900000000	participant6@trekking.test	\N	\N	\N	2026-11-01 10:45:21.583856+00	2026-11-01 10:45:21.583856+00
7	7	2	Local Test Participant 7	0900000000	participant7@trekking.test	\N	\N	\N	2026-11-02 05:26:17.847784+00	2026-11-02 05:26:17.847784+00
\.


--
-- Data for Name: payment_notices; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.payment_notices (id, attempt_id, provider, event_key, signature_valid, payload, received_at, processed_at, processing_error, created_at, updated_at) FROM stdin;
10	4	payos	LOCAL-TEST-EVENT-10	t	{"localTest": true}	2026-11-01 10:52:51.721031+00	2026-11-01 10:52:51.794484+00	\N	2026-11-01 10:52:51.673203+00	2026-11-01 10:52:51.673203+00
11	2	payos	LOCAL-TEST-EVENT-11	t	{"localTest": true}	2026-11-01 11:03:01.517177+00	2026-11-01 11:03:01.524379+00	\N	2026-11-01 11:03:01.510685+00	2026-11-01 11:03:01.510685+00
\.


--
-- Data for Name: policy_version_revocations; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.policy_version_revocations (policy_version_id, revoked_by, revoked_at, reason) FROM stdin;
\.


--
-- Data for Name: rental_package_template_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.rental_package_template_items (id, package_template_id, equipment_id, quantity_in_package, individual_rental_price_vnd, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: reviews; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.reviews (id, booking_id, author_id, participant_id, tour_rating, service_rating, content, equipment_feedback, image_urls, status, moderated_by, moderation_reason, operator_reply, replied_by, replied_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: review_votes; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.review_votes (review_id, account_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: schedule_guides; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.schedule_guides (schedule_id, guide_id, is_lead, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: support_messages; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.support_messages (id, support_request_id, author_id, body, attachment_urls, is_internal, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: tour_activities; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_activities (id, tour_id, day_number, display_order, title, description, start_time, location_id, created_at, updated_at) FROM stdin;
2	10	1	0	Tập trung và phổ biến an toàn	Gặp hướng dẫn viên, kiểm tra đồ cá nhân và nghe hướng dẫn an toàn.	07:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
4	10	1	1	Bắt đầu trekking	Di chuyển theo cung đường và nghỉ theo hướng dẫn của trưởng đoàn.	09:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
6	10	1	2	Dựng trại và ăn tối	Nghỉ ngơi, ăn tối và sinh hoạt tại khu cắm trại.	17:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
8	10	2	0	Ăn sáng và chuẩn bị	Thu dọn khu trại, dùng bữa sáng và kiểm tra hành lý.	06:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
10	10	2	1	Tiếp tục hành trình	Trekking về điểm kết thúc và nghỉ giữa chặng.	08:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
12	10	2	2	Kết thúc chương trình	Tập trung, kiểm tra quân số và kết thúc chương trình.	15:00:00	\N	2026-10-31 05:57:46.296568+00	2026-10-31 05:57:46.296568+00
13	9	1	0	Tập trung và phổ biến an toàn	Gặp hướng dẫn viên, kiểm tra đồ cá nhân và nghe hướng dẫn an toàn.	07:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
14	9	1	1	Bắt đầu trekking	Di chuyển theo cung đường và nghỉ theo hướng dẫn của trưởng đoàn.	09:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
15	9	1	2	Dựng trại và ăn tối	Nghỉ ngơi, ăn tối và sinh hoạt tại khu cắm trại.	17:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
16	9	2	0	Ăn sáng và chuẩn bị	Thu dọn khu trại, dùng bữa sáng và kiểm tra hành lý.	06:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
17	9	2	1	Tiếp tục hành trình	Trekking về điểm kết thúc và nghỉ giữa chặng.	08:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
18	9	2	2	Kết thúc chương trình	Tập trung, kiểm tra quân số và kết thúc chương trình.	15:00:00	\N	2026-10-31 13:43:34.161287+00	2026-10-31 13:43:34.161287+00
19	11	1	0	Tập trung và chuẩn bị	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
20	11	2	0	Tiếp tục hành trình	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
21	12	1	0	Tập trung và chuẩn bị	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
22	12	2	0	Tiếp tục hành trình	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
23	13	1	0	Tập trung và chuẩn bị	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
24	13	2	0	Tiếp tục hành trình	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
25	13	3	0	Tiếp tục hành trình	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
26	6	1	0	Tập trung và chuẩn bị	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
27	6	2	0	Tiếp tục hành trình	Nội dung mẫu; thời gian và hoạt động cụ thể do ban tổ chức xác nhận trước chuyến đi.	07:00:00	\N	2026-10-31 14:20:07.696539+00	2026-10-31 14:20:07.696539+00
\.


--
-- Data for Name: tour_questions; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_questions (id, tour_id, author_id, question, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: tour_answers; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_answers (id, question_id, author_id, answer, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: tour_equipment_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_equipment_items (id, package_id, equipment_id, quantity_in_package, individual_rental_price_vnd, created_at, updated_at, tour_id) FROM stdin;
1	1	1	1	180000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
2	1	2	2	70000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
3	1	3	2	30000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
4	2	8	1	80000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
5	2	9	1	60000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
6	3	5	1	100000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
7	3	4	1	40000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
8	3	6	1	35000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
9	3	10	1	25000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
10	\N	7	1	25000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
11	\N	5	1	100000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
12	\N	8	1	80000	2026-11-01 06:40:51.525883+00	2026-11-01 06:40:51.525883+00	6
\.


--
-- Data for Name: tour_itinerary_days; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_itinerary_days (id, tour_id, day_number, title, meals, overnight) FROM stdin;
6	10	1	Khởi hành và khám phá rừng Bidoup	Trưa, tối	Cắm trại
8	10	2	Trekking về điểm đón	Sáng, trưa	Không
9	9	1	Tập trung và trekking qua đồi cỏ	Trưa, tối	Cắm trại
10	9	2	Trekking về điểm kết thúc	Sáng, trưa	Không
11	11	1	Tập trung và bắt đầu hành trình	Theo chương trình của đoàn	Theo sắp xếp của ban tổ chức
12	11	2	Hoàn tất hành trình và trở về	Theo chương trình của đoàn	Không có
13	12	1	Tập trung và bắt đầu hành trình	Theo chương trình của đoàn	Theo sắp xếp của ban tổ chức
14	12	2	Hoàn tất hành trình và trở về	Theo chương trình của đoàn	Không có
15	13	1	Tập trung và bắt đầu hành trình	Theo chương trình của đoàn	Theo sắp xếp của ban tổ chức
16	13	2	Khám phá cung đường	Theo chương trình của đoàn	Theo sắp xếp của ban tổ chức
17	13	3	Hoàn tất hành trình và trở về	Theo chương trình của đoàn	Không có
18	6	1	Tập trung và bắt đầu hành trình	Theo chương trình của đoàn	Theo sắp xếp của ban tổ chức
19	6	2	Hoàn tất hành trình và trở về	Theo chương trình của đoàn	Không có
\.


--
-- Data for Name: tour_locations; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_locations (tour_id, location_id, visit_order, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: tour_media; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_media (id, tour_id, media_type, url, caption, display_order, is_cover, created_at, updated_at, storage_key) FROM stdin;
1	2	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793358166/trekking/tours/2/e6116327-2f88-429e-8900-23fc5c37b8aa.jpg	\N	0	t	2026-10-30 11:02:47.379899+00	2026-10-30 11:02:47.379899+00	trekking/tours/2/e6116327-2f88-429e-8900-23fc5c37b8aa
2	4	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793358787/trekking/tours/4/5c032927-5d2a-40ed-af8a-af57edc38ad1.jpg	\N	0	t	2026-10-30 11:13:08.681571+00	2026-10-30 11:13:08.681571+00	trekking/tours/4/5c032927-5d2a-40ed-af8a-af57edc38ad1
5	9	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793427506/trekking/tours/9/dff2aaf8-8acb-43b1-afd6-2bef567eff1d.jpg	\N	1	t	2026-10-31 06:18:26.596945+00	2026-10-31 06:18:35.507352+00	trekking/tours/9/dff2aaf8-8acb-43b1-afd6-2bef567eff1d
6	11	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456456/trekking/tours/11/7eaf251d-44cf-4d8a-931e-ecd4d0df95fb.png	Ảnh minh họa Tà Chì Nhù	0	t	2026-10-31 14:21:07.887736+00	2026-10-31 14:21:07.887736+00	trekking/tours/11/7eaf251d-44cf-4d8a-931e-ecd4d0df95fb
7	12	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456459/trekking/tours/12/6ba76e3a-0690-4925-996d-9648fdda345f.png	Ảnh minh họa Pha Luông	0	t	2026-10-31 14:21:08.324884+00	2026-10-31 14:21:08.324884+00	trekking/tours/12/6ba76e3a-0690-4925-996d-9648fdda345f
8	13	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456461/trekking/tours/13/90b4a66f-2ea5-4b13-b849-51526348c0de.png	Ảnh minh họa Hoàng Su Phì	0	t	2026-10-31 14:21:08.789995+00	2026-10-31 14:21:08.789995+00	trekking/tours/13/90b4a66f-2ea5-4b13-b849-51526348c0de
9	10	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456466/trekking/tours/10/0bde2da4-4177-4da9-b5e2-33d7c45fd3cc.png	Ảnh minh họa Bidoup – Núi Bà	0	t	2026-10-31 14:21:09.23258+00	2026-10-31 14:21:09.23258+00	trekking/tours/10/0bde2da4-4177-4da9-b5e2-33d7c45fd3cc
3	6	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793365489/trekking/tours/6/03331b2e-39a8-44ac-853a-996271172d70.jpg	\N	0	f	2026-10-30 13:04:50.450628+00	2026-10-31 14:21:28.174422+00	trekking/tours/6/03331b2e-39a8-44ac-853a-996271172d70
10	6	image	https://res.cloudinary.com/dv1t6kdio/image/upload/v1793456487/trekking/tours/6/071910d9-f9de-4257-b45e-958f6971aec0.png	Ảnh minh họa Hà Giang	1	t	2026-10-31 14:21:28.174422+00	2026-10-31 14:21:28.174422+00	trekking/tours/6/071910d9-f9de-4257-b45e-958f6971aec0
\.


--
-- Data for Name: tour_participant_checkouts; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_participant_checkouts (participant_id, checked_out_at, checked_out_by, note) FROM stdin;
\.


--
-- Data for Name: tour_required_items; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_required_items (id, tour_id, name, is_required, note, display_order, created_at, updated_at, equipment_id) FROM stdin;
\.


--
-- Data for Name: tour_schedule_defaults; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_schedule_defaults (tour_id, capacity, base_price_vnd, equipment_fee_vnd, deposit_percent, meeting_point, contact_phone) FROM stdin;
\.


--
-- Data for Name: tour_terms; Type: TABLE DATA; Schema: trekking; Owner: -
--

COPY trekking.tour_terms (id, tour_id, policy_version_id, display_order) FROM stdin;
10	6	7	0
11	6	5	1
12	6	6	2
13	9	5	0
14	9	6	1
15	9	7	2
16	10	5	0
17	10	6	1
18	10	7	2
19	11	5	0
20	11	6	1
21	11	7	2
22	12	5	0
23	12	6	1
24	12	7	2
25	13	5	0
26	13	6	1
27	13	7	2
\.


--
-- Name: ai_reference_documents_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.ai_reference_documents_id_seq', 1, false);


--
-- Name: app_accounts_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.app_accounts_id_seq', 2, true);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.audit_logs_id_seq', 1, false);


--
-- Name: bookings_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.bookings_id_seq', 7, true);


--
-- Name: chat_messages_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.chat_messages_id_seq', 1, false);


--
-- Name: chats_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.chats_id_seq', 1, false);


--
-- Name: customer_payments_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.customer_payments_id_seq', 11, true);


--
-- Name: customer_refunds_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.customer_refunds_id_seq', 1, false);


--
-- Name: equipment_categories_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_categories_id_seq', 1, false);


--
-- Name: equipment_handover_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_handover_items_id_seq', 1, false);


--
-- Name: equipment_handover_records_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_handover_records_id_seq', 1, false);


--
-- Name: equipment_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_id_seq', 10, true);


--
-- Name: equipment_reservation_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_reservation_items_id_seq', 9, true);


--
-- Name: equipment_reservations_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.equipment_reservations_id_seq', 3, true);


--
-- Name: guides_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.guides_id_seq', 1, false);


--
-- Name: locations_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.locations_id_seq', 1, false);




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
-- Name: policies_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.policies_id_seq', 4, true);


--
-- Name: policy_versions_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.policy_versions_id_seq', 8, true);


--
-- Name: refund_requests_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.refund_requests_id_seq', 1, false);


--
-- Name: rental_package_template_items_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.rental_package_template_items_id_seq', 1, false);


--
-- Name: rental_package_templates_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.rental_package_templates_id_seq', 3, true);


--
-- Name: reviews_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.reviews_id_seq', 1, false);


--
-- Name: support_messages_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.support_messages_id_seq', 1, false);


--
-- Name: support_requests_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.support_requests_id_seq', 1, false);


--
-- Name: tour_activities_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_activities_id_seq', 27, true);


--
-- Name: tour_answers_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_answers_id_seq', 1, false);


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
-- Name: tour_questions_id_seq; Type: SEQUENCE SET; Schema: trekking; Owner: -
--

SELECT pg_catalog.setval('trekking.tour_questions_id_seq', 1, false);


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

SELECT pg_catalog.setval('trekking.tours_id_seq', 1, false);


--
-- PostgreSQL database dump complete
--

\unrestrict yaZDetwFNptgBLwGclFHhlxWeufF5iCjJoKBb1KGjf56alXKMx1npMozKKtorSb

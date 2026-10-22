# File mẫu để test API trong Apidog

Tất cả file trong thư mục này là dữ liệu giả lập cho backend local. PDF có nhãn `TEST FIXTURE / KHONG CO GIA TRI PHAP LY`, không mô phỏng con dấu, chữ ký hoặc giấy phép do cơ quan nhà nước cấp.

| File | Dùng ở đâu | Cách dùng / kết quả mong đợi |
| --- | --- | --- |
| `sample-license.pdf` | **CO-15** upload giấy phép | Body -> form-data -> `file` -> chọn file này. MIME `application/pdf`, HTTP 200; `licenseAvailable=true`, công ty vẫn `pending`. |
| `sample-license.png` | Thay thế PDF ở **CO-15**, nếu file PNG có sẵn | Chọn một trong PDF hoặc PNG cho lượt thử. Dùng MIME đúng theo file; không bắt buộc upload cả hai. |
| `cooperation-policy.txt` | **POL-07** publish policy version | Copy toàn bộ nội dung vào chuỗi JSON `content`; giữ nguyên các trường khác. Đây không phải endpoint upload file. |
| `payout-terms.txt` | **POL-07** publish policy version | Copy toàn bộ nội dung vào chuỗi JSON `payoutTermsSnapshot`; giữ `platformFeePercent=10.00`, `payoutWaitHours=48`. |
| `policy-version-body.json` | **POL-07** publish policy version | Copy toàn bộ JSON vào raw body để dùng sẵn hai nội dung TXT với newline được escape đúng. Giữ effectiveFrom dưới dạng biến của environment. |
| `invalid-license.txt` | Negative upload bổ sung trước **CO-15** | Duplicate request CO-15 thành `CO-15-NEG`, chọn TXT; mong đợi HTTP 400 / `INVALID_COMPANY_LICENSE`. Sửa test status/error trong bản duplicate, rồi chạy CO-15 gốc bằng PDF. |

## Luồng thử với file

1. Hoàn thành **CO-01** và các bước trước CO-15 theo [kịch bản](../TEST_SCENARIOS.md). File PDF có tên công ty, mã thuế và email mẫu cố định; chúng không tự cập nhật theo `runId`, `taxCode` hoặc email trong environment. Backend kiểm tra định dạng file; admin phải hiểu đây là hồ sơ giả lập. Nếu muốn nội dung khớp environment, sinh lại PDF bằng tùy chọn lệnh bên dưới trước khi upload.
2. Negative upload là bước tùy chọn. Thực hiện trên công ty test đang pending; kiểm tra lỗi rồi dùng CO-15 gốc upload PDF thành công. Không đổi đuôi TXT thành PDF.
3. Dùng **CO-16** và **CO-22** download giấy phép, lưu response thành file PDF và mở kiểm tra tiêu đề/cảnh báo. Response là binary, không phải JSON.
4. Sau khi công ty được duyệt, dùng **POL-02** tạo policy và **POL-07** publish version. Dán hai nội dung TXT vào chuỗi JSON tương ứng bằng trình sửa JSON của Apidog; nếu sửa raw JSON trực tiếp phải escape dấu xuống dòng và dấu ngoặc kép đúng JSON.
5. Tiếp tục nhóm **AGR**: yêu cầu OTP, đọc email, nhập `agreementOtp`, ký và xem receipt. Bản chụp phải giữ nguyên nội dung, phí 10% và thời gian chờ 48 giờ.

## Sinh lại file

Từ thư mục gốc repository, có Python và `reportlab`:

```powershell
python tools/generate_test_documents.py
```

Để PDF khớp dữ liệu company của lượt thử (chỉ nhập thông tin giả lập):

```powershell
python tools/generate_test_documents.py --company-name "Công ty Trek Apidog 123456" --tax-code "QA-123456" --representative "Đại diện QA Mẫu" --contact-email "qa@example.invalid"
```

Tùy chọn `--preview` render PDF bằng PyMuPDF vào `tmp/pdfs/sample-license-page-1.png`. Generator này không tạo `sample-license.png`, không gọi API và không đổi database.

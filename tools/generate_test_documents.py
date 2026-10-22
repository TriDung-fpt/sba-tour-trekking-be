"""Create synthetic local API upload/policy fixtures without calling the backend.

Requires reportlab. Optional --preview requires PyMuPDF (import pymupdf).
The default PDF bytes and text files are reproducible across repeated runs with
the same installed fonts/reportlab version. No real credentials or PII are used.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/api/testing/fixtures"
DISCLAIMER = "TEST FIXTURE / KHONG CO GIA TRI PHAP LY"

COOPERATION_POLICY = """CHÍNH SÁCH HỢP TÁC THỬ NGHIỆM - KHÔNG CÓ GIÁ TRỊ PHÁP LÝ

1. Phạm vi: Công ty mẫu cung cấp dịch vụ trekking giả lập trong môi trường local. Tất cả thông tin, hồ sơ và giao dịch trong bài thử chỉ phục vụ kiểm tra API.
2. Trách nhiệm: Người quản lý công ty kiểm tra hồ sơ mẫu, tải giấy phép thử nghiệm và đọc phiên bản chính sách trước khi chấp nhận. Admin duyệt hồ sơ theo kịch bản kiểm thử.
3. Phí nền tảng: 10% theo trường platformFeePercent của phiên bản chính sách. Giá trị trong API là nguồn dữ liệu kiểm tra; nội dung văn bản phải nhất quán với giá trị này.
4. Đối soát: Thời gian chờ 48 giờ theo payoutWaitHours. Chi tiết tại payoutTermsSnapshot của cùng phiên bản chính sách.
5. Xác nhận: Chủ công ty yêu cầu OTP và nhập OTP mới nhất nhận được qua email để chấp nhận chính sách. Không ghi OTP vào tài liệu này.
6. Bằng chứng: Sau khi ký, kiểm tra agreement receipt lưu đúng policyVersionId, phí 10%, thời gian chờ 48 giờ và bản chụp nội dung chính sách.
7. Giới hạn: Đây là nội dung thử nghiệm, không tạo hợp đồng, nghĩa vụ thanh toán hay giấy phép kinh doanh thực tế.
"""

PAYOUT_TERMS = """ĐIỀU KHOẢN ĐỐI SOÁT THỬ NGHIỆM - KHÔNG CÓ GIÁ TRỊ PHÁP LÝ

Phí nền tảng mẫu: 10% (platformFeePercent = 10.00).
Thời gian chờ mẫu: 48 giờ (payoutWaitHours = 48), tính từ khi dịch vụ giả lập đã hoàn tất và đủ điều kiện đối soát.
Ví dụ để đọc hiểu dữ liệu: doanh thu giả lập 1.000.000 VND, phí 10% tương ứng 100.000 VND; phần còn lại 900.000 VND trước các điều chỉnh giả lập khác nếu có.
Ví dụ này chỉ giải thích nội dung bản chụp. Các API hiện tại không thực hiện chuyển tiền và bài thử không xác nhận khả năng tính toán hoặc chi trả thực tế.
Receipt sau khi ký phải giữ nguyên nội dung payoutTermsSnapshot của phiên bản đã chấp nhận, kể cả khi sau này có phiên bản chính sách mới.
"""

INVALID_LICENSE = """INVALID UPLOAD TEST FIXTURE - NOT A PDF, PNG OR JPEG
This is an ordinary UTF-8 text file for a negative upload check.
Select this .txt file as multipart form-data field `file` on a disposable pending company.
Expected: HTTP 400, error code INVALID_COMPANY_LICENSE.
Do not rename this file to .pdf or overwrite a valid upload test fixture.
"""

MANIFEST = """# File mẫu để test API trong Apidog

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
"""


def register_fonts() -> None:
    candidates = [
        (Path("C:/Windows/Fonts/arial.ttf"), Path("C:/Windows/Fonts/arialbd.ttf")),
        (Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
         Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")),
    ]
    for regular, bold in candidates:
        if regular.is_file() and bold.is_file():
            pdfmetrics.registerFont(TTFont("FixtureRegular", str(regular)))
            pdfmetrics.registerFont(TTFont("FixtureBold", str(bold)))
            return
    raise RuntimeError("Cần Arial hoặc DejaVu Sans regular/bold để hiển thị tiếng Việt.")


def create_license(destination: Path, args: argparse.Namespace) -> None:
    register_fonts()
    width, height = A4
    navy = colors.HexColor("#173042")
    teal = colors.HexColor("#267A78")
    ink = colors.HexColor("#253D4B")
    muted = colors.HexColor("#596D79")
    line = colors.HexColor("#DCE6EB")
    document = canvas.Canvas(str(destination), pagesize=A4, invariant=1, pageCompression=1)
    document.setTitle("Trekking QA - Hồ sơ giấy phép mẫu")
    document.setAuthor("Local API test fixture")
    document.setSubject(DISCLAIMER)

    def text(value: str, x: float, y: float, size: float = 10, bold: bool = False, color=ink) -> None:
        document.setFillColor(color)
        document.setFont("FixtureBold" if bold else "FixtureRegular", size)
        document.drawString(x, y, value)

    def paragraph(value: str, x: float, top: float, available_width: float,
                  size: float = 10, color=ink, bold: bool = False) -> float:
        style = ParagraphStyle("fixture", fontName="FixtureBold" if bold else "FixtureRegular",
                               fontSize=size, leading=size * 1.5, textColor=color)
        block = Paragraph(escape(value), style)
        _, block_height = block.wrap(available_width, height)
        block.drawOn(document, x, top - block_height)
        return top - block_height

    document.setFillColor(navy)
    document.rect(0, height - 154, width, 154, fill=1, stroke=0)
    text("TREKKING / LOCAL QA", 44, height - 43, 10, True, colors.HexColor("#9DD8D1"))
    text("Hồ sơ giấy phép mẫu", 44, height - 85, 25, True, colors.white)
    text("Tệp thử nghiệm cho luồng upload và download giấy phép công ty", 44, height - 113, 10, False, colors.HexColor("#D6E4EA"))

    document.setFillColor(colors.HexColor("#FFF1DE"))
    document.roundRect(40, height - 225, width - 80, 48, 7, fill=1, stroke=0)
    text(DISCLAIMER, 54, height - 204, 10, True, colors.HexColor("#8C510C"))
    text("Dữ liệu giả lập. Không dùng làm hồ sơ pháp lý hoặc giấy phép thực tế.", 54, height - 219, 9, False, colors.HexColor("#8C510C"))

    text("01  THÔNG TIN DOANH NGHIỆP GIẢ LẬP", 44, 580, 10, True, teal)
    fields = [
        ("Tên công ty", args.company_name),
        ("Mã số thuế mẫu", args.tax_code),
        ("Đại diện mẫu", args.representative),
        ("Email liên hệ mẫu", args.contact_email),
        ("Địa chỉ mẫu", "Khu dữ liệu QA, địa chỉ giả lập cho thử nghiệm local"),
        ("Mã tài liệu / ngày mẫu", "QA-LICENSE-001 / 21-10-2026"),
    ]
    top = 550
    for label, value in fields:
        text(label, 44, top - 11, 9, False, muted)
        bottom = paragraph(value, 200, top, width - 244, 10, bold=True)
        top = min(top - 43, bottom - 20)
        document.setStrokeColor(line)
        document.setLineWidth(0.6)
        document.line(44, top + 12, width - 44, top + 12)

    section_top = top - 7
    text("02  NỘI DUNG DÙNG CHO BÀI THỬ", 44, section_top, 10, True, teal)
    bottom = paragraph(
        "Tài liệu này giúp kiểm tra việc nhận file PDF, lưu giấy phép, hiển thị licenseAvailable "
        "và tải lại đúng nội dung. Đây không phải bằng chứng công ty đã được cấp phép. "
        "Bài thử sử dụng công ty và tài khoản riêng trong môi trường local.",
        44, section_top - 19, width - 88, 10,
    )
    paragraph(
        "Apidog: CO-15 upload file này bằng field file; CO-16 và CO-22 tải lại để kiểm tra. "
        "Tên công ty và mã thuế ở đây là giá trị mẫu cố định, cần sinh lại nếu muốn khớp environment.",
        44, bottom - 14, width - 88, 9, muted,
    )

    document.setStrokeColor(line)
    document.line(44, 57, width - 44, 57)
    text("TEST FIXTURE ONLY / KHÔNG CÓ GIÁ TRỊ PHÁP LÝ", 44, 39, 8, True, muted)
    text("01 / 01", width - 82, 39, 8, False, muted)
    document.showPage()
    document.save()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--company-name", default="Công ty Trek QA Mẫu")
    parser.add_argument("--tax-code", default="QA-DEMO-001")
    parser.add_argument("--representative", default="Đại diện QA Mẫu")
    parser.add_argument("--contact-email", default="qa@example.invalid")
    parser.add_argument("--preview", action="store_true", help="Render one-page PNG under tmp/pdfs using PyMuPDF")
    args = parser.parse_args()
    for name in ["company_name", "tax_code", "representative", "contact_email"]:
        value = getattr(args, name)
        if not value.strip() or len(value) > 100:
            parser.error(name.replace("_", "-") + " phải có từ 1 đến 100 ký tự")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    pdf_path = OUTPUT / "sample-license.pdf"
    create_license(pdf_path, args)
    for name, content in {
        "cooperation-policy.txt": COOPERATION_POLICY,
        "payout-terms.txt": PAYOUT_TERMS,
        "invalid-license.txt": INVALID_LICENSE,
        "TEST_FILES.md": MANIFEST,
    }.items():
        (OUTPUT / name).write_text(content, encoding="utf-8", newline="\n")
    publish_body = {
        "content": COOPERATION_POLICY.strip(),
        "effectiveFrom": "{{effectiveFrom}}",
        "platformFeePercent": 10.00,
        "payoutWaitHours": 48,
        "payoutTermsSnapshot": PAYOUT_TERMS.strip(),
    }
    (OUTPUT / "policy-version-body.json").write_text(
        json.dumps(publish_body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if args.preview:
        import pymupdf as fitz
        scratch = ROOT / "tmp/pdfs"
        scratch.mkdir(parents=True, exist_ok=True)
        with fitz.open(pdf_path) as pdf:
            assert len(pdf) == 1, "Sample license must have exactly one page"
            extracted = pdf[0].get_text()
            assert DISCLAIMER in extracted, "PDF warning is missing"
            assert args.company_name in extracted, "Vietnamese company name was not preserved"
            pdf[0].get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False).save(scratch / "sample-license-page-1.png")
        print("Preview: tmp/pdfs/sample-license-page-1.png")
    print("Generated 6 synthetic local API fixtures in docs/api/testing/fixtures/")
    print("sample-license.pdf SHA256: " + hashlib.sha256(pdf_path.read_bytes()).hexdigest())
    print("No API requests or database changes were performed.")


if __name__ == "__main__":
    main()

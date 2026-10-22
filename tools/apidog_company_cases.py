"""Company, policy and agreement scenarios for the Apidog/Postman export.

This module only describes requests. It never calls the running application.
"""


def build_company_folders(case):
    profile = {
        "name": "Công ty Trek Apidog {{runId}}",
        "taxCode": "{{taxCode}}",
        "representativeName": "Nguyễn Minh An",
        "contactEmail": "{{customerEmail}}",
        "contactPhone": "0900000000",
        "address": "10 Đường Mẫu, TP.HCM",
        "description": "Hồ sơ thử nghiệm trên môi trường local.",
    }
    updated_profile = {
        **profile,
        "contactPhone": "0900000001",
        "address": "20 Đường Mẫu, TP.HCM",
        "description": "Đã cập nhật hồ sơ trước khi admin duyệt.",
    }
    second_profile = {
        **profile,
        "name": "Công ty thử luồng từ chối {{runId}}",
        "taxCode": "{{taxCode}}-R",
    }
    policy = {
        "code": "COOP-{{runId}}",
        "title": "Điều khoản hợp tác thử nghiệm {{runId}}",
        "companyId": "{{companyId}}",
    }
    version = {
        "content": "Công ty đồng ý cung cấp dịch vụ trekking theo điều khoản hợp tác thử nghiệm này.",
        "effectiveFrom": "{{effectiveFrom}}",
        "platformFeePercent": 10.00,
        "payoutWaitHours": 48,
        "payoutTermsSnapshot": "Thanh toán sau 48 giờ kể từ khi hoàn tất và đối soát dịch vụ.",
    }
    approve = {"decision": "approve", "reason": "Đã kiểm tra hồ sơ và giấy phép thử nghiệm hợp lệ."}
    binary_checks = [
        "pm.expect(pm.response.headers.get('Content-Type')).to.include('application/octet-stream');",
        "pm.expect(pm.response.headers.get('Content-Disposition')).to.include('attachment');",
        "pm.expect(pm.response.headers.get('Cache-Control')).to.include('no-store');",
    ]

    company_items = [
        case(
            "CO-01", "Tạo công ty pending và lưu companyId", "POST", "/api/v1/companies", 201,
            actor="customer", body=profile, saves={"companyId": "id"},
            checks=["pm.expect(data.status).to.eql('pending');", "pm.expect(data.licenseAvailable).to.eql(false);"],
            description="Đăng nhập customer đã xác minh email. Mỗi lượt thử dùng runId/taxCode mới. Tạo owner và role company_manager cho chính customer; đây là công ty chính để ký thỏa thuận.",
        ),
        case(
            "CO-02", "Tạo trùng mã số thuế bị chặn", "POST", "/api/v1/companies", 409,
            actor="customer", body=profile, error="COMPANY_TAX_CODE_EXISTS",
            description="Chạy sau CO-01. Không thay taxCode; không tạo thêm công ty khi trả đúng 409.",
        ),
        case(
            "CO-03", "Hồ sơ không hợp lệ trả fieldErrors", "POST", "/api/v1/companies", 400,
            actor="customer", body={**profile, "name": "", "taxCode": "INVALID CODE", "contactEmail": "sai-email"},
            error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('name');", "pm.expect(data.fieldErrors).to.have.property('taxCode');", "pm.expect(data.fieldErrors).to.have.property('contactEmail');"],
            description="Tên trống, mã thuế chứa khoảng trắng và email sai định dạng; request phải bị chặn trước khi ghi database.",
        ),
        case(
            "CO-04", "Danh sách công ty mình quản lý", "GET", "/api/v1/companies?page=0&size=20", 200,
            actor="customer", required=["companyId"],
            checks=["pm.expect(data.items.some(x => String(x.id) === String(pm.environment.get('companyId')))).to.eql(true);", "pm.expect(data.page).to.eql(0);"],
            description="Chạy sau CO-01; customer hiện là owner của companyId.",
        ),
        case(
            "CO-05", "Phân trang công ty size=101 bị chặn", "GET", "/api/v1/companies?page=0&size=101", 400,
            actor="customer", error="INVALID_REQUEST",
            description="size hợp lệ 1–100; page hợp lệ từ 0.",
        ),
        case(
            "CO-06", "Đọc hồ sơ công ty mình", "GET", "/api/v1/companies/{{companyId}}", 200,
            actor="customer", checks=["pm.expect(String(data.id)).to.eql(String(pm.environment.get('companyId')));"],
            description="Chạy sau CO-01. Endpoint quản lý yêu cầu membership owner/manager active.",
        ),
        case(
            "CO-07", "Ẩn công ty ngoài phạm vi bằng 404", "GET", "/api/v1/companies/-1", 404,
            actor="customer", error="RESOURCE_NOT_FOUND",
            description="ID -1 không có membership; API không tiết lộ hồ sơ ngoài phạm vi.",
        ),
        case(
            "CO-08", "Admin không có membership không đọc bằng URL thành viên", "GET", "/api/v1/companies/{{companyId}}", 404,
            actor="admin", error="RESOURCE_NOT_FOUND",
            description="Admin phải là tài khoản riêng, không có membership trong công ty chính. Admin đọc hồ sơ qua /admin/companies/{id} ở folder 05.",
        ),
        case(
            "CO-09", "Cập nhật đầy đủ hồ sơ trước khi duyệt", "PUT", "/api/v1/companies/{{companyId}}", 200,
            actor="customer", body=updated_profile,
            checks=["pm.expect(data.contactPhone).to.eql('0900000001');", "pm.expect(data.status).to.eql('pending');"],
            description="PUT gửi đủ trường bắt buộc. Thực hiện trong folder 04 trước khi approve; sửa công ty đã approved sẽ đưa về pending và làm gián đoạn bước ký.",
        ),
        case(
            "CO-10", "Cập nhật thiếu tên bị chặn", "PUT", "/api/v1/companies/{{companyId}}", 400,
            actor="customer", body={**updated_profile, "name": ""}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('name');"],
            description="Request lỗi validation không thay đổi hồ sơ đã lưu.",
        ),
        case(
            "CO-11", "Không resubmit công ty đang pending", "POST", "/api/v1/companies/{{companyId}}/resubmit", 409,
            actor="customer", error="COMPANY_STATE_CONFLICT",
            description="Resubmit chỉ dành cho rejected. Luồng resubmit thành công được tách sang công ty thứ hai ở folder 11.",
        ),
        case(
            "CO-12", "Chưa có giấy phép thì download trả 404", "GET", "/api/v1/companies/{{companyId}}/license", 404,
            actor="customer", error="COMPANY_LICENSE_NOT_FOUND",
            description="Chạy trước CO-15 upload. Nếu đã upload, tạo runId và công ty mới để chạy lại theo thứ tự.",
        ),
        case(
            "CO-13", "Admin không được approve khi thiếu giấy phép", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 409,
            actor="admin", body=approve, error="COMPANY_LICENSE_REQUIRED",
            description="Chạy trước upload; công ty vẫn pending. Nếu admin account có mustChangePassword, hoàn tất đổi mật khẩu trước.",
        ),
        case(
            "AGR-01", "Không xin OTP ký khi công ty chưa được duyệt", "POST", "/api/v1/companies/{{companyId}}/agreements/otp", 409,
            actor="customer", body={"policyVersionId": 1}, error="COMPANY_NOT_APPROVED",
            description="Chạy khi công ty còn pending. Service kiểm tra trạng thái công ty trước khi tìm policy version; không gửi email ở case này.",
        ),
        case(
            "CO-15", "MANUAL — chọn file rồi upload giấy phép", "POST", "/api/v1/companies/{{companyId}}/license", 200,
            actor="customer", multipart=True,
            checks=["pm.expect(data.licenseAvailable).to.eql(true);", "pm.expect(data.status).to.eql('pending');", "pm.expect(data.licenseUrl).to.include('/license');"],
            description="DỪNG và chọn file tại Body → form-data → file trong Apidog. Dùng PDF/PNG/JPEG thật, không rỗng, tối đa 5 MiB; không đổi tên file văn bản thành .pdf. File phải có nội dung đúng MIME. Không chạy folder 05 trước khi upload thành công.",
        ),
        case(
            "CO-16", "Download giấy phép bằng token owner", "GET", "/api/v1/companies/{{companyId}}/license", 200,
            actor="customer", checks=binary_checks,
            description="Chạy sau CO-15. Response là byte file với Content-Disposition attachment, không phải JSON. Dùng Download/Save response của Apidog để xem file.",
        ),
    ]

    approval_items = [
        case(
            "CO-17", "Customer không được xem danh sách duyệt admin", "GET", "/api/v1/admin/companies?page=0&size=20", 403,
            actor="customer", error="FORBIDDEN", description="Customer không được đồng thời có role admin khi chạy case phân quyền này.",
        ),
        case(
            "CO-18", "Admin xem danh sách công ty pending", "GET", "/api/v1/admin/companies?status=pending&page=0&size=20", 200,
            actor="admin", required=["companyId"],
            checks=["pm.expect(data.items.some(x => String(x.id) === String(pm.environment.get('companyId')))).to.eql(true);"],
            description="Chạy sau folder 04, trước approve. Công ty chính vẫn pending và đã có giấy phép.",
        ),
        case(
            "CO-19", "Admin lọc trạng thái sai bị chặn", "GET", "/api/v1/admin/companies?status=UNKNOWN&page=0&size=20", 400,
            actor="admin", error="INVALID_REQUEST", description="Các giá trị hợp lệ phân biệt hoa thường: pending, approved, rejected, suspended.",
        ),
        case(
            "CO-20", "Admin phân trang âm bị chặn", "GET", "/api/v1/admin/companies?page=-1&size=20", 400,
            actor="admin", error="INVALID_REQUEST", description="page phải từ 0; size từ 1 đến 100.",
        ),
        case(
            "CO-21", "Admin đọc chi tiết hồ sơ", "GET", "/api/v1/admin/companies/{{companyId}}", 200,
            actor="admin", checks=["pm.expect(data.licenseAvailable).to.eql(true);"],
            description="Dùng URL admin để kiểm tra hồ sơ và giấy phép trước quyết định.",
        ),
        case(
            "CO-22", "Admin download giấy phép", "GET", "/api/v1/admin/companies/{{companyId}}/license", 200,
            actor="admin", checks=binary_checks, description="Response binary; xem file thực tế trước khi duyệt.",
        ),
        case(
            "CO-23", "Admin đọc công ty không tồn tại", "GET", "/api/v1/admin/companies/-1", 404,
            actor="admin", error="COMPANY_NOT_FOUND", description="Admin có quyền review nhưng ID -1 không tồn tại.",
        ),
        case(
            "CO-24", "Customer không được tự duyệt", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 403,
            actor="customer", body=approve, error="FORBIDDEN", description="Customer owner không có permission COMPANY_REVIEW.",
        ),
        case(
            "CO-25", "Quyết định thiếu lý do bị chặn", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 400,
            actor="admin", body={"decision": "approve", "reason": ""}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('reason');"], description="Mọi quyết định đều cần lý do; công ty vẫn pending.",
        ),
        case(
            "CO-26", "Admin duyệt công ty thành approved", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 200,
            actor="admin", body=approve,
            checks=["pm.expect(data.status).to.eql('approved');", "pm.expect(data.approvedBy).to.be.a('number');", "pm.expect(data.approvedAt).to.be.a('string');"],
            description="Giấy phép ở CO-15 phải đã upload thành công. Sau case này không sửa hồ sơ/upload lại trước khi ký; các thao tác đó đưa approved về pending.",
        ),
        case(
            "CO-27", "Không duyệt lặp công ty đã approved", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 409,
            actor="admin", body=approve, error="COMPANY_STATE_CONFLICT", description="Approve chỉ chấp nhận pending. Công ty vẫn approved để tiếp tục ký.",
        ),
        case(
            "POL-01", "Customer không được tạo chính sách", "POST", "/api/v1/admin/policies", 403,
            actor="customer", body=policy, error="FORBIDDEN", description="Cần admin và permission POLICY_MANAGE.",
        ),
        case(
            "POL-02", "Admin tạo policy riêng cho công ty và lưu policyId", "POST", "/api/v1/admin/policies", 201,
            actor="admin", body=policy, saves={"policyId": "id"},
            checks=["pm.expect(data.kind).to.eql('cooperation');", "pm.expect(data.active).to.eql(true);", "pm.expect(String(data.companyId)).to.eql(String(pm.environment.get('companyId')));"],
            description="code COOP-runId phải mới cho mỗi lượt chạy. Policy tạo mới chưa có nội dung để ký; cần publish ở POL-07.",
        ),
        case(
            "POL-03", "Mã policy trùng bị chặn", "POST", "/api/v1/admin/policies", 409,
            actor="admin", body=policy, error="POLICY_CODE_EXISTS", description="Chạy sau POL-02 với cùng code.",
        ),
        case(
            "POL-04", "Phí nền tảng trên 100 bị chặn", "POST", "/api/v1/admin/policies/{{policyId}}/versions", 400,
            actor="admin", body={**version, "platformFeePercent": 100.01}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('platformFeePercent');"], description="Phí hợp lệ từ 0 đến 100, tối đa hai chữ số thập phân. Request lỗi không xuất bản version.",
        ),
        case(
            "POL-05", "Phí có ba chữ số thập phân bị chặn", "POST", "/api/v1/admin/policies/{{policyId}}/versions", 400,
            actor="admin", body={**version, "platformFeePercent": 10.001}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('platformFeePercent');"], description="Kiểm tra giới hạn scale của phí; không làm đổi version hiện tại.",
        ),
        case(
            "POL-06", "Thời gian thanh toán âm bị chặn", "POST", "/api/v1/admin/policies/{{policyId}}/versions", 400,
            actor="admin", body={**version, "payoutWaitHours": -1}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('payoutWaitHours');"], description="payoutWaitHours phải từ 0; payoutTermsSnapshot bắt buộc không trống.",
        ),
        case(
            "POL-07", "Publish version có hiệu lực và lưu policyVersionId", "POST", "/api/v1/admin/policies/{{policyId}}/versions", 201,
            actor="admin", body=version, saves={"policyVersionId": "id"},
            checks=["pm.expect(data.versionNumber).to.eql(1);", "pm.expect(Number(data.platformFeePercent)).to.eql(10);", "pm.expect(data.payoutWaitHours).to.eql(48);"],
            description="effectiveFrom do bước khởi tạo đặt theo thời điểm chạy, phải không nằm trong tương lai. Version mới tự approved; lưu chính xác id response, không chọn phần tử đầu của danh sách policy.",
        ),
    ]

    agreement_items = [
        case(
            "AGR-02", "Đọc chính sách đủ điều kiện ký", "GET", "/api/v1/companies/{{companyId}}/agreement-policies", 200,
            actor="customer", required=["policyVersionId"],
            checks=["pm.expect(data).to.be.an('array');", "pm.expect(data.some(x => String(x.id) === String(pm.environment.get('policyVersionId')))).to.eql(true);"],
            description="Công ty approved, POL-07 đã xuất bản version có hiệu lực. Xác nhận policyVersionId đã lưu xuất hiện; đọc nội dung/phí/điều khoản trước ký.",
        ),
        case(
            "AGR-03", "ID policy bằng 0 bị chặn trước khi gửi OTP", "POST", "/api/v1/companies/{{companyId}}/agreements/otp", 400,
            actor="customer", body={"policyVersionId": 0}, error="VALIDATION_ERROR", description="policyVersionId phải > 0; case này không gửi email.",
        ),
        case(
            "AGR-04", "OTP sai định dạng bị chặn trước khi tiêu thụ", "POST", "/api/v1/companies/{{companyId}}/agreements", 400,
            actor="customer", body={"policyVersionId": "{{policyVersionId}}", "otp": "abc"}, error="VALIDATION_ERROR",
            checks=["pm.expect(data.fieldErrors).to.have.property('otp');"], description="OTP là chuỗi đúng sáu chữ số. Không dùng một mã sáu số ngẫu nhiên để tránh tiêu thụ số lần thử challenge thật.",
        ),
        case(
            "AGR-05", "Xem lịch sử thỏa thuận trước khi ký", "GET", "/api/v1/companies/{{companyId}}/agreements", 200,
            actor="customer", required=["policyVersionId"],
            checks=["pm.expect(data).to.be.an('array');", "pm.expect(data.some(x => String(x.policyVersionId) === String(pm.environment.get('policyVersionId')))).to.eql(false);"],
            description="Công ty mới của lượt chạy chưa chấp nhận version này; nếu đã ký thì dùng runId mới.",
        ),
        case(
            "AGR-06", "Xin OTP ký — DỪNG để đọc email và nhập agreementOtp", "POST", "/api/v1/companies/{{companyId}}/agreements/otp", 202,
            actor="customer", body={"policyVersionId": "{{policyVersionId}}"},
            checks=["pm.expect(data.expiresIn).to.eql(300);", "pm.expect(data.retryAfter).to.eql(60);", "pm.expect(data).not.to.have.property('otp');"],
            description="GỬI EMAIL THẬT đến customerEmail của tài khoản owner, không phải contactEmail công ty. SMTP phải hoạt động. Sau 202, dừng runner, lấy OTP mới từ email và điền biến agreementOtp dưới dạng chuỗi sáu số; tiếp tục AGR-07 trong 300 giây. Gửi lại cần chờ ít nhất 60 giây; tối đa 5 lần/giờ. Không chỉnh hồ sơ, membership, policy hay đăng xuất giữa xin OTP và ký.",
        ),
        case(
            "AGR-07", "MANUAL — nhập OTP rồi ký và lưu agreementId", "POST", "/api/v1/companies/{{companyId}}/agreements", 201,
            actor="customer", body={"policyVersionId": "{{policyVersionId}}", "otp": "{{agreementOtp}}"},
            required=["agreementOtp"], saves={"agreementId": "id"},
            checks=["pm.expect(String(data.companyId)).to.eql(String(pm.environment.get('companyId')));", "pm.expect(String(data.policyVersionId)).to.eql(String(pm.environment.get('policyVersionId')));", "pm.expect(Number(data.platformFeePercent)).to.eql(10);", "pm.expect(data.payoutWaitHours).to.eql(48);", "pm.expect(data.emailVerifiedAt).to.be.a('string');"],
            description="Chỉ Send sau khi đặt agreementOtp bằng mã mới từ AGR-06. Mã dùng một lần; giữ số 0 ở đầu. Nếu nhận INVALID_AGREEMENT_OTP, kiểm tra hết hạn/bối cảnh, xem lịch sử receipt trước khi xin lại mã. Không tự retry nhận lỗi như thành công.",
        ),
        case(
            "AGR-08", "Xem lịch sử có receipt vừa ký", "GET", "/api/v1/companies/{{companyId}}/agreements", 200,
            actor="customer", required=["agreementId"],
            checks=["pm.expect(data.some(x => String(x.id) === String(pm.environment.get('agreementId')))).to.eql(true);"], description="Chạy sau AGR-07 thành công; đây là bằng chứng đã ký.",
        ),
        case(
            "AGR-09", "Đọc chi tiết receipt", "GET", "/api/v1/companies/{{companyId}}/agreements/{{agreementId}}", 200,
            actor="customer",
            checks=["pm.expect(String(data.id)).to.eql(String(pm.environment.get('agreementId')));", "pm.expect(data.policyContent).to.be.a('string');", "pm.expect(data.payoutTermsSnapshot).to.be.a('string');"],
            description="Kiểm tra bằng chứng và bản chụp điều khoản tài chính; receipt không có API sửa/xóa.",
        ),
        case(
            "AGR-10", "Receipt không tồn tại bị ẩn bằng 404", "GET", "/api/v1/companies/{{companyId}}/agreements/-1", 404,
            actor="customer", error="RESOURCE_NOT_FOUND", description="Receipt phải tồn tại và thuộc companyId.",
        ),
        case(
            "AGR-11", "Không ký lặp cùng version", "POST", "/api/v1/companies/{{companyId}}/agreements", 409,
            actor="customer", body={"policyVersionId": "{{policyVersionId}}", "otp": "{{agreementOtp}}"},
            required=["agreementId", "agreementOtp"], error="AGREEMENT_ALREADY_ACCEPTED",
            description="Chạy sau AGR-07 thành công. Context kiểm tra receipt đã tồn tại trước khi tiêu thụ OTP; chỉ một receipt cho mỗi company/version.",
        ),
    ]

    optional_items = [
        case(
            "CO-28", "Tạo công ty thứ hai để thử reject/resubmit", "POST", "/api/v1/companies", 201,
            actor="customer", body=second_profile, saves={"rejectedCompanyId": "id"},
            checks=["pm.expect(data.status).to.eql('pending');"],
            description="TÙY CHỌN, chỉ chạy sau ký và các luồng dùng công ty chính. Dùng taxCode-R riêng; giữ nguyên companyId chính. Chỉ chạy tạo một lần mỗi runId.",
        ),
        case(
            "CO-29", "Admin từ chối công ty thứ hai", "POST", "/api/v1/admin/companies/{{rejectedCompanyId}}/decision", 200,
            actor="admin", body={"decision": "reject", "reason": "Hồ sơ thử nghiệm cần bổ sung giấy phép trước khi gửi lại."},
            checks=["pm.expect(data.status).to.eql('rejected');"],
            description="Chạy sau CO-28. Chỉ reject công ty thứ hai đang pending, không reject công ty chính đã approved.",
        ),
        case(
            "CO-30", "Customer gửi lại hồ sơ rejected", "POST", "/api/v1/companies/{{rejectedCompanyId}}/resubmit", 200,
            actor="customer",
            checks=["pm.expect(data.status).to.eql('pending');", "pm.expect(data.approvedAt).to.eql(null);", "pm.expect(data.approvedBy).to.eql(null);", "pm.expect(data.decisionReason).to.eql(null);"],
            description="Chạy sau CO-29; rejected → pending và xóa quyết định cũ. API resubmit không bắt buộc giấy phép, nhưng approve sau đó vẫn yêu cầu upload.",
        ),
        case(
            "CO-31", "Không gửi lại hai lần khi đã pending", "POST", "/api/v1/companies/{{rejectedCompanyId}}/resubmit", 409,
            actor="customer", error="COMPANY_STATE_CONFLICT", description="Chạy sau CO-30; trạng thái không còn rejected.",
        ),
        case(
            "CO-32", "CUỐI CÙNG — admin đình chỉ công ty chính", "POST", "/api/v1/admin/companies/{{companyId}}/decision", 200,
            actor="admin", body={"decision": "suspend", "reason": "Thử nghiệm luồng đình chỉ sau khi hoàn tất ký và các case khác."},
            required=["agreementId"], checks=["pm.expect(data.status).to.eql('suspended');"],
            description="CHỈ chạy khi đã hoàn tất ký và tất cả case cần công ty approved. Hành động này đổi công ty chính thành suspended; API hiện tại không có khôi phục trực tiếp. Dùng công ty mới cho lượt thử sau.",
        ),
        case(
            "CO-33", "Công ty suspended không được sửa hồ sơ", "PUT", "/api/v1/companies/{{companyId}}", 409,
            actor="customer", body=updated_profile, error="COMPANY_STATE_CONFLICT", description="Chạy sau CO-32. Không thay đổi hồ sơ đã lưu.",
        ),
        case(
            "AGR-12", "Vẫn đọc được receipt sau khi đình chỉ", "GET", "/api/v1/companies/{{companyId}}/agreements/{{agreementId}}", 200,
            actor="customer", checks=["pm.expect(String(data.id)).to.eql(String(pm.environment.get('agreementId')));"],
            description="Membership owner vẫn active; suspended không xóa bằng chứng đã ký.",
        ),
    ]

    return [
        {"name": "04 — Công ty trước duyệt (chọn file giấy phép thủ công)", "description": "Chạy sau đăng nhập customer/admin. Tạo hồ sơ → kiểm tra lỗi → cập nhật → chứng minh thiếu giấy phép → chọn file và upload. Giữ companyId để duyệt và ký. Không chạy lại folder này sau khi đã approved.", "item": company_items},
        {"name": "05 — Admin duyệt và xuất bản chính sách", "description": "Chỉ chạy sau upload thành công ở 04. Admin duyệt công ty, tạo policy riêng, publish bản có hiệu lực và lưu policyVersionId. Không chỉnh lại hồ sơ/giấy phép sau duyệt trước khi ký.", "item": approval_items},
        {"name": "06 — Thỏa thuận (DỪNG sau xin OTP, nhập agreementOtp)", "description": "Chạy sau 05. Gửi AGR-06 riêng, đọc email owner, nhập agreementOtp rồi gửi AGR-07 trong 5 phút. Không chạy cả folder tự động khi chưa có mã. Receipt là bằng chứng ký; mã OTP không có trong response API.", "item": agreement_items},
        {"name": "11 — TÙY CHỌN sau ký: reject, resubmit, suspend", "description": "Chạy cuối cùng sau mọi luồng cần công ty approved. Reject/resubmit dùng công ty thứ hai. Suspend đổi công ty chính thành suspended và hiện không có API khôi phục trực tiếp; receipt vẫn đọc được.", "item": optional_items},
    ]

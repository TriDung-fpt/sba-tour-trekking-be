"""Generate local manual API scenarios, Postman v2.1 JSON and an environment.

No HTTP requests, credentials, or database writes are performed by this generator.
Run from any directory: python tools/generate_apidog_collection.py
"""

import json
import re
import struct
import zlib
from pathlib import Path
from urllib.parse import parse_qsl

from apidog_company_cases import build_company_folders


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/api/testing"
INDEX = []


def script_event(kind, lines):
    return {"listen": kind, "script": {"type": "text/javascript", "exec": lines}}


def case(case_id, title, method, path, status, *, actor=None, body=None,
         error=None, saves=None, checks=None, required=None, description="",
         csrf=True, multipart=False, auth_result=None, refresh_actor=None):
    """One independently selectable request with prerequisites and assertions."""
    raw = json.dumps(body, ensure_ascii=False, indent=2) if body is not None else None
    if raw:
        # IDs are JSON numbers after substitution, not strings coerced by Jackson.
        raw = re.sub(r'"(\{\{\w+Id\}\})"', r'\1', raw)
    inputs = set(required or [])
    inputs.update(re.findall(r"\{\{(\w+)\}\}", path + (raw or "")))
    if actor:
        inputs.add(actor + "Token")
    if refresh_actor:
        inputs.add(refresh_actor + "RefreshToken")
    inputs -= {"runId", "effectiveFrom", "taxCode"}
    prerequisites = ", ".join("`" + name + "`" for name in sorted(inputs)) or "Không có"
    description = (
        description + "\n\nBiến cần có: " + prerequisites + ". "
        + "Kỳ vọng HTTP " + str(status) + (" / " + error if error else "")
        + ". Đọc TEST_SCENARIOS.md trước khi chạy; có bước OTP/file thủ công."
    ).strip()
    request = {
        "method": method,
        "header": [{"key": "Accept", "value": "application/json"}],
        "auth": {"type": "noauth"},
        "url": {
            "raw": "{{baseUrl}}" + path,
            "host": ["{{baseUrl}}"],
            "path": path.split("?")[0].lstrip("/").split("/"),
        },
        "description": description,
    }
    if "?" in path:
        request["url"]["query"] = [
            {"key": k, "value": v} for k, v in parse_qsl(path.split("?", 1)[1])
        ]
    if actor:
        request["auth"] = {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{" + actor + "Token}}", "type": "string"}],
        }
    if raw is not None:
        request["body"] = {"mode": "raw", "raw": raw, "options": {"raw": {"language": "json"}}}
        request["header"].append({"key": "Content-Type", "value": "application/json"})
    if multipart:
        request["body"] = {"mode": "formdata", "formdata": [{"key": "file", "type": "file", "src": []}]}

    pre = [
        "// Chỉ tạo dữ liệu test local; giữ nguyên runId cho tới khi bắt đầu lượt mới.",
        "if (!pm.environment.get('runId')) pm.environment.set('runId', String(Date.now()));",
        "if (!pm.environment.get('taxCode')) pm.environment.set('taxCode', 'QA-' + pm.environment.get('runId'));",
        "if (!pm.environment.get('effectiveFrom')) pm.environment.set('effectiveFrom', new Date(Date.now() - 60000).toISOString());",
        "var base = String(pm.environment.get('baseUrl') || '').replace(/\\/$/, '');",
        "if (!base) throw new Error('Chọn environment Trekking Local và điền baseUrl.');",
        "var needs = " + json.dumps(sorted(inputs)) + ";",
        "needs.forEach(function (key) {",
        "  var value = pm.environment.get(key);",
        "  if (value === undefined || value === null || String(value).trim() === '') throw new Error('Thiếu biến ' + key + ': hoàn thành bước trước hoặc nhập trong environment.');",
        "  if (/Otp$/.test(key) && !/^[0-9]{6}$/.test(String(value))) throw new Error(key + ' phải là OTP 6 chữ số từ email, giữ dạng chuỗi.');",
        "});",
    ]
    if refresh_actor:
        pre.append("var roleRefreshCookie = pm.environment.get('" + refresh_actor + "RefreshToken');")
    if path == "/api/v1/auth/logout" and refresh_actor == "customer":
        pre.append("pm.environment.set('loggedOutCustomerToken', pm.environment.get('customerToken'));")
    for output in (saves or {}):
        if output not in inputs:
            pre.append("pm.environment.set('" + output + "', ''); // Không giữ ID cũ khi request mới thất bại.")
    if auth_result:
        # A failed new login must not silently retain an old success token.
        pre += [f"pm.environment.set('{auth_result}Token', '');",
                f"pm.environment.set('{auth_result}RefreshToken', '');"]
    auth_post = method == "POST" and path.startswith("/api/v1/auth/")
    if auth_post and csrf:
        pre += [
            "// Gửi cookie tường minh cho test local Desktop, tách refresh cookie theo vai trò.",
            "pm.sendRequest({url: base + '/api/v1/auth/csrf', method: 'GET', header: {'Accept': 'application/json'}}, function (err, response) {",
            "  if (err || response.code !== 200) throw new Error('Không lấy được CSRF: kiểm tra Docker/baseUrl.');",
            "  var token = response.json();",
            "  if (!token.headerName || !token.token) throw new Error('CSRF response thiếu token/headerName.');",
            "  pm.environment.set('csrfToken', token.token);",
            "  pm.request.headers.upsert({key: token.headerName, value: token.token});",
            "  var cookie = 'XSRF-TOKEN=' + token.token;",
        ]
        if refresh_actor:
            pre.append("  cookie += '; trekking_refresh=' + roleRefreshCookie;")
        pre += ["  pm.request.headers.upsert({key: 'Cookie', value: cookie});", "});"]
    elif auth_post:
        pre += [
            "pm.request.headers.upsert({key: 'X-XSRF-TOKEN', value: 'deliberately-mismatched-csrf'});",
            "pm.request.headers.upsert({key: 'Cookie', value: 'XSRF-TOKEN=different-cookie-value'});",
        ]

    tests = [
        "pm.test(" + json.dumps(case_id + " | HTTP " + str(status), ensure_ascii=False) + ", function () {",
        "  pm.expect(pm.response.code).to.equal(" + str(status) + ");",
        "});",
        "var data = null; try { data = pm.response.json(); } catch (ignore) {}",
    ]
    if error:
        tests += [
            "pm.test('Mã lỗi nghiệp vụ và cấu trúc lỗi', function () {",
            "  pm.expect(data).to.be.an('object');",
            "  pm.expect(data.code).to.equal(" + json.dumps(error) + ");",
            "  pm.expect(data.message).to.be.a('string');",
            "  pm.expect(data.fieldErrors).to.be.an('object');",
            "});",
        ]
    if status == 204:
        tests += ["pm.test('Không có response body', function () { pm.expect(pm.response.text()).to.equal(''); });"]
    if checks:
        tests += ["pm.test('Kiểm tra dữ liệu nghiệp vụ', function () {"] + ["  " + line for line in checks] + ["});"]
    if saves:
        for target, field in saves.items():
            tests += [
                "if (pm.response.code === " + str(status) + " && data) {",
                "  var value = " + "data." + field + ";",
                "  pm.test('Response có " + field + " để lưu " + target + "', function () { pm.expect(value).to.not.equal(undefined); pm.expect(value).to.not.equal(null); });",
                "  if (value !== undefined && value !== null) pm.environment.set('" + target + "', String(value));",
                "}",
            ]
    if auth_result:
        tests += [
            "if (pm.response.code === 200 && data) {",
            "  pm.test('Access token, kiểu token và TTL', function () {",
            "    pm.expect(data.accessToken).to.be.a('string').and.not.empty;",
            "    pm.expect(data.tokenType).to.equal('Bearer');",
            "    pm.expect(data.expiresIn).to.be.above(0);",
            "  });",
            "  if (data.accessToken) pm.environment.set('" + auth_result + "Token', data.accessToken);",
            "  if (data.account && data.account.id) pm.environment.set('" + auth_result + "Id', String(data.account.id));",
            "  var setCookies = pm.response.headers.all().filter(function (h) { return h.key.toLowerCase() === 'set-cookie'; });",
            "  var match = null; setCookies.forEach(function (h) { var found = h.value.match(/(?:^|[,\\s])trekking_refresh=([^;\\s,]+)/); if (found) match = found; });",
            "  pm.test('Server đặt refresh cookie', function () { pm.expect(match).to.not.equal(null); });",
            "  if (match) pm.environment.set('" + auth_result + "RefreshToken', match[1]);",
            "}",
        ]
    if path == "/api/v1/auth/logout" and refresh_actor:
        tests += [f"if (pm.response.code === 204) {{ pm.environment.set('{refresh_actor}Token', ''); pm.environment.set('{refresh_actor}RefreshToken', ''); }}"]
    INDEX.append({"id": case_id, "title": title, "method": method, "path": path,
                  "status": status, "error": error, "inputs": sorted(inputs), "description": description})
    return {
        "name": case_id + " | " + title,
        "request": request,
        "protocolProfileBehavior": {"disableCookies": True},
        "event": [script_event("prerequest", pre), script_event("test", tests)],
        "response": [],
    }


def folder(name, items, description=""):
    return {"name": name, "description": description, "item": items}


def login(case_id, role, email=None, password=None, title=None):
    return case(case_id, title or "Đăng nhập " + role, "POST", "/api/v1/auth/login", 200,
                body={"email": "{{" + (email or role + "Email") + "}}", "password": "{{" + (password or role + "Password") + "}}"},
                auth_result=role,
                description="Lưu token/refresh cookie/ID vào environment của đúng vai trò. Không gửi bearer lên login.")


def build_folders():
    folders = [folder("00 | Kiểm tra backend", [
        case("SM-01", "Lấy CSRF token", "GET", "/api/v1/auth/csrf", 200,
             saves={"csrfToken": "token"}, checks=["pm.expect(data.headerName).to.equal('X-XSRF-TOKEN');"]),
        case("SM-02", "Chưa đăng nhập không được xem me", "GET", "/api/v1/auth/me", 401, error="UNAUTHORIZED"),
    ]), folder("01 | Kiểm tra dữ liệu sai và CSRF", [
        case("NEG-01", "Auth POST sai CSRF", "POST", "/api/v1/auth/login", 403,
             body={"email": "nobody@example.invalid", "password": "InvalidPass123!"}, error="INVALID_CSRF", csrf=False),
        case("NEG-02", "Email đăng ký sai định dạng", "POST", "/api/v1/auth/register", 400,
             body={"email": "khong-phai-email", "password": "DemoPassword123!", "fullName": "QA Test"}, error="VALIDATION_ERROR"),
        case("NEG-03", "Mật khẩu đăng ký quá ngắn", "POST", "/api/v1/auth/register", 400,
             body={"email": "qa-{{runId}}@example.invalid", "password": "short", "fullName": "QA Test"}, error="VALIDATION_ERROR"),
        case("NEG-04", "Sai thông tin đăng nhập", "POST", "/api/v1/auth/login", 401,
             body={"email": "missing-{{runId}}@example.invalid", "password": "DefinitelyWrong123!"}, error="INVALID_CREDENTIALS"),
        case("NEG-05", "OTP xác minh sai định dạng", "POST", "/api/v1/auth/verify-email", 400,
             body={"email": "qa-{{runId}}@example.invalid", "otp": "abc"}, error="VALIDATION_ERROR"),
    ])]
    for role, number, otp in [("customer", "02", "verificationOtp"), ("other", "03", "otherVerificationOtp")]:
        prefix = "CUS" if role == "customer" else "OTHER"
        items = [
            case(prefix + "-01", "Đăng ký " + role, "POST", "/api/v1/auth/register", 201,
                 body={"email": "{{" + role + "Email}}", "password": "{{" + role + "Password}}", "fullName": "QA " + role, "phone": "0900000000"},
                 description="Email mới nhận được mail thật. Dừng sau request để lấy OTP 6 chữ số trong email."),
            case(prefix + "-02", "Xác minh email — nhập OTP trước", "POST", "/api/v1/auth/verify-email", 200,
                 body={"email": "{{" + role + "Email}}", "otp": "{{" + otp + "}}"},
                 description="DỪNG: điền " + otp + " bằng OTP mới nhất, trong 5 phút."),
            login(prefix + "-03", role),
            case(prefix + "-04", "Thông tin " + role, "GET", "/api/v1/auth/me", 200, actor=role,
                 checks=["pm.expect(String(data.id)).to.equal(pm.environment.get('" + role + "Id'));", "pm.expect(data.roles).to.include('customer');"]),
        ]
        if role == "customer":
            items += [
                case("CUS-05", "Xem quyền hiện tại", "GET", "/api/v1/accounts/me/authorization", 200, actor=role,
                     checks=["pm.expect(data.roles).to.include('customer');", "pm.expect(data.companies).to.be.an('array');"]),
                case("CUS-06", "Làm mới phiên customer", "POST", "/api/v1/auth/refresh", 200,
                     refresh_actor=role, auth_result=role),
                case("CUS-07", "Customer không được quản trị tài khoản", "GET", "/api/v1/admin/accounts/{{customerId}}", 403,
                     actor=role, error="FORBIDDEN"),
                case("CUS-08", "Đăng ký trùng email", "POST", "/api/v1/auth/register", 409,
                     body={"email": "{{customerEmail}}", "password": "{{customerPassword}}", "fullName": "QA Duplicate"}, error="EMAIL_ALREADY_REGISTERED"),
            ]
        folders.append(folder(number + " | " + role + " đăng ký → DỪNG OTP → đăng nhập", items))

    # Build company definitions via the same helper; insert admin login before approval.
    company_folders = build_company_folders(case)
    for company_folder in company_folders:
        if company_folder["name"].startswith("04"):
            company_folder["item"].insert(0, login("ADM-00", "admin"))
            company_folder["item"].insert(2, case(
                "SCOPE-01", "Customer khác không xem được công ty của owner", "GET",
                "/api/v1/companies/{{companyId}}", 404, actor="other", error="RESOURCE_NOT_FOUND",
                description="other là customer thứ hai, không có membership tại companyId vừa tạo."
            ))
        if company_folder["name"].startswith("05"):
            company_folder["item"].insert(0, login("ADM-01", "admin"))
    folders.extend(company_folders)

    folders.append(folder("07 | Mời kế toán → DỪNG OTP → cấp quyền", [
        case("INV-01", "Admin mời kế toán", "POST", "/api/v1/admin/staff/invitations", 201, actor="admin",
             body={"email": "{{accountantEmail}}", "fullName": "QA Accountant", "phone": "0900000301", "role": "accountant"},
             saves={"accountantId": "accountId"}, checks=["pm.expect(data.deliveryStatus).to.equal('sent');", "pm.expect(data.activationRequired).to.equal(true);"],
             description="Dừng để lấy OTP email. Nếu deliveryStatus=retry_required, sửa SMTP rồi dùng resend sau cooldown."),
        case("INV-02", "Gửi lại lời mời kế toán — đợi 60 giây", "POST", "/api/v1/admin/staff/invitations/{{accountantId}}/resend", 202,
             actor="admin", checks=["pm.expect(data.deliveryStatus).to.equal('sent');"],
             description="Chạy TRƯỚC kích hoạt, sau invite ít nhất 60 giây. Mã mới thay mã cũ, cập nhật activationOtp từ mail mới nhất."),
        case("FIN-01", "Không cấp quyền cho kế toán chưa kích hoạt", "PUT", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 409,
             actor="admin", body={"permissions": ["FINANCE_READ"], "reason": "QA pending invitation"}, error="FINANCIAL_ACCOUNT_INELIGIBLE"),
        case("INV-03", "Kích hoạt kế toán — nhập OTP trước", "POST", "/api/v1/auth/activate-account", 200,
             body={"email": "{{accountantEmail}}", "otp": "{{activationOtp}}", "newPassword": "{{accountantPassword}}"}),
        login("INV-04", "accountant"),
        case("FIN-02", "Admin xem tài khoản kế toán", "GET", "/api/v1/admin/accounts/{{accountantId}}", 200, actor="admin",
             checks=["pm.expect(data.status).to.equal('active');", "pm.expect(data.mustChangePassword).to.equal(false);", "pm.expect(data.roles).to.include('accountant');"]),
        case("FIN-03", "Kế toán mới chưa có financial grant", "GET", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 200,
             actor="admin", checks=["pm.expect(data.permissions).to.eql([]);"]),
        case("FIN-04", "Admin cấp quyền đọc và chuẩn bị quyết toán", "PUT", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 200,
             actor="admin", body={"permissions": ["FINANCE_READ", "SETTLEMENT_PREPARE"], "reason": "QA test explicit financial grants"},
             checks=["pm.expect(data.permissions).to.eql(['FINANCE_READ', 'SETTLEMENT_PREPARE']);"]),
        case("FIN-05", "Kế toán thấy quyền được cấp", "GET", "/api/v1/accounts/me/authorization", 200,
             actor="accountant", checks=["pm.expect(data.permissions).to.include('FINANCE_READ');", "pm.expect(data.permissions).to.include('SETTLEMENT_PREPARE');"]),
        case("FIN-06", "Không gộp quyền chuẩn bị và duyệt quyết toán", "PUT", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 400,
             actor="admin", body={"permissions": ["SETTLEMENT_PREPARE", "SETTLEMENT_APPROVE"], "reason": "QA conflicting duties"}, error="CONFLICTING_FINANCIAL_DUTIES"),
        case("FIN-07", "Không gộp duyệt hoàn và ghi hoàn tiền", "PUT", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 400,
             actor="admin", body={"permissions": ["REFUND_APPROVE", "REFUND_RECORD"], "reason": "QA refund conflict"}, error="CONFLICTING_FINANCIAL_DUTIES"),
        case("FIN-08", "Request lỗi không thay grant đã lưu", "GET", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 200,
             actor="admin", checks=["pm.expect(data.permissions).to.eql(['FINANCE_READ', 'SETTLEMENT_PREPARE']);"]),
        case("FIN-09", "Kế toán không được tự quản trị grant", "GET", "/api/v1/admin/accounts/{{accountantId}}/financial-permissions", 403,
             actor="accountant", error="FORBIDDEN"),
    ]))
    folders.append(folder("08 | Mời guide → DỪNG OTP → đăng nhập", [
        case("GUI-01", "Owner mời hướng dẫn viên", "POST", "/api/v1/companies/{{companyId}}/guides/invitations", 201, actor="customer",
             body={"email": "{{guideEmail}}", "fullName": "QA Guide", "phone": "0900000401", "bio": "Guide test local"},
             saves={"guideId": "accountId"}, checks=["pm.expect(data.deliveryStatus).to.equal('sent');"],
             description="companyId phải approved. guideId ở environment là account ID, không phải ID hồ sơ guide."),
        case("GUI-02", "Gửi lại lời mời guide — đợi 60 giây", "POST", "/api/v1/companies/{{companyId}}/guides/invitations/{{guideId}}/resend", 202,
             actor="customer", checks=["pm.expect(data.deliveryStatus).to.equal('sent');"],
             description="Chạy sau invite ít nhất 60 giây, TRƯỚC activation. Nhập guideActivationOtp từ email mới nhất."),
        case("GUI-03", "Kích hoạt guide — nhập OTP trước", "POST", "/api/v1/auth/activate-account", 200,
             body={"email": "{{guideEmail}}", "otp": "{{guideActivationOtp}}", "newPassword": "{{guidePassword}}"}),
        login("GUI-04", "guide"),
        case("GUI-05", "Guide không phải người quản lý công ty", "GET", "/api/v1/companies/{{companyId}}", 404,
             actor="guide", error="RESOURCE_NOT_FOUND"),
    ]))
    folders.append(folder("09 | TÙY CHỌN Google, gửi lại mã và reset password", [
        case("OPT-01", "Gửi lại mã cho email đã xác minh vẫn trả chung", "POST", "/api/v1/auth/resend-verification", 202,
             body={"email": "{{customerEmail}}"}, checks=["pm.expect(data.message).to.equal('If the account is eligible, a verification code will be sent');"]),
        case("OPT-02", "Google ID token sai", "POST", "/api/v1/auth/google", 401,
             body={"idToken": "not-a-google-token"}, error="INVALID_GOOGLE_TOKEN",
             description="Backend cần cấu hình GOOGLE_CLIENT_IDS trước; nếu chưa cấu hình, đánh dấu blocked thay vì kết luận lỗi contract."),
        case("OPT-03", "Google login thật", "POST", "/api/v1/auth/google", 200,
             body={"idToken": "{{googleIdToken}}"}, auth_result="google",
             description="TÙY CHỌN: ID token Google thật chưa hết hạn, audience đã cấu hình. Có thể tạo/link account theo luật backend."),
        case("OPT-04", "Yêu cầu OTP đặt lại mật khẩu customer", "POST", "/api/v1/auth/forgot-password", 202,
             body={"email": "{{customerEmail}}"}, description="DỪNG: đọc email mới nhất và nhập resetOtp. Không gọi liên tục; cooldown 60 giây."),
        case("OPT-05", "Đặt lại mật khẩu — nhập resetOtp trước", "POST", "/api/v1/auth/reset-password", 200,
             body={"email": "{{customerEmail}}", "otp": "{{resetOtp}}", "newPassword": "{{newCustomerPassword}}"},
             checks=["pm.expect(data.message).to.equal('Password reset; please log in again');"],
             description="Đổi mật khẩu thật của account test và thu hồi tất cả phiên customer; chạy OPT-06 sau đó."),
        login("OPT-06", "customer", password="newCustomerPassword", title="Đăng nhập bằng mật khẩu mới"),
    ]))
    folders.append(folder("10 | CUỐI LUỒNG — quản trị tài khoản other", [
        case("ADM-02", "Thay toàn bộ role tài khoản other", "PUT", "/api/v1/admin/accounts/{{otherId}}/roles", 200, actor="admin",
             body={"roles": ["customer", "support_staff"], "reason": "QA test role replacement"},
             checks=["pm.expect(data.roles).to.eql(['customer', 'support_staff']);"]),
        case("ADM-03", "Không được cấp role admin", "PUT", "/api/v1/admin/accounts/{{otherId}}/roles", 400, actor="admin",
             body={"roles": ["admin"], "reason": "QA admin escalation denied"}, error="INVALID_ROLES"),
        case("ADM-04", "Khóa tài khoản other", "POST", "/api/v1/admin/accounts/{{otherId}}/block", 200, actor="admin",
             body={"reason": "QA test blocking"}, checks=["pm.expect(data.status).to.equal('blocked');"]),
        case("ADM-05", "Token cũ bị thu hồi sau khóa", "GET", "/api/v1/auth/me", 401, actor="other", error="UNAUTHORIZED"),
        case("ADM-06", "Mở khóa tài khoản other", "POST", "/api/v1/admin/accounts/{{otherId}}/unblock", 200, actor="admin",
             body={"reason": "QA test unblock"}, checks=["pm.expect(data.status).to.equal('active');"]),
        case("ADM-07", "Token cũ không hồi sinh sau mở khóa", "GET", "/api/v1/auth/me", 401, actor="other", error="UNAUTHORIZED"),
        login("ADM-08", "other", title="Other đăng nhập lại sau mở khóa"),
    ]))
    folders.append(folder("12 | Kết thúc phiên customer", [
        case("OUT-01", "Đăng xuất customer", "POST", "/api/v1/auth/logout", 204,
             refresh_actor="customer", required=["customerToken"],
             description="Chạy cuối. Giữ token cũ trong loggedOutCustomerToken để kiểm tra thu hồi, xóa customerToken/RefreshToken sau thành công."),
        case("OUT-02", "Token của phiên vừa logout bị thu hồi", "GET", "/api/v1/auth/me", 401,
             actor="loggedOutCustomer", error="UNAUTHORIZED"),
    ]))
    return sorted(folders, key=lambda group: group["name"])


def main():
    INDEX.clear()
    folders = build_folders()
    values = {
        "baseUrl": "http://localhost:8080", "runId": "", "taxCode": "", "effectiveFrom": "",
        "customerEmail": "", "customerPassword": "DemoPassword123!",
        "otherEmail": "", "otherPassword": "DemoOther123!",
        "adminEmail": "", "adminPassword": "",
        "accountantEmail": "", "accountantPassword": "DemoFinance123!",
        "guideEmail": "", "guidePassword": "DemoGuide123!", "newCustomerPassword": "DemoReset123!",
        "verificationOtp": "", "otherVerificationOtp": "", "activationOtp": "",
        "guideActivationOtp": "", "agreementOtp": "", "resetOtp": "", "googleIdToken": "",
    }
    for role in ["customer", "other", "admin", "accountant", "guide", "google"]:
        for suffix in ["Id", "Token", "RefreshToken"]:
            values[role + suffix] = ""
    for field in ["companyId", "rejectedCompanyId", "policyId", "policyVersionId", "agreementId", "csrfToken"]:
        values[field] = ""
    # Agent definitions may introduce other output IDs, never credentials.
    for entry in INDEX:
        for key in entry["inputs"]:
            values.setdefault(key, "")
    collection = {
        "info": {
            "_postman_id": "d812d9ae-71f7-48a6-95f2-1794c764f780",
            "name": "Trekking | Manual test scenarios | Apidog",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
            "description": "Đọc TEST_SCENARIOS.md. Chạy tuần tự từng folder/request, DỪNG lấy OTP và chọn license file. Không chạy toàn collection tự động. Mật khẩu mẫu chỉ cho dữ liệu test local. Public auth không kế thừa bearer; pre-request tự lấy CSRF. Tách token/refresh cookie theo vai trò trong environment. Bản này không chứa credentials thật.",
        },
        "auth": {"type": "noauth"},
        "item": folders,
        "variable": [{"key": "baseUrl", "value": "http://localhost:8080", "type": "string"}],
    }
    environment = {
        "id": "c5d929d6-4f3b-4c3d-80bd-3c54f492aa40",
        "name": "Trekking Local | điền email và admin password",
        "_postman_variable_scope": "environment",
        "values": [{"key": key, "value": value, "enabled": True,
                    "type": "secret" if any(s in key.lower() for s in ["password", "token", "otp"]) else "default"}
                   for key, value in values.items()],
    }
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, payload in [("Trekking.postman_collection.json", collection), ("Trekking.local.postman_environment.json", environment)]:
        (OUTPUT / name).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    fixtures = OUTPUT / "fixtures"
    fixtures.mkdir(exist_ok=True)

    def png_chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)

    # Small valid RGB image for upload validation; synthetic, contains no real license.
    pixels = b"".join(b"\x00" + b"\x30\x70\xa0" * 64 for _ in range(64))
    image = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
    image += png_chunk(b"tEXt", b"Description\x00QA test fixture - NOT a real company license")
    image += png_chunk(b"IDAT", zlib.compress(pixels)) + png_chunk(b"IEND", b"")
    (fixtures / "sample-license.png").write_bytes(image)
    lines = ["# Danh sách request và kết quả kỳ vọng", "", "Sinh từ `tools/generate_apidog_collection.py`; xem [hướng dẫn chạy](TEST_SCENARIOS.md).", ""]
    by_id = {entry["id"]: entry for entry in INDEX}
    for group in folders:
        lines += ["## " + group["name"], "", "| ID | Kịch bản | Method và URL | Kỳ vọng |", "| --- | --- | --- | --- |"]
        for item in group["item"]:
            entry = by_id[item["name"].split(" | ", 1)[0]]
            lines.append("| " + " | ".join([entry["id"], entry["title"], "`" + entry["method"] + " " + entry["path"] + "`", str(entry["status"]) + (" `" + entry["error"] + "`" if entry["error"] else "")]) + " |")
        lines.append("")
    (OUTPUT / "CASE_INDEX.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Generated {len(INDEX)} requests in {len(folders)} folders to {OUTPUT}")


if __name__ == "__main__":
    main()

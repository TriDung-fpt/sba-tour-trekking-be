# Chuẩn bị môi trường để test API trong Apidog

[Kịch bản và thứ tự chạy](TEST_SCENARIOS.md) · [Danh sách request](CASE_INDEX.md) · [JSON environment để import](Trekking.local.postman_environment.json)

Có hai nơi cấu hình riêng. **`.env` ở thư mục gốc chạy backend/Docker**, chứa kết nối database, Redis, khóa ký token và SMTP. **Environment Trekking Local trong Apidog dùng để gửi request**, chứa URL, email/mật khẩu test, OTP và token nhận từ response. Sửa một nơi không tự cập nhật nơi còn lại.

Tài liệu dùng placeholder và dữ liệu giả, không lấy hoặc hiển thị `.env` thật. Nếu backend đã chạy, giữ cấu hình hiện có; chỉ bổ sung giá trị còn thiếu. Không thay mật khẩu database hoặc tạo lại khóa chỉ để import collection.

## 1. Backend `.env`

File mẫu là [`.env.example`](../../../.env.example). Khi thiết lập lần đầu, sao chép thành `.env` rồi điền giá trị local. Nếu `.env` đã có, mở chỉnh đúng biến cần thay; không chép đè cả file mẫu lên cấu hình đang hoạt động. Không commit `.env`.

### Database, Redis và cổng

| Biến backend | Ví dụ local / yêu cầu | Ý nghĩa |
| --- | --- | --- |
| `POSTGRES_USER` | `trekking_local` | User khởi tạo PostgreSQL khi volume mới. Dùng giá trị thật đã có nếu database đã tồn tại. |
| `POSTGRES_PASSWORD` | `<mật_khẩu_DB_local>` | Mật khẩu PostgreSQL; tự đặt khi khởi tạo mới. |
| `POSTGRES_DB` | `trekking_db` | Database được tạo lúc khởi tạo mới. |
| `POSTGRES_PORT` | `5432` | Cổng PostgreSQL được publish ra máy bạn. |
| `DB_HOST` | `localhost` | Backend chạy bằng Maven trên máy kết nối tới địa chỉ này. Compose ghi đè thành `postgres`. |
| `DB_PORT` | `5432` | Khi Maven chạy trên máy, dùng cổng publish POSTGRES_PORT. Compose ghi đè thành `5432` bên trong mạng Docker. |
| `DB_NAME` | `trekking_db` | Phải trỏ đúng database; thông thường bằng POSTGRES_DB. |
| `DB_USERNAME` | `trekking_local` | User backend kết nối DB; với setup hiện tại thường bằng POSTGRES_USER. |
| `DB_PASSWORD` | `<cùng_mật_khẩu_POSTGRES_PASSWORD>` | Phải khớp mật khẩu user thật trong PostgreSQL. |
| `REDIS_HOST` | `localhost` | Backend chạy trên máy dùng địa chỉ này; Compose ghi đè thành `redis`. |
| `REDIS_PORT` | `6379` | Cổng Redis publish trên máy; Compose ghi đè cổng backend kết nối thành `6379`. |
| `REDIS_PASSWORD` | Để trống với Compose hiện tại | Redis trong Compose chưa bật mật khẩu. Chỉ đặt khi Redis đích thực sự cấu hình xác thực tương ứng. |
| `AUTH_REDIS_PREFIX` | `trekking:auth:` | Tiền tố dữ liệu phiên/OTP trong Redis; giữ ổn định trong lượt test. |
| `BACKEND_PORT` | `8080` | Compose publish `127.0.0.1:8080` tới cổng `8080` của container. Nếu đổi thành 8081, Apidog dùng `http://localhost:8081`. |
| `COMPANY_LICENSE_DIRECTORY` | `data/company-licenses` | Thư mục giấy phép khi chạy trên máy. Compose ghi đè `/app/company-licenses` và gắn volume riêng. |

Ví dụ quan hệ cần khớp khi **khởi tạo mới**:

```dotenv
POSTGRES_USER=trekking_local
POSTGRES_PASSWORD=<thay_bằng_mật_khẩu_mới>
POSTGRES_DB=trekking_db
DB_USERNAME=trekking_local
DB_PASSWORD=<cùng_mật_khẩu_ở_trên>
DB_NAME=trekking_db
BACKEND_PORT=8080
```

Đây là placeholder, phải thay trước khi dùng. Với volume PostgreSQL đã tồn tại, sửa POSTGRES_USER/PASSWORD/DB trong `.env` **không đổi user/mật khẩu/database đã lưu**. Nếu tự thay DB_PASSWORD theo mẫu, backend có thể không kết nối được nữa. Đối chiếu thông tin database hiện hữu và giữ nguyên khi đang hoạt động; không xóa volume để sửa sai cấu hình. Script trong `docker-entrypoint-initdb.d` chỉ khởi tạo với dữ liệu mới, không tự chạy lại mọi migration cho volume cũ.

### Khóa xác thực, cookie và CORS

| Biến backend | Giá trị cần có | Ý nghĩa |
| --- | --- | --- |
| `JWT_SECRET` | Khóa ngẫu nhiên riêng, ít nhất 32 byte UTF-8 | Ký/kiểm tra access token của backend. Không phải token để dán vào Bearer. |
| `OTP_SECRET` | Khóa ngẫu nhiên khác JWT_SECRET, ít nhất 32 byte UTF-8 | Bảo vệ OTP của backend. Không phải mã 6 chữ số trong email. |
| `AUTH_COOKIE_SECURE` | `false` cho HTTP local; `true` khi triển khai HTTPS | Cookie Secure cần môi trường phù hợp. Mặc định ứng dụng là true nếu không có cấu hình. |
| `AUTH_ALLOWED_ORIGINS` | Để trống nếu chỉ test bằng Desktop; ví dụ `http://localhost:5173` khi frontend dùng origin này | CORS cho frontend trình duyệt khác origin. Có thể liệt kê nhiều origin ngăn bằng dấu phẩy; không dùng `*`. |

Nếu thiết lập **mới**, chạy đoạn sau trong PowerShell để tạo hai khóa khác nhau từ bộ sinh số ngẫu nhiên mật mã. Đoạn này chỉ sinh giá trị mới, không đọc `.env`; sao chép hai dòng kết quả vào `.env` và giữ riêng:

```powershell
function New-LocalAuthSecret {
    $authRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $authBytes = New-Object byte[] 32
        $authRng.GetBytes($authBytes)
        [Convert]::ToBase64String($authBytes)
    } finally {
        $authRng.Dispose()
    }
}

$jwtLocalSecret = New-LocalAuthSecret
do { $otpLocalSecret = New-LocalAuthSecret } while ($otpLocalSecret -eq $jwtLocalSecret)
Write-Output ('JWT_SECRET=' + $jwtLocalSecret)
Write-Output ('OTP_SECRET=' + $otpLocalSecret)
```

Mỗi kết quả Base64 được sinh từ 32 byte ngẫu nhiên và dài hơn mức tối thiểu chuỗi UTF-8 backend yêu cầu. Không dùng chung hai khóa. Nếu backend đã có khóa hợp lệ, giữ nguyên: đổi khóa có thể làm token/OTP hiện tại mất hiệu lực.

Apidog Desktop gửi request local trực tiếp và thường không chịu giới hạn CORS của JavaScript trong trang web. Không thêm URL của Apidog vào AUTH_ALLOWED_ORIGINS chỉ để sửa lỗi token/CSRF. Nếu bạn test bằng frontend trên trình duyệt, cấu hình origin **chính xác** gồm scheme/host/port, ví dụ `http://localhost:5173`, và frontend gửi cookie bằng `credentials: "include"`. CORS không cấp role hay bỏ qua CSRF.

### SMTP để nhận OTP thật

| Biến backend | Ví dụ / cách điền | Ý nghĩa |
| --- | --- | --- |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP của hộp thư gửi. Có thể dùng SMTP khác mà bạn được phép sử dụng, hỗ trợ cấu hình auth/STARTTLS hiện tại. |
| `MAIL_PORT` | `587` | Cổng STARTTLS phù hợp cấu hình hiện tại. |
| `MAIL_USERNAME` | `<hộp_thư_gửi_của_bạn>@gmail.com` | Tài khoản xác thực SMTP; không phải email đăng nhập admin. |
| `MAIL_PASSWORD` | `<App_Password_của_hộp_thư_gửi>` | Với Gmail dùng App Password, không dùng mật khẩu đăng nhập Gmail thông thường. |
| `MAIL_FROM` | `<cùng_hộp_thư_gửi>@gmail.com` | Địa chỉ From được nhà cung cấp cho phép gửi; dễ nhất dùng cùng MAIL_USERNAME. |

Với Gmail, bật xác minh hai bước rồi tạo App Password cho hộp thư gửi; khả năng tạo có thể phụ thuộc chính sách tài khoản. Điền App Password được cấp vào MAIL_PASSWORD. [Hướng dẫn App Password của Google](https://support.google.com/accounts/answer/185833?hl=en), [SMTP Gmail và cổng TLS](https://support.google.com/a/answer/176600?hl=en).

Backend đang bật **SMTP auth + STARTTLS bắt buộc** trong application.properties. SMTP test không hỗ trợ TLS/auth sẽ không hoạt động với cấu hình này. Đổi riêng MAIL_PORT thành 465 cũng không tự bật implicit SSL. Với SMTP khác, lấy host/port/tài khoản được cấp và đối chiếu phương thức TLS trước khi dùng.

Hộp thư gửi SMTP và bốn email nhận OTP có thể khác nhau. `MAIL_FROM` không thay địa chỉ nhận OTP: đăng ký/reset/kích hoạt gửi tới email trong request; ký thỏa thuận gửi tới email tài khoản owner. `202` không chứng minh mail đã tới; kiểm tra inbox/spam và `deliveryStatus` ở lời mời.

Sau khi thực sự sửa `.env`, tạo lại container backend để áp dụng biến môi trường mới, ví dụ:

```powershell
docker compose up -d --build backend
docker compose ps
```

Restart container cũ đơn thuần không nạp lại các biến env_file đã thay. Không cần khởi tạo lại volume database/Redis vì sửa SMTP.

### Google login là tùy chọn

| Biến backend | Cách điền | Khi nào cần? |
| --- | --- | --- |
| `GOOGLE_CLIENT_IDS` | OAuth **Web client ID thật** của frontend; nhiều ID ngăn bằng dấu phẩy, ví dụ `<client_id_thật>.apps.googleusercontent.com` | Chỉ để kiểm tra Google login. Để trống vô hiệu hóa Google, email/password vẫn dùng được. |

Client ID lấy từ cấu hình OAuth của project Google bạn quản lý; không đặt chuỗi ví dụ tùy ý và không nhầm client ID với client secret. Backend này không cần Google client secret để xác minh ID token. Google hướng dẫn tạo Web client ID và origin frontend tại [Setup Google Identity Services](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid).

Frontend dùng nút Sign in with Google với client ID tương ứng. Sau khi người dùng đăng nhập, callback nhận `response.credential`; đây là **Google ID token**. Sao chép giá trị còn hạn này vào biến `googleIdToken` của Apidog, rồi chạy request Google login. Google mô tả field credential tại [JavaScript API reference](https://developers.google.com/identity/gsi/web/reference/js-reference).

Không tự ký Google ID token, không dán backend accessToken hoặc Google OAuth access token vào googleIdToken. Audience của ID token phải khớp GOOGLE_CLIENT_IDS. API `/google` nhận JSON `{"idToken":"..."}` kèm CSRF của backend, không phải endpoint để Google redirect thẳng vào. Backend cần kết nối HTTPS tới khóa công khai Google. Nếu GOOGLE_CLIENT_IDS trống, nhánh Google có thể trả `503 GOOGLE_AUTH_UNAVAILABLE`; đánh dấu SKIP/BLOCKED nhánh tương ứng.

## 2. Environment Apidog

Import [Trekking.postman_collection.json](Trekking.postman_collection.json), sau đó import riêng [Trekking.local.postman_environment.json](Trekking.local.postman_environment.json). Chọn đúng environment ở màn hình gửi request. Khi có chế độ giá trị dùng riêng trên máy/giá trị được chia sẻ, điền mật khẩu, OTP, token vào phần **local/current** đang được request sử dụng; kiểm tra preview biến. Không điền bí mật vào giá trị chia sẻ của team hoặc commit file export đã có thông tin thật.

Giữ các test case cùng method/URL nhưng khác dữ liệu và kỳ vọng. Nếu import hỏi cách xử lý endpoint trùng, chọn cách giữ toàn bộ request/case/examples, tránh gộp đè các negative case thành một endpoint duy nhất. Sau import đối chiếu folder và tên case với [CASE_INDEX](CASE_INDEX.md); kiểm tra Pre Processors/Post Processors và assertions còn đủ.

Trong **Pre Processors**, script tạo biến phải chạy **trước Variable Substitution** (có phiên bản ghi Variable Substitution & Inherit from Parents). Đây là điều kiện để runId/taxCode/effectiveFrom mới được đưa vào URL/body của chính request. Nếu thứ tự bị đổi khi import, chuyển custom pre-request script lên trước bước thay biến; xem [hướng dẫn sửa request của Apidog](https://docs.apidog.com/modifying-requests-597445m0).

### Biến bạn phải nhập

| Biến Apidog | Giá trị mẫu / nguồn lấy | Lưu ý |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | Theo BACKEND_PORT; không thêm `/api/v1` hoặc `/` cuối. |
| `customerEmail` | `your.mail+trek-customer-01@gmail.com` | Email mới, chưa đăng ký; owner công ty test. Thay your.mail bằng hộp thư thật của bạn. |
| `customerPassword` | `DemoPassword123!` hoặc mật khẩu test riêng | Dùng khi register/login customer; tối thiểu 8 ký tự, tối đa 72 byte UTF-8. |
| `otherEmail` | `your.mail+trek-other-01@gmail.com` | Email mới khác customer; test phạm vi, role và block/unblock. |
| `otherPassword` | `DemoOther123!` hoặc mật khẩu test riêng | Dùng register/login khách hàng thứ hai. |
| `adminEmail` | `<email_admin_đang_có>` | Admin active, đã xác minh, không bị bắt đổi mật khẩu. |
| `adminPassword` | `<mật_khẩu_admin_đang_có>` | Không có admin mẫu/mật khẩu mặc định trong collection. |
| `accountantEmail` | `your.mail+trek-accountant-01@gmail.com` | Email mới, để admin tạo invitation; không register trước. |
| `accountantPassword` | `DemoFinance123!` hoặc mật khẩu test riêng | Mật khẩu mới sẽ đặt khi activate invitation, rồi dùng login. |
| `guideEmail` | `your.mail+trek-guide-01@gmail.com` | Email mới, để owner mời guide; không register trước. |
| `guidePassword` | `DemoGuide123!` hoặc mật khẩu test riêng | Đặt khi activate guide, rồi dùng login. |
| `newCustomerPassword` | `DemoReset123!` hoặc mật khẩu test riêng | Chỉ nhánh reset; sau reset thành công, cập nhật customerPassword bằng giá trị này. |
| `verificationOtp` | Email register customer | Nhập trước CUS-02; chuỗi 6 chữ số mới nhất. |
| `otherVerificationOtp` | Email register khách hàng thứ hai | Nhập trước OTHER-02. |
| `activationOtp` | Email invitation accountant | Nhập trước INV-03; nếu resend, dùng mã mới nhất. |
| `guideActivationOtp` | Email invitation guide | Nhập trước GUI-03; nếu resend, dùng mã mới nhất. |
| `agreementOtp` | Email ký gửi tới customerEmail | Nhập trước AGR-07; không lấy từ contactEmail công ty. |
| `resetOtp` | Email forgot-password customer | Nhập trước OPT-05; OTP khác mục đích không thay được. |
| `googleIdToken` | response.credential từ frontend đăng nhập Google | Chỉ nhánh tùy chọn OPT-03; để trống nếu SKIP. |

Backend không có API tạo admin công khai: register tạo customer, API thay role cũng cấm cấp admin. Nếu chưa có admin hợp lệ, nhờ người quản lý môi trường cung cấp tài khoản được cấp sẵn; hoàn thành phần không cần admin và ghi BLOCKED phần phụ thuộc. Không sửa role trực tiếp trong database để làm bộ test chạy qua.

Bốn địa chỉ mới phải khác nhau và chưa tồn tại trong backend. Nếu Gmail/nhà cung cấp hỗ trợ plus addressing, các alias `your.mail+trek-customer-01@gmail.com`, `...+trek-other-01`, `...+trek-accountant-01`, `...+trek-guide-01` cùng về hộp thư gốc nhưng backend nhận bốn email khác nhau. Đổi hậu tố `01` cho lượt mới. Google mô tả alias dấu cộng tại [Gmail Blog](https://gmail.googleblog.com/2008/03/2-hidden-ways-to-get-more-from-your.html). Nếu nhà cung cấp không hỗ trợ, dùng bốn email/hộp thư nhận được mail riêng. Alias này phục vụ luồng email/password; không tạo ra bốn danh tính Google đăng nhập khác nhau.

### Biến script tự tạo và tự lưu

Để trống các output khi mới import. Chạy request trước đúng thứ tự để có dữ liệu; không nhập ID ví dụ từ API docs.

| Biến Apidog | Nguồn / ý nghĩa |
| --- | --- |
| `runId` | Pre-request tạo timestamp một lần; giữ ổn định trong lượt test. |
| `taxCode` | Tự tạo mã số thuế test từ runId; database yêu cầu duy nhất. |
| `effectiveFrom` | Tự tạo mốc ISO 8601 đã có hiệu lực, dùng publish policy version. |
| `csrfToken` | Pre-request lấy từ GET csrf cho auth POST; đây không phải access token. |
| `customerId`, `customerToken`, `customerRefreshToken` | Response login/refresh customer: ID, access token và refresh cookie. |
| `otherId`, `otherToken`, `otherRefreshToken` | Response login khách hàng thứ hai. |
| `adminId`, `adminToken`, `adminRefreshToken` | Response login admin hiện hữu. |
| `accountantId`, `accountantToken`, `accountantRefreshToken` | Invitation trả accountId; login accountant lưu token/cookie và ID. |
| `guideId`, `guideToken`, `guideRefreshToken` | Invitation trả **account ID**, không phải guide profile ID; login lưu token/cookie. |
| `googleId`, `googleToken`, `googleRefreshToken` | Response login Google tùy chọn; tách khỏi customer của luồng chính. |
| `companyId` | Công ty chính do customer tạo; dùng duyệt, license, guide và agreement. |
| `rejectedCompanyId` | Công ty thứ hai dùng nhánh reject/resubmit cuối luồng. |
| `policyId` | Policy admin tạo cho công ty test. |
| `policyVersionId` | Version policy được publish; dùng xin OTP và ký. |
| `agreementId` | Receipt nhận từ ký thành công; dùng đọc chi tiết. |
| `loggedOutCustomerToken` | Access token customer được giữ riêng trước logout để kiểm tra server từ chối phiên đã thu hồi; không dùng cho request nghiệp vụ mới. |

Token/cookie do script lưu là bí mật dù environment gắn type secret. Giữ riêng trên máy; tính năng che hiển thị không bảo đảm file export đã được xóa giá trị. Script xóa token/ID output cũ trước một số request tạo mới/login, tránh dùng kết quả cũ khi request thất bại. Nếu bước trước lỗi, sửa tiền điều kiện và chạy lại đúng bước; không điền bừa ID để bỏ qua.

### Chuỗi OTP và số ID trong JSON

OTP, email, password và token là chuỗi. ID trong request body là số JSON. Ví dụ body chưa thay biến:

```text
{"policyVersionId": {{policyVersionId}}, "otp": "{{agreementOtp}}"}
```

Sau khi environment có ID `7` và OTP ví dụ `012345`, request hợp lệ là:

```json
{"policyVersionId": 7, "otp": "012345"}
```

Không bỏ ngoặc kép của OTP vì sẽ mất số 0 đầu; không thêm ngoặc kép cho ID chỉ để hết cảnh báo editor. Trước khi biến ID được gán, body template có thể chưa parse được thành JSON; hoàn thành bước tạo tài nguyên và kiểm tra preview sau thay biến. Không để `{{policyVersionId}}` hoặc `{{agreementOtp}}` nguyên trong request gửi đi.

### Cookie và refresh cho nhiều vai trò

Collection tự lấy CSRF trước auth POST và đặt Cookie tường minh. Refresh/logout gửi refresh cookie đúng vai trò, không dựa vào cookie jar chung. Dùng Apidog Desktop và giữ tùy chọn tắt tự gửi cookie của request; nếu importer bỏ qua disableCookies, tắt ở client theo [hướng dẫn chạy](TEST_SCENARIOS.md). Không tự thêm Bearer vào request public hoặc đổi refresh cookie của customer thành admin.

Access token mặc định sống khoảng 15 phút; dừng lâu để chuẩn bị mail/file có thể phải login lại. Với refresh, dùng cookie mới script vừa lưu; không dùng lại cookie cũ sau rotation. Sau reset mật khẩu, login bằng newCustomerPassword rồi cập nhật customerPassword; sau block/unblock tài khoản other, dùng login mới vì phiên cũ không hồi phục.

## 3. Tài liệu mẫu và kiểm tra trước khi chạy

Các file mẫu đều là dữ liệu test, không phải giấy phép hay thỏa thuận pháp lý thật:

| File | Cách dùng |
| --- | --- |
| [sample-license.pdf](fixtures/sample-license.pdf) | Chọn cho field multipart file ở CO-15 nếu muốn upload PDF. |
| [sample-license.png](fixtures/sample-license.png) | Lựa chọn PNG hợp lệ cho cùng request upload. Chọn một file mỗi lần. |
| [cooperation-policy.txt](fixtures/cooperation-policy.txt) | Nội dung mẫu để đọc/thay field content khi publish version. |
| [payout-terms.txt](fixtures/payout-terms.txt) | Nội dung mẫu cho payoutTermsSnapshot. |
| [policy-version-body.json](fixtures/policy-version-body.json) | Copy nguyên JSON này vào raw body POL-07 để dùng hai nội dung văn bản đã được escape đúng. |
| [invalid-license.txt](fixtures/invalid-license.txt) | File âm cố ý không phải PDF/ảnh; chỉ dùng case upload không hợp lệ theo tài liệu test. |

File policy/terms là văn bản tham khảo, không phải upload endpoint riêng. Khi đưa nội dung vào JSON body, dùng editor để giữ escape/newline hợp lệ; request publish đã có nội dung test mặc định. Multipart file phải được chọn thủ công trên máy sau import.

Trước lượt test, kiểm tra Docker backend sẵn sàng; baseUrl đúng cổng; environment đang chọn; bốn email mới nhận được mail; admin có thông tin thật; script trước Variable Substitution; file upload đã chọn. Bắt đầu folder 00 và đi theo [TEST_SCENARIOS](TEST_SCENARIOS.md), dừng tại mỗi OTP. Không chạy toàn bộ collection một lượt.

Tài liệu cấu hình và JSON hợp lệ chưa chứng minh SMTP, Google hay import Apidog đã chạy thành công trên máy bạn. Nếu thiếu giá trị hoặc nhà cung cấp mail chưa hoạt động, ghi BLOCKED nhánh tương ứng; không đánh dấu PASS chỉ vì request mẫu đã có JSON.

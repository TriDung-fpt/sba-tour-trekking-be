# Ảnh tour trên Cloudinary

## Cấu hình

Điền ba biến sau trong `.env` của backend, lấy từ trang API Keys của Cloudinary:

```dotenv
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

Không commit `.env`. Nếu để trống, backend vẫn khởi động; tải/xóa file Cloudinary
trả 503 `IMAGE_STORAGE_NOT_CONFIGURED`. Sửa chú thích, thứ tự và chọn bìa vẫn dùng
được với ảnh đã có trong database.

Tour dùng `features.storage.ImageStorage`; hiện tại Cloudinary là adapter lưu ảnh.
Module khác có thể gọi cùng interface mà không cần dùng SDK Cloudinary trực tiếp.
Database đã áp dụng migration 010 thì cần chạy tiếp 011 và 012; database cũ hơn
cần chạy các migration còn thiếu theo thứ tự. Migration 010 đổi
`cloudinary_public_id` thành `storage_key` và giữ nguyên giá trị đang có.
Migration 011 thêm `image_url` và `storage_key` cho dụng cụ cùng gói thuê.
Migration 012 ghi rõ giá thuê tính theo số gói hoặc số món được chọn.

## Thử bằng Postman

Các API dùng `Authorization: Bearer <access_token>` như API tour hiện tại. Theo
SecurityConfig hiện tại, các đường dẫn tour không yêu cầu CSRF token.

### Tải ảnh

`POST /api/v1/tours/3/media`, chọn Body → form-data:

| Key | Kiểu | Nội dung |
| --- | --- | --- |
| file | File | Một ảnh JPEG, PNG hoặc WebP, tối đa 5 MB |
| caption | Text | Chú thích tùy chọn |

Để Postman tự tạo header `Content-Type` và phần boundary. Thành công trả 201 cùng
`TourMediaResponse`. Ảnh được thêm vào cuối danh sách; nếu tour chưa có ảnh bìa,
ảnh mới tự thành bìa. Tên file và URL từ client không được dùng để chọn file
Cloudinary; backend tạo `public_id` riêng cho từng lần tải.

### Ảnh dụng cụ và gói thuê

Catalog dùng chung cùng `ImageStorage`. Tải ảnh bằng multipart form-data, field
`file`, tối đa 5 MB; mỗi dụng cụ và mỗi gói có một ảnh đại diện. GET catalog trả
`imageUrl`, còn `storage_key` chỉ dùng nội bộ để thay hoặc xóa file.

- `POST /api/v1/tours/equipment-catalog/{equipmentId}/image`
- `DELETE /api/v1/tours/equipment-catalog/{equipmentId}/image`
- `POST /api/v1/tours/rental-packages/{packageId}/image`
- `DELETE /api/v1/tours/rental-packages/{packageId}/image`

### Sửa chú thích

`PATCH /api/v1/tours/3/media/12`, Body → raw → JSON:

```json
{ "caption": "Trại nghỉ giữa đường" }
```

`caption` trống, `null` hoặc bị bỏ qua sẽ xóa chú thích. API này không đổi file.

### Chọn ảnh bìa

`PATCH /api/v1/tours/3/media/12/cover`, không cần body. Thành công trả 200 cùng
ảnh đã chọn. Mỗi tour có tối đa một ảnh bìa; chọn lại ảnh đang làm bìa vẫn trả 200.

### Đổi thứ tự

`PUT /api/v1/tours/3/media/order`, gửi đầy đủ ID ảnh của tour theo thứ tự muốn xem:

```json
{ "mediaIds": [12, 15, 14] }
```

Thành công trả 200 và danh sách ảnh theo thứ tự mới. `displayOrder` được đánh lại
từ 0. Thiếu ID, ID trùng hoặc ID không thuộc danh sách ảnh của tour trả 400
`INVALID_MEDIA_ORDER`. Danh sách rỗng chỉ hợp lệ khi tour chưa có ảnh. API ảnh không
sửa các bản ghi video cũ.

### Xóa ảnh

`DELETE /api/v1/tours/3/media/12`, không cần body; thành công trả 204. Khi xóa ảnh
bìa, ảnh đầu tiên còn lại theo thứ tự hiển thị trở thành bìa. Xóa ảnh cuối cùng thì
tour không còn bìa. Bản ghi ảnh cũ có `storage_key = NULL` chỉ bị xóa trong
database; không đoán mã ảnh từ URL để xóa trên Cloudinary.

Xem lại bằng `GET /api/v1/tours/3` (trường `media`) hoặc `GET /api/v1/tours`
(trường `coverImageUrl`). Không có API GET ảnh riêng.

## Lỗi và thao tác đồng thời

- File rỗng, giả ảnh hoặc Cloudinary từ chối nội dung ảnh: 400 `INVALID_IMAGE`.
- File quá 5 MB: 413 `MAX_UPLOAD_SIZE`, thống nhất với giới hạn multipart hiện tại.
- Tour hoặc ảnh không thuộc tour: 404 `RESOURCE_NOT_FOUND`.
- Cloudinary gặp lỗi hoặc sai khóa: 503 `IMAGE_STORAGE_UNAVAILABLE`.
- Lưu database thất bại sau khi tải: backend thử xóa file vừa tạo, kể cả khi lỗi
  xảy ra ở bước commit. Nếu xóa bù thất bại, log ghi storage key cần dọn thủ công.

Thay đổi bìa, thứ tự, chú thích, xóa ảnh và lưu ảnh mới dùng cùng khóa trên tour.
Ảnh bìa cũ được bỏ trước khi đặt ảnh mới để giữ ràng buộc `uq_tour_cover`.
Khi xóa ảnh, backend flush thay đổi database trước khi gọi Cloudinary; nếu
Cloudinary báo lỗi, transaction database bị rollback.

Database và Cloudinary không dùng chung transaction: nếu Cloudinary đã xóa file
nhưng commit database thất bại, lần xóa lại sẽ dọn bản ghi còn lại (Cloudinary
`not found` được xem là đã xóa). Bản đầu chưa có job tự chạy lại việc dọn file.

## Kiểm tra

`TourMediaTests` kiểm tra API, ảnh bìa, thứ tự, ảnh cũ và dọn file khi lưu thất bại.
`features.storage.CloudinaryImageStorageTests` dùng SDK giả lập để kiểm tra định dạng, kích thước,
thiếu cấu hình và lỗi Cloudinary. `TourMediaIntegrationTests` dùng PostgreSQL để
kiểm tra một ảnh bìa duy nhất và rollback khi Cloudinary gặp lỗi. Test không gửi
ảnh lên tài khoản thật; các test tích hợp cần Docker.

Tài liệu SDK: [Cloudinary Java](https://cloudinary.com/documentation/java_integration),
[tải ảnh](https://cloudinary.com/documentation/java_image_and_video_upload).

# Tour terms

Admins manage reusable, versioned customer policies under Điều khoản & lưu ý. `kind` can be `general`, `payment` or `cancellation`; `GET /api/v1/admin/policies/available` lists currently effective versions. The cancellation version used for booking remains the tour's separately selected cancellation policy. Additional general and payment versions are attached through `PUT /api/v1/tours/{tourId}/terms` with `{ "versionIds": [1, 2] }`; this replaces the list atomically and maintains the supplied order. `GET` on the same URL returns the selected cancellation policy followed by additional terms. Public reads require a published tour; writes require admin. Attached versions are explicit and are not automatically updated to newer versions.

Public responses hide disabled, revoked or not-yet-effective versions; admin reads still show the assigned versions for reconciliation. The cancellation policy appears as the primary term and is selected separately so the displayed cancellation terms match the version used for new bookings.

Migration 017 must run after 016. Booking currently snapshots only its cancellation policy; additional terms are not included in booking snapshots. The schedule-specific policy-locking flow discussed separately has not yet been implemented.

Migration 018 adds `deleted_at`. `DELETE /api/v1/admin/policies/{id}` requires policy management permission, hides and disables the policy, and records an audit event. It preserves versions and existing references. Published tours using a deleted cancellation policy cannot accept new bookings until another policy is assigned. Deleted codes remain reserved.
# Cập nhật điều khoản của tour

`PUT /api/v1/tours/{tourId}/terms` nhận `versionIds` cho các điều khoản bổ sung và có thể nhận `cancellationPolicyVersionId` cho chính sách hủy đang có hiệu lực. Hai phần được kiểm tra và lưu trong cùng một giao dịch. Bỏ qua `cancellationPolicyVersionId` sẽ giữ chính sách hủy hiện tại. Phiên bản hủy vẫn được ghi riêng vào booking theo flow hiện hành.

Để gỡ chính sách hủy khỏi tour chưa xuất bản, gửi `removeCancellationPolicy: true`. Tour đã xuất bản phải giữ chính sách hủy và chỉ có thể thay bằng phiên bản khác; booking đã tạo vẫn giữ phiên bản cũ.

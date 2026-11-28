# ADV-009 — Giữ nhóm ghế

**Trạng thái:** DONE — đã review và kiểm thử PostgreSQL ngày 28/11/2026. Theo yêu cầu người dùng, thực hiện và đóng ADV-009 khi ADV-008/008A còn REVIEW; không tự đóng các ticket đó.

- V5 tạo reservation gắn show, owner và snapshot giá từng ghế. Service nội bộ chỉ nhận owner UUID đã được caller xác thực; chưa có endpoint.
- Khóa show trước, sau đó khóa từng show-seat theo thứ tự UUID ổn định. Chỉ show PUBLISHED và ghế AVAILABLE có giá cùng currency được giữ. Ghi reservation, ghế HELD và tăng phiên bản sơ đồ trong một transaction; trả kết quả sau commit. Transaction độc lập (`REQUIRES_NEW`): caller không được giữ khóa show trước khi gọi.
- Cấu hình `ticketlock.reservation.max-seats` mặc định 6 (1–6), `ticketlock.reservation.hold-seconds` mặc định 600. Thời gian bắt đầu lấy từ PostgreSQL sau khi lấy khóa. Giá snapshot là giá server khi hold; tổng kiểm tra tràn số.
- Test PostgreSQL kiểm tra tranh ghế, rollback sau khi đã ghi ghế, lỗi lúc commit, ghế trùng/thuộc show khác, nhóm 6 ghế, snapshot giá, deadline, version và liên kết owner/show; test migration kiểm tra cài mới và baseline. Đã chạy 16 test reservation, migration và inventory thành công. Expire/release/confirm, ownership API và idempotency thuộc ADV-010–012. Ghế HELD hết hạn chưa tự được giải phóng trong ticket này.

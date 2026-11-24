# ADV-003 — Hợp đồng API dự kiến M1

**Trạng thái: DRAFT.** [OpenAPI 3.0.3](openapi.yaml) mô tả API dự kiến, không đại diện endpoint đã chạy. Không tạo controller giả. Các quyết định này cần được triển khai và kiểm thử ở ADV-005–ADV-014; API có thể thay đổi sau review.

## Phạm vi và xác thực

- API đọc công khai chỉ trả Event có ít nhất một Show PUBLISHED và các Show PUBLISHED; Event/Show không công khai hoặc không tồn tại trả 404. Danh sách dùng `page` từ 0, `size` mặc định 20, tối đa 100; thứ tự ổn định theo ID cho Event, `startsAt, id` cho Show, và thời điểm tạo rồi ID cho Booking. Tổng số phản ánh thời điểm truy vấn, không hứa snapshot xuyên trang.
- Admin tạo Event, Show và publish; customer đọc, hủy reservation và tạo/đọc booking của chính mình. ADMIN không tự bypass ownership; quyền riêng cần policy/ticket khác. Server lấy UUID user từ `Authentication`, không tin `ownerId`, role hay trạng thái do client gửi. Input object từ customer từ chối field không xác định.
- Portal dùng `JSESSIONID` và cookie `XSRF-TOKEN`; POST/DELETE gửi header `X-XSRF-TOKEN` cùng cookie theo luồng CSRF hiện tại. Những route draft thuộc session chain; bearer JWT chưa được hứa hỗ trợ ở đây. Các API thực tế `/api/auth/csrf`, `/api/auth/register`, `/api/auth/login`, `/api/auth/logout`, `/api/token/login`, `/api/token/refresh`, `/api/token/logout`, `/api/stateless/**` nằm ngoài đặc tả draft; nhóm token/stateless chỉ bật profile `jwt`. Không giả định mọi `/api/**` dùng JWT hoặc có lỗi định dạng mới.
- Timestamp là UTC RFC 3339 với hậu tố `Z`. `showSeatId` định danh trạng thái ghế của một show; mỗi hold tối đa 6 ghế khác nhau (giới hạn hợp đồng M1, cấu hình server không vượt 6). Thời hạn mặc định 10 phút theo server; `now >= expiresAt` là hết hạn. Snapshot có thể cũ; trạng thái AVAILABLE không đảm bảo hold thành công.

## Giá và xác nhận

Snapshot ghế trả giá tham khảo theo đơn vị nhỏ nhất của currency. Khi tạo hold, server đọc giá hiện hành và lưu snapshot cho từng ghế cùng reservation trong transaction; response trả các giá và tổng. M1 không thu phí bổ sung: tổng bằng tổng giá ghế, cùng một currency; thay đổi chính sách phí cần cập nhật contract trước khi triển khai. Nếu giá thay đổi giữa snapshot và hold, hold vẫn dùng giá server tại commit và client **phải hiển thị rõ giá mới**. Client có thể hủy hold nếu không đồng ý. Tạo booking chưa thanh toán; trước checkout ở giai đoạn sau, client phải hiển thị tổng cuối và nhận xác nhận của khách. Không thu tiền chỉ từ thao tác hold/booking. Server không nhận giá, currency hoặc payment status do customer tự khai làm sự thật.

## Idempotency và timeout

`Idempotency-Key` bắt buộc cho POST reservation/booking; phạm vi là principal UUID + operation + key. Key là chuỗi ngẫu nhiên 1–128 ký tự theo schema, không chứa dữ liệu nhạy cảm. Fingerprint dựa trên payload hợp lệ chuẩn hóa: với reservation, sort tập showSeatId sau khi từ chối trùng; với booking là reservationId. Cùng key/payload trả nguyên kết quả cuối (kể cả lỗi nghiệp vụ cuối); cùng key/payload khác trả 409 `IDEMPOTENCY_KEY_REUSED`. Cùng key đang xử lý trả 202 `PENDING`; client chờ `Retry-After` nếu có và GET endpoint tra cứu tương ứng, truyền cùng key trong header. Không log key đầy đủ.

Nếu timeout sau khi gửi, client **không suy đoán thất bại**: tra cứu kết quả bằng key hoặc retry cùng request và key. Nếu GET trả 404, việc không tìm thấy không chứng minh request chưa/không commit khi hệ thống đang phục hồi; client chỉ retry cùng key, không tạo key mới cho cùng ý định. Key được lưu bền tối thiểu 24 giờ sau kết quả cuối; record đang xử lý không bị xóa. Sau thời gian này việc retry key cũ không còn bảo đảm dedupe; client phải đối chiếu reservation/booking trước khi tạo ý định mới. ADV-011 triển khai idempotency reservation; booking cần cơ chế tương đương ở ADV-013. Hợp đồng 202 và tra cứu là dự kiến; không có nghĩa queue đã tồn tại.

## Lỗi và correlation

Mỗi response lỗi mới có `{code, message, traceId}`; `message` an toàn cho người dùng, không chứa credentials, SQL hoặc token. Server tạo traceId cho request và trả `X-Trace-Id` trên response; nếu nhận `X-Request-Id` hợp lệ (1–64 ký tự ASCII chữ/số, dấu `._-`), server có thể ghi như correlation ID riêng, không dùng làm quyền hay tin tùy ý. Không ghi cookie, CSRF, bearer hoặc refresh token vào log. Các endpoint Mini chưa cam kết format này.

| Status | Ý nghĩa và ví dụ |
|---|---|
| 400 | UUID, body, pagination hoặc idempotency key không hợp lệ |
| 401 | Không có session hợp lệ hoặc session hết hạn |
| 403 | Principal không có role/ownership, hoặc thiếu/sai CSRF; ví dụ user B đọc reservation A |
| 404 | Tài nguyên không tồn tại hoặc chưa được công khai; key không tìm thấy trong phạm vi user |
| 409 | Ghế không còn trống, trạng thái không cho phép, hoặc cùng key khác payload |
| 429 | Vượt rate limit khi chức năng này được triển khai; có thể kèm `Retry-After` |
| 503 | Backend chưa sẵn sàng hoặc bounded queue đầy khi queue được triển khai; có thể kèm `Retry-After` |

Các response 429/503 không khẳng định rate limit hay queue đã được xây dựng. Khi retry thao tác có key sau 429/503, giữ nguyên key và payload; không mặc định đã hoặc chưa commit nếu kết quả không rõ.

## Lưu ý cho ticket tiếp theo

- ADV-005–008 cần chốt cách admin tạo/import layout trước publish; API layout không nằm trong draft này. Show chỉ publish sau khi dữ liệu và layout hợp lệ; layout đã mở bán không thay đổi như ADR.
- ADV-009–012 phải kiểm thử PostgreSQL với hai request tranh ghế, rollback nhóm, deadline, stale command và ownership. Booking độc nhất theo reservation được thực thi ở ADV-013.
- Đặc tả này chưa mô hình hóa payment, phát vé, realtime, Kafka hay API tách service. Không xem booking PENDING là thanh toán thành công.

# ADV-006 — Layout ghế và trạng thái ghế theo Show

**Trạng thái:** REVIEW — code đã được triển khai sớm; giữ nguyên và chờ ADV-005 DONE trước khi review/đóng ADV-006.

## Quyết định

- V3 tạo `seats` thuộc Event và `show_seats` thuộc Inventory. Mỗi show-seat có UUID, trạng thái AVAILABLE/HELD/SOLD, reservation ID, deadline và version; chưa có hành vi hold hay bảng reservation, sẽ triển khai ở ADV-009. Lịch sử không nằm trong bản ghi trạng thái hiện tại; chưa tạo bảng lịch sử ở ticket này.
- `(event_id, label)` và `(show_id, seat_id)` duy nhất. Foreign key tổng hợp bảo đảm show và seat cùng Event. `AVAILABLE` không có reservation/deadline; `HELD` cần cả hai; `SOLD` không có deadline. Chưa có FK reservation vì bảng chưa tồn tại.
- `SeatLayoutService.importSeats` nhận 1–1000 nhãn, chuẩn hóa khoảng trắng, từ chối nhãn trùng; cả lần import nằm trong transaction. Import lại cùng nhãn không tạo trùng. Seat là định nghĩa bất biến và có thể được nhiều show dùng; ShowSeat là bản ghi riêng cho từng show, nên thay đổi trạng thái một show không làm thay đổi show khác.
- Import khóa Event rồi khóa Show. Publish khóa cùng hàng Show và kiểm tra có ít nhất một ShowSeat trước khi commit. Chỉ show DRAFT chưa từng mở bán được import. Publish đồng thời đánh dấu `sales_opened_at`; show đã publish/hủy không được import thêm qua service. Layout dùng chung không có API sửa/xóa Seat; mọi thao tác layout trong tương lai phải giữ quy tắc bất biến và dùng cùng khóa. DB hiện không có trigger ngăn người có quyền SQL/repository sửa trực tiếp layout: invariant được áp dụng ở service được cung cấp, các đường ghi mới phải bổ sung kiểm tra tương ứng.
- `ShowSeatLayout` là port nội bộ do Event định nghĩa, Inventory triển khai và bắt buộc tham gia transaction của caller; không tạo controller hay API công khai trong ADV-006.

## Bằng chứng kiểm thử

- Backend test đầy đủ chạy trên PostgreSQL/Redis test riêng sau V3; migration test kiểm tra DB mới và nâng cấp từ V1, bảo toàn dữ liệu user/refresh. Kiểm thử Inventory riêng sau đó cũng xanh.
- Kiểm tra import lặp, từ chối nhãn trùng, hai show dùng cùng seat ID nhưng show-seat và trạng thái độc lập, DB chặn ghế trùng và ghế thuộc Event khác.
- Kiểm tra publish không có layout bị từ chối, publish có layout thành công, import sau publish bị từ chối và race publish/import kết thúc nhất quán trên PostgreSQL. Đây là một kịch bản đồng thời, không phải benchmark hay bằng chứng bao phủ mọi interleaving.
- Chạy [seed giả](../../../scripts/seed-seat-demo.sql) hai lần trên DB test đã migrate: lần hai không thêm bản ghi. Chỉ dùng DB test/demo độc lập, không chạy trên DB người dùng.

Ví dụ chạy seed trên môi trường test **sau khi backend test đã migrate V3**:

```bash
docker compose -f compose.test.yaml exec -T postgres psql -U ticketlock_test -d ticketlock_test -v ON_ERROR_STOP=1 < scripts/seed-seat-demo.sql
```

## Còn lại

ADV-007 bổ sung API admin/public, authorization và snapshot; ADV-009–012 triển khai hold, transaction cạnh tranh, expiry và kiểm thử no-oversell. Không xem status AVAILABLE hay test race layout ở đây là bằng chứng giữ ghế đã hoạt động. Liên kết reservation → show là phạm vi và tiêu chí nghiệm thu của ADV-009, không chặn đóng ADV-005 hoặc ADV-006. ADV-007 bổ sung giá/currency để snapshot ghế và publish theo contract có dữ liệu thật.

# Sprint 2 — Event, Show và sơ đồ ghế

**Trạng thái:** IN_PROGRESS — ADV-005/006/007 DONE, ADV-008 và ADV-008A REVIEW · [Backlog đầy đủ](../SPRINT_BACKLOG.md)

Mục tiêu cuối sprint: ADMIN tạo và publish show có layout/giá hợp lệ; khách xem show và snapshot ghế. Không triển khai hold hay booking trong sprint này.

| Thứ tự | Ticket | Điều kiện bắt đầu | Kết quả để đóng độc lập |
|---:|---|---|---|
| 1 | [ADV-005](ADV_005_EVENT_SHOW.md) | ADV-002 DONE | Event/Show, migration V2 và state transition được review; test layout port giả và fail-closed, không đòi layout/reservation thật |
| 2 | [ADV-006](ADV_006_SEAT_INVENTORY.md) | ADV-005 DONE | Layout/ShowSeat, V3, seed, test DB và test tích hợp layout/publish; chưa cần API/hold |
| 3 | ADV-007 | ADV-006 DONE và ADV-003 DONE | Cấu hình giá, API ADMIN/public, snapshot có giá, authorization/CSRF và kiểm thử; chưa cần hold |
| 4 | ADV-008 | ADV-007 DONE | React xem danh sách/show/snapshot; test loading/error và phân quyền; chưa cần booking |
| 5 | [ADV-008A](ADV_008A_NAVIGATION.md) | ADV-008 DONE | Trang `/events`, `/profile`, điều hướng desktop/mobile và deep-link; bảo toàn hành vi ADV-008 |

## Giữ nguyên tiến trình hiện tại

ADV-005 và ADV-006 đã được triển khai chồng lấn trước khi áp dụng quy tắc tuần tự. Giữ nguyên V2, V3, code, test, seed và dữ liệu; không sửa lịch sử hay gán DONE tự động. Thứ tự xử lý:

1. ADV-005 đã tự review và DONE; ADV-006 đã được review tiếp theo, bổ sung test rollback và DONE.
2. [ADV-007](ADV_007_EVENT_API.md) đã tự review và DONE; [ADV-008](ADV_008_SHOW_UI.md) đã triển khai, đang chờ kiểm tra giao diện và review.
3. Theo yêu cầu người dùng, ADV-008A đã triển khai trong lúc ADV-008 còn REVIEW; giữ nguyên code. Review trực quan rồi đóng ADV-008 trước, sau đó mới đóng ADV-008A; chưa bắt đầu ADV-009 khi ADV-008A chưa DONE. Giữ nguyên V2 và V3; ADV-007 bổ sung V4, không thay checksum migration cũ.

Nếu kiểm thử phát hiện lỗi liên quan nhiều ticket, sửa trong phạm vi an toàn và ghi ticket chịu trách nhiệm; không đổi checksum migration đã áp dụng trên database tồn tại. Không tự baseline hay xóa volume để làm lại migration.

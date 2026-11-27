# Sprint 2 — Event, Show và sơ đồ ghế

**Trạng thái:** IN_PROGRESS · [Backlog đầy đủ](../SPRINT_BACKLOG.md)

Mục tiêu cuối sprint: ADMIN tạo và publish show có layout/giá hợp lệ; khách xem show và snapshot ghế. Không triển khai hold hay booking trong sprint này.

| Thứ tự | Ticket | Điều kiện bắt đầu | Kết quả để đóng độc lập |
|---:|---|---|---|
| 1 | [ADV-005](ADV_005_EVENT_SHOW.md) | ADV-002 DONE | Event/Show, migration V2 và state transition được review; test layout port giả và fail-closed, không đòi layout/reservation thật |
| 2 | [ADV-006](ADV_006_SEAT_INVENTORY.md) | ADV-005 DONE | Layout/ShowSeat, V3, seed, test DB và test tích hợp layout/publish; chưa cần API/hold |
| 3 | ADV-007 | ADV-006 DONE và ADV-003 DONE | Cấu hình giá, API ADMIN/public, snapshot có giá, authorization/CSRF và kiểm thử; chưa cần hold |
| 4 | ADV-008 | ADV-007 DONE | React xem danh sách/show/snapshot; test loading/error và phân quyền; chưa cần booking |

## Giữ nguyên tiến trình hiện tại

ADV-005 và ADV-006 đã được triển khai chồng lấn trước khi áp dụng quy tắc tuần tự. Giữ nguyên V2, V3, code, test, seed và dữ liệu; không sửa lịch sử hay gán DONE tự động. Thứ tự xử lý:

1. Review ADV-005 theo tiêu chí độc lập mới. Kết quả mock chứng minh logic của ticket; bằng chứng layout thật hiện có là bổ sung, không phải dependency ngược. Nếu cần sửa lỗi của ADV-005, sửa và chạy test phù hợp rồi mới đánh dấu DONE.
2. Khi ADV-005 DONE, review phần ADV-006 đã có và nghiệm thu tích hợp thật. Các thay đổi vượt phạm vi không xóa; ghi rõ trong PR hoặc tách commit khi thích hợp, giữ V2 trước V3. Chỉ đánh dấu DONE khi kiểm thử/tiêu chí ADV-006 đạt.
3. Sau ADV-006 DONE, bắt đầu ADV-007, bổ sung giá/currency và cập nhật hợp đồng liên quan trước khi mở API. Sau ADV-007 DONE mới bắt đầu ADV-008.

Nếu kiểm thử phát hiện lỗi liên quan nhiều ticket, sửa trong phạm vi an toàn và ghi ticket chịu trách nhiệm; không đổi checksum migration đã áp dụng trên database tồn tại. Không tự baseline hay xóa volume để làm lại migration.

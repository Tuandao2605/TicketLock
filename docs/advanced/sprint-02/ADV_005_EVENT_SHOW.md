# ADV-005 — Event, Show và trạng thái publish

**Trạng thái:** REVIEW — nghiệm thu phạm vi Event/Show độc lập; kiểm thử reservation và layout thật thuộc ticket sau.

## Quyết định và phạm vi

- `Event` chứa tên và mô tả; `Show` tham chiếu `event_id`, thời điểm `Instant` UTC và địa điểm. V2 bổ sung hai bảng, FK và index; không sửa V1. DTO chỉ chứa dữ liệu trả về, chưa có endpoint.
- Show mới là `DRAFT`; `PUBLISHED` cần thời gian bắt đầu còn trong tương lai, địa điểm hợp lệ và layout hợp lệ. `CANCELLED` không được publish lại. Publish khóa hàng Show trong transaction và ghi `sales_opened_at`; đây là quyết định bảo thủ cho M1: publish đồng thời được coi là mở bán. Hủy không xóa mốc khóa.
- `ShowSeatLayout` là port nội bộ do Inventory triển khai; publish kiểm tra có ghế thật và đã được kiểm thử. Chưa có endpoint publish; phần HTTP thuộc ADV-007.
- ADV-006 dùng Seat bất biến và ShowSeat riêng, đồng bộ import và publish qua khóa hàng Show. Mốc `sales_opened_at` được service kiểm tra; không có trigger chặn ghi SQL trực tiếp. Các đường thay đổi layout bổ sung sau này phải giữ cùng quy tắc.
- Service hiện chỉ nội bộ; xác thực/phân quyền và controller thuộc ADV-007. Chưa có reservation nên liên kết reservation → show được triển khai và kiểm thử ở ADV-009; không khẳng định đã tồn tại.

## Tiêu chí đóng ADV-005 độc lập

- Migration V2 tạo Event/Show trên DB sạch và nâng cấp từ V1; FK, DTO, dữ liệu bắt buộc và `Instant` UTC được kiểm tra.
- Test domain/service dùng layout port giả: không publish khi layout chưa sẵn sàng, thời gian hoặc dữ liệu sai; publish thành công với layout hợp lệ giả lập; lưu mốc khóa, hủy không xóa mốc và không publish lại show đã hủy. Các kết quả giả lập chỉ kiểm tra logic ADV-005, chưa chứng minh layout thật.
- Trước khi có implementation layout thật, production phải từ chối publish nếu không kiểm chứng được layout; không cần endpoint HTTP để nghiệm thu.
- Không yêu cầu bảng reservation, thay đổi layout thật hoặc test end-to-end của ADV-006/009. Các ticket đó có nghiệm thu tích hợp riêng.

## Kiểm thử

- Bộ backend đầy đủ chạy trên PostgreSQL/Redis test riêng; migration test kiểm tra V1→V2 giữ user/refresh, V1+V2 trên DB sạch, và chạy lại không trùng.
- Test Event/Show kiểm tra thời gian tương lai, dữ liệu bắt buộc, trạng thái, fail-closed với policy thật, và mốc khóa tồn tại sau hủy trong unit test. PostgreSQL kiểm tra FK và từ chối `PUBLISHED` thiếu mốc khóa; test đọc lại entity sau clear persistence context.
- ADV-006 đã kiểm thử publish với layout và một cuộc race publish/import trên PostgreSQL; chi tiết tại [ADV-006](ADV_006_SEAT_INVENTORY.md). Reservation gắn Show vẫn chờ ADV-009.

## Công việc tiếp theo

1. Review và đóng ADV-005 theo tiêu chí độc lập phía trên, dùng bằng chứng test hiện có hoặc chạy lại nếu cần.
2. Sau khi ADV-005 DONE, review ADV-006 đã làm dở; rồi mới bắt đầu ADV-007. ADV-009 tự chịu trách nhiệm reservation gắn show.

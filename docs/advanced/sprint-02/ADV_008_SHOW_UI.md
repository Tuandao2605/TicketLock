# ADV-008 — Danh sách show và sơ đồ ghế

**Trạng thái:** REVIEW — giao diện và kiểm thử logic hoàn tất; cần review trực quan và kiểm tra cùng backend trước khi đóng ticket.

## Phạm vi

- Trang Sự kiện `/events` tải danh sách Event đã publish, phân trang, chọn Event để xem các Show đã publish; chọn Show để đọc chi tiết và snapshot ghế từ API ADV-007.
- Ghế hiển thị nhãn, trạng thái AVAILABLE/HELD/SOLD và giá tham khảo. Chỉ ghế AVAILABLE được chọn, tối đa sáu ghế; state lưu `showSeatId` của đúng show. Đổi show hoặc cập nhật snapshot xóa lựa chọn cũ.
- Có loading, danh sách rỗng, thử lại, thông báo mất kết nối, 401, 403 và tài nguyên không còn. Fetch dùng session cookie same-origin, không chuyển luồng sang JWT. API đọc là public; không thay thế authorization phía backend bằng ẩn nút trên giao diện.
- Chọn ghế chỉ là tương tác xem; không tạo reservation hay cam kết ghế còn trống. Màn hình hiển thị rõ thời điểm snapshot và giới hạn đó; hold thuộc ADV-009 trở đi.

## Kiểm thử

- `npm test` kiểm tra chọn/bỏ chọn bằng showSeatId, từ chối HELD/SOLD, giới hạn sáu ghế, fetch cùng session và phản hồi 401/403/503. Test React với jsdom và fixture kiểm tra chọn/switch show, trạng thái ghế, phản hồi trễ không ghi đè show mới, empty/403 và cảnh báo chưa giữ chỗ.
- `npm run build` thành công. Workflow CI đã được cập nhật chạy test frontend trước build; chưa có lượt chạy GitHub Actions cho thay đổi này.
- Chưa kiểm tra trực quan trên trình duyệt desktop/mobile với backend thật hoặc fixture vì browser surface không khả dụng. Test jsdom không thay thế review trực quan; cần kiểm tra để đóng ticket.

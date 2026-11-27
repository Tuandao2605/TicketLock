# ADV-008A — Trang Sự kiện và Tài khoản riêng

**Trạng thái:** REVIEW — đã triển khai theo yêu cầu người dùng khi ADV-008 còn REVIEW; chưa DONE vì cần kiểm tra trình duyệt thực và đóng ADV-008 trước.

## Đã thực hiện

- Thanh điều hướng dùng chung: `/` Khám phá, `/events` Sự kiện, `/profile` Tài khoản; `aria-current` chỉ trang hiện tại. Menu hiển thị ở mobile và dùng liên kết native có thể focus bằng bàn phím.
- Chuyển EventExplorer sang `/events`; giữ nguyên API, ghế theo showSeatId, trạng thái ghế, xử lý loading/lỗi và cảnh báo lựa chọn chưa giữ chỗ. Khi đổi suất hoặc refresh, xóa lựa chọn; phản hồi cũ sau khi đổi show không ghi đè snapshot hiện tại.
- `/profile` tái sử dụng trang hiện có, phân biệt khách chưa đăng nhập và lỗi 403; session và quyền vẫn do backend quyết định. Các đường dẫn có hoặc không dấu `/` cuối được định tuyến giống nhau.

## Bằng chứng và phần còn lại

- `npm test` với jsdom/fixture: điều hướng, `aria-current`, khả năng focus, chọn/đổi show, ghế HELD/SOLD, chặn phản hồi trễ, empty/403, profile guest/đã đăng nhập/403; test logic chọn ID và giới hạn sáu ghế.
- `npm run build` thành công. Vite và Nginx test trả 200 HTML cho deep-link `/events` và `/profile`. Nginx test dùng container riêng và đã dừng, không thao tác database thật.
- Browser surface không khả dụng, nên chưa thể kiểm tra trực quan desktop/mobile, keyboard interaction thật hoặc hoàn chỉnh end-to-end trong trình duyệt. Test DOM và HTTP không thay thế các kiểm tra này. Chưa có lượt chạy GitHub Actions của thay đổi mới.
- Sau khi kiểm tra browser, review và đóng ADV-008 trước; tiếp theo mới đóng ADV-008A theo thứ tự. Không bắt đầu ADV-009 cho tới khi hai ticket DONE.

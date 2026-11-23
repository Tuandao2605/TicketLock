# JWT cho ứng dụng mobile

Website React dùng session cookie và CSRF trên mọi kích thước màn hình. Ứng dụng mobile cài đặt sẽ dùng JWT API; dự án hiện chưa có source ứng dụng mobile, vì vậy phần tự động lưu token/refresh phía mobile chưa được triển khai. Không phân loại client bằng User-Agent.

Khởi chạy backend với JWT:

```bash
bash scripts/generate-jwt-keys.sh
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=jwt'
```

## Hợp đồng hiện tại

1. `POST /api/token/login` với JSON `{"email":"...","password":"..."}` trả access token và refresh token. Người dùng vẫn cần nhập thông tin lần đăng nhập đầu.
2. Gọi `/api/stateless/**` với `Authorization: Bearer <accessToken>`.
3. Khi cần đổi access token, `POST /api/token/refresh` với JSON `{"refreshToken":"..."}`. Lưu cặp token mới trước khi gửi refresh tiếp theo; backend xoay token mỗi lần sử dụng thành công.
4. `POST /api/token/logout` với refresh token hiện tại để thu hồi family, rồi xóa token ở client. Access token đã cấp vẫn dùng được đến hết hạn.

Mobile cần lưu refresh token trong kho bảo mật của hệ điều hành, không log token, và điều phối chỉ một refresh đang chạy cho mỗi phiên; khi gặp lỗi refresh hoặc kết quả không chắc chắn, xử lý an toàn và yêu cầu đăng nhập lại khi cần. Không tự động gửi lại refresh token cũ sau timeout: server có thể đã rotate và xem đó là reuse. Khi chạy trên thiết bị thật, cấu hình địa chỉ backend có thể truy cập từ thiết bị và dùng HTTPS; `localhost` trên điện thoại không trỏ đến máy phát triển.

JWT API chỉ bảo vệ các endpoint trong JWT chain (`/api/token/**`, `/api/stateless/**`); các endpoint dùng session như `/api/users/me` không tự chấp nhận bearer JWT. Chưa có API mobile cho toàn bộ nghiệp vụ booking/profile. JWT login hiện là API tự xây dựng, không phải OAuth authorization server đầy đủ.

Chi tiết rotate/reuse và giới hạn: [JWT lab](jwt-access-token-lab.md).

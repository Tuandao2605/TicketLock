# Ngày 11 — Dùng session và JWT trong cùng ứng dụng

## Mục tiêu

Chạy một backend TicketLock có hai cách xác thực song song và chứng minh request chỉ được xác thực bằng credential dành cho đúng API.

## Hai filter chain

| Chain | Matcher | Thứ tự | Credential | Session |
|---|---|---:|---|---|
| JWT | `/api/token/**`, `/api/stateless/**` | 1 | Login: email/password JSON; refresh/logout: refresh token JSON; API: bearer JWT | `STATELESS` |
| Session | Các đường dẫn còn lại | 2 | Cookie session và CSRF cho request thay đổi trạng thái | HTTP session |

Spring Security dùng chain đầu tiên có matcher khớp. Vì vậy các API stateless không đọc Authentication từ `JSESSIONID`; session chain cũng không giải mã `Authorization: Bearer`.

## Khởi động cùng lúc

Từ thư mục gốc:

```bash
bash scripts/generate-jwt-keys.sh
docker compose up -d --wait postgres
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=jwt'
```

Mở frontend trong terminal khác để tiếp tục login bằng session:

```bash
cd frontend
npm run dev
```

Profile `jwt` thêm chain stateless vào cùng Spring Boot application. Chain session vẫn xử lý React login, `/me`, CSRF và logout.

## Bài 1 — Xác nhận hai credential riêng biệt

1. Đăng nhập React bằng một tài khoản. `/me` phải trả `200`; browser giữ session cookie.
2. Dùng Postman gọi GET `/api/stateless/me`, chỉ gửi cookie session: phải trả `401`.
3. Gọi POST `/api/token/login` bằng JSON để lấy access token. Login JWT không cần CSRF và không tạo session cookie.
4. Gọi GET `/api/stateless/me` với `Authorization: Bearer <accessToken>`: phải trả `200`.
5. Gọi GET `/me` chỉ với bearer token, không gửi session cookie: phải trả `401`.
6. Gửi cả cookie session lẫn bearer sai/hết hạn đến `/api/stateless/me`: kết quả vẫn `401`; chain JWT không fallback sang cookie.

Trong Postman, dùng cookie jar cho session login và Bearer Token cho API JWT. Không dán token thật vào ghi chú hoặc ảnh chụp.

## Bài 2 — Phân biệt 401 và 403 trên cả hai mô hình

- GET protected chưa xác thực hoặc bearer JWT sai/hết hạn: `401`.
- POST thuộc chain session thiếu/sai CSRF có thể trả `403` trước khi tới controller, kể cả chưa đăng nhập. `permitAll()` không bỏ qua CSRF.
- Đã xác thực nhưng thiếu role: `403`.
- ADMIN gọi `/api/stateless/admin/hello` bằng JWT ADMIN: `200`.
- CUSTOMER gọi cùng endpoint: `403`.
- CUSTOMER gọi endpoint session `/api/customer/hello` bằng session: `200`; ADMIN gọi endpoint này: `403`.

Hai endpoint tương ứng kiểm tra quyền từ hai loại Authentication. Không dùng menu ẩn trên frontend làm bằng chứng authorization.

## Bài 3 — Logout độc lập

1. Giữ một session đăng nhập và một JWT token family đang hoạt động.
2. POST `/api/auth/logout` với CSRF token và cookie session. `/me` trở về `401`, nhưng JWT còn hạn vẫn gọi được `/api/stateless/me`.
3. Đăng nhập lại bằng session và xác nhận `/me` trả `200`. Login JWT lại để lấy refresh token mới; POST `/api/token/logout` với refresh token đó. Refresh tiếp theo trả `401`, nhưng access JWT đã phát hành vẫn dùng được tối đa đến `exp`.
4. Session đang đăng nhập vẫn dùng được sau JWT logout.

Đây là hai kho trạng thái tách biệt. Logout session hủy session; logout token thu hồi refresh family. Bản hiện tại chưa có denylist để thu hồi access JWT ngay lập tức.

## Code cần đọc

- `JwtSecurityConfig`: chain JWT ở `@Order(1)`, matcher, `STATELESS` và Resource Server.
- `SecurityConfig`: chain session ở `@Order(2)`, CSRF, SessionRegistry và logout.
- `JwtAccessTokenTests`: kiểm tra session không xác thực API JWT và JWT không xác thực endpoint session.
- `LoginController` và `TokenController`: hai luồng login khác nhau.

## Hoàn thành ngày 11 khi

- Cả login session và login JWT hoạt động cùng lúc với profile `jwt`.
- Cookie session không xác thực `/api/stateless/**`; bearer JWT không xác thực `/me`.
- Phân biệt được 401 với 403 trong cả hai chain.
- Giải thích được logout session và logout refresh family không thu hồi credential của mô hình còn lại.

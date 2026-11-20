# JWT access token — so sánh với session

## Mục tiêu

Login nhận access token và refresh token; access token gọi API stateless, refresh token được rotate và quản lý theo family trong PostgreSQL.

Hai SecurityFilterChain:

| Phạm vi | Thứ tự | Xác thực | CSRF |
|---|---|---|---|
| `/api/token/**`, `/api/stateless/**` | 1 | Email/password JSON khi login, refresh token JSON khi rotate/logout, bearer JWT trên API; STATELESS | Tắt vì không dùng cookie để xác thực |
| Các đường dẫn còn lại | 2 | HTTP session như trước | Bật |

Spring chọn chain khớp đầu tiên. Chain JWT không lưu Authentication vào HTTP
session; endpoint `/me` cũ vẫn dùng session. Filter lưu
session Redis luôn có thể bọc request, nhưng các endpoint JWT không chủ động tạo
session hay dựa vào session để xác thực.

## Khởi động

Từ thư mục gốc repository, tạo khóa local một lần:

```bash
bash scripts/generate-jwt-keys.sh
docker compose up -d --wait postgres
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=jwt'
```

Khóa private PKCS#8 và public X.509 nằm trong `backend/.jwt/`, được Git bỏ qua.
Script giữ lại cặp khóa hiện có. Không commit private key; cùng khóa thì token
cũ vẫn có thể xác minh sau restart, trong thời hạn của nó.

Lệnh Gradle trên đặt working directory của ứng dụng ở backend; khi Debug dùng
cấu hình có cwd backend và thêm profile jwt vào args.
Không bật jwt thì bài JWT chưa hoạt động; login React hiện vẫn dùng session.

## Bài 1 — Nhận token

Postman: POST `/api/token/login`, Body raw JSON:

```json
{
  "email": "email đã đăng ký",
  "password": "mật khẩu của tài khoản"
}
```

Content-Type là application/json. Endpoint này không yêu cầu CSRF token.
Response 200:

```json
{
  "accessToken": "chuỗi token nhận được",
  "tokenType": "Bearer",
  "expiresIn": 600,
  "refreshToken": "chuỗi ngẫu nhiên chỉ hiện khi phát hành/rotate",
  "refreshExpiresIn": 604800
}
```

Login session `/api/auth/login` vẫn yêu cầu CSRF và trả 204 như trước.
Không nhầm hai endpoint hoặc đặt bearer token cũ/sai ở request login token.
Đăng ký mới vẫn dùng API đăng ký có CSRF như bài trước.

Refresh token là opaque token ngẫu nhiên 256-bit, không phải JWT. Database chỉ lưu
SHA-256 hash, family ID, user và thời hạn. Family có hạn tuyệt đối 7 ngày từ login;
rotation không kéo dài hạn này. Giữ token trong biến local của Postman khi thực hành,
không lưu trong `localStorage`.

## Bài 2 — Gọi API bằng bearer

GET `/api/stateless/me`, chọn Authorization → Bearer Token và dán accessToken.
Kết quả 200 gồm userId, roles, expiresAt. Không cần cookie để gọi endpoint này.

Thử GET `/api/stateless/admin/hello`: ADMIN nhận 200, CUSTOMER nhận 403.
Xóa bearer token rồi thử lại: 401, kể cả đang có cookie phiên đăng nhập.
Ngược lại, chỉ gửi JWT vào `/me` cũ mà không có session cũng nhận 401.

Không lưu token thật trong ảnh, báo cáo hoặc dịch vụ decode JWT bên ngoài.
Postman token lưu ở biến local nếu cần; React chưa sử dụng token cho login.

## Bài 3 — Nội dung JWT

- `sub`: UUID user, không dùng email làm định danh chính.
- `iss`: ticketlock-local.
- `aud`: ticketlock-api.
- `iat`: thời điểm phát hành.
- `exp`: thời điểm hết hạn.
- `jti`: ID của token.
- `roles`: role được backend đọc từ database, không nhận từ client.

JWT này được ký RS256, payload không mã hóa. Không đưa mật khẩu, hash hoặc
thông tin nhạy cảm vào claims. Decode payload không chứng minh chữ ký hợp lệ.

## Bài 4 — Token không hợp lệ

- Sửa payload nhưng giữ signature: 401.
- Dùng token hết hạn: 401 sau khi tính cả clock skew được validator chấp nhận.
- Token có chữ ký hợp lệ nhưng sai issuer hoặc audience: 401.
- Token hợp lệ nhưng thiếu role: 403 ở endpoint đòi role tương ứng.
- Login sai credentials: 401; input không hợp lệ: 400.

Không thử bằng cách xóa timestamp trong payload mà giữ signature rồi kết luận
server kiểm tra timestamp: khi đó có thể bị từ chối vì chữ ký đã sai.
Các bài test dùng encoder test để ký token có claims sai với đúng khóa, qua đó
kiểm tra riêng validator.

Để thử thời hạn ngắn, tạm chỉnh `ticketlock.jwt.access-ttl=30s` trong
`application-jwt.properties`, login lại và chờ hơn thời hạn cộng clock skew.
Validator Spring Security mặc định cho phép độ lệch đồng hồ khoảng 60 giây.

## Bài 5 — Refresh rotation và logout

POST `/api/token/refresh` với Body raw JSON chứa refresh token hiện tại:

```json
{
  "refreshToken": "refresh token hiện tại"
}
```

Response 200 trả access token mới và refresh token kế tiếp. Thay giá trị refresh
token đang giữ bằng giá trị mới.

1. Gọi refresh với token mới nhất: nhận 200.
2. Gửi lại token đã dùng ở bước trước: nhận 401; backend thu hồi toàn family.
3. Thử token mới nhất vừa nhận: cũng nhận 401 vì family đã bị thu hồi.
4. Login lại để tạo family mới. Gọi POST `/api/token/logout` với refresh token hiện tại: nhận 204.
5. Thử refresh sau logout: nhận 401.

Logout/reuse chỉ thu hồi refresh family. Access JWT đã cấp vẫn có thể gọi API đến
khi hết hạn (tối đa 10 phút); bài lab chưa có access token denylist. Request refresh
và logout dùng token trong JSON body, không dùng cookie, nên CSRF filter của JWT chain
được tắt. Đây không phải cấu hình để sao chép nguyên trạng sang refresh cookie.

Không gửi hai refresh request song song từ cùng client. Backend serialize việc dùng
cùng token bằng row lock; request thứ hai thấy token đã dùng và thu hồi family, nên
client cần single-flight/mutex nếu sau này refresh tự động từ React.

### Mở nhiều tab và refresh đồng thời

Hiện React của TicketLock dùng HTTP session (`JSESSIONID`) cho đăng nhập; React chưa
gọi `/api/token/refresh`. JWT/refresh flow trong bài này được thao tác thủ công qua
Postman. Vì vậy, Web Locks, SharedWorker và BroadcastChannel chưa giải quyết lỗi nào
trong luồng đang chạy, nên chưa được thêm vào frontend.

Nếu sau này React tích hợp JWT refresh tự động, hãy điều phối một refresh tại một thời
điểm trong cùng origin bằng Web Locks hoặc SharedWorker. Khóa chỉ điều phối các tab
tuân thủ cùng cơ chế trên cùng origin; nó không đồng bộ thiết bị khác, client ngoài
browser, hoặc request đã gửi trước khi tab giữ khóa. [Web Locks API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API)
yêu cầu secure context. [BroadcastChannel](https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API)
cũng giới hạn ở cùng origin và không phải ranh giới bảo mật; tránh phát refresh token
dài hạn cho mọi tab.

Backend giữ strict rotation: token cũ dùng lại sẽ thu hồi đúng token family, không
khóa toàn bộ tài khoản. Không thêm grace period 30 giây theo mặc định: server không
thể phân biệt tab hợp lệ với kẻ đang replay cùng bearer token; trả lại token kế tiếp
cho token cũ sẽ cho phép token bị lấy cắp hoạt động thêm trong khoảng grace. OAuth
khuyến nghị rotation hoặc sender-constrained token để phát hiện replay
([RFC 9700 §4.14.2](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2));
cơ chế phía client giúp giảm request đua hợp lệ nhưng không thay thế kiểm tra replay ở server.

## Trade-off cần giải thích

- Giới hạn một HTTP session không giới hạn số access token được phát hành.
- Logout session không thu hồi JWT đã cấp.
- Đổi mật khẩu, disable tài khoản hoặc đổi role chưa tự vô hiệu hóa token cũ.
- Access token có thể tiếp tục dùng đến hết hạn theo quyền đã được ký.
- Private key bị thay làm token cũ không xác minh được; đây không phải cơ chế
  logout từng user. Key rotation là bài riêng.
- Refresh token lưu dạng hash và rotate; reuse token cũ thu hồi family.
- Logout/reuse không hủy access token đã cấp; token đó còn dùng được đến `exp`.
- React hiện vẫn dùng session login; flow JWT được thử qua Postman.
- Chưa bổ sung rate limiting; endpoint phát token cần throttling trước triển khai.

## Code cần đọc

1. `JwtSecurityConfig`: khóa, encoder/decoder, validators, ánh xạ roles và chain.
2. `AccessTokenService`: xác thực credentials và phát access token.
3. `RefreshTokenService`: tạo/hash, rotate, thu hồi family và từ chối reuse.
4. `TokenController`: hợp đồng HTTP login, refresh và logout token.
5. `RefreshToken` và `RefreshTokenRepository`: trạng thái token trong PostgreSQL và khóa hàng khi rotate.
6. `StatelessLabController`: đọc principal Jwt đã được Resource Server xác minh.
7. `JwtAccessTokenTests`: test access token và cách ly session/bearer; refresh flow cần kiểm tra thủ công theo Bài 5.

Tài liệu nền: [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

# Ngày 12 — Spring Session + Redis

## Mục tiêu

TicketLock luôn lưu HTTP session trong Redis,
giúp hai backend dùng chung trạng thái xác thực và giới hạn phiên theo tài khoản.
Không còn chế độ session trong bộ nhớ JVM. Redis phải chạy trước khi sử dụng ứng dụng.

## Kiến thức cần học

- `JSESSIONID` ở client chỉ định danh phiên; SecurityContext và thuộc tính phiên nằm
  trong Redis ở mọi profile. CSRF vẫn ở cookie `XSRF-TOKEN`, không nằm trong Redis session.
- Hai backend trỏ cùng Redis và namespace có thể đọc cùng phiên; không cần login lại
  chỉ vì request chuyển sang instance khác.
- `SpringSessionBackedSessionRegistry` tìm phiên theo principal trong Redis để phục vụ
  concurrent session control. Nó khác kho refresh token JWT trong PostgreSQL.
- Redis không tự đồng bộ state React, không push logout tới tab và không thu hồi JWT.

## Chạy từ thư mục gốc repository

```bash
docker compose up -d --wait
./backend/gradlew -p backend bootRun
```

Nếu terminal đang ở `backend/`:

```bash
./gradlew bootRun
```

Nếu tiếp tục từ ngày 11 và cần giữ JWT API hoạt động, tạo khóa một lần rồi bật profile JWT (Redis session luôn bật):

```bash
bash scripts/generate-jwt-keys.sh
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=jwt'
```

Hai lệnh bootRun trên là hai cách chạy thay thế, không chạy cùng lúc trên cổng 8080.
Chỉ chạy một backend trên cổng 8080. Frontend tiếp tục chạy bằng `npm run dev`
trong `frontend/`. Đăng nhập lại một lần sau khi chuyển từ bản cũ lưu session trong RAM.

## File liên quan

- `application.properties`: cấu hình Redis session mặc định, repository indexed, namespace và timeout.
- `application-session-lab.properties`: chỉ ghi đè thời gian idle thành một phút.
- `RedisSessionConfig`: dùng SpringSessionBackedSessionRegistry.
- `SecurityConfig`: vẫn gọi chiến lược concurrency, đổi ID, đổi CSRF, đăng ký phiên.
- `compose.yaml`: luôn khởi động Redis cùng PostgreSQL.
- `.env`: REDIS_HOST, REDIS_PORT và REDIS_PASSWORD cho local.

Spring Boot 4 dùng tiền tố `spring.session.data.redis`. Repository indexed
cần thiết cho việc tìm phiên theo principal và kiểm soát các phiên trên nhiều
instance. Tài liệu: https://docs.spring.io/spring-session/reference/4.2/configuration/redis.html

Cookie session luôn có tên JSESSIONID. Session ID có thể được mã hóa
Base64 khi lưu vào cookie bởi Spring Session; không mặc định cookie value
giống hệt ID dùng trong Redis key.

## Chuẩn bị client Postman

Dùng một tài khoản đã đăng ký. Chọn một hostname nhất quán (`localhost`), bật cookie
jar và không xóa `JSESSIONID` giữa các bước. Không kế thừa Bearer Auth từ collection
khi thử session; chọn No Auth cho các request session.

1. GET `/api/auth/csrf` để nhận cookie XSRF-TOKEN.
2. POST `/api/auth/login`, JSON `{ "email": "email đã đăng ký", "password": "mật khẩu" }`,
   header X-XSRF-TOKEN bằng cookie vừa nhận. Kết quả 204.
3. GET `/me` với cookie được Postman giữ tự động: 200 và đúng username.

Dùng pre-request script login/logout trong [ngày 6](day-06.md). Không gắn script lấy
CSRF vào request GET `/me` hoặc cả collection khi đo idle timeout. Redis session vẫn
sử dụng hợp đồng HTTP login/logout đã học, chỉ thay nơi lưu session.

## Bài 1 — Quan sát dữ liệu Redis

Đăng nhập và gọi `/me`, rồi chạy:

```bash
docker compose exec redis redis-cli ping
docker compose exec redis redis-cli --scan --pattern 'ticketlock:session:*'
```

Compose cung cấp REDISCLI_AUTH cho redis-cli, không cần gõ mật khẩu vào lệnh.
Không dùng KEYS * trên hệ thống lớn. Không chia sẻ cookie, CSRF token hoặc
nội dung SecurityContext thực trong báo cáo.

Chọn một key session từ kết quả rồi dùng Redis shell để kiểm tra TYPE và TTL:

```bash
docker compose exec redis redis-cli
```

Trong shell:

```text
TYPE <key-vừa-tìm>
TTL <key-vừa-tìm>
```

TTL có thể bao gồm khoảng đệm dọn dữ liệu của indexed repository; không dùng
TTL của một key bất kỳ làm bằng chứng duy nhất rằng phiên còn xác thực.
Kiểm tra khả năng truy cập bằng `/me` và deadline idle của session.

## Bài 2 — Session tồn tại qua restart backend

1. Login và kiểm tra `/me` trả 200.
2. Dừng riêng backend bằng Ctrl+C, giữ Redis và PostgreSQL chạy.
3. Chạy lại backend với cùng cấu hình Redis mặc định và giữ cookie browser/Postman.
4. `/me` vẫn trả 200 nếu chưa quá thời hạn không hoạt động.

Không dùng `docker compose down -v` vì thao tác đó xóa volume dữ liệu.
Redis có append-only file trong named volume; phiên vẫn có thời hạn, không
được bảo đảm sống vĩnh viễn chỉ vì có volume.

## Bài 3 — Hai backend dùng chung session

Giữ backend thứ nhất ở 8080, mở terminal khác:

```bash
./backend/gradlew -p backend bootRun --args='--server.port=8081'
```

Trong Postman, dùng cùng hostname localhost cho hai cổng và giữ cookie jar:

1. Với cookie jar A, login qua backend 8080 (quy trình CSRF và JSON như phần chuẩn bị).
2. Gọi `/me` qua 8081 với cùng cookie: 200.
3. Tạo client B độc lập: dùng browser profile riêng hoặc sao chép cookie phiên A vào
   header thủ công của request A trước khi thay phiên trong jar. Hai tab request hay
   hai collection trong cùng Postman không tự tách cookie jar cho cùng host.
   Login cùng tài khoản qua 8081 với client B. Nếu dùng jar chung và lưu cookie A,
   tắt cookie jar riêng trên request kiểm tra A để tránh Postman trộn hai JSESSIONID.
4. Phiên A gọi `/me` qua 8080 bằng đúng cookie A đã giữ: 401.
5. Phiên B gọi `/me` qua một trong hai cổng: 200.

Cookie được phân phạm vi theo hostname và path, không theo port.
Registry Redis thay cho SessionRegistryImpl giúp các backend tìm được phiên
của cùng tài khoản. Giới hạn này là chính sách ứng dụng; không coi kiểm tra rồi
đăng ký qua nhiều instance là giao dịch nguyên tử chống mọi cuộc đua login đồng thời.

## Bài 4 — Logout và idle timeout

Sau login gọi GET `/api/auth/csrf` để nhận cookie `XSRF-TOKEN` mới, rồi POST `/api/auth/logout` với cookie session, cookie CSRF và header `X-XSRF-TOKEN` tương ứng. Logout trả 204, `/me` tiếp theo trả 401.
Để kiểm chứng logout xuyên instance: đăng nhập qua 8080, xác nhận `/me` qua 8081
trả 200, lấy CSRF cookie và logout qua 8081, rồi `/me` qua 8080 phải trả 401.

Thử timeout một phút bằng profile lab thay vì sửa cấu hình mặc định:

```bash
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=session-lab'
```

Chạy lệnh này sau khi dừng backend đang chiếm cùng cổng; đăng nhập lại và lặp bài
idle timeout ngày 6, chờ ít nhất 70 giây không gửi request. Tránh polling vì request
nền có thể gia hạn idle timeout. `session-lab` đặt `spring.session.timeout=1m` cho Redis. Dừng lệnh có override
sau khi thử để lần chạy bình thường trở lại 15 phút.

Nếu Redis dừng, request cần session không hoạt động bình thường; ứng dụng không tự chuyển sang phiên trong bộ nhớ.
Khởi động lại Redis rồi kiểm tra cookie và thời hạn phiên.

## Test tự động

Test mặc định dùng Redis và PostgreSQL; không có test profile thay bằng kho session trong RAM.
Test Redis chạy tường minh với PostgreSQL và Redis đang healthy:

```bash
TICKETLOCK_REDIS_TEST=true ./backend/gradlew -p backend test --tests com.ticketlock.auth.RedisSessionTests
```

Test mở hai server ở cổng ngẫu nhiên, tạo tài khoản test, xác nhận chia sẻ phiên,
restart một server, login mới loại phiên cũ và logout xuyên instance.
Tài khoản test được xóa sau kiểm tra; namespace Redis test riêng có TTL.

## Câu hỏi tự trả lời

1. Cookie giữ gì và Redis giữ gì?
2. Vì sao session có thể sống qua restart backend?
3. Vì sao SessionRegistryImpl trong bộ nhớ chưa đủ khi có hai instance?
4. Vì sao không truyền Session ID vào request body để tự nhận diện người dùng?
5. Điều gì xảy ra nếu chỉ Redis hoặc chỉ PostgreSQL ngừng hoạt động?


## Điều kiện hoàn thành ngày 12

- [ ] Redis trả PONG và sau login có key dưới namespace `ticketlock:session`.
- [ ] `/me` vẫn trả 200 sau restart riêng backend, với cùng cookie và chưa idle quá hạn.
- [ ] Login ở 8080, cùng phiên gọi `/me` ở 8081 trả 200.
- [ ] Login tuần tự ở client B khiến client A nhận 401; không nhầm hai tab với hai phiên.
- [ ] Logout ở 8081 khiến cùng phiên không truy cập được `/me` ở 8080.
- [ ] Idle quá hạn Redis trả 401, không suy ra phiên còn hợp lệ chỉ từ TTL key.
- [ ] Giải thích được session Redis và refresh token PostgreSQL là hai cơ chế độc lập.

Ghi kết quả thực tế (status và hành vi, không ghi credential) cho từng mục. Code có
sẵn không đồng nghĩa mọi thí nghiệm đã chạy thành công trên môi trường của bạn.
Nếu đã hoàn thành toàn bộ các bước trên trước đây, chuyển sang ngày 13: ownership
booking; ownership hồ sơ đã có còn nghiệp vụ Event/Booking chưa được triển khai.

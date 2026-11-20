# Spring Session + Redis

## Mục tiêu

Session trước đây nằm trong Tomcat của một JVM. Bài này lưu session trong Redis,
giúp hai backend dùng chung trạng thái xác thực và giới hạn phiên theo tài khoản.
Mặc định ứng dụng vẫn dùng session trong bộ nhớ; bật profile `redis` để chuyển.

## Chạy từ thư mục gốc repository

```bash
docker compose --profile redis up -d --wait
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=redis'
```

Nếu terminal đang ở `backend/`:

```bash
./gradlew bootRun --args='--spring.profiles.active=redis'
```

Chỉ chạy một backend trên cổng 8080. Frontend tiếp tục chạy bằng `npm run dev`
trong `frontend/`. Đăng nhập lại khi chuyển giữa hai chế độ session.

## File liên quan

- `application.properties`: tắt auto-configuration Redis session ở chế độ mặc định.
- `application-redis.properties`: bật Redis session, repository indexed, namespace và timeout.
- `RedisSessionConfig`: dùng SpringSessionBackedSessionRegistry.
- `SecurityConfig`: vẫn gọi chiến lược concurrency, đổi ID, đổi CSRF, đăng ký phiên.
- `compose.yaml`: Redis chỉ được bật khi chọn Compose profile redis.
- `.env`: REDIS_HOST, REDIS_PORT và REDIS_PASSWORD cho local.

Spring Boot 4 dùng tiền tố `spring.session.data.redis`. Repository indexed
cần thiết cho việc tìm phiên theo principal và kiểm soát các phiên trên nhiều
instance. Tài liệu: https://docs.spring.io/spring-session/reference/4.2/configuration/redis.html

Trong profile này cookie vẫn có tên JSESSIONID. Session ID có thể được mã hóa
Base64 khi lưu vào cookie bởi Spring Session; không mặc định cookie value
giống hệt ID dùng trong Redis key.

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
3. Chạy lại backend với cùng profile redis và giữ cookie browser/Postman.
4. `/me` vẫn trả 200 nếu chưa quá thời hạn không hoạt động.

Không dùng `docker compose down -v` vì thao tác đó xóa volume dữ liệu.
Redis có append-only file trong named volume; phiên vẫn có thời hạn, không
được bảo đảm sống vĩnh viễn chỉ vì có volume.

## Bài 3 — Hai backend dùng chung session

Giữ backend thứ nhất ở 8080, mở terminal khác:

```bash
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=redis --server.port=8081'
```

Trong Postman, dùng cùng hostname localhost cho hai cổng và giữ cookie jar:

1. Login qua backend 8080.
2. Gọi `/me` qua 8081 với cùng cookie: 200.
3. Dùng cookie jar hoặc browser riêng, login cùng tài khoản qua 8081.
4. Phiên cũ gọi `/me` qua 8080: 401.
5. Phiên mới gọi `/me` qua một trong hai cổng: 200.

Cookie được phân phạm vi theo hostname và path, không theo port.
Registry Redis thay cho SessionRegistryImpl giúp các backend tìm được phiên
của cùng tài khoản. Giới hạn này là chính sách ứng dụng; không coi kiểm tra rồi
đăng ký qua nhiều instance là giao dịch nguyên tử chống mọi cuộc đua login đồng thời.

## Bài 4 — Logout và idle timeout

Sau login gọi GET `/api/auth/csrf` để nhận cookie `XSRF-TOKEN` mới, rồi POST `/api/auth/logout` với cookie session, cookie CSRF và header `X-XSRF-TOKEN` tương ứng. Logout trả 204, `/me` tiếp theo trả 401.
Ở mode Redis, chỉnh `spring.session.timeout` trong `application-redis.properties`
để thử thời hạn ngắn. Tránh polling vì request nền có thể gia hạn idle timeout.

Nếu Redis dừng, profile redis không tự chuyển sang phiên trong bộ nhớ.
Khởi động lại Redis rồi kiểm tra cookie và thời hạn phiên.

## Test tự động

Test mặc định dùng chế độ trong bộ nhớ, cần PostgreSQL như trước.
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

# TicketLock Mini

Bài JWT access token, refresh rotation và logout: xem `docs/mini/jwt-access-token-lab.md`.
Tạo khóa bằng `bash scripts/generate-jwt-keys.sh`, rồi chạy backend với profile
`jwt`. React dùng session tại `/login`; JWT API dành cho client mobile.
Đối chiếu session và JWT trên cùng ứng dụng theo `docs/mini/day-11.md`.

Bài Spring Session Redis: xem `docs/mini/redis-session-lab.md`.
Chạy `docker compose up -d --wait`, sau đó bật backend bằng
`./backend/gradlew -p backend bootRun`.
Redis là kho HTTP session duy nhất trong mọi profile. Backend cần Redis và PostgreSQL; không có fallback session trong RAM.

User ID dùng kiểu PostgreSQL `uuid`, sinh bằng Hibernate
`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` khi persist.
Không dùng `GenerationType.UUID` mặc định để suy ra version 7.

Database cũ còn ID BIGINT cần dừng backend và chạy migration trước khi chạy bản mới:

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ticketlock_app -d ticketlock < scripts/migrate-user-id-to-uuidv7.sql
```

Migration giữ tài khoản và bảng ánh xạ `user_id_migration` (old_id → new_id),
từ chối chạy nếu có khóa ngoại cần xử lý riêng. `ddl-auto=update` không thay
thế migration này. Kiểm tra kiểu cột `app_users.id` trên database của bạn trước khi quyết định có cần migration.

## Cấu trúc thư mục

```text
backend/       # Spring Boot: src, build.gradle, Gradle Wrapper
frontend/      # React + Vite
docs/mini/     # Thiết kế Mini, ghi chép và bài thực hành Security
docs/advanced/ # Sprint và backlog phát triển Advanced
scripts/       # SQL chạy thủ công
compose.yaml   # PostgreSQL local; Redis tùy chọn qua cấu hình Redis mặc định
.env           # Cấu hình local, Git bỏ qua
```

Các lệnh bên dưới chạy từ thư mục gốc repository. Backend đọc `.env` ở
thư mục gốc; khi Debug trong VS Code, chọn `Debug TicketLock Backend`.

Lab học bảo mật: [kiểm thử các lỗ hổng trên TicketLock](docs/mini/security-testing-lab.md).

Bài thực hành Spring Security và HTTP session, dùng Java 21, Spring Boot và Gradle.

Hoàn thiện ngày 6 về idle timeout, logout và vòng đời HTTP session: xem
`docs/mini/day-06.md`. Profile `session-lab` rút ngắn timeout còn một phút để thử nhanh;
cấu hình mặc định vẫn là 15 phút.

Hướng phát triển: nền tảng đặt vé cạnh tranh cao, engine giữ ghế để nghiên cứu concurrency/CPU cache và quy trình thanh toán phân tán bằng Saga, Outbox, Kafka. Xem [thiết kế nâng cao](TICKETLOCK_ADVANCED_DESIGN.md). Đây là kiến trúc dự kiến, chưa phải chức năng đã triển khai.
Sprint và ticket triển khai Advanced: [backlog](docs/advanced/SPRINT_BACKLOG.md).


Client mobile dùng JWT API khi backend bật profile `jwt`. Dự án chưa có ứng dụng mobile; hợp đồng tích hợp được ghi tại `docs/mini/mobile-jwt.md`. Website React dùng session, kể cả khi mở bằng trình duyệt điện thoại.

**Database đã tồn tại:** bản ADV-002 dùng Flyway và `validate`, không tự baseline schema cũ. Trước khi khởi động backend bản mới với database cũ, làm theo [hướng dẫn migration](docs/advanced/sprint-01/ADV_002_MIGRATION.md).

## Chạy ứng dụng

Tạo file cấu hình local nếu chưa có, rồi khởi động PostgreSQL:

```bash
cp -n .env.example .env
docker compose up -d --wait
docker compose ps
```

Database `ticketlock`, user `ticketlock_app`, cổng localhost `5432`.
Thông tin kết nối được đặt trong `.env`, không commit vào Git.
`.env.example` là bản mẫu development để người khác khởi tạo môi trường.
Docker Compose tự đọc `.env`; Spring Boot được cấu hình import file này
như Java properties khi chạy từ thư mục gốc dự án, kể cả khi Debug trong IDE.
Viết giá trị dạng `KEY=value`, không dùng `export` hoặc dấu nháy bao quanh giá trị.
Mật khẩu mẫu `ticketlock_dev_password` chỉ dùng cho bài học local.
Compose chỉ dùng mật khẩu khởi tạo khi volume còn trống;
đổi cấu hình sau đó không tự đổi mật khẩu trong database hiện có.

Xem log, mở SQL shell hoặc dừng database:

```bash
docker compose logs postgres
docker compose exec postgres psql -U ticketlock_app -d ticketlock
docker compose down
```

Dữ liệu giữ trong named volume sau khi dừng container. Không dùng `down -v`
nếu muốn giữ dữ liệu. Nếu cổng 5432 đã bị chiếm, đổi `DB_PORT` trong `.env`.
Tài khoản do image khởi tạo có quyền quản trị database;
đây là cấu hình lab, khi triển khai cần tài khoản ứng dụng ít quyền hơn.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./backend/gradlew -p backend bootRun
```

Giao diện React chạy ở cổng 5173. Mở `/login` để đăng nhập bằng email
của tài khoản trong database; không còn tài khoản `user` và mật khẩu ngẫu nhiên.
Đăng nhập thành công hiển thị thông tin tài khoản và nút đăng xuất.

### Frontend React — đăng ký CUSTOMER

Backend chạy bằng `./backend/gradlew -p backend bootRun`. Mở terminal khác:

```bash
cd frontend
npm ci
npm run dev
```

Mở frontend tại host localhost, cổng 5173, đường dẫn `/register`.
Frontend dùng React + Vite và proxy request tới backend cổng 8080.
Không mở file HTML trực tiếp hoặc dùng Live Server.

React lấy cookie `XSRF-TOKEN` qua `GET /api/auth/csrf`, đọc token ở frontend và gửi nó trong header `X-XSRF-TOKEN` cùng cookie khi gọi `POST /api/auth/register`. CSRF token không được lưu trong session; `JSESSIONID` vẫn được dùng cho phiên đăng nhập. Không tắt CSRF và không cần mở CORS cho môi trường dev này.
Backend trả 201 khi thành công, cố định role CUSTOMER, hash mật khẩu BCrypt
và lưu vào `app_users`. Không cần initializer hoặc SQL để tạo customer.

Đăng nhập dùng giao diện React tại `/login`, gửi JSON `{ email, password }` tới
`POST /api/auth/login`. Controller gọi AuthenticationManager, đổi ID của session có sẵn (hoặc tạo session khi cần lưu context), xóa CSRF cookie cũ và lưu SecurityContext vào session. Gọi lại `/api/auth/csrf` trước POST tiếp theo để nhận cookie token mới.
Đăng nhập thành công trả 204, sai thông tin trả 401. React đọc `/me` để hiển thị email.
Đăng xuất gửi POST `/api/auth/logout` với CSRF token mới; server hủy session.
Frontend build bằng `npm run build`; `dist/` chưa được tích hợp phục vụ từ backend.
Proxy chỉ dùng cho development; triển khai cần cấu hình reverse proxy cùng origin.

### Thêm tài khoản ADMIN local bằng SQL (tùy chọn)

Khởi động PostgreSQL, rồi chạy script tạo bảng và thêm tài khoản:

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ticketlock_app -d ticketlock < scripts/create-local-user.sql
```

Script chạy thủ công, không tự chạy khi ứng dụng khởi động. Mật khẩu trong
câu INSERT đã được hash BCrypt bằng PasswordEncoder của Spring Security.

Tài khoản local: `admin@ticketlock.test` / `AdminLocal123!`.
Nhập email vào giao diện React `/login` ở cổng 5173; mở `/me` qua cùng frontend sau khi đăng nhập.
Không cần bật profile dev.

Nếu email đã tồn tại, script giữ nguyên tài khoản và mật khẩu cũ.
Việc xóa initializer không xóa các tài khoản đã tạo trong database.
Các endpoint thực hành `/api/admin/**` và `/api/customer/**` đã kiểm tra role tương ứng.

## Endpoint thực hành

| Endpoint | Hành vi |
|---|---|
| `/hello` | Yêu cầu đăng nhập, trả lời chào |
| `/lab/session` | Public, tạo session nếu chưa có và tăng `visitCount` |
| `/lab/session/status` | Public, đọc session hiện có mà không tự tạo session |

Endpoint lab chỉ phục vụ học tập, cần loại bỏ trước khi triển khai thực tế.

## Kiểm tra và đóng gói

Trước khi chạy test, khởi động service test và đặt các biến trong mục [Kiểm thử CI](#kiểm-thử-ci-với-postgresql-và-redis-riêng). Gradle luôn dùng profile `ci` khi test.

```bash
./backend/gradlew -p backend clean test bootJar
```

JAR nằm trong `backend/build/libs/`, báo cáo test ở `backend/build/reports/tests/test/index.html`.
Không cần cài Gradle riêng; wrapper tải Gradle và dependency khi chạy lần đầu.

Nếu VS Code còn báo lỗi import sau khi chuyển từ Maven, chạy
**Java: Clean Java Language Server Workspace** từ Command Palette, cho phép khởi động
lại rồi import dự án Gradle. Chọn JDK 21 cho dự án.

Thiết kế và lộ trình học: [TICKETLOCK_MINI_DESIGN.md](docs/mini/TICKETLOCK_MINI_DESIGN.md).

### Kiểm tra login JSON bằng Postman

1. Gửi GET `/api/auth/csrf`; giữ cookie cùng host. Cookie jar cần có `XSRF-TOKEN`. Endpoint trả metadata `headerName` và `cookieName`; token thực tế đọc từ cookie `XSRF-TOKEN`.
2. Gửi POST `/api/auth/login`, chọn Body → raw → JSON:

```json
{
  "email": "email đã đăng ký",
  "password": "mật khẩu của bạn"
}
```

Thêm header `X-XSRF-TOKEN` với giá trị bằng cookie `XSRF-TOKEN`; Content-Type là
`application/json`. Thành công trả 204, sai credentials trả 401, đầu vào lỗi
trả 400, thiếu CSRF trả 403. Gọi GET `/me` với cookie mới để kiểm tra phiên.
Đăng nhập và logout xóa CSRF cookie cũ; gọi GET `/api/auth/csrf` để nhận cookie mới trước thao tác POST tiếp theo.
Xem [ngày 6](docs/mini/day-06.md) để lấy pre-request script cho login/logout và kiểm tra idle timeout.
Form-urlencoded không còn là hợp đồng login. Không còn trang login mặc định
ở backend; dùng giao diện React tại `/login` trên cổng frontend.

### Bài thực hành phân quyền role

| Người gọi | GET /api/admin/hello | GET /api/customer/hello |
|---|---|---|
| Chưa đăng nhập | 401 | 401 |
| CUSTOMER | 403 | 200 |
| ADMIN | 200 | 403 |

Các rule `hasRole` đặt trước `anyRequest`. ADMIN không tự kế thừa CUSTOMER.
Đăng ký mới luôn tạo CUSTOMER; tài khoản ADMIN local được tạo qua script riêng.
Khi thử bằng Postman, login JSON kèm CSRF và giữ cookie để gọi endpoint.
Các bài test phân quyền dùng danh tính giả lập; test JSON login kiểm tra riêng
luồng xác thực và lưu session.

### Đóng gói Docker

Từ thư mục gốc, build hai image:

```bash
docker build -t ticketlock-backend:local ./backend
docker build -t ticketlock-frontend:local ./frontend
```

Image frontend phục vụ React bằng Nginx và chuyển `/api`, `/me`, `/hello`
và các endpoint lab tới `http://backend:8080`. Hai container cần chạy trên cùng
mạng Docker và backend cần có tên mạng `backend`; cấu hình Compose và biến môi
trường cho server được bổ sung ở bước tiếp theo. Nginx trả `index.html` cho các
đường dẫn trang React khi tải lại. Không đưa `.env` hoặc khóa JWT vào build context.

### Demo trên laptop với Cloudflare Quick Tunnel

Môi trường này dùng database và volume riêng, không dùng dữ liệu của `compose.yaml`.
Chỉ dùng dữ liệu giả: ứng dụng còn endpoint lab và cấu hình phát triển. Quick Tunnel
công khai ứng dụng cho người có URL; không dùng làm production. Không cần domain hay
tài khoản Cloudflare. URL thay đổi khi tạo lại tunnel.

Tạo `.env.demo` từ `.env.demo.example`, thay cả hai mật khẩu bằng giá trị ngẫu nhiên
khác nhau, rồi chạy từ thư mục gốc:

```bash
cp -n .env.demo.example .env.demo
# Sửa .env.demo bằng trình soạn thảo trước khi chạy.
docker compose --env-file .env.demo -f compose.demo.yaml up -d --build
```

### Kết nối DataGrip với PostgreSQL demo

Chạy từ thư mục gốc để thêm cổng chỉ truy cập từ máy local:

```bash
docker compose --env-file .env.demo -f compose.demo.yaml -f compose.demo.datagrip.yaml up -d --no-deps postgres
```

Lệnh tạo lại container PostgreSQL và có thể gián đoạn kết nối ngắn; named volume `demo_postgres` được giữ. Trong DataGrip nhập host `localhost`, port `5433`, database `ticketlock`, user `ticketlock_app`, password là `DEMO_DB_PASSWORD` trong `.env.demo`. Không chia sẻ mật khẩu. Chọn **Test Connection**.

Để giữ cấu hình cổng trong các lần chạy Compose sau, luôn dùng cả hai `-f compose.demo.yaml -f compose.demo.datagrip.yaml`. Nếu cổng 5433 bận, đặt `DEMO_DB_LOCAL_PORT` trong `.env.demo` rồi chạy lại lệnh trên và dùng cổng tương ứng. Đổi mật khẩu `.env.demo` sau khi volume được tạo không tự đổi password PostgreSQL. Cổng 127.0.0.1 chỉ dành cho DataGrip trên cùng máy; Cloudflare Tunnel không cung cấp kết nối PostgreSQL.

Mở `http://localhost:8088` và kiểm tra đăng ký/đăng nhập. Để mở URL công khai:

```bash
docker compose --env-file .env.demo -f compose.demo.yaml --profile tunnel up -d tunnel
docker compose --env-file .env.demo -f compose.demo.yaml logs tunnel
```

Mở địa chỉ `https://...trycloudflare.com` trong log. Dừng toàn bộ bằng:

```bash
docker compose --env-file .env.demo -f compose.demo.yaml --profile tunnel down
```

Lệnh này giữ dữ liệu demo. Laptop tắt/ngủ hoặc Docker dừng sẽ ngắt dịch vụ. Không
chạy cấu hình này cùng backend local nếu cả hai dùng cùng cổng; frontend demo dùng
cổng 8088. JWT chưa bật trong demo; `/login` dùng session. Không đổi mật khẩu DB
sau khi đã tạo volume mà không cập nhật user trong PostgreSQL.

### Kiểm thử CI với PostgreSQL và Redis riêng

Cần Docker Engine + Compose, Java 21, Node 22 và npm. Dịch vụ test dùng cổng
localhost `15432`/`16380` và dữ liệu trong tmpfs; không dùng volume hoặc dữ liệu
của ứng dụng. Kiểm tra hai cổng chưa bị sử dụng. Từ thư mục gốc:

```bash
docker compose -f compose.test.yaml up -d --wait
export SPRING_PROFILES_INCLUDE=ci
export TICKETLOCK_TEST_DB_HOST=127.0.0.1 TICKETLOCK_TEST_DB_PORT=15432
export TICKETLOCK_TEST_DB_NAME=ticketlock_test TICKETLOCK_TEST_DB_USER=ticketlock_test
export TICKETLOCK_TEST_DB_PASSWORD=ticketlock_test_password
export TICKETLOCK_TEST_REDIS_HOST=127.0.0.1 TICKETLOCK_TEST_REDIS_PORT=16380
export TICKETLOCK_TEST_REDIS_PASSWORD=ticketlock_test_password
export TICKETLOCK_REDIS_TEST=true
./backend/gradlew -p backend test
npm --prefix frontend ci
npm --prefix frontend run build
```

Đợi `up --wait` báo healthy trước khi chạy test. Profile `ci` yêu cầu đầy đủ các
biến `TICKETLOCK_TEST_*`; chỉ kết nối service test độc lập. Test JWT tự tạo khóa
tạm; RedisSessionTests chạy khi `TICKETLOCK_REDIS_TEST=true`. Flyway được chạy
trên DB test rỗng, Hibernate validate; test migration còn kiểm tra baseline fixture,
chạy lặp và một migration cố tình sai trong schema test riêng.

Báo cáo backend ở `backend/build/reports/tests/test/index.html` và XML trong
`backend/build/test-results/test/`. GitHub Actions upload báo cáo khi job kết thúc.
Khi test xong, dừng các service test bằng `docker compose -f compose.test.yaml down`;
dữ liệu test trong tmpfs sẽ mất. Không dùng các lệnh này với `compose.yaml` hoặc
`compose.demo.yaml` vì đó là dữ liệu ứng dụng.

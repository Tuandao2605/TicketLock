# TicketLock Mini

User ID dùng kiểu PostgreSQL `uuid`, sinh bằng Hibernate
`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` khi persist.
Không dùng `GenerationType.UUID` mặc định để suy ra version 7.

Database cũ còn ID BIGINT cần dừng backend và chạy migration trước khi chạy bản mới:

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ticketlock_app -d ticketlock < scripts/migrate-user-id-to-uuidv7.sql
```

Migration giữ tài khoản và bảng ánh xạ `user_id_migration` (old_id → new_id),
từ chối chạy nếu có khóa ngoại cần xử lý riêng. `ddl-auto=update` không thay
thế migration này. Database local hiện tại đã được chuyển đổi.

## Cấu trúc thư mục

```text
backend/       # Spring Boot: src, build.gradle, Gradle Wrapper
frontend/      # React + Vite
docs/          # Ghi chép và bài thực hành
scripts/       # SQL chạy thủ công
compose.yaml   # PostgreSQL local
.env           # Cấu hình local, Git bỏ qua
```

Các lệnh bên dưới chạy từ thư mục gốc repository. Backend đọc `.env` ở
thư mục gốc; khi Debug trong VS Code, chọn `Debug TicketLock Backend`.

Lab học bảo mật: [kiểm thử các lỗ hổng trên TicketLock](docs/security-testing-lab.md).

Bài thực hành Spring Security và HTTP session, dùng Java 21, Spring Boot và Gradle.

Hướng phát triển: nền tảng đặt vé cạnh tranh cao, engine giữ ghế để nghiên cứu concurrency/CPU cache và quy trình thanh toán phân tán bằng Saga, Outbox, Kafka. Xem [thiết kế nâng cao](TICKETLOCK_ADVANCED_DESIGN.md). Đây là kiến trúc dự kiến, chưa phải chức năng đã triển khai.

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

React lấy CSRF token qua `GET /api/auth/csrf`, gửi token và cookie cùng
`POST /api/auth/register`. Không tắt CSRF và không cần mở CORS cho môi trường dev này.
Backend trả 201 khi thành công, cố định role CUSTOMER, hash mật khẩu BCrypt
và lưu vào `app_users`. Không cần initializer hoặc SQL để tạo customer.

Đăng nhập dùng giao diện React tại `/login`, gửi JSON `{ email, password }` tới
`POST /api/auth/login`. Controller gọi AuthenticationManager, đổi Session ID, làm mới CSRF và lưu SecurityContext vào session; đăng nhập
thành công trả 204, sai thông tin trả 401. React đọc `/me` để hiển thị email.
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
Nhập email vào ô Username trên form `/login`; mở `/me` sau khi đăng nhập.
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

```bash
./backend/gradlew -p backend clean test bootJar
```

JAR nằm trong `backend/build/libs/`, báo cáo test ở `backend/build/reports/tests/test/index.html`.
Không cần cài Gradle riêng; wrapper tải Gradle và dependency khi chạy lần đầu.

Nếu VS Code còn báo lỗi import sau khi chuyển từ Maven, chạy
**Java: Clean Java Language Server Workspace** từ Command Palette, cho phép khởi động
lại rồi import dự án Gradle. Chọn JDK 21 cho dự án.

Thiết kế và lộ trình học: [TICKETLOCK_MINI_DESIGN.md](TICKETLOCK_MINI_DESIGN.md).

### Kiểm tra login JSON bằng Postman

1. Gửi GET `/api/auth/csrf`, giữ cookie cùng host và lấy `token`, `headerName`.
2. Gửi POST `/api/auth/login`, chọn Body → raw → JSON:

```json
{
  "email": "email đã đăng ký",
  "password": "mật khẩu của bạn"
}
```

Thêm header có tên bằng `headerName`, giá trị bằng `token`; Content-Type là
`application/json`. Thành công trả 204, sai credentials trả 401, đầu vào lỗi
trả 400, thiếu CSRF trả 403. Gọi GET `/me` với cookie mới để kiểm tra phiên.
Lấy lại CSRF token sau đăng nhập trước khi POST logout hoặc thao tác khác.
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

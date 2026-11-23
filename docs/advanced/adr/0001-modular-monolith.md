# ADR 0001 — Modular monolith và ranh giới dữ liệu

**Trạng thái:** Đề xuất để review · **Phạm vi:** milestone M1 (Sprint 1–4)

## Bối cảnh

TicketLock hiện là một ứng dụng Spring Boot. PostgreSQL lưu tài khoản và refresh token; Redis lưu HTTP session. React dùng session tại `/login`; JWT API dành cho client mobile và chỉ bật với profile `jwt`. Event, Inventory và Booking chưa được triển khai. M1 cần kiểm chứng tính đúng đắn khi nhiều người tranh ghế trước khi thêm hàng đợi hay tách service.

## Kiểm kê code hiện tại của ADV-001

| Thành phần | Hiện có trong repository |
|---|---|
| `auth` | JSON session login/đăng ký/CSRF; JWT login, refresh rotation và logout khi bật `jwt` |
| `user` | `User`, `Role`, repository, DTO/mapper, service và API hồ sơ `/api/users/me`, `/api/users/{userId}` |
| `security` | SecurityFilterChain, user details, method security và Redis session registry |
| Controller lab | `/hello`, `/public/hello`, `/me`, `/blocked`, `/lab/session`, `/lab/session/status`, role/method-security lab |
| PostgreSQL theo entity | `app_users`, `refresh_tokens`; schema thực tế và các bảng phụ/migration lịch sử cần kiểm kê riêng ở ADV-002 |
| Redis | HTTP session và index principal; không chứa booking/reservation |
| Frontend | React dùng session; app mobile chưa có, JWT API dùng profile `jwt` |

`SecurityConfig` ở package gốc. Hiện `RefreshTokenService` truy cập `UserRepository` trực tiếp và `UserSecurity` cũng dùng repository; các rule boundary dưới đây là **quy tắc cho code mới và mục tiêu refactor**, không khẳng định code cũ đã tuân thủ. ADV-001 chưa refactor code.

## Quyết định

M1 dùng một ứng dụng, một lần deploy và một PostgreSQL. Tổ chức code theo module nghiệp vụ; mỗi module sở hữu dữ liệu và công bố service/interface cho module khác. Controller gọi service của module mình; không gọi repository của module khác. Không tạo dependency vòng giữa các module. Package cụ thể được tạo khi triển khai nghiệp vụ, không tạo package rỗng để minh họa.

```mermaid
flowchart LR
  Client[React / API client] --> Security[Spring Security]
  subgraph App[Spring Boot — một deploy]
    Security --> Auth[Auth / User]
    Security --> Event[Event]
    Security --> Inventory[Inventory]
    Security --> Booking[Booking]
    Booking --> Inventory
    Inventory --> Event
  end
  Auth --> PG[(PostgreSQL)]
  Event --> PG
  Inventory --> PG
  Booking --> PG
  Security --> Redis[(Redis: HTTP session)]
```

Mũi tên giữa module thể hiện lời gọi qua interface được công bố, không cho phép truy cập repository/bảng trực tiếp. Payment và Ticket là ranh giới dự kiến cho giai đoạn sau, chưa có trong M1. Kafka, service tách riêng và engine single-writer cũng chưa tồn tại trong hiện trạng.

## Quyền sở hữu

| Module | Dữ liệu và trách nhiệm | Interface dự kiến |
|---|---|---|
| Auth / User | User, role, password hash, refresh token; xác thực danh tính | Cung cấp danh tính đã xác thực và tra cứu user cần thiết |
| Event | Event, Show, layout Seat; quản lý publish | Kiểm tra show có thể đặt và lấy danh sách ghế trong layout |
| Inventory | ShowSeat, trạng thái ghế theo show, reservation, deadline, version | Hold, đọc, release, confirm reservation |
| Booking | Booking và liên kết reservation | Tạo, đọc booking theo owner; gọi Inventory qua interface |
| Payment (sau M1) | Payment operation và kết quả thanh toán | Charge, query, refund |
| Ticket (sau M1) | Vé và check-in | Phát vé, tra cứu, check-in |

Event mô tả sự kiện; Show là một suất diễn cụ thể. Seat thuộc layout của Event; ShowSeat là trạng thái của một ghế trong một Show. Một layout có thể phục vụ nhiều show mà trạng thái ghế độc lập. Giá và điều kiện thay đổi layout cần được chốt trước khi các ticket schema tương ứng được triển khai.

## Trust boundary

- Request body, UUID, role, giá và owner do client gửi đều là đầu vào chưa tin cậy. Server lấy principal từ `Authentication` đã xác thực; quyền và ownership được kiểm tra phía server.
- Session chain sử dụng session cookie và CSRF cho các thao tác thay đổi. Với profile `jwt`, `/api/token/**` và `/api/stateless/**` dùng chain stateless riêng; không mặc định mọi `/api/**` nhận bearer JWT.
- Hiện tại session principal có tên là email; JWT `sub` là UUID. Khi kết nối JWT tới nghiệp vụ mới phải chuyển cả hai sang một định danh user nội bộ đáng tin cậy, không giả định hai principal có cùng định dạng.
- Redis lưu session, không sở hữu reservation. Logout hoặc hết session không tự hủy hold hay booking.

## Transaction và invariant M1

- PostgreSQL là nguồn sự thật cho trạng thái ghế. Một show-seat có tối đa một hold hợp lệ tại một thời điểm.
- Hold nhiều ghế thành công toàn bộ hoặc không ghế nào. Lock/conditional update và thay đổi reservation nằm trong transaction DB; phản hồi thành công chỉ sau commit.
- Hold hợp lệ khi `now < deadline` theo đồng hồ server. Confirm, release và expire kiểm tra reservation, version và trạng thái phù hợp; confirm sau deadline bị từ chối ngay cả khi worker chưa dọn. Lệnh cũ không được giải phóng hold mới.
- M1 có thể dùng local transaction trên cùng DB; interface module không có nghĩa transaction tự động bao trùm các service/DB sau khi tách. Khi tách phải thiết kế protocol nhất quán riêng.

Ví dụ: customer gửi danh sách seat ID để hold. Security xác thực principal; application service chuyển danh tính đã xác thực sang user ID nội bộ đáng tin cậy. Inventory nhận user ID từ service nội bộ, kiểm tra show, ghế và điều kiện hold, rồi khóa hoặc cập nhật các ghế trong transaction. Nếu một ghế không hợp lệ hoặc đã bị giữ, transaction rollback toàn bộ. Server chỉ trả thành công sau commit. Release/confirm kiểm tra owner theo policy và reservation/version/deadline tại server.

## Phương án cân nhắc và hệ quả

- **Tách microservice ngay:** độc lập deploy nhưng thêm network, message và consistency trước khi có baseline đúng. Chưa chọn cho M1.
- **Một monolith không có ownership:** dễ bắt đầu nhưng dễ hình thành truy cập bảng chéo và dependency vòng. Không chọn.
- **Modular monolith:** đơn giản hóa transaction và kiểm thử; cần review dependency và ownership vì cùng process/DB không tự ngăn truy cập chéo.

## Review và việc cần theo dõi

- Client đổi role trong body không thay quyền: quyền đến từ principal đã xác thực.
- Hai request tranh cùng ghế không được cùng tạo hold hợp lệ; phải kiểm thử trên PostgreSQL thật ở ADV-012.
- Một ghế lỗi trong nhóm phải rollback cả nhóm; kiểm thử ở ADV-009/ADV-012.
- Định danh principal thống nhất, giá snapshot và chính sách thay đổi layout cần được cụ thể hóa trong API/schema ticket trước khi code. ADR này là quyết định thiết kế, chưa phải bằng chứng các invariant đã được triển khai.

## Ba tình huống review thiết kế

| Tình huống | Quyết định cần được thực thi ở ticket sau | Bằng chứng dự kiến |
|---|---|---|
| Client gửi role ADMIN/ownerId của người khác | Bỏ qua vai trò, owner do client tự khai; dùng Authentication và kiểm tra quyền/ownership phía server. | ADV-003 định nghĩa contract; ADV-012 kiểm thử quyền. |
| Hai request giữ cùng một ghế | DB là nguồn sự thật; transaction khóa/cập nhật có điều kiện để chỉ một hold hợp lệ thắng. | ADV-009 và ADV-012 kiểm thử đồng thời trên PostgreSQL. |
| Một ghế trong nhóm đã bị giữ | Rollback cả transaction; không lưu hold thành công một phần. | ADV-009 và ADV-012 kiểm tra trạng thái toàn bộ nhóm. |

Đây là review thiết kế, chưa chứng minh bằng test nghiệp vụ vì các module giữ ghế chưa tồn tại. Các điểm cần quyết định trước implementation: cách ánh xạ session email/JWT UUID sang user ID, snapshot giá và quy tắc đổi layout đã được ghi ở trên.

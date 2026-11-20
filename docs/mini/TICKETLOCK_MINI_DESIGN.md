# TicketLock Mini — Thiết kế đồ án học Spring Security trong 14 ngày

Phần thực hành bổ sung: [Lab kiểm thử bảo mật trên chính TicketLock](security-testing-lab.md), gồm session, CSRF, phân quyền, ownership, injection và JWT; thực hiện khi từng chức năng tương ứng đã có.

Đây là phạm vi học Security trong 14 ngày. Hướng phát triển engine giữ ghế, CPU cache và Saga/Outbox/Kafka được mô tả trong [thiết kế nâng cao](../../TICKETLOCK_ADVANCED_DESIGN.md), triển khai sau giai đoạn Mini.

## 1. Mục tiêu

Xây dựng ứng dụng quản lý sự kiện và đặt vé đơn giản bằng Java + Spring Boot để thực hành:

- Authentication: xác thực người dùng.
- Authorization: phân quyền theo vai trò và quyền sở hữu dữ liệu.
- Session management: vòng đời phiên đăng nhập, timeout, logout và giới hạn phiên.
- JWT: access token, refresh token và rotation.
- HTTP security: CSRF, CORS, cookie và xử lý lỗi xác thực.

Ứng dụng hiện có hai luồng xác thực độc lập:

- **React (ADMIN/CUSTOMER):** login JSON tại `/api/auth/login`, duy trì trạng thái bằng HTTP session và cookie CSRF. Backend đã tắt form login.
- **JWT lab, profile `jwt`:** `/api/token/login` trả access/refresh token; `/api/stateless/**` dùng bearer JWT. React chưa dùng luồng này.

Phần Event/Booking và portal quản trị chuyên biệt bên dưới là mục tiêu tiếp theo, chưa được triển khai. Hiện ownership thực hành trên hồ sơ `/api/users/{userId}`. Thiết kế nâng cao là kế hoạch riêng, không phải hiện trạng.

Mục tiêu sau 14 ngày là tự triển khai, debug và giải thích được hai luồng này. Nghiệp vụ đặt vé chỉ đóng vai trò tạo tình huống thực hành bảo mật.

## 2. Phạm vi chức năng

### Admin

- Đăng nhập, đăng xuất trên giao diện web.
- Xem thông tin tài khoản hiện tại.
- Tạo, sửa và xem danh sách sự kiện.
- Xem danh sách booking.
- Session hết hạn khi không hoạt động.
- Giới hạn một phiên đăng nhập cho mỗi tài khoản.

### Customer

- Đăng nhập bằng email và mật khẩu qua API.
- Nhận access token và refresh token.
- Xem sự kiện đang mở bán.
- Đặt vé cho một sự kiện.
- Xem và hủy booking của chính mình.
- Làm mới access token và đăng xuất.

### Khách chưa đăng nhập

- Xem danh sách, chi tiết sự kiện đang mở bán.
- Truy cập trang đăng nhập admin.

### Ngoài phạm vi

Chưa triển khai thanh toán thật, chọn ghế, giữ ghế bằng Redis, OAuth2, MFA, scanner, WebSocket và microservices.

## 3. Công nghệ và kiến trúc

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 21 |
| Backend | Spring Boot |
| Bảo mật | Spring Security |
| Truy cập dữ liệu | Spring Data JPA |
| Database | PostgreSQL |
| UI hiện tại | React + Vite; Thymeleaf còn là dependency nhưng không phục vụ form login |
| Validation | Jakarta Bean Validation |
| Session phân tán | Spring Session + Redis bắt buộc, mặc định trong mọi profile |
| Công cụ kiểm tra | Browser DevTools, Postman hoặc curl |
| Build | Gradle (Groovy DSL) |

Chọn bản Spring Boot ổn định có hỗ trợ Java 21 khi khởi tạo. Để Spring Boot quản lý phiên bản các thư viện Spring đi kèm.

Ứng dụng là một backend duy nhất:

```text
Browser React ─ JSON + Cookie ──┐
                                │
                                ▼
                       Spring Boot Application
                                ▲
Customer/Postman ── Bearer JWT ──┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
                PostgreSQL                Redis
            User, Event, Booking      HTTP session
                                     (mặc định)
```

HTTP session luôn lưu Redis; không còn cấu hình session trong RAM. Tomcat vẫn là servlet container.

## 4. Mô hình dữ liệu

### User

| Trường | Ý nghĩa |
|---|---|
| `id` | Khóa chính |
| `email` | Duy nhất, dùng đăng nhập |
| `passwordHash` | Mật khẩu hash bằng BCrypt |
| `role` | `ADMIN` hoặc `CUSTOMER` |
| `enabled` | Cho phép hoặc vô hiệu hóa tài khoản |
| `createdAt` | Ngày tạo |

User hiện dùng UUIDv7 do Hibernate sinh và PostgreSQL lưu kiểu uuid. Mỗi người dùng chỉ có một role. Không trả `passwordHash` trong response và không ghi mật khẩu vào log. Tạo sẵn một admin và hai customer trong môi trường development để thử phân quyền và ownership.

### Event — dự kiến, chưa có entity/endpoint

| Trường | Ý nghĩa |
|---|---|
| `id` | Khóa chính |
| `name` | Tên sự kiện |
| `description` | Mô tả |
| `location` | Địa điểm |
| `startsAt` | Thời gian diễn ra |
| `status` | `DRAFT`, `PUBLISHED`, `CANCELLED` |
| `createdBy` | Admin tạo sự kiện |
| `createdAt` | Ngày tạo |

Trong bản mini, vé chưa có ghế, giá tiền hoặc giới hạn số lượng. Mỗi booking đại diện cho một vé. Mọi admin được quản lý mọi sự kiện; ownership được thực hành trên booking của customer.

### Booking — dự kiến, chưa có entity/endpoint

| Trường | Ý nghĩa |
|---|---|
| `id` | Khóa chính |
| `customerId` | Khách đặt vé |
| `eventId` | Sự kiện được đặt |
| `status` | `CONFIRMED` hoặc `CANCELLED` |
| `createdAt` | Thời gian đặt |

Quy tắc nghiệp vụ:

- Chỉ đặt sự kiện `PUBLISHED` và chưa bắt đầu.
- Customer chỉ xem và hủy booking của mình.
- Chỉ hủy booking trước khi sự kiện bắt đầu.
- Hủy lại booking đã hủy không phát sinh thay đổi mới.
- Mỗi request tạo booking hợp lệ tạo một booking; chưa xử lý chống gửi lặp trong phạm vi này.

### RefreshToken

Đã triển khai trong profile `jwt`; `tokenHash` chính là khóa chính, không có cột `id` riêng.

| Trường | Ý nghĩa |
|---|---|
| `userId` | Chủ token |
| `tokenHash` | Hash SHA-256 của refresh token, khóa chính |
| `familyId` | Nhóm token thuộc cùng một lần đăng nhập |
| `expiresAt` | Hạn sử dụng |
| `usedAt` | Thời điểm token được đổi |
| `revokedAt` | Thời điểm bị thu hồi |

Refresh token là chuỗi ngẫu nhiên, chỉ lưu hash trong database. Khi refresh thành công, token cũ được đánh dấu đã dùng và thay bằng token mới trong cùng transaction. Nếu token đã dùng bị gửi lại, thu hồi cả family. Mỗi lần đăng nhập mới tạo một family riêng.

## 5. Endpoint

### React/session — endpoint đang chạy

| Method | Endpoint | Hành vi |
|---|---|---|
| GET | `/api/auth/csrf` | Public, cấp cookie XSRF-TOKEN và metadata JSON |
| POST | `/api/auth/register` | Public + CSRF, đăng ký CUSTOMER, trả 201 |
| POST | `/api/auth/login` | Public + CSRF, JSON email/password, trả 204 |
| POST | `/api/auth/logout` | CSRF, hủy phiên và xóa cookie, trả 204 |
| GET | `/me` | Đã đăng nhập, trả username/authorities |
| GET | `/api/users/me` | Hồ sơ tài khoản từ DB |
| GET | `/api/users/{userId}` | Chỉ được đọc chính mình; sai owner trả 403 |
| GET | `/api/admin/hello` | ADMIN |
| GET | `/api/customer/hello` | CUSTOMER |

### Admin Portal — đề xuất ban đầu, chưa triển khai

Các URL `/admin/**` dưới đây chưa có controller và chưa có rule ADMIN riêng; không dùng chúng làm hướng dẫn chạy hiện tại.

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| GET | `/admin/login` | Public | Trang đăng nhập |
| POST | `/admin/login` | Public, có CSRF token | Xử lý đăng nhập |
| POST | `/admin/logout` | Đã đăng nhập, có CSRF token | Đăng xuất |
| GET | `/admin` | ADMIN | Trang chính |
| GET | `/admin/me` | ADMIN | Thông tin người dùng hiện tại |
| GET | `/admin/events` | ADMIN | Danh sách sự kiện |
| POST | `/admin/events` | ADMIN | Tạo sự kiện |
| POST | `/admin/events/{id}/update` | ADMIN | Sửa sự kiện |
| GET | `/admin/bookings` | ADMIN | Xem booking |

Hiện tại JSON login do `LoginController` gọi `AuthenticationManager` và session strategy rồi lưu context. Logout do Spring Security xử lý. CSRF cookie được đọc và gửi qua header `X-XSRF-TOKEN`.

### JWT lab và Customer API dự kiến

Ba endpoint `/api/token/*` đã có khi bật profile `jwt`. Endpoint hiện có để đọc JWT là GET `/api/stateless/me` và `/api/stateless/admin/hello`. Các URL event/booking và `/api/me` trong bảng là dự kiến, chưa có mapping.

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/token/login` | Public | Nhận access và refresh token |
| POST | `/api/token/refresh` | Public; refresh token hợp lệ trong JSON body | Đổi token |
| POST | `/api/token/logout` | Public; refresh token trong JSON body | Thu hồi refresh token family |
| GET | `/api/events` | Public | Xem sự kiện mở bán |
| GET | `/api/events/{id}` | Public | Chi tiết sự kiện mở bán |
| GET | `/api/me` | CUSTOMER | Thông tin tài khoản |
| POST | `/api/bookings` | CUSTOMER | Đặt vé |
| GET | `/api/bookings` | CUSTOMER | Booking của bản thân |
| GET | `/api/bookings/{id}` | CUSTOMER, đúng chủ sở hữu | Chi tiết booking |
| POST | `/api/bookings/{id}/cancel` | CUSTOMER, đúng chủ sở hữu | Hủy booking |

Request tạo booking:

```json
{
  "eventId": 10
}
```

Không nhận `customerId` từ client. Backend lấy người đặt từ danh tính đã được xác thực.

Trong bản mini, refresh token truyền trong JSON body và kiểm tra bằng Postman. Endpoint refresh/logout không yêu cầu access token còn hạn; chúng kiểm tra refresh token. Nếu sau này chuyển token sang cookie cho frontend trình duyệt, cần đánh giá lại CSRF.

## 6. Thiết kế bảo mật

### Filter chain

| Chain | Phạm vi | Authentication | Session | CSRF |
|---|---|---|---|---|
| JWT, @Order(1), profile jwt | `/api/token/**`, `/api/stateless/**` | JSON credentials/refresh hoặc Bearer JWT tùy endpoint | `STATELESS` | Tắt vì không dùng cookie xác thực |
| Session, @Order(2) | Mọi request còn lại | JSON login + cookie session | HTTP session khi cần | CookieCsrfTokenRepository |

Yêu cầu:

- Cấu hình thứ tự bằng `@Order` và phạm vi bằng `securityMatcher`.
- Session chain hiện có `anyRequest().authenticated()`, chưa phải deny-by-default. Đường dẫn chưa mapping sau khi xác thực thường trả 404.
- Cookie admin không xác thực Customer API.
- JWT không thay thế session của Admin Portal.
- Session entry point trả 401 thay vì redirect. Chưa chuẩn hóa JSON cho mọi lỗi: một số response 401/403 có thể không có body.
- Dùng OAuth2 Resource Server của Spring Security để kiểm tra JWT. Chưa tự viết JWT authentication filter.
- `AccessTokenService` phát access JWT, `RefreshTokenService` quản lý rotation; Resource Server xác minh bearer token.
- Chuẩn hóa authority: ví dụ `ROLE_ADMIN`, `ROLE_CUSTOMER`, và ánh xạ claim JWT tương ứng để `hasRole` hoạt động đúng.

### Luồng session login hiện tại (ADMIN hoặc CUSTOMER)

```text
1. Browser mở trang login.
2. Người dùng gửi email, password và CSRF token.
3. Spring Security tải User từ database.
4. PasswordEncoder kiểm tra mật khẩu.
5. Xác thực thành công: đổi ID nếu đã có session; tạo phiên khi cần lưu context nếu chưa có.
6. SecurityContext được duy trì qua HTTP session.
7. Browser gửi cookie session trong request tiếp theo.
8. Logout làm session mất hiệu lực và xóa cookie tương ứng.
```

Cấu hình học tập:

- Timeout: 15 phút không hoạt động.
- Giới hạn một phiên cho mỗi tài khoản.
- Khi login mới thành công, phiên cũ mất quyền truy cập ở request tiếp theo.
- Cookie session có `HttpOnly`; bật `Secure` khi dùng HTTPS và cấu hình `SameSite` phù hợp.
- Quan sát tên cookie thực tế: servlet session thường dùng `JSESSIONID`, Spring Session có thể dùng tên khác.
- Registry hiện luôn là `SpringSessionBackedSessionRegistry`, dùng cùng Redis repository với session.

### Luồng đăng nhập customer

```text
1. Client gửi email và password.
2. Backend xác thực tài khoản.
3. Backend trả access token và refresh token.
4. Client gọi API với Authorization: Bearer <access-token>.
5. Backend kiểm tra chữ ký, thời hạn, issuer và audience của JWT.
6. Access token hết hạn: client gọi endpoint refresh.
7. Refresh token cũ được đánh dấu đã dùng và thay bằng token mới.
```

Thông số bài học:

- Access token: 10 phút.
- Refresh token: 7 ngày; family hết hạn tối đa 7 ngày kể từ lần đăng nhập, không kéo dài vô hạn qua rotation.
- JWT chứa `sub` là User ID, role, `iat`, `exp`, `iss`, `aud`.
- Cấu hình khóa ký và thuật toán xác minh rõ ràng; khóa lấy từ môi trường, không commit vào repository.
- Logout thu hồi refresh token family. Access token đã phát hành vẫn có hiệu lực đến khi hết hạn; chưa thêm blacklist.
- Thay đổi role hoặc vô hiệu hóa tài khoản chưa tự thu hồi access token đã phát hành. Refresh phải kiểm tra lại trạng thái tài khoản và role.

## 7. Phân quyền và lỗi

Phân quyền ở hai tầng:

- HTTP: endpoint nào public, endpoint nào cần đăng nhập hoặc role cụ thể.
- Service: bật method security, dùng `@PreAuthorize` và kiểm tra ownership booking.

Không tin `userId` do client gửi. Khi truy vấn booking của customer, giới hạn theo cả booking ID và current user ID.

| Tình huống API | Status |
|---|---|
| Thiếu token, token sai hoặc hết hạn | `401` |
| Đã xác thực nhưng không đủ role | `403` |
| Không có booking hoặc booking không thuộc customer | `404` |
| Dữ liệu đầu vào không hợp lệ | `400` |
| Trạng thái nghiệp vụ không cho phép thao tác | `409` |

Response lỗi không chứa stack trace, mật khẩu, token hoặc thông tin nội bộ. Login sai trả thông báo chung, không phân biệt email không tồn tại và mật khẩu sai.

CORS chỉ bổ sung khi thực hành client khác origin: chỉ cho phép origin cần thiết. Postman không chịu cơ chế CORS của trình duyệt, nên dùng browser để kiểm chứng phần này.

## 8. Cấu trúc source code dự kiến

Hiện `SecurityConfig`, `HelloController`, `SessionLabController` ở package gốc `com.ticketlock`. Các module `auth` và `user` đã tách controller/service/dto/entity/repository; `user` có mapper. Các package event/booking/admin trong sơ đồ dưới là dự kiến.

```text
com.ticketlock
├── security
│   ├── SecurityConfig
│   ├── CustomUserDetailsService
│   └── ...
├── auth
│   ├── AuthController
│   ├── AuthService
│   └── ...
├── user
│   ├── User
│   ├── UserRepository
│   └── ...
├── event
│   ├── Event
│   ├── EventService
│   ├── EventRepository
│   └── ...
├── booking
│   ├── Booking
│   ├── BookingService
│   ├── BookingRepository
│   └── ...
├── admin
│   └── AdminController
└── common
    └── exception
```

Controller nhận request và trả response. Service xử lý nghiệp vụ và ranh giới transaction. Repository truy cập database. Dùng DTO thay vì trả trực tiếp JPA entity qua API.

## 9. Lộ trình 14 ngày

Dự kiến 3–4 giờ tập trung mỗi ngày. Mỗi buổi gồm đọc khái niệm, triển khai, cố tình tạo lỗi và ghi lại kết quả.

| Ngày | Nội dung và kết quả |
|---|---|
| 1 | Khởi tạo app, quan sát HTTP, cookie và session |
| 2 | Filter chain, endpoint public và protected |
| 3 | User trong PostgreSQL, BCrypt, JSON session login và đăng ký CUSTOMER |
| 4 | Debug authentication; hiểu AuthenticationManager, AuthenticationProvider, UserDetailsService, SecurityContext; đọc current user |
| 5 | ADMIN/CUSTOMER và method security |
| 6 | Timeout, logout, session lifecycle — thực hành tại [day-06.md](day-06.md) |
| 7 | Session fixation và concurrent sessions |
| 8 | CSRF, CORS, xử lý 401/403 |
| 9 | Login API, phát hành và kiểm tra JWT |
| 10 | Refresh token, rotation, logout — thực hành tại [jwt-access-token-lab.md](jwt-access-token-lab.md) |
| 11 | Hoàn thiện hai mô hình bảo mật trong cùng ứng dụng — thực hành tại [day-11.md](day-11.md) |
| 12 | Redis session đã có; kiểm chứng restart, chia sẻ phiên và logout xuyên instance tại [Redis lab](redis-session-lab.md) |
| 13 | Ownership hồ sơ đã có; booking ownership chờ triển khai nghiệp vụ |
| 14 | Demo, sửa lỗi, viết README giải thích thiết kế |

Không cần viết toàn bộ kiến trúc ngay ngày đầu. Mỗi ngày bổ sung phần đang học và kiểm chứng trước khi chuyển bước.

## 10. Hướng dẫn ngày đầu tiên

**Mục tiêu:** hiểu browser duy trì trạng thái với server như thế nào. Chưa code JWT hoặc hoàn thành đăng nhập dùng database.

### Bước 1 — Khởi tạo dự án

Tạo Gradle project (Groovy DSL) dùng Java 21 với:

- Spring Web.
- Thymeleaf.
- Spring Security.

PostgreSQL và JPA thêm ngày 3 để ngày đầu tập trung vào HTTP và session.

### Bước 2 — Quan sát Spring Security mặc định (lịch sử ngày đầu)

Bản hiện tại đã có `CustomUserDetailsService` và tắt form login, nên không còn user/password sinh tự động hay trang login backend. Phần này chỉ mô tả giai đoạn khởi tạo; không xóa cấu hình hiện tại để thử lại.

Tạo endpoint `GET /hello`, chạy ứng dụng và truy cập bằng browser. Đăng nhập bằng tài khoản development mặc định do Spring Security cung cấp khi chưa có cấu hình tài khoản riêng.

Ghi lại:

- Vì sao request chuyển tới trang login?
- Response chuyển hướng có status gì?
- Sau login, browser gửi thêm dữ liệu gì?
- Mật khẩu mặc định development xuất hiện ở đâu?

Không commit mật khẩu hoặc token vào tài liệu.

### Bước 3 — Tạo bài thực hành session

Sau khi quan sát mặc định, cấu hình tạm cho phép `/lab/**` và tạo:

| Endpoint | Hành vi |
|---|---|
| `GET /lab/session` | Tạo session nếu chưa có, tăng `visitCount`, trả giá trị |
| `GET /lab/session/status` | Dùng `getSession(false)` kiểm tra session hiện có, không tự tạo mới |

Chỉ trả `visitCount` và trạng thái có session. Xem Session ID trong DevTools, không cần đưa vào response hoặc log.

Đây là endpoint thí nghiệm; việc tăng bộ đếm bằng GET chỉ phục vụ quan sát session và sẽ bị loại bỏ trước bản demo cuối.

### Bước 4 — Thực hiện bốn thí nghiệm

1. Reload nhiều lần: quan sát `visitCount` tăng.
2. Mở tab khác trong cùng browser profile: quan sát bộ đếm có dùng chung không.
3. Mở cửa sổ ẩn danh: quan sát session riêng.
4. Xóa cookie session rồi truy cập lại: quan sát server không còn liên kết request với session cũ.

Trong DevTools, kiểm tra request header `Cookie`, response header `Set-Cookie` khi xuất hiện, thuộc tính cookie và status code.

### Bước 5 — Viết ghi chú

Tạo `docs/mini/day-01.md`, trả lời bằng lời của mình:

- HTTP stateless nghĩa là gì?
- Cookie lưu ở đâu, session lưu ở đâu?
- Browser gửi Session ID bằng cách nào?
- Đóng một tab có đồng nghĩa với hủy session không?
- Xóa cookie có lập tức xóa session trên server không?
- Session ID có phải mật khẩu hay thông tin người dùng không?
- Vì sao biết Session ID có thể dẫn đến chiếm phiên?

### Điều kiện hoàn thành ngày 1

- [ ] Ứng dụng chạy được trên máy.
- [ ] Quan sát được chuyển hướng đến login mặc định.
- [ ] Quan sát được cookie session bằng DevTools.
- [ ] Thực hiện đủ bốn thí nghiệm.
- [ ] Phân biệt được cookie và dữ liệu session.
- [ ] Giải thích được luồng `request → cookie → tìm session → đọc dữ liệu`.
- [ ] Có ghi chú `docs/mini/day-01.md`.

## 11. Tiêu chí hoàn thành đồ án

- [ ] Tài khoản được đọc từ database; mật khẩu được hash.
- [ ] Admin đăng nhập và logout bằng session.
- [ ] Session timeout và session fixation protection được kiểm chứng.
- [ ] Login ở browser thứ hai làm phiên cũ mất quyền truy cập.
- [ ] Session hoạt động qua Redis; concurrent session registry được cấu hình phù hợp.
- [ ] Customer login nhận access token và refresh token.
- [ ] API từ chối JWT sai chữ ký, hết hạn, sai issuer hoặc audience.
- [ ] Refresh token chỉ dùng một lần; phát hiện reuse thu hồi family.
- [ ] Logout ngăn refresh tiếp và giải thích được giới hạn với access token còn hạn.
- [ ] CUSTOMER không truy cập được chức năng ADMIN.
- [ ] Customer A không xem hoặc hủy được booking của customer B.
- [ ] Cookie admin không thay thế bearer JWT trên API.
- [ ] Request thay đổi dữ liệu admin thiếu CSRF token bị từ chối.
- [ ] Chuyển chính sách URL sang deny-by-default khi chốt endpoint (hiện tại fallback chỉ yêu cầu xác thực).
- [ ] Endpoint `/lab/**` được loại bỏ.
- [ ] Có kiểm thử tích hợp cho các ranh giới quyền chính và README hướng dẫn chạy, demo.

## 12. Câu hỏi tự kiểm tra cuối khóa

1. Authentication khác authorization thế nào?
2. UserDetailsService có tự kiểm tra mật khẩu không?
3. AuthenticationManager và AuthenticationProvider đảm nhiệm gì?
4. SecurityContext được tạo, đọc và duy trì thế nào trong hai mô hình?
5. Vì sao đã login vẫn có thể nhận 403?
6. Session ID thay đổi sau login để làm gì?
7. Logout ảnh hưởng gì tới browser, session, refresh token và access token?
8. Vì sao JWT không tự loại bỏ nguy cơ CSRF?
9. Spring Security chọn filter chain nào cho một request?
10. Redis thay đổi việc lưu session ra sao khi có nhiều instance?
11. Vì sao kiểm tra role chưa đủ để bảo vệ booking?
12. Có thể chứng minh các câu trả lời trên bằng request, debugger hoặc bài kiểm thử nào?

## 13. Lệnh chạy với Gradle

Dự án sử dụng Gradle Wrapper, không cần cài Gradle riêng. Cần JDK 21.

```bash
./backend/gradlew -p backend test       # Chạy kiểm thử
./backend/gradlew -p backend bootRun    # Khởi động ứng dụng
./backend/gradlew -p backend bootJar    # Đóng gói executable JAR vào build/libs/
```

Trên Windows dùng `gradlew.bat`. Lần đầu wrapper tải Gradle và dependency vào `~/.gradle`. Kết quả build nằm trong `backend/build/`. Các lệnh trên chạy từ thư mục gốc repository.

# Ngày 02 — Filter Chain, Endpoint Public và Protected

Ghi chú từ bài thực hành TicketLock Mini. Trọng tâm ngày 2 là hiểu cách Spring Security đánh chặn HTTP request, nguyên lý hoạt động của Filter Chain, thứ tự ưu tiên của các quy tắc phân quyền, và sự khác biệt giữa Xác thực (Authentication) và Phân quyền (Authorization).

---

## 1. SecurityFilterChain dùng để làm gì?

`SecurityFilterChain` là **chuỗi các trạm gác an ninh (Servlet Filter liên tiếp)** đứng chặn ngay cửa ngõ trước khi bất kỳ HTTP request nào được chuyển tiếp tới Spring MVC hay Controller.

```text
HTTP Request ───→ [ SecurityFilterChain ] ───→ [ DispatcherServlet ] ───→ [ Controller ]
                         │
                         ├── 1. Kiểm tra CSRF Token (CsrfFilter)
                         ├── 2. Đọc Session / Cookie (SecurityContextHolderFilter)
                         ├── 3. Kiểm tra danh tính (AuthenticationFilter)
                         └── 4. Quyết định quyền truy cập (AuthorizationFilter)
```

- **Tập trung hóa bảo mật:** Thay vì phải kiểm tra quyền trong từng hàm Controller bằng code thủ công, mọi luật bảo vệ được cấu hình tập trung tại một nơi.
- **Đưa ra phán quyết:** Cho phép request đi tiếp vào Controller, chuyển hướng sang trang đăng nhập (`302 Found`), hoặc ngắt luồng và trả về lỗi (`401 Unauthorized` / `403 Forbidden`).

---

## 2. Ba quy tắc `permitAll`, `authenticated` và `denyAll` khác nhau thế nào?

| Quy tắc | Đối tượng áp dụng | Hành vi của hệ thống | Tình huống sử dụng thực tế |
|---|---|---|---|
| **`permitAll()`** | Mọi người dùng (bao gồm khách vãng lai chưa đăng nhập) | Cho phép request đi qua thẳng tới Controller mà không đòi hỏi danh tính. | Trang chủ xem danh sách sự kiện mở bán, tài nguyên tĩnh (CSS, JS, ảnh), endpoint lab session, trang `/login`. |
| **`authenticated()`** | Chỉ người dùng **đã đăng nhập thành công** | Kiểm tra `SecurityContext`. Nếu chưa xác thực (Anonymous), lập tức chặn lại và chuyển hướng về trang đăng nhập. | Trang xem thông tin cá nhân `/me`, đổi mật khẩu, xem lịch sử đặt vé. |
| **`denyAll()`** | **Không một ai** (kể cả Admin có toàn quyền) | Chặn tuyệt đối mọi request, luôn trả về mã lỗi `403 Forbidden`. | Tạm khóa các endpoint đang bảo trì khẩn cấp, vô hiệu hóa các tính năng thử nghiệm nội bộ chưa cho phép sử dụng. |

---

## 3. Vì sao rule cụ thể nên đặt trước rule rộng?

Spring Security đánh giá các quy tắc trong `authorizeHttpRequests` theo nguyên tắc **"First Match Wins"** (Khớp luật đầu tiên thì chốt hạ và **dừng lại ngay lập tức**, không duyệt tiếp các dòng phía sau).

### Phân tích qua bài thử nghiệm `/public/private`:

Nếu cấu hình sai (đặt rule rộng trước rule hẹp):
```java
// ❌ CẤU HÌNH SAI: Rule rộng nuốt chửng rule hẹp
.requestMatchers("/public/**").permitAll()
.requestMatchers("/public/private").authenticated()
.anyRequest().authenticated()
```

- Khi request gửi tới `GET /public/private`, Spring Security so khớp với dòng đầu tiên: `/public/**`.
- Vì `/public/private` khớp hoàn toàn với mẫu `/public/**`, hệ thống áp dụng ngay `permitAll()` và **cho phép truy cập tự do mà không cần đăng nhập**.
- Dòng thứ hai `.requestMatchers("/public/private").authenticated()` **không bao giờ được chạy tới** (trở thành dead code), vô tình tạo ra lỗ hổng bảo mật nghiêm trọng.

### Cách cấu hình đúng:
Luôn sắp xếp theo thứ tự: **Cụ thể (Specific) $\rightarrow$ Tổng quát (General) $\rightarrow$ Cuối cùng là `anyRequest()`**.

```java
// ✅ CẤU HÌNH ĐÚNG: Khóa cửa phòng riêng trước, mở cửa sảnh sau
.requestMatchers("/public/private").authenticated() // Rule hẹp, cụ thể
.requestMatchers("/public/**").permitAll()          // Rule rộng bao quát
.anyRequest().authenticated()                       // Chốt chặn mặc định
```

---

## 4. Vì sao `/blocked` không chạy code trong Controller?

Do vị trí đứng của `SecurityFilterChain` trong kiến trúc xử lý request của Java Web:

$$\text{Client Request} \longrightarrow \mathbf{[SecurityFilterChain]} \longrightarrow \text{DispatcherServlet} \longrightarrow \text{Controller}$$

1. Bộ lọc bảo mật `SecurityFilterChain` nằm ở tầng **Servlet Filter**, đứng **TRƯỚC** `DispatcherServlet` và **TRƯỚC** toàn bộ các Controller.
2. Khi request gửi đến endpoint `/blocked` (được gắn quy tắc `.denyAll()` hoặc yêu cầu `.authenticated()` nhưng chưa login):
   - Bộ lọc an ninh (`AuthorizationFilter`) phát hiện vi phạm quyền hạn.
   - Nó **chặn đứng request ngay tại trạm gác**, không gọi tiếp `filterChain.doFilter()`.
   - Hệ thống tự tạo `HttpServletResponse` chứa mã lỗi `403 Forbidden` và gửi thẳng về cho Client.
3. Request **chưa từng chạm tới** `DispatcherServlet` nên code Java bên trong hàm của Controller hoàn toàn không được thực thi.

---

## 5. Đã đăng nhập có đồng nghĩa được truy cập mọi endpoint không?

**Hoàn toàn KHÔNG.**

Đây là ranh giới cốt lõi giữa hai khái niệm:
- **Authentication (Xác thực - Bạn là ai?):**
  - Trả lời cho câu hỏi danh tính. Khi đăng nhập thành công với username/password, bạn được cấp danh tính hợp lệ và thỏa mãn điều kiện `authenticated()`.
- **Authorization (Phân quyền - Bạn có quyền làm gì?):**
  - Trả lời cho câu hỏi quyền hạn và phạm vi thao tác.
  - Sau khi xác thực danh tính, hệ thống tiếp tục kiểm tra:
    1. **Vai trò (Role-based):** Endpoint `/admin/**` chỉ dành cho tài khoản có role `ADMIN`. Một tài khoản `CUSTOMER` dù đã đăng nhập thành công vẫn sẽ nhận `403 Forbidden` khi cố truy cập trang quản trị.
    2. **Quyền sở hữu (Ownership-based):** Khách hàng A đã đăng nhập chỉ được xem và hủy vé của chính mình, không được phép xem hay hủy đơn đặt vé của khách hàng B (`/bookings/{id}`).

Đăng nhập thành công chỉ là "tấm vé qua cửa tòa nhà" (Authentication), còn việc được vào từng phòng ban cụ thể hay không phụ thuộc vào "chức vụ và quyền hạn" (Authorization).

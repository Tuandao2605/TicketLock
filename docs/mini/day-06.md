# Ngày 6 — Timeout, logout và vòng đời session

## Mục tiêu

Giải thích được session được tạo khi nào, server duy trì session ra sao, idle timeout khác logout thế nào, và request sau khi session mất hiệu lực nhận kết quả gì.

## Cấu hình trong TicketLock

- Bình thường, session hết hạn sau **15 phút không hoạt động** (`spring.session.timeout=15m`). Đây là idle timeout, không phải 15 phút cố định kể từ lúc đăng nhập.
- Khi request hợp lệ dùng lại session, thời điểm hoạt động được cập nhật; một endpoint kiểm tra liên tục có thể làm timeout không bao giờ xảy ra.
- `POST /api/auth/logout` do Spring Security xử lý. CSRF vẫn bật; logout invalidates session và xóa cookie `JSESSIONID` cùng cookie CSRF hiện tại.
- CSRF token được lưu trong cookie `XSRF-TOKEN` bằng `CookieCsrfTokenRepository`, không nằm trong HTTP session. Cookie này cho phép React đọc token; cookie session `JSESSIONID` vẫn là nơi browser giữ định danh phiên đăng nhập.
- Cách lưu này tránh cấp session chỉ để giữ CSRF token, nhưng không chống DoS: request vẫn tiêu thụ kết nối, băng thông, bộ đệm và CPU. Cần rate limit, giới hạn kết nối/kích thước request và bảo vệ ở reverse proxy hoặc hạ tầng.
- `SessionLifecycleListener` ghi sự kiện `Redis session created`, `Redis session deleted` và `Redis session expired`. Listener không ghi session ID hay thông tin người dùng.
- Log `deleted` phản ánh sự kiện xóa, `expired` phản ánh hết hạn; chúng không chứa session ID. Redis keyspace notification/cleanup có thể đến trễ; dùng `/me` để kiểm tra quyền truy cập thực tế.

## Bài 1 — Quan sát lúc tạo session

1. Chạy backend ở cấu hình mặc định.
2. Xóa cookie của `localhost` trong browser test hoặc mở cửa sổ riêng.
3. Mở `/api/auth/csrf`. Server trả cookie `XSRF-TOKEN`; thao tác này không cần tạo session chỉ để lưu CSRF token.
4. Xem Storage để thấy `XSRF-TOKEN`. Nếu chưa có `JSESSIONID`, request CSRF này không nên tạo session; `JSESSIONID` sẽ xuất hiện khi một luồng khác cần session.
5. Mở `/lab/session/status`. Endpoint này gọi `getSession(false)`, nên tự nó không tạo session mới.

Session có thể được tạo bởi đăng nhập, code gọi `request.getSession()` hoặc cơ chế request cache khi ứng dụng cần lưu request. Vì vậy hãy dùng riêng endpoint public `/api/auth/csrf` trên một client sạch để quan sát việc cấp CSRF cookie. Cookie CSRF tự nó không phải session và không giữ trạng thái server-side.

## Bài 2 — Idle timeout nhanh

Profile `session-lab` đặt timeout một phút, không thay đổi timeout 15 phút mặc định:

```bash
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=session-lab'
```

1. Đăng nhập tài khoản đã đăng ký bằng React hoặc Postman. Dùng cùng một client/cookie jar suốt bài; Postman không tự dùng cookie của React.
2. Gọi `/me`; kết quả phải là `200`. Trong Postman, request GET này không cần body hay pre-request CSRF.
3. Không gửi request tới ứng dụng ít nhất 70 giây. Nếu dùng React, tránh quay lại tab/cửa sổ trong lúc chờ: focus/visibility có thể kích hoạt `/me`. Dùng Postman riêng và đóng các tab dùng cùng phiên là cách dễ kiểm soát hơn.
4. Gọi `/me` lại với cùng browser/session. Kết quả mong đợi là `401`; đăng nhập lại thì endpoint trở về `200`.
5. Console có thể ghi `Redis session expired` khi Redis/Spring Session xử lý sự kiện hết hạn. Thời điểm cleanup không nhất thiết trùng chính xác giây thứ 60.

Không dùng `/lab/session` trong lúc chờ: endpoint này gọi `getSession()` và có thể tạo hoặc dùng session. Cũng không polling `/me`, vì request đó gia hạn idle timeout.

## Bài 3 — Request gia hạn idle timeout

Khởi động lại với profile `session-lab`, đăng nhập, rồi:

1. Gọi `/me` sau khoảng 40 giây.
2. Chờ thêm 40 giây, gọi `/me` lần nữa: session vẫn còn hiệu lực do vừa được dùng.
3. Từ request cuối, chờ hơn 60 giây mà không gọi ứng dụng, rồi gọi `/me`: nhận `401`.

Thí nghiệm này cho thấy timeout được tính từ lần truy cập gần nhất, không phải từ thời điểm login.

```text
00s  login          → phiên được xác thực
40s  GET /me → 200  → idle khoảng 40 giây, ghi nhận truy cập mới
80s  GET /me → 200  → idle khoảng 40 giây, ghi nhận truy cập mới
150s GET /me → 401  → đã idle khoảng 70 giây
```

Spring Session Redis quản lý thời điểm truy cập và hết hạn; controller không có hàm
`giaHanSession()`. `session.getLastAccessedTime()` và `session.getMaxInactiveInterval()`
là API quan sát, không phải logic tự gia hạn. `setMaxInactiveInterval()` thay đổi
cấu hình thời gian idle tối đa, không cần gọi lại trong mỗi request.

Request dùng session khác cũng có thể cập nhật hoạt động, không riêng `/me`.
Di chuột hoặc gõ chữ mà không gửi request tới server không gia hạn session.

## Bài 4 — Logout và cookie cũ

1. Đăng nhập và xác nhận `/me` trả `200`.
2. Gọi `GET /api/auth/csrf` để bảo đảm cookie `XSRF-TOKEN` mới đã được cấp.
3. Gửi `POST /api/auth/logout` cùng cookie `JSESSIONID`, cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN` có cùng giá trị token. Trong React luồng logout gửi request này tự động.
4. Kỳ vọng response `204`, response xóa `JSESSIONID`, và request `/me` tiếp theo trả `401`.
5. Thử gửi lại cookie cũ đã lưu riêng trong Postman: server không được khôi phục phiên đã invalidated.

Xóa cookie bằng tay chỉ xóa bản sao phía browser. Logout gửi request để server invalidates session; đây là hai thao tác khác nhau.

## Vòng đời cần nhớ

```text
chưa có session
   ├─ request CSRF → cookie XSRF-TOKEN, không cần session
   └─ login thành công → tạo/dùng session → server lưu SecurityContext
                                ├─ request tiếp theo → cập nhật last-access time
                                ├─ logout → invalidate ngay
                                └─ idle quá timeout → Spring Session hết hạn session
```

Khi session bị invalidate hoặc hết hạn, cookie có thể vẫn còn trong browser đến khi browser nhận response xóa cookie hoặc cookie tự hết hạn. Dù vậy ID cũ không còn đại diện cho một phiên xác thực hợp lệ trên server.

## Hoàn thành ngày 6 khi tự trả lời được

1. Vì sao gọi `/me` liên tục có thể khiến session không hết hạn?
2. Vì sao `/api/auth/csrf` không cần tạo session với cấu hình hiện tại?
3. Logout và xóa cookie khác nhau ở bước nào?
4. Sau idle timeout, cookie cũ còn trong browser có đồng nghĩa session còn hợp lệ không?
5. Vì sao thời điểm nhận log Redis expired có thể khác thời điểm phiên mất quyền truy cập?

Sau bài lab, dừng backend profile `session-lab`; lần chạy bình thường vẫn dùng timeout 15 phút.


## Các đoạn code áp dụng

| File | Logic hiện tại |
|---|---|
| [application.properties](../../backend/src/main/resources/application.properties) | Timeout Redis mặc định `15m`, cookie JSESSIONID HttpOnly, SameSite=Lax |
| [application-session-lab.properties](../../backend/src/main/resources/application-session-lab.properties) | Ghi đè timeout Redis thành `1m` khi bật profile session-lab |
| [RedisSessionConfig](../../backend/src/main/java/com/ticketlock/security/RedisSessionConfig.java) | Indexed session registry và CookieSerializer đặt JSESSIONID, HttpOnly, SameSite=Lax |
| [SecurityConfig](../../backend/src/main/java/com/ticketlock/SecurityConfig.java) | HttpSessionSecurityContextRepository, cookie CSRF, HTTP authorization, entry point 401 và logout |
| [LoginController](../../backend/src/main/java/com/ticketlock/auth/controller/LoginController.java) | Gọi session strategy, tạo SecurityContext và `saveContext(...)` |
| [HelloController](../../backend/src/main/java/com/ticketlock/HelloController.java) | `/me` đọc Authentication sau khi request đi qua security filters |
| [SessionLifecycleListener](../../backend/src/main/java/com/ticketlock/security/SessionLifecycleListener.java) | Lắng nghe Spring Session created/deleted/expired; không tự tạo timer hoặc hủy session |
| [main.jsx](../../frontend/src/main.jsx) | Kiểm tra `/me` khi mount/focus/visibility và thay đổi trạng thái login; khi 401 thì xóa user trên UI |

`expiredSessionStrategy` trong SecurityConfig dành cho phiên bị đánh dấu hết hiệu lực
bởi concurrent session control (login ở nơi khác). Idle timeout trong bài này do
Spring Session Redis xử lý; mất Authentication khiến HTTP authorization trả 401 trước controller.

Redis là kho session mặc định. Profile `session-lab` chỉ ghi đè `spring.session.timeout=1m`; vẫn dùng Redis và cùng namespace. Khi thử timeout ngắn, dùng phiên test mới để không nhầm với các phiên có cấu hình timeout cũ. Xem [Redis lab](redis-session-lab.md).

## CSRF token trong khi sử dụng web

```text
GET /api/auth/csrf → cookie A
POST /api/auth/login + cookie A + header A → 204, xóa CSRF cũ, lưu phiên xác thực
GET /api/auth/csrf → cookie B
GET /me + JSESSIONID → 200, không cần header CSRF
POST thao tác thay đổi dữ liệu + JSESSIONID + cookie B + header B
POST /api/auth/logout + JSESSIONID + cookie B + header B → 204, hủy phiên/xóa cookie
```

Endpoint CSRF trả JSON metadata `headerName` và `cookieName`, không còn trường token.
Token thực nằm trong cookie. Có thể dùng lại token hiện hành cho nhiều POST/PUT/PATCH/DELETE;
không bắt buộc tạo mới trước từng request. Login/logout xóa token cũ nên cần lấy lại.
React hiện gọi endpoint CSRF trước register/login/logout. JWT chain nhận bearer hoặc
refresh trong JSON body, không dùng cơ chế CSRF cookie này.

## Postman: chuẩn bị login và logout

1. Mở Cookies → Domains Allowlist, thêm đúng `localhost` (không protocol/port).
2. Giữ cookie jar bật; không tự xóa JSESSIONID trước mỗi request.
3. Đặt script dưới trong Before request của login/logout, không gắn vào mọi GET
   hoặc toàn collection vì request phụ có thể làm nhiễu bài idle timeout.
4. Login dùng POST `/api/auth/login`, JSON email/password; logout dùng POST
   `/api/auth/logout`, Body none. Script tự cập nhật header X-XSRF-TOKEN.

```javascript
const baseUrl = "http://localhost:8080";
const jar = pm.cookies.jar();

pm.sendRequest({
    url: `${baseUrl}/api/auth/csrf`,
    method: "GET"
}, function (err, res) {
    if (err || res.code !== 200) {
        pm.execution.skipRequest();
        throw new Error("Không lấy được cookie CSRF.");
    }
    jar.get(baseUrl, "XSRF-TOKEN", function (cookieError, token) {
        if (cookieError || !token) {
            pm.execution.skipRequest();
            throw new Error("Không đọc được cookie XSRF-TOKEN.");
        }
        pm.request.headers.upsert({ key: "X-XSRF-TOKEN", value: token });
    });
});
```

After response cho login/logout thành công:

```javascript
pm.test("Thành công trả 204", function () {
    pm.response.to.have.status(204);
});
```

Postman tự lưu Set-Cookie từ backend; script không tự tạo Session ID. Sau login,
GET `/me` phải trả 200; sau logout/hết idle timeout phải trả 401. Không gọi
`response.json()` với response 204 vì không có body. Nếu cookie phiên đã bị login
ở nơi khác làm mất hiệu lực, request đầu có thể bị concurrent-session filter trả
401; xóa cookie cũ một lần rồi login lại, không tự xóa mỗi request.

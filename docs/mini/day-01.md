# Ngày 01 — HTTP, Cookie và Session

Ghi chú HTTP/cookie/session áp dụng cho bản hiện tại: Spring Session lưu phiên trong Redis ở mọi profile. Tomcat vẫn phục vụ HTTP nhưng không phải kho phiên đăng nhập.

## 1. HTTP stateless nghĩa là gì?

HTTP không tự ghi nhớ mối liên hệ giữa các request. Việc đăng nhập thành công trong một request không tự khiến request tiếp theo mang danh tính người dùng.

Ứng dụng bổ sung cơ chế cookie và session để duy trì trạng thái: browser gửi Session ID, server dùng ID đó để tìm dữ liệu phiên đã lưu.

Stateless không có nghĩa server không thể lưu dữ liệu; nó mô tả việc bản thân giao thức HTTP không tự duy trì trạng thái phiên cho ứng dụng.

## 2. Cookie lưu ở đâu, session lưu ở đâu?

- **Cookie:** được browser lưu và quản lý phía client. Trong Firefox có thể xem tại DevTools → Storage → Cookies.
- **Session:** được quản lý phía server. Trong dự án hiện tại, Spring Session lưu dữ liệu HTTP session trong Redis.

Cookie `JSESSIONID` chỉ chứa mã định danh phiên. Dữ liệu như `visitCount` và SecurityContext của người đã đăng nhập nằm phía server.

```text
Browser                              Server
Cookie: JSESSIONID=example-id  ──────→ Session example-id
                                      ├── visitCount
                                      └── SecurityContext (nếu đã đăng nhập)
```

Spring Session và registry cùng dùng Redis; không còn chế độ session trong RAM của từng backend.

## 3. Browser gửi Session ID cho server bằng cách nào?

Server cấp cookie qua response header:

```http
Set-Cookie: JSESSIONID=example-id; Path=/; HttpOnly
```

Browser lưu cookie và tự gửi nó trong các request phù hợp với phạm vi và thuộc tính cookie:

```http
Cookie: JSESSIONID=example-id
```

Server dùng ID này để tìm session tương ứng. Không cần gửi lại username/password mỗi lần gọi `/hello` hoặc `/me`.

Browser không tự gửi cookie tới mọi website. Host/domain, path, Secure, SameSite và các quy tắc của browser quyết định khi nào cookie được gửi. Cookie không được phân biệt theo port.

## 4. Đóng một tab có đồng nghĩa hủy session trên server không?

**Không.** Đóng tab không tự gửi lệnh hủy session tới server. Các tab cùng browser profile thường dùng chung cookie cho cùng website.

Session phía server có thể vẫn tồn tại đến khi hết hạn, bị ứng dụng hủy hoặc bị mất khi server dừng nếu không có cơ chế lưu bền.

Đóng toàn bộ browser cũng không phải cách logout đáng tin cậy: browser có thể khôi phục session cookie khi khôi phục phiên duyệt web.

## 5. Xóa cookie có lập tức xóa session trên RAM server không?

**Không.** Xóa cookie chỉ khiến browser mất mã định danh để liên kết với session cũ. Server không tự nhận được thông báo xóa session.

Session cũ có thể còn tồn tại đến khi timeout hoặc bị hủy. Nếu truy cập `/lab/session` sau khi xóa cookie, `getSession()` tạo session mới và bộ đếm bắt đầu lại từ 1.

Nếu truy cập `/lab/session/status` trước, `getSession(false)` không tạo session mới, nên trong một request sạch kết quả là:

```json
{"hasSession":false,"visitCount":0}
```

Logout khác với chỉ xóa cookie: với `POST /api/auth/logout` hiện tại, Spring Security xử lý việc hủy session và xóa trạng thái xác thực phía server.

## 6. Session ID có phải mật khẩu hay thông tin người dùng không?

**Không.** Session ID là mã định danh khó đoán, giúp server tra cứu session. Bản thân ID không phải username, mật khẩu hay đối tượng Authentication.

Tuy nhiên, ID của phiên đã xác thực là thông tin nhạy cảm vì người sở hữu có thể dùng nó để gửi request dưới danh tính của phiên đó. Không đưa Session ID thực vào log, ảnh chia sẻ hoặc tài liệu.

Session cũng có thể thuộc người chưa đăng nhập. Có cookie `JSESSIONID` hoặc có `visitCount` không đồng nghĩa với đã xác thực thành công.

## 7. Vì sao biết Session ID có thể dẫn đến Session Hijacking?

Trong cơ chế session hiện tại, cookie đóng vai trò bằng chứng để server tìm lại phiên. Nếu kẻ xấu lấy được Session ID còn hiệu lực của người đã đăng nhập và có thể gửi nó trong request tới ứng dụng, server có thể coi request đó thuộc phiên của nạn nhân mà không yêu cầu lại mật khẩu.

Điều này cho phép kẻ xấu sử dụng quyền của phiên trong thời gian nó còn hiệu lực; không nhất thiết có nghĩa họ biết mật khẩu hoặc đã chiếm tài khoản vĩnh viễn. Session ID của khách chưa đăng nhập không tự cấp quyền của một tài khoản.

Các biện pháp liên quan:

- HTTPS và cookie Secure: giảm nguy cơ lộ cookie qua kết nối không mã hóa.
- HttpOnly: ngăn JavaScript đọc cookie trực tiếp, nhưng không loại bỏ toàn bộ tác hại của XSS.
- Đổi Session ID sau khi đăng nhập: chống session fixation; không thay thế bảo vệ chống đánh cắp ID sau đăng nhập.
- Timeout và logout hủy session: giới hạn thời gian phiên còn dùng được.
- Không làm lộ Session ID qua log, URL hay nội dung chia sẻ.

## Kết quả thực hành

- Chưa đăng nhập, truy cập `/hello` nhận `401`. Redirect đến `/login` là hành vi của cấu hình form login mặc định ở ngày đầu, hiện đã được thay bằng JSON login.
- Đăng nhập thành công thì truy cập được `/hello`.
- Request của browser có cookie `JSESSIONID`.
- Xóa cookie của phiên đăng nhập rồi vào `/hello` thì phải đăng nhập lại.
- Reload `/lab/session` làm `visitCount` tăng khi dùng lại cùng session.
- Xóa cookie rồi vào `/lab/session` tạo phiên mới và bộ đếm bắt đầu lại.
- `/lab/session/status` chỉ đọc session hiện có, không chủ động tạo mới hay tăng bộ đếm.
- `/me` đọc được username và authorities qua tham số Authentication.

## Liên hệ với code

```java
request.getSession();      // Lấy session hiện có; chưa có thì tạo.
request.getSession(false); // Lấy session hiện có; chưa có thì trả null.
```

Spring Security dùng SecurityContext để quản lý ngữ cảnh bảo mật. Với JSON session login hiện tại, `LoginController` chủ động lưu context qua `HttpSessionSecurityContextRepository`; filter chain khôi phục context ở request sau. Authentication chứa danh tính và authorities; nó không chứa toàn bộ dữ liệu session như `visitCount`.

Controller `/me` chỉ nhận và đọc Authentication do framework cung cấp. Nó không tự đọc cookie hoặc tạo session riêng cho endpoint.

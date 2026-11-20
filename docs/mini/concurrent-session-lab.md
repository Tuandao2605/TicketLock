# Giới hạn phiên đăng nhập và timeout

## Quy tắc

- Mỗi tài khoản chỉ có một phiên đăng nhập còn hiệu lực.
- Login ở nơi mới thành công sẽ đánh dấu phiên cũ expired.
- Khi phiên cũ gửi request tiếp theo, ConcurrentSessionFilter hủy phiên và trả 401.
- Không có cơ chế đẩy thông báo tức thì tới tab cũ.
- Session timeout sau 15 phút không hoạt động; request hợp lệ có thể gia hạn thời gian idle.

## Thử hai thiết bị

1. Khởi động lại backend.
2. Login tài khoản A trên React bằng browser thường, hoặc dùng Postman.
3. Mở cửa sổ ẩn danh và login cùng tài khoản A.
4. Ở phiên thứ nhất, gọi `/me`: nhận 401.
5. Ở phiên thứ hai, gọi `/me`: nhận 200.
6. Login tài khoản B không làm phiên A mất quyền.

Hai tab cùng browser profile thường dùng chung cookie, nên không phải hai phiên
độc lập. Dùng browser thường và ẩn danh, hoặc hai browser profile riêng.
React kiểm tra `/me` khi mở trang và khi quay về tab/cửa sổ browser.
Nếu nhận 401, giao diện xóa thông tin tài khoản và hiện lại form đăng nhập.
Không polling định kỳ vì request nền liên tục có thể gia hạn idle timeout.
Không có push tức thì: nếu tab cũ vẫn mở mà chưa gửi request hoặc lấy lại focus,
nội dung cũ có thể còn trên màn hình, nhưng không cấp quyền truy cập backend.

## Code hoạt động thế nào?

LoginController gọi CompositeSessionAuthenticationStrategy theo thứ tự:

1. Kiểm tra giới hạn phiên của principal trong SessionRegistry.
2. Đổi Session ID để chống fixation.
3. Xóa CSRF token cũ.
4. Đăng ký ID mới vào registry.
5. Controller lưu SecurityContext vào session.

Chỉ cấu hình maximumSessions ở HTTP là chưa đủ cho controller login thủ công;
controller phải gọi chiến lược kiểm soát phiên và đăng ký phiên như trên.

`RedisSessionConfig` luôn cung cấp `SpringSessionBackedSessionRegistry` từ cùng indexed Redis repository với HTTP session. Không còn SessionRegistryImpl hoặc HttpSessionEventPublisher trong cấu hình ứng dụng. Kiểm tra giới hạn rồi đăng ký phiên chưa phải giao dịch nguyên tử chống mọi cuộc đua login đồng thời; xem [Redis lab](redis-session-lab.md).

## Thử timeout

Bật profile `session-lab` để dùng `spring.session.timeout=1m`, rồi login:

```bash
./backend/gradlew -p backend bootRun --args='--spring.profiles.active=session-lab'
```

Không gửi request từ phiên đó trong hơn một phút, sau đó gọi endpoint protected.
Kết quả mong đợi là 401. Dừng profile `session-lab` sau khi thử; mặc định vẫn là 15 phút.
Mọi phiên đều lưu Redis; profile lab vẫn dùng Redis.
Dùng request thực để quan sát thời hạn phiên; MockMvc không mô phỏng thời gian chờ chỉ bằng cách gọi method controller.

Cookie `JSESSIONID` có HttpOnly và SameSite=Lax. Cookie `XSRF-TOKEN` cho phép JavaScript đọc, dùng cho header CSRF; nó không phải cookie xác thực. Khi triển khai HTTPS cần bật Secure;
không bật Secure trên HTTP localhost rồi kết luận cookie bị lỗi.

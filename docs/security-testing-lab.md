# Lab kiểm thử bảo mật TicketLock

## 1. Mục tiêu và phạm vi

Đóng vai người tấn công để hiểu các ranh giới bảo mật của chính TicketLock, sau đó sửa và kiểm chứng bằng bài kiểm thử hồi quy.

Chỉ thực hành trên localhost hoặc môi trường test do bạn quản lý, dùng tài khoản và dữ liệu giả. Các bài tải hoặc thử sai mật khẩu đều giới hạn số lượng request. Không dùng dữ liệu thật, tài khoản người khác hoặc hệ thống thanh toán thật.

Không mặc định ứng dụng đang có các lỗ hổng bên dưới. Một bài thử bị chặn đúng cũng là kết quả thành công. Nếu cần minh họa bản dễ bị khai thác, tạo trong nhánh hoặc profile lab riêng, không đưa vào cấu hình chạy bình thường.

## 2. Cách thực hiện mỗi bài

1. Ghi giả thuyết: kẻ tấn công có gì và muốn vượt qua ranh giới nào?
2. Ghi endpoint, tài khoản, dữ liệu và hành vi đúng mong đợi.
3. Thử trên bản hiện tại, lưu request/response đã che thông tin nhạy cảm.
4. Phân biệt lỗi bảo mật với lỗi chức năng hoặc cấu hình.
5. Sửa nguyên nhân, kiểm tra lại cùng tình huống.
6. Thêm test cho ranh giới quyền hoặc tính toàn vẹn quan trọng.

Mẫu báo cáo:

```text
Tên bài:
Commit/profile đang thử:
Điều kiện ban đầu:
Thao tác:
Kết quả mong đợi:
Kết quả thực tế:
Ảnh hưởng:
Nguyên nhân:
Cách sửa:
Kết quả kiểm tra lại:
```

Không lưu mật khẩu, JWT hoặc Session ID thật trong báo cáo.

## 3. Công cụ

- Firefox DevTools: Network và Storage.
- Hai browser profile riêng hoặc cửa sổ ẩn danh để tách tài khoản.
- Postman/curl để tạo lại request và thay đổi ID hoặc body.
- Spring Security Test và kiểm thử tích hợp.
- OWASP ZAP chỉ dùng bổ sung trên localhost; active scan chạy trên database test có thể reset vì scanner có thể tạo hoặc sửa dữ liệu.

## 4. Nhóm A — Làm được với ứng dụng hiện tại

### A1. Bỏ qua đăng nhập

**Mục tiêu:** kiểm tra endpoint protected có thực sự yêu cầu xác thực.

- Mở browser profile sạch, gọi `/hello` và `/me`.
- So sánh với `/public/hello` nếu đã triển khai và `/lab/session/status`.
- Gọi endpoint protected bằng Postman không gửi cookie.

**Đúng:** browser chưa đăng nhập bị chuyển tới login; không được trả dữ liệu người dùng. Với API JSON ở giai đoạn JWT, yêu cầu chưa xác thực trả 401 theo thiết kế. Không mặc định mọi response 302 đều là lỗi.

### A2. Session không đồng nghĩa đã đăng nhập

- Vào `/lab/session` để có cookie và `visitCount`.
- Khi chưa login, gọi `/me`.

**Đúng:** vẫn bị yêu cầu đăng nhập. Không dùng điều kiện `session != null` làm bằng chứng đã xác thực.

### A3. Session fixation

- Tạo session trước login bằng `/lab/session`.
- Quan sát ID trong DevTools, đăng nhập rồi so sánh ID sau login.
- Kiểm tra session ID cũ không còn truy cập được endpoint protected sau khi đổi ID. Chỉ dùng ID của phiên test của mình.

**Đúng:** Session ID đổi tại thời điểm xác thực, dữ liệu phiên có thể vẫn được giữ. Không nhầm việc đổi ID với tạo tài khoản hoặc tạo một session riêng cho mỗi URL.

### A4. Logout và phiên cũ

- Login bằng tài khoản test, xác nhận `/me` trả đúng user.
- Logout theo form/luồng Spring Security, gửi CSRF token khi được yêu cầu.
- Thử lại request protected với cookie của phiên trước logout.

**Đúng:** phiên cũ mất quyền truy cập. Xóa cookie thủ công không thay thế kiểm tra logout phía server. Không coi việc mở `/logout` hiện trang xác nhận là đã logout thành công.

### A5. Timeout và cookie

- Trong profile test, đặt session timeout ngắn, không gửi request để tránh gia hạn phiên, rồi kiểm tra lại.
- Kiểm tra HttpOnly và phạm vi cookie; kiểm tra Secure khi chạy HTTPS.
- Xác nhận thông tin đăng nhập không xuất hiện trong URL hoặc response JSON.

**Đúng:** hết thời gian không hoạt động thì endpoint protected yêu cầu xác thực lại. Cookie không Secure khi dùng HTTP localhost không tự chứng minh lỗi production; cần kiểm tra môi trường HTTPS riêng.

### A6. CSRF trên logout và thao tác thay đổi dữ liệu

- Với một request POST hợp lệ, thử lần lượt có token đúng, thiếu token và token sai.
- Bắt đầu bằng logout, sau đó áp dụng cho form tạo/sửa sự kiện khi có.

**Đúng:** request dùng session cookie nhưng thiếu/sai CSRF token bị chặn; token hợp lệ và đủ quyền mới được xử lý. Không thử bằng GET `/lab/session` để kết luận hệ thống chống CSRF tốt: đây là endpoint lab cố ý thay đổi bộ đếm qua GET, cần loại bỏ trước triển khai.

## 5. Nhóm B — Sau khi có role, database và booking

### B1. Vượt quyền theo role

- Login CUSTOMER rồi gọi trực tiếp URL ADMIN, không chỉ kiểm tra menu.
- Thử method thực sự thay đổi dữ liệu, không chỉ GET.

**Đúng:** 403 hoặc hành vi từ chối theo thiết kế; database không thay đổi. Bảo vệ ở backend, không dựa vào ẩn nút trên UI.

### B2. IDOR/BOLA — Truy cập booking của người khác

- Tạo customer A, customer B và booking riêng cho mỗi người.
- Đăng nhập A, thay booking ID trong request xem/hủy thành ID của B.
- Thử thêm `customerId` của B vào body tạo booking.

**Đúng:** không đọc/hủy được booking B; theo thiết kế Mini trả 404. Backend lấy customer từ Authentication, không tin user ID do client cung cấp.

### B3. Mass assignment

- Thêm các trường không được phép như `role`, `enabled`, `status`, `customerId` vào request mà customer được quyền gửi.

**Đúng:** từ chối hoặc bỏ qua trường ngoài hợp đồng; không thay role, owner hoặc trạng thái bảo vệ. Dùng request DTO có danh sách trường rõ ràng, không bind trực tiếp JPA entity từ body.

### B4. SQL injection

- Thử chuỗi có dấu nháy hoặc biểu thức SQL đơn giản trong chức năng tìm kiếm dùng dữ liệu giả.
- Kiểm tra repository có nối chuỗi đầu vào thành JPQL/SQL hay không.

**Đúng:** đầu vào là dữ liệu, không thay ý nghĩa câu truy vấn; không lộ SQL/stack trace. Dùng parameter binding; các phần tên cột hoặc thứ tự sort động cần allowlist riêng.

### B5. Stored XSS trong nội dung sự kiện

- Nhập HTML và một payload JavaScript vô hại chỉ đánh dấu trang trong trường tên/mô tả sự kiện test.
- Mở trang hiển thị nội dung bằng tài khoản test khác.

**Đúng:** văn bản được escape; không chạy JavaScript ngoài dự kiến. Kiểm tra cách render Thymeleaf, tránh đưa nội dung chưa xử lý vào `th:utext`. Nếu hỗ trợ rich text, dùng sanitizer với allowlist và kiểm tra đúng ngữ cảnh output.

### B6. Brute-force và dò tài khoản

- Thử tối đa khoảng 10 lần login sai trên tài khoản lab, quan sát response và cơ chế throttling nếu đã triển khai.
- So sánh thông báo email không tồn tại với mật khẩu sai; không kết luận từ một lần đo thời gian.

**Đúng sau khi bổ sung hardening:** có giới hạn hợp lý, thông báo chung, không lộ mật khẩu trong log. Chỉ dựa vào IP hoặc khóa tài khoản vĩnh viễn có thể gây chặn nhầm/DoS, cần cân nhắc thiết kế.

## 6. Nhóm C — Sau khi có JWT và refresh token

### C1. Token giả, hết hạn và sai phạm vi

- Gọi API không có token, token sai chữ ký, hết hạn, sai issuer/audience.
- Sửa claim role nhưng giữ chữ ký cũ.

**Đúng:** 401 và không có side effect. Token hợp lệ nhưng thiếu quyền trả 403. Server không chỉ decode payload rồi tin claim; phải xác minh bằng thuật toán và khóa được cấu hình.

### C2. Refresh rotation và reuse

- Refresh lần một để nhận token mới.
- Dùng lại refresh token cũ.
- Thử refresh sau logout và thử hai request refresh đồng thời.

**Đúng:** token cũ không được dùng lại; reuse thu hồi family theo thiết kế. Rotation có tính nguyên tử. Client cần tránh tự tạo nhiều request refresh cạnh tranh không cần thiết.

### C3. Phân biệt logout với access token expiry

- Logout rồi thử refresh và access token còn hạn.

**Đúng theo bản Mini:** refresh bị từ chối, access token có thể vẫn dùng đến hết hạn. Đây là trade-off đã thiết kế, không tự coi là bug. Nếu yêu cầu thu hồi ngay, thiết kế thêm revocation thay vì chỉ thay response logout.

### C4. CORS và cách lưu token

- Dùng trang frontend test khác origin để gọi API; Postman không kiểm chứng được CORS của browser.
- Kiểm tra origin được phép, credentials và response thực tế.

**Đúng:** chỉ cấp quyền đọc response cross-origin cho origin đã chọn. CORS không thay thế authentication và không ngăn curl gọi server. Nếu chuyển token sang cookie, đánh giá lại CSRF; không tắt CSRF chỉ vì token có định dạng JWT.

## 7. Nhóm D — Khi triển khai bản Advanced

- **Tranh ghế:** gửi một batch nhỏ request đồng thời cho cùng ghế; chỉ một reservation hợp lệ thắng.
- **Thay giá:** sửa amount trong request; server tính tiền từ dữ liệu tin cậy.
- **Thanh toán replay:** gửi callback giả, sai chữ ký, lặp hoặc trễ; không trừ tiền/cấp vé nhiều lần.
- **Nhóm ghế:** thao tác all-or-nothing, không giữ được một phần rồi báo thành công.
- **Hold cũ:** replay Release/Confirm của reservation cũ; không ảnh hưởng hold mới.
- **Check-in trùng:** hai request quét cùng vé; chỉ một lần được ghi nhận.
- **Service permissions:** credential của Notification không được sửa Inventory hoặc tạo payment.

Nhóm này thực hiện sau khi có nghiệp vụ tương ứng, trên sandbox thanh toán và dữ liệu có thể reset.

## 8. Lịch học bổ sung

| Buổi | Nội dung | Thời lượng gợi ý |
|---|---|---|
| 1 | A1–A3: xác thực và session fixation | 60–90 phút |
| 2 | A4–A6: logout, timeout, CSRF | 60–90 phút |
| 3 | B1–B3: role, ownership, mass assignment | 90 phút |
| 4 | B4–B6: injection, XSS, login throttling | 90 phút |
| 5 | C1–C4: JWT, refresh, CORS | 90 phút |

Xen kẽ với tính năng đang học trong lộ trình 14 ngày. Bài chưa đủ điều kiện thì đánh dấu chưa thực hiện; không cần xây toàn bộ tính năng trước để hoàn thành danh sách.

## 9. Tiêu chí hoàn thành

- Có báo cáo ngắn cho từng bài đã chạy, bao gồm cả trường hợp bị chặn đúng.
- Mỗi phát hiện có bước tái hiện, ảnh hưởng, cách sửa và kết quả thử lại.
- Có test cho các ranh giới trọng yếu: anonymous, sai role, sai owner, token lỗi, CSRF và refresh reuse khi đã triển khai.
- Không còn endpoint lab hoặc cấu hình cố ý yếu trong bản dùng để triển khai.
- Giải thích được authentication, authorization, CSRF và tính toàn vẹn nghiệp vụ bảo vệ những ranh giới khác nhau.

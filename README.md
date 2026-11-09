# TicketLock Mini

Lab học bảo mật: [kiểm thử các lỗ hổng trên TicketLock](docs/security-testing-lab.md).

Bài thực hành Spring Security và HTTP session, dùng Java 21, Spring Boot và Gradle.

Hướng phát triển: nền tảng đặt vé cạnh tranh cao, engine giữ ghế để nghiên cứu concurrency/CPU cache và quy trình thanh toán phân tán bằng Saga, Outbox, Kafka. Xem [thiết kế nâng cao](TICKETLOCK_ADVANCED_DESIGN.md). Đây là kiến trúc dự kiến, chưa phải chức năng đã triển khai.

## Chạy ứng dụng

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

Mở http://localhost:8080/hello. Spring Security chuyển người chưa đăng nhập đến `/login`.
Username development mặc định là `user`; mật khẩu ngẫu nhiên xuất hiện sau
`Using generated security password:` trong terminal. Đăng nhập thành công sẽ mở
request đã lưu, hoặc `/hello` nếu không có request trước đó.

## Endpoint thực hành

| Endpoint | Hành vi |
|---|---|
| `/hello` | Yêu cầu đăng nhập, trả lời chào |
| `/lab/session` | Public, tạo session nếu chưa có và tăng `visitCount` |
| `/lab/session/status` | Public, đọc session hiện có mà không tự tạo session |

Endpoint lab chỉ phục vụ học tập, cần loại bỏ trước khi triển khai thực tế.

## Kiểm tra và đóng gói

```bash
./gradlew clean test bootJar
```

JAR nằm trong `build/libs/`, báo cáo test ở `build/reports/tests/test/index.html`.
Không cần cài Gradle riêng; wrapper tải Gradle và dependency khi chạy lần đầu.

Nếu VS Code còn báo lỗi import sau khi chuyển từ Maven, chạy
**Java: Clean Java Language Server Workspace** từ Command Palette, cho phép khởi động
lại rồi import dự án Gradle. Chọn JDK 21 cho dự án.

Thiết kế và lộ trình học: [TICKETLOCK_MINI_DESIGN.md](TICKETLOCK_MINI_DESIGN.md).

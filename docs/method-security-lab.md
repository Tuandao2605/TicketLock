# Thực hành Method Security

## Mục tiêu

Phân biệt kiểm tra quyền theo URL trong SecurityFilterChain và kiểm tra quyền
trước khi chạy method service bằng `@PreAuthorize`.

## Code liên quan

- `SecurityConfig`: `@EnableMethodSecurity` bật xử lý annotation.
- `security/lab/MethodSecurityLabController`: nhận GET `/api/method-security/admin-message`.
- `security/lab/MethodSecurityLabService`: `adminMessage()` yêu cầu ADMIN.

Endpoint không nằm dưới `/api/admin/**`, vì vậy rule HTTP chỉ yêu cầu đăng nhập.
CUSTOMER đi qua controller nhưng bị chặn trước khi thân method service chạy.

## Thực hành Postman

Khởi động lại backend. Login JSON với CSRF và giữ cookie như bài trước.

| Người gọi      | GET /api/method-security/admin-message |
| -------------- | -------------------------------------- |
| Chưa đăng nhập | 401                                    |
| CUSTOMER       | 403                                    |
| ADMIN          | 200, JSON có message                   |

Khi đổi tài khoản, logout bằng POST kèm CSRF mới hoặc dùng cookie jar riêng.
Request GET của bài này không yêu cầu CSRF token.

## Quan sát bằng Debug

1. Đặt breakpoint tại dòng gọi `service.adminMessage()` trong controller.
2. Đặt thêm breakpoint tại dòng `return` bên trong service.
3. CUSTOMER: dừng ở controller, tiếp tục sẽ nhận 403; không dừng trong thân service.
4. ADMIN: dừng được ở cả hai chỗ, sau đó trả 200.

## Luồng xử lý

```text
Request → HTTP security (đã đăng nhập?)
        → Controller
        → Spring proxy (@PreAuthorize kiểm tra ADMIN)
        → Thân method service
```

Controller phải gọi bean service do Spring quản lý. Không tự `new` service.
Gọi method có annotation từ chính object đó bằng `this.adminMessage()` thường
không đi qua proxy nên không áp dụng được kiểm tra này.

ADMIN không tự có role CUSTOMER. Annotation này không thay thế ownership:
khi có booking, vẫn cần kiểm tra booking thuộc về user đang gọi.

## Câu hỏi tự trả lời

1. Vì sao CUSTOMER vào được controller nhưng không vào được thân method service?
2. `@EnableMethodSecurity` có vai trò gì?
3. Nếu cùng endpoint cũng bị chặn ở HTTP, có thể quan sát kiểm tra service bằng request đó không?
4. Vì sao không tự tạo service bằng `new`?

Các endpoint trong package lab chỉ phục vụ học tập, cần gỡ trước khi triển khai.

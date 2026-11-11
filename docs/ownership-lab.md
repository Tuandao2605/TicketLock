# Bài thực hành quyền sở hữu dữ liệu

## Mục tiêu

Cùng role CUSTOMER không có nghĩa được xem hồ sơ của mọi customer.
UUID khó đoán không thay thế kiểm tra quyền sở hữu ở backend.

## Code

```text
user/controller/UserController → user/service/UserService
                                       |
                          @PreAuthorize gọi UserSecurity.isSelf()
                                       |
                              UserRepository → UserMapper → UserResponse
```

`@PreAuthorize("@userSecurity.isSelf(#userId, authentication)")` lấy UUID từ
request và Authentication từ SecurityContext để đối chiếu với tài khoản DB.
`@P("userId")` chỉ rõ tên tham số cho biểu thức. Password hash không có trong DTO.

## Thử bằng Postman

1. Đăng ký hai tài khoản A và B; login từng tài khoản để lấy UUID qua GET `/api/users/me`.
2. Lưu UUID của cả hai, sau đó login A và giữ cookie phiên A.
3. GET `/api/users/{UUID-của-A}` phải trả 200.
4. Đổi thành UUID của B: phải trả 403 dù A và B đều là CUSTOMER.
5. UUID hợp lệ nhưng không tồn tại cũng trả 403; UUID sai định dạng trả 400.
6. Xóa cookie và thử lại: trả 401.

Ở bài này ADMIN cũng chỉ được đọc hồ sơ chính mình, chưa có chức năng quản trị
hồ sơ người khác. Nếu sau này thêm, phải thiết kế quyền riêng.

GET không cần CSRF token. Khi login/logout vẫn dùng CSRF và cookie như bài trước.

## Debug

Đặt breakpoint trong `UserSecurity.isSelf()` và trong `UserService.profile()`.
Khi A yêu cầu hồ sơ B: kiểm tra trả false, thân method service không chạy.
Khi A yêu cầu hồ sơ A: kiểm tra trả true, service đọc dữ liệu rồi mapper tạo DTO.

## Tự trả lời

1. Vì sao hasRole('CUSTOMER') chưa đủ bảo vệ hồ sơ?
2. Vì sao không tin userId do client gửi hoặc chỉ so sánh hai giá trị client cung cấp?
3. Mapper có nhiệm vụ kiểm tra quyền không?
4. Vì sao API không trả JPA entity User trực tiếp?

Đây là nền tảng để kiểm tra ownership booking khi triển khai nghiệp vụ đặt vé.
Không sửa schema database cho bài này.

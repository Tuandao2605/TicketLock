# ADV-007 — API quản trị và snapshot ghế

**Trạng thái:** DONE — tự review API, phân quyền và bằng chứng kiểm thử trước ADV-008.

## Đã triển khai

- Session chain cho phép đọc công khai Event/Show PUBLISHED; Event chỉ hiển thị khi có Show PUBLISHED. Show DRAFT/CANCELLED trả 404 ở các endpoint công khai. Phân trang Event/Show mặc định 20, tối đa 100.
- Các thao tác tạo Event/Show, import nhãn, đặt giá và publish yêu cầu ADMIN qua method security; POST/PUT vẫn cần cookie CSRF và header `X-XSRF-TOKEN`. Không có bearer JWT cho các URL này. Response lỗi API dùng `code`, `message`, `traceId`; header `X-Trace-Id` do server tạo.
- Admin import danh sách nhãn, sau đó đặt **một giá chung** cho toàn bộ ghế hiện có của show DRAFT bằng `PUT /api/v1/shows/{showId}/price` (`{"amount":150000,"currency":"VND"}`). `amount` là số nguyên theo đơn vị nhỏ nhất. Các ghế thêm sau khi đặt giá cần được đặt giá lại. Publish chỉ thành công khi show có ghế và mọi ghế có giá/currency hợp lệ; không đổi layout/giá qua service sau khi mở bán.
- Snapshot trả showSeat ID, nhãn, trạng thái và giá, không có danh tính người giữ. Version tăng khi import layout hoặc đặt giá. Snapshot khóa hàng Show trong transaction; ADV-009 phải đồng bộ thay đổi trạng thái ghế và tăng version trong cùng transaction trước khi công khai hold.
- [V4](../../../backend/src/main/resources/db/migration/V4__show_seat_prices.sql) thêm giá và version, không sửa V1–V3. Các show cũ chưa có giá không được tự gán giá. Nếu show cũ đã PUBLISHED nhưng chưa có giá, snapshot trả 409 và API không tự thay đổi giá sau mở bán; cần đối chiếu và thực hiện quy trình migration dữ liệu riêng trước khi công khai. Không baseline hay sửa database local/demo trong ticket này.

Hợp đồng, ví dụ và mã lỗi: [OpenAPI](../api/openapi.yaml) · [contract](../api/contract.md).

## Kiểm thử và giới hạn

- Test MockMvc/PostgreSQL xác nhận ADMIN tạo → import → đặt giá → publish; public đọc được, DRAFT không lộ; CUSTOMER, người chưa đăng nhập và request thiếu CSRF không được thao tác quản trị; không sửa giá/layout sau publish.
- Bộ test backend đầy đủ với PostgreSQL/Redis test riêng: **44 test thành công, 0 lỗi, 0 bỏ qua** ngày 27/11/2026. Test migration chạy V1–V4 trên DB sạch và nâng cấp fixture V1.
- Hold, reservation và API Booking chưa triển khai; snapshot AVAILABLE không bảo đảm giữ được ghế. Tính bất biến layout hiện áp dụng qua service, không ngăn người có quyền trực tiếp sửa DB. Dữ liệu thật cũ chưa được nâng cấp trong lần này.

## Tự review trước ADV-008

Đã đối chiếu controller, security chain, snapshot/giá, migration V4 và 44 test đã chạy ở môi trường test. Các endpoint đọc chỉ trả dữ liệu đã publish; thao tác thay đổi yêu cầu ADMIN và CSRF. Giá cũ NULL, JWT cho API này và hiệu lực snapshot vẫn có giới hạn như mô tả trên. Đây là tự review, không thay thế review PR độc lập.

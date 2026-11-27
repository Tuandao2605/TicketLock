# Sprint 1 — Nền tảng cho TicketLock Advanced

**Trạng thái:** DONE — các ticket và điều kiện Sprint 1 đã được kiểm tra · **Thời lượng tham khảo:** 2 tuần · **Milestone:** chuẩn bị M1

Sprint 1 tạo ranh giới module, đường nâng cấp database, hợp đồng API và môi trường kiểm thử. Đầu ra là nền tảng để bắt đầu Event/Show/Seat ở Sprint 2. Đây là kế hoạch làm việc; các ô chưa đánh dấu không có nghĩa đã được triển khai.

ADR: [0001 — Modular monolith](0001-modular-monolith.md) · Backlog tổng: [SPRINT_BACKLOG.md](../SPRINT_BACKLOG.md) · Thiết kế: [Advanced](../../../TICKETLOCK_ADVANCED_DESIGN.md)

## Hiện trạng và phạm vi

- Backend là một Spring Boot application; PostgreSQL lưu user/refresh token, Redis lưu HTTP session. `ddl-auto=update` đang quản lý schema tại thời điểm viết kế hoạch.
- React đăng nhập bằng session + CSRF. JWT API tồn tại qua profile `jwt`; React chưa tích hợp JWT refresh tự động.
- Code hiện tại có `auth`, `user`, `security` và các endpoint lab; chưa có Event, Show, Seat, Reservation hay Booking.
- Sprint 1 **không triển khai** giữ ghế, payment, Kafka, microservice, engine Disruptor hoặc UI booking.
- Không chỉnh sửa/xóa dữ liệu local đang có khi kiểm tra migration; thử trước trên bản sao database hoặc database test độc lập.

## Thứ tự và sức chứa

```text
ADV-001 ──┬──> ADV-002 ──┐
          └──> ADV-003 ──┴──> ADV-004
```

| Thứ tự | Ticket | Ưu tiên | Độ lớn | Đầu ra |
|---:|---|---|---|---|
| 1 | ADV-001 — ADR và module boundary | P0 | M | Quyết định kiến trúc được ghi và review |
| 2 | ADV-002 — Versioned migration | P0 | L | DB sạch và bản sao DB Mini nâng cấp được |
| 3 | ADV-003 — API contract | P0 | M | OpenAPI draft và bảng lỗi được review |
| 4 | ADV-004 — CI và test environment | P1 | M | Pipeline chạy trên tài nguyên test độc lập |

Các bước nhỏ là checklist của ticket cha, không phải ticket độc lập đã làm xong. Phân bổ theo 20–30 giờ/tuần chỉ là ước lượng; ADV-002 có rủi ro lớn nhất vì phải bảo toàn dữ liệu. Nếu không đủ sức chứa, ưu tiên P0, chia nhỏ ADV-002 và chuyển ADV-004 sang sprint sau. Không đánh dấu Sprint 1 hoàn tất đầy đủ nếu ADV-004 chưa đạt.

Một gợi ý lịch làm việc: ngày 1–2 cho ADV-001; ngày 3–6 cho ADV-002; ngày 7–8 cho ADV-003; ngày 9–10 cho ADV-004 và review. Điều chỉnh sau khi đo thời gian thực tế, không coi lịch này là cam kết.

---

## ADV-001 — ADR kiến trúc và ranh giới module

**Trạng thái:** DONE — ADR đã tự review theo tiêu chí ticket · **Ưu tiên:** P0 · **Size:** M · **Phụ thuộc:** không

### Mục tiêu

Chốt ai sở hữu dữ liệu và ai được gọi ai trước khi tạo Event/Inventory. Làm rõ hiện trạng Mini so với kiến trúc đích để người mới không tưởng module dự kiến đã chạy.

### Đầu vào

- [Thiết kế Advanced](../../../TICKETLOCK_ADVANCED_DESIGN.md), phần 3–8.
- Source hiện có: `backend/src/main/java/com/ticketlock/auth`, `user`, `security` và controller lab ở package gốc.
- Hai luồng xác thực: session/CSRF cho React; bearer JWT cho API JWT khi profile `jwt` bật.

### Quyết định triển khai

- Một ứng dụng Spring Boot và một PostgreSQL cho M1; giao tiếp giữa module qua service interface.
- Event sở hữu Event, Show và layout Seat; Inventory sở hữu ShowSeat, reservation và trạng thái giữ ghế. Booking sở hữu booking; User sở hữu tài khoản/role/password hash, Auth sở hữu xác thực/session/refresh token và gọi interface User.
- Hold hết hạn khi `now >= deadline` theo đồng hồ server; logout không giải phóng hold. Transaction M1 nằm trong một database.
- Session principal và JWT `sub` dùng UUID; email dùng để login/hiển thị. Các phiên email cũ cần cutover an toàn và đăng nhập lại.
- Đã chọn A: khóa sơ đồ ghế của Show sau khi mở bán; ngừng bán không tự mở khóa. Layout dùng chung không được làm thay đổi Show đã mở bán.
- Giá được chốt lúc giữ ghế thành công: server lưu snapshot giá và currency trong cùng transaction, checkout dùng snapshot khi hold còn hợp lệ. Quy trình xử lý giá thay đổi trước hold sẽ được thiết kế ở ADV-003.
- ADR ghi các quyết định này cùng lý do và hệ quả; thay đổi phải cập nhật ADR và các ticket liên quan.

### Việc cần làm

- [x] Kiểm kê package, endpoint, bảng và nơi lưu session thực tế; ghi phần nào đã tồn tại.
- [x] Viết `docs/advanced/sprint-01/0001-modular-monolith.md` theo các mục: bối cảnh, quyết định, phương án đã cân nhắc, hệ quả và câu hỏi mở.
- [x] Mô tả sơ đồ một backend/deploy, PostgreSQL và Redis; ghi rõ Kafka, service tách riêng và engine mới là giai đoạn sau.
- [x] Lập bảng ownership dự kiến: user → user, role và password hash; auth → xác thực, session và refresh; event → event/show metadata; inventory → trạng thái ghế và reservation; booking → booking/Saga sau này; payment → payment operation; ticket → ticket/check-in. Show/seat layout và current seat state cần chỉ rõ module sở hữu duy nhất trước khi tạo schema.
- [x] Chốt cách module gọi qua service/public interface. Controller không gọi trực tiếp repository của module khác; dependency giữa module không được tạo vòng lặp.
- [x] Ghi trust boundary: lấy user từ `Authentication`; không tin `ownerId`, giá hoặc role trong request; session cookie và bearer JWT đi qua chain tương ứng.
- [x] Ghi invariant: một show-seat có tối đa một hold hợp lệ; giữ nhóm ghế thành công toàn bộ hoặc không ghế nào; confirm/release phải kiểm tra reservation/version/deadline; chỉ trả thành công sau commit bền.
- [x] Giải thích Event là sự kiện, Show là một suất cụ thể. Quyết định quy tắc hủy/hết hạn hold, và logout không tự hủy booking.
- [x] Cập nhật liên kết thiết kế/backlog tới ADR sau khi tạo.

### Kết quả và nghiệm thu

- ADR phân biệt **hiện tại / quyết định cho M1 / dự kiến phân tán**, có bảng dữ liệu, transaction boundary và interface giữa module.
- Mô tả rõ transaction chỉ trong một DB ở monolith; sau khi tách service không giả định JPA transaction bao trùm nhiều DB.
- Có ví dụ request đặt giữ ghế và giải thích ai kiểm tra principal, owner, deadline và tính bền.
- Review được: đổi role trong client body không đổi quyền; hai hold cùng một ghế không được cùng hợp lệ; một ghế lỗi trong nhóm không tạo hold một phần.
- **Ngoài phạm vi:** không tạo package rỗng hàng loạt hoặc triển khai nghiệp vụ chỉ để hoàn thành ADR.

### Cách bắt đầu ngay

1. Đọc `SecurityConfig`, `LoginController`, `UserService` và `RefreshTokenService` để xác định hiện trạng.
2. Vẽ luồng request từ client → filter chain → controller → service → repository; đánh dấu Redis chỉ lưu session, PostgreSQL lưu user/refresh.
3. Viết bảng module ownership và các invariant vào ADR; sau đó tự review theo ba tình huống ở phần nghiệm thu.

**Tiến độ:** [ADR 0001](0001-modular-monolith.md) đã có sơ đồ, bảng ownership, kiểm kê code và ba tình huống tự review. Đã tự review ba tình huống và chốt UUID, khóa sơ đồ sau mở bán, snapshot giá lúc hold. Quy trình giá/phí được tiếp tục ở ADV-003; các invariant hold vẫn cần kiểm thử trong ADV-009/ADV-012.

**Bằng chứng hoàn thành:** link ADR, sơ đồ, bảng ownership và nội dung review trong PR/tài liệu.

---

## ADV-002 — Migration versioned và nâng cấp DB Mini

**Trạng thái:** DONE — đã tự review triển khai và bằng chứng test · **Ưu tiên:** P0 · **Size:** L · **Phụ thuộc:** ADV-001

### Mục tiêu

Database sạch được tạo bằng migration có phiên bản; bản sao DB Mini hiện có nâng cấp mà bảo toàn user, UUIDv7, password hash và refresh token. Ứng dụng kiểm tra schema bằng `validate` sau khi migration chạy.

### Quyết định triển khai

- Dùng Flyway với migration SQL trong `backend/src/main/resources/db/migration`; kiểm tra module PostgreSQL phù hợp với phiên bản Spring Boot trước khi thêm dependency.
- `V1` tạo schema Mini cho DB rỗng. Với DB cũ, chỉ baseline ở version 1 sau khi kiểm tra schema tương đương V1; các thay đổi về sau dùng V2 trở lên. Không bật baseline tự động cho mọi database.
- Nếu schema cũ khác V1, dừng, ghi khác biệt và thiết kế đường nâng cấp riêng trên bản sao; không giả vờ V1 đã được áp dụng.

### Việc cần làm

- [x] Kiểm kê schema thực tế và đối chiếu entity `User`, `RefreshToken`, index, unique constraint, foreign key; ghi cả khác biệt do `ddl-auto=update` có thể đã tạo.
- [x] Ghi lý do chọn Flyway và kiểm tra dependency/tương thích với phiên bản Spring Boot/PostgreSQL đang dùng trước khi thêm vào `backend/build.gradle`.
- [x] Thiết kế migration đầu tiên cho DB sạch, kèm quy trình baseline DB đã có schema. Phân biệt **schema mới** với **DB cũ đã có bảng**; không mặc định đánh dấu baseline khi chưa đối chiếu schema.
- [x] Tạo hai database test độc lập: một DB rỗng và một DB phục dựng từ bản sao dữ liệu Mini giả lập; không thao tác phá hủy trên volume đang dùng.
- [x] Viết migration và hướng dẫn backup/restore, cách kiểm tra schema/version/checksum. Giữ script UUID cũ như công cụ lịch sử nếu còn cần, ghi rõ điều kiện sử dụng.
- [x] Chuyển `spring.jpa.hibernate.ddl-auto=validate` sau khi schema migration khớp entity. Không dùng `clean` hoặc `docker compose down -v` để xử lý lỗi migration.
- [x] Kiểm tra nâng cấp DB có user/refresh token bằng dữ liệu giả: so UUID, role, enabled, password hash và quan hệ trước/sau. Không ghi mật khẩu/hash/token thật vào log hoặc tài liệu.
- [x] Cập nhật README và mô tả cách xử lý khi baseline/schema/checksum không khớp; dừng và điều tra thay vì sửa migration đã chạy tùy tiện.

### Kết quả và nghiệm thu

- DB rỗng migrate rồi app khởi động thành công với `validate`.
- Bản sao DB cũ nâng cấp được, các bản ghi và quan hệ kiểm tra trước/sau không mất hoặc thay đổi ngoài dự kiến; đăng nhập bằng tài khoản test sau nâng cấp thành công.
- Khởi động/migrate lần hai không chạy trùng; migration history phản ánh đúng phiên bản.
- Có bằng chứng trường hợp DB cũ sai schema dừng an toàn và hướng dẫn khôi phục. Có quy trình backup/restore đã thử trên dữ liệu test.
- **Ngoài phạm vi:** chưa tạo bảng Event/Booking, chưa xóa database local.

**Tiến độ:** [Hướng dẫn ADV-002](ADV_002_MIGRATION.md) ghi kết quả DB rỗng và baseline fixture. Đã đối chiếu bản sao DB local và diễn tập backup/restore, đăng nhập, khởi động lại và từ chối DB chưa baseline với fixture giả. Đã kiểm chứng baseline trên bản sao demo, bảo toàn dữ liệu và refresh token giả sau restore. Đã tự review migration, cấu hình và bằng chứng; DB gốc/demo vẫn cần quy trình riêng khi nâng cấp.

**Bằng chứng hoàn thành:** migration, log đã che thông tin nhạy cảm, kết quả kiểm tra DB rỗng/DB cũ và hướng dẫn nâng cấp.

---

## ADV-003 — Hợp đồng API và lỗi nghiệp vụ

**Trạng thái:** DONE — draft đã tự review và validate · **Ưu tiên:** P0 · **Size:** M · **Phụ thuộc:** ADV-001

### Mục tiêu

Viết hợp đồng có thể review trước khi code Event/Reservation, bao gồm authorization, input, output, idempotency và lỗi. Endpoint trong tài liệu phải đánh dấu **dự kiến**, tránh nhầm với endpoint Mini đang chạy.

### Quyết định triển khai

- Viết OpenAPI 3 YAML thủ công tại `docs/advanced/api/openapi.yaml`, đánh dấu draft; mỗi endpoint dự kiến ghi rõ actor, credential và lỗi.
- Dùng `Idempotency-Key` theo principal + operation + key; cùng key và payload trả kết quả cũ, khác payload trả 409, đang xử lý trả trạng thái pending có thể tra cứu. ADV-011 triển khai lưu bền.
- Lỗi mới dùng `code`, `message`, `traceId`; 409 cho xung đột nghiệp vụ, 429 cho rate limit, 503 cho queue đầy hoặc dịch vụ chưa sẵn sàng.

### Việc cần làm

- [x] Tạo tài liệu versioned trong `docs/advanced/api/` và chỉ rõ trạng thái draft. Không đưa endpoint giả vào controller để làm tài liệu.
- [x] Mô tả Event/Show list/detail, admin tạo/publish, snapshot ghế, tạo/đọc/hủy reservation và booking dự kiến; xác định UUID, pagination, thời gian UTC và giới hạn số ghế.
- [x] Ghi rõ endpoint thực tế hiện có cho đăng ký/login/session và JWT chỉ để tham chiếu; không tự xem mọi `/api/**` đều là JWT.
- [x] Với từng endpoint dự kiến, mô tả method/path, actor, credential, CSRF nếu dùng session cookie, request/response ví dụ và trạng thái lỗi. Không đưa token thật vào ví dụ.
- [x] Định nghĩa `Idempotency-Key` cho thao tác cần retry: phạm vi theo user + operation + key; cùng key/cùng payload, cùng key/khác payload và request đang xử lý. Ghi rõ việc lưu record bền sẽ được triển khai ở ADV-011.
- [x] Quy định correlation ID và response lỗi ổn định (ví dụ `code`, `message`, `traceId`); không cam kết format đó đã áp dụng cho mọi endpoint Mini.
- [x] Lập bảng 400/401/403/404/409/429/503, phân biệt xung đột ghế 409, giới hạn tần suất 429 và hàng đợi đầy/dịch vụ chưa sẵn sàng 503. CSRF thiếu/sai ở session chain có thể trả 403.
- [x] Rà lại quyền sở hữu: client không quyết định owner, giá hoặc trạng thái payment; không suy ra authorization từ UUID khó đoán.

### Kết quả và nghiệm thu

- OpenAPI parse/validate được; mọi ví dụ dùng dữ liệu giả. Endpoint dự kiến được gắn nhãn rõ.
- Reviewer xác định được khi nào 401 khác 403, 409 khác 503, và cách retry khi request timeout sau commit.
- Request customer không có trường cho phép tự cấp `ownerId` hoặc giá; role/ownership được lấy và kiểm tra phía server.
- Hợp đồng không tuyên bố queue, rate limit hoặc booking đã có; không phá hợp đồng login hiện hành.

**Bằng chứng:** [OpenAPI draft](../api/openapi.yaml), [quy tắc và bảng lỗi](../api/contract.md); `openapi-spec-validator` 0.9.0 kiểm tra thành công. Có ví dụ hold thành công, conflict và không đủ quyền. Đây là hợp đồng, chưa phải endpoint đã triển khai.

---

## ADV-004 — CI và môi trường kiểm thử tích hợp

**Trạng thái:** DONE — kiểm thử local và GitHub Actions đã có bằng chứng · **Ưu tiên:** P1 · **Size:** M · **Phụ thuộc:** ADV-002, ADV-003

### Mục tiêu

Từ clone sạch, tự động build và kiểm tra backend/frontend với PostgreSQL, Redis và database test độc lập; kiểm tra migration trước khi bắt đầu nghiệp vụ mới.

### Quyết định triển khai

- Dùng service containers PostgreSQL và Redis trong GitHub Actions; test local kết nối service test độc lập qua cấu hình test.
- Pipeline chạy backend test và kiểm tra migration, sau đó build frontend; dùng dữ liệu giả và không dùng volume ứng dụng.

### Việc cần làm

- [x] Ghi điều kiện Docker, Java 21, Node theo `frontend/package.json` và Gradle Wrapper.
- [x] Tạo cấu hình test với DB test và namespace Redis test riêng; không dùng cookie/session hoặc dữ liệu ứng dụng local. Không log credentials.
- [x] Bổ sung workflow phù hợp repository: build frontend, backend test, migrate DB sạch và validate schema; cache dependency nếu cần nhưng không cache secrets/DB.
- [x] Đưa bài test migration từ ADV-002 vào luồng có thể chạy lặp; nếu cần DB legacy fixture thì dùng dữ liệu giả đã xác định.
- [x] Ghi lệnh chạy local và điều kiện services healthy trong README; chỉ ra artifact báo cáo lỗi để debug.
- [x] Thử fixture migration sai và ghi bằng chứng lệnh kiểm thử CI thất bại tại local; fixture nằm ngoài repository và không được merge.

### Kết quả và nghiệm thu

- Clone sạch chạy pipeline xanh và cho báo cáo đủ debug khi lỗi; không cần mật khẩu thật.
- Kiểm tra migration lỗi làm CI đỏ; test không đụng dữ liệu PostgreSQL/Redis của ứng dụng đang chạy.
- Chạy lại pipeline không phụ thuộc thứ tự hay dữ liệu phiên cũ. Ghi lại các bài cần Docker và cách chạy riêng nếu có.
- **Ngoài phạm vi:** chưa cần cluster Kafka, benchmark CI hay production deployment.

**Bằng chứng local (24/11/2026):** 29 test backend thành công, gồm kiểm thử Redis nhiều instance và 3 test migration; `npm ci` và build frontend thành công. Chỉ định migration V1 giả sai qua `SPRING_FLYWAY_LOCATIONS` khiến test context thất bại với `FlywayValidateException`, rồi chạy lại bộ test bình thường thành công. Review bổ sung: bản sao cây mã hiện tại không có `.env`, build cache hay node_modules đã chạy 29 test backend và build frontend thành công; đã chạy lại với Redis không mật khẩu tương tự service GitHub Actions. Lượt chạy GitHub Actions CI #1 cho commit `3c59692` trên `main` có trạng thái thành công theo ảnh người dùng cung cấp; ảnh chỉ xác nhận kết quả tổng thể, không hiển thị chi tiết từng bước. Kiểm thử migration sai được thực hiện local, không phải lần chạy lỗi trên GitHub. Xem [README](../../../README.md#kiểm-thử-ci-với-postgresql-và-redis-riêng) và `.github/workflows/ci.yml`.

---

## Review cuối Sprint 1

- [x] ADV-001 DONE: ADR được review, trách nhiệm và invariant nhất quán.
- [x] ADV-002 DONE: migration DB sạch + nâng cấp bản sao DB cũ thành công, không mất dữ liệu test.
- [x] ADV-003 DONE: OpenAPI draft hợp lệ, authorization/idempotency/lỗi được giải thích.
- [x] ADV-004 DONE: CI dùng PostgreSQL/Redis test độc lập và bắt được lỗi có chủ đích.
- [x] README chỉ đúng cách khởi động và kiểm tra; các thay đổi Mini đang có được bảo toàn.
- [x] Ghi review cuối sprint: chưa có số giờ thực tế; vấn đề đã gặp là cô lập schema test và database cũ chưa baseline. Không có ticket Sprint 1 chuyển sprint; các invariant nghiệp vụ tiếp tục kiểm thử ở Sprint 2–3. ADV-002 đã hoàn thành; có thể bắt đầu ADV-005.

Mẫu ghi ticket khi làm:

```text
Mã / trạng thái:
Nhánh / PR:
Đầu vào và quyết định:
Các bước đã làm:
Kết quả thực tế và bằng chứng:
Lỗi / câu hỏi chưa giải quyết:
Bước tiếp theo:
```

# TicketLock Advanced — Sprint và ticket triển khai

## 1. Phạm vi và cách dùng

Backlog triển khai [thiết kế Advanced](../../TICKETLOCK_ADVANCED_DESIGN.md), tiếp nối
[Mini](../mini/TICKETLOCK_MINI_DESIGN.md). Tất cả ticket bắt đầu ở **TODO**; mô tả ticket
không có nghĩa chức năng đã tồn tại. Chỉ chuyển DONE sau khi có code, bằng chứng kiểm
thử và tài liệu chạy lại.

Hiện trạng làm đầu vào:

- Java 21, Spring Boot, PostgreSQL; HTTP session luôn lưu Redis, không có fallback RAM.
- React đang dùng JSON session login và cookie CSRF. JWT API và refresh rotation có
  sẵn qua profile `jwt`; React chưa dùng JWT tự động.
- ADMIN/CUSTOMER, method security, ownership hồ sơ đã có. Chưa có Event/Show/Seat,
  Reservation, thanh toán, vé, Kafka hoặc engine giữ ghế.
- Redis session phục vụ đăng nhập. Giữ ghế thuộc user/reservation trong Inventory,
  không gắn tuổi thọ vào session, cookie hay sự kiện logout.

Đây là **backlog repository**, chưa tạo issue trên GitHub/Jira. Khi tạo issue, dùng
mã `ADV-NNN` trong tiêu đề, branch và PR để truy vết.

## 2. Nhịp sprint và quy tắc chọn việc

Giả định một người học và triển khai, sprint hai tuần, khoảng 20–30 giờ mỗi tuần.
Mười hai sprint là khung 24 tuần, không phải cam kết hoàn thành; đây là phạm vi sau
hai tuần học Security Mini. Hai sprint đầu dùng để đo năng lực thực tế và chia lại
khối lượng. Nếu chỉ có 10–15 giờ/tuần, kéo dài hoặc giảm số ticket trong sprint.

- `P0`: bảo vệ tính đúng đắn/bảo mật, chặn release của mốc tương ứng.
- `P1`: cần để hoàn chỉnh tính năng hoặc demo của mốc.
- `P2`: mở rộng, chỉ lấy khi P0/P1 đã xong.
- `S/M/L`: độ lớn tương đối, dự kiến tối đa khoảng 1/2/4 ngày làm việc tập trung;
  chưa gồm thời gian học công nghệ. Ticket vượt L cần tách trước khi bắt đầu.
- Sprint là thứ tự mục tiêu. Không kéo ticket chưa đủ dependency vào làm để chạy kịp lịch.
- Không đặt mục tiêu throughput trước baseline. Không lấy JMH làm bằng chứng latency HTTP.

Mỗi ticket có người phụ trách, trạng thái TODO/IN_PROGRESS/REVIEW/DONE và liên kết PR
khi được đưa vào công cụ quản lý. Không gán DONE chỉ vì đã viết hướng dẫn.

## 3. Các mốc bàn giao

| Sprint | Mục tiêu | Demo cuối sprint |
|---|---|---|
| 1 | Nền domain, migration và ranh giới module | DB migration chạy lại được, kiến trúc thống nhất |
| 2 | Event, Show và sơ đồ ghế | Admin publish suất, customer xem ghế |
| 3 | Giữ ghế bền, all-or-nothing | Nhiều request tranh ghế chỉ có một người thắng |
| 4 | Booking và trải nghiệm giữ ghế | Đặt, xem, hủy; restart không mất hold |
| 5 | Engine benchmark độc lập | So sánh lock/CAS/Disruptor trên cùng workload |
| 6 | Single-writer tích hợp | API đi qua queue, DB commit rồi mới báo thành công |
| 7 | Partition ownership và fencing | Owner cũ mất lease không thể ghi tiếp |
| 8 | Tách service, Outbox/Inbox/Kafka | Crash/retry không mất chuyển trạng thái nghiệp vụ |
| 9 | Payment sandbox và Saga | Charge → confirm; hold hết hạn → refund |
| 10 | Phát vé và check-in | Một vé chỉ check-in thành công một lần |
| 11 | Phục hồi và vận hành | Đối soát trạng thái không rõ, replay có kiểm soát |
| 12 | Load test, hardening và release | Demo đầy đủ, báo cáo tính đúng đắn/hiệu năng |

- **M1 — cuối sprint 4:** modular monolith đặt ghế đúng, chưa tuyên bố đã có Saga.
- **M2 — cuối sprint 7:** engine được đo, tích hợp và bảo vệ owner xuyên instance.
- **M3 — cuối sprint 10:** luồng phân tán charge/confirm/refund/ticket/check-in.
- **M4 — cuối sprint 12:** phục hồi, vận hành và bằng chứng demo có thể tái lập.

Các quyết định trong từng ticket là hướng triển khai mặc định cho lộ trình này, không khẳng định chức năng đã tồn tại. Khi thực tế hoặc kết quả benchmark bác bỏ một quyết định, ghi lý do và tác động vào ADR/ticket liên quan trước khi đổi hướng; cập nhật các ticket phụ thuộc.

## 4. Definition of Ready và Definition of Done

Ticket sẵn sàng khi dependency đã DONE, API/state/schema liên quan rõ ràng, có kịch
bản thành công và thất bại, môi trường chạy và dữ liệu test xác định. Với ticket
benchmark, workload/metric phải được thống nhất trước khi chạy.

Ticket DONE khi:

- Code được review; không lẫn thay đổi ngoài phạm vi ticket.
- Migration versioned chạy được trên DB sạch; có cách nâng cấp dữ liệu hiện có.
- Có kiểm thử phù hợp rủi ro: quyền sở hữu, tranh chấp, idempotency, crash/recovery
  được kiểm thử khi ticket chạm tới; không chỉ kiểm tra HTTP happy path.
- Transaction, timeout và trạng thái lỗi được mô tả; không log credential/payment data.
- README/lab có lệnh chạy và bằng chứng kết quả; CI kiểm tra backend/frontend liên quan.
- Không báo thành công trước khi thay đổi cần bền đã commit. Không đánh dấu side effect
  hoàn tất chỉ vì request đã được gửi hoặc đã đưa message vào queue.

## Sprint 1 — Chuẩn bị modular monolith

Chi tiết các bước, checklist và nghiệm thu: [Sprint 1 chi tiết](sprint-01/SPRINT_01.md). ADR: [0001 — Modular monolith](sprint-01/0001-modular-monolith.md).

**Mục tiêu:** xây nền để phát triển domain mà không tách service quá sớm.

### ADV-001 — ADR kiến trúc, module và trust boundary

**Trạng thái: DONE** — đã tự review thiết kế; kiểm thử invariant thuộc các ticket nghiệp vụ sau.

- **P0 · M · Phụ thuộc:** không.
- **Quyết định:** Dùng modular monolith một deploy; Auth và User tách module; principal session/JWT là UUID. Event sở hữu layout, Inventory sở hữu trạng thái ghế; khóa sơ đồ sau mở bán và chốt giá khi hold thành công. Giao tiếp qua service interface.
- **Phạm vi:** chốt module `auth`, `event`, `inventory`, `booking`, `payment`, `ticket`;
  từng module chia controller/dto/mapper/entity/repository/service theo nhu cầu.
  Giai đoạn đầu một deploy; không để controller gọi repository module khác.
- **Nghiệm thu:** ADR chỉ rõ module sở hữu dữ liệu, transaction và API nội bộ;
  cập nhật sơ đồ hiện tại/đích, phân biệt event với show; liệt kê invariant một ghế
  chỉ thuộc một hold hợp lệ và multi-seat all-or-nothing.

### ADV-002 — Versioned migration và baseline DB Mini

**Trạng thái: DONE** — đã tự review, kiểm thử DB rỗng và bản sao; DB gốc chưa baseline.

- **P0 · L · Phụ thuộc:** ADV-001.
- **Quyết định:** Dùng Flyway và SQL versioned; DB cũ chỉ baseline sau khi đối chiếu schema, DB mới chạy toàn bộ migration.
- **Phạm vi:** chọn Flyway hoặc Liquibase, tạo baseline cho user/refresh hiện có;
  chuyển `ddl-auto` sang `validate` trong cấu hình dùng migration.
- **Nghiệm thu:** DB sạch chạy đủ migration; DB Mini có dữ liệu nâng cấp mà không
  mất user/UUIDv7/hash; có hướng dẫn backup/baseline, không dùng `clean` hay xóa volume
  để sửa schema thật; khởi động lại không chạy trùng migration.

### ADV-003 — Hợp đồng API và lỗi nghiệp vụ

**Trạng thái: DONE** — đã tự review và validate draft · [OpenAPI draft](api/openapi.yaml) · [Quy tắc API](api/contract.md)

- **P0 · M · Phụ thuộc:** ADV-001.
- **Quyết định:** Viết OpenAPI 3 YAML thủ công trong `docs/advanced/api/`; đây là hợp đồng dự kiến, không tạo controller giả.
- **Phạm vi:** OpenAPI cho event/show/reservation/booking, pagination, validation,
  correlation ID và mã lỗi ổn định; quy định idempotency key theo owner + operation.
- **Nghiệm thu:** 400/401/403/404/409/429/503 có trường hợp sử dụng rõ; seat conflict
  khác queue full; lỗi không lộ hash/token; customer không tự truyền ownerId để cấp quyền.

### ADV-004 — CI và môi trường integration test

**Trạng thái: REVIEW** — test trên bản sao cây mã sạch đã chạy; chờ kết quả GitHub Actions trên commit thực tế.

- **P1 · M · Phụ thuộc:** ADV-002, ADV-003.
- **Quyết định:** Dùng PostgreSQL và Redis service containers trên CI; cấu hình test riêng cũng cho phép chạy với các service local độc lập.
- **Phạm vi:** PostgreSQL/Redis test độc lập dữ liệu local; CI build/test backend,
  build frontend và kiểm tra migration. Chọn Testcontainers hoặc dịch vụ CI tương đương.
- **Nghiệm thu:** clone sạch chạy được theo hướng dẫn; test không đọc/ghi session thật;
  secrets không commit; một invariant hoặc migration cố tình sai làm pipeline thất bại.

**Sprint exit:** ADR, API draft và CI đủ để bắt đầu Event/Inventory.

## Sprint 2 — Event, Show và ghế

### ADV-005 — Event/Show schema và trạng thái publish

- **P0 · M · Phụ thuộc:** ADV-002.
- **Quyết định:** Event và Show là hai aggregate; mỗi Show có trạng thái DRAFT, PUBLISHED hoặc CANCELLED và thời gian UTC.
- **Phạm vi:** event là nội dung sự kiện, show là một suất có thời gian/địa điểm và
  sơ đồ ghế riêng; DRAFT/PUBLISHED/CANCELLED; thời gian dùng Instant/UTC trong backend.
- **Nghiệm thu:** không publish show có thời gian hoặc dữ liệu bắt buộc sai; reservation
  gắn showId; migration và DTO không trả entity trực tiếp; sau khi mở bán không thêm, xóa, đổi định danh hay bố trí ghế của Show, kể cả thay đổi gián tiếp qua layout dùng chung.

### ADV-006 — Ghế và trạng thái hiện tại theo show

- **P0 · L · Phụ thuộc:** ADV-005.
- **Quyết định:** Lưu layout Seat thuộc Event và một ShowSeat cho mỗi ghế của từng Show; PostgreSQL là nguồn sự thật trạng thái.
- **Phạm vi:** Seat, ShowSeat; unique `(showId, seatId)`; trạng thái AVAILABLE/HELD/SOLD,
  reservation hiện hành, deadline và version; lịch sử tách khỏi record trạng thái.
- **Nghiệm thu:** ghế trùng trong cùng show bị DB chặn; cùng seat layout có thể được dùng
  bởi hai show độc lập; import layout không tạo ghế mồ côi; có seed demo có thể chạy lại.

### ADV-007 — API quản trị và đọc sơ đồ ghế

- **P0 · M · Phụ thuộc:** ADV-003, ADV-005, ADV-006.
- **Quyết định:** Tách API quản trị ADMIN và API đọc công khai; chỉ trả show đã publish và snapshot không chứa owner; layout version/snapshot không làm thay đổi Show đã mở bán.
- **Phạm vi:** ADMIN tạo/publish show; public xem show PUBLISHED; snapshot ghế có thời
  điểm/version và không công bố danh tính người giữ ghế.
- **Nghiệm thu:** CUSTOMER không sửa show; DRAFT không lộ qua public API; snapshot không
  hứa giữ ghế cho client; token/session phải qua đúng filter chain đã quy định.

### ADV-008 — Giao diện danh sách show và sơ đồ ghế

- **P1 · M · Phụ thuộc:** ADV-007.
- **Quyết định:** Dùng React và session hiện tại; tải snapshot qua HTTP, chưa thêm realtime hay đặt ghế.
- **Phạm vi:** React dùng session/CSRF hiện có; list/detail, trạng thái loading/error,
  phân biệt available/held/sold. Chưa thay luồng login thành JWT SPA.
- **Nghiệm thu:** chọn đúng show và seat ID; xử lý 401/403; không chỉ dựa vào menu ẩn
  để bảo vệ admin; không dùng countdown/client state làm bằng chứng ghế vẫn còn.

**Sprint exit:** tạo show, publish và xem snapshot ghế bằng UI/API.

## Sprint 3 — Reservation đúng và bền

### ADV-009 — Hold nhóm ghế bằng transaction PostgreSQL

- **P0 · L · Phụ thuộc:** ADV-006, ADV-007.
- **Quyết định:** Dùng transaction PostgreSQL và khóa ShowSeat theo thứ tự ID tăng dần; mặc định hold 10 phút, giới hạn nhóm bằng cấu hình.
- **Phạm vi:** giữ 1..N ghế trong 10 phút, giới hạn N được cấu hình; khóa hàng theo thứ
  tự ổn định hoặc conditional update tương đương; lưu snapshot giá từng ghế và currency do server xác định trong cùng transaction; không gọi provider trong transaction.
- **Nghiệm thu:** nhiều request cùng ghế chỉ một hold hợp lệ thắng; thiếu một ghế thì
  cả nhóm rollback; seat trùng/sai show bị từ chối; response chỉ thành công sau commit. Giá niêm yết đổi sau hold không đổi snapshot; quy trình giá đổi trước hold và xác nhận thanh toán được chốt ở ADV-003.

### ADV-010 — Expire, release và confirm có điều kiện

- **P0 · L · Phụ thuộc:** ADV-009.
- **Quyết định:** Dùng deadline theo server và conditional update theo reservation/version; worker chỉ dọn dẹp, không quyết định tính hợp lệ của hold.
- **Phạm vi:** mọi lệnh mang reservation/version; expiry worker có thể chạy lặp;
  quyết định deadline ở server bằng Clock có thể thay trong test.
- **Nghiệm thu:** expire/release của hold cũ không đụng hold mới; confirm hold hết hạn
  bị từ chối dù timer chưa chạy; confirm/release lặp không tạo chuyển trạng thái mới;
  hai request sát deadline cho kết quả theo cùng quy tắc đã ghi trong API.

### ADV-011 — Idempotency cho tạo reservation

- **P0 · M · Phụ thuộc:** ADV-009, ADV-003.
- **Quyết định:** Lưu key, fingerprint và kết quả trong PostgreSQL theo principal + operation + key; retry cùng payload dùng lại kết quả.
- **Phạm vi:** lưu request fingerprint và kết quả bền theo principal + operation + key;
  quy định thời hạn record và response khi thao tác đang xử lý.
- **Nghiệm thu:** retry cùng key/payload trả cùng reservation; cùng key khác payload
  nhận conflict; hai request đồng thời không tạo hai hold; timeout sau commit có API
  truy vấn/retry kết quả, không tự đổi key rồi đặt thêm.

### ADV-012 — Ownership reservation và bộ kiểm thử tranh chấp

- **P0 · M · Phụ thuộc:** ADV-010, ADV-011, ADV-004.
- **Quyết định:** Áp dụng ownership cho customer ở service; ADMIN không mặc nhiên vượt ownership; dùng PostgreSQL thật cho bài test race.
- **Phạm vi:** customer chỉ đọc/hủy hold của mình; lấy owner từ principal. Thêm kiểm
  thử nhiều request tranh một ghế và hai nhóm ghế giao nhau trên DB thật.
- **Nghiệm thu:** user B không đọc/hủy reservation A; role ADMIN không tự bypass ownership
  nếu chưa có policy; kiểm thử khẳng định số hold hợp lệ, rollback và version sau cuộc đua.

**Sprint exit:** backend chứng minh no oversell, all-or-nothing và chống stale command.

## Sprint 4 — Booking và recovery baseline

### ADV-013 — Booking gắn reservation và API tra cứu

- **P0 · M · Phụ thuộc:** ADV-010, ADV-011, ADV-012.
- **Quyết định:** Tạo tối đa một booking logic cho mỗi reservation, lấy owner và giá từ server; booking chưa hàm ý đã thanh toán.
- **Phạm vi:** tạo booking idempotent từ hold của mình; danh sách/detail theo owner;
  state và deadline rõ; chưa coi đã thanh toán hoặc phát vé.
- **Nghiệm thu:** một hold không tạo hai booking logic; booking không thuộc user bị
  chặn; logout không hủy booking; không nhận giá/trạng thái thanh toán từ client làm sự thật.

### ADV-014 — UI giữ ghế, countdown và hủy hold

- **P1 · M · Phụ thuộc:** ADV-008, ADV-013.
- **Quyết định:** Dùng deadline server cho countdown và truy vấn lại sau reload; giữ nguyên idempotency key khi retry cùng ý định.
- **Phạm vi:** thao tác hold, hiển thị deadline từ server, xung đột ghế, trang booking;
  retry giữ nguyên idempotency key cho cùng ý định.
- **Nghiệm thu:** reload truy vấn lại trạng thái; hai tab cùng session không giữ ghế
  thay nhau ngoài ý muốn; client xử lý expired/conflict; request thay đổi dữ liệu có CSRF.

### ADV-015 — Recovery từ PostgreSQL và kiểm thử crash

- **P0 · L · Phụ thuộc:** ADV-010, ADV-013.
- **Quyết định:** Khôi phục trạng thái từ PostgreSQL trước readiness; thử crash trước commit và sau commit trước response.
- **Phạm vi:** khởi động/reconcile hold hiện hành; xử lý deadline đã qua; fault injection
  tại trước commit và sau commit trước response; có lệnh đọc trạng thái sau restart.
- **Nghiệm thu:** committed hold không mất; rollback không để ghế HELD; retry sau mất
  response trả kết quả cũ; không nhận command khi trạng thái phục hồi chưa sẵn sàng.

### ADV-016 — Baseline HTTP và demo M1

- **P1 · M · Phụ thuộc:** ADV-012, ADV-014, ADV-015.
- **Quyết định:** Đo HTTP baseline với workload ghế nóng và phân bố đều; báo cáo success, conflict và latency riêng.
- **Phạm vi:** load test baseline DB, workload đều/hot seat, metrics success/conflict,
  latency và lock wait; ghi phần cứng, dataset và giới hạn tải.
- **Nghiệm thu:** có báo cáo tái lập và kết quả invariant; phân biệt request/s với số
  vé giữ thành công/s; demo M1 không gọi sandbox trả thành công là thanh toán thật.

**Sprint exit / M1:** một backend đặt ghế đúng, có UI và phục hồi sau restart.

## Sprint 5 — Thí nghiệm CPU cache và engine độc lập

### ADV-017 — Module benchmark và lock baseline

- **P0 · M · Phụ thuộc:** ADV-016.
- **Quyết định:** Tạo module JMH riêng với lock baseline và workload có seed; correctness harness chạy riêng khỏi phép đo.
- **Phạm vi:** Gradle module JMH độc lập, command/result contract, partition theo show,
  lock baseline trong bộ nhớ; workload cố định seed, warmup/forks và workload reset.
- **Nghiệm thu:** benchmark không gọi DB/network; không đo mã chết hoặc chỉ đo setup;
  workload không hết ghế ngay đầu rồi chỉ đo rejection; có raw result và correctness harness.

### ADV-018 — CAS một ghế trên bitmap word

- **P1 · M · Phụ thuộc:** ADV-017.
- **Quyết định:** Thử CAS trên bitmap cho một ghế trong benchmark; không dùng CAS nhiều word làm transaction nhóm.
- **Phạm vi:** AtomicLongArray/VarHandle, AVAILABLE→HELD một ghế và release có identity/version;
  đo retries, cùng word/khác word. Không đưa CAS multi-word vào production.
- **Nghiệm thu:** một ghế chỉ một claimant; không mất bit ghế khác; không gọi CAS một
  word là transaction nhóm ghế; harness kiểm tra stale release và tái sử dụng slot/version.

### ADV-019 — Disruptor single-writer prototype

- **P1 · L · Phụ thuộc:** ADV-017.
- **Quyết định:** Prototype Disruptor với nhiều producer, một writer mỗi partition và ring buffer hữu hạn; chưa tích hợp vào API.
- **Phạm vi:** MULTI producer, một writer mỗi partition, bounded ring buffer;
  command dữ liệu bất biến hoặc copy đúng vòng đời slot; read qua command/snapshot an toàn.
- **Nghiệm thu:** producer không sửa state writer; queue full có hành vi hữu hạn;
  exception không làm consumer chết âm thầm; nhóm ghế all-or-nothing trong engine;
  harness không đọc nhầm dữ liệu slot đã tái sử dụng.

### ADV-020 — Báo cáo layout, false sharing và quyết định engine

- **P0 · L · Phụ thuộc:** ADV-018, ADV-019.
- **Quyết định:** So sánh cùng workload và ghi ADR dựa trên số đo; giữ baseline nếu chưa chứng minh được lợi ích của engine.
- **Phạm vi:** so lock/CAS/Disruptor cùng workload một ghế; nhóm ghế so riêng;
  thử object layout/flat arrays; padding hoặc `@Contended` chỉ trong module benchmark tùy chọn.
- **Nghiệm thu:** ghi throughput, latency theo cách đo rõ, allocation/GC, CPU, retry/queue;
  chạy nhiều forks; không trộn batching/wait strategy khác nhau rồi quy hết lợi ích cho cache;
  ADR có quyền chọn baseline nếu Disruptor không có lợi. `@Contended` không bắt buộc cho app.

**Sprint exit:** có bằng chứng hiệu năng, chưa suy ra no oversell xuyên instance từ benchmark.

## Sprint 6 — Tích hợp single-writer với persistence

### ADV-021 — Routing command và giới hạn partition worker

- **P0 · M · Phụ thuộc:** ADV-020, ADV-015.
- **Quyết định:** Định tuyến showId tới tập worker hữu hạn; dùng feature flag và giữ baseline làm mặc định đến khi có bằng chứng.
- **Phạm vi:** showId định tuyến tới số worker hữu hạn; command Hold/Release/Expire/Confirm;
  feature flag chọn baseline hoặc engine được ADR chấp nhận.
- **Nghiệm thu:** cùng show không có hai writer trong process; không tạo thread cho từng
  ghế; deadline/owner/version kiểm tra trên writer; metrics queue depth có giới hạn cardinality.

### ADV-022 — Commit DB trước khi publish state/result

- **P0 · L · Phụ thuộc:** ADV-021, ADV-011.
- **Quyết định:** Ghi DB và idempotency trong cùng transaction, commit trước khi cập nhật cache hoặc trả kết quả.
- **Phạm vi:** consumer xử lý I/O tuần tự trước; transaction ghi state và idempotency,
  commit rồi cập nhật bitmap/snapshot và hoàn tất response; lỗi persistence có cơ chế reload.
- **Nghiệm thu:** DB rollback không trả success; crash sau commit trước cache update
  phục hồi từ DB; expire đi qua cùng writer; read không thấy state chưa commit;
  constraint/conditional update trong DB vẫn bảo vệ dù cache sai.

### ADV-023 — Backpressure, timeout và shutdown

- **P0 · M · Phụ thuộc:** ADV-022.
- **Quyết định:** Giới hạn hàng đợi và thời gian enqueue; timeout HTTP là kết quả chưa rõ và được tra cứu bằng cùng key.
- **Phạm vi:** bounded admission, thời hạn enqueue, HTTP timeout, trạng thái UNKNOWN/PENDING
  phía client và graceful shutdown/drain; không giả định hủy HTTP là rollback command.
- **Nghiệm thu:** queue full trả mã đã quy định, không tăng RAM vô hạn; timeout sau commit
  tra cứu được bằng key; shutdown không báo thành công cho command bị mất; readiness tắt trước drain.

### ADV-024 — Kiểm thử hồi quy engine tích hợp

- **P0 · M · Phụ thuộc:** ADV-022, ADV-023.
- **Quyết định:** Chạy cùng bộ kiểm thử nghiệp vụ và workload HTTP với flag bật/tắt; chỉ đổi mặc định khi kết quả cho phép.
- **Phạm vi:** chạy lại invariant M1, crash/restart, timeout/retry và load HTTP với engine bật/tắt.
- **Nghiệm thu:** kết quả nghiệp vụ tương đương baseline; có số đo end-to-end gồm DB/queue;
  nếu lợi ích chưa rõ thì giữ flag baseline mặc định, không bỏ kiểm tra tính đúng đắn để tăng tốc.

**Sprint exit:** engine bền trong một process, sẵn sàng bảo vệ partition trên nhiều instance.

## Sprint 7 — Owner và fencing xuyên instance

### ADV-025 — Lease partition và fencing token tăng dần

- **P0 · L · Phụ thuộc:** ADV-024.
- **Quyết định:** Dùng PostgreSQL làm authoritative store cho lease và fencing token tăng dần; thời gian lease dựa trên đồng hồ DB.
- **Phạm vi:** chọn cơ chế lease có authoritative store, TTL/renewal, token đơn điệu;
  không dùng một Redis SETNX đơn giản thay toàn bộ fencing protocol.
- **Nghiệm thu:** mọi lần tiếp quản có token mới; reconnect không hồi sinh quyền owner cũ;
  mất lease thì ngừng nhận lệnh partition; mô tả rõ clock/renewal/failure assumptions.

### ADV-026 — Fencing tại ranh giới DB transaction

- **P0 · L · Phụ thuộc:** ADV-025.
- **Quyết định:** Khóa và kiểm tra bản ghi lease/token trong cùng transaction ghi ghế; mọi writer bắt buộc đi qua ranh giới này.
- **Phạm vi:** kiểm tra token owner bằng lock/conditional write trong cùng transaction
  thay đổi ghế; xử lý transaction đang chạy khi lease hết hạn.
- **Nghiệm thu:** pause owner A, B tiếp quản, A resume không thể commit ghi cũ;
  không có check-then-write không được bảo vệ; ghế vẫn được unique/current-state constraint bảo vệ.

### ADV-027 — Tiếp quản, định tuyến và rehydrate partition

- **P0 · M · Phụ thuộc:** ADV-026.
- **Quyết định:** Owner mới tải lại trạng thái bền trước readiness; định tuyến lại và retry bằng cùng idempotency key.
- **Phạm vi:** owner mới tải dữ liệu bền trước khi ready, định tuyến/retry có deadline;
  di chuyển show không làm hai cache được quyền ghi đồng thời.
- **Nghiệm thu:** kill owner rồi retry giữ nguyên key cho kết quả đúng; owner mới không
  dùng snapshot stale; network partition không tạo hai writer hợp lệ trong DB.

### ADV-028 — Chaos test ownership và demo M2

- **P0 · M · Phụ thuộc:** ADV-027.
- **Quyết định:** Dùng hai instance và fault injection có kịch bản; đánh giá invariant và thời gian phục hồi riêng.
- **Phạm vi:** hai instance, pause/kill/network delay ở các bước lease/commit;
  so invariant ghế/hold và ghi nhận thời gian phục hồi.
- **Nghiệm thu:** không bán/giữ trùng trong fault scenarios đã liệt kê; alert lease lost;
  báo cáo phân biệt downtime với mất tính đúng đắn; tài liệu chạy lại độc lập máy tác giả.

**Sprint exit / M2:** engine nhiều instance có fencing được kiểm chứng.

## Sprint 8 — Service boundary và message bền

### ADV-029 — Tách Inventory/Booking/Payment deploy boundary

- **P0 · L · Phụ thuộc:** ADV-028, ADV-001.
- **Quyết định:** Tách Booking, Inventory và Payment thành process riêng, mỗi service sở hữu schema và credential riêng; không đọc bảng chéo.
- **Phạm vi:** tối thiểu Booking (chứa orchestrator), Inventory và Payment chạy riêng;
  mỗi bên sở hữu DB/schema và DB credential riêng, không truy vấn chéo bảng nghiệp vụ.
  Auth và Ticket/Notification có thể còn là module, chưa cần tách process.
- **Nghiệm thu:** local Compose chạy được các boundary; không cross-service JPA relation;
  principal/owner được truyền theo contract tin cậy; Redis session không bị xem là auth
  mặc định cho service nội bộ; JWT customer vẫn do issuer/key cấu hình hiện có xác minh.

### ADV-030 — Kafka local và message envelope có version

- **P0 · M · Phụ thuộc:** ADV-029.
- **Quyết định:** Dùng Kafka local, key theo aggregate cần giữ thứ tự và envelope có schemaVersion; không giả định thứ tự toàn cục.
- **Phạm vi:** topic/key/consumer group, eventId/sagaId/aggregateId/version/schemaVersion/
  causationId; tương thích schema, retention và retry policy được ghi lại.
- **Nghiệm thu:** key giữ đúng phạm vi ordering cần thiết; không hứa thứ tự toàn cục;
  consumer từ chối schema không hỗ trợ có quan sát; không đặt credential vào payload/log.

### ADV-031 — Outbox polling relay chống mất message

- **P0 · L · Phụ thuộc:** ADV-029, ADV-030.
- **Quyết định:** Dùng transactional outbox và polling relay; chỉ đánh dấu gửi sau broker ack, chấp nhận duplicate.
- **Phạm vi:** state + outbox cùng local transaction, claim batch giữa relay workers,
  chỉ đánh dấu sent sau broker ack; retry/backoff và metric oldest pending age.
- **Nghiệm thu:** crash trước publish không mất event; publish xong trước sent có thể
  duplicate nhưng không mất; không đánh dấu sent khi publish fail; retry không đảo thứ tự
  aggregate ngoài hợp đồng ADV-030.

### ADV-032 — Inbox và bảo vệ command nội bộ

- **P0 · L · Phụ thuộc:** ADV-030, ADV-031.
- **Quyết định:** Dùng inbox unique eventId trong transaction nghiệp vụ và semantic operation ID; ack sau commit và xác thực service nội bộ.
- **Phạm vi:** inbox unique eventId cùng transaction business/outbox, ack sau commit;
  semantic operation ID cho command khác eventId; HTTP service credential/scope,
  broker ACL và publisher/subscriber tin cậy trong môi trường demo phân tán.
- **Nghiệm thu:** duplicate không áp lại side effect; out-of-order không lùi state;
  client public không giả lệnh Confirm/Refund; crash trước/sau ack được redeliver an toàn.

**Sprint exit:** có messaging bền giữa service; chưa coi Saga thanh toán đã hoàn thành.

## Sprint 9 — Payment và Saga Orchestration

### ADV-033 — Payment sandbox với trạng thái không rõ

- **P0 · L · Phụ thuộc:** ADV-032.
- **Quyết định:** Dùng provider giả lập có charge/refund/query và operation ID bền; timeout giữ UNKNOWN đến khi đối soát.
- **Phạm vi:** provider giả lập charge/refund/query, operation ID bền và idempotency;
  giả lập success/failure/delay/timeout sau charge; tiền dùng minor units + currency,
  số tiền lấy từ snapshot lúc hold do server lưu (bổ sung migration giá nếu chưa có); không tin giá client gửi.
- **Nghiệm thu:** cùng operation không charge hai lần; không gọi provider trong DB
  transaction kéo dài; crash sau provider success trước local save được query/reconcile;
  timeout giữ UNKNOWN/PENDING thay vì khẳng định thất bại.

### ADV-034 — Orchestrator bền cho happy path

- **P0 · L · Phụ thuộc:** ADV-033, ADV-010, ADV-032.
- **Quyết định:** Booking điều phối Saga bền trong DB; ghi state và outgoing command cùng transaction, Inventory là bên xác nhận SOLD.
- **Phạm vi:** SagaInstance/Step, version/deadline/nextRetryAt; HELD → PAYMENT_PENDING →
  CONFIRMING → ISSUING; state và outgoing command ghi cùng transaction.
- **Nghiệm thu:** user chỉ checkout booking của mình; PaymentSucceeded đúng operation
  mới kích confirm; restart từng bước tiếp tục từ DB; chỉ Inventory xác nhận SOLD.

### ADV-035 — Compensation release và refund

- **P0 · L · Phụ thuộc:** ADV-034.
- **Quyết định:** Thanh toán thất bại chắc chắn thì release; đã trả tiền nhưng hold hết hạn thì refund; kết quả confirm chưa rõ phải query.
- **Phạm vi:** payment failed chắc chắn → release; paid nhưng hold expired → refund;
  ambiguous confirm → query trước quyết định; refund pending/retry không báo thành công sớm.
- **Nghiệm thu:** payment đến trễ không lấy ghế người khác; duplicate refund chỉ hoàn
  tiền một lần; confirm thực tế đã commit không refund vì message trễ; lỗi không phục hồi
  chuyển manual review thay vì tự đánh dấu COMPLETED/REFUNDED.

### ADV-036 — Kịch bản lỗi Saga và trạng thái booking cho UI

- **P0 · M · Phụ thuộc:** ADV-035.
- **Quyết định:** Hiển thị pending và refund pending theo trạng thái server; polling hữu hạn và kiểm thử duplicate, delay, restart.
- **Phạm vi:** API/UI hiển thị pending/refund pending/cancelled rõ; refresh trạng thái
  theo lịch hữu hạn; fault injection message duplicate/delay/out-of-order và kill orchestrator.
- **Nghiệm thu:** UI không thông báo thanh toán thất bại khi kết quả còn UNKNOWN;
  mỗi kịch bản có trạng thái tiền/ghế/booking mong đợi; retry/restart không sinh Saga kép;
  chưa hoàn tất booking khi Ticket chưa xác nhận phát vé.

**Sprint exit:** charge/confirm/refund đúng với sandbox; ISSUING chờ Ticket sprint 10.

## Sprint 10 — Ticket, check-in và trải nghiệm đầy đủ

### ADV-037 — Phát vé idempotent và hoàn tất Saga

- **P0 · L · Phụ thuộc:** ADV-036.
- **Quyết định:** Ticket module phát vé idempotent theo booking + showSeat sau xác nhận Inventory; Saga hoàn tất khi nhận TicketIssued.
- **Phạm vi:** ticket module có transaction/inbox/outbox; unique booking + showSeat;
  chỉ phát sau Inventory confirmed; TicketIssued hoàn tất Saga.
- **Nghiệm thu:** retry không phát hai vé; event giả/trái state không phát vé;
  lỗi tạm ở ISSUING được retry; không tự hoàn tiền khi vé chỉ chậm phát;
  QR/token vé không chứa thông tin nhạy cảm và không thay authorization.

### ADV-038 — STAFF, phân công sự kiện và check-in nguyên tử

- **P0 · L · Phụ thuộc:** ADV-037, ADV-002.
- **Quyết định:** STAFF chỉ check-in show được phân công; ADMIN quản lý assignment, quyền check-in cũng cần assignment; dùng cập nhật nguyên tử.
- **Phạm vi:** thêm ROLE_STAFF và assignment show/event, method security; check-in conditional
  update/unique record; quyết định quyền admin cụ thể, không mặc định mọi role là superuser.
- **Nghiệm thu:** staff không được phân công nhận 403; hai scanner cùng vé chỉ một
  check-in thành công; vé sai show/hủy/không hợp lệ bị chặn; assignment sửa bởi admin hợp lệ.

### ADV-039 — UI checkout, vé và staff scanner

- **P1 · L · Phụ thuộc:** ADV-037, ADV-038, ADV-014.
- **Quyết định:** Tiếp tục React session/CSRF; lấy trạng thái checkout và check-in từ server, hỗ trợ nhập mã và quét nếu trình duyệt cho phép.
- **Phạm vi:** luồng chọn ghế → booking → sandbox checkout → vé, staff nhập/quét mã;
  React tiếp tục session/CSRF; JWT APIs dành client/Postman theo contract riêng.
- **Nghiệm thu:** reload không mất liên kết booking; nhiều tab đọc cùng trạng thái server;
  nút lặp không charge/check-in kép; session hết hạn yêu cầu login lại nhưng không xóa booking;
  thông báo pending/refund và check-in duplicate có hành động rõ.

### ADV-040 — Notification độc lập và demo M3

- **P1 · M · Phụ thuộc:** ADV-037, ADV-039.
- **Quyết định:** Dùng notification sink local độc lập, dedupe theo booking + loại; lỗi thông báo không đảo trạng thái nghiệp vụ.
- **Phạm vi:** notification module/sink local, dedupe theo loại thông báo + booking;
  chỉ sau completion, retry độc lập; chưa cần email provider thật hay service riêng.
- **Nghiệm thu:** notification fail không hủy vé/booking; retry không spam thông báo;
  demo success/expired-refund/check-in chạy end-to-end và lưu trace IDs để đối chiếu.

**Sprint exit / M3:** luồng đặt ghế, thanh toán giả lập, phát vé, check-in hoạt động.

## Sprint 11 — Đối soát, replay và vận hành

### ADV-041 — Reconciliation job và deadline Saga

- **P0 · L · Phụ thuộc:** ADV-035, ADV-037.
- **Quyết định:** Dùng job claim có giới hạn và backoff để query trạng thái chưa rõ; hết retry chuyển manual review.
- **Phạm vi:** job tìm operation UNKNOWN và Saga quá deadline; query provider/inventory;
  claim job giữa instances, retry budget và backoff, không coi hết retry là hoàn tất.
- **Nghiệm thu:** provider success sau timeout được phát hiện; refund pending được tiếp tục;
  reconcile chạy lặp không phát thêm charge/refund/ticket; trạng thái không giải được
  lên manual review với bằng chứng, không đoán kết quả.

### ADV-042 — DLQ và replay có audit

- **P0 · M · Phụ thuộc:** ADV-032, ADV-041.
- **Quyết định:** Lưu message lỗi vào DLQ và replay qua cùng consumer/inbox; giới hạn ADMIN và ghi audit.
- **Phạm vi:** DLQ có reason/schema/correlation, API admin xem và replay theo event;
  giới hạn quyền, số lượng, audit actor/time; không sửa payload tùy ý để bypass invariant.
- **Nghiệm thu:** poison message không retry vô hạn; replay qua cùng inbox/semantic
  idempotency; DLQ không tự làm Saga thành COMPLETED; replay không bỏ kiểm tra owner/version.

### ADV-043 — Metrics, tracing và dashboard Saga

- **P1 · M · Phụ thuộc:** ADV-040, ADV-041.
- **Quyết định:** Thu thập metric có cardinality thấp và trace theo correlation; dashboard chỉ ADMIN, không ghi credential.
- **Phạm vi:** queue depth, oldest outbox, payment unknown, saga stuck, refund pending,
  lease loss; trace HTTP→Kafka→DB; dashboard chỉ admin với dữ liệu đã che.
- **Nghiệm thu:** từ booking ID tìm được Saga/operation/events; alert có ngưỡng và runbook;
  không label metric bằng mỗi user/token/reservation làm cardinality tăng vô hạn;
  log không chứa session ID, refresh token, khóa ký hoặc QR credential đầy đủ.

### ADV-044 — Failure matrix và soak recovery

- **P0 · L · Phụ thuộc:** ADV-041, ADV-042, ADV-043.
- **Quyết định:** Chạy failure matrix trên dữ liệu test riêng; đối soát tiền, ghế và vé sau từng kịch bản phục hồi.
- **Phạm vi:** Redis/PostgreSQL/Kafka/provider dừng rồi trở lại, slow consumer, outbox
  backlog, kill process từng ranh giới commit/publish/ack; chạy trên dữ liệu giả riêng.
- **Nghiệm thu:** tiền/ghế/vé đối soát khớp sau phục hồi; không có success không bền;
  Redis mất không fallback phiên RAM; backlog giảm được sau phục hồi và không tăng RAM vô hạn;
  tài liệu phân biệt lỗi availability với invariant bị vi phạm.

**Sprint exit:** có công cụ xử lý sự cố, không chỉ restart rồi hy vọng dữ liệu đúng.

## Sprint 12 — Hardening và release Advanced

### ADV-045 — Security review nghiệp vụ và hạ tầng

- **P0 · L · Phụ thuộc:** ADV-044.
- **Quyết định:** Rà soát và kiểm thử quyền, trust boundary, giới hạn đầu vào và secrets theo các luồng đang triển khai.
- **Phạm vi:** audit URL/method/ownership, session/CSRF/JWT separation, credential nội bộ,
  rate limit login/hold/checkout, payload/seat-count limit, cookie HTTPS và secrets runtime.
- **Nghiệm thu:** customer A không thao tác booking B; STAFF chỉ check-in show được giao;
  không tin giá/owner do client gửi; JWT sai issuer/aud/signature bị chặn; cookie không
  thay bearer tại API stateless; hạn chế tải không phá idempotency hoặc quyền truy cập.

### ADV-046 — Load test end-to-end và quyết định capacity

- **P0 · L · Phụ thuộc:** ADV-044, ADV-045, ADV-020.
- **Quyết định:** Đo end-to-end theo workload đã công bố; đặt SLO sau baseline và báo cáo riêng success, conflict, timeout.
- **Phạm vi:** HTTP + Kafka + DB + sandbox, workload nóng/đều và nhóm ghế; công bố
  phần cứng/dataset, ramp-up, p50/p95/p99, throughput success/conflict và resource saturation.
- **Nghiệm thu:** đặt SLO dựa baseline và đo lại cùng workload; không tính toàn bộ 409 là
  vé bán thành công; inventory audit không oversell; báo cáo queue/backlog, timeout và recovery;
  so riêng JMH CPU với số liệu toàn hệ thống, không gộp thành một kết luận.

### ADV-047 — Runbook release, migration và recovery

- **P0 · M · Phụ thuộc:** ADV-045, ADV-046.
- **Quyết định:** Viết runbook local có thứ tự migration, backup/restore, rollback deploy và đối soát; kiểm tra bằng dữ liệu test.
- **Phạm vi:** lệnh khởi động/seed/demo, migration thứ tự service, backup/restore,
  schema/message compatibility, rollback deploy và tiến trình reconcile; môi trường demo local.
- **Nghiệm thu:** người khác chạy được từ clone sạch; không dùng xóa DB để rollback;
  restore trên dữ liệu test có audit hậu phục hồi và giữ fencing/invariant;
  nêu rõ giới hạn sandbox và các việc chưa đạt production.

### ADV-048 — Demo cuối và báo cáo học tập Advanced

- **P1 · M · Phụ thuộc:** ADV-047.
- **Quyết định:** Chuẩn bị script demo tái lập và báo cáo liên kết bằng chứng; ghi rõ giới hạn và việc còn lại.
- **Phạm vi:** video hoặc script demo, sơ đồ hiện trạng, ADR tổng kết,
  báo cáo engine/cache + Saga + security và liên kết test/benchmark raw results.
- **Nghiệm thu:** demo một ghế nhiều người tranh, all-or-nothing, restart sau commit,
  owner cũ bị fencing, paid-after-expiry refund, duplicate không charge/vé/refund kép,
  check-in race; liệt kê limitation và backlog sau release, không gắn nhãn lock-free
  hoặc exactly-once cho toàn hệ thống.

**Sprint exit / M4:** release Advanced có bằng chứng, hướng dẫn và giới hạn rõ ràng.

## 5. Backlog mở rộng sau M4

Các việc sau không chặn phạm vi trên; cần ticket/ADR riêng khi được chọn:

| Mã | Ưu tiên | Nội dung | Điều kiện bắt đầu |
|---|---|---|---|
| ADV-049 | P2 | Debezium CDC thay polling relay | Outbox polling đúng và có metric cần tối ưu |
| ADV-050 | P2 | Provider authorize/capture thay charge-first | Saga hiện tại được kiểm chứng; có sandbox hỗ trợ |
| ADV-051 | P2 | Tách Auth/Ticket/Notification thành service riêng | Có lý do deploy/scale/team rõ, contract hiện tại ổn định |
| ADV-052 | P2 | React JWT SPA hoặc BFF | ADR credential/storage/CSRF, recovery và logout được duyệt |
| ADV-053 | P2 | Web Locks/SharedWorker cho JWT refresh | ADV-052 thực sự chọn browser-managed refresh; có kiểm thử nhiều tab |
| ADV-054 | P2 | WebSocket/SSE cập nhật sơ đồ ghế | Snapshot/query đúng; giới hạn fan-out và reconnect |
| ADV-055 | P2 | OAuth2/MFA/Organizer và ABAC nâng cao | Không làm gián đoạn các invariant nghiệp vụ đã có |

Không thêm grace period refresh token chỉ để tránh race: cần threat model và protocol
riêng. Không khóa cả tài khoản vì một lần token reuse; chính sách hiện tại thu hồi family.
Redis session vẫn là mặc định cho portal trong mọi phương án mở rộng.

## 6. Ticket nên bắt đầu ngay

Bắt đầu **ADV-001**, sau đó ADV-002 và ADV-003; đưa ADV-004 vào sau khi schema/API nền
đủ rõ. Không bắt đầu Disruptor hoặc Kafka trước khi có baseline ghế/hold đúng để so sánh.

Mẫu issue để sao chép:

```text
[ADV-NNN] Tiêu đề
Sprint / Milestone:
Priority / Size:
Owner / Status: TODO
Dependencies:
Vấn đề và kết quả mong muốn:
Quyết định triển khai / lý do:
Các bước thực hiện theo thứ tự:
Scope / ngoài phạm vi:
API, schema, transaction hoặc message liên quan:
Acceptance criteria:
Kịch bản quyền / tranh chấp / lỗi cần kiểm tra:
Bằng chứng: test, request/response đã che, benchmark hoặc demo
PR / tài liệu cập nhật:
```

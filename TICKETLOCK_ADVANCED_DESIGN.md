# TicketLock — Nền tảng đặt vé cạnh tranh cao và xử lý thanh toán phân tán

## 1. Mô tả dự án

TicketLock là hệ thống bán vé sự kiện có sơ đồ ghế, phục vụ tình huống nhiều khách đồng thời tranh mua một tập ghế giới hạn trong giờ mở bán. Khách đăng nhập, chọn ghế, giữ chỗ có thời hạn, thanh toán và nhận vé. Ban tổ chức quản lý sự kiện; nhân viên thực hiện check-in theo sự kiện được phân công.

Dự án khai thác ba nhóm kiến thức:

1. **Security:** session cho portal, JWT cho khách hàng, phân quyền theo vai trò và tài nguyên.
2. **Concurrency và CPU cache:** thiết kế engine giữ ghế, so sánh locking, CAS và single-writer; đo tác động của layout dữ liệu, false sharing và LMAX Disruptor.
3. **Distributed transactions:** điều phối thanh toán và xác nhận ghế bằng Saga Orchestration, local transaction, Transactional Outbox và Kafka.

Mục tiêu là chứng minh tính đúng đắn trước, rồi đo hiệu năng. Không mặc định hệ thống đạt nghìn vé/giây khi chưa benchmark, cũng không gọi toàn bộ hệ thống là lock-free chỉ vì dùng Disruptor.

## 2. Quan hệ với phiên bản Mini

Phiên bản hiện tại vẫn là bài học Security trong 14 ngày theo [thiết kế Mini](TICKETLOCK_MINI_DESIGN.md). Tài liệu này là hướng phát triển sau đó, chưa mô tả chức năng đã triển khai.

| Giai đoạn | Đầu ra |
|---|---|
| A — Security Mini | Session, JWT, role, ownership; hoàn thành mục tiêu học của lead |
| B — Đặt ghế đúng | Modular monolith, PostgreSQL, giữ ghế có hạn, chống bán trùng |
| C — Engine hiệu năng | Benchmark độc lập cho lock, CAS và Disruptor; tích hợp phương án đã đo |
| D — Giao dịch phân tán | Tách service, Saga, Outbox, Kafka, kiểm thử lỗi và phục hồi |

Không đưa toàn bộ giai đoạn B–D vào deadline Security hai tuần.

## 3. Chức năng nghiệp vụ

- Khách xem sự kiện và sơ đồ ghế, giữ một hoặc nhiều ghế trong 10 phút.
- Giữ nhiều ghế có tính all-or-nothing: giữ được tất cả hoặc không giữ ghế nào.
- Khách khởi tạo thanh toán cho reservation còn hiệu lực.
- Hệ thống xác nhận ghế và phát hành vé sau khi quy trình thanh toán hoàn thành.
- Thanh toán thất bại giải phóng ghế; thanh toán thành công nhưng không thể xác nhận ghế phải hoàn tiền.
- Callback hoặc message trùng không tạo thêm thanh toán, vé hoặc lần hoàn tiền.
- Admin quản lý sự kiện; staff chỉ check-in sự kiện được phân công.
- Check-in cùng vé đồng thời chỉ có một thao tác thành công.

Reservation thuộc user và có ID riêng, không lấy HTTP Session ID làm định danh. Logout không tự hủy đơn hàng hoặc thanh toán đang xử lý.

## 4. Kiến trúc đích

```text
Admin / Customer / Scanner
           |
    API và Security
           |
    Booking Service ---------------- Saga Orchestrator
           |                                 |
           +-------------- Kafka ------------+
                          |                  |
                   Inventory Service    Payment Service
                   + Seat Engine        + Payment Sandbox
                          |
                    Ticket Service ----- Notification Service

Auth Service: tài khoản, credentials, token và quyền
Mỗi service nghiệp vụ: dữ liệu riêng + inbox/outbox khi cần
```

Booking Service chứa Saga Orchestrator ở bước đầu để giảm số service. Ticket và Notification có thể tách sau. Admin portal dùng session; việc tách Auth Service không tự làm session được chia sẻ cho mọi service.

Mỗi service sở hữu dữ liệu và transaction riêng. Không dùng một transaction JPA bao trùm nhiều database. API nội bộ xác thực service và kiểm tra scope; dữ liệu user gửi vào không được dùng làm bằng chứng ownership.

## 5. Engine giữ ghế: concurrency và CPU cache

### 5.1. Quy tắc bắt buộc

- Mỗi ghế thuộc một suất/sự kiện chỉ có tối đa một reservation có quyền giữ hợp lệ.
- Các trạng thái: `AVAILABLE → HELD → SOLD`; hết hạn hoặc hủy có thể chuyển `HELD → AVAILABLE`.
- Mỗi hold có `reservationId`, `ownerId`, `expiresAt` và version.
- Hold, Release, Expire và Confirm phải kiểm tra đúng reservation/version để lệnh cũ không giải phóng hoặc bán ghế của người mới.
- Engine trả thành công cho thao tác giữ/xác nhận chỉ sau khi kết quả đã được lưu bền theo cơ chế đã chọn.

### 5.2. Ba phương án để học và so sánh

| Phương án | Thiết kế | Điều cần đo |
|---|---|---|
| Lock baseline | Lock theo partition, cập nhật trạng thái trong critical section | Contention, thời gian chờ, latency |
| CAS experiment | AtomicLongArray hoặc VarHandle cho bitmap theo word | Retry, contention cùng word, giới hạn multi-seat |
| Disruptor | Nhiều producer đưa command vào ring buffer; một writer xử lý mỗi partition | Queueing, batching, backpressure, CPU |

CAS trên một word không tự tạo giao dịch nguyên tử cho nhiều ghế nằm ở nhiều word. Bản CAS ban đầu chỉ benchmark giữ một ghế. Muốn hỗ trợ nhóm ghế phải thiết kế giao thức bổ sung; không ghép các CAS rồi coi là all-or-nothing.

### 5.3. Phương án tích hợp ưu tiên: single-writer theo suất diễn

```text
HTTP threads
     |
     v
Bounded ring buffer, MULTI producer
     |
     v
Một consumer sở hữu trạng thái một partition
     |
     +-- Kiểm tra quyền giữ ghế, version, deadline
     +-- Lưu trạng thái + outbox bằng local DB transaction
     +-- Commit thành công: cập nhật cache và trả kết quả
```

Partition theo `showId`: toàn bộ ghế trong một suất do cùng một writer xử lý. Trong một process, có thể ánh xạ nhiều suất vào một số lượng consumer giới hạn; không tạo một thread cho mỗi ghế.

Single-writer loại bỏ việc nhiều thread cùng ghi bitmap của partition đó. Nó không làm DB, network hay toàn pipeline trở thành lock-free. DB I/O ban đầu chạy tuần tự cùng consumer để bảo đảm dễ hiểu; chỉ tối ưu batching hoặc tách persistence sau khi có protocol bảo toàn thứ tự và durability.

Ring buffer đầy phải có giới hạn chờ hoặc từ chối có kiểm soát. Không ghi đè command chưa xử lý. Các request timeout có thể đã commit, nên client retry bằng cùng idempotency key và truy vấn trạng thái.

### 5.4. Layout dữ liệu

- Dùng `long[]` bitmap cho trạng thái cần quét nhanh; tách held/sold hoặc dùng representation có định nghĩa rõ.
- Lưu owner, deadline và version trong các mảng tương ứng; bitmap không đủ để biểu diễn toàn bộ reservation.
- Không để nhiều thread sửa trực tiếp `BitSet`: lớp này không tự thread-safe.
- Giữ trạng thái mutable trong writer; API đọc qua command hoặc immutable snapshot được publish an toàn.
- Đo allocation và locality khi so sánh mảng phẳng với nhiều đối tượng Seat.
- Chỉ thử padding hoặc `@Contended` khi profiler/benchmark chỉ ra false sharing giữa các counter độc lập. `@Contended` không chữa race condition hoặc tranh chấp cùng một biến.
- `jdk.internal.vm.annotation.Contended` là API nội bộ, cần cấu hình export và JVM flag phù hợp; chỉ đặt trong module benchmark tùy chọn, không ép ứng dụng học Security phụ thuộc vào nó.

### 5.5. Durability và nhiều instance

PostgreSQL là nguồn dữ liệu bền ở thiết kế đầu tiên. Bitmap là trạng thái tăng tốc có thể dựng lại. Khi khởi động, tải ghế/hold còn hiệu lực, kiểm tra deadline rồi mới nhận command.

Trong nhiều instance, chỉ một owner được quyền ghi mỗi partition. Cần lease và fencing token tăng dần, được kiểm tra trong DB transaction. Unique constraint và cập nhật có điều kiện tiếp tục bảo vệ dữ liệu. Single-writer trong một JVM không ngăn instance khác bán trùng ghế.

Expiry cũng là command đi qua writer. Timer chỉ kích hoạt xử lý; mọi Hold/Confirm vẫn kiểm tra deadline tại thời điểm ra quyết định. Không dựa vào Redis expiry hoặc sự kiện session bị hủy để bảo đảm đúng hạn.

## 6. Saga Orchestration cho thanh toán và phát hành vé

### 6.1. Luồng thành công

1. Inventory tạo hold bền với thời hạn 10 phút.
2. Booking lưu Saga và gửi lệnh thanh toán qua outbox trong cùng transaction.
3. Payment xử lý với provider bằng idempotency key và phát `PaymentSucceeded` sau khi ghi nhận kết quả bền.
4. Orchestrator nhận kết quả, chuyển Saga sang `CONFIRMING` và gửi `ConfirmReservation`.
5. Inventory chỉ chuyển `HELD → SOLD` nếu hold vẫn hợp lệ, đúng owner/version; sau commit phát `ReservationConfirmed`.
6. Orchestrator yêu cầu Ticket phát hành vé với unique constraint theo booking/ghế.
7. Ticket phát `TicketIssued`; Booking hoàn tất Saga và Notification gửi thông báo.

Thiết kế sandbox ban đầu dùng charge rồi confirm: nếu hold hết hạn trước Confirm, bắt buộc đi theo nhánh refund. Khi mở rộng có thể thử authorize/capture, nhưng vẫn cần xử lý mọi trạng thái trung gian.

### 6.2. Trạng thái và bù trừ

```text
HELD → PAYMENT_PENDING → CONFIRMING → ISSUING → COMPLETED
            |                |
       PAYMENT_FAILED    CONFIRM_REJECTED
            |                |
      RELEASE_PENDING   REFUND_PENDING
            |                |
        CANCELLED         REFUNDED
```

| Sự cố | Cách xử lý |
|---|---|
| Thanh toán thất bại chắc chắn | Release hold bằng reservation/version; hủy booking |
| Payment timeout, chưa rõ kết quả | Reconcile với provider; không coi timeout là chắc chắn chưa trừ tiền |
| Tiền đã trừ nhưng hold hết hạn | Từ chối confirm; refund idempotent; không lấy lại ghế đã bán cho người khác |
| Confirm đã thành công nhưng response/message trễ | Query/retry idempotent; không refund chỉ vì timeout |
| Phát vé lỗi sau khi ghế đã SOLD | Giữ trạng thái ISSUING và retry; lỗi vĩnh viễn vào xử lý bù trừ có kiểm soát |
| Notification lỗi | Retry riêng; không hủy booking đã hoàn tất |
| Refund chưa thành công | Giữ REFUND_PENDING, retry/reconcile và cảnh báo; không đánh dấu REFUNDED sớm |

Saga là chuỗi local transaction với eventual consistency và compensation, không phải rollback ACID tức thì trên mọi service. Orchestrator lưu trạng thái bền, deadline và lịch retry để tiếp tục sau restart.

## 7. Transactional Outbox, Inbox và Kafka

Mỗi local transaction thay đổi dữ liệu đồng thời insert message vào outbox của service đó. Relay dùng polling trước; Debezium CDC là bước mở rộng. Relay publish Kafka rồi ghi nhận đã gửi; crash ở giữa có thể tạo message lặp.

Consumer lưu `eventId` vào inbox và cập nhật nghiệp vụ + outbox tiếp theo trong cùng local transaction. Acknowledge Kafka sau commit. Unique key trên inbox ngăn cùng message áp dụng lại; idempotency nghiệp vụ còn bảo vệ các message khác ID nhưng cùng thao tác.

Message envelope gồm `eventId`, `sagaId`, `aggregateId`, `aggregateVersion`, `type`, `occurredAt`, `schemaVersion`, `causationId` và payload. Partition key giữ thứ tự cho aggregate cần xử lý; không giả định Kafka tạo thứ tự toàn cục giữa các topic.

Không gọi payment provider trong DB transaction rồi kỳ vọng rollback được giao dịch bên ngoài. Payment dùng operation ID bền, provider idempotency và reconciliation để vượt qua crash giữa provider call và local commit.

Kafka không tự bảo đảm exactly-once cho side effect ở PostgreSQL, provider thanh toán hoặc email. Các endpoint và consumer phải chịu được message trùng, trễ và sai thứ tự.

## 8. Dữ liệu chính

| Chủ sở hữu | Dữ liệu |
|---|---|
| Auth | User, role, credentials, refresh token |
| Inventory | Show, Seat, Reservation, ReservationSeat, partition fencing token |
| Booking | Booking, SagaInstance, SagaStep, idempotency record |
| Payment | PaymentOperation, provider reference, refund operation |
| Ticket | Ticket, CheckIn |
| Mỗi service liên quan | OutboxEvent, InboxMessage |

Inventory giữ record duy nhất cho `(showId, seatId)` và chuyển trạng thái có điều kiện. Không chỉ đặt unique constraint trên lịch sử reservation rồi cho rằng đã chống bán trùng. Dữ liệu lịch sử tách khỏi trạng thái hiện tại của ghế.

## 9. Benchmark và kiểm thử chấp nhận

### Engine độc lập

- Dùng JMH đo engine trong bộ nhớ, không trộn DB/network vào con số CPU benchmark.
- So sánh cùng workload: lock baseline, CAS một ghế, Disruptor một ghế; benchmark nhóm ghế riêng trên phương án hỗ trợ.
- Thay đổi số producer, số ghế, phân bố request đều/hot seat, tỷ lệ thất bại và kích thước nhóm.
- Đo throughput xử lý và số hold thành công riêng, latency p50/p95/p99, allocation/GC, CPU, queue depth và CAS retries khi có.
- Ghi phần cứng, JDK, heap, wait strategy, warmup, số fork và dữ liệu đầu vào.
- Busy-spin chỉ thử khi có CPU phù hợp; không mặc định tốt nhất trên laptop.

### Tính đúng đắn và phục hồi

- Nhiều request tranh một ghế chỉ có một hold hợp lệ thắng.
- Giữ nhóm ghế không được thành công một phần.
- Expire/Release cũ không tác động hold mới.
- Restart sau commit nhưng trước response không làm mất hold; retry trả cùng kết quả.
- Hai instance nhận cùng partition, fencing ngăn owner cũ tiếp tục ghi.
- Duplicate message/callback không tạo thêm payment, ticket hoặc refund.
- Crash giữa commit outbox và publish, hoặc sau publish trước đánh dấu, không mất kết quả nghiệp vụ.
- Kill orchestrator ở từng bước rồi restart vẫn tiếp tục Saga.
- Payment thành công sau khi hold hết hạn dẫn đến refund, không cấp vé trùng.
- Dead-letter không được coi là Saga đã hoàn tất; có công cụ replay và đối soát.

### Đo toàn hệ thống

Load test HTTP và Kafka riêng với JMH. Báo cáo latency end-to-end gồm queue, DB và thanh toán giả lập. Mục tiêu tải cụ thể được đặt sau baseline trên máy chạy, không suy ra từ kết quả ring buffer.

## 10. Sản phẩm bàn giao

- Mini app và tài liệu Security phục vụ deadline hiện tại.
- Engine benchmark với baseline, kết quả thô và nhận xét trade-off.
- Modular monolith có đặt ghế đúng và restart recovery.
- Demo Saga dùng payment sandbox, Kafka và Outbox/Inbox.
- Kịch bản fault injection, dashboard trạng thái Saga và hướng dẫn replay/reconcile.

## Tài liệu nền tảng

- [LMAX Disruptor User Guide](https://lmax-exchange.github.io/disruptor/user-guide/): producer, consumer, sequencing và wait strategies.
- [Java BitSet API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html): giới hạn thread safety.
- [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): relay sự kiện từ outbox bằng CDC.

# ADV-002 — Flyway và database Mini

## Trạng thái

Chọn Flyway vì dùng SQL versioned và migration history phù hợp với phạm vi PostgreSQL hiện tại; Spring Boot starter cùng module PostgreSQL đã chạy thành công trong kiểm thử với PostgreSQL 17. V1 đã tạo schema mới với PK, unique email, check role, foreign key refresh user và hai index. Hibernate dùng `validate`; Flyway không tự baseline database cũ. Bản này cần kiểm tra và baseline có chủ đích trước khi chạy trên DB đã tồn tại.

Đã chạy bộ test backend trên DB rỗng độc lập `ticketlock_adv002_verify`: V1 chạy và Hibernate validate thành công. Đã tạo DB test `ticketlock_adv002_legacy_verify` với schema tương đương V1, thêm user/refresh giả, baseline rồi kiểm tra liên kết và giá trị fixture còn nguyên. Đây là **fixture mô phỏng**, chưa chứng minh toàn bộ dữ liệu/schema cũ ngoài môi trường test tương đương. Hai DB test vẫn còn để đối chiếu; không chứa dữ liệu thật.

Đã đối chiếu bản sao DB local và đọc schema DB demo; kết quả chi tiết và giới hạn được ghi bên dưới. Không baseline DB local hoặc demo gốc trong quá trình kiểm tra.

## Đối chiếu bản sao DB local — 23/11/2026

Đã dùng `pg_dump -Fc` tạo snapshot DB local `ticketlock` và phục hồi vào database độc lập `adv002_audit_20261123` trên cùng PostgreSQL. Đây là bản sao dữ liệu thật, cần bảo vệ như DB gốc; file backup nằm tại `/tmp/adv002_audit_20261123.dump` trong container PostgreSQL. DB gốc không bị baseline hay sửa. Snapshot nhất quán tại thời điểm dump, không khẳng định bao gồm các thay đổi phát sinh sau đó; chưa diễn tập khôi phục sang máy khác.

- Hai bảng `app_users`, `refresh_tokens`: tên cột, kiểu, độ dài, nullable, PK, unique email, check role, FK và hai index refresh khớp V1 về chức năng. Thứ tự cột và tên một số constraint khác, không ảnh hưởng chức năng kiểm tra này.
- Có thêm bảng lịch sử `user_id_migration`, gồm `old_id` bigint PK và `new_id` UUID unique; có một bản ghi ánh xạ và không có ánh xạ mồ côi. V1 không tạo bảng này, vì vậy schema đầy đủ không giống hệt DB mới; giữ lại bảng, không xóa chỉ để baseline.
- Bản sao có 5 user, 2 refresh token, 0 refresh token mồ côi, 0 role ngoài ADMIN/CUSTOMER và 0 user ID khác UUIDv7. Không phát hiện email chưa chuẩn hóa, hash rỗng, expiry trước/lúc tạo hoặc timestamp dùng/thu hồi trước lúc tạo.
- Không có trigger, view, materialized view, sequence, foreign table, function hay schema người dùng khác; extension chỉ có `plpgsql`. Không có RLS policy trên các bảng public.
- Đây là kiểm tra metadata và tính toàn vẹn tổng hợp, không xác nhận từng password hash/token hay chứng minh login sau nâng cấp. Chưa so sánh đầy đủ mọi quyền, owner và object ở cấp cluster; chưa thử baseline trên bản sao dữ liệu thật. Cần đánh giá bảng lịch sử và tiếp tục kiểm thử trước khi baseline DB gốc.

## Diễn tập backup và nâng cấp fixture — 23/11/2026

- Trên PostgreSQL local đã tạo các database test `adv002_fixture_1123`, `adv002_restore_1123`, `adv002_invalid_1123`, chỉ chứa schema và dữ liệu giả. Tạo một user UUIDv7 với BCrypt test và một refresh token giả có FK đến user.
- `pg_dump -Fc` fixture vào `/tmp/adv002_fixture_1123.dump` trong container PostgreSQL; `pg_restore --no-owner --no-privileges` vào DB restore độc lập thành công. So sánh MD5 của toàn bộ hàng JSON theo thứ tự khóa: user `df1996428c4bd196666ab031cf9a6395`, refresh `396dc12f9ca0905f7695da03dace6b60`; trước và sau restore trùng nhau. Đây là dấu kiểm dữ liệu fixture, không phải bằng chứng mật mã cho dữ liệu thật.
- Chạy backend Java 21 trên cổng test 18082 với `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` chỉ cho DB restore. Hibernate validate và backend khởi động; login bằng tài khoản test trả 204, `/me` trả 200. Tắt backend và chạy lại với baseline mặc định `false`: khởi động thành công, history chỉ có một dòng version 1, type `BASELINE`, success `true`. Dấu kiểm hai bảng không đổi sau chạy lại.
- Restore fixture khác vào DB invalid, xóa cột `password_hash` **chỉ trên DB test**, rồi chạy backend với baseline mặc định tắt. Flyway từ chối schema không rỗng chưa có history; backend dừng, `flyway_schema_history` không được tạo. Đây là kiểm tra bảo vệ DB cũ chưa baseline nói chung, không phải Flyway tự phát hiện cột sai: vẫn cần đối chiếu schema trước khi chủ động baseline.
- Ba DB test và file dump fixture còn để kiểm tra; backend test đã dừng. Không sửa, baseline hoặc xóa database local/demo. Các bước bổ sung với snapshot demo và refresh JWT được ghi bên dưới.

## Kiểm tra DB demo — 24/11/2026

Đã chỉ đọc database `ticketlock` trong container `ticketlock-demo-postgres-1` thuộc Docker context `desktop-linux`; không baseline, ghi dữ liệu hay xóa volume. Schema `public` chưa có `flyway_schema_history`.

- `app_users` và `refresh_tokens` khớp V1 về tên cột, kiểu, độ dài, nullable, PK, unique email, check role, FK và hai index refresh. Tên unique constraint và thứ tự cột khác V1, không đổi ý nghĩa các kiểm tra này.
- Có 2 user, đều UUIDv7 và role hợp lệ; 0 refresh token và 0 orphan. Không có bảng phụ, view, sequence, trigger, function người dùng hoặc column default trong public; extension hiện thấy là `plpgsql`.
- Đây là kết quả chỉ đọc trên DB demo đang hoạt động; chưa backup/restore DB demo, kiểm tra toàn bộ quyền/owner, hoặc thử login sau baseline. Không đủ cơ sở tự baseline. Cần backup và thử trên bản sao trước khi nâng cấp backend dùng Flyway.

## Kiểm thử bổ sung trên bản sao demo — 24/11/2026

- Dùng `pg_dump -Fc` và `pg_restore` trên PostgreSQL demo tạo hai DB độc lập `adv002_demo_before_1124`, `adv002_demo_after_1124` từ cùng snapshot. Backup `/tmp/adv002_demo_1124.dump` trong container và cả hai bản sao chứa dữ liệu thật; bảo vệ như DB gốc. Snapshot nhất quán tại thời điểm dump, không bao gồm thay đổi sau đó; DB demo gốc không baseline hay bị sửa.
- Thêm cùng một user và refresh token **giả** vào hai bản sao. Sau baseline version 1 trên DB `after`, so sánh kín digest toàn bộ hàng user và refresh theo khóa với DB `before`: trùng, bao gồm UUID, hash, role, enabled và quan hệ. Lần chạy đầu tạo baseline rồi dừng do Redis test không có; chạy lại với Redis test riêng và baseline mặc định tắt thì ứng dụng khởi động và validate thành công. History có một dòng BASELINE version 1.
- Sau so sánh, dùng refresh token giả đã tồn tại trước baseline: refresh trả 200 và xoay token. JWT login và API profile trả 200; session login trả 204 và `/me` trả 200. Thử nghiệm này làm thay đổi dữ liệu token **chỉ trong bản sao after**.
- DB rỗng `adv002_fresh_1124`: backend chạy V1 rồi Hibernate validate thành công; khởi động lại thành công, history chỉ có một dòng SQL version 1. Schema sai/chưa baseline đã được kiểm thử bằng fixture ngày 23/11 như trên.
- Các DB và file dump test được giữ lại để đối chiếu; backend test đã dừng. Redis test độc lập có tên `adv002-redis-test`. Không dùng kết quả này để tự baseline DB demo thật: backup mới, kiểm tra schema và thực hiện quy trình có chủ đích khi nâng cấp từng DB.

## Database rỗng

Chuẩn bị DB PostgreSQL rỗng riêng và Redis. Đặt DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD cho môi trường đó. Chạy backend; Flyway tự chạy V1, sau đó Hibernate validate. Khởi động lần hai và kiểm tra `flyway_schema_history`: chỉ một migration V1 thành công, không tự áp dụng V1 lại.

## Database đã có dữ liệu

1. Dừng mọi backend ghi vào DB, xác nhận chính xác host, database, môi trường và user. **Không thực hiện trên DB app đang phục vụ.**
2. Sao lưu bằng `pg_dump -Fc` ra file riêng, kiểm tra thoát lệnh thành công và khôi phục backup vào DB test độc lập trước; xác nhận có thể kết nối và dữ liệu fixture được bảo toàn. Không ghi credential vào log.
3. So sánh schema bản sao với [V1](../../../backend/src/main/resources/db/migration/V1__mini_schema.sql): cột/type/nullability, PK, unique, check, FK, index, các bảng/trigger bổ sung; kiểm tra orphan và role khác ADMIN/CUSTOMER. `ddl-auto=validate` không kiểm tra đầy đủ các ràng buộc này.
4. Nếu khác biệt, dừng; thiết kế migration phù hợp và thử lại trên bản sao. Đặc biệt nếu thiếu FK `refresh_tokens.user_id → app_users.id`, hoặc ID còn bigint, không baseline như thể schema đã tương đương. Script `scripts/migrate-user-id-to-uuidv7.sql` là script lịch sử, không tự chạy và từ chối xử lý khi có FK tham chiếu user.
5. Chỉ sau khi chứng minh tương đương, dùng Flyway baseline version 1 có chủ đích theo tài liệu Flyway hoặc cấu hình tạm `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` **chỉ cho một lần chạy đã xác nhận đúng DB**. Không ghi tùy chọn này vào cấu hình mặc định. Baseline không chạy V1: nó chỉ ghi marker và bỏ qua migration đến V1.
6. Xóa biến tạm, chạy lại bình thường, kiểm tra history và login/refresh bằng tài khoản test; đối chiếu dữ liệu trước/sau. Nếu có lỗi hoặc checksum bất thường, dừng, điều tra và khôi phục theo backup đã thử; không tự `repair`, `clean` hay xóa volume.

Ví dụ kiểm tra schema qua psql (thay cách kết nối cho đúng DB test):

```sql
\d+ app_users
\d+ refresh_tokens
SELECT count(*) AS orphan_count FROM refresh_tokens r LEFT JOIN app_users u ON u.id=r.user_id WHERE u.id IS NULL;
SELECT role, count(*) FROM app_users GROUP BY role;
SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
```

Nguồn: [Flyway baseline](https://documentation.red-gate.com/fd/baseline-277578867.html), [baseline-on-migrate và giới hạn an toàn](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

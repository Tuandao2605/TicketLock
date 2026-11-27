-- Dữ liệu mẫu cho môi trường phát triển. Không sửa hoặc xóa dữ liệu có sẵn.
-- ID cố định theo namespace riêng giúp chạy lại an toàn.
BEGIN;

CREATE TEMP TABLE demo_events (n integer, id uuid, name text, description text) ON COMMIT DROP;
INSERT INTO demo_events VALUES
  (1, md5('orbit-demo-event-1')::uuid, '[DEMO] Đêm nhạc Dải Ngân Hà', 'Một đêm âm nhạc giữa ánh sao và những giai điệu vũ trụ.'),
  (2, md5('orbit-demo-event-2')::uuid, '[DEMO] Hành trình tới Sao Hỏa', 'Khám phá không gian qua triển lãm và trải nghiệm tương tác.'),
  (3, md5('orbit-demo-event-3')::uuid, '[DEMO] Lễ hội Ánh sáng Thiên Hà', 'Sự kiện ánh sáng và nghệ thuật lấy cảm hứng từ các thiên hà.');

INSERT INTO events (id, name, description, created_at)
SELECT id, name, description, now() FROM demo_events
ON CONFLICT (id) DO NOTHING;

INSERT INTO shows (id, event_id, starts_at, venue, status, sales_opened_at, created_at)
SELECT md5('orbit-demo-show-' || e.n || '-' || s.n)::uuid, e.id,
       now() + (e.n * 14 + s.n * 7) * interval '1 day',
       CASE s.n WHEN 1 THEN 'Nhà hát Sao Bắc Đẩu' ELSE 'Trung tâm Thiên Hà' END,
       'PUBLISHED', now(), now()
FROM demo_events e CROSS JOIN generate_series(1, 2) AS s(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO seats (id, event_id, label)
SELECT md5('orbit-demo-seat-' || e.n || '-' || s.n)::uuid, e.id,
       chr(65 + (s.n - 1) / 8) || ((s.n - 1) % 8 + 1)::text
FROM demo_events e CROSS JOIN generate_series(1, 40) AS s(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO show_seats (id, show_id, seat_id, event_id, status, reservation_id, hold_expires_at, version, price_amount, currency)
SELECT md5('orbit-demo-inventory-' || e.n || '-' || sh.n || '-' || s.n)::uuid,
       md5('orbit-demo-show-' || e.n || '-' || sh.n)::uuid,
       md5('orbit-demo-seat-' || e.n || '-' || s.n)::uuid, e.id,
       CASE WHEN s.n IN (5, 16) THEN 'HELD' WHEN s.n IN (7, 18, 25) THEN 'SOLD' ELSE 'AVAILABLE' END,
       CASE WHEN s.n IN (5, 16) THEN md5('orbit-demo-hold-' || e.n || '-' || sh.n || '-' || s.n)::uuid ELSE NULL END,
       CASE WHEN s.n IN (5, 16) THEN now() + interval '30 days' ELSE NULL END,
       0, CASE WHEN s.n <= 16 THEN 350000 ELSE 200000 END, 'VND'
FROM demo_events e CROSS JOIN generate_series(1, 2) AS sh(n) CROSS JOIN generate_series(1, 40) AS s(n)
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Dữ liệu giả cho DB test/demo độc lập đã migrate V3. Không dùng trên DB thật.
-- Chạy lặp với cùng dữ liệu, không tự sửa layout Show đã publish.
BEGIN;
INSERT INTO events (id, name, description, created_at)
VALUES ('01900000-0000-7000-8000-000000000001', 'Demo Event', 'Du lieu demo', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO shows (id, event_id, starts_at, venue, status, created_at)
VALUES ('01900000-0000-7000-8000-000000000002', '01900000-0000-7000-8000-000000000001', '2099-01-01T12:00:00Z', 'Demo Hall', 'DRAFT', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO seats (id, event_id, label)
VALUES ('01900000-0000-7000-8000-000000000003', '01900000-0000-7000-8000-000000000001', 'A1'),
       ('01900000-0000-7000-8000-000000000004', '01900000-0000-7000-8000-000000000001', 'A2')
ON CONFLICT DO NOTHING;
WITH locked_show AS (
  SELECT id, event_id FROM shows WHERE id = '01900000-0000-7000-8000-000000000002'
    AND event_id = '01900000-0000-7000-8000-000000000001' AND status = 'DRAFT' AND sales_opened_at IS NULL FOR UPDATE
)
INSERT INTO show_seats (id, show_id, seat_id, event_id, status, version)
SELECT v.id::uuid, l.id, s.id, l.event_id, 'AVAILABLE', 0
FROM locked_show l JOIN seats s ON s.event_id = l.event_id
JOIN (VALUES ('01900000-0000-7000-8000-000000000005', 'A1'), ('01900000-0000-7000-8000-000000000006', 'A2')) v(id,label) ON s.label = v.label
ON CONFLICT DO NOTHING;
COMMIT;

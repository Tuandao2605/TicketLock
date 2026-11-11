-- PostgreSQL 17 lab migration. Chạy khi backend đã dừng.
-- Giữ tài khoản và bảng ánh xạ ID cũ-mới; không tự chuyển các khóa ngoại.
BEGIN;
LOCK TABLE app_users IN ACCESS EXCLUSIVE MODE;

-- PostgreSQL 17 chưa có uuidv7() tích hợp. Hàm tạm dùng cho migration:
-- 48 bit Unix milliseconds, version 7, random payload và RFC variant.
CREATE FUNCTION pg_temp.migration_uuidv7() RETURNS uuid
LANGUAGE sql VOLATILE AS $$
    SELECT (
        lpad(to_hex(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0')
        || '7' || substr(replace(gen_random_uuid()::text, '-', ''), 14, 3)
        || substr(replace(gen_random_uuid()::text, '-', ''), 17, 16)
    )::uuid;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'app_users'
          AND column_name = 'id' AND data_type = 'bigint'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE contype = 'f' AND confrelid = 'app_users'::regclass
        ) THEN
            RAISE EXCEPTION 'Có khóa ngoại tham chiếu app_users. Cần migration riêng cho bảng liên quan.';
        END IF;

        CREATE TABLE IF NOT EXISTS user_id_migration (
            old_id bigint PRIMARY KEY,
            new_id uuid NOT NULL UNIQUE
        );
        INSERT INTO user_id_migration(old_id, new_id)
            SELECT id, pg_temp.migration_uuidv7() FROM app_users
            ON CONFLICT (old_id) DO NOTHING;

        -- Giữ bảng ánh xạ để đối chiếu; không suy ra UUID bằng cách cast BIGINT.
        ALTER TABLE app_users ADD COLUMN id_uuid uuid;
        UPDATE app_users u SET id_uuid = m.new_id
            FROM user_id_migration m WHERE u.id = m.old_id;
        ALTER TABLE app_users ALTER COLUMN id_uuid SET NOT NULL;
        ALTER TABLE app_users DROP COLUMN id;
        ALTER TABLE app_users RENAME COLUMN id_uuid TO id;
        ALTER TABLE app_users ADD PRIMARY KEY (id);
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'app_users'
          AND column_name = 'id' AND data_type = 'uuid'
    ) THEN
        RAISE EXCEPTION 'Kiểu app_users.id không được hỗ trợ.';
    END IF;
END;
$$;
COMMIT;

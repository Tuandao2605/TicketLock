-- Local account: admin@ticketlock.test / AdminLocal123!
-- Run manually. Existing accounts are unchanged.
CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO app_users (id, email, password_hash, role, enabled, created_at)
VALUES ('01a1c334-1054-769f-93dc-d7056cbfc21d', 'admin@ticketlock.test', '$2a$10$5o07nQmbLMps0ORsRQn/nO.SRScBfuz8.NkF11c3njxvmBbEBp3wS', 'ADMIN', true, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

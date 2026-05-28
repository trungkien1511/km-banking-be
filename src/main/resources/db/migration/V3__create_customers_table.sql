CREATE TABLE IF NOT EXISTS customers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    kyc_status           VARCHAR(50) NOT NULL DEFAULT 'NOT_SUBMITTED',
    kyc_data             JSONB,
    address_line1        VARCHAR(255),
    address_line2        VARCHAR(255),
    city                 VARCHAR(100),
    country              VARCHAR(100),
    postal_code          VARCHAR(20),
    kyc_verified_at      TIMESTAMPTZ,
    kyc_rejected_reason  TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers(user_id);

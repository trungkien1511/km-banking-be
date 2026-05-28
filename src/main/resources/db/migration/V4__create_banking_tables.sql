CREATE TABLE IF NOT EXISTS bank_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID           NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    account_number    VARCHAR(50)    NOT NULL UNIQUE,
    account_type      VARCHAR(50)    NOT NULL,
    balance           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'VND',
    status            VARCHAR(50)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bank_accounts_customer_id    ON bank_accounts(customer_id);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_account_number ON bank_accounts(account_number);

CREATE TABLE IF NOT EXISTS transactions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id      UUID REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    initiated_by           UUID REFERENCES users(id) ON DELETE SET NULL,
    amount                 NUMERIC(19, 4) NOT NULL,
    fee                    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency               VARCHAR(3)     NOT NULL DEFAULT 'VND',
    transaction_type       VARCHAR(50)    NOT NULL,
    status                 VARCHAR(50)    NOT NULL,
    reference_number       VARCHAR(100)   NOT NULL UNIQUE,
    description            TEXT,
    initiated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    completed_at           TIMESTAMPTZ,
    failed_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_source_account      ON transactions(source_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_destination_account ON transactions(destination_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_reference           ON transactions(reference_number);
CREATE INDEX IF NOT EXISTS idx_transactions_initiated_by        ON transactions(initiated_by);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID           NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    account_id      UUID           NOT NULL REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    entry_type      VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19, 4) NOT NULL,
    running_balance NUMERIC(19, 4) NOT NULL,
    balance_after   NUMERIC(19, 4) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_transaction ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account     ON ledger_entries(account_id);

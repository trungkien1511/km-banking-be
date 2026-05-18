-- ===================================================================
-- V3: Core Transactions & Double-Entry Ledger Bookkeeping Schema
-- ===================================================================
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id UUID REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    amount DECIMAL(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    transaction_type VARCHAR(30) NOT NULL, -- TRANSFER, DEPOSIT, WITHDRAW, PAYMENT
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED
    reference_number VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    entry_type VARCHAR(10) NOT NULL, -- DEBIT, CREDIT
    amount DECIMAL(18, 4) NOT NULL,
    running_balance DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indices for rapid balance auditing and account history queries
CREATE INDEX idx_trans_src ON transactions(source_account_id);
CREATE INDEX idx_trans_dest ON transactions(destination_account_id);
CREATE INDEX idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_trans ON ledger_entries(transaction_id);

CREATE TABLE cash_transactions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  type VARCHAR(20) NOT NULL,
  amount DECIMAL(15, 2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'USD',
  transaction_date DATE NOT NULL,
  description VARCHAR(255),
  fx_rate_to_base DECIMAL(10, 6),
  broker_transaction_id VARCHAR(100),
  source VARCHAR(20) DEFAULT 'MANUAL',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cash_tx_portfolio ON cash_transactions(portfolio_id);
CREATE INDEX idx_cash_tx_date ON cash_transactions(portfolio_id, transaction_date);

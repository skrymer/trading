-- FX tracking for multi-currency portfolios (e.g., AUD account trading USD instruments)

-- Add FX rate to executions (AUD per USD at time of trade)
ALTER TABLE executions ADD COLUMN fx_rate_to_base DECIMAL(12, 6);

-- Add base currency to portfolios (the account's home currency)
ALTER TABLE portfolios ADD COLUMN base_currency VARCHAR(10) DEFAULT 'USD';

-- Add base-currency realized P&L to positions
ALTER TABLE positions ADD COLUMN realized_pnl_base DECIMAL(15, 2);

-- Forex lots: tracks each USD acquisition for FIFO tax reporting
CREATE TABLE forex_lots (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  acquisition_date DATE NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'USD',
  quantity DECIMAL(15, 2) NOT NULL,
  remaining_quantity DECIMAL(15, 2) NOT NULL,
  cost_rate DECIMAL(12, 6) NOT NULL,
  cost_basis DECIMAL(15, 2) NOT NULL,
  source_execution_id BIGINT,
  source_description VARCHAR(255),
  status VARCHAR(10) DEFAULT 'OPEN',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
  FOREIGN KEY (source_execution_id) REFERENCES executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_forex_lots_portfolio ON forex_lots(portfolio_id);
CREATE INDEX idx_forex_lots_status ON forex_lots(portfolio_id, status);

-- Forex disposals: records each FIFO disposal for tax reporting
CREATE TABLE forex_disposals (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  disposal_date DATE NOT NULL,
  quantity DECIMAL(15, 2) NOT NULL,
  cost_rate DECIMAL(12, 6) NOT NULL,
  disposal_rate DECIMAL(12, 6) NOT NULL,
  cost_basis_aud DECIMAL(15, 2) NOT NULL,
  proceeds_aud DECIMAL(15, 2) NOT NULL,
  realized_fx_pnl DECIMAL(15, 2) NOT NULL,
  source_execution_id BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
  FOREIGN KEY (lot_id) REFERENCES forex_lots(id) ON DELETE CASCADE,
  FOREIGN KEY (source_execution_id) REFERENCES executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_forex_disposals_portfolio ON forex_disposals(portfolio_id);
CREATE INDEX idx_forex_disposals_lot ON forex_disposals(lot_id);

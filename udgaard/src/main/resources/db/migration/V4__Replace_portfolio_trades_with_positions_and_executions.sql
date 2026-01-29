-- V4: Replace portfolio_trades with positions + executions architecture
-- This migration implements the Position/Execution model to properly handle broker imports

-- 1. Create positions table (user-facing aggregate)
CREATE TABLE positions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  symbol VARCHAR(50) NOT NULL,
  underlying_symbol VARCHAR(50),
  instrument_type VARCHAR(20) NOT NULL,

  -- Options-specific fields
  option_type VARCHAR(10),
  strike_price DECIMAL(10, 2),
  expiration_date DATE,
  multiplier INT DEFAULT 100,

  -- Position state (aggregated from executions)
  current_quantity INT NOT NULL,
  current_contracts INT,
  average_entry_price DECIMAL(10, 2) NOT NULL,
  total_cost DECIMAL(15, 2) NOT NULL,
  status VARCHAR(10) NOT NULL,

  -- Dates
  opened_date DATE NOT NULL,
  closed_date DATE,

  -- P&L
  realized_pnl DECIMAL(15, 2),

  -- Rolling (clean 1-to-1 relationship)
  rolled_to_position_id BIGINT,
  parent_position_id BIGINT,
  roll_number INT DEFAULT 0,

  -- Strategy (editable metadata)
  entry_strategy VARCHAR(50),
  exit_strategy VARCHAR(50),

  -- Metadata (editable)
  notes TEXT,
  currency VARCHAR(10) DEFAULT 'USD',
  source VARCHAR(20) DEFAULT 'MANUAL', -- 'BROKER' or 'MANUAL'

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
  FOREIGN KEY (rolled_to_position_id) REFERENCES positions(id),
  FOREIGN KEY (parent_position_id) REFERENCES positions(id)
);

-- Create indexes for positions table
CREATE INDEX idx_portfolio_status ON positions(portfolio_id, status);
CREATE INDEX idx_symbol ON positions(symbol);
CREATE INDEX idx_source ON positions(source);

-- 2. Create executions table (immutable broker transaction history)
CREATE TABLE executions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  position_id BIGINT NOT NULL,
  broker_trade_id VARCHAR(100),
  linked_broker_trade_id VARCHAR(100),

  -- Execution details (signed quantity: positive = buy, negative = sell)
  quantity INT NOT NULL,
  price DECIMAL(10, 2) NOT NULL,
  execution_date DATE NOT NULL,
  execution_time TIME,

  -- Costs
  commission DECIMAL(10, 2),

  -- Metadata
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE
);

-- Create indexes for executions table
CREATE UNIQUE INDEX idx_broker_trade_id ON executions(broker_trade_id);
CREATE INDEX idx_position ON executions(position_id);
CREATE INDEX idx_execution_date ON executions(execution_date);

-- 3. Drop old portfolio_trades table
DROP TABLE IF EXISTS portfolio_trades;

-- 4. Update portfolios table - simplify broker fields
ALTER TABLE portfolios DROP COLUMN IF EXISTS broker_account_id;
ALTER TABLE portfolios DROP COLUMN IF EXISTS broker_query_id;

ALTER TABLE portfolios ADD COLUMN IF NOT EXISTS broker VARCHAR(20);
ALTER TABLE portfolios ADD COLUMN IF NOT EXISTS last_sync_date TIMESTAMP;

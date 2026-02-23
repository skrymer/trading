-- Scanner trades table for tracking stock scan opportunities

CREATE TABLE scanner_trades (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  sector_symbol VARCHAR(50),
  instrument_type VARCHAR(20) NOT NULL,
  entry_price DECIMAL(10, 2) NOT NULL,
  entry_date DATE NOT NULL,
  quantity INT NOT NULL,
  -- Option fields
  option_type VARCHAR(10),
  strike_price DECIMAL(10, 2),
  expiration_date DATE,
  multiplier INT DEFAULT 100,
  -- Strategy
  entry_strategy_name VARCHAR(100) NOT NULL,
  exit_strategy_name VARCHAR(100) NOT NULL,
  -- Rolling
  rolled_credits DECIMAL(15, 2) DEFAULT 0,
  roll_count INT DEFAULT 0,
  -- Metadata
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scanner_trades_symbol ON scanner_trades(symbol);
CREATE INDEX idx_scanner_trades_entry_date ON scanner_trades(entry_date);

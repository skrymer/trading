-- Initial database schema for Trading Platform

-- ========================================
-- STOCK TABLES
-- ========================================

CREATE TABLE stocks (
  symbol VARCHAR(50) PRIMARY KEY,
  sector_symbol VARCHAR(50),
  market_cap BIGINT
);

CREATE TABLE stock_quotes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  quote_date DATE NOT NULL,
  open_price DECIMAL(19, 4),
  close_price DECIMAL(19, 4),
  high_price DECIMAL(19, 4),
  low_price DECIMAL(19, 4),
  volume BIGINT,
  atr DECIMAL(19, 4),
  adx DECIMAL(19, 4),
  donchian_high DECIMAL(19, 4),
  donchian_mid DECIMAL(19, 4),
  donchian_low DECIMAL(19, 4),
  donchian_upper_band DECIMAL(19, 4),
  donchian_channel_score INT,
  in_uptrend BOOLEAN,
  buy_signal BOOLEAN,
  sell_signal BOOLEAN,
  close_price_ema5 DECIMAL(19, 4),
  close_price_ema10 DECIMAL(19, 4),
  close_price_ema20 DECIMAL(19, 4),
  close_price_ema50 DECIMAL(19, 4),
  close_price_ema100 DECIMAL(19, 4),
  close_price_ema200 DECIMAL(19, 4),
  trend VARCHAR(20),
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_stock_quotes_symbol ON stock_quotes(stock_symbol);
CREATE INDEX idx_stock_quotes_date ON stock_quotes(quote_date);
CREATE INDEX idx_stock_quotes_symbol_date ON stock_quotes(stock_symbol, quote_date);
CREATE INDEX idx_stock_quotes_date_trend ON stock_quotes(quote_date, trend);

CREATE TABLE order_blocks (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  type VARCHAR(20) NOT NULL,
  sensitivity VARCHAR(20) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  start_price DECIMAL(19, 4) NOT NULL,
  end_price DECIMAL(19, 4) NOT NULL,
  low_price DECIMAL(19, 4),
  high_price DECIMAL(19, 4),
  volume BIGINT NOT NULL,
  volume_strength DECIMAL(19, 4),
  rate_of_change DECIMAL(19, 4),
  is_active BOOLEAN NOT NULL,
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_order_blocks_symbol ON order_blocks(stock_symbol);
CREATE INDEX idx_order_blocks_active ON order_blocks(is_active);

CREATE TABLE earnings (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  symbol VARCHAR(50),
  fiscal_date_ending DATE NOT NULL,
  reported_date DATE,
  reported_eps DECIMAL(19, 4),
  reportedeps DECIMAL(19, 4),
  estimated_eps DECIMAL(19, 4),
  estimatedeps DECIMAL(19, 4),
  surprise DECIMAL(19, 4),
  surprise_percentage DECIMAL(19, 4),
  report_time VARCHAR(50),
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_earnings_symbol ON earnings(stock_symbol);
CREATE INDEX idx_earnings_date ON earnings(fiscal_date_ending);

-- ========================================
-- BREADTH TABLES (self-computed from stock data)
-- ========================================

CREATE TABLE market_breadth_daily (
  quote_date DATE PRIMARY KEY,
  breadth_percent DECIMAL(19, 4) NOT NULL,
  ema_5 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_10 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_20 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_50 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  donchian_upper_band DECIMAL(19, 4) NOT NULL DEFAULT 0,
  donchian_lower_band DECIMAL(19, 4) NOT NULL DEFAULT 0
);

CREATE TABLE sector_breadth_daily (
  sector_symbol VARCHAR(10) NOT NULL,
  quote_date DATE NOT NULL,
  stocks_in_uptrend INT NOT NULL,
  stocks_in_downtrend INT NOT NULL,
  total_stocks INT NOT NULL,
  bull_percentage DECIMAL(19, 4) NOT NULL,
  ema_5 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_10 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_20 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  ema_50 DECIMAL(19, 4) NOT NULL DEFAULT 0,
  donchian_upper_band DECIMAL(19, 4) NOT NULL DEFAULT 0,
  donchian_lower_band DECIMAL(19, 4) NOT NULL DEFAULT 0,
  PRIMARY KEY (sector_symbol, quote_date)
);

CREATE INDEX idx_sector_breadth_sector ON sector_breadth_daily(sector_symbol);
CREATE INDEX idx_sector_breadth_date ON sector_breadth_daily(quote_date);

-- ========================================
-- PORTFOLIO TABLES
-- ========================================

CREATE TABLE portfolios (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  initial_balance DECIMAL(19, 2) NOT NULL,
  current_balance DECIMAL(19, 2) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  user_id VARCHAR(255),
  broker VARCHAR(20),
  broker_config TEXT,
  last_sync_date TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_portfolios_user_id ON portfolios(user_id);

CREATE TABLE positions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  symbol VARCHAR(50) NOT NULL,
  underlying_symbol VARCHAR(50),
  instrument_type VARCHAR(20) NOT NULL,
  option_type VARCHAR(10),
  strike_price DECIMAL(10, 2),
  expiration_date DATE,
  multiplier INT DEFAULT 100,
  current_quantity INT NOT NULL,
  current_contracts INT,
  average_entry_price DECIMAL(10, 2) NOT NULL,
  total_cost DECIMAL(15, 2) NOT NULL,
  status VARCHAR(10) NOT NULL,
  opened_date DATE NOT NULL,
  closed_date DATE,
  realized_pnl DECIMAL(15, 2),
  rolled_to_position_id BIGINT,
  parent_position_id BIGINT,
  roll_number INT DEFAULT 0,
  entry_strategy VARCHAR(50),
  exit_strategy VARCHAR(50),
  notes TEXT,
  currency VARCHAR(10) DEFAULT 'USD',
  source VARCHAR(20) DEFAULT 'MANUAL',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE,
  FOREIGN KEY (rolled_to_position_id) REFERENCES positions(id),
  FOREIGN KEY (parent_position_id) REFERENCES positions(id)
);

CREATE INDEX idx_portfolio_status ON positions(portfolio_id, status);
CREATE INDEX idx_symbol ON positions(symbol);
CREATE INDEX idx_source ON positions(source);

CREATE TABLE executions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  position_id BIGINT NOT NULL,
  broker_trade_id VARCHAR(100),
  linked_broker_trade_id VARCHAR(100),
  quantity INT NOT NULL,
  price DECIMAL(10, 2) NOT NULL,
  execution_date DATE NOT NULL,
  execution_time TIME,
  commission DECIMAL(10, 2),
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_broker_trade_id ON executions(broker_trade_id);
CREATE INDEX idx_position ON executions(position_id);
CREATE INDEX idx_execution_date ON executions(execution_date);

-- ========================================
-- REFERENCE DATA
-- ========================================

CREATE TABLE symbols (
  symbol VARCHAR(50) PRIMARY KEY,
  asset_type VARCHAR(20) NOT NULL
);

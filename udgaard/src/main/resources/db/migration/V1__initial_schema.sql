-- Initial database schema for Trading Platform

-- ========================================
-- STOCK TABLES
-- ========================================

CREATE TABLE stocks (
  symbol VARCHAR(50) PRIMARY KEY,
  sector_symbol VARCHAR(50)
);

CREATE TABLE stock_quotes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  quote_date DATE NOT NULL,
  open_price DECIMAL(19, 4),
  close_price DECIMAL(19, 4),
  high_price DECIMAL(19, 4),
  low_price DECIMAL(19, 4),
  volume BIGINT,
  adjusted_close DECIMAL(19, 4),
  atr DECIMAL(19, 4),
  adx DECIMAL(19, 4),
  ema5 DECIMAL(19, 4),
  ema10 DECIMAL(19, 4),
  ema20 DECIMAL(19, 4),
  ema50 DECIMAL(19, 4),
  ema200 DECIMAL(19, 4),
  donchian_high DECIMAL(19, 4),
  donchian_mid DECIMAL(19, 4),
  donchian_low DECIMAL(19, 4),
  donchian_upper_band DECIMAL(19, 4),
  donchian_upper_band_market DECIMAL(19, 4),
  donchian_upper_band_sector DECIMAL(19, 4),
  donchian_lower_band_market DECIMAL(19, 4),
  donchian_lower_band_sector DECIMAL(19, 4),
  donchian_channel_score INT,
  in_uptrend BOOLEAN,
  buy_signal BOOLEAN,
  sell_signal BOOLEAN,
  heatmap DECIMAL(19, 4),
  previous_heatmap DECIMAL(19, 4),
  stock_heatmap DECIMAL(19, 4),
  previous_stock_heatmap DECIMAL(19, 4),
  sector_heatmap DECIMAL(19, 4),
  previous_sector_heatmap DECIMAL(19, 4),
  sector_is_in_uptrend BOOLEAN,
  sector_donkey_channel_score INT,
  signal VARCHAR(20),
  close_price_ema5 DECIMAL(19, 4),
  close_price_ema10 DECIMAL(19, 4),
  close_price_ema20 DECIMAL(19, 4),
  close_price_ema50 DECIMAL(19, 4),
  trend VARCHAR(20),
  last_buy_signal DATE,
  last_sell_signal DATE,
  spy_signal VARCHAR(20),
  spy_in_uptrend BOOLEAN,
  spy_heatmap DECIMAL(19, 4),
  spy_previous_heatmap DECIMAL(19, 4),
  spy_ema200 DECIMAL(19, 4),
  spy_sma200 DECIMAL(19, 4),
  spy_ema50 DECIMAL(19, 4),
  spy_days_above_200sma INT,
  spy_buy_signal BOOLEAN,
  spy_sell_signal BOOLEAN,
  market_heatmap DECIMAL(19, 4),
  previous_market_heatmap DECIMAL(19, 4),
  market_advancing_percent DECIMAL(19, 4),
  market_is_in_uptrend BOOLEAN,
  market_donkey_channel_score INT,
  market_in_uptrend BOOLEAN,
  sector_in_uptrend BOOLEAN,
  market_donchey_channel_score INT,
  sector_donchey_channel_score INT,
  previous_quote_date DATE,
  sector_breadth DECIMAL(19, 4),
  sector_stocks_in_downtrend INT,
  sector_stocks_in_uptrend INT,
  sector_bull_percentage DECIMAL(19, 4),
  sector_stocks_above_ema INT,
  sector_stocks_count INT,
  market_stocks_above_ema INT,
  market_stocks_count INT,
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_stock_quotes_symbol ON stock_quotes(stock_symbol);
CREATE INDEX idx_stock_quotes_date ON stock_quotes(quote_date);
CREATE INDEX idx_stock_quotes_symbol_date ON stock_quotes(stock_symbol, quote_date);

CREATE TABLE order_blocks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
-- BREADTH TABLES
-- ========================================

CREATE TABLE breadth (
  symbol_type VARCHAR(50) NOT NULL,
  symbol_value VARCHAR(50) NOT NULL,
  PRIMARY KEY (symbol_type, symbol_value)
);

CREATE TABLE breadth_quotes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol_type VARCHAR(50) NOT NULL,
  symbol_value VARCHAR(50) NOT NULL,
  symbol VARCHAR(50),
  quote_date DATE NOT NULL,
  stocks_with_buy_signal INT,
  stocks_with_sell_signal INT,
  stocks_in_uptrend INT,
  stocks_in_neutral INT,
  stocks_in_downtrend INT,
  bull_stocks_percentage DECIMAL(19, 4),
  ema_5 DECIMAL(19, 4),
  ema_10 DECIMAL(19, 4),
  ema_20 DECIMAL(19, 4),
  ema_50 DECIMAL(19, 4),
  heatmap DECIMAL(19, 4),
  previous_heatmap DECIMAL(19, 4),
  donchian_upper_band DECIMAL(19, 4),
  previous_donchian_upper_band DECIMAL(19, 4),
  donchian_lower_band DECIMAL(19, 4),
  previous_donchian_lower_band DECIMAL(19, 4),
  donkey_channel_score INT,
  FOREIGN KEY (symbol_type, symbol_value) REFERENCES breadth(symbol_type, symbol_value) ON DELETE CASCADE
);

CREATE INDEX idx_breadth_quotes_symbol ON breadth_quotes(symbol_type, symbol_value);
CREATE INDEX idx_breadth_quotes_date ON breadth_quotes(quote_date);

-- ========================================
-- PORTFOLIO TABLES
-- ========================================

CREATE TABLE portfolios (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  initial_balance DECIMAL(19, 2) NOT NULL,
  current_balance DECIMAL(19, 2) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  user_id VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_portfolios_user_id ON portfolios(user_id);

CREATE TABLE portfolio_trades (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  portfolio_id BIGINT NOT NULL,
  symbol VARCHAR(50) NOT NULL,
  instrument_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  entry_date DATE NOT NULL,
  exit_date DATE,
  entry_price DECIMAL(19, 4) NOT NULL,
  exit_price DECIMAL(19, 4),
  quantity INT NOT NULL,
  entry_strategy VARCHAR(255),
  exit_strategy VARCHAR(255),
  exit_reason VARCHAR(255),
  option_type VARCHAR(10),
  strike_price DECIMAL(19, 4),
  expiration_date DATE,
  contracts INT,
  multiplier INT,
  entry_intrinsic_value DECIMAL(19, 4),
  entry_extrinsic_value DECIMAL(19, 4),
  exit_intrinsic_value DECIMAL(19, 4),
  exit_extrinsic_value DECIMAL(19, 4),
  underlying_entry_price DECIMAL(19, 4),
  underlying_symbol VARCHAR(50),
  currency VARCHAR(10),
  parent_trade_id BIGINT,
  rolled_to_trade_id BIGINT,
  roll_number INT,
  original_entry_date DATE,
  original_cost_basis DECIMAL(19, 4),
  cumulative_realized_profit DECIMAL(19, 4),
  total_roll_cost DECIMAL(19, 4),
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_trades_portfolio_id ON portfolio_trades(portfolio_id);
CREATE INDEX idx_portfolio_trades_status ON portfolio_trades(status);
CREATE INDEX idx_portfolio_trades_entry_date ON portfolio_trades(entry_date);

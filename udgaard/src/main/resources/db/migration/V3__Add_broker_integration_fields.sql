-- Add broker integration fields to portfolios table
ALTER TABLE portfolios
ADD COLUMN broker VARCHAR(50) DEFAULT 'MANUAL';

ALTER TABLE portfolios
ADD COLUMN broker_account_id VARCHAR(100);

ALTER TABLE portfolios
ADD COLUMN broker_config TEXT;  -- JSON string for flexible broker-specific config

ALTER TABLE portfolios
ADD COLUMN last_sync_date TIMESTAMP;

-- Add broker integration fields to portfolio_trades table
ALTER TABLE portfolio_trades
ADD COLUMN broker_trade_id VARCHAR(100);

ALTER TABLE portfolio_trades
ADD COLUMN linked_broker_trade_id VARCHAR(100);

-- Create index for broker trade ID lookups (used during sync)
CREATE INDEX idx_portfolio_trades_broker_trade_id ON portfolio_trades(broker_trade_id);

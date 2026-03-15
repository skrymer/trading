-- Add close/status fields for soft-delete trade tracking
ALTER TABLE scanner_trades ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE scanner_trades ADD COLUMN exit_price DECIMAL(10, 2);
ALTER TABLE scanner_trades ADD COLUMN exit_date DATE;
ALTER TABLE scanner_trades ADD COLUMN realized_pnl DECIMAL(15, 2);
ALTER TABLE scanner_trades ADD COLUMN closed_at TIMESTAMP;

CREATE INDEX idx_scanner_trades_status ON scanner_trades(status);

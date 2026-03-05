-- Store the FX rate at portfolio start date (e.g., AUD/USD rate when portfolio was created)
-- Used to calculate FX impact on the initial USD balance
ALTER TABLE portfolios ADD COLUMN initial_fx_rate DECIMAL(12, 6);

-- V6: Move sector_symbol from stocks table to symbols table
-- Sector is reference data that rarely changes, so it belongs with the symbol definition.
-- This eliminates the need to fetch company info during every stock refresh.

ALTER TABLE symbols ADD COLUMN sector_symbol VARCHAR(50);

-- Migrate existing sector data from stocks to symbols
UPDATE symbols s
SET sector_symbol = st.sector_symbol
FROM stocks st
WHERE s.symbol = st.symbol
AND st.sector_symbol IS NOT NULL;

-- Drop sector_symbol and market_cap from stocks table
ALTER TABLE stocks DROP COLUMN sector_symbol;
ALTER TABLE stocks DROP COLUMN market_cap;

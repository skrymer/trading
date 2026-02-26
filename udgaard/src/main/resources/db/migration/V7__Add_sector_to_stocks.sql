-- V7: Add sector column to stocks table (populated from Midgaard)
-- Sector is now stored directly on stocks for simpler joins.

ALTER TABLE stocks ADD COLUMN sector VARCHAR(50);

-- Migrate existing sector data from symbols to stocks
UPDATE stocks st
SET sector = s.sector_symbol
FROM symbols s
WHERE st.symbol = s.symbol
AND s.sector_symbol IS NOT NULL;

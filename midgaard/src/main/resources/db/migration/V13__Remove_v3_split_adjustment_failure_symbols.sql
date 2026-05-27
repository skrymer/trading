-- V13: Remove symbols matching `BadPrintIntegrityValidator`'s V3 invariant —
-- bars where close >= 5x the prior bar AND the next bar holds at >= 50% of
-- the spike AND the prior bar is sub-cent AND the symbol has at least 4 bars
-- of prior history (bar_position >= 5). This is the AT-class case that V2
-- (which requires prev >= 0.01) misses: real low-price pre-split history
-- followed by a reverse-split adjustment that the provider failed to back-
-- propagate. The bar-position floor separates this from IHG-class stub-first-
-- bar artifacts where prev is sub-cent only because it's the symbol's very
-- first bar.
--
-- Why symbol-level removal: same reasoning as V11/V12. Each contaminated bar
-- invalidates any backtest trade that opens, closes, or crosses it. Cheaper
-- to drop the whole symbol than reason about every possible trade
-- interaction. Cost: ~31 symbols (20 delisted + 11 active including KNTK,
-- AMTB, TWO, ALT, AT, BUSE, ORLA). Accepted as collateral damage.
--
-- Re-ingestion: same as V11/V12 — `updateAll()` iterates `ingestion_status`,
-- so once those rows are gone the symbols stay gone.

CREATE TEMPORARY TABLE v3_failure_symbols AS
WITH neighboured AS (
    SELECT
        symbol,
        close_price,
        LAG(close_price)  OVER w AS prev_close,
        LEAD(close_price) OVER w AS next_close,
        ROW_NUMBER()      OVER w AS bar_position
    FROM quotes
    WINDOW w AS (PARTITION BY symbol ORDER BY quote_date)
)
SELECT DISTINCT symbol
FROM neighboured
WHERE prev_close > 0
  AND prev_close < 0.01
  AND next_close > 0
  AND bar_position >= 5
  AND close_price / prev_close  >= 5
  AND next_close  / close_price >= 0.50;

-- Emit the full offender list to the Flyway migration log. The temporary
-- table is dropped at session end, so this is the only durable audit trail
-- of which symbols V13 removed.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    SELECT string_agg(symbol, ', ' ORDER BY symbol) INTO offenders FROM v3_failure_symbols;
    RAISE NOTICE 'V13 removing V3 split-adjustment-failure symbols (%): %', (SELECT COUNT(*) FROM v3_failure_symbols), COALESCE(offenders, '<none>');
END $$;

-- Delete order: children first, parent (`symbols`) last. Matches V11/V12.
DELETE FROM ovtlyr_signals   WHERE symbol IN (SELECT symbol FROM v3_failure_symbols);
DELETE FROM earnings         WHERE symbol IN (SELECT symbol FROM v3_failure_symbols);
DELETE FROM ingestion_status WHERE symbol IN (SELECT symbol FROM v3_failure_symbols);
DELETE FROM quotes           WHERE symbol IN (SELECT symbol FROM v3_failure_symbols);
DELETE FROM symbols          WHERE symbol IN (SELECT symbol FROM v3_failure_symbols);

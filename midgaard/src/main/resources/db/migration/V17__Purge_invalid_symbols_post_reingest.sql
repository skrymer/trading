-- V17: Purge invalid symbols surfaced by the 2026-06 full re-ingest. Two removal
-- classes plus one bar-level fix:
--
-- (A) Contaminated price data — BadPrintIntegrityValidator V1 (bad-print V-shape,
--     CRITICAL), V2/V3 (split-adjustment failures, HIGH; the upstream EODHD root
--     cause is tracked in issue #49). Symbol-level removal mirrors V11/V12/V13: a
--     single contaminated bar invalidates any backtest trade crossing it, so the
--     whole symbol is dropped rather than reasoning about every interaction. All
--     such symbols here are delisted.
--
-- (B) Unavailable in EODHD — 6 symbols that return no OHLCV at all (confirmed by a
--     retry of the failed ingests): ticker renames (CDAY->DAY, AAR->AIR), a warrant
--     and old delisted issues. They carry zero bars; removal just drops dead
--     catalogue rows.
--
-- (C) ERX (LEVERAGED_ETF, Heimdall leveraged-sector-rotation basket — V14) is matched by the
--     V2/V3 predicate but is a wanted instrument, so it is SPARED from removal via the
--     `symbol <> 'ERX'` guard below. Its contamination is 9 sub-cent 2008 launch bars that
--     EODHD re-serves on every re-ingest, so a one-shot migration cannot durably fix them —
--     bar-level cleanup needs an ingestion-time filter. Left in place here and tracked in #49.
--
-- Durability: initialIngestAll iterates the existing symbols catalogue (symbolRepository.findAll),
-- not EODHD re-discovery, so once these rows are gone they stay gone (matching V11-V13). This holds
-- only for symbol removal; bar-level contamination on a KEPT symbol (e.g. ERX) recurs on re-ingest,
-- which is why ERX is deferred to #49 rather than patched here.

-- (A) Contaminated symbols (V1 ∪ V2 ∪ V3), excluding the wanted ERX.
CREATE TEMPORARY TABLE invalid_symbols AS
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
WHERE symbol <> 'ERX'
  AND (
        -- V1: bad-print V-shape (spike >=5x prev, reverts to <=20% of spike)
        (prev_close >= 0.01 AND next_close > 0 AND close_price / prev_close >= 5 AND next_close / close_price <= 0.20)
        -- V2: split-adjustment failure, normal-priced prev (spike holds at >=50%)
        OR (prev_close >= 0.01 AND next_close > 0 AND close_price / prev_close >= 5 AND next_close / close_price >= 0.50)
        -- V3: split-adjustment failure, sub-cent prev confirmed by >=4 prior bars
        OR (prev_close > 0 AND prev_close < 0.01 AND next_close > 0 AND bar_position >= 5
            AND close_price / prev_close >= 5 AND next_close / close_price >= 0.50)
      );

-- (B) Symbols with no OHLCV in EODHD (renames / delisted; confirmed by retry).
CREATE TEMPORARY TABLE unavailable_symbols (symbol TEXT PRIMARY KEY);
INSERT INTO unavailable_symbols (symbol) VALUES
    ('AAR'), ('ATGE'), ('CDAY'), ('CYTOW'), ('HLXH'), ('TSOR');

-- Combined removal set; emit the full lists to the migration log as the durable
-- audit trail (temp tables are dropped at session end).
CREATE TEMPORARY TABLE symbols_to_remove AS
    SELECT symbol FROM invalid_symbols
    UNION
    SELECT symbol FROM unavailable_symbols;

DO $$
DECLARE
    contaminated   TEXT;
    unavailable    TEXT;
    active_matched TEXT;
BEGIN
    -- Safety rail: the contaminated predicate is expected to match only delisted
    -- symbols (ERX, the one active match, is already excluded above). If it ever
    -- matches a live symbol — e.g. after data drift or a re-run on changed data —
    -- abort rather than silently purge a tradable instrument.
    SELECT string_agg(i.symbol, ', ' ORDER BY i.symbol) INTO active_matched
    FROM invalid_symbols i
    JOIN symbols s ON s.symbol = i.symbol
    WHERE s.delisted_at IS NULL;
    IF active_matched IS NOT NULL THEN
        RAISE EXCEPTION 'V17 abort: contaminated predicate matched ACTIVE symbol(s): % — review before purging', active_matched;
    END IF;

    SELECT string_agg(symbol, ', ' ORDER BY symbol) INTO contaminated FROM invalid_symbols;
    SELECT string_agg(symbol, ', ' ORDER BY symbol) INTO unavailable  FROM unavailable_symbols;
    RAISE NOTICE 'V17 removing % contaminated symbols (V1/V2/V3, #49): %',
        (SELECT COUNT(*) FROM invalid_symbols), COALESCE(contaminated, '<none>');
    RAISE NOTICE 'V17 removing % EODHD-unavailable symbols: %',
        (SELECT COUNT(*) FROM unavailable_symbols), COALESCE(unavailable, '<none>');
END $$;

-- Delete order: children first, parent (`symbols`) last. Matches V11/V12/V13.
DELETE FROM ovtlyr_signals   WHERE symbol IN (SELECT symbol FROM symbols_to_remove);
DELETE FROM earnings         WHERE symbol IN (SELECT symbol FROM symbols_to_remove);
DELETE FROM ingestion_status WHERE symbol IN (SELECT symbol FROM symbols_to_remove);
DELETE FROM quotes           WHERE symbol IN (SELECT symbol FROM symbols_to_remove);
DELETE FROM symbols          WHERE symbol IN (SELECT symbol FROM symbols_to_remove);

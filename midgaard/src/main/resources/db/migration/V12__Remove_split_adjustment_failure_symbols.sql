-- V12: Remove symbols matching `BadPrintIntegrityValidator`'s V2 invariant —
-- bars where close >= 5x the prior bar's close AND the next bar holds at
-- >= 50% of the spike. This is the canonical signature of an upstream
-- split-adjustment failure: the provider (EODHD) either missed a corporate
-- action entirely (e.g. IVT had no 2014 split recorded) or applied a wrong
-- adjustment factor (e.g. AT 2014-07-22 recorded as 31-for-1 but the
-- adjusted ratio observed is 982x). Unlike V1 these are not single-bar bad
-- prints — the inflated price *holds* because the corporate action is real,
-- just unadjusted in the data.
--
-- Why symbol-level removal: bar-level surgery is more surgical but each
-- contaminated bar invalidates any backtest trade that opens, closes, or
-- crosses it. Cheaper to drop the whole symbol than reason about every
-- possible trade interaction. Cost: ~127 symbols, 63 of them still-active
-- legitimate large-caps (DAC, WFRD, KRG, AU, FE, EXPE, etc.) — accepted as
-- collateral damage. The `BadPrintIntegrityValidator` will continue to
-- surface any new V2 contamination arriving via future ingest, so the same
-- rule can be re-applied later if needed.
--
-- The `MIN_PRICE` 0.01 floor on prev_close matches the validator and
-- excludes stub/placeholder first bars (e.g. IHG's $0.0001 listing bar)
-- that would otherwise produce false positives.
--
-- Re-ingestion: same as V11 — `updateAll()` iterates `ingestion_status`, so
-- once those rows are gone the symbols stay gone.

CREATE TEMPORARY TABLE v2_failure_symbols AS
WITH neighboured AS (
    SELECT
        symbol,
        close_price,
        LAG(close_price)  OVER (PARTITION BY symbol ORDER BY quote_date) AS prev_close,
        LEAD(close_price) OVER (PARTITION BY symbol ORDER BY quote_date) AS next_close
    FROM quotes
)
SELECT DISTINCT symbol
FROM neighboured
WHERE prev_close >= 0.01
  AND next_close  > 0
  AND close_price / prev_close  >= 5
  AND next_close  / close_price >= 0.50;

-- Emit the full offender list to the Flyway migration log. The temporary
-- table is dropped at session end, so this is the only durable audit trail
-- of which symbols V12 removed.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    SELECT string_agg(symbol, ', ' ORDER BY symbol) INTO offenders FROM v2_failure_symbols;
    RAISE NOTICE 'V12 removing split-adjustment-failure symbols (%): %', (SELECT COUNT(*) FROM v2_failure_symbols), COALESCE(offenders, '<none>');
END $$;

-- Delete order: children first, parent (`symbols`) last. Matches V11.
DELETE FROM ovtlyr_signals   WHERE symbol IN (SELECT symbol FROM v2_failure_symbols);
DELETE FROM earnings         WHERE symbol IN (SELECT symbol FROM v2_failure_symbols);
DELETE FROM ingestion_status WHERE symbol IN (SELECT symbol FROM v2_failure_symbols);
DELETE FROM quotes           WHERE symbol IN (SELECT symbol FROM v2_failure_symbols);
DELETE FROM symbols          WHERE symbol IN (SELECT symbol FROM v2_failure_symbols);

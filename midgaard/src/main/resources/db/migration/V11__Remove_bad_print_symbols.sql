-- V11: Remove symbols that contain the bad-print V-shape pattern: a bar
-- whose close is >= 5x the prior bar's close AND the next bar's close
-- reverts to <= 20% of the spike. This is the canonical data-corruption
-- signature — a single corrupt close surrounded by neighbours that
-- contradict it, almost always on microscopic volume (a handful of shares).
--
-- Why: a single such bar in the universe poisons any backtest that takes
-- an entry near the spike or an exit at the spike. AAK's $0.0063 -> $30.40
-- -> $0.0063 sequence produced phantom +30,000% trade returns in v3/v4
-- runs. Removing the contaminated symbols entirely is cheaper and more
-- reliable than per-strategy filters that have repeatedly failed to catch
-- the contamination class.
--
-- Scope: removes the symbol row plus all dependent rows in `quotes`,
-- `earnings`, `ovtlyr_signals`, and `ingestion_status` (none of which
-- carry an FK constraint, hence explicit DELETEs in dependency order).
-- Affects ~48 symbols out of ~4100 (~1.2% of the universe). Includes some
-- active mid-cap names (FUBO, ERX, APLS, CLSK, TGTX, etc.) — accepted as
-- collateral damage because the bar-level corruption invalidates any
-- backtest result they participate in.
--
-- Re-ingestion: the scheduled daily `updateAll()` iterates the
-- `ingestion_status` table, so once those rows are gone the symbols stay
-- gone. New bad-print symbols arriving via future ingest are detected by
-- `BadPrintIntegrityValidator` and surfaced through the integrity dashboard.

CREATE TEMPORARY TABLE bad_print_symbols AS
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
WHERE prev_close > 0
  AND next_close > 0
  AND close_price / prev_close      >= 5
  AND next_close  / close_price     <= 0.20;

-- Emit the full offender list to the Flyway migration log. The temporary
-- table is dropped at session end, so this is the only durable audit trail
-- of which symbols V11 removed.
DO $$
DECLARE
    offenders TEXT;
BEGIN
    SELECT string_agg(symbol, ', ' ORDER BY symbol) INTO offenders FROM bad_print_symbols;
    RAISE NOTICE 'V11 removing bad-print symbols (%): %', (SELECT COUNT(*) FROM bad_print_symbols), COALESCE(offenders, '<none>');
END $$;

-- Delete order: children first, parent (`symbols`) last. No FKs exist today,
-- so the order is currently cosmetic — kept consistent so a future FK from
-- `quotes` -> `symbols` (or similar) ON DELETE RESTRICT would not block this
-- migration.
DELETE FROM ovtlyr_signals   WHERE symbol IN (SELECT symbol FROM bad_print_symbols);
DELETE FROM earnings         WHERE symbol IN (SELECT symbol FROM bad_print_symbols);
DELETE FROM ingestion_status WHERE symbol IN (SELECT symbol FROM bad_print_symbols);
DELETE FROM quotes           WHERE symbol IN (SELECT symbol FROM bad_print_symbols);
DELETE FROM symbols          WHERE symbol IN (SELECT symbol FROM bad_print_symbols);

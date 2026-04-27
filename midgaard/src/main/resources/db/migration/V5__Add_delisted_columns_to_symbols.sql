-- V5: Add columns supporting delisted-ticker ingestion for survivorship-bias-free backtests.
--
-- delisted_at  - Last trading date (null for currently-listed symbols)
-- cik          - SEC Central Index Key, used to look up SIC codes via EDGAR for delisted issuers
--                whose EODHD fundamentals return "NA" sectors
--
-- Both columns nullable so the existing live-symbol rows backfill cleanly to NULL.

ALTER TABLE symbols
    ADD COLUMN delisted_at DATE,
    ADD COLUMN cik VARCHAR(20);

CREATE INDEX idx_symbols_delisted_at ON symbols(delisted_at) WHERE delisted_at IS NOT NULL;

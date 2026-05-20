-- V10: Storage for Ovtlyr signals — third-party buy/sell calls from ovtlyr.com,
-- ingested as reference data. Sparse by design: one row per (symbol, date) only
-- when ovtlyr emitted a BUY or SELL; days with no call have no row. The PK index
-- covers symbol-scoped lookups, so no separate symbol index is needed.

CREATE TABLE ovtlyr_signals (
    symbol       VARCHAR(50) NOT NULL,
    signal_date  DATE        NOT NULL,
    signal       VARCHAR(10) NOT NULL,
    PRIMARY KEY (symbol, signal_date),
    -- The read path maps `signal` to a Kotlin enum; enforce the domain at the DB
    -- boundary so a stray value can never break a whole symbol's lookup.
    CONSTRAINT ck_ovtlyr_signals_signal CHECK (signal IN ('BUY', 'SELL'))
);

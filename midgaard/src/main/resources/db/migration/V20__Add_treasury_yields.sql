-- V20: Add the treasury-yield reference series (the short rate the backtest engine credits idle
-- cash at — ADR 0016). `maturity` is the series key (e.g. 'US3M' for the 3-month T-bill that SGOV
-- tracks); `yield_pct` is the GROSS end-of-day yield in percent (the SGOV expense haircut is applied
-- once downstream in Udgaard, never stored here). Sparse-by-design like ovtlyr_signals: one row per
-- maturity per trading day.
CREATE TABLE treasury_yields (
    maturity    VARCHAR(20)   NOT NULL,
    yield_date  DATE          NOT NULL,
    yield_pct   NUMERIC(10,6) NOT NULL,
    PRIMARY KEY (maturity, yield_date)
);

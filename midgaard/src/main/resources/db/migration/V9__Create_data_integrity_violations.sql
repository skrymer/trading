-- V9: Storage for the DataIntegrityValidator framework. One row per
-- (validator, invariant) tuple per validation run. Truncate-and-replace on each
-- runAll() — no history, no run_id; latest snapshot only.
--
-- Counts + sample symbols carry the drill-down info; full affected-symbol list
-- is recoverable via the validator's underlying SQL (documented in its KDoc).

CREATE TABLE data_integrity_violations (
    id              BIGSERIAL    PRIMARY KEY,
    validator       VARCHAR(100) NOT NULL,
    invariant       VARCHAR(255) NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    description     TEXT         NOT NULL,
    count           INT          NOT NULL,
    sample_symbols  TEXT[]       NOT NULL DEFAULT '{}',
    detected_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_data_integrity_violations_validator ON data_integrity_violations(validator);

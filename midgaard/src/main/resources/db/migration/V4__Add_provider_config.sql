CREATE TABLE provider_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT         NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

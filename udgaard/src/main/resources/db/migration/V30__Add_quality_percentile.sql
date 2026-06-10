-- V30: Mirror of Midgaard's quality_percentile (ADR 0019 L2) onto Udgaard's stock_quotes, alongside
-- relative_strength_percentile (V27). Ingested from Midgaard per quote row; null until Midgaard's
-- operator-triggered quality pass has run and for any bar below the min-filings / min-peer / 2000-01-01
-- floors. The gross-profitability gate condition reads it fail-closed on null.
ALTER TABLE stock_quotes ADD COLUMN quality_percentile DECIMAL(19, 4);

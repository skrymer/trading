-- V25: Add semis/biotech/gold/oil sector ETFs + their Direxion 3x leveraged counterparts
-- to the Udgaard symbol registry. Needed for the Heimdall leveraged-sector-rotation candidate.
-- ERX already present (V2's original leveraged-ETF set); ON CONFLICT keeps this idempotent.
-- Udgaard's symbols table carries only (symbol, asset_type) — sector lives on the stocks table.
INSERT INTO symbols (symbol, asset_type) VALUES
    ('SOXX', 'ETF'),
    ('XBI',  'ETF'),
    ('GDX',  'ETF'),
    ('XOP',  'ETF'),
    ('ERX',  'LEVERAGED_ETF'),
    ('LABU', 'LEVERAGED_ETF'),
    ('NUGT', 'LEVERAGED_ETF'),
    ('GUSH', 'LEVERAGED_ETF'),
    ('EDC',  'LEVERAGED_ETF')
ON CONFLICT (symbol) DO NOTHING;

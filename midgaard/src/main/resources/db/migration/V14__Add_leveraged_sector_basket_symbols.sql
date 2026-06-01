-- V14: Add semis/biotech/gold/oil sector ETFs + their Direxion 3x leveraged counterparts
-- (ERX/EDC underlyings XLE/EEM already present). Needed for the Heimdall leveraged-sector-rotation
-- candidate. sector/sector_symbol left NULL to match existing sector ETFs (XLK/XLF/SPY).
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

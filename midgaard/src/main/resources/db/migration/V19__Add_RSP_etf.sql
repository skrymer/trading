-- V19: Add RSP (Invesco S&P 500 Equal-Weight ETF) for the RSP/SPY leadership-concentration ratio.
-- Equal-weight vs cap-weight relative strength is the cleanest direct measure of broad-vs-narrow
-- market leadership. RSP launched 2003-04-30, so the ratio only exists from 2003 on (covers the
-- 2005-2015 screen window + firewall Blocks B/C, but truncates Block A's 2000-2002 dot-com bear).
-- sector/sector_symbol left NULL to match the existing broad-market ETFs (SPY/QQQ/VOO).
INSERT INTO symbols (symbol, asset_type) VALUES
    ('RSP', 'ETF')
ON CONFLICT (symbol) DO NOTHING;

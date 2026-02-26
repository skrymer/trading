-- V3: Add sector_symbol column derived from AlphaVantage sector names.
-- Maps human-readable sector names to their corresponding SPDR sector ETF symbols.

ALTER TABLE symbols ADD COLUMN sector_symbol VARCHAR(10);

UPDATE symbols SET sector_symbol = 'XLK' WHERE sector = 'TECHNOLOGY';
UPDATE symbols SET sector_symbol = 'XLF' WHERE sector = 'FINANCIAL SERVICES';
UPDATE symbols SET sector_symbol = 'XLV' WHERE sector = 'HEALTHCARE';
UPDATE symbols SET sector_symbol = 'XLE' WHERE sector = 'ENERGY';
UPDATE symbols SET sector_symbol = 'XLI' WHERE sector = 'INDUSTRIALS';
UPDATE symbols SET sector_symbol = 'XLY' WHERE sector = 'CONSUMER CYCLICAL';
UPDATE symbols SET sector_symbol = 'XLP' WHERE sector = 'CONSUMER DEFENSIVE';
UPDATE symbols SET sector_symbol = 'XLC' WHERE sector = 'COMMUNICATION SERVICES';
UPDATE symbols SET sector_symbol = 'XLB' WHERE sector = 'BASIC MATERIALS';
UPDATE symbols SET sector_symbol = 'XLRE' WHERE sector = 'REAL ESTATE';
UPDATE symbols SET sector_symbol = 'XLU' WHERE sector = 'UTILITIES';

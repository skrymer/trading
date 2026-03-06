-- Add trigger_date to order_blocks to fix look-ahead bias.
-- The trigger_date is the date when the ROC crossing fires (4-15 bars after the origin candle).
-- Before this fix, conditions used startDate (origin candle date), allowing them to "see"
-- order blocks before they would have been discovered in live trading.

ALTER TABLE order_blocks ADD COLUMN trigger_date DATE;

-- Backfill: set trigger_date = start_date for existing records.
-- This is conservative; a full re-ingestion will compute correct trigger dates.
UPDATE order_blocks SET trigger_date = start_date;

ALTER TABLE order_blocks ALTER COLUMN trigger_date SET NOT NULL;

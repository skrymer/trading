ALTER TABLE cash_transactions ADD COLUMN converted_amount DECIMAL(15, 2);

-- For existing records, default converted_amount to amount (assumes same currency)
UPDATE cash_transactions SET converted_amount = amount;

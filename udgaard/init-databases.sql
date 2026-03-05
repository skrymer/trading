-- Create the Midgaard datastore database (Udgaard's 'trading' DB is created via POSTGRES_DB env var)
CREATE DATABASE datastore;
GRANT ALL PRIVILEGES ON DATABASE datastore TO trading;

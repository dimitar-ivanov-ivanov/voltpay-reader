create schema read;

CREATE SCHEMA IF NOT EXISTS partman;

CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA partman;

CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

SELECT * FROM pg_available_extensions WHERE name = 'pg_partman';

select * from read."transaction" t 

select * from read.idempotency i 

-- no records after failing 
-- no records after republishing  

truncate read.transaction;

truncate read.idempotency;
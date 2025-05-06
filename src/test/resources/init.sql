CREATE SCHEMA IF NOT EXISTS read;

CREATE TABLE IF NOT EXISTS read.transaction (
              id VARCHAR(30),
              amount NUMERIC(20, 6),
              status INTEGER,
              currency VARCHAR(3),
              cust_id BIGINT,
              type VARCHAR(3),
              created_at TIMESTAMP NOT NULL,
              updated_at TIMESTAMP NOT NULL,
              comment VARCHAR(300),
              version INTEGER,
              PRIMARY KEY (id, created_at)
              );

 CREATE TABLE IF NOT EXISTS read.idempotency (
     id VARCHAR(255) PRIMARY KEY,
     date DATE
 );
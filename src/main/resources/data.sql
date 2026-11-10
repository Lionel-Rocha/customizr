-- 1. Criação do Enum
CREATE TYPE role AS ENUM ('PLAYER', 'MASTER', 'SPECTATOR');
--
---- 2. Criação das tabelas
--CREATE TABLE users (
--    id uuid PRIMARY KEY,
--    name varchar(30) NOT NULL
--);
--CREATE TABLE source (
--    id uuid PRIMARY KEY,
--    name varchar(255) NOT NULL,
--    code varchar(10) NOT NULL
--);
--
--CREATE TABLE campaign (
--    id uuid PRIMARY KEY,
--    name varchar(255) NOT NULL,
--    master_id uuid NOT NULL,
--    code varchar(8) NOT NULL,
--    created_at timestamp(6) NOT NULL,
--    CONSTRAINT fk_campaign_master FOREIGN KEY (master_id) REFERENCES users (id)
--);

-- 3. Inserts (Atenção às aspas simples e ao preenchimento do role)
INSERT INTO users (user_id, name) VALUES (random_uuid(),'O Escriba');

INSERT INTO source (id, name, code) VALUES (random_uuid(), 'Player''s Handbook (2024)', 'XPHB');
-- Note o "Player''s" com duas aspas simples para escapar a aspa simples no SQL.
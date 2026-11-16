INSERT INTO users (user_id, name, password)
VALUES (RANDOM_UUID(), 'O Escriba', '$2a$10$Dyo3wtgUn8ZEIelFrPtSee4LLZs0YcvEmAf7icBDs4H6MwHnvW8BG');

INSERT INTO users (user_id, name, password)
VALUES (RANDOM_UUID(), 'O Artífice', '$2a$10$JzYPmAQbgwuR7J55AzK.hOrBkC80lBWR0ejwV9XbRMWLKDMsRHQPu');

INSERT INTO source (id, name, code)
VALUES (RANDOM_UUID(), 'Player''s Handbook (2024)', 'XPHB');



INSERT INTO campaign (id, name, master_id, code, created_at)
SELECT RANDOM_UUID(), 'Campanha de O Escriba', user_id, 'ESCRIBA1', CURRENT_TIMESTAMP
FROM users WHERE name = 'O Escriba';

INSERT INTO membership (id, campaign_id, user_id, role)
SELECT RANDOM_UUID(), c.id, u.user_id, 'PLAYER'
FROM campaign c, users u
WHERE c.name = 'Campanha de O Escriba' AND u.name = 'O Artífice';

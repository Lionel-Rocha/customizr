-- Corrige fichas salvas ANTES da correcao da troca de Inteligencia e Sabedoria (PostgreSQL).
--
-- O problema: o construtor de Attributes recebia (FOR, DES, CON, SAB, INT, CAR) e o
-- CharacterService passava (FOR, DES, CON, INT, SAB, CAR). Quem salvou INT 16 / SAB 10 ficou
-- gravado como INT 10 / SAB 16 (e a API devolvia trocado tambem).
--
-- QUANDO RODAR: uma vez, junto com o deploy da versao corrigida, ANTES de alguem salvar
-- atributos pela versao nova. Fichas salvas pela versao nova ja estao certas e seriam trocadas
-- de novo se o script rodasse depois delas.
--
-- O script se protege contra rodar duas vezes (anota em manual_migrations).
-- Pra ver antes o que vai mudar, rode a consulta comentada no fim.

CREATE TABLE IF NOT EXISTS manual_migrations (
    name       VARCHAR(200) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    changed INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM manual_migrations WHERE name = '20261117_swap_intelligence_wisdom') THEN
        RAISE NOTICE 'Ja aplicado antes; nada foi alterado.';
        RETURN;
    END IF;

    UPDATE character_sheets SET intelligence = wisdom, wisdom = intelligence WHERE intelligence <> wisdom;
    GET DIAGNOSTICS changed = ROW_COUNT;

    INSERT INTO manual_migrations (name) VALUES ('20261117_swap_intelligence_wisdom');
    RAISE NOTICE 'Fichas corrigidas (INT e SAB destrocados): %', changed;
END $$;

-- Conferir antes (mostra como esta gravado agora e como vai ficar):
-- SELECT c.name, s.intelligence AS int_gravado, s.wisdom AS sab_gravado,
--        s.wisdom AS int_correto, s.intelligence AS sab_correto
-- FROM character_sheets s JOIN characters c ON c.character_id = s.character_id
-- WHERE s.intelligence <> s.wisdom
-- ORDER BY c.name;

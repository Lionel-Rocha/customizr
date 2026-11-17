package top.customizr.system;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O UPDATE do script 20261117_swap_intelligence_wisdom.sql destroca INT e SAB gravados pela versao
 * antiga. (O bloco DO $$ e so PostgreSQL; aqui roda o UPDATE dele no H2, lido do proprio arquivo.)
 */
@SpringBootTest
@Transactional
class ManualMigrationTests {

    @Autowired private CharacterService characterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void swapScriptFixesSheetsSavedByTheOldCode() throws Exception {
        String master = "m-" + UUID.randomUUID();
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        Character mage = characterService.create(new CreateCharacterRequest(campaign.getId(), "Mage"), master);
        characterService.updateAttributes(mage.getCharacterId(), new UpdateAttributesRequest(8, 14, 12, 16, 10, 11), master);
        entityManager.flush();
        // simula o que a versao antiga gravava: INT e SAB trocados
        jdbc.update("UPDATE character_sheets SET intelligence = 10, wisdom = 16 WHERE character_id = ?", mage.getCharacterId());

        int changed = jdbc.update(updateStatementFromScript());
        entityManager.clear();

        assertTrue(changed >= 1);
        var sheet = characterService.getCharacterSheet(mage.getCharacterId(), master);
        assertEquals(16, sheet.attributes().intelligence());
        assertEquals(10, sheet.attributes().wisdom());
        assertEquals(8, sheet.attributes().strength(), "os outros atributos nao mudam");
    }

    private String updateStatementFromScript() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/manual-migrations/20261117_swap_intelligence_wisdom.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(UPDATE character_sheets[^;]+);").matcher(sql);
            assertTrue(m.find(), "o script tem o UPDATE");
            return m.group(1);
        }
    }
}

package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CatalogFeatRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.ContentImportService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pre-requisitos dos talentos do catalogo, conferidos pela entrada original do 5etools. */
@SpringBootTest
@Transactional
class FeatPrerequisitesTests {

    @Autowired private ContentImportService contentImportService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private CharacterService characterService;
    @Autowired private FeatDefinitionRepository featRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void playersNeedThePrerequisitesAndTheMasterCanOverride() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        contentImportService.importFeats(objectMapper.readTree("""
                {"feat": [{"name": "Grappler %s", "source": "XPHB", "category": "G",
                           "prerequisite": [{"level": 4, "ability": [{"str": 13, "dex": 13}]}], "entries": ["Strong grip."]}]}
                """.formatted(suffix)));
        UUID grappler = featRepository.findByNameContainingIgnoreCaseOrderByName("Grappler " + suffix).getFirst().getId();

        String master = "m-" + suffix, player = "p-" + suffix;
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "x"))));
        User p = userRepository.save(new User(player, "x"));
        membershipRepository.save(new Membership(campaign.getId(), p.getUserId(), Role.PLAYER));
        Character hero = characterService.create(new CreateCharacterRequest(campaign.getId(), "Hero"), player);
        characterService.updateAttributes(hero.getCharacterId(), new UpdateAttributesRequest(10, 10, 10, 10, 10, 10), player);
        DndClass fighter = dndClassRepository.save(new DndClass("Fighter-" + suffix, 10, 3, null));
        characterService.addClass(hero.getCharacterId(), new AddCharacterClassRequest(fighter.getClassId(), 1), player);

        ResponseStatusException denied = assertThrows(ResponseStatusException.class, () -> sheetService.addCatalogFeat(
                hero.getCharacterId(), new CatalogFeatRequest(grappler, 1, null), player));
        assertTrue(denied.getReason().contains("level 4") && denied.getReason().contains("STR 13 or DEX 13"), denied.getReason());

        // nivel 4 e DES 14: agora pode
        characterService.getCharacterSheet(hero.getCharacterId(), player);
        hero.getSheet().getClasses().getFirst().setLevel(4);
        characterService.updateAttributes(hero.getCharacterId(), new UpdateAttributesRequest(10, 14, 10, 10, 10, 10), player);
        assertEquals(1, sheetService.addCatalogFeat(hero.getCharacterId(), new CatalogFeatRequest(grappler, 4, null), player)
                .details().feats().size());

        // outro personagem sem os requisitos: o mestre libera mesmo assim
        Character rookie = characterService.create(new CreateCharacterRequest(campaign.getId(), "Rookie"), player);
        assertEquals(1, sheetService.addCatalogFeat(rookie.getCharacterId(), new CatalogFeatRequest(grappler, 1, null), master)
                .details().feats().size());
    }
}

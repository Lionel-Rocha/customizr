package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.CustomResourceRequest;
import top.customizr.system.dto.InspirationRequest;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.ResourceRequest;
import top.customizr.system.dto.RestRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Membership;
import top.customizr.system.model.ResourceRecharge;
import top.customizr.system.model.Role;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Source;
import top.customizr.system.model.Species;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recursos de classe com usos (regras de 2014 e 2024), recursos feitos a mao e Inspiracao Heroica. */
@SpringBootTest
@Transactional
class ClassResourcesTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SpeciesRepository speciesRepository;

    private String master;
    private String player;
    private Campaign campaign;
    private Source source;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User p = userRepository.save(new User(player, "p"));
        membershipRepository.save(new Membership(campaign.getId(), p.getUserId(), Role.PLAYER));
        // codigo proprio: as classes "Fighter", "Barbarian"... nao batem com as do banco
        source = sourceRepository.save(new Source("Resource tests", "RT" + UUID.randomUUID().toString().substring(0, 6)));
    }

    @Test
    void fighter2024RecoversOneSecondWindOnShortRestAndActionSurgeFully() {
        UUID hero = character("Fighter", "one", 4, 10);
        assertEquals(3, resource(sheet(hero), "fighter.second-wind").max(), "Retomar o Folego: 3 no nivel 4");
        assertEquals(1, resource(sheet(hero), "fighter.action-surge").max());

        spend(hero, "fighter.second-wind", 2);
        spend(hero, "fighter.action-surge", 1);
        assertThrows(ResponseStatusException.class, () -> spend(hero, "fighter.action-surge", 1), "sem uso sobrando");

        var shortRest = sheetService.rest(hero, new RestRequest(RestRequest.RestType.SHORT, 0), player);
        assertEquals(2, resource(shortRest.sheet(), "fighter.second-wind").remaining(), "so 1 volta no curto (2024)");
        assertEquals(1, resource(shortRest.sheet(), "fighter.action-surge").remaining(), "Surto de Acao volta inteiro");
        assertTrue(shortRest.recovered().containsAll(List.of("Second Wind +1", "Action Surge")), shortRest.recovered().toString());

        var longRest = sheetService.rest(hero, new RestRequest(RestRequest.RestType.LONG, 0), player);
        assertEquals(3, resource(longRest.sheet(), "fighter.second-wind").remaining());
    }

    @Test
    void classicRulesAndPoolsFollowTheirOwnEdition() {
        UUID barbarian = character("Barbarian", "classic", 3, 12);
        assertEquals(ResourceRecharge.LONG_REST, resource(sheet(barbarian), "barbarian.rage").recharge(), "Furia de 2014: so no longo");
        spend(barbarian, "barbarian.rage", 1);
        var shortRest = sheetService.rest(barbarian, new RestRequest(RestRequest.RestType.SHORT, 0), player);
        assertEquals(2, resource(shortRest.sheet(), "barbarian.rage").remaining());

        UUID paladin = character("Paladin", "classic", 5, 10);
        var pool = resource(sheet(paladin), "paladin.lay-on-hands");
        assertEquals(25, pool.max(), "Cura pelas Maos = 5 x nivel");
        assertEquals("HP", pool.unit());
        spend(paladin, "paladin.lay-on-hands", 7);
        var recovered = sheetService.recoverResource(paladin, new ResourceRequest("paladin.lay-on-hands", 2), player);
        assertEquals(20, resource(recovered, "paladin.lay-on-hands").remaining());
        assertEquals(1, resource(recovered, "paladin.channel-divinity").max(), "Canalizar Divindade de 2014: 1");

        UUID bard = character("Bard", "one", 4, 8);
        characterService.updateAttributes(bard, new UpdateAttributesRequest(8, 14, 12, 10, 10, 16), player);
        var inspiration = resource(sheet(bard), "bard.bardic-inspiration");
        assertEquals(3, inspiration.max(), "CAR +3");
        assertEquals(ResourceRecharge.LONG_REST, inspiration.recharge(), "Fonte de Inspiracao so no nivel 5");
    }

    @Test
    void customResourcesKeepTheirOwnRecharge() {
        UUID hero = character("Fighter", "one", 1, 10);
        var created = sheetService.addCustomResource(hero, new CustomResourceRequest("Wand of Magic Missiles", 7,
                ResourceRecharge.MANUAL, "1d6+1 charges at dawn"), player);
        String wand = created.details().resources().stream().filter(r -> r.custom()).findFirst().orElseThrow().id();

        spend(hero, wand, 3);
        var afterRest = sheetService.rest(hero, new RestRequest(RestRequest.RestType.LONG, 0), player);
        assertEquals(4, resource(afterRest.sheet(), wand).remaining(), "manual nao volta no descanso");
        assertEquals(7, resource(sheetService.recoverResource(hero, new ResourceRequest(wand, null), player), wand).remaining(),
                "devolver sem quantidade = tudo");

        assertThrows(ResponseStatusException.class, () -> sheetService.addCustomResource(hero,
                new CustomResourceRequest("Broken", 0, null, null), player));
        var removed = sheetService.removeCustomResource(hero, UUID.fromString(wand), player);
        assertTrue(removed.details().resources().stream().noneMatch(r -> r.custom()));
    }

    @Test
    void heroicInspirationIsGivenByTheMasterAndSpentOnAReroll() {
        UUID hero = character("Fighter", "one", 1, 10);
        assertThrows(AccessDeniedException.class, () -> sheetService.changeInspiration(hero, new InspirationRequest(true), player),
                "o jogador nao se da Inspiracao");
        assertTrue(sheetService.changeInspiration(hero, new InspirationRequest(true), master).details().heroicInspiration());

        var reroll = sheetService.roll(hero, new SheetRollRequest(SheetRollRequest.RollKind.SKILL, Skill.ATHLETICS,
                null, null, false, true), player);
        assertTrue(reroll.notes().contains("Heroic Inspiration: reroll"));
        assertFalse(sheet(hero).details().heroicInspiration());
        assertThrows(ResponseStatusException.class, () -> sheetService.roll(hero, new SheetRollRequest(
                SheetRollRequest.RollKind.SKILL, Skill.ATHLETICS, null, null, false, true), player), "ja gastou");

        // Humano de 2024 (Engenhoso) ganha no descanso longo
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        Species human = speciesRepository.findAll().stream()
                .filter(s -> s.getName().equals("Human") && xphb.getId().equals(s.getSource().getId())).findFirst()
                .orElseGet(() -> speciesRepository.save(new Species("Human", xphb)));
        sheetService.chooseOrigin(hero, new OriginRequest(human.getId(), null), player);
        var rest = sheetService.rest(hero, new RestRequest(RestRequest.RestType.LONG, 0), player);
        assertTrue(rest.sheet().details().heroicInspiration());
        assertTrue(rest.recovered().contains("Heroic Inspiration"));
    }

    // ------------------------------------------------------------------ apoio

    private UUID character(String className, String edition, int level, int hitDie) {
        DndClass dnd = dndClassRepository.findByClassNameAndSourceCode(className, source.getCode())
                .orElseGet(() -> dndClassRepository.save(new DndClass(className, source.getCode(), 1, hitDie, 3, source,
                        edition, null, null, null, null, null, null, null)));
        UUID id = characterService.create(new CreateCharacterRequest(campaign.getId(), className + " hero"), player).getCharacterId();
        characterService.updateAttributes(id, new UpdateAttributesRequest(16, 12, 14, 10, 10, 10), player);
        characterService.addClass(id, new AddCharacterClassRequest(dnd.getClassId(), level), player);
        characterService.updateCombatStats(id, new UpdateCombatStatsRequest(10 * level, null, null, 15, 30), player);
        return id;
    }

    private CharacterSheetResponse sheet(UUID id) {
        return characterService.getCharacterSheet(id, player);
    }

    private void spend(UUID id, String resource, int amount) {
        sheetService.spendResource(id, new ResourceRequest(resource, amount), player);
    }

    private static CharacterSheetResponse.ResourceInfo resource(CharacterSheetResponse sheet, String id) {
        return sheet.details().resources().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no resource " + id + " in " + sheet.details().resources()));
    }
}

package top.customizr.system;

import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.AttackRollRequest;
import top.customizr.system.dto.CatalogFeatRequest;
import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.ExperienceRequest;
import top.customizr.system.dto.OriginRequest;
import top.customizr.system.dto.PortraitRequest;
import top.customizr.system.dto.ProficienciesRequest;
import top.customizr.system.dto.RestRequest;
import top.customizr.system.dto.SheetRollRequest;
import top.customizr.system.dto.SpendSpellSlotRequest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.ChatMessageResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.CharacterStatus;
import top.customizr.system.model.Condition;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.FeatDefinition;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.Sheet;
import top.customizr.system.model.Skill;
import top.customizr.system.model.Source;
import top.customizr.system.model.Species;
import top.customizr.system.model.SpellSlotPool;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.ChatService;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ficha completa: proficiencias, condicoes, testes contra a morte, espacos, descansos, ataques, XP. */
@SpringBootTest
@Transactional
class FullSheetTests {

    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private ChatService chatService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private FeatDefinitionRepository featRepository;

    private String master;
    private String player;
    private Campaign campaign;
    private UUID aria;

    @BeforeEach
    void setUp() {
        master = "master-" + UUID.randomUUID();
        player = "player-" + UUID.randomUUID();
        campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        User p = userRepository.save(new User(player, "p"));
        membershipRepository.save(new Membership(campaign.getId(), p.getUserId(), Role.PLAYER));
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), player).getCharacterId();
        // Ladina 3 / Maga 2 (conjuradora completa 2 = 3 espacos de 1o)
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        DndClass rogue = dndClassRepository.save(new DndClass("Rogue-" + UUID.randomUUID(), 8, 3, null));
        DndClass wizard = dndClassRepository.save(new DndClass("Wizard-" + UUID.randomUUID(), "XPHB", 1, 6, 3, xphb,
                null, null, null, "int", "full", null, null, null));
        characterService.updateAttributes(aria, new UpdateAttributesRequest(8, 16, 14, 14, 12, 10), player);
        characterService.addClass(aria, new AddCharacterClassRequest(rogue.getClassId(), 3), player);
        characterService.addClass(aria, new AddCharacterClassRequest(wizard.getClassId(), 2), player);
        characterService.updateCombatStats(aria, new UpdateCombatStatsRequest(30, null, null, 14, 30), player);
    }

    @Test
    void proficienciesAndExpertiseChangeTheBonuses() {
        var details = sheetService.updateProficiencies(aria, new ProficienciesRequest(
                Set.of(Skill.STEALTH, Skill.PERCEPTION), Set.of(Skill.STEALTH),
                Set.of(AbilityScore.DEXTERITY, AbilityScore.INTELLIGENCE)), player).details();

        int prof = 3; // nivel 5
        assertEquals(3 + 2 * prof, skill(details, Skill.STEALTH).bonus(), "DES +3 com especializacao");
        assertEquals(1 + prof, skill(details, Skill.PERCEPTION).bonus());
        assertEquals(3 + prof, details.savingThrows().stream().filter(s -> s.ability() == AbilityScore.DEXTERITY)
                .findFirst().orElseThrow().bonus());
        assertEquals(10 + 1 + prof, characterService.getCharacterSheet(aria, player).combat().passivePerception());

        assertThrows(ResponseStatusException.class, () -> sheetService.updateProficiencies(aria,
                new ProficienciesRequest(Set.of(), Set.of(Skill.ARCANA), Set.of()), player),
                "especializacao sem proficiencia");
    }

    @Test
    void conditionsAndExhaustionChangeTheRolls() {
        sheetService.updateConditions(aria, new ConditionsRequest(Set.of(Condition.POISONED), 2), player);

        SheetRollResponse check = sheetService.roll(aria,
                new SheetRollRequest(SheetRollRequest.RollKind.SKILL, Skill.STEALTH, null, RollMode.NORMAL, false), player);
        assertTrue(check.roll().expression().startsWith("2d20kl1"), "envenenado = desvantagem");
        assertTrue(check.notes().contains("Exaustão 2: -4"));

        SheetRollResponse both = sheetService.roll(aria,
                new SheetRollRequest(SheetRollRequest.RollKind.SKILL, Skill.STEALTH, null, RollMode.ADVANTAGE, false), player);
        assertTrue(both.roll().expression().startsWith("1d20"), "vantagem e desvantagem se anulam");
        assertEquals(20, characterService.getCharacterSheet(aria, player).details().currentSpeedFt(), "30 - 2x5");

        sheetService.updateConditions(aria, new ConditionsRequest(Set.of(Condition.PARALYZED), 0), player);
        assertEquals("AUTOMATIC_FAILURE", sheetService.roll(aria,
                new SheetRollRequest(SheetRollRequest.RollKind.SAVE, null, AbilityScore.DEXTERITY, null, false), player).outcome());

        assertThrows(ResponseStatusException.class, () -> sheetService.updateConditions(aria,
                new ConditionsRequest(null, 7), player));
    }

    @Test
    void deathSavesFollowTheRules() {
        characterService.changeHitPoints(aria, new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 99), player);
        Sheet sheet = characterRepository.findById(aria).orElseThrow().getSheet();

        assertEquals(Sheet.DeathSaveOutcome.FAILURE, sheet.deathSave(5));
        assertEquals(Sheet.DeathSaveOutcome.SUCCESS, sheet.deathSave(10));
        assertEquals(Sheet.DeathSaveOutcome.DEAD, sheet.deathSave(1), "1 natural = duas falhas");
        assertEquals(CharacterStatus.DEAD, sheet.getCharacter().getStatus());

        sheet.longRest();
        assertEquals(CharacterStatus.ALIVE, sheet.getCharacter().getStatus());
        characterService.changeHitPoints(aria, new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 99), player);
        assertEquals(Sheet.DeathSaveOutcome.REVIVED, sheet.deathSave(20));
        assertEquals(1, sheet.getHpCurrent());
        assertThrows(IllegalStateException.class, () -> sheet.deathSave(12), "com PV nao rola");
    }

    @Test
    void spellSlotsHitDiceAndRests() {
        var slots = characterService.getCharacterSheet(aria, player).details().spellSlots();
        assertEquals(3, slots.getFirst().total(), "maga 2: tres espacos de 1o nivel");
        for (int i = 0; i < 3; i++) {
            sheetService.spendSpellSlot(aria, new SpendSpellSlotRequest(SpellSlotPool.STANDARD, 1), player);
        }
        assertThrows(ResponseStatusException.class,
                () -> sheetService.spendSpellSlot(aria, new SpendSpellSlotRequest(SpellSlotPool.STANDARD, 1), player));
        assertThrows(ResponseStatusException.class,
                () -> sheetService.spendSpellSlot(aria, new SpendSpellSlotRequest(SpellSlotPool.STANDARD, 2), player));

        characterService.changeHitPoints(aria, new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 20), player);
        Sheet sheet = characterRepository.findById(aria).orElseThrow().getSheet();
        Sheet.ShortRestResult rest = sheet.shortRest(2, sides -> sides); // rola o maximo
        assertEquals(java.util.List.of("1d8[8]+2", "1d8[8]+2"), rest.rolls(), "dados de ladina (d8) primeiro, + CON");
        assertEquals(20, rest.healed());
        assertEquals(3, sheet.getHitDiceRemaining());

        var afterLong = sheetService.rest(aria, new RestRequest(RestRequest.RestType.LONG, 0), player).sheet();
        assertEquals(30, afterLong.combat().hpCurrent());
        assertEquals(0, afterLong.details().spellSlots().getFirst().spent());
        assertEquals(5, afterLong.details().hitDiceRemaining(), "recupera metade (2) dos 5");
    }

    @Test
    void attacksRollToHitAndDamageAndCanGoToChat() {
        UUID attackId = sheetService.addAttack(aria, new AttackRequest("Rapier", 6, "1d8+3", "piercing", null), player)
                .details().attacks().getFirst().id();

        SheetRollResponse attack = sheetService.rollAttack(aria, attackId, new AttackRollRequest(null, 1, true), player);
        assertTrue(attack.roll().expression().startsWith("1d20+6"));
        assertNotNull(attack.chatMessageId());
        if (!attack.roll().isCriticalFail()) {
            assertNotNull(attack.damage());
            assertTrue(attack.hit());
        }
        ChatMessageResponse card = chatService.history(campaign.getId(), null, null, master).getLast();
        assertEquals("Aria", card.speakerName());
        assertTrue(card.content().startsWith("Aria - Rapier (perfurante): 1d20["), card.content());

        assertThrows(ResponseStatusException.class, () -> sheetService.addAttack(aria,
                new AttackRequest("Bad", 1, "banana", null, null), player));
        assertEquals(0, sheetService.removeAttack(aria, attackId, player).details().attacks().size());
    }

    @Test
    void originFeatsExperienceAndPortrait() {
        Source xphb = sourceRepository.getSourceByCode("XPHB").orElseThrow();
        Species elf = speciesRepository.save(new Species("Elf-" + UUID.randomUUID(), xphb));
        FeatDefinition alert = featRepository.save(new FeatDefinition("Alert-" + UUID.randomUUID(), xphb));

        var sheet = sheetService.chooseOrigin(aria, new OriginRequest(elf.getId(), null), player);
        assertEquals(elf.getName(), sheet.details().species().name());
        sheet = sheetService.addCatalogFeat(aria, new CatalogFeatRequest(alert.getId(), 1, null), player);
        assertEquals(alert.getName(), sheet.details().feats().getFirst().name());
        assertThrows(ResponseStatusException.class, () -> sheetService.addCatalogFeat(aria,
                new CatalogFeatRequest(alert.getId(), 4, null), player), "talento nao repetivel");

        assertThrows(AccessDeniedException.class, () -> sheetService.gainExperience(aria,
                new ExperienceRequest(1000), player), "jogador nao se da XP");
        var withXp = sheetService.gainExperience(aria, new ExperienceRequest(6600), master).details();
        assertEquals(5, withXp.levelByExperience());
        assertEquals(14000 - 6600, withXp.experienceToNextLevel());

        assertEquals("/image/abc", sheetService.changePortrait(aria, new PortraitRequest("/image/abc"), player)
                .details().portraitUrl());
        assertNull(sheetService.changePortrait(aria, new PortraitRequest(" "), player).details().portraitUrl());
    }

    private static CharacterSheetResponse.SkillInfo skill(CharacterSheetResponse.DetailsInfo details, Skill skill) {
        return details.skills().stream().filter(s -> s.skill() == skill).findFirst().orElseThrow();
    }
}

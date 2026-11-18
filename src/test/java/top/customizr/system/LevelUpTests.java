package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddCharacterClassRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.LevelUpPreviewResponse;
import top.customizr.system.dto.LevelUpRequest;
import top.customizr.system.dto.LevelUpResponse;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.FeatDefinition;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.LevelUpService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Assistente de subir de nivel: PV, proficiencia, Aumento de Valor de Atributo, subclasse, multiclasse. */
@SpringBootTest
@Transactional
class LevelUpTests {

    @Autowired private CharacterService characterService;
    @Autowired private LevelUpService levelUp;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private DndClassRepository dndClassRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private FeatDefinitionRepository featRepository;

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
        source = sourceRepository.save(new Source("Level up tests", "LU" + UUID.randomUUID().toString().substring(0, 6)));
    }

    @Test
    void abilityScoreImprovementRaisesConstitutionAndHitPointsRetroactively() {
        DndClass fighter = dndClass("Fighter", 10, null, null);
        UUID hero = character(fighter, 3);   // CON 14, PV 30

        LevelUpPreviewResponse plan = levelUp.preview(hero, null, player);
        assertEquals(4, plan.classLevel());
        assertTrue(plan.abilityScoreImprovement(), "Guerreiro 4: tabela padrao (classe sem lista de habilidades)");
        assertEquals(10 / 2 + 1 + 2, plan.averageHp());
        assertEquals(2, plan.proficiencyAfter());
        assertTrue(plan.resources().stream().anyMatch(r -> r.id().equals("fighter.second-wind") && r.before() == 2 && r.after() == 3));

        LevelUpResponse res = levelUp.apply(hero, new LevelUpRequest(null, LevelUpRequest.HitPointMethod.AVERAGE, null, null,
                Map.of(AbilityScore.CONSTITUTION, 2), null, false), player);
        assertEquals(3, res.retroactiveHp(), "CON 14 -> 16: +1 PV por nivel anterior");
        assertEquals(6 + 3 + 3, res.hpGained(), "media 6 + CON nova 3 + retroativo 3");
        assertEquals(42, res.sheet().combat().hpMax());
        assertEquals(42, res.sheet().combat().hpCurrent());
        assertEquals(16, res.sheet().attributes().constitution());
        assertEquals(4, res.sheet().combat().totalLevel());

        // nivel 5 nao da aumento de atributo; e a proficiencia sobe
        LevelUpPreviewResponse five = levelUp.preview(hero, null, player);
        assertFalse(five.abilityScoreImprovement());
        assertEquals(3, five.proficiencyAfter());
        assertThrows(ResponseStatusException.class, () -> levelUp.apply(hero, new LevelUpRequest(null, null, null, null,
                Map.of(AbilityScore.STRENGTH, 2), null, false), player), "nivel sem aumento de atributo");
    }

    @Test
    void featInsteadOfAbilityScoresAndTheRulesOfTheImprovement() {
        DndClass fighter = dndClass("Fighter", 10, null, null);
        UUID hero = character(fighter, 3);
        FeatDefinition tough = featRepository.save(new FeatDefinition("Tough-" + UUID.randomUUID(), source));

        assertThrows(ResponseStatusException.class, () -> levelUp.apply(hero, new LevelUpRequest(null, null, null, null,
                Map.of(AbilityScore.STRENGTH, 1), null, false), player), "+1 sozinho nao e aumento valido");
        assertThrows(ResponseStatusException.class, () -> levelUp.apply(hero, new LevelUpRequest(null, null, null, null,
                Map.of(AbilityScore.STRENGTH, 2), tough.getId(), false), player), "talento + 2 pontos");

        LevelUpResponse res = levelUp.apply(hero, new LevelUpRequest(null, LevelUpRequest.HitPointMethod.MANUAL, 7, null,
                Map.of(AbilityScore.STRENGTH, 1), tough.getId(), false), player);
        assertEquals(tough.getName(), res.featName());
        assertEquals(17, res.sheet().attributes().strength(), "meio-talento +1");
        assertEquals(7 + 2, res.hpGained(), "dado da mesa + CON");
        assertTrue(res.sheet().details().feats().stream().anyMatch(f -> f.name().equals(tough.getName())));
    }

    @Test
    void subclassIsChosenOnItsLevel() {
        DndClass fighter = dndClass("Fighter", 10, null, null);
        Subclass champion = fighter.addSubclass("Champion", 3, source);
        dndClassRepository.save(fighter);
        UUID hero = character(fighter, 2);

        LevelUpPreviewResponse plan = levelUp.preview(hero, null, player);
        assertTrue(plan.subclassRequired());
        assertEquals("Champion", plan.subclasses().getFirst().name());
        assertThrows(ResponseStatusException.class, () -> levelUp.apply(hero, new LevelUpRequest(null, null, null, null,
                null, null, false), player), "falta a subclasse");

        LevelUpResponse res = levelUp.apply(hero, new LevelUpRequest(null, null, null, champion.getSubclassId(), null, null, false), player);
        assertEquals("Champion", res.subclassName());
        assertEquals("Champion", res.sheet().classes().getFirst().subclass().name());
        assertFalse(levelUp.preview(hero, null, player).subclassRequired(), "ja escolheu");
    }

    @Test
    void multiclassAndFirstLevel() {
        DndClass fighter = dndClass("Fighter", 10, null, null);
        DndClass wizard = dndClass("Wizard", 6, "full",
                Map.of("class", Map.of("multiclassing", Map.of("requirements", Map.of("int", 13)))));
        UUID hero = character(fighter, 2);   // INT 10

        assertThrows(ResponseStatusException.class, () -> levelUp.preview(hero, UUID.randomUUID(), player));
        LevelUpPreviewResponse plan = levelUp.preview(hero, wizard.getClassId(), player);
        assertTrue(plan.newClass());
        assertEquals(1, plan.classLevel());
        assertNull(plan.fixedHp(), "multiclasse nao ganha o dado cheio");
        assertTrue(plan.warnings().stream().anyMatch(w -> w.startsWith("Multiclassing with Wizard needs INT 13")), plan.warnings().toString());
        assertTrue(plan.spellSlots().stream().anyMatch(s -> s.pool().equals("STANDARD") && s.level() == 1 && s.before() == 0 && s.after() == 2));

        LevelUpResponse res = levelUp.apply(hero, new LevelUpRequest(wizard.getClassId(), LevelUpRequest.HitPointMethod.ROLL,
                null, null, null, null, false), player);
        assertNotNull(res.hpRoll());
        assertEquals(2, res.sheet().classes().size());
        assertThrows(ResponseStatusException.class, () -> levelUp.preview(hero, null, player), "duas classes: diga qual sobe");

        // personagem sem classe: o primeiro nivel e o dado cheio + CON
        UUID fresh = characterService.create(new CreateCharacterRequest(campaign.getId(), "Fresh"), player).getCharacterId();
        characterService.updateAttributes(fresh, new UpdateAttributesRequest(10, 10, 16, 10, 10, 10), player);
        assertEquals(13, levelUp.preview(fresh, fighter.getClassId(), player).fixedHp());
        assertEquals(13, levelUp.apply(fresh, new LevelUpRequest(fighter.getClassId(), null, null, null, null, null, false), player)
                .sheet().combat().hpMax());

        String stranger = "stranger-" + UUID.randomUUID();
        userRepository.save(new User(stranger, "p"));
        assertThrows(AccessDeniedException.class, () -> levelUp.preview(hero, null, stranger));
        assertEquals(4, levelUp.apply(hero, new LevelUpRequest(fighter.getClassId(), null, null, null, null, null, false), master)
                .sheet().combat().totalLevel(), "o mestre tambem sobe o nivel");
    }

    // ------------------------------------------------------------------ apoio

    private DndClass dndClass(String name, int hitDie, String casterProgression, Map<String, Object> metadata) {
        return dndClassRepository.save(new DndClass(name, source.getCode(), 1, hitDie, 3, source, "one", null, null,
                casterProgression == null ? null : "int", casterProgression, null, null, metadata));
    }

    private UUID character(DndClass dndClass, int level) {
        UUID id = characterService.create(new CreateCharacterRequest(campaign.getId(), "Hero"), player).getCharacterId();
        characterService.updateAttributes(id, new UpdateAttributesRequest(16, 12, 14, 10, 10, 10), player);
        characterService.addClass(id, new AddCharacterClassRequest(dndClass.getClassId(), level), player);
        characterService.updateCombatStats(id, new UpdateCombatStatsRequest(30, null, null, 16, 30), player);
        return id;
    }
}

package top.customizr.system;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.AttackRequest;
import top.customizr.system.dto.ConditionsRequest;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.HitPointChangeRequest;
import top.customizr.system.dto.SceneRequest;
import top.customizr.system.dto.TokenAttackOption;
import top.customizr.system.dto.TokenAttackRequest;
import top.customizr.system.dto.TokenAttackResponse;
import top.customizr.system.dto.TokenRequest;
import top.customizr.system.dto.TokenResponse;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.Condition;
import top.customizr.system.model.GridType;
import top.customizr.system.model.Membership;
import top.customizr.system.model.Role;
import top.customizr.system.model.Source;
import top.customizr.system.model.User;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.MembershipRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.UserRepository;
import top.customizr.system.service.CharacterService;
import top.customizr.system.service.CharacterSheetService;
import top.customizr.system.service.LiveCombatService;
import top.customizr.system.service.LiveTableService;
import top.customizr.system.service.MonsterService;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combate na mesa: ataques de personagem e de monstro entre tokens, dano/cura e condicoes. */
@SpringBootTest
@Transactional
class LiveCombatTests {

    @Autowired private LiveTableService live;
    @Autowired private LiveCombatService combat;
    @Autowired private CharacterService characterService;
    @Autowired private CharacterSheetService sheetService;
    @Autowired private MonsterService monsterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SourceRepository sourceRepository;

    private String master;
    private String ana;
    private String bia;
    private Character aria;
    private UUID sceneId;

    @BeforeEach
    void setUp() throws Exception {
        master = "m-" + UUID.randomUUID();
        ana = "ana-" + UUID.randomUUID();
        bia = "bia-" + UUID.randomUUID();
        Campaign campaign = campaignRepository.save(new Campaign("C", userRepository.save(new User(master, "p"))));
        for (String name : List.of(ana, bia)) {
            User u = userRepository.save(new User(name, "p"));
            membershipRepository.save(new Membership(campaign.getId(), u.getUserId(), Role.PLAYER));
        }
        aria = characterService.create(new CreateCharacterRequest(campaign.getId(), "Aria"), ana);
        characterService.updateCombatStats(aria.getCharacterId(), new UpdateCombatStatsRequest(20, 20, 0, 14, 30), ana);
        if (sourceRepository.getSourceByCode("XMM").isEmpty()) {
            sourceRepository.save(new Source("Monster Manual (2024)", "XMM"));
        }
        try (InputStream in = getClass().getResourceAsStream("/importer/bestiary-srd52-amostra.json")) {
            monsterService.importBestiary(objectMapper.readTree(in.readAllBytes()));
        }
        sceneId = live.createScene(campaign.getId(), new SceneRequest("Cave", null, GridType.SQUARE, 70, 5, null, null), master).id();
    }

    @Test
    void monsterAttacksAreReadFromTheStatBlock() {
        TokenResponse dragon = monsterToken("red dragon", false);
        List<TokenAttackOption> attacks = combat.attacks(dragon.id(), master);
        TokenAttackOption rend = attacks.stream().filter(a -> a.name().equals("Rend")).findFirst().orElseThrow();
        assertEquals("ATTACK", rend.kind());
        assertEquals(14, rend.attackBonus());
        assertEquals("1d10+8+2d4", rend.damage(), "o dano de fogo extra entra junto");
        assertEquals("slashing", rend.damageType());
        TokenAttackOption breath = attacks.stream().filter(a -> a.name().startsWith("Fire Breath")).findFirst().orElseThrow();
        assertEquals("SAVE", breath.kind());
        assertEquals(21, breath.saveDc());
        assertEquals("DEX", breath.saveAbility());
        assertEquals("17d6", breath.damage());
        assertTrue(attacks.stream().noneMatch(a -> a.name().equals("Multiattack")), "multiataque nao e um ataque");

        TokenAttackOption scimitar = combat.attacks(monsterToken("goblin", false).id(), master).getFirst();
        assertEquals("1d6+2", scimitar.damage(), "o dano que so vale com vantagem fica de fora");
    }

    @Test
    void aMonsterAttackHitsOrMissesByTheTargetArmorClassAndTakesTheHitPoints() {
        TokenResponse goblin = monsterToken("goblin", false);
        TokenResponse hero = live.addToken(sceneId, token(null, aria.getCharacterId(), false), master);
        String scimitar = combat.attacks(goblin.id(), master).getFirst().id();

        boolean sawHit = false;
        for (int i = 0; i < 40 && !sawHit; i++) {
            characterService.updateCombatStats(aria.getCharacterId(), new UpdateCombatStatsRequest(20, 20, 0, 14, 30), ana);
            TokenAttackResponse result = combat.attack(goblin.id(), new TokenAttackRequest(scimitar, hero.id(), null, null), master);
            assertNotNull(result.hit(), "a CA da ficha e conhecida");
            assertNotNull(result.chatMessageId(), "o ataque vai pro chat");
            int hp = characterRepository.findById(aria.getCharacterId()).orElseThrow().getSheet().getHpCurrent();
            if (result.hit()) {
                sawHit = true;
                assertEquals(20 - result.damageApplied(), hp);
            } else {
                assertEquals(20, hp);
                assertNull(result.damageApplied());
            }
        }
        assertTrue(sawHit, "em 40 ataques +4 contra CA 14 algum acerta");

        combat.updateConditions(hero.id(), new ConditionsRequest(Set.of(Condition.UNCONSCIOUS), null), master);
        TokenAttackResponse onSleeper = combat.attack(goblin.id(), new TokenAttackRequest(scimitar, hero.id(), null, false), master);
        assertTrue(onSleeper.notes().contains("Advantage: target unconscious"));
        assertTrue(onSleeper.attackRoll().expression().startsWith("2d20kh1"));
    }

    @Test
    void thePlayerAttacksWithTheirOwnTokenAndMonsterDefensesCount() {
        UUID fireBolt = sheetService.addAttack(aria.getCharacterId(), new AttackRequest("Fire Bolt", 30, "1d10", "fire", null), ana)
                .details().attacks().getFirst().id();
        TokenResponse hero = live.addToken(sceneId, token(null, aria.getCharacterId(), false), master);
        TokenResponse dragon = monsterToken("red dragon", false);
        TokenResponse zombie = monsterToken("zombie", false);

        assertEquals(1, combat.attacks(hero.id(), ana).size());
        assertThrows(AccessDeniedException.class, () -> combat.attacks(hero.id(), bia), "token de outro jogador");
        assertThrows(AccessDeniedException.class, () -> combat.attacks(dragon.id(), ana), "monstro e do mestre");

        TokenAttackResponse onDragon = attackUntilHit(hero.id(), fireBolt.toString(), dragon.id());
        assertEquals(0, onDragon.damageApplied(), "dragao vermelho e imune a fogo");
        assertEquals(256, live.readScene(sceneId, master).tokens().stream()
                .filter(t -> t.id().equals(dragon.id())).findFirst().orElseThrow().hpCurrent());

        TokenAttackResponse onZombie = attackUntilHit(hero.id(), fireBolt.toString(), zombie.id());
        int zombieHp = live.readScene(sceneId, master).tokens().stream()
                .filter(t -> t.id().equals(zombie.id())).findFirst().orElseThrow().hpCurrent();
        assertEquals(Math.max(0, 15 - onZombie.damageApplied()), zombieHp);
    }

    @Test
    void hitPointsAndConditionsOnTokens() {
        TokenResponse goblin = monsterToken("goblin", false);
        TokenResponse hero = live.addToken(sceneId, token(null, aria.getCharacterId(), false), master);

        TokenResponse hurt = combat.changeHitPoints(goblin.id(), new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 3), master);
        assertEquals(7, hurt.hpCurrent());
        assertEquals(10, combat.changeHitPoints(goblin.id(), new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.HEALING, 50), master).hpCurrent());
        assertThrows(AccessDeniedException.class, () -> combat.changeHitPoints(goblin.id(),
                new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 3), ana));

        TokenResponse mine = combat.changeHitPoints(hero.id(), new HitPointChangeRequest(HitPointChangeRequest.HitPointChangeType.DAMAGE, 5), ana);
        assertEquals(15, mine.hpCurrent(), "token de personagem mexe na ficha");
        assertEquals(15, characterRepository.findById(aria.getCharacterId()).orElseThrow().getSheet().getHpCurrent());

        combat.updateConditions(goblin.id(), new ConditionsRequest(Set.of(Condition.PRONE), null), master);
        combat.updateConditions(hero.id(), new ConditionsRequest(Set.of(Condition.POISONED), null), ana);
        var seenByBia = live.readScene(sceneId, bia).tokens();
        TokenResponse goblinView = seenByBia.stream().filter(t -> t.id().equals(goblin.id())).findFirst().orElseThrow();
        TokenResponse heroView = seenByBia.stream().filter(t -> t.id().equals(hero.id())).findFirst().orElseThrow();
        assertEquals(Set.of(Condition.PRONE), goblinView.conditions(), "condicoes aparecem no mapa pra todos");
        assertNull(goblinView.armorClass(), "jogador nao ve a CA do monstro");
        assertEquals(Set.of(Condition.POISONED), heroView.conditions(), "condicao do token vai pra ficha");
        assertEquals(14, heroView.armorClass());
    }

    @Test
    void aPlayerCannotTargetAHiddenToken() {
        UUID sword = sheetService.addAttack(aria.getCharacterId(), new AttackRequest("Sword", 5, "1d8+3", "slashing", null), ana)
                .details().attacks().getFirst().id();
        TokenResponse hero = live.addToken(sceneId, token(null, aria.getCharacterId(), false), master);
        TokenResponse ambush = monsterToken("bandit captain", true);
        assertThrows(ResponseStatusException.class, () -> combat.attack(hero.id(),
                new TokenAttackRequest(sword.toString(), ambush.id(), null, null), ana));
    }

    private TokenAttackResponse attackUntilHit(UUID attacker, String attackId, UUID target) {
        for (int i = 0; i < 40; i++) {
            TokenAttackResponse r = combat.attack(attacker, new TokenAttackRequest(attackId, target, null, null), ana);
            if (Boolean.TRUE.equals(r.hit())) return r;
        }
        throw new AssertionError("+30 deveria acertar");
    }

    private TokenResponse monsterToken(String name, boolean hidden) {
        UUID monsterId = monsterService.search(name, null, null).getFirst().id();
        return live.addToken(sceneId, new TokenRequest(null, 0.0, 0.0, 1, null, hidden, null, null, monsterId, null, null, null), master);
    }

    private static TokenRequest token(String name, UUID characterId, boolean hidden) {
        return new TokenRequest(name, 0.0, 0.0, 1, null, hidden, characterId, null, null, null, null, null);
    }
}

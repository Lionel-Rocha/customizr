package top.customizr.system.service;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.RestResponse;
import top.customizr.system.dto.SheetRollResponse;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Character;
import top.customizr.system.model.Condition;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.Sheet;
import top.customizr.system.model.SheetAttack;
import top.customizr.system.model.Skill;
import top.customizr.system.model.SpellSlotPool;
import top.customizr.system.repository.BackgroundRepository;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.SpeciesRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A ficha "em jogo": origem, proficiencias, condicoes, testes contra a morte, espacos de magia,
 * descansos, ataques, talentos do catalogo, XP, retrato e rolagens (que podem ir pro chat).
 * Quem mexe: o dono do personagem ou o mestre da campanha (como no CharacterService).
 */
@Service
public class CharacterSheetService {

    private static final Set<Condition> DISADVANTAGE_ON_CHECKS = EnumSet.of(Condition.POISONED, Condition.FRIGHTENED);
    private static final Set<Condition> DISADVANTAGE_ON_ATTACKS = EnumSet.of(
            Condition.POISONED, Condition.FRIGHTENED, Condition.BLINDED, Condition.PRONE, Condition.RESTRAINED);
    private static final Set<Condition> FAIL_STR_DEX_SAVES = EnumSet.of(
            Condition.PARALYZED, Condition.PETRIFIED, Condition.STUNNED, Condition.UNCONSCIOUS);

    private final CharacterRepository characterRepository;
    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final FeatDefinitionRepository featRepository;
    private final ChatService chatService;

    public CharacterSheetService(
            CharacterRepository characterRepository,
            SpeciesRepository speciesRepository,
            BackgroundRepository backgroundRepository,
            FeatDefinitionRepository featRepository,
            ChatService chatService
    ) {
        this.characterRepository = characterRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.featRepository = featRepository;
        this.chatService = chatService;
    }

    @Transactional
    public CharacterSheetResponse chooseOrigin(UUID characterId, OriginRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        character.chooseOrigin(
                request.speciesId() == null ? null : speciesRepository.findById(request.speciesId())
                        .orElseThrow(() -> notFound("Species")),
                request.backgroundId() == null ? null : backgroundRepository.findById(request.backgroundId())
                        .orElseThrow(() -> notFound("Background")));
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse updateProficiencies(UUID characterId, ProficienciesRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        valid(() -> { character.getSheet().updateProficiencies(request.skills(), request.expertise(), request.savingThrows()); return null; });
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse updateConditions(UUID characterId, ConditionsRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        valid(() -> { character.getSheet().updateConditions(request.conditions(), request.exhaustion()); return null; });
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse spendSpellSlot(UUID characterId, SpendSpellSlotRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        valid(() -> { character.getSheet().spendSpellSlot(
                request.pool() == null ? SpellSlotPool.STANDARD : request.pool(), request.level()); return null; });
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public RestResponse rest(UUID characterId, RestRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        if (request.type() == RestRequest.RestType.LONG) {
            character.getSheet().longRest();
            return new RestResponse("LONG", List.of(), 0, CharacterSheetResponse.from(character));
        }
        Sheet.ShortRestResult result = valid(() -> character.getSheet().shortRest(request.hitDice(),
                sides -> ThreadLocalRandom.current().nextInt(1, sides + 1)));
        return new RestResponse("SHORT", result.rolls(), result.healed(), CharacterSheetResponse.from(character));
    }

    @Transactional
    public CharacterSheetResponse addAttack(UUID characterId, AttackRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        valid(() -> character.getSheet().addAttack(request.name(), request.attackBonus(), request.damage(),
                request.damageType(), request.notes()));
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse removeAttack(UUID characterId, UUID attackId, String username) {
        Character character = manageable(characterId, username);
        valid(() -> { character.getSheet().removeAttack(attackId); return null; });
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse addCatalogFeat(UUID characterId, CatalogFeatRequest request, String username) {
        Character character = manageable(characterId, username);
        requireBody(request);
        var feat = featRepository.findById(request.featId()).orElseThrow(() -> notFound("Feat"));
        valid(() -> character.getSheet().addCatalogFeat(feat, request.characterLevel(), request.choices()));
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse removeCatalogFeat(UUID characterId, UUID characterFeatId, String username) {
        Character character = manageable(characterId, username);
        valid(() -> { character.getSheet().removeCatalogFeat(characterFeatId); return null; });
        return CharacterSheetResponse.from(character);
    }

    /** XP so o mestre da (o jogador nao se premia). */
    @Transactional
    public CharacterSheetResponse gainExperience(UUID characterId, ExperienceRequest request, String username) {
        Character character = find(characterId);
        if (!character.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Only the campaign master can award experience");
        }
        requireBody(request);
        character.gainExperience(request.amount());
        return CharacterSheetResponse.from(character);
    }

    @Transactional
    public CharacterSheetResponse changePortrait(UUID characterId, PortraitRequest request, String username) {
        Character character = manageable(characterId, username);
        character.changePortrait(request == null ? null : request.portraitUrl());
        return CharacterSheetResponse.from(character);
    }

    // ------------------------------------------------------------------ rolagens

    @Transactional
    public SheetRollResponse roll(UUID characterId, SheetRollRequest request, String username) {
        Character character = manageable(characterId, username);
        if (request == null || request.kind() == null) throw badRequest("Roll kind is required");
        Sheet sheet = character.getSheet();
        List<String> notes = new ArrayList<>();
        RollMode mode = request.mode() == null ? RollMode.NORMAL : request.mode();
        String label;
        int bonus;
        String outcome = null;
        switch (request.kind()) {
            case SKILL -> {
                Skill skill = require(request.skill(), "Skill");
                label = skill.getLabel();
                bonus = sheet.skillBonus(skill);
                mode = withConditions(mode, sheet, DISADVANTAGE_ON_CHECKS, notes);
            }
            case ABILITY -> {
                AbilityScore ability = require(request.ability(), "Ability");
                label = name(ability) + " check";
                bonus = sheet.abilityModifier(ability);
                mode = withConditions(mode, sheet, DISADVANTAGE_ON_CHECKS, notes);
            }
            case SAVE -> {
                AbilityScore ability = require(request.ability(), "Ability");
                label = name(ability) + " saving throw";
                bonus = sheet.savingThrowBonus(ability);
                if ((ability == AbilityScore.STRENGTH || ability == AbilityScore.DEXTERITY)
                        && sheet.getConditions().stream().anyMatch(FAIL_STR_DEX_SAVES::contains)) {
                    outcome = "AUTOMATIC_FAILURE";
                    notes.add("Fails Strength and Dexterity saving throws (" + conditionNames(sheet, FAIL_STR_DEX_SAVES) + ")");
                }
                if (ability == AbilityScore.DEXTERITY && sheet.getConditions().contains(Condition.RESTRAINED)) {
                    mode = combine(mode, RollMode.DISADVANTAGE);
                    notes.add("Disadvantage: restrained");
                }
            }
            case INITIATIVE -> {
                label = "Initiative";
                bonus = sheet.getInitiative();
            }
            case DEATH_SAVE -> {
                RollResult d20 = DiceRoller.roll("1d20");
                Sheet.DeathSaveOutcome result = valid(() -> sheet.deathSave(d20.naturalD20()));
                return finish(character, "Death saving throw", d20, null, null, result.name(), notes, request.toChat(), username);
            }
            default -> throw badRequest("Unknown roll kind");
        }
        int penalty = sheet.getD20Penalty();
        if (penalty > 0) notes.add("Exhaustion " + sheet.getExhaustion() + ": -" + penalty);
        RollResult roll = DiceRoller.roll(d20(mode) + signed(bonus - penalty));
        return finish(character, label, roll, null, null, outcome, notes, request.toChat(), username);
    }

    /** Ataque da ficha: d20 + bonus (vantagem/desvantagem pelas condicoes) e o dano; critico dobra os dados. */
    @Transactional
    public SheetRollResponse rollAttack(UUID characterId, UUID attackId, AttackRollRequest request, String username) {
        Character character = manageable(characterId, username);
        Sheet sheet = character.getSheet();
        SheetAttack attack = sheet.getAttacks().stream().filter(a -> a.getId().equals(attackId)).findFirst()
                .orElseThrow(() -> notFound("Attack"));
        List<String> notes = new ArrayList<>();
        RollMode mode = withConditions(request == null || request.mode() == null ? RollMode.NORMAL : request.mode(),
                sheet, DISADVANTAGE_ON_ATTACKS, notes);
        int penalty = sheet.getD20Penalty();
        if (penalty > 0) notes.add("Exhaustion " + sheet.getExhaustion() + ": -" + penalty);
        RollResult hit = DiceRoller.roll(d20(mode) + signed(attack.getAttackBonus() - penalty));
        boolean critical = hit.isCriticalHit();
        RollResult damage = hit.isCriticalFail() ? null
                : DiceRoller.roll(critical ? DiceRoller.doubleDice(attack.getDamage()) : attack.getDamage());
        Boolean hits = null;
        if (request != null && request.targetArmorClass() != null) {
            hits = !hit.isCriticalFail() && (critical || hit.total() >= request.targetArmorClass());
        }
        String outcome = critical ? "CRITICAL_HIT" : hit.isCriticalFail() ? "CRITICAL_MISS" : null;
        return finish(character, attack.getName() + (attack.getDamageType() == null ? "" : " (" + attack.getDamageType() + ")"),
                hit, damage, hits, outcome, notes, request != null ? request.toChat() : null, username);
    }

    private SheetRollResponse finish(Character character, String label, RollResult roll, RollResult damage, Boolean hit,
                                     String outcome, List<String> notes, Boolean toChat, String username) {
        String fullLabel = character.getName() + " - " + label;
        UUID messageId = null;
        if (Boolean.TRUE.equals(toChat)) {
            String extra = (damage == null ? "" : " | damage: " + damage.detail())
                    + (hit == null ? "" : hit ? " | HIT" : " | MISS")
                    + (outcome == null ? "" : " | " + outcome)
                    + (notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")");
            messageId = chatService.postRoll(character.getCampaign().getId(), character.getCharacterId(),
                    fullLabel, roll, extra, username).id();
        }
        return new SheetRollResponse(fullLabel, roll, damage, hit, outcome, notes, messageId);
    }

    private static RollMode withConditions(RollMode mode, Sheet sheet, Set<Condition> causes, List<String> notes) {
        String found = conditionNames(sheet, causes);
        if (found.isEmpty()) return mode;
        notes.add("Disadvantage: " + found);
        return combine(mode, RollMode.DISADVANTAGE);
    }

    /** Vantagem + desvantagem se anulam. */
    private static RollMode combine(RollMode a, RollMode b) {
        if (a == RollMode.NORMAL) return b;
        if (b == RollMode.NORMAL || a == b) return a;
        return RollMode.NORMAL;
    }

    private static String conditionNames(Sheet sheet, Set<Condition> causes) {
        return sheet.getConditions().stream().filter(causes::contains)
                .map(c -> c.name().toLowerCase()).sorted().reduce((x, y) -> x + ", " + y).orElse("");
    }

    private static String d20(RollMode mode) {
        return switch (mode) {
            case ADVANTAGE -> "2d20kh1";
            case DISADVANTAGE -> "2d20kl1";
            default -> "1d20";
        };
    }

    private static String signed(int value) {
        return value == 0 ? "" : value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String name(AbilityScore ability) {
        String n = ability.name().toLowerCase();
        return java.lang.Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------------ utilitarios

    private <T> T require(T value, String what) {
        if (value == null) throw badRequest(what + " is required");
        return value;
    }

    private <T> T valid(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private void requireBody(Object request) {
        if (request == null) throw badRequest("Request body is required");
    }

    private Character find(UUID characterId) {
        return characterRepository.findById(characterId).orElseThrow(() -> notFound("Character"));
    }

    private Character manageable(UUID characterId, String username) {
        Character character = find(characterId);
        if (!character.getOwner().getName().equals(username)
                && !character.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("You cannot manage this character");
        }
        return character;
    }

    private ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

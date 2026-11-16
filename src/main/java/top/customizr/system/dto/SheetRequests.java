package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Condition;
import top.customizr.system.model.RollMode;
import top.customizr.system.model.Skill;
import top.customizr.system.model.SpellSlotPool;

import java.util.Set;
import java.util.UUID;

/** Pedidos das rotas da ficha completa (um arquivo so, pra nao espalhar 10 records minusculos). */
public final class SheetRequests {

    private SheetRequests() {}

    public record Origin(UUID speciesId, UUID backgroundId) {}

    public record Proficiencies(Set<Skill> skills, Set<Skill> expertise, Set<AbilityScore> savingThrows) {}

    public record Conditions(Set<Condition> conditions, Integer exhaustion) {}

    public record SpendSlot(SpellSlotPool pool, int level) {}

    public enum RestType { SHORT, LONG }

    public record Rest(RestType type, int hitDice) {}

    public record Attack(String name, int attackBonus, String damage, String damageType, String notes) {}

    public record CatalogFeat(UUID featId, int characterLevel, String choices) {}

    public record Experience(int amount) {}

    public record Portrait(String portraitUrl) {}

    public enum RollKind { ABILITY, SAVE, SKILL, INITIATIVE, DEATH_SAVE }

    /** skill pra SKILL; ability pra ABILITY/SAVE. toChat = manda o resultado pro chat da campanha. */
    public record Roll(RollKind kind, Skill skill, AbilityScore ability, RollMode mode, Boolean toChat) {}

    /** targetArmorClass opcional: diz se acertou. */
    public record AttackRoll(RollMode mode, Integer targetArmorClass, Boolean toChat) {}
}

package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/**
 * O que muda no proximo nivel desta classe, antes de confirmar.
 * {@code fixedHp}: no primeiro nivel do personagem o PV e o dado cheio + CON (sem escolha).
 * {@code features}: habilidades que chegam neste nivel (da classe e da subclasse ja escolhida);
 * {@code choices}: grupos de escolha que abrem (estilo de luta, invocacoes...), escolhidos depois na lista de features.
 * {@code abilityCap}: teto do atributo (20; 30 na Dadiva Epica).
 */
public record LevelUpPreviewResponse(
        UUID classId,
        String className,
        boolean newClass,
        int classLevel,
        int totalLevel,
        int hitDie,
        int constitutionModifier,
        int averageHp,
        Integer fixedHp,
        int proficiencyBefore,
        int proficiencyAfter,
        List<String> features,
        List<String> choices,
        boolean subclassRequired,
        List<SubclassOption> subclasses,
        boolean abilityScoreImprovement,
        boolean epicBoon,
        int abilityCap,
        List<SlotChange> spellSlots,
        List<ResourceChange> resources,
        boolean experienceReady,
        List<String> warnings
) {
    public record SubclassOption(UUID id, String name, String sourceCode) {}

    /** Espacos de magia que mudam ({@code pool} STANDARD ou PACT). */
    public record SlotChange(String pool, int level, int before, int after) {}

    /** Recurso de classe que muda ou aparece ({@code before} 0 = novo). */
    public record ResourceChange(String id, String name, int before, int after) {}
}

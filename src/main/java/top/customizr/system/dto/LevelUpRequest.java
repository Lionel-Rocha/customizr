package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;

import java.util.Map;
import java.util.UUID;

/**
 * Subir 1 nivel. {@code classId}: classe que sobe (uma que o personagem ja tem ou uma nova, multiclasse).
 * PV: {@code AVERAGE} (metade do dado + 1), {@code ROLL} (o servidor rola) ou {@code MANUAL} ({@code hpValue} = o que saiu
 * no dado da mesa); a CON entra sozinha. No nivel que da Aumento de Valor de Atributo: {@code abilityIncreases}
 * (+2 em um ou +1 em dois) ou {@code featId} (com ate +1 de meio-talento). {@code announce}: manda um cartao pro chat
 * (padrao: sim).
 */
public record LevelUpRequest(UUID classId, HitPointMethod hpMethod, Integer hpValue, UUID subclassId,
                             Map<AbilityScore, Integer> abilityIncreases, UUID featId, Boolean announce) {

    public enum HitPointMethod { AVERAGE, ROLL, MANUAL }
}

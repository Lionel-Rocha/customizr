package top.customizr.system.dto;

import java.util.UUID;

/**
 * Uma linha da lista "o que o personagem tem / pode pegar".
 * status: GRANTED (veio com o nivel/especie/antecedente), SELECTED (escolha ja feita), AVAILABLE (escolha
 * que a classe oferece e ainda nao foi feita: adicionar com POST /character/{id}/classes/{characterClassId}/feats).
 * origin: "Fighter 2", "Species", "Background", "Feat", "Spell (Wizard)", "Item".
 */
public record CharacterFeatureResponse(
        SharedKind kind,
        UUID refId,
        String name,
        String origin,
        Integer level,
        String type,
        String choiceGroup,
        String status,
        UUID characterClassId,
        String text
) {}

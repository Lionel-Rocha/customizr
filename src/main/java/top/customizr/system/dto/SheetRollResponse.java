package top.customizr.system.dto;

import top.customizr.system.model.RollResult;

import java.util.List;
import java.util.UUID;

/**
 * label: o que foi rolado ("Aria - Stealth"); notes: o que mudou a rolagem (vantagem por condicao,
 * exaustao...); outcome: resultado especial (falha automatica, teste contra a morte etc.).
 */
public record SheetRollResponse(
        String label,
        RollResult roll,
        RollResult damage,
        Boolean hit,
        String outcome,
        List<String> notes,
        UUID chatMessageId
) {}

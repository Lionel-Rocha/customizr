package top.customizr.system.dto;

import top.customizr.system.model.RollResult;

import java.util.List;
import java.util.UUID;

/** Resultado da area: a rolagem de dano e, pra cada token dentro dela, a salvaguarda e o dano que levou. */
public record TemplateResolveResponse(String label, int dc, String ability, RollResult damageRoll, List<Target> targets,
                                      UUID chatMessageId) {

    public record Target(UUID tokenId, String name, Integer saveTotal, boolean saved, boolean automaticFailure,
                         Integer damageApplied) {}
}

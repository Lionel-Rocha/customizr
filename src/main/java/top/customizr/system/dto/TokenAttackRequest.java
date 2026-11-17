package top.customizr.system.dto;

import top.customizr.system.model.RollMode;

import java.util.UUID;

/**
 * attackId: id de {@code GET /live/tokens/{id}/attacks}. targetTokenId opcional (sem alvo so rola).
 * applyDamage: se acertar, ja tira o PV do alvo (padrao: sim). mode: vantagem/desvantagem escolhida na mao;
 * as condicoes de quem ataca e do alvo entram sozinhas.
 */
public record TokenAttackRequest(String attackId, UUID targetTokenId, RollMode mode, Boolean applyDamage) {}

package top.customizr.system.dto;

import top.customizr.system.model.RollResult;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de um ataque na mesa. hit: null quando nao da pra saber (sem alvo, alvo sem CA, ou salvaguarda);
 * damageApplied: quanto saiu do PV do alvo (depois de resistencia/imunidade/vulnerabilidade do monstro).
 */
public record TokenAttackResponse(String label, RollResult attackRoll, RollResult damageRoll, Boolean hit, String outcome,
                                  List<String> notes, UUID targetTokenId, String targetName, Integer damageApplied,
                                  UUID chatMessageId) {}

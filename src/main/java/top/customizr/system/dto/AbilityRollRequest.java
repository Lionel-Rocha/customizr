package top.customizr.system.dto;

import java.util.UUID;

/**
 * Rolagem de atributos. Tudo opcional: o padrao e 4d6 tirando o menor, 6 valores.
 * campaignId (+ characterId opcional) = manda o resultado pro chat, pra mesa ver que foi rolado de verdade.
 */
public record AbilityRollRequest(Integer dice, Integer sides, Integer dropLowest, Integer sets, Integer rerollOnesOrBelow,
                                 UUID campaignId, UUID characterId) {}

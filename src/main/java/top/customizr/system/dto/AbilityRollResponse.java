package top.customizr.system.dto;

import top.customizr.system.model.AbilityScoreGenerator;

import java.util.List;
import java.util.UUID;

/** method: ex. "4d6 drop lowest 1"; scores: os valores do maior pro menor; chatMessageId se foi pro chat. */
public record AbilityRollResponse(String method, List<AbilityScoreGenerator.RolledScore> rolls, List<Integer> scores,
                                  int total, UUID chatMessageId) {}

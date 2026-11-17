package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;

import java.util.Map;

/** budget null = 27 pontos. */
public record PointBuyRequest(Map<AbilityScore, Integer> scores, Integer budget) {}

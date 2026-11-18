package top.customizr.system.dto;

import java.util.UUID;

/** key: "condition:POISONED" (troca o icone da condicao) ou "marker:Blessed" (marcador pronto). Mesma key = substitui. */
public record CampaignIconRequest(String key, String label, UUID imageId) {}

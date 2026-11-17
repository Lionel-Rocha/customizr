package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/** tokenIds vazio = todos os tokens visiveis da cena ativa. */
public record InitiativeRollRequest(List<UUID> tokenIds) {}

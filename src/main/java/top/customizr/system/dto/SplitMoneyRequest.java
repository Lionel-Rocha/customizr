package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/** Dividir as moedas do espolio igualmente: entre esses personagens (vazio = todos da campanha). */
public record SplitMoneyRequest(List<UUID> characterIds) {}

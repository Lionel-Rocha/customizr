package top.customizr.system.dto;

import java.util.UUID;

/** Gente da campanha na mesa: nome, papel (MASTER, PLAYER, SPECTATOR) e cor (null = automatica). */
public record MemberResponse(UUID userId, String name, String role, String color) {}

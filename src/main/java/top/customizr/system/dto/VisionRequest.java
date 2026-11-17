package top.customizr.system.dto;

/** Cena: enabled liga/desliga a visao dinamica. Token: visionFt = alcance em pes (null = sem limite). */
public record VisionRequest(Boolean enabled, Integer visionFt) {}

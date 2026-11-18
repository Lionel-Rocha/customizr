package top.customizr.system.dto;

import top.customizr.system.model.SceneLighting;

/** Luz da cena: BRIGHT (clara), DIM (penumbra) ou DARK (escura). */
public record SceneLightingRequest(SceneLighting lighting) {}

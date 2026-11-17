package top.customizr.system.dto;

import java.util.List;

/** enabled liga/desliga; reveal/hide = casas [coluna, linha]; clear = cobre tudo; revealAll = tira a neblina. */
public record FogRequest(Boolean enabled, List<int[]> reveal, List<int[]> hide, Boolean clear, Boolean revealAll) {}

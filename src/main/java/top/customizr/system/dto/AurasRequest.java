package top.customizr.system.dto;

import java.util.List;

/** Troca as auras do token (ate 4): raio em pes, cor #RRGGBB e um nome curto. */
public record AurasRequest(List<Item> auras) {

    public record Item(int radiusFt, String color, String label) {}
}

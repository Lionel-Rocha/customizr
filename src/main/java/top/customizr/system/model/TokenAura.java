package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/** Aura ou anel de alcance fixo do token (aura do paladino a 10 pes, espirito guardiao a 15...). Todos veem. */
@Embeddable
@Getter
public class TokenAura {

    @Column(name = "radius_ft", nullable = false)
    private int radiusFt;

    /** Cor em #RRGGBB (padrao: dourado). */
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "label", length = 30)
    private String label;

    protected TokenAura() {}

    public TokenAura(int radiusFt, String color, String label) {
        if (radiusFt < 5 || radiusFt > 500) throw new IllegalArgumentException("Aura radius goes from 5 to 500 feet");
        if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Aura color must be #RRGGBB");
        if (label != null && label.length() > 30) throw new IllegalArgumentException("Aura label is too long");
        this.radiusFt = radiusFt;
        this.color = color;
        this.label = label == null || label.isBlank() ? null : label.trim();
    }
}

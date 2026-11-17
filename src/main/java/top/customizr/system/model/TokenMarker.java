package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/**
 * Marcador livre no token (concentracao, abencoado, marca do cacador, em chamas...): rotulo, icone e,
 * se tiver, quantas rodadas faltam. A contagem desce no inicio do turno do token na iniciativa
 * (ou na virada da rodada, se o token nao estiver na iniciativa); chegou a zero, some.
 */
@Embeddable
@Getter
public class TokenMarker {

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    /** Icone curto (um emoji ou uma chave de icone que a tela conhece). */
    @Column(name = "icon", length = 16)
    private String icon;

    @Column(name = "rounds_left")
    private Integer roundsLeft;

    protected TokenMarker() {}

    public TokenMarker(String label, String icon, Integer roundsLeft) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Marker label is required");
        if (label.trim().length() > 40) throw new IllegalArgumentException("Marker label is too long");
        if (icon != null && icon.length() > 16) throw new IllegalArgumentException("Marker icon is too long");
        if (roundsLeft != null && (roundsLeft < 1 || roundsLeft > 1000)) {
            throw new IllegalArgumentException("Rounds must be between 1 and 1000");
        }
        this.label = label.trim();
        this.icon = icon == null || icon.isBlank() ? null : icon.trim();
        this.roundsLeft = roundsLeft;
    }

    /** Uma rodada a menos; devolve se ainda vale. */
    boolean tick() {
        if (roundsLeft == null) return true;
        roundsLeft--;
        return roundsLeft > 0;
    }
}

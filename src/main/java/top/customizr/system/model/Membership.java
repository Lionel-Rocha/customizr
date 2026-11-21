package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
public class Membership {
    @Id
    UUID id;

    @Getter
    UUID campaignId;

    @Getter
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Getter @Setter
    Role role;

    /** Cor do jogador na mesa (#RRGGBB): anel dos tokens dele, regua, ping. Null = cor automatica. */
    @Getter
    @Column(length = 7)
    String color;

    protected Membership() {}

    public Membership(UUID campaignId, UUID userId, Role role) {
        this.id = UUID.randomUUID();
        this.campaignId = campaignId;
        this.userId = userId;
        this.role = role;
    }

    public void changeColor(String color) {
        if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("A cor precisa ser #RRGGBB");
        this.color = color;
    }
}

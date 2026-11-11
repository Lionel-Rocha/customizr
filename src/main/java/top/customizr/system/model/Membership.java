package top.customizr.system.model;

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

    protected Membership() {}

    public Membership(UUID campaignId, UUID userId, Role role) {
        this.id = UUID.randomUUID();
        this.campaignId = campaignId;
        this.userId = userId;
        this.role = role;
    }
}

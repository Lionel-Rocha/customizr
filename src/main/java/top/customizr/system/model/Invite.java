package top.customizr.system.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name="invite")
@Getter
@Setter
public class Invite {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User invitedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected Invite() {}

    public Invite(Campaign campaign, User invitedUser, Role role) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.invitedUser = invitedUser;
        this.role = role;
    }
}
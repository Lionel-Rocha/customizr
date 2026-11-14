package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "npcs")
@Getter
@EqualsAndHashCode(of = "id")
public class NPC {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String personality;

    @Column(name = "master_information", length = 4000)
    private String masterInformation;

    @Column(name = "photo_url")
    private String photoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    protected NPC() {}

    public NPC(String name, String personality, String masterInformation, String photoUrl, Campaign campaign) {
        update(name, personality, masterInformation, photoUrl);
        if (campaign == null) {
            throw new IllegalArgumentException("NPC campaign is required");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
    }

    public void update(String name, String personality, String masterInformation, String photoUrl) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("NPC name is required");
        }
        this.name = name.trim();
        this.personality = personality;
        this.masterInformation = masterInformation;
        this.photoUrl = photoUrl;
    }
}

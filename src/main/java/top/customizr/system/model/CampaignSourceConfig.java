package top.customizr.system.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

@Entity
public class CampaignSourceConfig {

    @Id
    private UUID id;

    @ManyToOne @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne @JoinColumn(name = "source_id")
    private Source source;

    private boolean enabled;

    public CampaignSourceConfig(Campaign campaign, Source source, boolean enabled) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.source = source;
        this.enabled = enabled;
    }
}

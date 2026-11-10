package top.customizr.system.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "campaign_source_config", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"campaign_id", "source_id"})
})
public class CampaignSourceConfig {

    @Id
    private UUID id;

    @ManyToOne @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne @JoinColumn(name = "source_id")
    private Source source;

    private boolean enabled;

    public CampaignSourceConfig() {
    }

    public CampaignSourceConfig(Campaign campaign, Source source, boolean enabled) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.source = source;
        this.enabled = enabled;
    }
}

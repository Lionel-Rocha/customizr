package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "Campaign")
@Getter @Setter
public class Campaign {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "master_id", nullable = false)
    private User master;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignSourceConfig> sourceConfigs = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Character> characters = new ArrayList<>();

    protected Campaign() {}

    public Campaign(String name, User master) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.master = master;
        this.createdAt = LocalDateTime.now();
        this.code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String toString() {
        return "Campaign{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", master=" + master +
                ", code='" + code + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

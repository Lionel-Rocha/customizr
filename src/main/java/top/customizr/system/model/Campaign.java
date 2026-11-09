package top.customizr.system.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

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

    protected Campaign() {}

    public Campaign(String name, User master, String code) {
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

package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Imagem guardada no banco (mapa, retrato de personagem/NPC), em vez de depender de URL de fora.
 * Mesma imagem enviada de novo na mesma campanha nao duplica (conferida pelo SHA-256).
 */
@Entity
@Table(name = "image_assets", uniqueConstraints = @UniqueConstraint(
        name = "uk_image_campaign_hash", columnNames = {"campaign_id", "sha256"}))
@Getter
public class ImageAsset {

    public static final int MAX_BYTES = 8 * 1024 * 1024;

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(nullable = false)
    private int size;

    // bytea (PostgreSQL e H2 aceitam). Sem @Lob, que no PostgreSQL vira "large object" (oid).
    @JsonIgnore
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ImageAsset() {}

    public ImageAsset(Campaign campaign, byte[] data, String contentType, String sha256, String fileName) {
        if (campaign == null || data == null || data.length == 0) {
            throw new IllegalArgumentException("Campaign and image data are required");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Image is too big (max 8 MB)");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.data = data;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.fileName = fileName == null ? null : fileName.length() > 200 ? fileName.substring(0, 200) : fileName;
        this.size = data.length;
        this.createdAt = LocalDateTime.now();
    }

    public String getUrl() {
        return "/image/" + id;
    }
}

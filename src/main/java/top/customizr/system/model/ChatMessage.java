package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensagem do chat da campanha. O texto ja vem com as rolagens feitas pelo servidor,
 * marcadas como {@code ⟦total ← conta⟧} (ver {@link InlineRolls}); ninguem forja um resultado
 * porque os marcadores digitados pelo usuario sao trocados por colchetes antes.
 */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_campaign_time", columnList = "campaign_id, created_at"))
@Getter
public class ChatMessage {

    public static final int MAX_LENGTH = 2000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Nome exibido (personagem que esta falando) ou null = o nome do usuario. */
    @Column(name = "speaker_name")
    private String speakerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageType type;

    @Column(nullable = false, length = 4000)
    private String content;

    /** So pra {@link ChatMessageType#WHISPER}: quem recebe (o mestre tambem ve). */
    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Column(name = "roll_total")
    private Integer rollTotal;

    @Column(name = "natural_d20")
    private Integer naturalD20;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {}

    public ChatMessage(Campaign campaign, User author, String speakerName, ChatMessageType type, String content,
                       User recipient, RollResult roll) {
        if (campaign == null || author == null || type == null) {
            throw new IllegalArgumentException("Campaign, author and type are required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message is empty");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("Message is too long");
        }
        if (type == ChatMessageType.WHISPER && recipient == null) {
            throw new IllegalArgumentException("Whisper needs a recipient");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.author = author;
        this.speakerName = speakerName == null || speakerName.isBlank() ? null : speakerName.trim();
        this.type = type;
        this.content = content;
        this.recipient = recipient;
        this.rollTotal = roll == null ? null : roll.total();
        this.naturalD20 = roll == null ? null : roll.naturalD20();
        this.createdAt = LocalDateTime.now();
    }

    /** Quem pode ler: todo mundo da campanha, menos sussurro (so autor, destinatario e mestre). */
    public boolean isVisibleTo(User user) {
        if (type != ChatMessageType.WHISPER) {
            return true;
        }
        UUID id = user.getUserId();
        return author.getUserId().equals(id)
                || recipient.getUserId().equals(id)
                || campaign.getMaster().getUserId().equals(id);
    }
}

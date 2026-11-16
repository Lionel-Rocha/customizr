package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Entrada da enciclopedia. {@code visible = false}: so o mestre ve (segredo ainda nao revelado). */
@Entity
@Table(name = "encyclopedia_entries")
@Getter
public class EncyclopediaEntry {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encyclopedia_id", nullable = false)
    private Encyclopedia encyclopedia;

    @Column(name = "entry_name", nullable = false)
    private String entryName;

    @Column(length = 20000)
    private String information;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Column(nullable = false)
    private boolean visible;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_name")
    private String referenceName;

    protected EncyclopediaEntry() {}

    EncyclopediaEntry(Encyclopedia encyclopedia, String entryName, String information, EntryType type, boolean visible,
                      ReferenceType referenceType, EncyclopediaReferenceable reference) {
        this.id = UUID.randomUUID();
        this.encyclopedia = encyclopedia;
        update(entryName, information, type, visible);
        reference(referenceType, reference);
    }

    public void update(String entryName, String information, EntryType type, boolean visible) {
        this.entryName = Places.requireName(entryName, "Entry");
        this.information = Places.blankToNull(information);
        this.type = type == null ? EntryType.OTHER : type;
        this.visible = visible;
    }

    public void reference(ReferenceType referenceType, EncyclopediaReferenceable reference) {
        if ((referenceType == null) != (reference == null)) {
            throw new IllegalArgumentException("Reference type and reference must be given together");
        }
        this.referenceType = referenceType;
        this.referenceId = reference == null ? null : reference.getReferenceId();
        this.referenceName = reference == null ? null : reference.getDisplayName();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}

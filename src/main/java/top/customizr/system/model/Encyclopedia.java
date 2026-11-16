package top.customizr.system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Enciclopedia da campanha (Campaign 1 -- 1 Encyclopedia), criada na primeira entrada. */
@Entity
@Table(name = "encyclopedias")
@Getter
public class Encyclopedia {

    @Id
    private UUID id;

    @JsonIgnore
    @OneToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, unique = true)
    private Campaign campaign;

    @OneToMany(mappedBy = "encyclopedia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("entryName")
    private List<EncyclopediaEntry> entries = new ArrayList<>();

    protected Encyclopedia() {}

    public Encyclopedia(Campaign campaign) {
        if (campaign == null) {
            throw new IllegalArgumentException("Encyclopedia campaign is required");
        }
        this.id = UUID.randomUUID();
        this.campaign = campaign;
    }

    public EncyclopediaEntry addEntry(String entryName, String information, EntryType type, boolean visible,
                                      ReferenceType referenceType, EncyclopediaReferenceable reference) {
        EncyclopediaEntry entry = new EncyclopediaEntry(this, entryName, information, type, visible, referenceType, reference);
        entries.add(entry);
        return entry;
    }
}

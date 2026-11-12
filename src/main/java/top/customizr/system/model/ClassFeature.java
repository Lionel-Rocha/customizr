package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "class_feature_grants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"class_id", "feature_id", "level"})
})
@Getter
public class ClassFeature {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private DndClass dndClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private int level;

    @Column(name = "choice_group")
    private String choiceGroup;

    protected ClassFeature() {}

    ClassFeature(DndClass dndClass, Feature feature, int level, String choiceGroup) {
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("Feature level must be between 1 and 20");
        }
        this.id = UUID.randomUUID();
        this.dndClass = dndClass;
        this.feature = feature;
        this.level = level;
        this.choiceGroup = choiceGroup;
    }
}

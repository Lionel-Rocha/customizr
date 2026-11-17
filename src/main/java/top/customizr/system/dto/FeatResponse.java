package top.customizr.system.dto;

import top.customizr.system.model.FeatDefinition;
import java.util.UUID;

public record FeatResponse(UUID id, String name, String source, Integer page, String category, String prerequisite,
                           boolean repeatable, String description, String descriptionText) {
    public static FeatResponse from(FeatDefinition f) {
        return new FeatResponse(f.getId(), f.getName(), f.getSource().getCode(), f.getPage(), f.getCategory(),
                f.getPrerequisite(), f.isRepeatable(), f.getDescription(), f.getDescriptionText());
    }
}

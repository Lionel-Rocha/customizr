package top.customizr.system.dto;

import top.customizr.system.model.Species;
import java.util.UUID;

/** description = entries cru com tags {@...} (igual Feature); descriptionText = texto pronto. */
public record SpeciesResponse(UUID id, String name, String source, Integer page, String size, Integer speedFt,
                              String abilityScoreIncreases, String description, String descriptionText) {
    public static SpeciesResponse from(Species s) {
        return new SpeciesResponse(s.getId(), s.getName(), s.getSource().getCode(), s.getPage(), s.getSize(), s.getSpeedFt(),
                s.getAbilityScoreIncreases(), s.getDescription(), s.getDescriptionText());
    }
}

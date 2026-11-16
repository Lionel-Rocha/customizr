package top.customizr.system.dto;

import top.customizr.system.model.Background;
import top.customizr.system.model.FeatDefinition;
import top.customizr.system.model.Species;

import java.util.UUID;

public final class ContentResponse {

    private ContentResponse() {}

    public record SpeciesInfo(UUID id, String name, String source, String size, Integer speedFt,
                              String abilityScoreIncreases, String description) {
        public static SpeciesInfo from(Species s) {
            return new SpeciesInfo(s.getId(), s.getName(), s.getSource().getCode(), s.getSize(), s.getSpeedFt(),
                    s.getAbilityScoreIncreases(), s.getDescription());
        }
    }

    public record BackgroundInfo(UUID id, String name, String source, String abilityScoreIncreases,
                                 String skillProficiencies, String grantedFeat, String description) {
        public static BackgroundInfo from(Background b) {
            return new BackgroundInfo(b.getId(), b.getName(), b.getSource().getCode(), b.getAbilityScoreIncreases(),
                    b.getSkillProficiencies(), b.getGrantedFeat(), b.getDescription());
        }
    }

    public record FeatInfo(UUID id, String name, String source, String category, String prerequisite,
                           boolean repeatable, String description) {
        public static FeatInfo from(FeatDefinition f) {
            return new FeatInfo(f.getId(), f.getName(), f.getSource().getCode(), f.getCategory(), f.getPrerequisite(),
                    f.isRepeatable(), f.getDescription());
        }
    }
}

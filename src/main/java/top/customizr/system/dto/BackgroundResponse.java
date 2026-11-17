package top.customizr.system.dto;

import top.customizr.system.model.Background;
import java.util.UUID;

public record BackgroundResponse(UUID id, String name, String source, Integer page, String abilityScoreIncreases,
                                 String skillProficiencies, String grantedFeat, String description, String descriptionText) {
    public static BackgroundResponse from(Background b) {
        return new BackgroundResponse(b.getId(), b.getName(), b.getSource().getCode(), b.getPage(), b.getAbilityScoreIncreases(),
                b.getSkillProficiencies(), b.getGrantedFeat(), b.getDescription(), b.getDescriptionText());
    }
}

package top.customizr.system.dto;

import java.util.UUID;

public record AddSubclassFeatureResponse(
        UUID featureId,
        UUID subclassId,
        String name,
        String source,
        int level,
        String description,
        String choiceGroup
) {}

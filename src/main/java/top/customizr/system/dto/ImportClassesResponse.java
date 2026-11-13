package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

public record ImportClassesResponse(List<ImportedClass> imported, List<SkippedClass> skipped) {
    public record ImportedClass(
            UUID classId,
            String name,
            String source,
            int subclasses,
            int classFeatures
    ) {}

    public record SkippedClass(String name, String source, String reason) {}
}

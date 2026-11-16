package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

public record ImportClassesResponse(
        List<ImportedClass> imported,
        List<SkippedClass> skipped,
        List<UnresolvedSubclassSpell> unresolvedSubclassSpells,
        int subclassesAdded,
        int subclassFeaturesAdded,
        int subclassSpellLinksAdded
) {
    public record ImportedClass(
            UUID classId,
            String name,
            String source,
            int subclasses,
            int classFeatures
    ) {}

    public record SkippedClass(String name, String source, String reason) {}

    public record UnresolvedSubclassSpell(
            String className,
            String classSource,
            String subclassName,
            String subclassSource,
            String reference,
            String reason
    ) {}
}

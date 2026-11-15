package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

public record ImportItemsResponse(
        List<ImportedItem> imported,
        List<SkippedItem> skipped
) {
    public record ImportedItem(UUID id, String name, String source) {}

    public record SkippedItem(String name, String source, String reason) {}
}

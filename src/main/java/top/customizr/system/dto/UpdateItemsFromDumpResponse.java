package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

public record UpdateItemsFromDumpResponse(
        List<ProcessedItem> created,
        List<ProcessedItem> updated,
        List<SkippedItem> skipped
) {
    public record ProcessedItem(UUID id, String name, String source) {}

    public record SkippedItem(String name, String source, String reason) {}
}

package top.customizr.system.dto;

import java.util.List;
import java.util.UUID;

/** Mesmo formato do /item/update-from-dump: criados, atualizados e ignorados (com o motivo). */
public record ContentImportResponse(
        List<Processed> created,
        List<Processed> updated,
        List<Skipped> skipped
) {
    public record Processed(UUID id, String name, String source) {}

    public record Skipped(String name, String source, String reason) {}
}

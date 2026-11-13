package top.customizr.system.dto;

import top.customizr.system.model.Source;

import java.util.UUID;

public record SourceResponse(UUID id, String name, String code) {
    public static SourceResponse from(Source source) {
        return new SourceResponse(source.getId(), source.getName(), source.getCode());
    }
}

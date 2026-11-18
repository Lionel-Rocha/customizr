package top.customizr.system.dto;

import top.customizr.system.model.Source;

import java.util.UUID;

/** {@code official}: livro da Wizards (true), nao oficial (false) ou nao se sabe (null), vindo da atualizacao do 5etools. */
public record SourceResponse(UUID id, String name, String code, Boolean official) {
    public static SourceResponse from(Source source) {
        return new SourceResponse(source.getId(), source.getName(), source.getCode(), source.getOfficial());
    }
}

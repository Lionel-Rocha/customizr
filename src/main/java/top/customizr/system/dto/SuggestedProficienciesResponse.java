package top.customizr.system.dto;

import top.customizr.system.model.ProficiencySuggestions;

import java.util.List;

/**
 * O que a especie, o antecedente e as classes dao (lido do 5etools). {@code choices}: o que e escolha do jogador,
 * ex.: "Elf: choose 1 language (any standard)".
 */
public record SuggestedProficienciesResponse(List<String> languages, List<String> tools, List<String> weapons,
                                             List<String> armor, List<String> choices) {
    public static SuggestedProficienciesResponse from(ProficiencySuggestions.Suggested s) {
        return new SuggestedProficienciesResponse(s.languages(), s.tools(), s.weapons(), s.armor(), s.choices());
    }
}

package top.customizr.system.dto;

import java.util.List;

/** Idiomas, ferramentas, armas e armaduras da ficha (texto livre). Lista nula = deixa como esta; vazia = limpa. */
public record OtherProficienciesRequest(List<String> languages, List<String> tools, List<String> weapons,
                                        List<String> armor) {}

package top.customizr.system.dto;

/**
 * Historia e anotacoes do personagem. Campo nulo = fica como esta; texto vazio = apaga.
 * {@code gmNotes}: so o mestre grava.
 */
public record CharacterStoryRequest(String alignment, String age, String height, String weight, String eyes,
                                    String hair, String skin, String appearance, String personality, String ideals,
                                    String bonds, String flaws, String backstory, String allies, String notes,
                                    String gmNotes) {}

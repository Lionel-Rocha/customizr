package top.customizr.system.dto;

import top.customizr.system.model.CharacterStory;

import java.util.UUID;

/** Historia e anotacoes. {@code gmNotes} so vem pro mestre (nulo pro jogador); {@code master} diz quem esta vendo. */
public record CharacterStoryResponse(UUID characterId, String name, boolean master,
                                     String alignment, String age, String height, String weight, String eyes,
                                     String hair, String skin, String appearance, String personality, String ideals,
                                     String bonds, String flaws, String backstory, String allies, String notes,
                                     String gmNotes) {

    public static CharacterStoryResponse from(UUID characterId, String name, CharacterStory s, boolean master) {
        return new CharacterStoryResponse(characterId, name, master, s.getAlignment(), s.getAge(), s.getHeight(),
                s.getWeight(), s.getEyes(), s.getHair(), s.getSkin(), s.getAppearance(), s.getPersonality(),
                s.getIdeals(), s.getBonds(), s.getFlaws(), s.getBackstory(), s.getAllies(), s.getNotes(),
                master ? s.getGmNotes() : null);
    }
}

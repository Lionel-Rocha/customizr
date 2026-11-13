package top.customizr.system.dto;

import top.customizr.system.model.Attributes;

public record AttributesResponse(
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma
) {
    public static AttributesResponse from(Attributes attributes) {
        return new AttributesResponse(
                attributes.getStrength(),
                attributes.getDexterity(),
                attributes.getConstitution(),
                attributes.getIntelligence(),
                attributes.getWisdom(),
                attributes.getCharisma()
        );
    }
}

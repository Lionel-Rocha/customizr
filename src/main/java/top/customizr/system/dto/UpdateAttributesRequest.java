package top.customizr.system.dto;

public record UpdateAttributesRequest(
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma
) {}

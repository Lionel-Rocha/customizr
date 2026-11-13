package top.customizr.system.dto;

import java.util.UUID;

public record ChooseSubclassRequest(UUID subclassId, int chosenAtLevel) {}

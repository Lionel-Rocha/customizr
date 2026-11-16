package top.customizr.system.dto;

import java.util.List;

public record RestResponse(String type, List<String> hitDiceRolls, int healed, CharacterSheetResponse sheet) {}

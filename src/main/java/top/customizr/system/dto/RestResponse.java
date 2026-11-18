package top.customizr.system.dto;

import java.util.List;

/** {@code recovered}: recursos que voltaram no descanso ("Rage +1", "Focus Points", "Heroic Inspiration"). */
public record RestResponse(String type, List<String> hitDiceRolls, int healed, List<String> recovered,
                           CharacterSheetResponse sheet) {}

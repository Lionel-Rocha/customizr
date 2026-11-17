package top.customizr.system.dto;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Skill;
import java.util.Set;

public record ProficienciesRequest(Set<Skill> skills, Set<Skill> expertise, Set<AbilityScore> savingThrows) {}

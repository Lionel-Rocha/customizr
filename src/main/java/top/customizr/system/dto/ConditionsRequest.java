package top.customizr.system.dto;

import top.customizr.system.model.Condition;
import java.util.Set;

/** exhaustion de 0 a 6; campos null ficam como estao. */
public record ConditionsRequest(Set<Condition> conditions, Integer exhaustion) {}

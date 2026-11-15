package top.customizr.system.dto;

import top.customizr.system.model.Duration;
import top.customizr.system.model.DurationUnit;

public record SpellDurationRequest(
        int amount,
        DurationUnit unit,
        boolean concentration
) {
    public Duration toModel() {
        return new Duration(amount, unit, concentration);
    }
}

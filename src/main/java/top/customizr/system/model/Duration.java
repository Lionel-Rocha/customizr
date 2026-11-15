package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Embeddable
@Getter
@EqualsAndHashCode
public class Duration {

    @Column(name = "duration_amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false)
    private DurationUnit unit;

    @Column(name = "duration_concentration", nullable = false)
    private boolean concentration;

    protected Duration() {}

    public Duration(int amount, DurationUnit unit, boolean concentration) {
        if (unit == null) {
            throw new IllegalArgumentException("Duration unit is required");
        }
        if ((unit == DurationUnit.INSTANTANEOUS || unit == DurationUnit.CONCENTRATION) && amount != 0) {
            throw new IllegalArgumentException("Instantaneous and unbounded concentration durations use amount 0");
        }
        if (unit == DurationUnit.INSTANTANEOUS && concentration) {
            throw new IllegalArgumentException("Instantaneous duration cannot require concentration");
        }
        if (unit != DurationUnit.INSTANTANEOUS && unit != DurationUnit.CONCENTRATION && amount < 1) {
            throw new IllegalArgumentException("Duration amount must be positive");
        }
        if (unit == DurationUnit.CONCENTRATION && !concentration) {
            throw new IllegalArgumentException("Concentration duration must be marked as concentration");
        }

        this.amount = amount;
        this.unit = unit;
        this.concentration = concentration;
    }
}

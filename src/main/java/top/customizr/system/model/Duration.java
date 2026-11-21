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
            throw new IllegalArgumentException("Falta a unidade da duração");
        }
        if ((unit == DurationUnit.INSTANTANEOUS || unit == DurationUnit.CONCENTRATION) && amount != 0) {
            throw new IllegalArgumentException("Instantaneous and unbounded concentration durations use amount 0");
        }
        if ((unit == DurationUnit.UNTIL_DISPELLED || unit == DurationUnit.SPECIAL) && amount != 0) {
            throw new IllegalArgumentException("Until dispelled and special durations use amount 0");
        }
        if (unit == DurationUnit.INSTANTANEOUS && concentration) {
            throw new IllegalArgumentException("Duração instantânea não pode exigir concentração");
        }
        if (unit != DurationUnit.INSTANTANEOUS && unit != DurationUnit.CONCENTRATION
                && unit != DurationUnit.UNTIL_DISPELLED && unit != DurationUnit.SPECIAL && amount < 1) {
            throw new IllegalArgumentException("A duração precisa ser positiva");
        }
        if (unit == DurationUnit.CONCENTRATION && !concentration) {
            throw new IllegalArgumentException("Duração de concentração precisa estar marcada como concentração");
        }

        this.amount = amount;
        this.unit = unit;
        this.concentration = concentration;
    }
}

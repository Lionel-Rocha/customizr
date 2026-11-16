package top.customizr.system.model;

/**
 * Resultado de uma rolagem. {@code detail} ja vem pronto pra mostrar ("2d6[4,5] + 3 = 12");
 * {@code naturalD20} e o d20 que valeu quando a expressao comeca com um d20 (critico/falha).
 */
public record RollResult(String expression, int total, String detail, Integer naturalD20) {

    public boolean isCriticalHit() {
        return naturalD20 != null && naturalD20 == 20;
    }

    public boolean isCriticalFail() {
        return naturalD20 != null && naturalD20 == 1;
    }
}

package top.customizr.system.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moedas de 5e e quanto cada uma vale em po (o inventario guarda tudo em po, {@code moneyGp}).
 * 1 pl = 10 po; 1 pe = 0,5 po; 1 pp = 0,1 po; 1 pc = 0,01 po.
 */
public enum Currency {
    PLATINUM("pp", new BigDecimal("10")),
    GOLD("gp", BigDecimal.ONE),
    ELECTRUM("ep", new BigDecimal("0.5")),
    SILVER("sp", new BigDecimal("0.1")),
    COPPER("cp", new BigDecimal("0.01"));

    private final String symbol;
    private final BigDecimal goldValue;

    Currency(String symbol, BigDecimal goldValue) {
        this.symbol = symbol;
        this.goldValue = goldValue;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getGoldValue() {
        return goldValue;
    }

    /** Quantidade nessa moeda -> po. */
    public BigDecimal toGold(BigDecimal amount) {
        return amount.multiply(goldValue);
    }

    /**
     * Quebra um valor em po nas moedas usuais (sem electro): 123,45 po = 12 pl, 3 po, 4 pp, 5 pc.
     * {@code usePlatinum = false} deixa tudo em ouro (como muita mesa prefere).
     */
    public static Map<Currency, Long> breakdown(BigDecimal gold, boolean usePlatinum) {
        long copper = gold.multiply(new BigDecimal("100")).setScale(0, RoundingMode.DOWN).longValueExact();
        Map<Currency, Long> out = new LinkedHashMap<>();
        if (usePlatinum) {
            out.put(PLATINUM, copper / 1000);
            copper %= 1000;
        }
        out.put(GOLD, copper / 100);
        copper %= 100;
        out.put(SILVER, copper / 10);
        out.put(COPPER, copper % 10);
        return out;
    }
}

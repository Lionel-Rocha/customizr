package top.customizr.system.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * As moedas de verdade do inventario (quantas pl, po, pe, pp e pc), nao so o valor.
 * O {@link Inventory} mantem {@code moneyGp} sempre igual ao valor desta bolsa, pra loja e espolio seguirem em po.
 */
@Embeddable
public class CoinPurse {

    /** Valor de cada moeda em pc. */
    private static final Map<Currency, Long> COPPER = new EnumMap<>(Map.of(
            Currency.PLATINUM, 1000L, Currency.GOLD, 100L, Currency.ELECTRUM, 50L,
            Currency.SILVER, 10L, Currency.COPPER, 1L));

    /** Ordem de gastar: da moeda menor pra maior (o troco volta em po/pp/pc). */
    private static final Currency[] SMALLEST_FIRST = {Currency.COPPER, Currency.SILVER, Currency.ELECTRUM,
            Currency.GOLD, Currency.PLATINUM};

    /** 50 moedas pesam 1 lb (2014 e 2024). */
    public static final int COINS_PER_POUND = 50;

    @Column(name = "coins_pp")
    private Long platinum;
    @Column(name = "coins_gp")
    private Long gold;
    @Column(name = "coins_ep")
    private Long electrum;
    @Column(name = "coins_sp")
    private Long silver;
    @Column(name = "coins_cp")
    private Long copper;

    protected CoinPurse() {}

    /** Bolsa nova com um valor em po, quebrado em po/pp/pc (sem platina nem electro). */
    public static CoinPurse ofGold(BigDecimal gold) {
        CoinPurse purse = empty();
        purse.giveChange(toCopper(gold));
        return purse;
    }

    public static CoinPurse empty() {
        CoinPurse purse = new CoinPurse();
        purse.platinum = 0L;
        purse.gold = 0L;
        purse.electrum = 0L;
        purse.silver = 0L;
        purse.copper = 0L;
        return purse;
    }

    public long count(Currency currency) {
        Long value = switch (currency) {
            case PLATINUM -> platinum;
            case GOLD -> gold;
            case ELECTRUM -> electrum;
            case SILVER -> silver;
            case COPPER -> copper;
        };
        return value == null ? 0 : value;
    }

    public Map<Currency, Long> asMap() {
        Map<Currency, Long> out = new EnumMap<>(Currency.class);
        for (Currency c : Currency.values()) out.put(c, count(c));
        return out;
    }

    public long totalCopper() {
        long total = 0;
        for (Currency c : Currency.values()) total += count(c) * COPPER.get(c);
        return total;
    }

    public BigDecimal totalGold() {
        return BigDecimal.valueOf(totalCopper()).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
    }

    public long totalCoins() {
        long total = 0;
        for (Currency c : Currency.values()) total += count(c);
        return total;
    }

    /** Poe ou tira moedas dessa moeda exatamente (tirar mais do que tem da erro). */
    void add(Currency currency, long amount) {
        long next = count(currency) + amount;
        if (next < 0) {
            throw new IllegalArgumentException("Only " + count(currency) + " " + currency.getSymbol() + " in the purse");
        }
        set(currency, next);
    }

    /**
     * Gasta {@code copperCost} pc: usa as moedas da menor pra maior e devolve o troco em po/pp/pc.
     * Ex.: 5 pc + 2 po pagando 1 po = gasta os 5 pc e 1 po, volta 1 pp de troco.
     */
    void pay(long copperCost) {
        if (copperCost < 0) throw new IllegalArgumentException("Cost cannot be negative");
        if (copperCost > totalCopper()) throw new IllegalArgumentException("Not enough money");
        long paid = 0;
        for (Currency c : SMALLEST_FIRST) {
            long value = COPPER.get(c);
            while (paid < copperCost && count(c) > 0) {
                long need = (copperCost - paid + value - 1) / value;
                long take = Math.min(count(c), need);
                set(c, count(c) - take);
                paid += take * value;
            }
            if (paid >= copperCost) break;
        }
        giveChange(paid - copperCost);
    }

    /**
     * Troca {@code amount} moedas de {@code from} por {@code to} (cambista): o valor tem que dar moedas inteiras.
     * Ex.: 30 pp -> 3 po; 1 pl -> 10 po; 25 pp -> po nao fecha (2,5 po).
     */
    void exchange(Currency from, Currency to, long amount) {
        if (from == null || to == null || from == to) throw new IllegalArgumentException("Choose two different coins");
        if (amount < 1) throw new IllegalArgumentException("Amount must be at least 1");
        long value = amount * COPPER.get(from);
        long target = COPPER.get(to);
        if (value % target != 0) {
            throw new IllegalArgumentException(amount + " " + from.getSymbol() + " does not make whole " + to.getSymbol());
        }
        add(from, -amount);
        add(to, value / target);
    }

    private void giveChange(long copperValue) {
        add(Currency.GOLD, copperValue / 100);
        add(Currency.SILVER, (copperValue % 100) / 10);
        add(Currency.COPPER, copperValue % 10);
    }

    private void set(Currency currency, long value) {
        switch (currency) {
            case PLATINUM -> platinum = value;
            case GOLD -> gold = value;
            case ELECTRUM -> electrum = value;
            case SILVER -> silver = value;
            case COPPER -> copper = value;
        }
    }

    static long toCopper(BigDecimal gold) {
        return gold.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static long copperValue(Currency currency) {
        return COPPER.get(currency);
    }
}

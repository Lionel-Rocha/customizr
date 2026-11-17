package com.vttcaseiro.inventory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Quantia de dinheiro, guardada por moeda (nao convertida na hora de
 * guardar -- "3 PO e 5 PP" continua sendo "3 PO e 5 PP" ate alguem pedir
 * o total). {@link #totalInCopper()} da o valor total numa base comum
 * (peças de cobre), o que e o que precisa pra comparar/somar quantias
 * misturadas sem reinventar a tabela de conversao toda vez.
 */
public class Money {
    private Map<Currency, Integer> amounts = new EnumMap<>(Currency.class);

    public Money() {
        for (Currency c : Currency.values()) {
            amounts.put(c, 0);
        }
    }

    public int get(Currency currency) {
        return amounts.getOrDefault(currency, 0);
    }

    /** @throws IllegalArgumentException se a quantia for negativa */
    public void add(Currency currency, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Nao da pra adicionar quantia negativa (use subtract).");
        }
        amounts.merge(currency, amount, Integer::sum);
    }

    /** @return true se tinha o suficiente e a subtracao foi feita; false
     * se nao tinha (e nesse caso NADA muda -- a operacao e tudo ou nada). */
    public boolean subtract(Currency currency, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Quantia nao pode ser negativa.");
        }
        int current = get(currency);
        if (current < amount) {
            return false;
        }
        amounts.put(currency, current - amount);
        return true;
    }

    public long totalInCopper() {
        long total = 0;
        for (Currency c : Currency.values()) {
            total += (long) get(c) * c.copperValue();
        }
        return total;
    }

    /** Tenta pagar um valor em copper. So paga se {@link #totalInCopper()}
     * for suficiente; senao nao mexe em nada. Quando precisa "quebrar"
     * moeda maior (ex.: pagar 50cp tendo so 1 PO), nao faz isso gastando
     * a moeda inteira sem dar troco -- em vez disso, calcula o saldo
     * final em copper (total - pago) e remonta esse saldo nas moedas
     * disponiveis, da maior pra menor, com o menor numero de pecas
     * possivel. O valor total depois de pagar sempre bate exatamente com
     * "total antes - valor pago", nao importa que moedas voce tinha.
     *
     * @return true se pagou */
    public boolean pay(long amountInCopper) {
        if (amountInCopper < 0) {
            throw new IllegalArgumentException("Valor a pagar nao pode ser negativo.");
        }
        if (amountInCopper == 0) {
            return true; // nada a fazer -- e de proposito nao mexer em nada aqui
        }
        long total = totalInCopper();
        if (total < amountInCopper) {
            return false;
        }
        long remaining = total - amountInCopper;
        for (Currency c : Currency.values()) {
            amounts.put(c, 0);
        }
        Currency[] highToLow = {Currency.PLATINUM, Currency.GOLD, Currency.ELECTRUM, Currency.SILVER, Currency.COPPER};
        for (Currency c : highToLow) {
            int count = (int) (remaining / c.copperValue());
            amounts.put(c, count);
            remaining -= (long) count * c.copperValue();
        }
        return true;
    }
}

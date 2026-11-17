package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabela aleatoria do mestre (tesouro, encontros, nomes, clima). Cada linha
 * e um resultado; pode comecar com uma faixa ("1-3: goblins", "4: orc") --
 * sem faixa, cada linha vale 1. O dado e o maior numero das faixas.
 */
public class RandomTable {
    private static final Pattern RANGE = Pattern.compile("^\\s*(\\d+)\\s*(?:-\\s*(\\d+))?\\s*[:.)-]\\s*(.*)$");

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String entries = "";

    public RandomTable() {
    }

    public RandomTable(String name, String entries) {
        setName(name);
        setEntries(entries);
    }

    public String getId() { return id; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name == null ? "" : name.strip(); }
    public String getEntries() { return entries == null ? "" : entries; }
    public void setEntries(String entries) { this.entries = entries == null ? "" : entries; }

    /** Uma linha ja com a faixa resolvida. */
    public record Row(int from, int to, String text) {
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        int next = 1;
        for (String line : getEntries().split("\\R")) {
            if (line.isBlank()) continue;
            Matcher m = RANGE.matcher(line);
            if (m.matches() && !m.group(3).isBlank()) {
                int from = Integer.parseInt(m.group(1));
                int to = m.group(2) == null ? from : Integer.parseInt(m.group(2));
                if (to < from) { int t = from; from = to; to = t; }
                out.add(new Row(from, to, m.group(3).strip()));
                next = to + 1;
            } else {
                out.add(new Row(next, next, line.strip()));
                next++;
            }
        }
        return out;
    }

    /** Tamanho do dado (maior numero das faixas). */
    public int dieSize() {
        int max = 0;
        for (Row r : rows()) max = Math.max(max, r.to());
        return max;
    }

    /** Resultado rolado: "d6 = 4: 50 po e uma pocao". roller recebe o tamanho do dado e devolve 1..N. */
    public String roll(IntUnaryOperator roller) {
        List<Row> rows = rows();
        if (rows.isEmpty()) return "(tabela vazia)";
        int die = dieSize();
        int value = roller.applyAsInt(die);
        for (Row r : rows) {
            if (value >= r.from() && value <= r.to()) return "d" + die + " = " + value + ": " + r.text();
        }
        return "d" + die + " = " + value + ": (nenhuma linha cobre esse numero)";
    }

    /** Tabelas prontas pra comecar (o mestre edita/apaga a vontade). */
    public static List<RandomTable> samples() {
        return List.of(
                new RandomTable("Tesouro rapido", "1-2: 2d6 po\n3-4: 4d10 po e uma bugiganga\n5: Pocao de Cura\n"
                        + "6: 1d4 gemas de 50 po\n7: Pergaminho de magia de 1o nivel\n8: Item magico comum"),
                new RandomTable("Clima", "Ceu limpo\nNublado\nGaroa fina\nChuva forte\nNeblina densa\nVento frio"),
                new RandomTable("Nome de taverna", "O Javali Dourado\nA Caneca Quebrada\nO Dragao Bebado\n"
                        + "A Coruja Sonolenta\nO Barril Furado\nA Ultima Vela"),
                new RandomTable("Complicacao na estrada", "1-2: Nada acontece\n3: Uma roda da carroca quebra\n"
                        + "4: Viajantes pedem ajuda\n5: Rastros frescos de algo grande\n6: Emboscada de bandidos"));
    }
}

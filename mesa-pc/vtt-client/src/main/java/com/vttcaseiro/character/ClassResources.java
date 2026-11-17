package com.vttcaseiro.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recursos com usos que cada classe de 2024 ganha por nivel (Furia do
 * Barbaro, Inspiracao Bardica, Segundo Folego...). Aplicar a classe na
 * ficha cria/atualiza esses recursos sozinho -- o jogador nao precisa
 * lembrar quantas Furias tem no nivel 6.
 */
public final class ClassResources {

    private ClassResources() {
    }

    private static int byLevel(int level, int[][] table) {
        int value = 0;
        for (int[] row : table) if (level >= row[0]) value = row[1];
        return value;
    }

    /** Os recursos da classe nesse nivel (nome em portugues, maximo, recarga). */
    public static List<Resource> forClass(String className, int level, CharacterSheet sheet) {
        List<Resource> out = new ArrayList<>();
        if (className == null) return out;
        String c = className.toLowerCase(Locale.ROOT);
        int cha = Math.max(1, CharacterSheet.abilityModifier(sheet.getCharisma()));
        if (c.contains("barbar")) {
            out.add(new Resource("Furia", byLevel(level, new int[][]{{1, 2}, {3, 3}, {6, 4}, {12, 5}, {17, 6}}), Resource.LONG));
        } else if (c.contains("bard")) {
            // Fonte de Inspiracao (nivel 5): passa a voltar no descanso curto.
            out.add(new Resource("Inspiracao Bardica", cha, level >= 5 ? Resource.SHORT : Resource.LONG));
        } else if (c.contains("cleric") || c.contains("cleri")) {
            if (level >= 2) out.add(new Resource("Canalizar Divindade", byLevel(level, new int[][]{{2, 2}, {6, 3}, {18, 4}}), Resource.SHORT));
        } else if (c.contains("druid")) {
            if (level >= 2) out.add(new Resource("Forma Selvagem", byLevel(level, new int[][]{{2, 2}, {6, 3}, {17, 4}}), Resource.SHORT));
        } else if (c.contains("fighter") || c.contains("guerreir")) {
            out.add(new Resource("Retomar o Folego", byLevel(level, new int[][]{{1, 2}, {4, 3}, {10, 4}}), Resource.SHORT));
            if (level >= 2) out.add(new Resource("Surto de Acao", level >= 17 ? 2 : 1, Resource.SHORT));
        } else if (c.contains("monk") || c.contains("monge")) {
            if (level >= 2) out.add(new Resource("Pontos de Foco", level, Resource.SHORT));
        } else if (c.contains("paladin")) {
            out.add(new Resource("Cura pelas Maos (PV)", 5 * level, Resource.LONG));
            if (level >= 3) out.add(new Resource("Canalizar Divindade", level >= 11 ? 3 : 2, Resource.SHORT));
        } else if (c.contains("ranger") || c.contains("patrulheir")) {
            out.add(new Resource("Inimigo Favorito (Marca do Cacador)",
                    byLevel(level, new int[][]{{1, 2}, {5, 3}, {9, 4}, {13, 5}, {17, 6}}), Resource.LONG));
        } else if (c.contains("sorcer") || c.contains("feiticeir")) {
            if (level >= 2) out.add(new Resource("Pontos de Feiticaria", level, Resource.LONG));
        } else if (c.contains("warlock") || c.contains("bruxo")) {
            out.add(new Resource("Espacos de Pacto",
                    byLevel(level, new int[][]{{1, 1}, {2, 2}, {11, 3}, {17, 4}}), Resource.SHORT));
        } else if (c.contains("wizard") || c.contains("mago")) {
            out.add(new Resource("Recuperacao Arcana", 1, Resource.LONG));
        }
        return out;
    }

    /**
     * Cria os recursos que faltam e atualiza o maximo dos que ja existem
     * (subir de nivel); mantem os usos gastos. Devolve "Furia 3/3" etc.
     */
    public static List<String> apply(CharacterSheet sheet) {
        List<String> changes = new ArrayList<>();
        for (Resource fresh : forClass(sheet.getClassName(), sheet.getLevel(), sheet)) {
            var existing = sheet.findResource(fresh.getName());
            if (existing.isPresent()) {
                Resource r = existing.get();
                int spent = r.getMax() - r.getCurrent();
                r.setMax(fresh.getMax());
                r.setCurrent(Math.max(0, fresh.getMax() - spent));
                r.setRecharge(fresh.getRecharge());
                changes.add(r.getName() + " " + r.usesText());
            } else {
                sheet.getResources().add(fresh);
                changes.add(fresh.getName() + " " + fresh.usesText());
            }
        }
        return changes;
    }

    /** Magias conhecidas do catalogo de efeitos -> rodadas de duracao (1 min = 10 rodadas). */
    public static final Map<String, Integer> SPELL_ROUNDS = Map.ofEntries(
            Map.entry("bless", 10), Map.entry("bencao", 10),
            Map.entry("bane", 10), Map.entry("perdicao", 10),
            Map.entry("shield", 1), Map.entry("escudo", 1),
            Map.entry("shield of faith", 100), Map.entry("escudo da fe", 100),
            Map.entry("haste", 10), Map.entry("velocidade", 10),
            Map.entry("guidance", 10), Map.entry("orientacao", 10));

    /** Magias que so afetam quem conjura (Escudo). */
    public static boolean selfOnly(String spellName) {
        String k = EffectModifiers.plain(spellName == null ? "" : spellName);
        return k.equals("shield") || k.equals("escudo");
    }

    /** Duracao em rodadas, ou 0 se nao e uma magia do catalogo. */
    public static int spellRounds(String spellName) {
        return SPELL_ROUNDS.getOrDefault(EffectModifiers.plain(spellName == null ? "" : spellName), 0);
    }
}

package com.vttcaseiro.character;

import java.util.ArrayList;
import java.util.List;

/**
 * Bastiao do personagem (regras de 2024, a partir do nivel 5): nome, instalacoes basicas, defensores e ate seis
 * instalacoes especiais, cada uma com espaco, ordem, contratados e anotacoes. So guarda o que o jogador anota.
 */
public class Bastion {
    public static final int BASIC_LINES = 11;
    public static final int SPECIAL_SLOTS = 6;
    private static final int MAX_TEXT = 4000;

    /** Espacos de uma instalacao (chave guardada, nome mostrado). */
    public static final String[][] SPACES = {{"cramped", "Apertado"}, {"roomy", "Espaçoso"}, {"vast", "Vasto"}};
    /** Ordens que uma instalacao especial pode receber. */
    public static final String[][] ORDERS = {{"craft", "Criar"}, {"empower", "Fortalecer"}, {"harvest", "Colher"},
            {"maintain", "Manter"}, {"recruit", "Recrutar"}, {"research", "Pesquisar"}, {"trade", "Negociar"}};

    public static class SpecialFacility {
        private String name;
        private String space;
        private String order;
        private String hirelings;
        private String notes;

        public String getName() { return name; }
        public void setName(String v) { this.name = clip(v); }
        public String getSpace() { return space; }
        public void setSpace(String v) { this.space = known(SPACES, v); }
        public String getOrder() { return order; }
        public void setOrder(String v) { this.order = known(ORDERS, v); }
        public String getHirelings() { return hirelings; }
        public void setHirelings(String v) { this.hirelings = clip(v); }
        public String getNotes() { return notes; }
        public void setNotes(String v) { this.notes = clip(v); }

        public boolean isEmpty() {
            return blank(name) && space == null && order == null && blank(hirelings) && blank(notes);
        }
    }

    private String name;
    private String defenders;
    private List<String> basicFacilities = new ArrayList<>();
    private List<SpecialFacility> specialFacilities = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String v) { this.name = clip(v); }
    public String getDefenders() { return defenders; }
    public void setDefenders(String v) { this.defenders = clip(v); }

    /** Sempre {@link #BASIC_LINES} linhas (vazias = ""). */
    public List<String> getBasicFacilities() {
        normalize();
        return basicFacilities;
    }

    /** Sempre {@link #SPECIAL_SLOTS} instalacoes (as nao usadas ficam vazias). */
    public List<SpecialFacility> getSpecialFacilities() {
        normalize();
        return specialFacilities;
    }

    public void setBasicFacility(int i, String v) {
        normalize();
        basicFacilities.set(i, v == null ? "" : clip(v));
    }

    /** Arruma o que veio de arquivo antigo ou editado na mao: tamanhos certos, nada nulo, valores conhecidos. */
    public void normalize() {
        if (basicFacilities == null) basicFacilities = new ArrayList<>();
        basicFacilities.replaceAll(s -> s == null ? "" : clip(s));
        while (basicFacilities.size() < BASIC_LINES) basicFacilities.add("");
        while (basicFacilities.size() > BASIC_LINES) basicFacilities.remove(basicFacilities.size() - 1);
        if (specialFacilities == null) specialFacilities = new ArrayList<>();
        specialFacilities.replaceAll(f -> f == null ? new SpecialFacility() : f);
        while (specialFacilities.size() < SPECIAL_SLOTS) specialFacilities.add(new SpecialFacility());
        while (specialFacilities.size() > SPECIAL_SLOTS) specialFacilities.remove(specialFacilities.size() - 1);
        for (SpecialFacility f : specialFacilities) {
            f.space = known(SPACES, f.space);
            f.order = known(ORDERS, f.order);
        }
    }

    /** Nome mostrado de uma chave (ou null). */
    public static String label(String[][] table, String key) {
        for (String[] kv : table) if (kv[0].equals(key)) return kv[1];
        return null;
    }

    /** Chave de um nome mostrado (ou null). */
    public static String key(String[][] table, String label) {
        for (String[] kv : table) if (kv[1].equals(label) || kv[0].equals(label)) return kv[0];
        return null;
    }

    private static String known(String[][] table, String v) {
        return v == null ? null : key(table, v);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clip(String v) {
        if (v == null) return null;
        return v.length() > MAX_TEXT ? v.substring(0, MAX_TEXT) : v;
    }
}

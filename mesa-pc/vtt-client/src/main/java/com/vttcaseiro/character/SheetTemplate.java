package com.vttcaseiro.character;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo de ficha feito a partir de um PDF editavel (a ficha oficial de qualquer sistema): as paginas viram imagens
 * (que viajam pela rede como as outras imagens da mesa) e cada campo do formulario guarda a posicao na pagina.
 * A ficha criada com o modelo mostra a propria pagina do PDF com os campos editaveis por cima.
 * <p>
 * Coordenadas em pontos do PDF (1/72"), com a origem no canto de CIMA a esquerda (ja convertido do PDF, que conta
 * de baixo pra cima).
 */
public class SheetTemplate {
    public static final String CHARACTER = "personagem";
    public static final String MOB = "monstro";
    public static final int MAX_FIELDS = 2000;
    public static final int MAX_PAGES = 20;

    /** Papeis que o programa usa (token, rastreador): qual campo do PDF e o nome, os PV, a CA... */
    public static final String ROLE_NAME = "nome";
    public static final String ROLE_HP = "pv";
    public static final String ROLE_HP_MAX = "pvMax";
    public static final String ROLE_AC = "ca";
    public static final String ROLE_INIT = "iniciativa";
    public static final String ROLE_SPEED = "deslocamento";
    public static final String[] ROLES = {ROLE_NAME, ROLE_HP, ROLE_HP_MAX, ROLE_AC, ROLE_INIT, ROLE_SPEED};

    public static class Page {
        public String assetId;
        public double width;
        public double height;
    }

    public static class Field {
        public String name;
        public String label;
        /** "texto", "caixa" (marcar), "lista" (opcoes). */
        public String type = "texto";
        public boolean multiline;
        public int page;
        public double x;
        public double y;
        public double w;
        public double h;
        public List<String> options = new ArrayList<>();
        public String defaultValue = "";
        /** Tamanho da letra no PDF (0 = automatico). */
        public double fontSize;
    }

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String kind = CHARACTER;
    private List<Page> pages = new ArrayList<>();
    private List<Field> fields = new ArrayList<>();
    private Map<String, String> roles = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name == null ? "" : name.trim(); }
    public String getKind() { return MOB.equals(kind) ? MOB : CHARACTER; }
    public void setKind(String kind) { this.kind = MOB.equals(kind) ? MOB : CHARACTER; }
    public List<Page> getPages() { return pages == null ? pages = new ArrayList<>() : pages; }
    public List<Field> getFields() { return fields == null ? fields = new ArrayList<>() : fields; }
    public Map<String, String> getRoles() { return roles == null ? roles = new LinkedHashMap<>() : roles; }

    @Override
    public String toString() {
        return getName() + " (PDF, " + getFields().size() + " campos)";
    }

    /** Corta o absurdo (arquivo malfeito ou vindo da rede). */
    public SheetTemplate sanitize() {
        getPages().removeIf(java.util.Objects::isNull);
        while (pages.size() > MAX_PAGES) pages.remove(pages.size() - 1);
        getFields().removeIf(f -> f == null || f.name == null || f.name.isBlank());
        while (fields.size() > MAX_FIELDS) fields.remove(fields.size() - 1);
        for (Field f : fields) {
            if (f.options == null) f.options = new ArrayList<>();
            if (f.label == null || f.label.isBlank()) f.label = f.name;
            f.page = Math.max(0, Math.min(Math.max(0, pages.size() - 1), f.page));
            if (!Double.isFinite(f.x)) f.x = 0;
            if (!Double.isFinite(f.y)) f.y = 0;
            if (!Double.isFinite(f.w) || f.w <= 0) f.w = 60;
            if (!Double.isFinite(f.h) || f.h <= 0) f.h = 14;
        }
        getRoles().entrySet().removeIf(e -> e.getValue() == null || findField(e.getValue()) == null);
        return this;
    }

    public Field findField(String fieldName) {
        for (Field f : getFields()) if (f.name.equals(fieldName)) return f;
        return null;
    }

    /** Valores iniciais (os que ja vinham no PDF). */
    public Map<String, String> defaults() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : getFields()) if (f.defaultValue != null && !f.defaultValue.isBlank()) out.put(f.name, f.defaultValue);
        return out;
    }

    // ------------------------------------------------------------------ papeis automaticos

    private static final Map<String, List<String>> ROLE_HINTS = new LinkedHashMap<>();

    static {
        ROLE_HINTS.put(ROLE_NAME, List.of("nome do personagem", "charactername", "character name", "nome personagem",
                "personagem", "nome", "name", "nome da criatura", "criatura", "monstro"));
        ROLE_HINTS.put(ROLE_HP_MAX, List.of("pv maximo", "pv max", "pvmax", "pontos de vida maximos", "vida maxima",
                "hpmax", "hp max", "max hp", "pontos de vida max", "pv total"));
        ROLE_HINTS.put(ROLE_HP, List.of("pv atual", "pvatual", "pontos de vida atuais", "hpcurrent", "current hp",
                "hp atual", "vida atual", "pontos de vida", "pv", "hp"));
        ROLE_HINTS.put(ROLE_AC, List.of("classe de armadura", "armor class", "ca", "ac", "defesa", "armadura"));
        ROLE_HINTS.put(ROLE_INIT, List.of("iniciativa", "initiative", "init"));
        ROLE_HINTS.put(ROLE_SPEED, List.of("deslocamento", "movimento", "speed", "velocidade"));
    }

    static String plain(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Adivinha os papeis pelos nomes dos campos (o mestre pode trocar depois). */
    public void guessRoles() {
        Map<String, String> found = new LinkedHashMap<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Map.Entry<String, List<String>> e : ROLE_HINTS.entrySet()) {
            String best = null;
            int bestRank = Integer.MAX_VALUE;
            for (Field f : getFields()) {
                if (!"texto".equals(f.type) || used.contains(f.name)) continue;
                String p = plain(f.name);
                String pl = plain(f.label);
                List<String> hints = e.getValue();
                for (int i = 0; i < hints.size(); i++) {
                    String h = hints.get(i);
                    boolean exact = p.equals(h) || pl.equals(h);
                    boolean contains = (" " + p + " ").contains(" " + h + " ") || (" " + pl + " ").contains(" " + h + " ");
                    int rank = exact ? i : contains ? 100 + i : Integer.MAX_VALUE;
                    if (rank < bestRank) {
                        bestRank = rank;
                        best = f.name;
                    }
                }
            }
            if (best != null) {
                found.put(e.getKey(), best);
                used.add(best);
            }
        }
        getRoles().clear();
        getRoles().putAll(found);
    }

    /** O numero inteiro de um campo ("+3", "15 (3d8)", "12/20") ou null. */
    public static Integer intOf(String value) {
        if (value == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[+-]?\\d+").matcher(value);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group().replace("+", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String valueOfRole(Map<String, String> values, String role) {
        String f = getRoles().get(role);
        return f == null || values == null ? null : values.get(f);
    }
}

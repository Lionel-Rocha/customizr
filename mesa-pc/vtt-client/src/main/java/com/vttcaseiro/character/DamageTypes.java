package com.vttcaseiro.character;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Os 13 tipos de dano de D&D, com os nomes em ingles (5e.tools, bestiario)
 * e em portugues (fichas digitadas a mao), e as regras de resistencia,
 * imunidade e vulnerabilidade de 2024: imune = 0; resistente = metade
 * (arredonda pra baixo); vulneravel = dobro (depois da resistencia).
 */
public final class DamageTypes {

    /** chave canonica (ingles) -> nome em portugues. */
    private static final Map<String, String> PT = new LinkedHashMap<>();
    /** palavra (sem acento, minuscula) -> chave canonica. */
    private static final Map<String, String> WORDS = new LinkedHashMap<>();

    static {
        add("acid", "ácido", "acid", "acido", "acida");
        add("bludgeoning", "contundente", "bludgeoning", "contundente", "concussao");
        add("cold", "frio", "cold", "frio", "gelo");
        add("fire", "fogo", "fire", "fogo", "igneo");
        add("force", "energia", "force", "energia", "forca");
        add("lightning", "elétrico", "lightning", "eletrico", "eletricidade", "relampago", "raio");
        add("necrotic", "necrótico", "necrotic", "necrotico");
        add("piercing", "perfurante", "piercing", "perfurante", "perfuracao");
        add("poison", "veneno", "poison", "veneno", "venenoso");
        add("psychic", "psíquico", "psychic", "psiquico");
        add("radiant", "radiante", "radiant", "radiante");
        add("slashing", "cortante", "slashing", "cortante", "corte");
        add("thunder", "trovejante", "thunder", "trovao", "trovejante", "sonico");
    }

    private static void add(String key, String pt, String... words) {
        PT.put(key, pt);
        for (String w : words) WORDS.put(w, key);
    }

    private DamageTypes() {
    }

    private static String plain(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** Primeiro tipo de dano citado no texto ("1d8+3 cortante", "Slashing"), ou null. */
    public static String find(String text) {
        if (text == null || text.isBlank()) return null;
        for (String word : plain(text).split("[^a-z]+")) {
            String key = WORDS.get(word);
            if (key != null) return key;
        }
        return null;
    }

    /** Todos os tipos citados no texto, na ordem. */
    public static Set<String> findAll(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        for (String word : plain(text).split("[^a-z]+")) {
            String key = WORDS.get(word);
            if (key != null) out.add(key);
        }
        return out;
    }

    public static String namePt(String key) {
        return key == null ? "" : PT.getOrDefault(key, key);
    }

    /** O que a criatura resiste / ignora / sofre em dobro (chaves canonicas). */
    public record Defenses(Set<String> resist, Set<String> immune, Set<String> vulnerable) {
        public static final Defenses NONE = new Defenses(Set.of(), Set.of(), Set.of());

        public boolean isEmpty() {
            return resist.isEmpty() && immune.isEmpty() && vulnerable.isEmpty();
        }
    }

    /** Monta a partir das listas de texto (bestiario ou ficha). */
    public static Defenses defenses(List<String> resist, List<String> immune, List<String> vulnerable) {
        return new Defenses(collect(resist), collect(immune), collect(vulnerable));
    }

    private static Set<String> collect(List<String> texts) {
        Set<String> out = new LinkedHashSet<>();
        if (texts != null) for (String t : texts) out.addAll(findAll(t));
        return out;
    }

    /** Dano final + explicacao curta ("" quando nada muda). */
    public record Result(int amount, String note) {
    }

    public static Result apply(int amount, String type, Defenses d) {
        if (type == null || d == null || amount <= 0) return new Result(Math.max(0, amount), "");
        String name = namePt(type);
        if (d.immune().contains(type)) return new Result(0, "imune a " + name);
        int result = amount;
        String note = "";
        if (d.resist().contains(type)) {
            result = result / 2;
            note = "resistente a " + name;
        }
        if (d.vulnerable().contains(type)) {
            result = result * 2;
            note = note.isEmpty() ? "vulneravel a " + name : note + " e vulneravel";
        }
        return new Result(result, note);
    }
}

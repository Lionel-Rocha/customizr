package com.vttcaseiro.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As partes de {@code ClassService.importClasses} do projeto da minha
 * noiva (customizr, Jackson) que sao logica pura -- nao tocam no Gson/
 * Jackson diretamente, so operam sobre Map/List genericos (o formato
 * "achatado" que qualquer biblioteca de JSON produz por baixo: objeto
 * vira Map, array vira List, resto e String/Number/Boolean/null).
 * Portado com cuidado, metodo por metodo, direto do Java real dela --
 * e por isso que da pra TESTAR isto de verdade aqui, mesmo sem o Gson
 * disponivel neste ambiente: um Map/List construido a mao em Java puro
 * se comporta identico ao que o Gson teria produzido.
 * <p>
 * {@link ClassImporter} e quem faz a parte que SO da pra fazer com o
 * Gson de verdade (ler o arquivo, navegar no JsonObject/JsonArray) --
 * essa parte nao foi testada aqui, so revisada.
 */
public final class ClassJsonAnalyzer {

    private ClassJsonAnalyzer() {
    }

    public static String text(Map<String, Object> node, String field) {
        if (node == null) return null;
        Object value = node.get(field);
        return value == null ? null : String.valueOf(value);
    }

    public static Optional<Integer> optionalInt(Map<String, Object> node, String field) {
        if (node == null) return Optional.empty();
        Object value = node.get(field);
        if (value instanceof Number n) {
            return Optional.of(n.intValue());
        }
        if (value instanceof String s) {
            try {
                return Optional.of(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** O objeto aninhado num campo (ex.: "hd" dentro de uma classe), ou um
     * Map vazio se o campo nao existir ou nao for um objeto -- assim quem
     * chama nunca precisa checar nulo, so le com optionalInt/text normal. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> nestedMap(Map<String, Object> node, String field) {
        if (node == null) return Map.of();
        Object value = node.get(field);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Mesma ideia de nestedMap, pra uma lista aninhada. */
    @SuppressWarnings("unchecked")
    public static List<Object> nestedList(Map<String, Object> node, String field) {
        if (node == null) return List.of();
        Object value = node.get(field);
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Uma linha (classFeature/subclass/subclassFeature) "e" desta
     * classe quando o className bate e, OU nao tem fonte de classe
     * informada na linha, OU a fonte bate tambem (5etools so preenche
     * "classSource" quando a classe NAO e do livro basico -- ausente
     * normalmente significa "do mesmo livro que a classe"). */
    public static boolean matchesClass(Map<String, Object> row, String className, String sourceCode) {
        if (row == null || className == null || !className.equals(text(row, "className"))) {
            return false;
        }
        String rowSource = text(row, "classSource");
        return isBlank(rowSource) || rowSource.equals(sourceCode);
    }

    /** Mesma ideia de matchesClass, mas pra uma linha de subclassFeature
     * bater com uma linha de subclass especifica (precisa bater classe
     * E shortName da subclasse E, se informado, a fonte da subclasse). */
    public static boolean matchesSubclassFeature(Map<String, Object> feature, Map<String, Object> subclass) {
        if (!matchesClass(feature, text(subclass, "className"), text(subclass, "classSource"))) {
            return false;
        }
        String subclassShort = text(subclass, "shortName");
        String featureShort = text(feature, "subclassShortName");
        if (subclassShort == null || !subclassShort.equals(featureShort)) {
            return false;
        }
        String subclassSource = text(subclass, "source");
        String featureSubclassSource = text(feature, "subclassSource");
        return isBlank(featureSubclassSource) || featureSubclassSource.equals(subclassSource);
    }

    /** O 5etools referencia uma feature como uma string tipo
     * "Rage|Barbarian||1" ou, com fonte explicita e envolvendo subclasse,
     * "Frenzy|Barbarian||Berserker||3" -- o nivel e sempre o ULTIMO
     * segmento que da pra ler como numero de 1 a 20 (os outros segmentos
     * podem ser nome/fonte, que tambem podem por acaso ser numericos em
     * teoria, mas nunca vem depois do nivel de verdade na convencao do
     * 5etools). Devolve null se nao achar nenhum segmento assim. */
    public static Integer referenceLevel(String reference) {
        if (reference == null) {
            return null;
        }
        String[] parts = reference.split("\\|", -1);
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                int level = Integer.parseInt(parts[i].trim());
                if (level >= 1 && level <= 20) {
                    return level;
                }
            } catch (NumberFormatException ignored) {
                // outros segmentos sao nome/fonte, nao numero -- ignora e tenta o anterior
            }
        }
        return null;
    }

    /** Detecta recursivamente se uma entrada de feature envolve escolha
     * do jogador (campo "type":"options", ou um "choose" em qualquer
     * nivel da arvore) -- e o que decide se vira FeatureType.CHOICE ou
     * FeatureType.AUTOMATIC. */
    @SuppressWarnings("unchecked")
    public static boolean hasChoiceOptions(Object node) {
        if (node instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if ("options".equals(map.get("type")) || map.containsKey("choose")) {
                return true;
            }
            for (Object value : map.values()) {
                if (hasChoiceOptions(value)) {
                    return true;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                if (hasChoiceOptions(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** O nivel em que a classe desbloqueia subclasse. Tenta, nesta ordem:
     * (1) campo explicito "subclassUnlockLevel" na propria classe; (2) o
     * menor nivel entre as referencias de classFeatures marcadas
     * "gainSubclassFeature"; (3) o menor nivel entre as subclassFeatures
     * que pertencem a essa classe. Null se nenhuma das tres der certo
     * (nesse caso a importacao dessa classe e pulada -- ver ClassImporter). */
    @SuppressWarnings("unchecked")
    public static Integer findSubclassUnlockLevel(
            Map<String, Object> classEntry, String sourceCode, List<Map<String, Object>> subclassEntries) {
        Optional<Integer> explicit = optionalInt(classEntry, "subclassUnlockLevel");
        if (explicit.isPresent() && explicit.get() >= 1 && explicit.get() <= 20) {
            return explicit.get();
        }

        Integer best = null;
        Object classFeatures = classEntry.get("classFeatures");
        if (classFeatures instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> rawRef) {
                    Map<String, Object> ref = (Map<String, Object>) rawRef;
                    if (Boolean.TRUE.equals(ref.get("gainSubclassFeature"))) {
                        Integer level = referenceLevel(text(ref, "classFeature"));
                        if (level != null && (best == null || level < best)) {
                            best = level;
                        }
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }

        String className = text(classEntry, "name");
        for (Map<String, Object> subclass : subclassEntries) {
            if (!matchesClass(subclass, className, sourceCode)) {
                continue;
            }
            Object refs = subclass.get("subclassFeatures");
            if (refs instanceof List<?> list) {
                for (Object ref : list) {
                    Integer level = referenceLevel(String.valueOf(ref));
                    if (level != null && (best == null || level < best)) {
                        best = level;
                    }
                }
            }
        }
        return best;
    }
}

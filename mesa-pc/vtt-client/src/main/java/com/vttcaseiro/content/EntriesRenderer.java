package com.vttcaseiro.content;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o formato de "entries" do 5e.tools -- texto com marcacao
 * inline {@code {@tag ...}} dentro de uma arvore de blocos tipados
 * ("entries", "list", "item", "table" etc, a mesma estrutura usada em
 * features de classe, antecedentes, especies, feats, itens e magias) --
 * num texto plano legivel, pronto pra mandar pro chat quando alguem
 * clica numa entrada de compendio anexada na ficha.
 * <p>
 * NAO e um renderer completo (o 5e.tools de verdade tem um Renderer.js
 * enorme, com dezenas de tipos de bloco e tags). Cobre os tipos de bloco
 * e as tags inline que de fato aparecem no conteudo do XPHB/XDMG/XMM que
 * importei pra popular o compendio -- um tipo de bloco ou tag
 * desconhecida cai num fallback razoavel (tenta achar "entries"/"entry"
 * dentro, ou devolve o texto cru) em vez de travar ou perder o resto do
 * texto. Ver HANDOFF.md pra uma lista honesta do que NAO esta coberto.
 * <p>
 * Pura logica sobre Map/List/String, igual {@link ClassJsonAnalyzer} e
 * pela mesma razao: um Map/List montado a mao em Java puro se comporta
 * identico ao que o Gson teria produzido, entao da pra TESTAR isto de
 * verdade mesmo sem o Gson disponivel neste ambiente.
 */
public final class EntriesRenderer {

    /** Casa tanto "{@tag conteudo}" quanto "{@tag}" sem conteudo nenhum
     * (ex.: "{@h}", usado em blocos de ataque pra significar "Acerto:"). */
    private static final Pattern TAG = Pattern.compile("\\{@(\\S+) ([^{}]*)\\}|\\{@(\\S+)\\}");

    private EntriesRenderer() {
    }

    /** Uma lista de entries (o campo "entries" de um antecedente, feat,
     * especie, item ou magia) virando texto plano, um paragrafo por
     * linha-em-branco. */
    @SuppressWarnings("unchecked")
    public static String render(List<Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object entry : entries) {
            String rendered = renderNode(entry);
            if (!rendered.isBlank()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(rendered);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String renderNode(Object node) {
        if (node == null) {
            return "";
        }
        if (node instanceof String s) {
            return renderInline(s);
        }
        if (node instanceof Number n) {
            return formatNumber(n);
        }
        if (node instanceof Boolean) {
            return String.valueOf(node);
        }
        if (node instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String rendered = renderNode(item);
                if (!rendered.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(rendered);
                }
            }
            return sb.toString();
        }
        if (node instanceof Map<?, ?> rawMap) {
            return renderTypedNode((Map<String, Object>) rawMap);
        }
        return String.valueOf(node);
    }

    private static String renderTypedNode(Map<String, Object> map) {
        String type = ClassJsonAnalyzer.text(map, "type");
        String name = ClassJsonAnalyzer.text(map, "name");
        if (type == null) {
            type = "";
        }
        switch (type) {
            case "list":
            case "options":
                return renderList(map);
            case "item":
                return renderItem(map);
            case "entries":
            case "inset":
            case "insetReadaloud":
            case "section":
                return renderEntriesBlock(map, name);
            case "table":
                return renderTable(map);
            case "quote":
                return renderQuote(map);
            case "image":
            case "gallery":
                return ""; // sem como mostrar imagem em texto de chat
            default:
                return renderFallback(map, name);
        }
    }

    private static String renderEntriesBlock(Map<String, Object> map, String name) {
        String body = render(ClassJsonAnalyzer.nestedList(map, "entries"));
        if (name != null && !name.isBlank()) {
            return body.isBlank() ? name : name + ": " + body;
        }
        return body;
    }

    private static String renderQuote(Map<String, Object> map) {
        String body = render(ClassJsonAnalyzer.nestedList(map, "entries"));
        String by = ClassJsonAnalyzer.text(map, "by");
        StringBuilder sb = new StringBuilder("\"").append(body).append("\"");
        if (by != null && !by.isBlank()) {
            sb.append(" -- ").append(by);
        }
        return sb.toString();
    }

    private static String renderList(Map<String, Object> map) {
        List<Object> items = ClassJsonAnalyzer.nestedList(map, "items");
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            String rendered = item instanceof Map<?, ?>
                    ? renderItem((Map<String, Object>) item)
                    : renderInline(formatNumber(item));
            if (!rendered.isBlank()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("- ").append(rendered);
            }
        }
        return sb.toString();
    }

    private static String renderItem(Map<String, Object> map) {
        String name = ClassJsonAnalyzer.text(map, "name");
        String entryText = ClassJsonAnalyzer.text(map, "entry");
        String body = entryText != null
                ? renderInline(entryText)
                : render(ClassJsonAnalyzer.nestedList(map, "entries"));
        if (name != null && !name.isBlank()) {
            return body.isBlank() ? renderInline(name) : renderInline(name) + " " + body;
        }
        return body;
    }

    /** Tabela ("colLabels" + "rows", formato do 5e.tools -- ver HANDOFF.md
     * pra exemplos reais) reconstruida linha por linha como texto, uma
     * linha por "|": cada celula e um numero/string comum (renderInline
     * direto) ou um objeto {@code {"type":"cell","roll":{...}}} (celula de
     * tabela rolavel -- "exact" vira um numero so, "min"/"max" vira um
     * intervalo "N-M"; "pad":true preenche com zero igual o 5e.tools faz
     * na tela, ex. 04 em vez de 4). */
    private static String renderTable(Map<String, Object> map) {
        String caption = ClassJsonAnalyzer.text(map, "caption");
        List<Object> colLabels = ClassJsonAnalyzer.nestedList(map, "colLabels");
        List<Object> rows = ClassJsonAnalyzer.nestedList(map, "rows");

        StringBuilder sb = new StringBuilder("Tabela");
        if (caption != null && !caption.isBlank()) {
            sb.append(": ").append(caption);
        }
        if (!colLabels.isEmpty()) {
            sb.append("\n").append(renderTableRow(colLabels));
        }
        for (Object row : rows) {
            if (row instanceof List<?> cells) {
                sb.append("\n").append(renderTableRow((List<Object>) cells));
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String renderTableRow(List<Object> cells) {
        StringBuilder sb = new StringBuilder();
        for (Object cell : cells) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(renderTableCell(cell));
        }
        return sb.toString();
    }

    private static String renderTableCell(Object cell) {
        if (cell == null) {
            return "";
        }
        if (cell instanceof Number n) {
            return renderInline(formatNumber(n));
        }
        if (cell instanceof String) {
            return renderInline(String.valueOf(cell));
        }
        if (cell instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cellMap = (Map<String, Object>) rawMap;
            String type = ClassJsonAnalyzer.text(cellMap, "type");
            if ("cell".equals(type) && cellMap.containsKey("roll")) {
                return renderTableRollCell(ClassJsonAnalyzer.nestedMap(cellMap, "roll"));
            }
            return renderTypedNode(cellMap);
        }
        return String.valueOf(cell);
    }

    private static String renderTableRollCell(Map<String, Object> roll) {
        boolean pad = Boolean.TRUE.equals(roll.get("pad"));
        Object exact = roll.get("exact");
        if (exact != null) {
            return padRollNumber(exact, pad);
        }
        Object min = roll.get("min");
        Object max = roll.get("max");
        if (min != null && max != null) {
            return padRollNumber(min, pad) + "-" + padRollNumber(max, pad);
        }
        return "";
    }

    private static String padRollNumber(Object value, boolean pad) {
        String text = formatNumber(value);
        return pad && text.length() == 1 ? "0" + text : text;
    }

    /** O JSON generico deste arquivo vem de {@code Gson.fromJson(el,
     * Object.class)} (ver FiveToolsJson.toMap) -- isso faz TODO numero
     * virar {@code Double}, mesmo um inteiro de verdade no JSON original
     * (ex.: {@code "exact": 4} chega aqui como 4.0). Sem isso, qualquer
     * numero de tabela ou de texto aparecia errado no chat ("1-4.0" em
     * vez de "1-4", "04.0" em vez de "04" com pad). */
    private static String formatNumber(Object value) {
        if (value instanceof Double d && !d.isNaN() && !d.isInfinite() && d == Math.floor(d)) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(value);
    }

    /** Tipo de bloco nao reconhecido: tenta "entries" ou "entry" antes de
     * desistir, pra nao perder texto por causa de um "type" que o
     * 5e.tools tenha adicionado depois desta rodada. */
    private static String renderFallback(Map<String, Object> map, String name) {
        if (map.containsKey("entries")) {
            return renderEntriesBlock(map, name);
        }
        String entry = ClassJsonAnalyzer.text(map, "entry");
        if (entry != null) {
            return name != null && !name.isBlank() ? name + ": " + renderInline(entry) : renderInline(entry);
        }
        return "";
    }

    /** As tags inline {@code {@tag ...}} dentro de um texto -- ver a lista
     * coberta no javadoc da classe; tag desconhecida cai no fallback de
     * {@link #displaySegment}. */
    public static String renderInline(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = TAG.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String tag = m.group(1) != null ? m.group(1) : m.group(3);
            String content = m.group(2) != null ? m.group(2) : "";
            out.append(renderTag(tag, content));
            last = m.end();
        }
        out.append(text.substring(last));
        return out.toString();
    }

    private static String renderTag(String tag, String content) {
        switch (tag.toLowerCase(Locale.ROOT)) {
            case "b": case "bold": case "i": case "italic": case "u": case "s": case "strike": case "note":
            case "filter": case "link":
                return displaySegment(content);
            case "dc":
                return "CD " + content.trim();
            case "hit": {
                String v = content.trim();
                return v.startsWith("+") || v.startsWith("-") ? v : "+" + v;
            }
            case "h":
                return "Acerto: ";
            case "atk":
                return renderAttackCode(content) + ":";
            // Tags do formato 2024 (XMM/XPHB): sem elas o texto do monstro
            // saia "m +4, reach..." e "dex CD 21" em vez de frases legiveis.
            case "atkr":
                return renderAttackRollCode(content) + ":";
            case "actsave":
                return "Salvaguarda de " + abilityPt(content.trim()) + ":";
            case "actsavefail":
                return "Falha:";
            case "actsavesuccess":
                return "Sucesso:";
            case "actsavesuccessorfail":
                return "Falha ou sucesso:";
            case "acttrigger":
                return "Gatilho:";
            case "actresponse":
                return "Resposta:";
            case "recharge":
                return content.isBlank() ? "(Recarrega com 6)" : "(Recarrega com " + content.trim() + "-6)";
            case "chance":
                // {@chance 25|||Random reading!|Regular reading}: so o numero.
                return content.split("\\|")[0].trim() + "%";
            case "damage": case "dice":
                return content.split("\\|")[0].trim();
            case "scaledamage": case "scaledice": {
                // {@scaledamage 8d6|3-9|1d6}: o texto fala do AUMENTO por
                // nivel, que e o 3o campo (o 1o e o dano base da magia).
                String[] parts = content.split("\\|");
                return (parts.length >= 3 ? parts[2] : parts[0]).trim();
            }
            default:
                return displaySegment(content);
        }
    }

    private static String renderAttackCode(String content) {
        StringBuilder sb = new StringBuilder();
        for (String code : content.split(",")) {
            if (sb.length() > 0) sb.append("/");
            sb.append(attackCodeName(code.trim()));
        }
        return sb.toString();
    }

    /** {@code {@atkr m}} / {@code r} / {@code m,r} (2024). */
    private static String renderAttackRollCode(String content) {
        String c = content.replace(" ", "").toLowerCase(Locale.ROOT);
        return switch (c) {
            case "m" -> "Ataque corpo a corpo";
            case "r" -> "Ataque a distancia";
            case "m,r", "r,m" -> "Ataque corpo a corpo ou a distancia";
            default -> "Ataque";
        };
    }

    private static String abilityPt(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "str" -> "FOR";
            case "dex" -> "DES";
            case "con" -> "CON";
            case "int" -> "INT";
            case "wis" -> "SAB";
            case "cha" -> "CAR";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    private static String attackCodeName(String code) {
        switch (code.toLowerCase(Locale.ROOT)) {
            case "mw": return "Ataque com arma corpo a corpo";
            case "rw": return "Ataque com arma a distancia";
            case "ms": return "Ataque com magia corpo a corpo";
            case "rs": return "Ataque com magia a distancia";
            default: return code;
        }
    }

    /** {@code name|source|display} -> display; {@code name|source} ->
     * name; nome sozinho -> nome; tambem tira um sufixo {@code #algumacoisa}
     * usado em algumas referencias (ex.: {@code "light|xphb#c"}). */
    static String displaySegment(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\|");
        String chosen = parts.length >= 3 ? parts[parts.length - 1] : parts[0];
        int hash = chosen.indexOf('#');
        if (hash >= 0) {
            chosen = chosen.substring(0, hash);
        }
        return chosen.trim();
    }
}

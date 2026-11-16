package top.customizr.system.model;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma os "entries" do 5etools em texto legivel: paragrafos, blocos com titulo, listas,
 * tabelas simples e as marcacoes {@code {@spell fireball|xphb}} viram so "fireball". Entende tambem
 * as marcacoes de 2024 ({@code {@actSave con}} = "Constitution Saving Throw:", {@code {@atkr m}} etc.).
 */
public final class EntriesRenderer {

    private static final Pattern TAG = Pattern.compile("\\{@(\\S+) ([^{}]*)\\}|\\{@(\\S+)\\}");
    private static final String PIPE = Pattern.quote("|");

    private EntriesRenderer() {}

    public static String render(JsonNode entries) {
        if (entries == null || entries.isNull() || entries.isMissingNode()) {
            return null;
        }
        String text = node(entries).strip();
        return text.isEmpty() ? null : text;
    }

    /** Texto solto com marcacoes do 5etools (ex.: nota de resistencia) -> texto limpo. */
    public static String renderText(String text) {
        return text == null ? null : inline(text);
    }

    private static String node(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return "";
        if (n.isString()) return inline(n.asString());
        if (n.isNumber() || n.isBoolean()) return n.asString();
        if (n.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : n) {
                String r = node(item);
                if (!r.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(r);
                }
            }
            return sb.toString();
        }
        if (n.isObject()) return typed(n);
        return n.asString();
    }

    private static String typed(JsonNode n) {
        String type = n.path("type").asString("");
        String name = n.path("name").asString(null);
        switch (type) {
            case "list", "options" -> {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : n.path("items")) {
                    String r = node(item);
                    if (!r.isBlank()) {
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append("- ").append(r.replace("\n\n", " "));
                    }
                }
                return sb.toString();
            }
            case "item", "itemSub", "itemSpell" -> {
                String body = n.has("entry") ? node(n.path("entry")) : node(n.path("entries"));
                return name == null ? body : inline(name) + " " + body;
            }
            case "table" -> {
                StringBuilder sb = new StringBuilder();
                if (name != null) sb.append(inline(name)).append('\n');
                StringBuilder head = new StringBuilder();
                for (JsonNode label : n.path("colLabels")) {
                    if (!head.isEmpty()) head.append(" | ");
                    head.append(node(label));
                }
                if (!head.isEmpty()) sb.append(head).append('\n');
                for (JsonNode row : n.path("rows")) {
                    StringBuilder line = new StringBuilder();
                    JsonNode cells = row.isObject() ? row.path("row") : row;
                    for (JsonNode cell : cells) {
                        if (!line.isEmpty()) line.append(" | ");
                        line.append(cell.isObject() && cell.has("roll") ? rollRange(cell.path("roll")) : node(cell));
                    }
                    sb.append(line).append('\n');
                }
                return sb.toString().strip();
            }
            case "quote" -> {
                String body = node(n.path("entries"));
                String by = n.path("by").asString(null);
                return "\"" + body + "\"" + (by == null ? "" : " - " + inline(by));
            }
            case "image", "gallery" -> {
                return "";
            }
            default -> {
                String body = node(n.path("entries"));
                if (body.isBlank() && n.has("entry")) body = node(n.path("entry"));
                return name == null || name.isBlank() ? body : inline(name) + ". " + body;
            }
        }
    }

    private static String rollRange(JsonNode roll) {
        if (roll.has("exact")) return roll.path("exact").asString();
        return roll.path("min").asString() + "-" + roll.path("max").asString();
    }

    /** "{@spell fireball|xphb}" -> "fireball"; "{@dice 1d6}" -> "1d6"; "{@b negrito}" -> "negrito". */
    static String inline(String text) {
        String current = text;
        for (int guard = 0; guard < 10 && current.contains("{@"); guard++) {
            Matcher m = TAG.matcher(current);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                String tag = m.group(1) != null ? m.group(1) : m.group(3);
                String content = m.group(2) == null ? "" : m.group(2);
                String shown = switch (tag.toLowerCase(Locale.ROOT)) {
                    case "h" -> "Hit: ";
                    case "hom" -> "Hit or Miss: ";
                    case "atk", "atkr" -> attackName(content) + ":";
                    case "actsave" -> abilityName(content.trim()) + " Saving Throw:";
                    case "actsavefail" -> content.isBlank() ? "Failure:" : "Failure by " + content.trim() + " or More:";
                    case "actsavefailby" -> "Failure by " + content.trim() + " or More:";
                    case "actsavesuccess" -> "Success:";
                    case "actsavesuccessorfail" -> "Failure or Success:";
                    case "acttrigger" -> "Trigger:";
                    case "actresponse" -> "Response:";
                    case "recharge" -> "(Recharge " + (content.isBlank() ? "6" : content.trim() + "-6") + ")";
                    case "dc" -> "DC " + content.trim();
                    case "hit" -> (content.trim().startsWith("-") ? "" : "+") + content.trim();
                    case "chance" -> content.split(PIPE)[0].trim() + "%";
                    case "scaledamage", "scaledice" -> {
                        String[] parts = content.split(PIPE);
                        yield (parts.length >= 3 ? parts[2] : parts[0]).trim();
                    }
                    default -> {
                        String[] parts = content.split(PIPE);
                        yield parts.length >= 3 && !parts[2].isBlank() ? parts[2] : parts[0];
                    }
                };
                m.appendReplacement(out, Matcher.quoteReplacement(shown));
            }
            m.appendTail(out);
            current = out.toString();
        }
        return current;
    }

    /** "{@atkr m}" -> "Melee Attack Roll"; "m,r" -> "Melee or Ranged Attack Roll"; {@atk mw} (2014) -> "Melee Weapon Attack". */
    private static String attackName(String content) {
        List<String> names = new ArrayList<>();
        boolean weaponOrSpell = false;
        for (String code : content.split(",")) {
            String c = code.trim();
            weaponOrSpell |= c.length() == 2;
            names.add(switch (c) {
                case "m" -> "Melee";
                case "r" -> "Ranged";
                case "mw" -> "Melee Weapon";
                case "rw" -> "Ranged Weapon";
                case "ms" -> "Melee Spell";
                case "rs" -> "Ranged Spell";
                default -> c;
            });
        }
        return String.join(" or ", names) + (weaponOrSpell ? " Attack" : " Attack Roll");
    }

    private static String abilityName(String code) {
        List<String> names = new ArrayList<>();
        for (String c : code.split(",")) {
            names.add(switch (c.trim()) {
                case "str" -> "Strength";
                case "dex" -> "Dexterity";
                case "con" -> "Constitution";
                case "int" -> "Intelligence";
                case "wis" -> "Wisdom";
                case "cha" -> "Charisma";
                default -> c.trim();
            });
        }
        return String.join(" or ", names);
    }

    /** Campos simples tipo {"str": 2, "con": 1} -> "STR +2, CON +1". */
    public static String abilityBonuses(JsonNode ability) {
        if (ability == null || !ability.isArray() || ability.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode option : ability) {
            StringBuilder one = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = option.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                if (f.getValue().isNumber()) {
                    if (!one.isEmpty()) one.append(", ");
                    int v = f.getValue().asInt();
                    one.append(f.getKey().toUpperCase()).append(v >= 0 ? " +" : " ").append(v);
                } else if (f.getKey().equals("choose")) {
                    JsonNode choose = f.getValue();
                    JsonNode from = choose.has("weighted") ? choose.path("weighted").path("from") : choose.path("from");
                    StringBuilder list = new StringBuilder();
                    for (JsonNode a : from) {
                        if (!list.isEmpty()) list.append(", ");
                        list.append(a.asString().toUpperCase());
                    }
                    if (!one.isEmpty()) one.append(", ");
                    one.append("choose from ").append(list);
                    if (choose.has("weighted")) {
                        one.append(" (").append(choose.path("weighted").path("weights").toString()).append(")");
                    } else if (choose.has("count")) {
                        one.append(" (").append(choose.path("count").asInt()).append(")");
                    }
                }
            }
            if (!one.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" or ");
                sb.append(one);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}

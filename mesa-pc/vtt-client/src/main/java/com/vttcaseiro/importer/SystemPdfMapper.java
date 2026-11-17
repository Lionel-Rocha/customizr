package com.vttcaseiro.importer;

import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.GameSystem;
import com.vttcaseiro.character.Resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ficha em PDF preenchivel de Tormenta20 ou Ordem Paranormal. Reconhece o sistema pelos nomes dos
 * campos (AGI/PRE/VIG/NEX/SAN/PE = Ordem; PM + Divindade/Ladinagem/Misticismo/Nobreza... = Tormenta20)
 * e preenche a ficha daquele sistema. Os nomes variam de ficha pra ficha, entao cada dado tem apelidos.
 * Treino das pericias sai do numero: Ordem = bonus/5 (5, 10, 15); Tormenta20 = bonus - metade do nivel
 * - atributo >= 2.
 */
public final class SystemPdfMapper {
    private SystemPdfMapper() {
    }

    /** Qual sistema estes campos parecem ser (null = nenhum dos dois: deixa pro leitor de D&D). */
    public static GameSystem detect(Map<String, String> normalizedFields) {
        int op = 0, t20 = 0;
        for (String k : normalizedFields.keySet()) {
            if (k.matches("(agi|agilidade|pre|presenca|vig|vigor|nex|san|sanidade|pe|pontosdeesforco|esforco|trilha|patente|ocultismo|afinidade)")) op++;
            if (k.matches("(pm|pontosdemana|mana|divindade|ladinagem|misticismo|nobreza|guerra|jogatina|cavalgar|raca|tormenta20|t20)")) t20++;
        }
        if (op >= 3 && op > t20) return GameSystem.ORDEM;
        if (t20 >= 3) return GameSystem.TORMENTA20;
        return null;
    }

    public static SheetImport.Result map(Map<String, String> raw, String format) {
        Map<String, String> f = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue().strip();
            if (!v.isEmpty() && !v.equalsIgnoreCase("off")) f.putIfAbsent(Json.norm(e.getKey()), v);
        }
        GameSystem gs = detect(f);
        if (gs == null) return null;
        List<String> notes = new ArrayList<>();
        CharacterSheet s = new CharacterSheet("Personagem importado");
        gs.initialize(s);
        String name = first(f, "nome", "nomedopersonagem", "personagem", "charactername", "name");
        if (name != null) s.setName(name);
        s.setPlayerName(first(f, "jogador", "nomedojogador", "player", "playername"));

        // atributos
        String[][] attrAliases = gs == GameSystem.ORDEM
                ? new String[][]{{"AGI", "agi", "agilidade"}, {"FOR", "for", "forca"}, {"INT", "int", "intelecto"},
                    {"PRE", "pre", "presenca"}, {"VIG", "vig", "vigor"}}
                : new String[][]{{"FOR", "for", "forca"}, {"DES", "des", "destreza"}, {"CON", "con", "constituicao"},
                    {"INT", "int", "inteligencia"}, {"SAB", "sab", "sabedoria"}, {"CAR", "car", "carisma"}};
        int found = 0;
        for (String[] a : attrAliases) {
            for (int i = 1; i < a.length; i++) {
                Integer v = PdfFieldMapper.number(f.get(a[i]));
                if (v != null) {
                    s.getCustomAttributes().put(a[0], v);
                    found++;
                    break;
                }
            }
        }

        // campos do personagem
        for (String field : gs.fields()) {
            String v = first(f, Json.norm(field), Json.norm(field) + "1");
            if (v != null) s.getCustomFields().put(field, v);
        }
        String cls = first(f, "classe", "class");
        if (cls != null) s.getCustomFields().put("Classe", cls);
        if (gs == GameSystem.TORMENTA20) {
            Integer lvl = PdfFieldMapper.number(first(f, "nivel", "nv", "level", "niveldepersonagem"));
            if (lvl != null) s.setLevel(lvl);
        } else {
            String nex = first(f, "nex");
            if (nex != null) s.getCustomFields().put("NEX", nex.endsWith("%") ? nex : nex + "%");
        }

        // PV, recursos, Defesa
        Integer pvMax = PdfFieldMapper.number(first(f, "pvmax", "pvmaximo", "pvtotal", "pontosdevidamaximos", "vidamaxima", "pv"));
        Integer pv = PdfFieldMapper.number(first(f, "pvatual", "pvatuais", "vidaatual"));
        if (pvMax != null && pvMax > 0) {
            s.setHpMax(pvMax);
            s.setHpCurrent(pv != null ? pv : pvMax);
        }
        for (String r : gs.resources()) {
            String n = Json.norm(r);
            String[] maxKeys = r.equals("SAN") ? new String[]{"sanmax", "sanidademax", "sanidademaxima", "santotal", "san", "sanidade"}
                    : r.equals("PE") ? new String[]{"pemax", "petotal", "pontosdeesforcomax", "pe", "esforco"}
                    : new String[]{"pmmax", "pmtotal", "manamax", "pm", "mana", "pontosdemana"};
            Integer max = PdfFieldMapper.number(first(f, maxKeys));
            Integer cur = PdfFieldMapper.number(first(f, n + "atual", n + "atuais"));
            if (max != null && max > 0) {
                Resource res = s.findResource(r).orElseGet(() -> {
                    Resource nr = new Resource(r, 1, Resource.NEVER);
                    s.getResources().add(nr);
                    return nr;
                });
                res.setMax(max);
                res.setCurrent(cur != null ? cur : max);
            }
        }
        Integer def = PdfFieldMapper.number(first(f, "defesa", "def", "defesatotal"));
        if (def != null && def > 0) s.setArmorClass(def);
        else notes.add("Não achei a Defesa no PDF; preencha na ficha.");

        // pericias: treino pelo numero (ou caixa marcada "<pericia>treinada")
        int trained = 0;
        for (GameSystem.SkillDef d : gs.skills()) {
            String key = Json.norm(d.name());
            String box = first(f, key + "treinada", key + "treino", key + "tr", "treino" + key);
            Integer total = PdfFieldMapper.number(first(f, key, key + "total", key + "bonus"));
            int level = 0;
            if (gs == GameSystem.ORDEM) {
                if (total != null && total > 0 && total % 5 == 0) level = Math.min(3, total / 5);
                else if (box != null && PdfFieldMapper.checked(box)) level = 1;
            } else {
                if (total != null) {
                    int extra = total - s.getLevel() / 2 - gs.attrModifier(s, d.attr());
                    if (extra >= 2) level = 1;
                } else if (box != null && PdfFieldMapper.checked(box)) {
                    level = 1;
                }
            }
            if (level > 0) {
                s.getCustomSkills().put(d.name(), level);
                trained++;
            }
        }

        // ataques e poderes
        for (int i = 1; i <= 6; i++) {
            String n = first(f, "arma" + i, "ataque" + i, "armanome" + i, "nomearma" + i, i == 1 ? "arma" : "x");
            if (n == null) continue;
            String test = first(f, "teste" + i, "ataqueteste" + i, "armateste" + i, "bonusataque" + i);
            String dmg = first(f, "dano" + i, "armadano" + i, "danoarma" + i);
            String crit = first(f, "critico" + i, "armacritico" + i);
            s.addAttack(new Attack(n, test == null ? (gs == GameSystem.ORDEM ? "Luta" : "Luta") : test,
                    dmg == null ? "" : dmg, crit == null ? "" : crit));
        }
        String powers = first(f, "poderes", "habilidades", "habilidadesepoderes", "poderesehabilidades", "rituais", "magias");
        if (powers != null) {
            for (String line : powers.split("\\n+")) {
                if (!line.isBlank()) s.addTrait(new CharacterTrait(line.strip(), "", "feat"));
            }
        }
        String inv = first(f, "inventario", "equipamento", "itens");
        if (inv != null) s.getCustomFields().put("Inventário", inv);
        String story = first(f, "historia", "historico", "anotacoes", "descricao");
        if (story != null) s.setBackstory(story);

        if (found < 3) return null;
        notes.add(trained + " perícia(s) treinada(s) reconhecida(s) pelo número. Confira o treino na aba Perícias.");
        return new SheetImport.Result(s, format + ", " + gs.name(), notes);
    }

    private static String first(Map<String, String> f, String... keys) {
        for (String k : keys) {
            String v = f.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

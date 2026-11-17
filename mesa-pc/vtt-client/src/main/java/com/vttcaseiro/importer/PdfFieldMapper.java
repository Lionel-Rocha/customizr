package com.vttcaseiro.importer;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.inventory.Currency;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma os campos de um PDF preenchivel (nome do campo -> valor) numa ficha.
 * Os nomes variam de ficha pra ficha (oficial de 5e, D&amp;D Beyond, traducoes),
 * entao cada dado tem uma lista de apelidos comparados sem acento/espaco/caixa.
 * Proficiencia em pericia/salvaguarda e deduzida do proprio numero (bonus - mod
 * = proficiencia; o dobro = especialista), o que funciona em qualquer ficha.
 */
public final class PdfFieldMapper {
    private PdfFieldMapper() {
    }

    private static final Map<Ability, String[]> ABILITY_ALIASES = new EnumMap<>(Map.of(
            Ability.STRENGTH, new String[]{"str", "strength", "forca", "for", "strscore"},
            Ability.DEXTERITY, new String[]{"dex", "dexterity", "destreza", "des", "dexscore"},
            Ability.CONSTITUTION, new String[]{"con", "constitution", "constituicao", "conscore"},
            Ability.INTELLIGENCE, new String[]{"int", "intelligence", "inteligencia", "intscore"},
            Ability.WISDOM, new String[]{"wis", "wisdom", "sabedoria", "sab", "wisscore"},
            Ability.CHARISMA, new String[]{"cha", "charisma", "carisma", "car", "chascore"}));

    /** Caixas de proficiencia da ficha oficial de 5e (2014), que tem nomes genericos. */
    private static final Map<String, Object> WOTC_CHECKBOXES = new HashMap<>();

    static {
        Object[] saves = {Ability.STRENGTH, Ability.DEXTERITY, Ability.CONSTITUTION, Ability.INTELLIGENCE,
                Ability.WISDOM, Ability.CHARISMA};
        int[] saveBoxes = {11, 18, 19, 20, 21, 22};
        for (int i = 0; i < 6; i++) WOTC_CHECKBOXES.put("checkbox" + saveBoxes[i], saves[i]);
        Skill[] skills = {Skill.ACROBATICS, Skill.ANIMAL_HANDLING, Skill.ARCANA, Skill.ATHLETICS, Skill.DECEPTION,
                Skill.HISTORY, Skill.INSIGHT, Skill.INTIMIDATION, Skill.INVESTIGATION, Skill.MEDICINE, Skill.NATURE,
                Skill.PERCEPTION, Skill.PERFORMANCE, Skill.PERSUASION, Skill.RELIGION, Skill.SLEIGHT_OF_HAND,
                Skill.STEALTH, Skill.SURVIVAL};
        for (int i = 0; i < skills.length; i++) WOTC_CHECKBOXES.put("checkbox" + (23 + i), skills[i]);
    }

    private static final Pattern INT = Pattern.compile("[+-]?\\s*\\d+");

    /** Le os campos. Devolve null se quase nada foi reconhecido (pra cair na leitura por texto). */
    public static SheetImport.Result map(Map<String, String> rawFields, String format) {
        Map<String, String> f = new LinkedHashMap<>();
        for (var e : rawFields.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue().strip();
            if (!v.isEmpty()) f.putIfAbsent(Json.norm(e.getKey()), v);
        }
        List<String> notes = new ArrayList<>();
        int recognized = 0;
        CharacterSheet s = new CharacterSheet("Personagem importado");

        String name = first(f, "charactername", "charactername2", "name", "nome", "nomedopersonagem", "personagem");
        if (name != null) { s.setName(name); recognized++; }
        s.setPlayerName(first(f, "playername", "player", "jogador", "nomedojogador"));
        String classLevel = first(f, "classlevel", "classandlevel", "classeenivel", "classenivel");
        String cls = first(f, "class", "classe", "class1");
        Integer lvl = number(first(f, "level", "nivel", "lvl", "characterlevel"));
        if (classLevel != null) {
            Matcher m = Pattern.compile("\\d+").matcher(classLevel);
            int sum = 0;
            while (m.find()) sum += Integer.parseInt(m.group());
            String c = classLevel.replaceAll("\\d+", "").replaceAll("\\s*/\\s*", " / ").replaceAll("\\s+", " ").strip();
            if (!c.isEmpty()) s.setClassName(c);
            if (sum > 0) s.setLevel(sum);
            recognized++;
        }
        if (cls != null) { s.setClassName(cls); recognized++; }
        if (lvl != null) s.setLevel(lvl);
        s.setSubclass(first(f, "subclass", "subclasse", "archetype", "arquetipo"));
        s.setBackground(first(f, "background", "antecedente", "historico"));
        s.setSpecies(first(f, "race", "species", "especie", "raca", "racespecies"));
        s.setAlignment(first(f, "alignment", "tendencia", "alinhamento"));
        Integer xp = number(first(f, "xp", "experiencepoints", "experiencia", "pontosdeexperiencia"));
        if (xp != null) s.setExperiencePoints(xp);

        Map<Ability, Integer> mods = new EnumMap<>(Ability.class);
        for (var e : ABILITY_ALIASES.entrySet()) {
            Integer score = null;
            for (String alias : e.getValue()) {
                Integer v = number(f.get(alias));
                if (v != null && v >= 1 && v <= 30) { score = v; break; }
            }
            if (score != null) {
                Json.setAbility(s, e.getKey(), score);
                recognized++;
            }
            mods.put(e.getKey(), CharacterSheet.abilityModifier(s.abilityScore(e.getKey())));
        }

        Integer ac = number(first(f, "ac", "armorclass", "ca", "classedearmadura", "armor"));
        if (ac != null && ac > 0 && ac < 40) { s.setArmorClass(ac); recognized++; }
        Integer hpMax = number(first(f, "hpmax", "maxhp", "hitpointmaximum", "pvmax", "pvmaximo", "pontosdevidamaximos",
                "pvmaximos", "maxhitpoints"));
        if (hpMax != null && hpMax > 0) { s.setHpMax(hpMax); recognized++; }
        Integer hp = number(first(f, "hpcurrent", "currenthp", "currenthitpoints", "pvatuais", "pvatual", "hp", "pv"));
        if (hp != null) s.setHpCurrent(hp);
        else if (s.getHpMax() != null) s.setHpCurrent(s.getHpMax());
        Integer temp = number(first(f, "hptemp", "temphp", "temporaryhitpoints", "pvtemporarios"));
        if (temp != null) s.setHpTemp(temp);
        String speed = first(f, "speed", "deslocamento", "movimento", "walkingspeed");
        if (speed != null) s.setSpeed(speedText(speed));
        String hd = first(f, "hd", "hdtotal", "hitdice", "dadosdevida", "totalhitdice");
        if (hd != null) {
            Matcher m = Pattern.compile("d(\\d+)").matcher(hd.toLowerCase());
            if (m.find()) s.setHitDie("d" + m.group(1));
        }
        int prof = s.proficiencyBonus();
        Integer printedProf = number(first(f, "profbonus", "proficiencybonus", "bonusdeproficiencia", "proficiencia"));
        if (printedProf != null && printedProf >= 2 && printedProf <= 6) prof = printedProf;
        Integer init = number(first(f, "initiative", "init", "iniciativa"));
        if (init != null) s.setInitiativeBonus(init - mods.get(Ability.DEXTERITY));

        // Salvaguardas: "ST Strength" = +5 -> se 5 - mod == prof, e proficiente.
        for (Ability a : Ability.values()) {
            String full = Json.norm(a.name());
            Integer v = number(first(f, "st" + full, full + "save", full + "savingthrow", "save" + full,
                    a.abbreviationPt().toLowerCase() + "salv", "salvaguarda" + ptName(a), "resistencia" + ptName(a),
                    full.substring(0, 3) + "save", full.substring(0, 3) + "st"));
            if (v != null && v - mods.get(a) >= prof && prof > 0) s.setSavingThrowProficient(a, true);
        }
        // Pericias: pelo valor do campo.
        for (var e : f.entrySet()) {
            Skill sk = skillFromField(e.getKey());
            if (sk == null) continue;
            if (!e.getValue().matches("[+-]?\\s*\\d{1,2}")) continue; // so campo numerico (nao "historia" = texto)
            Integer v = number(e.getValue());
            if (v == null) continue;
            int diff = v - mods.get(sk.getAbility());
            if (diff >= prof * 2 && prof > 0) s.setSkillExpertise(sk, true);
            else if (diff >= prof && prof > 0) s.setSkillProficient(sk, true);
        }
        // Caixas da ficha oficial (quando marcadas).
        for (var e : f.entrySet()) {
            Object target = WOTC_CHECKBOXES.get(e.getKey());
            if (target == null || !checked(e.getValue())) continue;
            if (target instanceof Ability a) s.setSavingThrowProficient(a, true);
            if (target instanceof Skill sk && !s.isSkillProficient(sk)) s.setSkillProficient(sk, true);
        }

        // Ataques: "Wpn Name"/"Wpn1 AtkBonus"/"Wpn1 Damage" (oficial) e variacoes.
        for (int i = 1; i <= 6; i++) {
            String n = first(f, i == 1 ? "wpnname" : "wpnname" + i, "weapon" + i, "weapon" + i + "name",
                    "attack" + i, "attack" + i + "name", "arma" + i, "ataque" + i);
            if (n == null) continue;
            String bonus = first(f, "wpn" + i + "atkbonus", "weapon" + i + "bonus", "attack" + i + "bonus",
                    "weapon" + i + "atk", "atk" + i + "bonus", "bonusataque" + i);
            String dmg = first(f, "wpn" + i + "damage", "weapon" + i + "damage", "attack" + i + "damage",
                    "dano" + i, "damage" + i);
            s.addAttack(new Attack(n, bonus == null ? "" : bonus.replaceAll("\\s+", ""), dmg == null ? "" : dmg, ""));
        }

        // Dinheiro.
        String[][] coins = {{"cp", "pc"}, {"sp", "pp"}, {"ep", "pe"}, {"gp", "po"}, {"pp", "pl"}};
        Currency[] cur = {Currency.COPPER, Currency.SILVER, Currency.ELECTRUM, Currency.GOLD, Currency.PLATINUM};
        for (int i = 0; i < coins.length; i++) {
            Integer v = number(f.get(coins[i][0]));
            if (v != null && v > 0) s.getInventory().getMoney().add(cur[i], v);
        }

        // Textos.
        s.setPersonalityTraits(first(f, "personalitytraits", "personality", "tracosdepersonalidade", "personalidade"));
        s.setIdeals(first(f, "ideals", "ideais"));
        s.setBonds(first(f, "bonds", "vinculos", "ligacoes"));
        s.setFlaws(first(f, "flaws", "defeitos", "fraquezas"));
        s.setBackstory(first(f, "backstory", "characterbackstory", "historia", "historiadopersonagem"));
        s.setAge(first(f, "age", "idade"));
        s.setHeight(first(f, "height", "altura"));
        s.setWeight(first(f, "weight", "peso"));
        s.setEyes(first(f, "eyes", "olhos"));
        s.setSkin(first(f, "skin", "pele"));
        s.setHair(first(f, "hair", "cabelo", "cabelos"));
        s.setAppearance(first(f, "appearance", "aparencia"));
        String langs = first(f, "proficiencieslang", "languages", "idiomas", "otherproficiencieslanguages");
        if (langs != null) s.setToolProficiencies(langs);
        String features = first(f, "featuresandtraits", "features", "featurestraits", "caracteristicas",
                "caracteristicasetracos", "featurestraits1");
        if (features != null) s.addTrait(new CharacterTrait("Características (da ficha PDF)", features, "class"));
        String equipment = first(f, "equipment", "equipamento", "inventory", "inventario");
        if (equipment != null) {
            for (String line : equipment.split("[\\n,;]+")) {
                String item = line.strip();
                if (item.isEmpty() || item.length() > 80) continue;
                Matcher m = Pattern.compile("^(\\d+)\\s*x?\\s+(.+)$").matcher(item);
                if (m.matches()) s.getInventory().addItem(m.group(2), Integer.parseInt(m.group(1)), 0, 0);
                else s.getInventory().addItem(item, 1, 0, 0);
            }
        }
        List<String> spellNames = new ArrayList<>();
        for (var e : f.entrySet()) {
            if (e.getKey().startsWith("spells") && e.getKey().length() > 6 && !e.getValue().matches("[\\d\\s]+")) {
                spellNames.add(e.getValue());
            }
        }
        if (!spellNames.isEmpty()) {
            s.addTrait(new CharacterTrait("Magias (da ficha PDF)", String.join("\n", spellNames), "class"));
            notes.add("As magias do PDF foram listadas numa característica; adicione-as na aba de magias pra rolar.");
        }

        if (recognized < 3) return null;
        if (s.getArmorClass() == null) notes.add("Não achei a CA no PDF; preencha na ficha.");
        if (s.getHpMax() == null) notes.add("Não achei o PV máximo no PDF; preencha na ficha.");
        return new SheetImport.Result(s, format, notes);
    }

    /** Ficha "achatada" (sem campos): tenta achar nome e atributos no texto. Melhor que nada; o jogador confere. */
    public static SheetImport.Result fromText(String text) {
        if (text == null || text.isBlank()) return null;
        CharacterSheet s = new CharacterSheet("Personagem importado");
        int found = 0;
        for (var e : ABILITY_ALIASES.entrySet()) {
            for (String alias : e.getValue()) {
                if (alias.length() < 3) continue;
                Matcher m = Pattern.compile("(?im)\\b" + Pattern.quote(alias) + "[a-z]*\\b[^\\d\\n]{0,12}(\\d{1,2})\\b")
                        .matcher(stripAccents(text));
                if (m.find()) {
                    int v = Integer.parseInt(m.group(1));
                    if (v >= 1 && v <= 30) { Json.setAbility(s, e.getKey(), v); found++; break; }
                }
            }
        }
        Matcher name = Pattern.compile("(?im)^(?:character name|nome do personagem|nome)\\s*[:\\-]?\\s*(.+)$")
                .matcher(text);
        if (name.find()) s.setName(name.group(1).strip());
        if (found < 3) return null;
        List<String> notes = new ArrayList<>();
        notes.add("Este PDF não tem campos preenchiveis; li só os atributos pelo texto. Confira tudo.");
        return new SheetImport.Result(s, "PDF (texto)", notes);
    }

    private static String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String ptName(Ability a) {
        return switch (a) {
            case STRENGTH -> "forca";
            case DEXTERITY -> "destreza";
            case CONSTITUTION -> "constituicao";
            case INTELLIGENCE -> "inteligencia";
            case WISDOM -> "sabedoria";
            case CHARISMA -> "carisma";
        };
    }

    private static Skill skillFromField(String key) {
        String k = key.replaceAll("(mod|bonus|total|value)$", "");
        Skill direct = Json.skill(k);
        if (direct != null) return direct;
        // "SlightofHand", "Animal" (campos da ficha oficial com nome cortado)
        if (k.equals("animal")) return Skill.ANIMAL_HANDLING;
        return null;
    }

    static boolean checked(String v) {
        String n = v.strip().toLowerCase();
        return n.equals("yes") || n.equals("on") || n.equals("true") || n.equals("1") || n.equals("x") || n.equals("sim");
    }

    private static String first(Map<String, String> f, String... keys) {
        for (String k : keys) {
            String v = f.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    static Integer number(String v) {
        if (v == null) return null;
        Matcher m = INT.matcher(v);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group().replaceAll("\\s+", "").replace("+", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String speedText(String raw) {
        String t = raw.strip();
        if (t.toLowerCase().matches(".*\\d\\s*(m|metros?)\\b.*")) return t;
        Integer n = number(t);
        if (n == null) return t;
        return n > 20 || t.toLowerCase().contains("ft") || t.toLowerCase().contains("pes") ? Json.feetToMeters(n) : n + " m";
    }
}

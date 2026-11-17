package com.vttcaseiro.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Personagem do D&amp;D Beyond (o JSON do "character-service", que extensoes e sites de exportacao baixam). */
final class DdbSheetReader {
    private DdbSheetReader() {
    }

    private static final Ability[] BY_ID = {Ability.STRENGTH, Ability.DEXTERITY, Ability.CONSTITUTION,
            Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA};
    private static final String[] SCORE_SUBTYPES = {"strength-score", "dexterity-score", "constitution-score",
            "intelligence-score", "wisdom-score", "charisma-score"};
    private static final String[] ALIGNMENTS = {"", "Leal e Bom", "Neutro e Bom", "Caotico e Bom", "Leal e Neutro",
            "Neutro", "Caotico e Neutro", "Leal e Mau", "Neutro e Mau", "Caotico e Mau"};

    static SheetImport.Result read(JsonObject d) {
        List<String> notes = new ArrayList<>();
        CharacterSheet s = new CharacterSheet(FoundrySheetReader.nameOr(Json.str(d, "name"), "Personagem importado"));
        List<JsonObject> modifiers = allModifiers(d);

        int[] scores = {10, 10, 10, 10, 10, 10};
        for (JsonElement e : Json.arr(d, "stats")) readStat(e, scores, false);
        for (JsonElement e : Json.arr(d, "bonusStats")) readStat(e, scores, true);
        for (JsonObject m : modifiers) {
            if (!"bonus".equals(Json.str(m, "type"))) continue;
            String sub = Json.str(m, "subType");
            for (int i = 0; i < 6; i++) {
                if (SCORE_SUBTYPES[i].equals(sub)) scores[i] += Json.intOr(m, "value", Json.intOr(m, "fixedValue", 0));
            }
        }
        for (JsonElement e : Json.arr(d, "overrideStats")) {
            if (!e.isJsonObject()) continue;
            Integer id = Json.integer(e.getAsJsonObject(), "id");
            Integer v = Json.integer(e.getAsJsonObject(), "value");
            if (id != null && v != null && id >= 1 && id <= 6) scores[id - 1] = v;
        }
        for (int i = 0; i < 6; i++) Json.setAbility(s, BY_ID[i], scores[i]);

        // Classes, nivel, dado de vida.
        int level = 0;
        List<String> classNames = new ArrayList<>();
        for (JsonElement e : Json.arr(d, "classes")) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            int lv = Json.intOr(c, "level", 1);
            level += lv;
            String name = Json.str(c, "definition.name");
            if (name != null) classNames.add(classNames.isEmpty() ? name : name + " " + lv);
            if (classNames.size() == 1) {
                Integer hd = Json.integer(c, "definition.hitDice");
                if (hd != null) s.setHitDie("d" + hd);
                String sub = Json.str(c, "subclassDefinition.name");
                if (sub != null) s.setSubclass(sub);
            }
            for (JsonElement f : Json.arr(c, "classFeatures")) {
                if (!f.isJsonObject()) continue;
                JsonObject def = Json.obj(f.getAsJsonObject(), "definition");
                Integer req = Json.integer(def, "requiredLevel");
                String fn = Json.str(def, "name");
                if (fn != null && (req == null || req <= lv)) {
                    String desc = Json.stripHtml(Json.str(def, "description"));
                    s.addTrait(new CharacterTrait(fn, desc == null ? "" : desc, "class"));
                }
            }
        }
        if (level > 0) s.setLevel(level);
        if (!classNames.isEmpty()) s.setClassName(String.join(" / ", classNames));

        String race = Json.str(d, "race.fullName");
        if (race == null) race = Json.str(d, "race.baseName");
        s.setSpecies(race);
        for (JsonElement t : Json.arr(d, "race.racialTraits")) {
            if (!t.isJsonObject()) continue;
            String n = Json.str(t.getAsJsonObject(), "definition.name");
            if (n == null || n.matches("(?i)ability score.*|age|alignment|size|speed|languages")) continue;
            String desc = Json.stripHtml(Json.str(t.getAsJsonObject(), "definition.description"));
            s.addTrait(new CharacterTrait(n, desc == null ? "" : desc, "species"));
        }
        s.setBackground(Json.str(d, "background.definition.name"));
        Integer align = Json.integer(d, "alignmentId");
        if (align != null && align > 0 && align < ALIGNMENTS.length) s.setAlignment(ALIGNMENTS[align]);
        for (JsonElement f : Json.arr(d, "feats")) {
            if (!f.isJsonObject()) continue;
            String n = Json.str(f.getAsJsonObject(), "definition.name");
            String desc = Json.stripHtml(Json.str(f.getAsJsonObject(), "definition.description"));
            if (n != null) s.addTrait(new CharacterTrait(n, desc == null ? "" : desc, "feat"));
        }

        // PV: base + CON por nivel + bonus (ou o valor fixado pelo jogador).
        int conMod = CharacterSheet.abilityModifier(scores[2]);
        Integer override = Json.integer(d, "overrideHitPoints");
        int max = override != null ? override
                : Json.intOr(d, "baseHitPoints", 0) + Json.intOr(d, "bonusHitPoints", 0) + conMod * Math.max(1, level);
        if (max > 0) {
            s.setHpMax(max);
            s.setHpCurrent(Math.max(0, max - Json.intOr(d, "removedHitPoints", 0)));
        }
        s.setHpTemp(Json.intOr(d, "temporaryHitPoints", 0));
        notes.add("A CA não vem pronta no JSON do D&D Beyond (depende da armadura); preencha a CA na ficha.");

        // Proficiencias e expertise.
        Set<String> langs = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (JsonObject m : modifiers) {
            String type = Json.str(m, "type");
            String sub = Json.str(m, "subType");
            if (type == null || sub == null) continue;
            if ("language".equals(type)) {
                String n = Json.str(m, "friendlySubtypeName");
                if (n != null) langs.add(n);
                continue;
            }
            if (sub.endsWith("-saving-throws") && "proficiency".equals(type)) {
                for (int i = 0; i < 6; i++) {
                    if (sub.startsWith(SCORE_SUBTYPES[i].replace("-score", ""))) {
                        s.setSavingThrowProficient(BY_ID[i], true);
                    }
                }
                continue;
            }
            Skill sk = Json.skill(sub);
            if (sk == null) continue;
            if ("expertise".equals(type)) s.setSkillExpertise(sk, true);
            else if ("proficiency".equals(type) && seen.add(sk.name())) s.setSkillProficient(sk, true);
        }
        s.getLanguages().addAll(langs);

        // Dinheiro e inventario.
        for (String c : new String[]{"cp", "sp", "ep", "gp", "pp"}) {
            Integer v = Json.integer(d, "currencies." + c);
            if (v != null && v > 0) s.getInventory().getMoney().add(FoundrySheetReader.currency(c), v);
        }
        for (JsonElement e : Json.arr(d, "inventory")) {
            if (!e.isJsonObject()) continue;
            JsonObject it = e.getAsJsonObject();
            String n = Json.str(it, "definition.name");
            if (n == null) continue;
            Double w = Json.num(it, "definition.weight");
            Double cost = Json.num(it, "definition.cost");
            s.getInventory().addItem(n, Math.max(1, Json.intOr(it, "quantity", 1)), w == null ? 0 : w,
                    cost == null ? 0 : Math.round(cost * 100));
        }

        // Magias (por classe + de especie/talento/item).
        Set<String> spellNames = new HashSet<>();
        for (JsonElement cs : Json.arr(d, "classSpells")) {
            if (cs.isJsonObject()) for (JsonElement sp : Json.arr(cs.getAsJsonObject(), "spells")) addSpell(s, sp, spellNames);
        }
        JsonObject spells = Json.obj(d, "spells");
        if (spells != null) {
            for (String k : spells.keySet()) for (JsonElement sp : Json.arr(spells, k)) addSpell(s, sp, spellNames);
        }

        s.setPersonalityTraits(Json.str(d, "traits.personalityTraits"));
        s.setIdeals(Json.str(d, "traits.ideals"));
        s.setBonds(Json.str(d, "traits.bonds"));
        s.setFlaws(Json.str(d, "traits.flaws"));
        s.setAppearance(Json.str(d, "traits.appearance"));
        s.setBackstory(Json.str(d, "notes.backstory"));
        s.setAge(Json.str(d, "age"));
        s.setHair(Json.str(d, "hair"));
        s.setEyes(Json.str(d, "eyes"));
        s.setSkin(Json.str(d, "skin"));
        s.setHeight(Json.str(d, "height"));
        s.setWeight(Json.str(d, "weight"));
        s.setExperiencePoints(Json.intOr(d, "currentXp", 0));
        return new SheetImport.Result(s, "D&D Beyond (JSON)", notes);
    }

    private static void readStat(JsonElement e, int[] scores, boolean add) {
        if (!e.isJsonObject()) return;
        Integer id = Json.integer(e.getAsJsonObject(), "id");
        Integer v = Json.integer(e.getAsJsonObject(), "value");
        if (id == null || v == null || id < 1 || id > 6) return;
        scores[id - 1] = add ? scores[id - 1] + v : v;
    }

    private static List<JsonObject> allModifiers(JsonObject d) {
        List<JsonObject> out = new ArrayList<>();
        JsonObject mods = Json.obj(d, "modifiers");
        if (mods == null) return out;
        for (String k : mods.keySet()) {
            for (JsonElement m : Json.arr(mods, k)) if (m.isJsonObject()) out.add(m.getAsJsonObject());
        }
        return out;
    }

    private static void addSpell(CharacterSheet s, JsonElement sp, Set<String> seen) {
        if (!sp.isJsonObject()) return;
        String n = Json.str(sp.getAsJsonObject(), "definition.name");
        if (n == null || !seen.add(n)) return;
        PreparedSpell p = new PreparedSpell(Json.intOr(sp.getAsJsonObject(), "definition.level", 0), n);
        p.setConcentration(Boolean.TRUE.equals(bool(sp.getAsJsonObject(), "definition.concentration")));
        p.setRitual(Boolean.TRUE.equals(bool(sp.getAsJsonObject(), "definition.ritual")));
        s.getSpells().add(p);
    }

    private static Boolean bool(JsonObject o, String path) {
        JsonElement e = Json.at(o, path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
    }
}

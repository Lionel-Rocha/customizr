package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.inventory.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ator do Foundry VTT, sistema dnd5e ("Export Data" na ficha). Funciona com v10+ ("system") e antigos ("data"). */
final class FoundrySheetReader {
    private FoundrySheetReader() {
    }

    private static final Map<String, Ability> ABILITIES = Map.of(
            "str", Ability.STRENGTH, "dex", Ability.DEXTERITY, "con", Ability.CONSTITUTION,
            "int", Ability.INTELLIGENCE, "wis", Ability.WISDOM, "cha", Ability.CHARISMA);

    private static final Map<String, Skill> SKILLS = Map.ofEntries(
            Map.entry("acr", Skill.ACROBATICS), Map.entry("ani", Skill.ANIMAL_HANDLING),
            Map.entry("arc", Skill.ARCANA), Map.entry("ath", Skill.ATHLETICS),
            Map.entry("dec", Skill.DECEPTION), Map.entry("his", Skill.HISTORY),
            Map.entry("ins", Skill.INSIGHT), Map.entry("itm", Skill.INTIMIDATION),
            Map.entry("inv", Skill.INVESTIGATION), Map.entry("med", Skill.MEDICINE),
            Map.entry("nat", Skill.NATURE), Map.entry("prc", Skill.PERCEPTION),
            Map.entry("prf", Skill.PERFORMANCE), Map.entry("per", Skill.PERSUASION),
            Map.entry("rel", Skill.RELIGION), Map.entry("slt", Skill.SLEIGHT_OF_HAND),
            Map.entry("ste", Skill.STEALTH), Map.entry("sur", Skill.SURVIVAL));

    static SheetImport.Result read(JsonObject root, JsonObject sys) {
        List<String> notes = new ArrayList<>();
        CharacterSheet s = new CharacterSheet(nameOr(Json.str(root, "name"), "Personagem importado"));
        if ("npc".equals(Json.str(root, "type"))) notes.add("Este ator é um NPC do Foundry; virou ficha de personagem.");

        for (var e : ABILITIES.entrySet()) {
            Integer v = Json.integer(sys, "abilities." + e.getKey() + ".value");
            if (v != null) Json.setAbility(s, e.getValue(), v);
            Double prof = Json.num(sys, "abilities." + e.getKey() + ".proficient");
            if (prof != null && prof >= 1) s.setSavingThrowProficient(e.getValue(), true);
        }
        for (var e : SKILLS.entrySet()) {
            Double v = Json.num(sys, "skills." + e.getKey() + ".value");
            if (v == null) v = Json.num(sys, "skills." + e.getKey() + ".proficient");
            if (v == null) continue;
            if (v >= 2) s.setSkillExpertise(e.getValue(), true);
            else if (v >= 1) s.setSkillProficient(e.getValue(), true);
        }

        Integer hpMax = Json.integer(sys, "attributes.hp.max");
        Integer hp = Json.integer(sys, "attributes.hp.value");
        if (hpMax != null && hpMax > 0) s.setHpMax(hpMax);
        if (hp != null) s.setHpCurrent(hp);
        boolean needHp = hpMax == null;
        s.setHpTemp(Json.intOr(sys, "attributes.hp.temp", 0));
        Integer ac = Json.integer(sys, "attributes.ac.flat");
        if (ac == null) ac = Json.integer(sys, "attributes.ac.value");
        if (ac != null) s.setArmorClass(ac);
        boolean needAc = ac == null;
        Double walk = Json.num(sys, "attributes.movement.walk");
        if (walk != null && walk > 0) {
            String units = Json.str(sys, "attributes.movement.units");
            s.setSpeed("m".equals(units) ? (int) Math.round(walk) + " m" : Json.feetToMeters(walk));
        }
        Integer initBonus = Json.integer(sys, "attributes.init.bonus");
        if (initBonus != null) s.setInitiativeBonus(initBonus);

        s.setAlignment(Json.str(sys, "details.alignment"));
        s.setExperiencePoints(Json.intOr(sys, "details.xp.value", 0));
        s.setPersonalityTraits(Json.stripHtml(Json.str(sys, "details.trait")));
        s.setIdeals(Json.stripHtml(Json.str(sys, "details.ideal")));
        s.setBonds(Json.stripHtml(Json.str(sys, "details.bond")));
        s.setFlaws(Json.stripHtml(Json.str(sys, "details.flaw")));
        s.setAppearance(Json.stripHtml(Json.str(sys, "details.appearance")));
        s.setBackstory(Json.stripHtml(Json.str(sys, "details.biography.value")));
        s.setAge(Json.str(sys, "details.age"));
        s.setHeight(Json.str(sys, "details.height"));
        s.setWeight(Json.str(sys, "details.weight"));
        s.setEyes(Json.str(sys, "details.eyes"));
        s.setHair(Json.str(sys, "details.hair"));
        s.setSkin(Json.str(sys, "details.skin"));
        String race = Json.str(sys, "details.race");
        if (race != null && !race.matches("[A-Za-z0-9]{16}")) s.setSpecies(race); // v3+: e um id de item
        String bg = Json.str(sys, "details.background");
        if (bg != null && !bg.matches("[A-Za-z0-9]{16}")) s.setBackground(bg);

        for (String c : new String[]{"cp", "sp", "ep", "gp", "pp"}) {
            Integer v = Json.integer(sys, "currency." + c);
            if (v != null && v > 0) s.getInventory().getMoney().add(currency(c), v);
        }
        for (JsonElement l : Json.arr(sys, "traits.languages.value")) {
            if (l.isJsonPrimitive()) s.getLanguages().add(Json.titleCase(l.getAsString()));
        }
        for (JsonElement l : Json.arr(sys, "traits.dr.value")) if (l.isJsonPrimitive()) s.getDamageResistances().add(l.getAsString());
        for (JsonElement l : Json.arr(sys, "traits.di.value")) if (l.isJsonPrimitive()) s.getDamageImmunities().add(l.getAsString());
        for (JsonElement l : Json.arr(sys, "traits.dv.value")) if (l.isJsonPrimitive()) s.getDamageVulnerabilities().add(l.getAsString());

        // Espacos de magia: spells.spell1.value (atual) / override ou max.
        for (int lvl = 1; lvl <= 9; lvl++) {
            Integer cur = Json.integer(sys, "spells.spell" + lvl + ".value");
            Integer max = Json.integer(sys, "spells.spell" + lvl + ".override");
            if (max == null) max = Json.integer(sys, "spells.spell" + lvl + ".max");
            if (max == null && cur != null && cur > 0) max = cur;
            if (max != null && max > 0) {
                s.getSpellSlots().setMax(lvl, max);
                s.getSpellSlots().setCurrent(lvl, cur == null ? max : cur);
            }
        }
        String castAbility = Json.str(sys, "attributes.spellcasting");
        if (castAbility != null && ABILITIES.containsKey(castAbility)) s.setSpellcastingAbility(ABILITIES.get(castAbility));

        readItems(root, s, notes);
        // O Foundry calcula PV maximo e CA na hora (nao grava no arquivo): calcula igual aqui.
        if (needHp) {
            int computed = computeHpMax(root, s);
            if (computed > 0) {
                s.setHpMax(computed);
                if (hp == null || hp > computed) s.setHpCurrent(computed);
            } else if (hp != null && hp > 0) {
                s.setHpMax(hp);
            }
        }
        if (needAc) s.setArmorClass(computeAc(root, s));
        if (needHp || needAc) {
            notes.add("PV máximo e CA calculados como o Foundry faz (dado de vida + CON; armadura vestida + DES + escudo)."
                    + " Confira se o personagem tiver item mágico ou característica que muda isso.");
        }
        return new SheetImport.Result(s, "Foundry VTT (dnd5e)", notes);
    }

    private static void readItems(JsonObject root, CharacterSheet s, List<String> notes) {
        int level = 0;
        List<String> classes = new ArrayList<>();
        List<Object[]> weapons = new ArrayList<>();
        for (JsonElement el : Json.arr(root, "items")) {
            if (!el.isJsonObject()) continue;
            JsonObject it = el.getAsJsonObject();
            String type = Json.str(it, "type");
            String name = Json.str(it, "name");
            if (type == null || name == null) continue;
            JsonObject is = Json.obj(it, "system");
            if (is == null) is = Json.obj(it, "data");
            if (is == null) is = new JsonObject();
            String desc = Json.stripHtml(Json.str(is, "description.value"));
            switch (type) {
                case "class" -> {
                    int lv = Json.intOr(is, "levels", 1);
                    level += lv;
                    classes.add(classes.isEmpty() ? name : name + " " + lv);
                    String hd = Json.str(is, "hitDice");
                    if (hd == null) hd = Json.str(is, "hd.denomination");
                    if (hd != null && hd.matches("d\\d+") && classes.size() == 1) s.setHitDie(hd);
                    String spellAbility = Json.str(is, "spellcasting.ability");
                    if (spellAbility != null && ABILITIES.containsKey(spellAbility) && s.getSpellcastingAbility() == null) {
                        s.setSpellcastingAbility(ABILITIES.get(spellAbility));
                    }
                }
                case "subclass" -> s.setSubclass(name);
                case "race" -> s.setSpecies(name);
                case "background" -> s.setBackground(name);
                case "spell" -> {
                    PreparedSpell sp = new PreparedSpell(Json.intOr(is, "level", 0), name);
                    sp.setNotes(desc);
                    s.getSpells().add(sp);
                }
                case "feat" -> {
                    String sub = Json.str(is, "type.value");
                    String cat = "race".equals(sub) ? "species" : "class".equals(sub) ? "class" : "feat";
                    s.addTrait(new CharacterTrait(name, desc == null ? "" : desc, cat));
                }
                case "weapon" -> {
                    weapons.add(new Object[]{name, is});
                    addItem(s, name, is);
                }
                case "equipment", "loot", "consumable", "tool", "backpack", "container" -> addItem(s, name, is);
                default -> { }
            }
        }
        if (level > 0) s.setLevel(level);
        if (!classes.isEmpty()) {
            s.setClassName(classes.size() == 1 ? classes.get(0) : String.join(" / ", classes));
        }
        // Ataques: bonus = proficiencia + FOR (ou DES: distancia; acuidade = o maior) + bonus magico.
        for (Object[] w : weapons) s.addAttack(weaponAttack(s, (String) w[0], (JsonObject) w[1]));
    }

    private static int mod(int score) {
        return CharacterSheet.abilityModifier(score);
    }

    static Attack weaponAttack(CharacterSheet s, String name, JsonObject is) {
        String type = Json.str(is, "type.value");
        if (type == null) type = Json.str(is, "weaponType");
        java.util.Set<String> props = new java.util.HashSet<>();
        JsonElement pe = Json.at(is, "properties");
        if (pe != null && pe.isJsonArray()) {
            for (JsonElement p : pe.getAsJsonArray()) if (p.isJsonPrimitive()) props.add(p.getAsString());
        } else if (pe != null && pe.isJsonObject()) {
            for (var e : pe.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsBoolean()) props.add(e.getKey());
            }
        }
        boolean ranged = type != null && type.endsWith("R");
        String action = Json.str(is, "actionType");
        if ("rwak".equals(action)) ranged = true;
        int str = mod(s.getStrength()), dex = mod(s.getDexterity());
        int abilityMod = props.contains("fin") ? Math.max(str, dex) : ranged ? dex : str;
        String forced = Json.str(is, "ability");
        if (forced != null && forced.length() == 3) {
            abilityMod = switch (forced) {
                case "dex" -> dex;
                case "con" -> mod(s.getConstitution());
                case "int" -> mod(s.getIntelligence());
                case "wis" -> mod(s.getWisdom());
                case "cha" -> mod(s.getCharisma());
                default -> str;
            };
        }
        int magic = Json.intOr(is, "magicalBonus", 0);
        int attackBonus = s.proficiencyBonus() + abilityMod + magic;
        String damage = weaponDamage(is).replace("@mod", String.valueOf(abilityMod));
        // soma o modificador no primeiro dado ("1d8 cortante" -> "1d8+3 cortante")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+d\\d+)(\\s*\\+\\s*\\d+)?(.*)$").matcher(damage);
        if (m.matches() && m.group(2) == null) {
            int total = abilityMod + magic;
            damage = m.group(1) + (total == 0 ? "" : (total > 0 ? "+" : "") + total) + m.group(3);
        }
        String notes = "";
        Integer vn = Json.integer(is, "damage.versatile.number");
        Integer vd = Json.integer(is, "damage.versatile.denomination");
        if (props.contains("ver")) {
            if (vd == null || vd <= 0) {
                // versoes novas do dnd5e deixam em branco: versatil = um dado acima (d6->d8, d8->d10, d10->d12)
                Integer bd = Json.integer(is, "damage.base.denomination");
                Integer bn = Json.integer(is, "damage.base.number");
                if (bd != null && bd > 0) {
                    vd = bd >= 12 ? 12 : bd + 2;
                    vn = bn;
                }
            }
            if (vd != null && vd > 0) notes = "versátil " + (vn == null ? 1 : vn) + "d" + vd;
        }
        Integer range = Json.integer(is, "range.value");
        Integer longRange = Json.integer(is, "range.long");
        if (range != null && range > 0) {
            notes = (notes.isEmpty() ? "" : notes + ", ") + "alcance " + range + (longRange != null && longRange > 0 ? "/" + longRange : "") + " ft";
        }
        return new Attack(name, (attackBonus >= 0 ? "+" : "") + attackBonus, damage, notes);
    }

    /** PV maximo como o Foundry: 1o nivel = dado cheio + CON; depois a media (dado/2 + 1) + CON por nivel. */
    static int computeHpMax(JsonObject root, CharacterSheet s) {
        int con = mod(s.getConstitution());
        int total = 0;
        boolean first = true;
        for (JsonElement el : Json.arr(root, "items")) {
            if (!el.isJsonObject() || !"class".equals(Json.str(el.getAsJsonObject(), "type"))) continue;
            JsonObject is = Json.obj(el.getAsJsonObject(), "system");
            if (is == null) is = Json.obj(el.getAsJsonObject(), "data");
            if (is == null) continue;
            String hd = Json.str(is, "hitDice");
            if (hd == null) hd = Json.str(is, "hd.denomination");
            int faces = hd != null && hd.matches("d\\d+") ? Integer.parseInt(hd.substring(1)) : 8;
            int levels = Math.max(1, Json.intOr(is, "levels", 1));
            for (int i = 0; i < levels; i++) {
                total += (first ? faces : faces / 2 + 1) + con;
                first = false;
            }
        }
        return Math.max(0, total);
    }

    /** CA como o Foundry ("default"): armadura vestida + DES (leve: toda; media: ate +2; pesada: nada) + escudo. */
    static int computeAc(JsonObject root, CharacterSheet s) {
        int dex = mod(s.getDexterity());
        Integer armorBase = null;
        int dexCap = 99;
        int shield = 0;
        for (JsonElement el : Json.arr(root, "items")) {
            if (!el.isJsonObject() || !"equipment".equals(Json.str(el.getAsJsonObject(), "type"))) continue;
            JsonObject is = Json.obj(el.getAsJsonObject(), "system");
            if (is == null) is = Json.obj(el.getAsJsonObject(), "data");
            if (is == null) continue;
            JsonElement eq = Json.at(is, "equipped");
            if (eq == null || !eq.isJsonPrimitive() || !eq.getAsBoolean()) continue;
            String kind = Json.str(is, "type.value");
            if (kind == null) kind = Json.str(is, "armor.type");
            int value = Json.intOr(is, "armor.value", 0) + Json.intOr(is, "armor.magicalBonus", 0);
            if ("shield".equals(kind)) {
                shield += value;
            } else if ("light".equals(kind) || "medium".equals(kind) || "heavy".equals(kind)) {
                if (armorBase == null || value > armorBase) {
                    armorBase = value;
                    Integer cap = Json.integer(is, "armor.dex");
                    dexCap = "heavy".equals(kind) ? 0 : "medium".equals(kind) ? (cap == null || cap == 0 ? 2 : cap)
                            : (cap == null || cap == 0 ? 99 : cap);
                }
            }
        }
        int ac = armorBase == null ? 10 + dex : armorBase + Math.min(dex, dexCap);
        return ac + shield;
    }

    private static String weaponDamage(JsonObject is) {
        JsonArray parts = Json.arr(is, "damage.parts");
        if (!parts.isEmpty() && parts.get(0).isJsonArray()) {
            JsonArray p = parts.get(0).getAsJsonArray();
            String formula = p.size() > 0 && p.get(0).isJsonPrimitive() ? p.get(0).getAsString() : "";
            String type = p.size() > 1 && p.get(1).isJsonPrimitive() ? p.get(1).getAsString() : "";
            return (formula.replaceAll("\\s*\\+?\\s*@mod", "").trim() + " " + type).trim();
        }
        Integer n = Json.integer(is, "damage.base.number");
        String den = Json.str(is, "damage.base.denomination");
        if (n != null && den != null) {
            String type = "";
            JsonArray types = Json.arr(is, "damage.base.types");
            if (!types.isEmpty() && types.get(0).isJsonPrimitive()) type = " " + types.get(0).getAsString();
            return n + "d" + den.replace("d", "") + type;
        }
        return "";
    }

    private static void addItem(CharacterSheet s, String name, JsonObject is) {
        int qty = Math.max(1, Json.intOr(is, "quantity", 1));
        Double w = Json.num(is, "weight");
        if (w == null) w = Json.num(is, "weight.value");
        Double price = Json.num(is, "price");
        String den = "gp";
        if (price == null) {
            price = Json.num(is, "price.value");
            String d = Json.str(is, "price.denomination");
            if (d != null) den = d;
        }
        long copper = price == null ? 0 : Math.round(price * currency(den).copperValue());
        s.getInventory().addItem(name, qty, w == null ? 0 : w, copper);
    }

    static Currency currency(String code) {
        return switch (code == null ? "gp" : code) {
            case "cp" -> Currency.COPPER;
            case "sp" -> Currency.SILVER;
            case "ep" -> Currency.ELECTRUM;
            case "pp" -> Currency.PLATINUM;
            default -> Currency.GOLD;
        };
    }

    static String nameOr(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.strip();
    }
}

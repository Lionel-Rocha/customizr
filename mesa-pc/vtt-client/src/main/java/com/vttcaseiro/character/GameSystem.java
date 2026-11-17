package com.vttcaseiro.character;

import java.util.List;
import java.util.Locale;

/**
 * Sistema de RPG de uma ficha. O D&amp;D 5e usa a ficha completa de sempre (CharacterSheet + tela
 * propria); os outros sistemas usam os campos genericos da ficha (atributos, pericias, campos e
 * recursos por nome) com as regras de rolagem definidas aqui.
 *
 * <ul>
 *   <li><b>Tormenta20</b>: FOR DES CON INT SAB CAR; teste = 1d20 + metade do nivel + atributo
 *       + treino (+2 ate o nivel 6, +4 do 7 ao 14, +6 do 15 em diante) + outros. O atributo pode
 *       ser anotado como valor (10, 14...) ou ja como modificador (+2): ate 7 e tratado como modificador.
 *       PV, PM e Defesa.</li>
 *   <li><b>Ordem Paranormal</b>: AGI FOR INT PRE VIG (0 a 5); teste = rola tantos d20 quanto o
 *       atributo e fica com o maior (atributo 0: 2d20 e fica com o menor) + treino (treinado +5,
 *       veterano +10, expert +15) + outros. PV, PE, SAN, NEX e Defesa.</li>
 * </ul>
 */
public final class GameSystem {

    public record Attr(String key, String label) {
    }

    public record SkillDef(String name, String attr) {
    }

    public static final String DND5E = "dnd5e";
    public static final String T20 = "t20";
    public static final String OP = "op";

    private final String id;
    private final String name;
    private final List<Attr> attrs;
    private final List<SkillDef> skills;
    private final List<String> trainingLevels;
    private final List<String> resources;
    private final List<String> fields;
    private final int defaultAttr;

    private GameSystem(String id, String name, List<Attr> attrs, List<SkillDef> skills, List<String> trainingLevels,
                       List<String> resources, List<String> fields, int defaultAttr) {
        this.id = id;
        this.name = name;
        this.attrs = attrs;
        this.skills = skills;
        this.trainingLevels = trainingLevels;
        this.resources = resources;
        this.fields = fields;
        this.defaultAttr = defaultAttr;
    }

    public static final GameSystem DND = new GameSystem(DND5E, "D&D 5e (2024)", List.of(), List.of(), List.of(),
            List.of(), List.of(), 10);

    public static final GameSystem TORMENTA20 = new GameSystem(T20, "Tormenta20",
            List.of(new Attr("FOR", "Força"), new Attr("DES", "Destreza"), new Attr("CON", "Constituição"),
                    new Attr("INT", "Inteligência"), new Attr("SAB", "Sabedoria"), new Attr("CAR", "Carisma")),
            List.of(new SkillDef("Acrobacia", "DES"), new SkillDef("Adestramento", "CAR"), new SkillDef("Atletismo", "FOR"),
                    new SkillDef("Atuação", "CAR"), new SkillDef("Cavalgar", "DES"), new SkillDef("Conhecimento", "INT"),
                    new SkillDef("Cura", "SAB"), new SkillDef("Diplomacia", "CAR"), new SkillDef("Enganação", "CAR"),
                    new SkillDef("Fortitude", "CON"), new SkillDef("Furtividade", "DES"), new SkillDef("Guerra", "INT"),
                    new SkillDef("Iniciativa", "DES"), new SkillDef("Intimidação", "CAR"), new SkillDef("Intuição", "SAB"),
                    new SkillDef("Investigação", "INT"), new SkillDef("Jogatina", "CAR"), new SkillDef("Ladinagem", "DES"),
                    new SkillDef("Luta", "FOR"), new SkillDef("Misticismo", "INT"), new SkillDef("Nobreza", "INT"),
                    new SkillDef("Ofício", "INT"), new SkillDef("Percepção", "SAB"), new SkillDef("Pilotagem", "DES"),
                    new SkillDef("Pontaria", "DES"), new SkillDef("Reflexos", "DES"), new SkillDef("Religião", "SAB"),
                    new SkillDef("Sobrevivência", "SAB"), new SkillDef("Vontade", "SAB")),
            List.of("Destreinado", "Treinado"),
            List.of("PM"),
            List.of("Raça", "Origem", "Classe", "Divindade", "Tamanho", "Deslocamento"), 10);

    public static final GameSystem ORDEM = new GameSystem(OP, "Ordem Paranormal",
            List.of(new Attr("AGI", "Agilidade"), new Attr("FOR", "Força"), new Attr("INT", "Intelecto"),
                    new Attr("PRE", "Presença"), new Attr("VIG", "Vigor")),
            List.of(new SkillDef("Acrobacia", "AGI"), new SkillDef("Adestramento", "PRE"), new SkillDef("Artes", "PRE"),
                    new SkillDef("Atletismo", "FOR"), new SkillDef("Atualidades", "INT"), new SkillDef("Ciências", "INT"),
                    new SkillDef("Crime", "AGI"), new SkillDef("Diplomacia", "PRE"), new SkillDef("Enganação", "PRE"),
                    new SkillDef("Fortitude", "VIG"), new SkillDef("Furtividade", "AGI"), new SkillDef("Iniciativa", "AGI"),
                    new SkillDef("Intimidação", "PRE"), new SkillDef("Intuição", "PRE"), new SkillDef("Investigação", "INT"),
                    new SkillDef("Luta", "FOR"), new SkillDef("Medicina", "INT"), new SkillDef("Ocultismo", "INT"),
                    new SkillDef("Percepção", "PRE"), new SkillDef("Pilotagem", "AGI"), new SkillDef("Pontaria", "AGI"),
                    new SkillDef("Profissão", "INT"), new SkillDef("Reflexos", "AGI"), new SkillDef("Religião", "PRE"),
                    new SkillDef("Sobrevivência", "INT"), new SkillDef("Tática", "INT"), new SkillDef("Tecnologia", "INT"),
                    new SkillDef("Vontade", "PRE")),
            List.of("Destreinado", "Treinado (+5)", "Veterano (+10)", "Expert (+15)"),
            List.of("PE", "SAN"),
            List.of("Origem", "Classe", "Trilha", "NEX", "Patente", "Deslocamento", "Afinidade"), 1);

    public static List<GameSystem> all() {
        return List.of(DND, TORMENTA20, ORDEM);
    }

    public static GameSystem byId(String id) {
        if (T20.equals(id)) return TORMENTA20;
        if (OP.equals(id)) return ORDEM;
        return DND;
    }

    public static GameSystem of(CharacterSheet sheet) {
        return byId(sheet == null ? null : sheet.getSystem());
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Attr> attrs() { return attrs; }
    public List<SkillDef> skills() { return skills; }
    public List<String> trainingLevels() { return trainingLevels; }
    public List<String> resources() { return resources; }
    public List<String> fields() { return fields; }
    public boolean isCustom() { return this != DND; }
    public int defaultAttr() { return defaultAttr; }

    @Override
    public String toString() { return name; }

    /** Prepara uma ficha nova deste sistema (atributos iniciais, recursos, PV). */
    public void initialize(CharacterSheet s) {
        s.setSystem(id);
        if (!isCustom()) return;
        for (Attr a : attrs) s.getCustomAttributes().putIfAbsent(a.key(), defaultAttr);
        for (String r : resources) {
            if (s.findResource(r).isEmpty()) {
                s.getResources().add(new Resource(r, this == ORDEM && r.equals("SAN") ? 12 : 4, Resource.NEVER));
            }
        }
        if (s.getHpMax() == null) {
            s.setHpMax(this == ORDEM ? 20 : 18);
            s.setHpCurrent(s.getHpMax());
        }
        if (s.getArmorClass() == null) s.setArmorClass(this == ORDEM ? 11 : 10);
        if (this == ORDEM) {
            s.getCustomFields().putIfAbsent("NEX", "5%");
            s.setSpeed("9 m");
        } else {
            s.getCustomFields().putIfAbsent("Deslocamento", "9 m");
        }
    }

    public int attr(CharacterSheet s, String key) {
        Integer v = s.getCustomAttributes().get(key);
        return v == null ? defaultAttr : v;
    }

    /** Modificador do atributo (T20: valor ou modificador; Ordem: o proprio valor = numero de dados). */
    public int attrModifier(CharacterSheet s, String key) {
        int v = attr(s, key);
        if (this == TORMENTA20) return Math.abs(v) <= 7 ? v : Math.floorDiv(v - 10, 2);
        return v;
    }

    public SkillDef skill(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (SkillDef d : skills) if (d.name().toLowerCase(Locale.ROOT).equals(n)) return d;
        return null;
    }

    public int training(CharacterSheet s, String skill) {
        Integer t = s.getCustomSkills().get(skill);
        return t == null ? 0 : Math.max(0, Math.min(trainingLevels.size() - 1, t));
    }

    public int otherBonus(CharacterSheet s, String skill) {
        Integer b = s.getCustomSkillBonus().get(skill);
        return b == null ? 0 : b;
    }

    /** Bonus fixo do teste de pericia (sem os dados). */
    public int skillBonus(CharacterSheet s, String skill) {
        SkillDef d = skill(skill);
        if (d == null) return 0;
        int t = training(s, d.name());
        int other = otherBonus(s, d.name());
        if (this == TORMENTA20) {
            int lvl = s.getLevel();
            int trainBonus = t > 0 ? (lvl >= 15 ? 6 : lvl >= 7 ? 4 : 2) : 0;
            return lvl / 2 + attrModifier(s, d.attr()) + trainBonus + other;
        }
        if (this == ORDEM) return t * 5 + other;
        return other;
    }

    /** Dados do teste com este atributo ("1d20" no T20; "3d20kh1" / "2d20kl1" na Ordem). */
    public String testDice(CharacterSheet s, String attrKey) {
        if (this == ORDEM) {
            int n = attr(s, attrKey);
            return n <= 0 ? "2d20kl1" : n == 1 ? "1d20" : Math.min(n, 20) + "d20kh1";
        }
        return "1d20";
    }

    /** Expressao completa do teste da pericia, pronta pro rolador. */
    public String skillTest(CharacterSheet s, String skill) {
        SkillDef d = skill(skill);
        if (d == null) return "1d20";
        return withBonus(testDice(s, d.attr()), skillBonus(s, d.name()));
    }

    /** Teste puro de atributo. */
    public String attrTest(CharacterSheet s, String attrKey) {
        if (this == TORMENTA20) return withBonus("1d20", attrModifier(s, attrKey));
        return testDice(s, attrKey);
    }

    /** Teste de ataque: o campo do ataque pode ser o nome de uma pericia (Luta, Pontaria) ou um bonus (+5). */
    public String attackTest(CharacterSheet s, Attack attack) {
        String field = attack.getAttackBonusOrDc() == null ? "" : attack.getAttackBonusOrDc().trim();
        String[] parts = field.split("\\s*\\+\\s*", 2);
        SkillDef d = skill(parts[0]);
        if (d != null) {
            int extra = 0;
            if (parts.length > 1) {
                try { extra = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { }
            }
            return withBonus(testDice(s, d.attr()), skillBonus(s, d.name()) + extra);
        }
        java.util.OptionalInt b = attack.attackBonus();
        return withBonus("1d20", b.isPresent() ? b.getAsInt() : 0);
    }

    public String initiativeTest(CharacterSheet s) {
        return skillTest(s, "Iniciativa");
    }

    /** Valor que vai pro token como "bonus de iniciativa" (o fixo, sem os dados). */
    public int initiativeBonus(CharacterSheet s) {
        return skillBonus(s, "Iniciativa");
    }

    static String withBonus(String dice, int bonus) {
        return bonus == 0 ? dice : dice + (bonus > 0 ? "+" + bonus : String.valueOf(bonus));
    }

    /** Defesa sugerida (T20: 10 + DES; Ordem: 10 + AGI), pra ajudar a preencher. */
    public int suggestedDefense(CharacterSheet s) {
        if (this == TORMENTA20) return 10 + attrModifier(s, "DES");
        if (this == ORDEM) return 10 + attr(s, "AGI");
        return 10;
    }
}

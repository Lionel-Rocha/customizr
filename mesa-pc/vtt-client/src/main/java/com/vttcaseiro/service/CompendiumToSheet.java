package com.vttcaseiro.service;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumEntryType;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.content.FeatureGrant;
import com.vttcaseiro.content.Subclass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Leva conteudo importado do 5e.tools (compendio e classes) pra dentro de
 * uma ficha: aplicar classe/subclasse (dado de vida, salvaguardas,
 * armaduras, pericias, conjuracao e as caracteristicas ate o nivel atual),
 * adicionar magia, talento, especie, antecedente ou item. Sem tela -- a
 * CharacterSheetView so escolhe o que aplicar.
 */
public final class CompendiumToSheet {

    private CompendiumToSheet() {
    }

    /** Resumo do que mudou, pra mostrar no chat/log. */
    public record ApplyResult(List<String> changes, int traitsAdded) {
        public String summary() {
            return String.join("; ", changes) + (traitsAdded > 0 ? "; " + traitsAdded + " característica(s) adicionada(s)" : "");
        }
    }

    /**
     * Aplica a classe. Pode ser chamado de novo ao subir de nivel: so
     * acrescenta as caracteristicas que ainda nao estao na ficha (pelo nome).
     */
    public static ApplyResult applyClass(CharacterSheet sheet, DndClass dndClass, Subclass subclass,
                                         Collection<Skill> chosenSkills) {
        List<String> changes = new ArrayList<>();
        sheet.setClassName(dndClass.getClassName());
        if (subclass != null) sheet.setSubclass(subclass.getName());
        if (dndClass.getHitDieFaces() > 0) {
            sheet.setHitDie("d" + dndClass.getHitDieFaces());
            changes.add("dado de vida d" + dndClass.getHitDieFaces());
        }
        Ability casting = abilityFromCode(dndClass.getSpellcastingAbility());
        if (casting != null) {
            sheet.setSpellcastingAbility(casting);
            changes.add("conjuração por " + casting.abbreviationPt());
        }
        for (String code : dndClass.getSavingThrows()) {
            Ability a = abilityFromCode(code);
            if (a != null) sheet.setSavingThrowProficient(a, true);
        }
        if (!dndClass.getSavingThrows().isEmpty()) {
            changes.add("salvaguardas " + String.join("/", dndClass.getSavingThrows()).toUpperCase(Locale.ROOT));
        }
        for (String armor : dndClass.getArmorTraining()) {
            switch (armor) {
                case "light" -> sheet.setArmorTrainingLight(true);
                case "medium" -> sheet.setArmorTrainingMedium(true);
                case "heavy" -> sheet.setArmorTrainingHeavy(true);
                case "shield" -> sheet.setArmorTrainingShields(true);
                default -> { }
            }
        }
        if (!dndClass.getWeaponProficiencies().isEmpty()) {
            String weapons = String.join(", ", dndClass.getWeaponProficiencies());
            sheet.setWeaponProficiencies(mergeText(sheet.getWeaponProficiencies(), weapons));
        }
        if (chosenSkills != null) {
            for (Skill skill : chosenSkills) sheet.setSkillProficient(skill, true);
            if (!chosenSkills.isEmpty()) changes.add(chosenSkills.size() + " perícia(s)");
        }

        int added = 0;
        for (FeatureGrant grant : dndClass.featuresUpToLevel(sheet.getLevel(), subclass)) {
            if (grant.getFeature() == null || grant.getFeature().getName() == null) continue;
            String name = grant.getFeature().getName();
            boolean present = sheet.getTraits().stream().anyMatch(t -> name.equalsIgnoreCase(t.getName()));
            if (present) continue;
            String desc = "Nível " + grant.getLevel() + ". " + (grant.getFeature().getDescription() == null ? "" : grant.getFeature().getDescription());
            sheet.addTrait(new CharacterTrait(name, desc.trim(), "class"));
            added++;
        }
        List<String> resources = com.vttcaseiro.character.ClassResources.apply(sheet);
        if (!resources.isEmpty()) changes.add("recursos: " + String.join(", ", resources));
        return new ApplyResult(changes, added);
    }

    /** Pericias que a classe deixa escolher, ja como enum. */
    public static List<Skill> skillChoices(DndClass dndClass) {
        List<Skill> result = new ArrayList<>();
        List<String> names = dndClass.getSkillChoices();
        if (names.isEmpty() && dndClass.getSkillChoiceCount() > 0) {
            return List.of(Skill.values()); // "qualquer perícia"
        }
        for (String n : names) {
            Skill s = skillFromToolsName(n);
            if (s != null && !result.contains(s)) result.add(s);
        }
        return result;
    }

    /** "animal handling" / "sleight of hand" (5e.tools) -> enum. */
    public static Skill skillFromToolsName(String name) {
        if (name == null) return null;
        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Skill.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static Ability abilityFromCode(String code) {
        if (code == null) return null;
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "str" -> Ability.STRENGTH;
            case "dex" -> Ability.DEXTERITY;
            case "con" -> Ability.CONSTITUTION;
            case "int" -> Ability.INTELLIGENCE;
            case "wis" -> Ability.WISDOM;
            case "cha" -> Ability.CHARISMA;
            default -> null;
        };
    }

    /** Entrada de magia do compendio -> linha da tabela de magias. */
    public static PreparedSpell spellFromEntry(CompendiumEntry entry) {
        PreparedSpell spell = new PreparedSpell(entry.getSpellLevel() == null ? 0 : entry.getSpellLevel(), entry.getName());
        spell.setCastingTime(nonNull(entry.getSpellCastingTime()));
        spell.setRange(nonNull(entry.getSpellRange()));
        spell.setConcentration(Boolean.TRUE.equals(entry.getSpellConcentration()));
        spell.setRitual(Boolean.TRUE.equals(entry.getSpellRitual()));
        // O importador grava "Components: V, S, M (...)" no inicio do texto.
        String full = entry.getFullText() == null ? "" : entry.getFullText();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Components:\\s*([^|\\n]*)").matcher(full);
        spell.setMaterialRequired(m.find() && m.group(1).matches("(?s).*\\bM\\b.*"));
        spell.setNotes(nonNull(entry.getSpellDuration()));
        fillSpellDice(spell, full);
        return spell;
    }

    /**
     * Dados da magia tirados do texto: o primeiro "XdY" da descricao e o
     * "increases by XdY for each spell slot level above". Se o aumento
     * vier IGUAL ao dado base, nao confia (o compendio antigo renderizava
     * o scaledamage errado, ex.: Bola de Fogo "+8d6") e deixa vazio pro
     * jogador preencher.
     */
    static void fillSpellDice(PreparedSpell spell, String fullText) {
        if (fullText == null) return;
        int start = fullText.indexOf("Duration:");
        String body = start >= 0 ? fullText.substring(start) : fullText;
        int nl = body.indexOf('\n');
        if (nl >= 0) body = body.substring(nl);
        String main = body;
        int higher = main.indexOf("Using a Higher-Level");
        if (higher >= 0) main = main.substring(0, higher);
        int upgrade = main.indexOf("Cantrip Upgrade");
        if (upgrade >= 0) main = main.substring(0, upgrade);
        java.util.regex.Matcher dice = java.util.regex.Pattern.compile("\\b(\\d+d\\d+)\\b").matcher(main);
        String lower = main.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher save = java.util.regex.Pattern
                .compile("(strength|dexterity|constitution|intelligence|wisdom|charisma) saving throw").matcher(lower);
        if (save.find()) spell.setSaveAbility(save.group(1).toUpperCase(Locale.ROOT));
        spell.setHalfOnSave(lower.contains("half as much"));
        spell.setSpellAttack(lower.contains("spell attack"));
        if (!dice.find()) return;
        spell.setDice(dice.group(1));
        String after = main.substring(dice.end(), Math.min(main.length(), dice.end() + 40));
        String before = main.substring(Math.max(0, dice.start() - 60), dice.start()).toLowerCase(Locale.ROOT);
        if (before.contains("regain") || after.toLowerCase(Locale.ROOT).contains("hit point")) {
            spell.setDamageType("healing");
        } else {
            String type = com.vttcaseiro.character.DamageTypes.find(after);
            if (type != null) spell.setDamageType(type);
        }
        java.util.regex.Matcher per = java.util.regex.Pattern
                .compile("increases by (\\d+d\\d+) for each (?:spell )?slot level above").matcher(body);
        if (per.find() && !per.group(1).equals(dice.group(1))) spell.setDicePerLevel(per.group(1));
    }

    /**
     * Adiciona uma entrada do compendio na ficha, do jeito certo pro tipo:
     * magia -> tabela de magias; talento/especie/antecedente -> traco na
     * caixa certa (+ o que eles concedem, via relatedEntryIds); item ->
     * equipamento. Devolve o que foi feito, pro chat/log.
     */
    public static String addEntry(CharacterSheet sheet, CompendiumEntry entry, Compendium compendium) {
        CompendiumEntryType type = entry.getType();
        if (type == null) return "Tipo desconhecido: " + entry.getName();
        switch (type) {
            case SPELL -> {
                boolean present = sheet.getSpells().stream().anyMatch(s -> entry.getName().equalsIgnoreCase(s.getName()));
                if (!present) sheet.getSpells().add(spellFromEntry(entry));
                return present ? entry.getName() + " já estava na lista de magias." : "Magia adicionada: " + entry.getName();
            }
            case ITEM -> {
                long copper = entry.getItemValueGp() == null ? 0 : Math.round(entry.getItemValueGp() * 100);
                double weight = entry.getItemWeightLb() == null ? 0 : entry.getItemWeightLb();
                sheet.getInventory().addItem(entry.getName(), 1, weight, copper);
                String note = Boolean.TRUE.equals(entry.getItemRequiresAttunement()) ? " (exige sintonia)" : "";
                return "Item adicionado: " + entry.getName() + note;
            }
            case SPECIES -> {
                sheet.setSpecies(entry.getName());
                return addTraitWithRelated(sheet, entry, "species", compendium);
            }
            case BACKGROUND -> {
                sheet.setBackground(entry.getName());
                return addTraitWithRelated(sheet, entry, "other", compendium);
            }
            case FEAT -> {
                return addTraitWithRelated(sheet, entry, "feat", compendium);
            }
            default -> {
                return addTraitWithRelated(sheet, entry, "other", compendium);
            }
        }
    }

    private static String addTraitWithRelated(CharacterSheet sheet, CompendiumEntry entry, String category,
                                              Compendium compendium) {
        List<String> added = new ArrayList<>();
        if (addTraitIfMissing(sheet, entry, category)) added.add(entry.getName());
        if (compendium != null) {
            for (CompendiumEntry related : compendium.relatedTo(entry)) {
                if (related.getType() == CompendiumEntryType.SPELL) {
                    boolean present = sheet.getSpells().stream().anyMatch(s -> related.getName().equalsIgnoreCase(s.getName()));
                    if (!present) {
                        sheet.getSpells().add(spellFromEntry(related));
                        added.add(related.getName() + " (magia)");
                    }
                } else if (addTraitIfMissing(sheet, related, related.getType() == CompendiumEntryType.FEAT ? "feat" : "other")) {
                    added.add(related.getName());
                }
            }
        }
        return added.isEmpty() ? entry.getName() + " já estava na ficha." : "Adicionado: " + String.join(", ", added);
    }

    private static boolean addTraitIfMissing(CharacterSheet sheet, CompendiumEntry entry, String category) {
        boolean present = sheet.getTraits().stream().anyMatch(t -> entry.getName().equalsIgnoreCase(t.getName()));
        if (present) return false;
        String text = entry.getFullText() != null && !entry.getFullText().isBlank()
                ? entry.getFullText() : nonNull(entry.getShortDescription());
        sheet.addTrait(new CharacterTrait(entry.getName(), text, category));
        return true;
    }

    private static String mergeText(String existing, String extra) {
        if (existing == null || existing.isBlank()) return extra;
        if (existing.toLowerCase(Locale.ROOT).contains(extra.toLowerCase(Locale.ROOT))) return existing;
        return existing + ", " + extra;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}

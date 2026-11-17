package com.vttcaseiro.mob;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le o array "monster" de um bestiary-*.json do 5e.tools (ex.:
 * {@code bestiary-xmm.json}, o Manual dos Monstros 2024) e devolve uma
 * {@link MobSheet} pronta pra cada bloco -- mesmo papel que os 5
 * importadores de {@code com.vttcaseiro.compendium} tem pra antecedente/
 * especie/feat/item/magia, so que pra {@code MobSheet} em vez de
 * {@code CompendiumEntry} (ver HANDOFF.md, secao do compendio, pra por
 * que monstro NAO entra no mesmo sistema: uma ficha de monstro tem campos
 * completamente diferentes -- CA/PV/velocidade/resistencias -- de "um
 * texto pra mandar no chat").
 * <p>
 * A parte que SO da pra fazer com o Gson de verdade (ler o JsonObject/
 * JsonArray) esta NESTA classe, igual o resto dos importadores -- nao
 * testada aqui (sem Gson no ambiente onde escrevi isto), so revisada com
 * cuidado. A logica de traducao de campo (tamanho/tipo/alinhamento/CR/
 * velocidade/bonus) esta em {@link MobJsonAnalyzer}, testada de verdade
 * com Map/List construidos a mao em Java puro.
 * <p>
 * NAO importado de proposito (fora do escopo pedido -- "antecedente/
 * especie/feat/item/magia" original nunca incluiu monstro; isso foi um
 * avanco extra oferecido depois): percepcao passiva ("passive" -- a
 * ficha nao tem campo pra isso ainda), spellcasting estruturado (o bloco
 * "Spellcasting" vira so mais uma action/trait em prosa, igual qualquer
 * outra), grupo de acao legendaria de covil ("legendaryGroup"/
 * "legendaryActionsLair") e a condicao de uma velocidade especial (ex.:
 * "(hover)" em voo) -- ver javadoc de {@link MobJsonAnalyzer#speedValue}.
 */
public final class MobImporter {

    private static final Gson GSON = new Gson();

    private MobImporter() {
    }

    /** Um monstro importado com sucesso, ou um pulado por nome ja existir
     * na mesa -- mesmo padrao de {@code ClassImporter.ImportResult}, so
     * que simplificado pra so o nome (um monstro nao tem "codigo de
     * fonte" relevante aqui -- quem usa so precisa saber quais entraram e
     * quais nao). */
    public record ImportResult(List<MobSheet> imported, List<String> skipped) {
    }

    /** @param existingNames nomes de monstros que JA existem nesta mesa
     *   (ver MesaRepository.listMobs()) -- um monstro do arquivo com o
     *   MESMO nome (sem diferenciar maiusculas) e pulado, pra importar o
     *   bestiario inteiro varias vezes nao duplicar nada. Comparar so por
     *   nome (em vez de nome+conteudo, como o Compendium.merge faz) e de
     *   proposito: dois "Goblin" na mesma mesa seriam indistinguiveis na
     *   lista de qualquer jeito. */
    public static ImportResult importMonsters(JsonObject root, List<String> existingNames) {
        List<MobSheet> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        JsonElement monsterEl = root == null ? null : root.get("monster");
        if (monsterEl == null || !monsterEl.isJsonArray()) {
            return new ImportResult(imported, skipped);
        }
        for (JsonElement el : monsterEl.getAsJsonArray()) {
            Map<String, Object> row = toMap(el);
            String name = nameOrFallback(ClassJsonAnalyzer.text(row, "name"));
            boolean alreadyExists = existingNames != null && existingNames.stream()
                    .anyMatch(existing -> existing.equalsIgnoreCase(name));
            if (alreadyExists) {
                skipped.add(name);
                continue;
            }
            imported.add(importOne(row));
        }
        return new ImportResult(imported, skipped);
    }

    private static MobSheet importOne(Map<String, Object> row) {
        MobSheet mob = new MobSheet(nameOrFallback(ClassJsonAnalyzer.text(row, "name")));

        mob.setSize(MobJsonAnalyzer.sizeName(ClassJsonAnalyzer.nestedList(row, "size")));
        mob.setCreatureType(MobJsonAnalyzer.typeName(row.get("type")));
        mob.setAlignment(MobJsonAnalyzer.alignmentName(ClassJsonAnalyzer.nestedList(row, "alignment")));

        mob.setArmorClass(MobJsonAnalyzer.armorClass(ClassJsonAnalyzer.nestedList(row, "ac")));

        Map<String, Object> hp = ClassJsonAnalyzer.nestedMap(row, "hp");
        mob.setHitPointsAverage(MobJsonAnalyzer.hpAverage(hp));
        mob.setHitPointsFormula(MobJsonAnalyzer.hpFormula(hp));

        Map<String, Object> speed = ClassJsonAnalyzer.nestedMap(row, "speed");
        mob.setSpeedWalk(MobJsonAnalyzer.speedValue(speed, "walk"));
        mob.setSpeedFly(MobJsonAnalyzer.speedValue(speed, "fly"));
        mob.setSpeedSwim(MobJsonAnalyzer.speedValue(speed, "swim"));
        mob.setSpeedClimb(MobJsonAnalyzer.speedValue(speed, "climb"));
        mob.setSpeedBurrow(MobJsonAnalyzer.speedValue(speed, "burrow"));

        mob.setStrength(ClassJsonAnalyzer.optionalInt(row, "str").orElse(10));
        mob.setDexterity(ClassJsonAnalyzer.optionalInt(row, "dex").orElse(10));
        mob.setConstitution(ClassJsonAnalyzer.optionalInt(row, "con").orElse(10));
        mob.setIntelligence(ClassJsonAnalyzer.optionalInt(row, "int").orElse(10));
        mob.setWisdom(ClassJsonAnalyzer.optionalInt(row, "wis").orElse(10));
        mob.setCharisma(ClassJsonAnalyzer.optionalInt(row, "cha").orElse(10));

        mob.getSavingThrowBonuses().putAll(MobJsonAnalyzer.bonusMap(ClassJsonAnalyzer.nestedMap(row, "save")));
        mob.getSkillBonuses().putAll(MobJsonAnalyzer.bonusMap(ClassJsonAnalyzer.nestedMap(row, "skill")));

        mob.getDamageVulnerabilities().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "vulnerable")));
        mob.getDamageResistances().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "resist")));
        mob.getDamageImmunities().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "immune")));
        mob.getConditionImmunities().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "conditionImmune")));
        mob.getSenses().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "senses")));
        mob.getLanguages().addAll(MobJsonAnalyzer.stringListOrEmpty(ClassJsonAnalyzer.nestedList(row, "languages")));

        mob.setChallengeRating(MobJsonAnalyzer.crNumber(row.get("cr")));

        for (Object t : ClassJsonAnalyzer.nestedList(row, "trait")) {
            if (t instanceof Map<?, ?> m) {
                mob.addTrait(traitFrom(castMap(m), "trait"));
            }
        }
        for (Object a : ClassJsonAnalyzer.nestedList(row, "action")) {
            if (a instanceof Map<?, ?> m) {
                mob.addAction(traitFrom(castMap(m), "action"));
            }
        }
        for (Object la : ClassJsonAnalyzer.nestedList(row, "legendary")) {
            if (la instanceof Map<?, ?> m) {
                mob.addLegendaryAction(traitFrom(castMap(m), "legendary"));
            }
        }

        return mob;
    }

    private static CharacterTrait traitFrom(Map<String, Object> entry, String category) {
        String name = ClassJsonAnalyzer.text(entry, "name");
        String description = EntriesRenderer.render(ClassJsonAnalyzer.nestedList(entry, "entries"));
        // O nome tambem tem tags ("Fire Breath {@recharge 5}") -- renderiza.
        String shown = name == null ? "?" : com.vttcaseiro.content.EntriesRenderer.renderInline(name).trim();
        return new CharacterTrait(shown, description, category);
    }

    private static String nameOrFallback(String name) {
        return (name == null || name.isBlank()) ? "Monstro importado" : name;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonElement element) {
        return (Map<String, Object>) GSON.fromJson(element, Object.class);
    }
}

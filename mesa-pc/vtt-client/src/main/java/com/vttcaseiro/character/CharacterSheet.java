package com.vttcaseiro.character;

import com.vttcaseiro.compendium.CompendiumReference;
import com.vttcaseiro.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Uma ficha de D&D 5e. Como TokenModel/SceneModel, e so dados (sem
 * JavaFX) -- isso e o que fica salvo na pasta de fichas da biblioteca
 * (ver com.vttcaseiro.library.LibraryStorage) e o que viaja pela rede
 * quando a Fase 2 precisar.
 * <p>
 * Nomes de campo em ingles (mesmo padrao do resto do projeto -- a tela
 * que ainda vamos construir em cima disso que mostra tudo em portugues).
 */
public class CharacterSheet {
    private String id;
    private String name = "Novo personagem";
    private String playerName;
    private String species;
    private String className;
    private String background;
    private int level = 1;

    private int strength = 10;
    private int dexterity = 10;
    private int constitution = 10;
    private int intelligence = 10;
    private int wisdom = 10;
    private int charisma = 10;

    private Integer hpCurrent;
    private Integer hpMax;
    private Integer armorClass;

    /** Imagem usada como retrato na ficha E como imagem do token quando
     * a ficha e arrastada pro mapa. */
    private String portraitImagePath;

    /** Id do asset no ImageAssetStore da mesa (ver com.vttcaseiro.library.
     * ImageAssetStore) -- substitui gradualmente portraitImagePath como
     * forma "oficial" de guardar o retrato, porque um caminho de arquivo
     * local nao significa nada numa mesa aberta em outra maquina, e um
     * asset guardado por hash e compartilhado (sem duplicar bytes) entre
     * a ficha e qualquer token criado a partir dela. */
    private String portraitImageAssetId;

    private List<CharacterTrait> traits = new ArrayList<>();

    /** Inventario proprio deste personagem -- separado do inventario
     * compartilhado da Party (ver com.vttcaseiro.party.Party). Um item
     * pode ser transferido de um pro outro com Inventory.transferItemTo. */
    private Inventory inventory = new Inventory();

    /** Separado de {@code traits} de proposito -- um Feat sabe validar os
     * proprios pre-requisitos (ver com.vttcaseiro.character.prerequisite),
     * um CharacterTrait so e "nome + descricao + clicavel". */
    private List<Feat> feats = new ArrayList<>();

    /** Quais das seis habilidades este personagem e proficiente em
     * salvaguarda (JP). Usado declarado como "LinkedHashSet guardado como
     * Set" de proposito e nao EnumSet: EnumSet nao tem construtor publico
     * sem argumentos, entao o Gson nao consegue recriar um EnumSet ao
     * carregar uma ficha salva -- travaria toda vez que a mesa abrisse uma
     * ficha com pelo menos uma salvaguarda proficiente marcada. */
    private Set<Ability> savingThrowProficiencies = new LinkedHashSet<>();

    /** Mesma logica do campo acima, mas pra pericias. */
    private Set<Skill> skillProficiencies = new LinkedHashSet<>();

    /** Pericias com expertise (bonus de proficiencia em dobro). Uma pericia
     * so pode ter expertise se ja for proficiente -- ver
     * setSkillExpertise/setSkillProficient abaixo, que mantem essa regra
     * sempre que um dos dois muda. */
    private Set<Skill> skillExpertise = new LinkedHashSet<>();

    /** 0 a 3. Ver clampDeathSave -- nunca guardamos um valor fora dessa
     * faixa, porque na mesa 3 sucessos estabiliza e 3 falhas mata. */
    private int deathSaveSuccesses;
    private int deathSaveFailures;

    private List<Attack> attacks = new ArrayList<>();
    private List<String> languages = new ArrayList<>();

    /** Entradas do compendio da mesa (classe, subclasse, antecedente,
     * especie, feat de catalogo, item, magia) que esta ficha anexou --
     * guarda so a REFERENCIA (ver CompendiumReference), nunca o texto
     * completo copiado, pra uma correcao no compendio chegar em todas as
     * fichas que anexaram aquela entrada. Resolver a referencia pro texto
     * de verdade (pra mandar no chat quando clicada) e trabalho do
     * com.vttcaseiro.service.CompendiumService, nao desta classe. */
    private List<CompendiumReference> compendiumAttachments = new ArrayList<>();

    /** Slots de magia (nivel 1 a 9) -- ver {@link SpellSlots}. Um
     * guerreiro puro simplesmente nunca tera nenhum "max" setado em
     * nenhum nivel (hasAnySlots() fica false), sem precisar de nenhum
     * campo "classe conjura ou nao" separado. */
    private SpellSlots spellSlots = new SpellSlots();

    /** Campos de personalidade -- nenhum e obrigatorio, todos ficam null/
     * em branco numa ficha nova. Texto livre de proposito (idade "25
     * anos" ou "jovem pros padroes elficos", altura "1.70m" ou "alto",
     * etc.) em vez de numero/unidade fixa -- a mesma logica que levou
     * GridSettings.cellSize a nao ter unidade real ainda (ver
     * HANDOFF.md): forcar um formato cedo demais tende a atrapalhar mais
     * do que ajudar numa mesa caseira. */
    private String age;
    private String height;
    private String weight;
    private String eyes;
    private String skin;
    private String hair;
    private String alignment;
    private String personalityTraits;
    private String ideals;
    private String bonds;
    private String flaws;
    private String backstory;

    // ------------------------------------------------------------------
    // Campos da ficha oficial de 2024 (DnD_2024_Character-Sheet.pdf) que
    // ainda nao existiam. Todos tem padrao seguro, entao uma ficha salva
    // antes deles existirem abre normal (o Gson usa o construtor sem
    // argumentos e so sobrescreve o que estiver no JSON).
    // ------------------------------------------------------------------

    private String subclass;
    private int experiencePoints;

    /** PV temporario -- some primeiro quando o personagem toma dano. */
    private int hpTemp;

    /** Dado de vida da classe ("d6", "d8", "d10", "d12"). O total de dados
     * de vida e o nivel (ver hitDiceMax), so os gastos ficam guardados. */
    private String hitDie = "d8";
    private int hitDiceSpent;

    private boolean heroicInspiration;

    /** Caixa "shield" ao lado da CA na ficha 2024: quando marcada, soma +2
     * em cima de {@code armorClass} (que passa a ser a CA sem escudo). */
    private boolean shieldEquipped;

    /** Bonus extra de iniciativa alem do mod. de DES (ex.: talento Alert). */
    private int initiativeBonus;

    private String speed = "9 m";
    private String size = "Medio";

    private boolean armorTrainingLight;
    private boolean armorTrainingMedium;
    private boolean armorTrainingHeavy;
    private boolean armorTrainingShields;
    private String weaponProficiencies;
    private String toolProficiencies;

    /** Habilidade de conjuracao (INT pro mago, SAB pro clerigo...). null =
     * personagem nao conjura, e a secao de conjuracao mostra "--". */
    private Ability spellcastingAbility;
    private List<PreparedSpell> spells = new ArrayList<>();

    private String appearance;

    /** Condicoes oficiais ativas (nomes de com.vttcaseiro.model.Condition)
     * -- espelhadas no token ligado a esta ficha. */
    private List<String> conditions = new ArrayList<>();

    /** Efeitos livres ("Abencoado", "Furia", "Escudo da Fe +2"). */
    private List<String> effects = new ArrayList<>();

    /** Exaustao 2024: 0 a 6. Cada nivel = -2 em todo teste de d20 e -1,5 m
     * de deslocamento; 6 = morte. */
    private int exhaustionLevel;

    /** Magia que o personagem esta concentrando (null = nenhuma). Tomar
     * dano pede teste de CON, CD max(10, metade do dano). */
    private String concentratingOn;
    /** Usos contados (Furia 3/3, cargas, pocoes). */
    private List<Resource> resources = new ArrayList<>();
    /** Tipos de dano (texto livre: "fogo, veneno" ou "Fire"). */
    private List<String> damageResistances = new ArrayList<>();
    private List<String> damageImmunities = new ArrayList<>();
    private List<String> damageVulnerabilities = new ArrayList<>();

    /** playerId (AppSettings.getOrCreatePlayerId) de quem joga com esta
     * ficha. null = ficha antiga/sem dono: so o mestre mexe ate alguem
     * assumir (o jogador salvando a propria ficha, ou o mestre usando
     * "Dar controle a..."). */
    private String ownerPlayerId;

    public String getOwnerPlayerId() { return ownerPlayerId; }
    public void setOwnerPlayerId(String ownerPlayerId) { this.ownerPlayerId = ownerPlayerId; }

    /** Os 3 espacos de "Magic Item Attunement" da ficha 2024. */
    private List<String> attunedItems = new ArrayList<>(List.of("", "", ""));

    public CharacterSheet() {
        this.id = UUID.randomUUID().toString();
    }

    public CharacterSheet(String name) {
        this();
        this.name = name;
    }

    public static int abilityModifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    /** Bonus de proficiencia pelo nivel (igual em 2014 e 2024). */
    public int proficiencyBonus() {
        if (level <= 4) return 2;
        if (level <= 8) return 3;
        if (level <= 12) return 4;
        if (level <= 16) return 5;
        return 6;
    }

    /** Sistema de RPG da ficha (null = D&D 5e). Ver GameSystem. */
    private String system;
    /** Atributos/pericias/campos genericos dos outros sistemas (Tormenta20, Ordem Paranormal). */
    private java.util.LinkedHashMap<String, Integer> customAttributes = new java.util.LinkedHashMap<>();
    /** Pericia -> nivel de treino (0 = destreinado). */
    private java.util.LinkedHashMap<String, Integer> customSkills = new java.util.LinkedHashMap<>();
    /** Pericia -> outros bonus. */
    private java.util.LinkedHashMap<String, Integer> customSkillBonus = new java.util.LinkedHashMap<>();
    private java.util.LinkedHashMap<String, String> customFields = new java.util.LinkedHashMap<>();

    public String getSystem() { return system == null || system.isBlank() ? GameSystem.DND5E : system; }
    public void setSystem(String system) { this.system = GameSystem.DND5E.equals(system) ? null : system; }
    public java.util.Map<String, Integer> getCustomAttributes() {
        if (customAttributes == null) customAttributes = new java.util.LinkedHashMap<>();
        return customAttributes;
    }
    public java.util.Map<String, Integer> getCustomSkills() {
        if (customSkills == null) customSkills = new java.util.LinkedHashMap<>();
        return customSkills;
    }
    public java.util.Map<String, Integer> getCustomSkillBonus() {
        if (customSkillBonus == null) customSkillBonus = new java.util.LinkedHashMap<>();
        return customSkillBonus;
    }
    public java.util.Map<String, String> getCustomFields() {
        if (customFields == null) customFields = new java.util.LinkedHashMap<>();
        return customFields;
    }

    public String getId() { return id; }
    /** Ficha importada vira uma ficha NOVA (nao colide com a original se ela tambem estiver na mesa). */
    public void assignNewId() { this.id = UUID.randomUUID().toString(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(20, level)); }

    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }

    public int getDexterity() { return dexterity; }
    public void setDexterity(int dexterity) { this.dexterity = dexterity; }

    public int getConstitution() { return constitution; }
    public void setConstitution(int constitution) { this.constitution = constitution; }

    public int getIntelligence() { return intelligence; }
    public void setIntelligence(int intelligence) { this.intelligence = intelligence; }

    public int getWisdom() { return wisdom; }
    public void setWisdom(int wisdom) { this.wisdom = wisdom; }

    public int getCharisma() { return charisma; }
    public void setCharisma(int charisma) { this.charisma = charisma; }

    public Integer getHpCurrent() { return hpCurrent; }
    public void setHpCurrent(Integer hpCurrent) { this.hpCurrent = hpCurrent; }

    public Integer getHpMax() { return hpMax; }
    public void setHpMax(Integer hpMax) { this.hpMax = hpMax; }

    public Integer getArmorClass() { return armorClass; }
    public void setArmorClass(Integer armorClass) { this.armorClass = armorClass; }

    public String getPortraitImagePath() { return portraitImagePath; }
    public void setPortraitImagePath(String portraitImagePath) { this.portraitImagePath = portraitImagePath; }

    public String getPortraitImageAssetId() { return portraitImageAssetId; }
    public void setPortraitImageAssetId(String portraitImageAssetId) { this.portraitImageAssetId = portraitImageAssetId; }

    public Inventory getInventory() { return inventory; }

    public List<Feat> getFeats() { return feats; }
    public void addFeat(Feat feat) { feats.add(feat); }

    public List<CharacterTrait> getTraits() { return traits; }
    public void addTrait(CharacterTrait trait) { traits.add(trait); }
    public void removeTrait(CharacterTrait trait) { traits.remove(trait); }

    /** Valor bruto (FOR/DES/etc) da habilidade pedida, sem passar pelo
     * modificador -- usado por AbilityScorePrerequisite e por quem
     * precisa do numero "cru" em vez do modificador de +/-. */
    public int abilityScore(Ability ability) {
        return switch (ability) {
            case STRENGTH -> strength;
            case DEXTERITY -> dexterity;
            case CONSTITUTION -> constitution;
            case INTELLIGENCE -> intelligence;
            case WISDOM -> wisdom;
            case CHARISMA -> charisma;
        };
    }

    public Set<Ability> getSavingThrowProficiencies() { return savingThrowProficiencies; }

    public boolean isSavingThrowProficient(Ability ability) {
        return savingThrowProficiencies.contains(ability);
    }

    public void setSavingThrowProficient(Ability ability, boolean proficient) {
        if (proficient) {
            savingThrowProficiencies.add(ability);
        } else {
            savingThrowProficiencies.remove(ability);
        }
    }

    /** Bonus total de um teste de salvaguarda: modificador da habilidade, +bonus de proficiencia se for
     * proficiente nela, + o extra geral das salvaguardas (Manto da Protecao, Aura do Paladino...). */
    public int savingThrowBonus(Ability ability) {
        int bonus = abilityModifier(abilityScore(ability)) + saveBonusAll;
        return isSavingThrowProficient(ability) ? bonus + proficiencyBonus() : bonus;
    }

    // ---------------- extras das pericias e salvaguardas, e o "pau pra toda obra"

    /** Pericias com metade da proficiencia (Pau pra Toda Obra do Bardo), arredondada pra baixo. */
    private Set<Skill> skillJackOfAllTrades;
    /** Extra nas pericias de um atributo (ex.: +1 em todas as de DES). */
    private java.util.Map<Ability, Integer> skillBonusByAbility;
    /** Extra em todas as pericias. */
    private int skillBonusAll;
    /** Extra em todas as salvaguardas. */
    private int saveBonusAll;

    private Set<Skill> jack() {
        if (skillJackOfAllTrades == null) skillJackOfAllTrades = new LinkedHashSet<>();
        return skillJackOfAllTrades;
    }

    public boolean hasSkillJackOfAllTrades(Skill skill) { return jack().contains(skill); }

    /** Marca da pericia: 0 nada, 1 proficiente, 2 especialista, 3 pau pra toda obra. */
    public int skillLevel(Skill skill) {
        if (hasSkillExpertise(skill)) return 2;
        if (isSkillProficient(skill)) return 1;
        return hasSkillJackOfAllTrades(skill) ? 3 : 0;
    }

    public void setSkillLevel(Skill skill, int level) {
        skillProficiencies.remove(skill);
        skillExpertise.remove(skill);
        jack().remove(skill);
        switch (level) {
            case 1 -> skillProficiencies.add(skill);
            case 2 -> {
                skillProficiencies.add(skill);
                skillExpertise.add(skill);
            }
            case 3 -> jack().add(skill);
            default -> { }
        }
    }

    /** Um clique a mais na marca: nada -> proficiente -> especialista -> pau pra toda obra -> nada. */
    public int cycleSkillLevel(Skill skill) {
        int next = (skillLevel(skill) + 1) % 4;
        setSkillLevel(skill, next);
        return next;
    }

    public int getSkillBonusFor(Ability ability) {
        return skillBonusByAbility == null ? 0 : skillBonusByAbility.getOrDefault(ability, 0);
    }

    public void setSkillBonusFor(Ability ability, int bonus) {
        if (skillBonusByAbility == null) skillBonusByAbility = new java.util.EnumMap<>(Ability.class);
        if (bonus == 0) skillBonusByAbility.remove(ability);
        else skillBonusByAbility.put(ability, Math.max(-20, Math.min(20, bonus)));
    }

    public int getSkillBonusAll() { return skillBonusAll; }
    public void setSkillBonusAll(int bonus) { this.skillBonusAll = Math.max(-20, Math.min(20, bonus)); }

    public int getSaveBonusAll() { return saveBonusAll; }
    public void setSaveBonusAll(int bonus) { this.saveBonusAll = Math.max(-20, Math.min(20, bonus)); }

    public Set<Skill> getSkillProficiencies() { return skillProficiencies; }
    public Set<Skill> getSkillExpertise() { return skillExpertise; }

    public boolean isSkillProficient(Skill skill) { return skillProficiencies.contains(skill); }
    public boolean hasSkillExpertise(Skill skill) { return skillExpertise.contains(skill); }

    /** Tirar a proficiencia de uma pericia tambem tira a expertise dela --
     * nao existe expertise sem proficiencia nas regras de 5e. */
    public void setSkillProficient(Skill skill, boolean proficient) {
        if (proficient) {
            skillProficiencies.add(skill);
            jack().remove(skill);
        } else {
            skillProficiencies.remove(skill);
            skillExpertise.remove(skill);
        }
    }

    /** Dar expertise tambem garante a proficiencia (regra inversa do
     * metodo acima -- expertise sem proficiencia nao faz sentido). */
    public void setSkillExpertise(Skill skill, boolean expertise) {
        if (expertise) {
            skillExpertise.add(skill);
            skillProficiencies.add(skill);
            jack().remove(skill);
        } else {
            skillExpertise.remove(skill);
        }
    }

    /** Bonus total de um teste de pericia: modificador da habilidade da
     * pericia, + bonus de proficiencia em dobro se tiver expertise, +bonus
     * simples se so for proficiente, ou so o modificador se nao for
     * proficiente nem tiver expertise. */
    public int skillBonus(Skill skill) {
        // + extra das pericias desse atributo + extra de todas as pericias
        int bonus = abilityModifier(abilityScore(skill.getAbility())) + getSkillBonusFor(skill.getAbility()) + skillBonusAll;
        if (hasSkillExpertise(skill)) {
            return bonus + 2 * proficiencyBonus();
        }
        if (isSkillProficient(skill)) {
            return bonus + proficiencyBonus();
        }
        if (hasSkillJackOfAllTrades(skill)) {
            return bonus + proficiencyBonus() / 2;   // metade, arredondada pra baixo
        }
        return bonus;
    }

    public int getDeathSaveSuccesses() { return deathSaveSuccesses; }
    public void setDeathSaveSuccesses(int count) { this.deathSaveSuccesses = clampDeathSave(count); }

    public int getDeathSaveFailures() { return deathSaveFailures; }
    public void setDeathSaveFailures(int count) { this.deathSaveFailures = clampDeathSave(count); }

    public void resetDeathSaves() {
        deathSaveSuccesses = 0;
        deathSaveFailures = 0;
    }

    private static int clampDeathSave(int count) {
        return Math.max(0, Math.min(3, count));
    }

    /** O que um teste contra a morte fez (ver applyDeathSave). */
    public enum DeathSaveOutcome {
        /** 20 natural: volta com 1 PV. */
        REVIVED,
        SUCCESS,
        /** Terceiro sucesso: estavel (continua a 0 PV, inconsciente, sem rolar mais). */
        STABLE,
        FAILURE,
        /** Terceira falha. */
        DEAD,
        /** Nao estava a 0 PV (ou ja estava estavel/morto): nada mudou. */
        NONE
    }

    /**
     * Marca o resultado do d20 natural do teste contra a morte: 20 = volta com 1 PV (zera os testes e tira o
     * Inconsciente); 10+ = sucesso; 1 = duas falhas; o resto = uma falha. Tres sucessos = estavel (os testes
     * zeram); tres falhas = morto.
     */
    public DeathSaveOutcome applyDeathSave(int natural) {
        if (hpCurrent == null || hpCurrent > 0 || deathSaveFailures >= 3) return DeathSaveOutcome.NONE;
        if (natural >= 20) {
            heal(1);
            return DeathSaveOutcome.REVIVED;
        }
        if (natural >= 10) {
            setDeathSaveSuccesses(deathSaveSuccesses + 1);
            if (deathSaveSuccesses >= 3) {
                resetDeathSaves();
                return DeathSaveOutcome.STABLE;
            }
            return DeathSaveOutcome.SUCCESS;
        }
        setDeathSaveFailures(deathSaveFailures + (natural <= 1 ? 2 : 1));
        return deathSaveFailures >= 3 ? DeathSaveOutcome.DEAD : DeathSaveOutcome.FAILURE;
    }

    public List<Attack> getAttacks() { return attacks; }
    public void addAttack(Attack attack) { attacks.add(attack); }

    public List<CompendiumReference> getCompendiumAttachments() { return compendiumAttachments; }

    /** Anexa uma entrada do compendio (nao deixa anexar o mesmo id duas
     * vezes). */
    public void attachCompendiumEntry(CompendiumReference reference) {
        if (reference == null || reference.getEntryId() == null) {
            return;
        }
        boolean alreadyAttached = compendiumAttachments.stream()
                .anyMatch(existing -> reference.getEntryId().equals(existing.getEntryId()));
        if (!alreadyAttached) {
            compendiumAttachments.add(reference);
        }
    }

    public void detachCompendiumEntry(String entryId) {
        compendiumAttachments.removeIf(ref -> ref.getEntryId() != null && ref.getEntryId().equals(entryId));
    }

    public SpellSlots getSpellSlots() { return spellSlots; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getHeight() { return height; }
    public void setHeight(String height) { this.height = height; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public String getEyes() { return eyes; }
    public void setEyes(String eyes) { this.eyes = eyes; }

    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }

    public String getHair() { return hair; }
    public void setHair(String hair) { this.hair = hair; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public String getPersonalityTraits() { return personalityTraits; }
    public void setPersonalityTraits(String personalityTraits) { this.personalityTraits = personalityTraits; }

    public String getIdeals() { return ideals; }
    public void setIdeals(String ideals) { this.ideals = ideals; }

    public String getBonds() { return bonds; }
    public void setBonds(String bonds) { this.bonds = bonds; }

    public String getFlaws() { return flaws; }
    public void setFlaws(String flaws) { this.flaws = flaws; }

    public String getBackstory() { return backstory; }
    public void setBackstory(String backstory) { this.backstory = backstory; }

    public List<String> getLanguages() { return languages; }

    /** Ignora null/branco e nao duplica um idioma ja presente (comparando
     * sem diferenciar maiusculas, pra "Comum" e "comum" nao entrarem duas
     * vezes por descuido de digitacao). */
    public void addLanguage(String language) {
        if (language == null || language.isBlank()) {
            return;
        }
        String trimmed = language.trim();
        boolean alreadyPresent = languages.stream().anyMatch(existing -> existing.equalsIgnoreCase(trimmed));
        if (!alreadyPresent) {
            languages.add(trimmed);
        }
    }

    // ------------------------------------------------------------------
    // Campos da ficha 2024 + tudo que a ficha impressa obriga o jogador a
    // calcular de cabeca (aqui calculado sempre a partir dos atributos,
    // nunca guardado -- assim nao tem como ficar desatualizado).
    // ------------------------------------------------------------------

    public String getSubclass() { return subclass; }
    public void setSubclass(String subclass) { this.subclass = subclass; }

    public int getExperiencePoints() { return experiencePoints; }
    public void setExperiencePoints(int xp) { this.experiencePoints = Math.max(0, xp); }

    public int getHpTemp() { return hpTemp; }
    public void setHpTemp(int hpTemp) { this.hpTemp = Math.max(0, hpTemp); }

    public String getHitDie() { return hitDie; }
    public void setHitDie(String hitDie) { this.hitDie = hitDie; }

    // ---------------- dados de vida por tipo (multiclasse: d10 x4 + d8 x3)

    public static final List<String> HIT_DIE_TYPES = List.of("d6", "d8", "d10", "d12");

    /** Quantos dados de cada tipo o personagem tem. Vazio = ficha antiga: {@code hitDie} x nivel. */
    private java.util.Map<String, Integer> hitDicePool;
    /** Gastos de cada tipo. */
    private java.util.Map<String, Integer> hitDiceSpentByDie;

    /** Total de cada tipo, do maior dado pro menor. */
    public java.util.Map<String, Integer> hitDiceTotals() {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        boolean any = hitDicePool != null && hitDicePool.values().stream().anyMatch(n -> n != null && n > 0);
        for (int i = HIT_DIE_TYPES.size() - 1; i >= 0; i--) {
            String die = HIT_DIE_TYPES.get(i);
            int n = any ? hitDicePool.getOrDefault(die, 0) : (die.equals(hitDie) ? level : 0);
            if (n > 0) out.put(die, n);
        }
        if (!any && !out.containsKey(hitDie) && level > 0) out.put(hitDie == null ? "d8" : hitDie, level);
        return out;
    }

    /** Define quantos dados de um tipo o personagem tem (0 tira o tipo). */
    public void setHitDiceCount(String die, int count) {
        if (hitDicePool == null || hitDicePool.values().stream().noneMatch(n -> n != null && n > 0)) {
            hitDicePool = new java.util.LinkedHashMap<>(hitDiceTotals());   // comeca do que a ficha antiga dizia
        }
        hitDicePool.put(die, Math.max(0, Math.min(40, count)));
        hitDicePool.values().removeIf(n -> n == null || n <= 0);
        if (hitDicePool.isEmpty()) hitDicePool.put(die, 0);
        if (spentByDie().getOrDefault(die, 0) > Math.max(0, count)) spentByDie().put(die, Math.max(0, count));
    }

    private java.util.Map<String, Integer> spentByDie() {
        if (hitDiceSpentByDie == null) {
            hitDiceSpentByDie = new java.util.LinkedHashMap<>();
            // ficha antiga: os gastos eram um numero so -> entram no maior dado
            int legacy = hitDiceSpent;
            for (var e : hitDiceTotals().entrySet()) {
                int take = Math.min(legacy, e.getValue());
                if (take > 0) hitDiceSpentByDie.put(e.getKey(), take);
                legacy -= take;
            }
        }
        return hitDiceSpentByDie;
    }

    public int hitDiceSpentOf(String die) {
        return Math.min(spentByDie().getOrDefault(die, 0), hitDiceTotals().getOrDefault(die, 0));
    }

    public int hitDiceRemainingOf(String die) {
        return hitDiceTotals().getOrDefault(die, 0) - hitDiceSpentOf(die);
    }

    public int hitDiceMax() {
        return hitDiceTotals().values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getHitDiceSpent() {
        return hitDiceTotals().keySet().stream().mapToInt(this::hitDiceSpentOf).sum();
    }

    /** Gastos no total (os do maior dado primeiro) -- usado ao recortar quando o nivel cai. */
    public void setHitDiceSpent(int spent) {
        int left = Math.max(0, spent);
        java.util.Map<String, Integer> map = spentByDie();
        map.clear();
        for (var e : hitDiceTotals().entrySet()) {
            int take = Math.min(left, e.getValue());
            if (take > 0) map.put(e.getKey(), take);
            left -= take;
        }
        hitDiceSpent = getHitDiceSpent();
    }

    public int hitDiceRemaining() { return hitDiceMax() - getHitDiceSpent(); }

    /** Gasta um dado desse tipo. false se nao sobrou nenhum. */
    public boolean spendHitDie(String die) {
        if (die == null || hitDiceRemainingOf(die) <= 0) return false;
        spentByDie().merge(die, 1, Integer::sum);
        hitDiceSpent = getHitDiceSpent();
        return true;
    }

    /** Gasta o maior dado que sobrou; devolve qual foi (ou null). */
    public String spendLargestHitDie() {
        for (String die : hitDiceTotals().keySet()) {
            if (spendHitDie(die)) return die;
        }
        return null;
    }

    // ---------------- iniciativa com dados extras

    /** Dados somados a iniciativa ("1d4", "1d8"), de habilidades como Inspiracao de Bardo ou Gloom Stalker. */
    private String initiativeDice;

    public String getInitiativeDice() { return initiativeDice == null ? "" : initiativeDice; }
    public void setInitiativeDice(String dice) {
        this.initiativeDice = dice == null || dice.isBlank() ? null : dice.replaceAll("\\s+", "");
    }

    /** CA da ficha antiga com a caixa "Escudo (+2)": passa os +2 pra CA e desliga a caixa (o escudo vira item). */
    public boolean migrateShieldIntoArmorClass() {
        if (!shieldEquipped) return false;
        int base = armorClass != null ? armorClass : 10 + abilityModifier(dexterity);
        armorClass = base + 2;
        shieldEquipped = false;
        return true;
    }

    public boolean hasHeroicInspiration() { return heroicInspiration; }
    public void setHeroicInspiration(boolean value) { this.heroicInspiration = value; }

    public boolean isShieldEquipped() { return shieldEquipped; }
    public void setShieldEquipped(boolean value) { this.shieldEquipped = value; }

    /** CA final: {@code armorClass} (ou 10 + DES se ainda nao preenchida)
     * + 2 com escudo. */
    public int totalArmorClass() {
        int base = armorClass != null ? armorClass : 10 + abilityModifier(dexterity);
        // + efeitos ativos (magia Escudo +5, Escudo da Fe +2, "Anel [ca+1]").
        return base + (shieldEquipped ? 2 : 0) + EffectModifiers.acBonus(getEffects());
    }

    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int bonus) { this.initiativeBonus = bonus; }

    public int initiative() {
        GameSystem gs = GameSystem.of(this);
        if (gs.isCustom()) return gs.initiativeBonus(this) + initiativeBonus;
        return abilityModifier(dexterity) + initiativeBonus;
    }

    public int passivePerception() { return 10 + skillBonus(Skill.PERCEPTION); }

    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }

    /** Lembrete de deslocamento com exaustao (cada nivel tira 1,5 m). */
    public String speedWithExhaustion() {
        if (exhaustionLevel == 0) return speed;
        String penalty = String.format(java.util.Locale.ROOT, "%.1f", exhaustionLevel * 1.5).replace('.', ',');
        return speed + " (-" + penalty + " m exaustao)";
    }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public boolean isArmorTrainingLight() { return armorTrainingLight; }
    public void setArmorTrainingLight(boolean v) { this.armorTrainingLight = v; }
    public boolean isArmorTrainingMedium() { return armorTrainingMedium; }
    public void setArmorTrainingMedium(boolean v) { this.armorTrainingMedium = v; }
    public boolean isArmorTrainingHeavy() { return armorTrainingHeavy; }
    public void setArmorTrainingHeavy(boolean v) { this.armorTrainingHeavy = v; }
    public boolean isArmorTrainingShields() { return armorTrainingShields; }
    public void setArmorTrainingShields(boolean v) { this.armorTrainingShields = v; }

    public String getWeaponProficiencies() { return weaponProficiencies; }
    public void setWeaponProficiencies(String v) { this.weaponProficiencies = v; }

    public String getToolProficiencies() { return toolProficiencies; }
    public void setToolProficiencies(String v) { this.toolProficiencies = v; }

    public Ability getSpellcastingAbility() { return spellcastingAbility; }
    public void setSpellcastingAbility(Ability ability) { this.spellcastingAbility = ability; }

    public boolean isSpellcaster() { return spellcastingAbility != null; }

    public int spellcastingModifier() {
        return spellcastingAbility == null ? 0 : abilityModifier(abilityScore(spellcastingAbility));
    }

    /** CD de resistencia de magia = 8 + mod. de conjuracao + proficiencia. */
    public int spellSaveDc() { return 8 + spellcastingModifier() + proficiencyBonus(); }

    /** Bonus de ataque de magia = mod. de conjuracao + proficiencia. */
    public int spellAttackBonus() { return spellcastingModifier() + proficiencyBonus(); }

    public List<PreparedSpell> getSpells() { return spells; }

    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }

    /** Sempre 3 posicoes (completa com "" se uma ficha antiga/editada a mao
     * vier com menos). */
    public List<String> getAttunedItems() {
        while (attunedItems.size() < 3) attunedItems.add("");
        return attunedItems;
    }

    /** Tracos filtrados pela categoria ("class", "species", "feat"...),
     * pras tres caixas separadas da ficha 2024 (Class Features, Species
     * Traits, Feats). "other" junta todo o resto (sem categoria, ou
     * categorias antigas tipo "attack"/"spell"), pra nenhum traco de
     * ficha antiga sumir da tela. */
    public List<CharacterTrait> traitsInCategory(String category) {
        List<String> main = List.of("class", "species", "feat");
        List<CharacterTrait> result = new ArrayList<>();
        for (CharacterTrait t : traits) {
            String c = t.getCategory() == null ? "" : t.getCategory().toLowerCase();
            String bucket = main.contains(c) ? c : "other";
            if (bucket.equalsIgnoreCase(category)) result.add(t);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Acoes de mesa: dano, cura, descanso.
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Condicoes, efeitos, exaustao, concentracao
    // ------------------------------------------------------------------

    public List<String> getConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return conditions;
    }

    public void setConditions(List<String> list) {
        conditions = list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public boolean hasCondition(com.vttcaseiro.model.Condition c) {
        return getConditions().contains(c.name());
    }

    public void setCondition(com.vttcaseiro.model.Condition c, boolean active) {
        if (active && !hasCondition(c)) getConditions().add(c.name());
        if (!active) getConditions().remove(c.name());
    }

    public List<String> getEffects() {
        if (effects == null) effects = new ArrayList<>();
        return effects;
    }

    public void setEffects(List<String> list) {
        effects = list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public int getExhaustionLevel() { return exhaustionLevel; }
    public void setExhaustionLevel(int level) { this.exhaustionLevel = Math.max(0, Math.min(6, level)); }

    public List<String> getDamageResistances() {
        if (damageResistances == null) damageResistances = new ArrayList<>();
        return damageResistances;
    }
    public void setDamageResistances(List<String> v) { damageResistances = v == null ? new ArrayList<>() : new ArrayList<>(v); }

    public List<String> getDamageImmunities() {
        if (damageImmunities == null) damageImmunities = new ArrayList<>();
        return damageImmunities;
    }
    public void setDamageImmunities(List<String> v) { damageImmunities = v == null ? new ArrayList<>() : new ArrayList<>(v); }

    public List<String> getDamageVulnerabilities() {
        if (damageVulnerabilities == null) damageVulnerabilities = new ArrayList<>();
        return damageVulnerabilities;
    }
    public void setDamageVulnerabilities(List<String> v) { damageVulnerabilities = v == null ? new ArrayList<>() : new ArrayList<>(v); }

    public DamageTypes.Defenses defenses() {
        return DamageTypes.defenses(getDamageResistances(), getDamageImmunities(), getDamageVulnerabilities());
    }

    public List<Resource> getResources() {
        if (resources == null) resources = new ArrayList<>();
        return resources;
    }

    public java.util.Optional<Resource> findResource(String name) {
        for (Resource r : getResources()) if (r.getName().equalsIgnoreCase(name == null ? "" : name.trim())) {
            return java.util.Optional.of(r);
        }
        return java.util.Optional.empty();
    }

    /** Recupera os recursos que recarregam neste descanso. Devolve os nomes recuperados. */
    public List<String> recoverResources(boolean longRest) {
        List<String> names = new ArrayList<>();
        for (Resource r : getResources()) if (r.recover(longRest)) names.add(r.getName());
        return names;
    }

    public String getConcentratingOn() { return concentratingOn; }
    public void setConcentratingOn(String spell) {
        this.concentratingOn = spell == null || spell.isBlank() ? null : spell.trim();
    }

    /**
     * O que condicoes e exaustao fazem com um teste de d20 (regras 2024).
     * {@code chosen} e o modo que o jogador escolheu na ficha -- entra na
     * conta como mais uma fonte de vantagem/desvantagem.
     */
    public RollAdjustment rollAdjustment(RollKind kind, Ability ability, RollMode chosen) {
        return CombatRules.rollAdjustment(getConditions(), exhaustionLevel, kind, ability, chosen,
                getEffects(), CombatRules::rollDice);
    }

    /**
     * Resultado de aplicar dano, com tudo que a mesa precisa avisar:
     * caiu a 0 (fica Inconsciente), morte instantanea por dano massivo
     * (o que sobra depois de zerar &gt;= PV maximo), CD do teste de
     * concentracao, e se o dano veio com o personagem ja caido (= uma
     * falha no teste contra a morte).
     */
    public record DamageOutcome(int hpAfter, int absorbedByTemp, boolean droppedToZero,
                                boolean instantDeath, Integer concentrationDc, boolean damagedWhileDown) {
    }

    /** Dano tira primeiro do PV temporario, depois do atual (nunca abaixo
     * de 0). Devolve o PV atual depois do dano. */
    public int applyDamage(int amount) {
        return takeDamage(amount).hpAfter();
    }

    /** Versao completa de {@link #applyDamage(int)} (ver DamageOutcome). */
    public DamageOutcome takeDamage(int amount) {
        int before = hpCurrent == null ? (hpMax == null ? 0 : hpMax) : hpCurrent;
        if (amount <= 0) return new DamageOutcome(before, 0, false, false, null, false);
        int fromTemp = Math.min(hpTemp, amount);
        hpTemp -= fromTemp;
        int rest = amount - fromTemp;
        hpCurrent = Math.max(0, before - rest);
        boolean wasDown = before == 0;
        boolean dropped = !wasDown && hpCurrent == 0;
        int overflow = rest - before;
        boolean instantDeath = !wasDown && hpMax != null && hpMax > 0 && overflow >= hpMax;
        if (hpCurrent == 0 && rest > 0) {
            setCondition(com.vttcaseiro.model.Condition.UNCONSCIOUS, true);
        }
        if (wasDown && rest > 0) {
            setDeathSaveFailures(deathSaveFailures + 1);
        }
        Integer dc = null;
        if (concentratingOn != null) {
            if (hpCurrent == 0) {
                concentratingOn = null; // Inconsciente perde a concentracao
            } else {
                dc = Math.min(30, Math.max(10, amount / 2));
            }
        }
        return new DamageOutcome(hpCurrent, fromTemp, dropped, instantDeath, dc, wasDown && rest > 0);
    }

    /** Cura ate o maximo (se o maximo nao estiver preenchido, sem teto).
     * Sair de 0 PV zera os testes contra a morte e tira o Inconsciente. */
    public int heal(int amount) {
        if (amount <= 0) return hpCurrent == null ? 0 : hpCurrent;
        int current = hpCurrent == null ? 0 : hpCurrent;
        int healed = current + amount;
        hpCurrent = hpMax == null ? healed : Math.min(hpMax, healed);
        if (current == 0 && hpCurrent > 0) {
            resetDeathSaves();
            setCondition(com.vttcaseiro.model.Condition.UNCONSCIOUS, false);
        }
        return hpCurrent;
    }

    /** Gasta um dado de vida (descanso curto). Devolve false se nao tiver
     * nenhum sobrando -- quem chama rola o dado e chama heal(). */
    public boolean spendHitDie() {
        return spendLargestHitDie() != null;
    }

    /** Descanso longo nas regras de 2024: PV no maximo, PV temporario some,
     * recupera todos os dados de vida gastos, repoe os slots de magia e
     * zera os testes contra a morte. */
    public void longRest() {
        if (hpMax != null) hpCurrent = hpMax;
        hpTemp = 0;
        hitDiceSpent = 0;
        if (hitDiceSpentByDie != null) hitDiceSpentByDie.clear();
        spellSlots.restoreAll();
        recoverResources(true);
        resetDeathSaves();
        // 2024: descanso longo tira 1 nivel de exaustao.
        if (exhaustionLevel > 0) exhaustionLevel--;
        if (hpCurrent != null && hpCurrent > 0) setCondition(com.vttcaseiro.model.Condition.UNCONSCIOUS, false);
    }
}

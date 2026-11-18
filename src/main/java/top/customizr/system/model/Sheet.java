package top.customizr.system.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "character_sheets")
@Getter
public class Sheet {

    @Id
    private UUID sheetId;

    @OneToOne(optional = false)
    @JoinColumn(name = "character_id", nullable = false, unique = true)
    private Character character;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterClass> classes = new ArrayList<>();

    @Embedded
    private Attributes attributes;

    // Valores de combate (Integer: fichas antigas no banco ficam com null = ainda nao preenchido)
    @Column(name = "hp_max")
    private Integer hpMax;

    @Column(name = "hp_current")
    private Integer hpCurrent;

    @Column(name = "hp_temp")
    private Integer hpTemp;

    @Column(name = "armor_class")
    private Integer armorClass;

    @Column(name = "speed_ft")
    private Integer speedFt;

    // ------------------------------------------------------------------ ficha completa

    @ElementCollection
    @CollectionTable(name = "sheet_skill_proficiencies", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "skill", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Skill> skillProficiencies = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_skill_expertise", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "skill", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Skill> skillExpertise = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_saving_throws", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "ability", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<AbilityScore> savingThrowProficiencies = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "sheet_conditions", joinColumns = @JoinColumn(name = "sheet_id"))
    @Column(name = "condition_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Condition> conditions = new LinkedHashSet<>();

    private Integer exhaustion;

    @Column(name = "death_save_successes")
    private Integer deathSaveSuccesses;

    @Column(name = "death_save_failures")
    private Integer deathSaveFailures;

    @Column(name = "hit_dice_spent")
    private Integer hitDiceSpent;

    /** Espacos de magia gastos por nivel (os totais vem do {@link SpellSlotCalculator}). */
    @ElementCollection
    @CollectionTable(name = "sheet_spent_slots", joinColumns = @JoinColumn(name = "sheet_id"))
    @MapKeyColumn(name = "spell_level")
    @Column(name = "spent", nullable = false)
    private Map<Integer, Integer> spentSpellSlots = new HashMap<>();

    @Column(name = "pact_slots_spent")
    private Integer pactSlotsSpent;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<SheetAttack> attacks = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("characterLevel")
    private List<CharacterFeat> catalogFeats = new ArrayList<>();

    /** Usos gastos dos recursos de classe (chave "classe.recurso"; o maximo vem do {@link ClassResources}). */
    @ElementCollection
    @CollectionTable(name = "sheet_spent_resources", joinColumns = @JoinColumn(name = "sheet_id"))
    @MapKeyColumn(name = "resource_key", length = 80)
    @Column(name = "spent", nullable = false)
    private Map<String, Integer> spentResources = new HashMap<>();

    /** Recursos criados a mao (cargas de item, usos de talento...). */
    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<SheetResource> customResources = new ArrayList<>();

    /** Inspiracao Heroica (2024): tem ou nao tem; gastar = rolar um dado de novo. */
    @Column(name = "heroic_inspiration")
    private Boolean heroicInspiration;

    protected Sheet() {}

    public Sheet(Character character) {
        this.sheetId = UUID.randomUUID();
        this.character = character;
    }

    public CharacterClass addClass(DndClass dndClass, int level) {
        boolean alreadyHasClass = classes.stream()
                .anyMatch(characterClass -> characterClass.getDndClass().getClassId().equals(dndClass.getClassId()));
        if (alreadyHasClass) {
            throw new IllegalArgumentException("Character already has this class");
        }
        CharacterClass characterClass = new CharacterClass(this, dndClass, level);
        classes.add(characterClass);
        return characterClass;
    }

    // ------------------------------------------------------------------ subir de nivel

    public static final int MAX_LEVEL = 20;

    /** Sobe 1 nivel na classe (ou entra nela no nivel 1, se for multiclasse nova). */
    public CharacterClass gainClassLevel(DndClass dndClass) {
        if (getTotalLevel() >= MAX_LEVEL) {
            throw new IllegalStateException("The character is already level " + MAX_LEVEL);
        }
        CharacterClass existing = classOf(dndClass);
        if (existing == null) return addClass(dndClass, 1);
        existing.setLevel(existing.getLevel() + 1);
        return existing;
    }

    /** A classe da ficha que e esta {@link DndClass}, ou null. */
    public CharacterClass classOf(DndClass dndClass) {
        return classes.stream().filter(c -> c.getDndClass().getClassId().equals(dndClass.getClassId()))
                .findFirst().orElse(null);
    }

    /**
     * Como as classes ficariam depois de subir na {@code dndClass}: copias soltas (fora da ficha, nao vao pro banco),
     * pra calcular espacos de magia e recursos do proximo nivel sem mexer na ficha.
     */
    public List<CharacterClass> classesAfterLevelUp(DndClass dndClass) {
        List<CharacterClass> copy = new ArrayList<>();
        boolean found = false;
        for (CharacterClass c : classes) {
            boolean target = c.getDndClass().getClassId().equals(dndClass.getClassId());
            found |= target;
            copy.add(new CharacterClass(this, c.getDndClass(), Math.min(MAX_LEVEL, c.getLevel() + (target ? 1 : 0))));
        }
        if (!found) copy.add(new CharacterClass(this, dndClass, 1));
        return copy;
    }

    /** PV ganho no nivel: soma no maximo e no atual (quem ainda nao tinha PV comeca com o ganho). */
    public void raiseMaxHp(int gain) {
        if (gain < 1) throw new IllegalArgumentException("Hit point gain must be at least 1");
        hpMax = (hpMax == null ? 0 : hpMax) + gain;
        hpCurrent = Math.min(hpMax, (hpCurrent == null ? 0 : hpCurrent) + gain);
    }

    /** Aumento de atributo (Aumento de Valor de Atributo, talento, Dadiva Epica), sem passar de {@code cap}. */
    public void increaseAbilities(Map<AbilityScore, Integer> increases, int cap) {
        if (increases == null || increases.isEmpty()) return;
        if (attributes == null) throw new IllegalStateException("Set the ability scores before increasing them");
        int[] v = {attributes.getStrength(), attributes.getDexterity(), attributes.getConstitution(),
                attributes.getIntelligence(), attributes.getWisdom(), attributes.getCharisma()};
        for (Map.Entry<AbilityScore, Integer> e : increases.entrySet()) {
            int amount = e.getValue() == null ? 0 : e.getValue();
            if (e.getKey() == null || amount < 0) throw new IllegalArgumentException("Invalid ability increase");
            int i = e.getKey().ordinal();
            if (v[i] + amount > cap) {
                throw new IllegalArgumentException(e.getKey().name().toLowerCase() + " cannot go above " + cap);
            }
            v[i] += amount;
        }
        attributes = new Attributes(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    @Transient
    public List<SpellSlotCount> getSpellSlots() {
        return SpellSlotCalculator.calculate(classes);
    }

    public void updateAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    // ------------------------------------------------------------------ valores derivados

    /** Soma dos niveis de todas as classes (multiclasse conta tudo). */
    @Transient
    public int getTotalLevel() {
        return classes.stream().mapToInt(CharacterClass::getLevel).sum();
    }

    /** Bonus de proficiencia pelo nivel total: +2 (1-4), +3 (5-8), +4 (9-12), +5 (13-16), +6 (17-20). */
    @Transient
    public int getProficiencyBonus() {
        int level = Math.max(1, getTotalLevel());
        return 2 + (Math.min(level, 20) - 1) / 4;
    }

    @Transient
    public int getInitiative() {
        return attributes == null ? 0 : attributes.getModifier("dexterity");
    }

    /**
     * Capacidade de carga (2014 e 2024): FOR x 15 lb, vezes o tamanho da especie (Miudo 0,5; Pequeno/Medio 1;
     * Grande 2; Enorme 4; Imenso 8). Nulo sem atributos.
     */
    @Transient
    public Integer getCarryingCapacityLb() {
        return attributes == null ? null : (int) Math.floor(attributes.getStrength() * 15 * sizeMultiplier());
    }

    /** Empurrar, arrastar ou erguer: o dobro da capacidade (FOR x 30 lb, vezes o tamanho). */
    @Transient
    public Integer getPushDragLiftLb() {
        Integer capacity = getCarryingCapacityLb();
        return capacity == null ? null : capacity * 2;
    }

    private double sizeMultiplier() {
        String size = character == null || character.getSpecies() == null ? null : character.getSpecies().getSize();
        if (size == null || size.isBlank()) return 1;
        // "Small or Medium": vale o primeiro (os dois contam como 1)
        return switch (size.trim().split("\\s+")[0].toLowerCase()) {
            case "tiny", "t" -> 0.5;
            case "large", "l" -> 2;
            case "huge", "h" -> 4;
            case "gargantuan", "g" -> 8;
            default -> 1;
        };
    }

    /** Percepcao passiva: 10 + bonus de Percepcao (com proficiencia/especializacao). */
    @Transient
    public int getPassivePerception() {
        return 10 + skillBonus(Skill.PERCEPTION);
    }

    /**
     * PV maximo sugerido pelas regras: no 1o nivel da primeira classe, dado cheio + CON; nos outros
     * niveis, a media do dado (metade + 1) + CON. Minimo de 1 PV por nivel.
     * Retorna null se o personagem ainda nao tem classe.
     */
    @Transient
    public Integer getSuggestedHpMax() {
        if (classes.isEmpty()) {
            return null;
        }
        int con = attributes == null ? 0 : attributes.getModifier("constitution");
        int total = 0;
        boolean first = true;
        for (CharacterClass characterClass : classes) {
            int die = Math.max(1, characterClass.getDndClass().getHitDie());
            for (int level = 1; level <= characterClass.getLevel(); level++) {
                int roll = first ? die : die / 2 + 1;
                total += Math.max(1, roll + con);
                first = false;
            }
        }
        return total;
    }

    public void updateCombatStats(Integer hpMax, Integer hpCurrent, Integer hpTemp, Integer armorClass, Integer speedFt) {
        requireNonNegative(hpMax, "Max HP");
        requireNonNegative(hpCurrent, "Current HP");
        requireNonNegative(hpTemp, "Temporary HP");
        requireNonNegative(armorClass, "Armor class");
        requireNonNegative(speedFt, "Speed");
        Integer max = hpMax != null ? hpMax : this.hpMax;
        Integer current = hpCurrent != null ? hpCurrent : this.hpCurrent;
        if (max != null && current != null && current > max) {
            throw new IllegalArgumentException("Current HP cannot exceed max HP");
        }
        if (hpMax != null) this.hpMax = hpMax;
        if (hpCurrent != null) this.hpCurrent = hpCurrent;
        else if (hpMax != null && this.hpCurrent == null) this.hpCurrent = hpMax;
        if (hpTemp != null) this.hpTemp = hpTemp;
        if (armorClass != null) this.armorClass = armorClass;
        if (speedFt != null) this.speedFt = speedFt;
    }

    /** Dano: gasta o PV temporario primeiro, o PV atual nao fica abaixo de 0. */
    public void applyDamage(int amount) {
        requireHp();
        if (amount < 0) {
            throw new IllegalArgumentException("Damage must be positive");
        }
        int temp = hpTemp == null ? 0 : hpTemp;
        int absorbed = Math.min(temp, amount);
        hpTemp = temp - absorbed;
        hpCurrent = Math.max(0, hpCurrent - (amount - absorbed));
    }

    /** Cura: nao passa do PV maximo. */
    public void heal(int amount) {
        requireHp();
        if (amount < 0) {
            throw new IllegalArgumentException("Healing must be positive");
        }
        boolean wasDown = hpCurrent == 0;
        hpCurrent = Math.min(hpMax, hpCurrent + amount);
        if (wasDown && hpCurrent > 0) {
            resetDeathSaves();
            character.revive();
        }
    }

    /** PV temporario nao acumula: fica o maior entre o atual e o novo. */
    public void grantTemporaryHp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Temporary HP must be positive");
        }
        hpTemp = Math.max(hpTemp == null ? 0 : hpTemp, amount);
    }

    private void requireHp() {
        if (hpMax == null || hpCurrent == null) {
            throw new IllegalStateException("Set the character's max HP first");
        }
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    // ------------------------------------------------------------------ pericias e salvaguardas

    public int abilityModifier(AbilityScore ability) {
        return attributes == null ? 0 : attributes.getModifier(ability.getKey());
    }

    public int skillBonus(Skill skill) {
        int bonus = abilityModifier(skill.getAbility());
        if (skillExpertise.contains(skill)) bonus += 2 * getProficiencyBonus();
        else if (skillProficiencies.contains(skill)) bonus += getProficiencyBonus();
        return bonus;
    }

    public int savingThrowBonus(AbilityScore ability) {
        return abilityModifier(ability) + (savingThrowProficiencies.contains(ability) ? getProficiencyBonus() : 0);
    }

    /** Especializacao exige proficiencia na mesma pericia. */
    public void updateProficiencies(Set<Skill> skills, Set<Skill> expertise, Set<AbilityScore> savingThrows) {
        Set<Skill> proficient = skills == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(nonEmpty(skills, Skill.class));
        Set<Skill> experts = expertise == null ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(nonEmpty(expertise, Skill.class));
        if (!proficient.containsAll(experts)) {
            throw new IllegalArgumentException("Expertise requires proficiency in the same skill");
        }
        skillProficiencies.clear();
        skillProficiencies.addAll(proficient);
        skillExpertise.clear();
        skillExpertise.addAll(experts);
        savingThrowProficiencies.clear();
        if (savingThrows != null) savingThrowProficiencies.addAll(savingThrows);
    }

    private static <E extends Enum<E>> Set<E> nonEmpty(Set<E> set, Class<E> type) {
        return set.isEmpty() ? EnumSet.noneOf(type) : set;
    }

    // ------------------------------------------------------------------ condicoes e exaustao

    public int getExhaustion() {
        return exhaustion == null ? 0 : exhaustion;
    }

    /** Regra de 2024: cada nivel de exaustao tira 2 dos testes de d20 e 5 pes do deslocamento; 6 = morte. */
    public void updateConditions(Set<Condition> conditions, Integer exhaustion) {
        if (exhaustion != null && (exhaustion < 0 || exhaustion > 6)) {
            throw new IllegalArgumentException("Exhaustion must be between 0 and 6");
        }
        if (conditions != null) {
            this.conditions.clear();
            this.conditions.addAll(conditions);
        }
        if (exhaustion != null) {
            this.exhaustion = exhaustion;
            if (exhaustion == 6) character.die();
        }
    }

    @Transient
    public int getD20Penalty() {
        return 2 * getExhaustion();
    }

    @Transient
    public Integer getCurrentSpeedFt() {
        return speedFt == null ? null : Math.max(0, speedFt - 5 * getExhaustion());
    }

    // ------------------------------------------------------------------ testes contra a morte

    public int getDeathSaveSuccesses() {
        return deathSaveSuccesses == null ? 0 : deathSaveSuccesses;
    }

    public int getDeathSaveFailures() {
        return deathSaveFailures == null ? 0 : deathSaveFailures;
    }

    public enum DeathSaveOutcome { SUCCESS, FAILURE, STABLE, DEAD, REVIVED }

    /**
     * Teste contra a morte (so com 0 PV): 10+ sucesso; 1 = duas falhas; 20 = volta com 1 PV.
     * Tres sucessos = estavel; tres falhas = morto.
     */
    public DeathSaveOutcome deathSave(int naturalD20) {
        if (hpCurrent == null || hpCurrent > 0) {
            throw new IllegalStateException("Death saves are only rolled at 0 hit points");
        }
        if (getDeathSaveFailures() >= 3 || getDeathSaveSuccesses() >= 3) {
            throw new IllegalStateException("The character is already stable or dead");
        }
        if (naturalD20 == 20) {
            heal(1);
            return DeathSaveOutcome.REVIVED;
        }
        if (naturalD20 >= 10) {
            deathSaveSuccesses = getDeathSaveSuccesses() + 1;
            return deathSaveSuccesses >= 3 ? DeathSaveOutcome.STABLE : DeathSaveOutcome.SUCCESS;
        }
        deathSaveFailures = Math.min(3, getDeathSaveFailures() + (naturalD20 == 1 ? 2 : 1));
        if (deathSaveFailures >= 3) {
            character.die();
            return DeathSaveOutcome.DEAD;
        }
        return DeathSaveOutcome.FAILURE;
    }

    private void resetDeathSaves() {
        deathSaveSuccesses = 0;
        deathSaveFailures = 0;
    }

    // ------------------------------------------------------------------ espacos de magia

    public int spentSlots(SpellSlotPool pool, int spellLevel) {
        if (pool == SpellSlotPool.PACT) return pactSlotsSpent == null ? 0 : pactSlotsSpent;
        return spentSpellSlots.getOrDefault(spellLevel, 0);
    }

    public void spendSpellSlot(SpellSlotPool pool, int spellLevel) {
        SpellSlotCount slot = getSpellSlots().stream()
                .filter(s -> s.pool() == pool && s.spellLevel() == spellLevel)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The character has no level " + spellLevel + " "
                        + pool.name().toLowerCase() + " slots"));
        int spent = spentSlots(pool, spellLevel);
        if (spent >= slot.totalSlots()) {
            throw new IllegalStateException("No level " + spellLevel + " slots left");
        }
        if (pool == SpellSlotPool.PACT) pactSlotsSpent = spent + 1;
        else spentSpellSlots.put(spellLevel, spent + 1);
    }

    // ------------------------------------------------------------------ dados de vida e descanso

    public int getHitDiceSpent() {
        return hitDiceSpent == null ? 0 : hitDiceSpent;
    }

    @Transient
    public int getHitDiceRemaining() {
        return Math.max(0, getTotalLevel() - getHitDiceSpent());
    }

    /** Resultado do descanso curto: dados gastos (com o valor rolado) e PV recuperado. */
    public record ShortRestResult(List<String> rolls, int healed, List<String> recovered) {}

    /**
     * Descanso curto: gasta dados de vida (o maior primeiro), cada um cura dado + CON (minimo 0),
     * e recupera os espacos de Magia de Pacto.
     */
    public ShortRestResult shortRest(int hitDice, IntUnaryOperator die) {
        if (hitDice < 0 || hitDice > getHitDiceRemaining()) {
            throw new IllegalArgumentException("Only " + getHitDiceRemaining() + " hit dice left");
        }
        if (hitDice > 0) requireHp();
        List<Integer> pool = new ArrayList<>();
        for (CharacterClass c : classes) {
            for (int i = 0; i < c.getLevel(); i++) pool.add(Math.max(1, c.getDndClass().getHitDie()));
        }
        pool.sort(java.util.Comparator.reverseOrder());
        List<Integer> available = pool.subList(Math.min(pool.size(), getHitDiceSpent()), pool.size());
        int con = abilityModifier(AbilityScore.CONSTITUTION);
        List<String> rolls = new ArrayList<>();
        int before = hpCurrent == null ? 0 : hpCurrent;
        for (int i = 0; i < hitDice; i++) {
            int sides = available.get(i);
            int rolled = die.applyAsInt(sides);
            rolls.add("1d" + sides + "[" + rolled + "]" + (con >= 0 ? "+" : "") + con);
            heal(Math.max(0, rolled + con));
        }
        hitDiceSpent = getHitDiceSpent() + hitDice;
        pactSlotsSpent = 0;
        List<String> recovered = rechargeResources(false);
        return new ShortRestResult(rolls, (hpCurrent == null ? 0 : hpCurrent) - before, recovered);
    }

    /**
     * Descanso longo: PV cheio, PV temporario zera, recupera todos os espacos de magia e metade dos
     * dados de vida (minimo 1), tira 1 de exaustao, zera os testes contra a morte e recupera os recursos
     * (menos os de recarga manual). Humano de 2024 (Engenhoso) ganha Inspiracao Heroica.
     * Devolve o nome dos recursos que voltaram.
     */
    public List<String> longRest() {
        if (hpMax != null) {
            hpCurrent = hpMax;
            character.revive();
        }
        hpTemp = 0;
        spentSpellSlots.clear();
        pactSlotsSpent = 0;
        hitDiceSpent = Math.max(0, getHitDiceSpent() - Math.max(1, getTotalLevel() / 2));
        if (getExhaustion() > 0) exhaustion = getExhaustion() - 1;
        resetDeathSaves();
        List<String> recovered = rechargeResources(true);
        if (isResourcefulHuman() && !getHeroicInspiration()) {
            heroicInspiration = true;
            recovered.add("Heroic Inspiration");
        }
        return recovered;
    }

    // ------------------------------------------------------------------ recursos e Inspiracao Heroica

    /**
     * Um recurso como a tela mostra. {@code id} = chave da classe ("barbarian.rage") ou o UUID do recurso criado a mao.
     */
    public record ResourceUse(String id, String name, String className, int max, int spent, ResourceRecharge recharge,
                              String unit, boolean custom, String notes) {
        public int remaining() {
            return Math.max(0, max - spent);
        }
    }

    /** Recursos de classe (pelo nivel) e os criados a mao, com quanto ja foi gasto. */
    @Transient
    public List<ResourceUse> getResources() {
        List<ResourceUse> out = new ArrayList<>();
        for (ClassResources.ClassResource r : ClassResources.of(this)) {
            out.add(new ResourceUse(r.key(), r.name(), r.className(), r.max(),
                    Math.min(r.max(), spentResources.getOrDefault(r.key(), 0)), r.recharge(), r.unit(), false, null));
        }
        for (SheetResource r : customResources) {
            out.add(new ResourceUse(r.getId().toString(), r.getName(), null, r.getMaxUses(), r.getUsed(), r.getRecharge(),
                    null, true, r.getNotes()));
        }
        return out;
    }

    /** Gasta {@code amount} usos (pontos, no caso de Cura pelas Maos). */
    public ResourceUse spendResource(String resourceId, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Amount must be at least 1");
        ResourceUse use = resource(resourceId);
        if (amount > use.remaining()) {
            throw new IllegalStateException("Only " + use.remaining() + " " + use.name() + " left");
        }
        setSpent(use, use.spent() + amount);
        return resource(resourceId);
    }

    /** Devolve usos; {@code amount} nulo = devolve tudo. */
    public ResourceUse recoverResource(String resourceId, Integer amount) {
        if (amount != null && amount < 1) throw new IllegalArgumentException("Amount must be at least 1");
        ResourceUse use = resource(resourceId);
        setSpent(use, amount == null ? 0 : Math.max(0, use.spent() - amount));
        return resource(resourceId);
    }

    public SheetResource addCustomResource(String name, int maxUses, ResourceRecharge recharge, String notes) {
        if (customResources.size() >= 50) throw new IllegalStateException("A sheet can have at most 50 custom resources");
        SheetResource resource = new SheetResource(this, name, maxUses, recharge, notes);
        customResources.add(resource);
        return resource;
    }

    public SheetResource updateCustomResource(UUID resourceId, String name, int maxUses, ResourceRecharge recharge, String notes) {
        SheetResource resource = customResource(resourceId);
        resource.update(name, maxUses, recharge, notes);
        return resource;
    }

    public void removeCustomResource(UUID resourceId) {
        customResources.remove(customResource(resourceId));
    }

    public boolean getHeroicInspiration() {
        return Boolean.TRUE.equals(heroicInspiration);
    }

    public void changeHeroicInspiration(boolean value) {
        heroicInspiration = value;
    }

    /** Gasta a Inspiracao Heroica (pra rolar um dado de novo). */
    public void useHeroicInspiration() {
        if (!getHeroicInspiration()) throw new IllegalStateException("The character has no Heroic Inspiration");
        heroicInspiration = false;
    }

    private ResourceUse resource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("Resource is required");
        return getResources().stream().filter(r -> r.id().equalsIgnoreCase(resourceId.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The character has no resource " + resourceId));
    }

    private SheetResource customResource(UUID resourceId) {
        return customResources.stream().filter(r -> r.getId().equals(resourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Custom resource not found"));
    }

    private void setSpent(ResourceUse use, int spent) {
        if (use.custom()) {
            customResource(UUID.fromString(use.id())).setUsed(spent);
        } else if (spent <= 0) {
            spentResources.remove(use.id());
        } else {
            spentResources.put(use.id(), spent);
        }
    }

    /** Descanso: devolve o que a regra manda e diz o que voltou ("Rage +1", "Focus Points"). */
    private List<String> rechargeResources(boolean longRest) {
        List<String> recovered = new ArrayList<>();
        for (ResourceUse use : getResources()) {
            if (use.spent() == 0 || use.recharge() == ResourceRecharge.MANUAL) continue;
            int spent = use.spent();
            if (longRest || use.recharge() == ResourceRecharge.SHORT_REST) spent = 0;
            else if (use.recharge() == ResourceRecharge.SHORT_REST_ONE) spent = spent - 1;
            if (spent == use.spent()) continue;
            setSpent(use, spent);
            recovered.add(spent == 0 ? use.name() : use.name() + " +" + (use.spent() - spent));
        }
        if (longRest) {
            // chave de recurso que a ficha nao tem mais (classe saiu, nivel caiu) sai junto
            java.util.Set<String> current = new java.util.HashSet<>();
            ClassResources.of(this).forEach(r -> current.add(r.key()));
            spentResources.keySet().retainAll(current);
        }
        return recovered;
    }

    /** Humano do Livro do Jogador de 2024: o traco Engenhoso da Inspiracao Heroica a cada descanso longo. */
    private boolean isResourcefulHuman() {
        Species species = character == null ? null : character.getSpecies();
        if (species == null || !"Human".equalsIgnoreCase(species.getName())) return false;
        String code = species.getSourceCode() != null ? species.getSourceCode()
                : species.getSource() == null ? null : species.getSource().getCode();
        return "XPHB".equalsIgnoreCase(code);
    }

    // ------------------------------------------------------------------ ataques e talentos

    public SheetAttack addAttack(String name, int attackBonus, String damage, String damageType, String notes) {
        SheetAttack attack = new SheetAttack(this, name, attackBonus, damage, damageType, notes);
        attacks.add(attack);
        return attack;
    }

    public void removeAttack(UUID attackId) {
        if (!attacks.removeIf(a -> a.getId().equals(attackId))) {
            throw new IllegalArgumentException("Attack not found");
        }
    }

    public CharacterFeat addCatalogFeat(FeatDefinition feat, int characterLevel, String choices) {
        if (catalogFeats.stream().anyMatch(f -> f.getFeat().getId().equals(feat.getId())) && !feat.isRepeatable()) {
            throw new IllegalArgumentException("The character already has this feat");
        }
        CharacterFeat selection = new CharacterFeat(this, feat, characterLevel, choices);
        catalogFeats.add(selection);
        return selection;
    }

    public void removeCatalogFeat(UUID characterFeatId) {
        if (!catalogFeats.removeIf(f -> f.getId().equals(characterFeatId))) {
            throw new IllegalArgumentException("Feat not found on this character");
        }
    }
}

package com.vttcaseiro.mob;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.content.ChallengeRatingCalculator;
import com.vttcaseiro.content.ChallengeRatingTable;
import com.vttcaseiro.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A ficha de um monstro/NPC -- construida EM CIMA do mesmo vocabulario da
 * CharacterSheet de proposito (abilityModifier, CharacterTrait,
 * Inventory), mas com os campos que uma ficha de monstro realmente tem e
 * uma ficha de jogador nao (tipo de criatura, tamanho, sentidos,
 * resistencias/imunidades a dano, CR). Sao classes separadas (em vez de
 * MobSheet herdar de CharacterSheet) porque as duas tem formatos de save
 * diferentes meio de nascenca -- um monstro nao tem "classe" nem
 * "antecedente" nem nivel no sentido de personagem, por exemplo.
 */
public class MobSheet {
    private String id;
    private String name = "Novo monstro";
    private String size = "Médio";
    private String creatureType = "humanoide";
    private String alignment = "neutro";

    private int armorClass = 10;
    private String armorNote = "";

    private int hitPointsAverage = 1;
    private String hitPointsFormula = "1d8";

    private int speedWalk = 30;
    private int speedFly;
    private int speedSwim;
    private int speedClimb;
    private int speedBurrow;

    private int strength = 10;
    private int dexterity = 10;
    private int constitution = 10;
    private int intelligence = 10;
    private int wisdom = 10;
    private int charisma = 10;

    /** Bonus de salvaguarda/pericia JA PRONTO por ability/pericia --
     * diferente de CharacterSheet (que guarda so quais sao proficientes e
     * calcula o bonus), porque um monstro importado do 5e.tools/5emm vem
     * com os bonus finais no JSON, nao com "e proficiente ou nao". */
    private Map<String, Integer> savingThrowBonuses = new LinkedHashMap<>();
    private Map<String, Integer> skillBonuses = new LinkedHashMap<>();

    private List<String> damageVulnerabilities = new ArrayList<>();
    private List<String> damageResistances = new ArrayList<>();
    private List<String> damageImmunities = new ArrayList<>();
    private List<String> conditionImmunities = new ArrayList<>();
    private List<String> senses = new ArrayList<>();
    private List<String> languages = new ArrayList<>();

    private double challengeRating = 0.25;
    private int experiencePoints = ChallengeRatingTable.xpFor(0.25);

    /** Reusa CharacterTrait -- clicar num trait/acao do monstro no painel
     * do mestre manda pro chat igual uma ficha de jogador (mesmo
     * CharacterTrait.toChatText()). */
    private List<CharacterTrait> traits = new ArrayList<>();
    private List<CharacterTrait> actions = new ArrayList<>();
    private List<CharacterTrait> legendaryActions = new ArrayList<>();

    private String portraitImagePath;
    private String imageData;
    /** Id do asset no ImageAssetStore da mesa -- mesma ideia descrita em
     * CharacterSheet.portraitImageAssetId. Monstros importados do
     * 5e.tools/5emm em lote se beneficiam ainda mais disso: varias
     * instancias do mesmo monstro (3 goblins na mesma cena) apontam pro
     * MESMO asset, nunca triplicam a imagem no disco. */
    private String portraitImageAssetId;
    /** Livro de onde veio no 5etools (ex.: "XMM"); serve pra achar a imagem dele por link. */
    private String sourceCode;

    /** Loot deste monstro especifico -- reusa Inventory (mesma classe que
     * CharacterSheet e Party usam), pra poder transferir pro inventario de
     * um personagem com o mesmo Inventory.transferItemTo quando o grupo
     * saqueia o corpo. */
    private Inventory lootInventory = new Inventory();

    public MobSheet() {
        this.id = UUID.randomUUID().toString();
    }

    public MobSheet(String name) {
        this();
        this.name = name;
    }

    public static int abilityModifier(int score) {
        return CharacterSheet.abilityModifier(score);
    }

    /** Sugere o CR deste monstro a partir dos numeros ja preenchidos nele
     * (PV medio, CA) + dano/ataque medio por rodada informado por quem
     * esta montando o monstro (isso nao da pra inferir automaticamente
     * dos traits em texto livre). Nao seta challengeRating sozinho --
     * quem chama decide se aceita a sugestao. */
    public double suggestedChallengeRating(int damagePerRound, int attackBonusOrSaveDc) {
        return ChallengeRatingCalculator.estimateCr(hitPointsAverage, armorClass, damagePerRound, attackBonusOrSaveDc);
    }

    public String getId() { return id; }

    /** Id novo (copia de um monstro vira outro monstro, nao o mesmo). */
    public void assignNewId() { this.id = UUID.randomUUID().toString(); }

    /**
     * Ficha feita com um modelo de PDF editavel: o modelo vem junto (as imagens das paginas e onde fica cada campo),
     * pra quem recebe a ficha pela rede ver igual, e os valores de cada campo do PDF.
     */
    private com.vttcaseiro.character.SheetTemplate template;
    private java.util.Map<String, String> templateValues;

    public com.vttcaseiro.character.SheetTemplate getTemplate() { return template == null ? null : template.sanitize(); }
    public void setTemplate(com.vttcaseiro.character.SheetTemplate t) { this.template = t; }
    public boolean usesTemplate() { return getTemplate() != null; }

    public java.util.Map<String, String> getTemplateValues() {
        if (templateValues == null) templateValues = new java.util.LinkedHashMap<>();
        return templateValues;
    }

    public void setTemplateValues(java.util.Map<String, String> values) {
        this.templateValues = values == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(values);
    }

    /**
     * So o mestre recebe a ficha deste monstro pela rede (os jogadores nao veem CA, PV, ataques... nem que o
     * monstro existe na lista). Padrao: escondido -- o mestre libera o que quiser mostrar.
     */
    private boolean hiddenFromPlayers = true;

    public boolean isHiddenFromPlayers() { return hiddenFromPlayers; }
    public void setHiddenFromPlayers(boolean hidden) { this.hiddenFromPlayers = hidden; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSize() {
        // fichas antigas gravaram sem acento
        if ("Medio".equals(size)) return "Médio";
        if ("Minusculo".equals(size)) return "Minúsculo";
        if ("Miudo".equals(size)) return "Miúdo";
        return size;
    }
    public void setSize(String size) { this.size = size; }

    public String getCreatureType() { return creatureType; }
    public void setCreatureType(String creatureType) { this.creatureType = creatureType; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public int getArmorClass() { return armorClass; }
    public void setArmorClass(int armorClass) { this.armorClass = armorClass; }

    public String getArmorNote() { return armorNote; }
    public void setArmorNote(String armorNote) { this.armorNote = armorNote; }

    public int getHitPointsAverage() { return hitPointsAverage; }
    public void setHitPointsAverage(int hitPointsAverage) { this.hitPointsAverage = Math.max(1, hitPointsAverage); }

    public String getHitPointsFormula() { return hitPointsFormula; }
    public void setHitPointsFormula(String hitPointsFormula) { this.hitPointsFormula = hitPointsFormula; }

    public int getSpeedWalk() { return speedWalk; }
    public void setSpeedWalk(int speedWalk) { this.speedWalk = speedWalk; }

    public int getSpeedFly() { return speedFly; }
    public void setSpeedFly(int speedFly) { this.speedFly = speedFly; }

    public int getSpeedSwim() { return speedSwim; }
    public void setSpeedSwim(int speedSwim) { this.speedSwim = speedSwim; }

    public int getSpeedClimb() { return speedClimb; }
    public void setSpeedClimb(int speedClimb) { this.speedClimb = speedClimb; }

    public int getSpeedBurrow() { return speedBurrow; }
    public void setSpeedBurrow(int speedBurrow) { this.speedBurrow = speedBurrow; }

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

    public int abilityModifier(Ability ability) {
        int score = switch (ability) {
            case STRENGTH -> strength;
            case DEXTERITY -> dexterity;
            case CONSTITUTION -> constitution;
            case INTELLIGENCE -> intelligence;
            case WISDOM -> wisdom;
            case CHARISMA -> charisma;
        };
        return abilityModifier(score);
    }

    public Map<String, Integer> getSavingThrowBonuses() { return savingThrowBonuses; }
    public Map<String, Integer> getSkillBonuses() { return skillBonuses; }

    public List<String> getDamageVulnerabilities() { return damageVulnerabilities; }
    public List<String> getDamageResistances() { return damageResistances; }
    public List<String> getDamageImmunities() { return damageImmunities; }
    public List<String> getConditionImmunities() { return conditionImmunities; }
    public List<String> getSenses() { return senses; }
    public List<String> getLanguages() { return languages; }

    public double getChallengeRating() { return challengeRating; }
    public void setChallengeRating(double challengeRating) {
        this.challengeRating = challengeRating;
        this.experiencePoints = ChallengeRatingTable.xpFor(challengeRating);
    }

    public int getExperiencePoints() { return experiencePoints; }

    public List<CharacterTrait> getTraits() { return traits; }
    public void addTrait(CharacterTrait trait) { traits.add(trait); }

    public List<CharacterTrait> getActions() { return actions; }
    public void addAction(CharacterTrait action) { actions.add(action); }

    public List<CharacterTrait> getLegendaryActions() { return legendaryActions; }
    public void addLegendaryAction(CharacterTrait action) { legendaryActions.add(action); }

    public String getPortraitImagePath() { return portraitImagePath; }
    public void setPortraitImagePath(String portraitImagePath) { this.portraitImagePath = portraitImagePath; }

    public String getImageData() { return imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }

    public String getPortraitImageAssetId() { return portraitImageAssetId; }
    public String getSourceCode() { return sourceCode == null || sourceCode.isBlank() ? null : sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public void setPortraitImageAssetId(String portraitImageAssetId) { this.portraitImageAssetId = portraitImageAssetId; }

    public Inventory getLootInventory() { return lootInventory; }

    /** Bonus de salvaguarda: o do bloco do monstro se tiver, senao o modificador. */
    public int saveBonus(Ability ability) {
        String code = switch (ability) {
            case STRENGTH -> "str";
            case DEXTERITY -> "dex";
            case CONSTITUTION -> "con";
            case INTELLIGENCE -> "int";
            case WISDOM -> "wis";
            case CHARISMA -> "cha";
        };
        Integer listed = savingThrowBonuses.get(code);
        if (listed != null) return listed;
        int score = switch (ability) {
            case STRENGTH -> strength;
            case DEXTERITY -> dexterity;
            case CONSTITUTION -> constitution;
            case INTELLIGENCE -> intelligence;
            case WISDOM -> wisdom;
            case CHARISMA -> charisma;
        };
        return abilityModifier(score);
    }
}

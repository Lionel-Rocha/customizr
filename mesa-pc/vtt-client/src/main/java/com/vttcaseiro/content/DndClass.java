package com.vttcaseiro.content;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma classe de D&D importada do 5etools -- nome, dado de vida, nivel em
 * que desbloqueia subclasse, as proprias features por nivel e as
 * subclasses (cada uma com as proprias features). Mesmo modelo do
 * DndClass do projeto dela, adaptado pra arquivo em vez de banco
 * (sem as colunas de JPA, sem o "metadata" generico em JSON -- aqui
 * guardamos so os campos que o projeto realmente usa).
 */
public class DndClass {
    private String id;
    private String className;
    private String sourceCode;
    private int hitDieNumber;
    private int hitDieFaces;
    private int subclassUnlockLevel;
    private String spellcastingAbility;
    private List<FeatureGrant> features = new ArrayList<>();
    private List<Subclass> subclasses = new ArrayList<>();

    /** Salvaguardas proficientes ("str", "con"... -- campo "proficiency"
     * do 5e.tools). Lista vazia em classes importadas antes disto existir. */
    private List<String> savingThrows = new ArrayList<>();
    /** Treinamento de armadura ("light", "medium", "heavy", "shield"). */
    private List<String> armorTraining = new ArrayList<>();
    /** Proficiencias com armas, ja em texto legivel ("simple", "martial"...). */
    private List<String> weaponProficiencies = new ArrayList<>();
    /** Pericias que o jogador escolhe no nivel 1 (nomes do 5e.tools em
     * ingles, ex.: "arcana") e quantas pode escolher. */
    private List<String> skillChoices = new ArrayList<>();
    private int skillChoiceCount;

    public DndClass() {
        this.id = UUID.randomUUID().toString();
    }

    public DndClass(String className, String sourceCode, int hitDieNumber, int hitDieFaces, int subclassUnlockLevel) {
        this();
        this.className = className;
        this.sourceCode = sourceCode;
        this.hitDieNumber = hitDieNumber;
        this.hitDieFaces = hitDieFaces;
        this.subclassUnlockLevel = subclassUnlockLevel;
    }

    public String getId() { return id; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public int getHitDieNumber() { return hitDieNumber; }
    public void setHitDieNumber(int hitDieNumber) { this.hitDieNumber = hitDieNumber; }

    public int getHitDieFaces() { return hitDieFaces; }
    public void setHitDieFaces(int hitDieFaces) { this.hitDieFaces = hitDieFaces; }

    public int getSubclassUnlockLevel() { return subclassUnlockLevel; }
    public void setSubclassUnlockLevel(int subclassUnlockLevel) { this.subclassUnlockLevel = subclassUnlockLevel; }

    public String getSpellcastingAbility() { return spellcastingAbility; }
    public void setSpellcastingAbility(String spellcastingAbility) { this.spellcastingAbility = spellcastingAbility; }

    public List<FeatureGrant> getFeatures() { return features; }

    public List<String> getSavingThrows() { if (savingThrows == null) savingThrows = new ArrayList<>(); return savingThrows; }
    public List<String> getArmorTraining() { if (armorTraining == null) armorTraining = new ArrayList<>(); return armorTraining; }
    public List<String> getWeaponProficiencies() { if (weaponProficiencies == null) weaponProficiencies = new ArrayList<>(); return weaponProficiencies; }
    public List<String> getSkillChoices() { if (skillChoices == null) skillChoices = new ArrayList<>(); return skillChoices; }
    public int getSkillChoiceCount() { return skillChoiceCount; }
    public void setSkillChoiceCount(int count) { this.skillChoiceCount = Math.max(0, count); }

    /** "Wizard (XPHB)" -- usado nas listas de escolha. */
    @Override
    public String toString() {
        return className + (sourceCode == null ? "" : " (" + sourceCode + ")");
    }

    public void addFeature(Feature feature, int level, String choiceGroup) {
        features.add(new FeatureGrant(feature, level, choiceGroup));
    }

    public List<Subclass> getSubclasses() { return subclasses; }

    public Subclass addSubclass(String name, String shortName, String sourceCode, int unlockLevel) {
        Subclass subclass = new Subclass(name, shortName, sourceCode, unlockLevel);
        subclasses.add(subclass);
        return subclass;
    }

    /** Todas as features (de classe E de todas as subclasses) ate o
     * nivel dado, ordenadas por nivel -- o que um personagem desse nivel
     * e dessa classe/subclasse realmente tem. Util pra "Criacao de
     * Personagem Guiada" calcular/mostrar o que o personagem ganhou. */
    public List<FeatureGrant> featuresUpToLevel(int level, Subclass chosenSubclass) {
        List<FeatureGrant> result = new ArrayList<>();
        for (FeatureGrant grant : features) {
            if (grant.getLevel() <= level) {
                result.add(grant);
            }
        }
        if (chosenSubclass != null) {
            for (FeatureGrant grant : chosenSubclass.getFeatures()) {
                if (grant.getLevel() <= level) {
                    result.add(grant);
                }
            }
        }
        result.sort((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));
        return result;
    }
}

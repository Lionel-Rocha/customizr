package com.vttcaseiro.compendium;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma entrada generica de compendio: uma classe, uma subclasse, um
 * antecedente, uma especie, um feat "de catalogo" (ver nota abaixo), um
 * item (magico ou nao, equipamento ou nao) ou uma magia. E um "envelope"
 * com todos os campos possiveis na mesma classe (mesmo padrao de
 * {@code com.vttcaseiro.net.NetMessage} e pela mesma razao: sete tipos de
 * conteudo bem diferentes entre si, cada um usando so uma parte dos
 * campos, cabem aqui sem precisar de sete classes + um adapter
 * polimorfico do Gson só pra poder viver numa lista so).
 * <p>
 * IMPORTANTE pra feats especificamente: isto aqui e o feat "de catalogo"
 * (nome + descricao + fonte, pra navegar/anexar/mandar pro chat) -- e
 * DIFERENTE de {@code com.vttcaseiro.character.Feat}, que e o feat que um
 * personagem realmente TEM, com pre-requisito que trava mecanicamente
 * (ver character.prerequisite). Quando o jogador pega um feat do
 * compendio pra valer, o service que faz isso (ver
 * com.vttcaseiro.service.CompendiumService) cria o Feat mecanico
 * correspondente -- a entrada de compendio e so a "ficha de catalogo"
 * antes disso.
 */
public class CompendiumEntry {
    private String id;
    private CompendiumEntryType type;
    private String name = "";
    private String source = "";
    private String shortDescription = "";
    private String fullText = "";

    /** Palavras-chave livres pra busca/filtro na tela de compendio. */
    private List<String> tags = new ArrayList<>();

    /** Ids de OUTRAS entradas de compendio relacionadas a esta -- isto e o
     * que deixa tudo "comunicavel entre si": uma varinha magica (ITEM)
     * pode listar o id da magia (SPELL) que ela conjura; um feat pode
     * listar o id de um item que ele desbloqueia; etc. A tela pode
     * desenhar isso como link clicavel dentro da descricao. */
    private List<String> relatedEntryIds = new ArrayList<>();

    // Campos so usados por ITEM (ficam null pros outros tipos)
    private Double itemValueGp;
    private Double itemWeightLb;
    private Boolean itemIsMagic;
    private String itemRarity;
    private Boolean itemRequiresAttunement;

    // Campos so usados por SPELL
    private Integer spellLevel;
    private String spellSchool;
    private String spellCastingTime;
    private String spellRange;
    private String spellDuration;
    private Boolean spellConcentration;
    private Boolean spellRitual;

    public CompendiumEntry() {
        this.id = UUID.randomUUID().toString();
    }

    public CompendiumEntry(CompendiumEntryType type, String name) {
        this();
        this.type = type;
        this.name = name;
    }

    public String getId() { return id; }
    /** So usado por quem IMPORTA conteudo com um id estavel conhecido
     * (por exemplo, o nome+fonte do 5e.tools) em vez do UUID aleatorio
     * do construtor -- permite reimportar a mesma fonte sem duplicar
     * entradas (ver Compendium.merge). */
    public void setId(String id) { this.id = id; }

    public CompendiumEntryType getType() { return type; }
    public void setType(CompendiumEntryType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public List<String> getTags() { return tags; }
    public void addTag(String tag) {
        if (tag != null && !tag.isBlank() && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    public List<String> getRelatedEntryIds() { return relatedEntryIds; }
    public void addRelatedEntryId(String entryId) {
        if (entryId != null && !entryId.isBlank() && !relatedEntryIds.contains(entryId)) {
            relatedEntryIds.add(entryId);
        }
    }

    public Double getItemValueGp() { return itemValueGp; }
    public void setItemValueGp(Double itemValueGp) { this.itemValueGp = itemValueGp; }

    public Double getItemWeightLb() { return itemWeightLb; }
    public void setItemWeightLb(Double itemWeightLb) { this.itemWeightLb = itemWeightLb; }

    public Boolean getItemIsMagic() { return itemIsMagic; }
    public void setItemIsMagic(Boolean itemIsMagic) { this.itemIsMagic = itemIsMagic; }

    public String getItemRarity() { return itemRarity; }
    public void setItemRarity(String itemRarity) { this.itemRarity = itemRarity; }

    public Boolean getItemRequiresAttunement() { return itemRequiresAttunement; }
    public void setItemRequiresAttunement(Boolean itemRequiresAttunement) { this.itemRequiresAttunement = itemRequiresAttunement; }

    public Integer getSpellLevel() { return spellLevel; }
    public void setSpellLevel(Integer spellLevel) { this.spellLevel = spellLevel; }

    public String getSpellSchool() { return spellSchool; }
    public void setSpellSchool(String spellSchool) { this.spellSchool = spellSchool; }

    public String getSpellCastingTime() { return spellCastingTime; }
    public void setSpellCastingTime(String spellCastingTime) { this.spellCastingTime = spellCastingTime; }

    public String getSpellRange() { return spellRange; }
    public void setSpellRange(String spellRange) { this.spellRange = spellRange; }

    public String getSpellDuration() { return spellDuration; }
    public void setSpellDuration(String spellDuration) { this.spellDuration = spellDuration; }

    public Boolean getSpellConcentration() { return spellConcentration; }
    public void setSpellConcentration(Boolean spellConcentration) { this.spellConcentration = spellConcentration; }

    public Boolean getSpellRitual() { return spellRitual; }
    public void setSpellRitual(Boolean spellRitual) { this.spellRitual = spellRitual; }

    /** Texto pronto pra mandar pro chat quando a entrada e clicada na
     * ficha -- mesma ideia de CharacterTrait.toChatText(), so que com uma
     * linha extra de "cabecalho" pros tipos que tem dados mecanicos
     * relevantes (nivel/escola da magia, valor/peso do item). */
    public String toChatText() {
        StringBuilder sb = new StringBuilder(name);
        String header = mechanicalHeader();
        if (!header.isEmpty()) {
            sb.append(" (").append(header).append(")");
        }
        if (fullText != null && !fullText.isBlank()) {
            sb.append(": ").append(fullText);
        }
        return sb.toString();
    }

    private String mechanicalHeader() {
        if (type == CompendiumEntryType.SPELL && spellLevel != null) {
            String levelText = spellLevel == 0 ? "truque" : "nivel " + spellLevel;
            return spellSchool != null && !spellSchool.isBlank() ? levelText + ", " + spellSchool : levelText;
        }
        if (type == CompendiumEntryType.ITEM && itemIsMagic != null && itemIsMagic) {
            return itemRarity != null && !itemRarity.isBlank() ? "magico, " + itemRarity : "magico";
        }
        return "";
    }
}

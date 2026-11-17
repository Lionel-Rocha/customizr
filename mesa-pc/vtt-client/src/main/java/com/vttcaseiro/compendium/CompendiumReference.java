package com.vttcaseiro.compendium;

/**
 * Um "ponteiro" leve pra uma CompendiumEntry, guardado na ficha em vez
 * da entrada inteira. De proposito -- se guardassemos a entrada completa
 * copiada na ficha, reimportar/corrigir aquele item/magia no compendio
 * nunca mais chegaria nas fichas que ja a anexaram (viraria uma copia
 * congelada, igual uma foto, em vez de um link). {@code displayName} e
 * guardado aqui MESMO ASSIM como "ultimo nome conhecido", so pra tela
 * poder mostrar algo (tachado ou avisando "nao encontrado") se a entrada
 * original for apagada do compendio depois do anexo.
 */
public class CompendiumReference {
    private String entryId;
    private CompendiumEntryType entryType;
    private String displayName;

    public CompendiumReference() {
    }

    public CompendiumReference(String entryId, CompendiumEntryType entryType, String displayName) {
        this.entryId = entryId;
        this.entryType = entryType;
        this.displayName = displayName;
    }

    public static CompendiumReference of(CompendiumEntry entry) {
        return new CompendiumReference(entry.getId(), entry.getType(), entry.getName());
    }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public CompendiumEntryType getEntryType() { return entryType; }
    public void setEntryType(CompendiumEntryType entryType) { this.entryType = entryType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}

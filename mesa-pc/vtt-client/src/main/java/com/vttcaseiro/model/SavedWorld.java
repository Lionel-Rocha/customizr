package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mundo do Azgaar guardado na mesa (pra abrir regioes dele depois e editar): o arquivo (.map ou o JSON compactado)
 * copiado pra pasta "mundos", a imagem do mundo inteiro (asset), como ela foi feita e as edicoes do mestre.
 */
public class SavedWorld {
    /** Imagem do PNG exportado (nao da pra redesenhar). */
    public static final String STYLE_PNG = "PNG";

    private String name;
    private String mapFile;
    private String imageAssetId;
    private String sceneName;
    /** RELEVO, POLITICO (desenhada aqui) ou PNG; null = mundo importado antes disso (nao redesenha). */
    private String style;
    /** Os nomes viraram rotulos da mesa (o desenho saiu sem nomes). */
    private boolean labelsAsPins;
    private WorldEdits edits;
    private List<Region> regions;

    /** Uma regiao aberta deste mundo (pra redesenhar quando o mundo for editado). */
    public static class Region {
        public String sceneName;
        public double x;
        public double y;
        public double w;
        public double h;
        public String style;
        public boolean labelsAsPins;
    }

    public SavedWorld() {
    }

    public SavedWorld(String name, String mapFile, String imageAssetId, String sceneName) {
        this.name = name;
        this.mapFile = mapFile;
        this.imageAssetId = imageAssetId;
        this.sceneName = sceneName;
    }

    public String getName() { return name == null ? "Mundo" : name; }
    public String getMapFile() { return mapFile; }
    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }
    public String getSceneName() { return sceneName; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    /** A imagem foi desenhada aqui (da pra redesenhar com as edicoes). */
    public boolean isDrawn() { return style != null && !STYLE_PNG.equals(style); }

    public boolean isLabelsAsPins() { return labelsAsPins; }
    public void setLabelsAsPins(boolean labelsAsPins) { this.labelsAsPins = labelsAsPins; }

    public WorldEdits getEdits() {
        if (edits == null) edits = new WorldEdits();
        return edits;
    }
    public void setEdits(WorldEdits edits) { this.edits = edits; }

    public List<Region> getRegions() {
        if (regions == null) regions = new ArrayList<>();
        return regions;
    }

    @Override
    public String toString() {
        return getName();
    }
}

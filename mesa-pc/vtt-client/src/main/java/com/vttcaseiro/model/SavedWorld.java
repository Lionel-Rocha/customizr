package com.vttcaseiro.model;

/**
 * Mundo do Azgaar guardado na mesa (pra abrir regioes dele depois): o arquivo .map copiado pra pasta "mundos" e a
 * imagem do mundo inteiro (asset), que e de onde as regioes sao recortadas.
 */
public class SavedWorld {
    private String name;
    private String mapFile;
    private String imageAssetId;
    private String sceneName;

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
    public String getSceneName() { return sceneName; }

    @Override
    public String toString() {
        return getName();
    }
}

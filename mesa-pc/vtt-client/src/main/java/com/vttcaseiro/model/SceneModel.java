package com.vttcaseiro.model;

import java.util.ArrayList;
import java.util.List;

/** Um mapa: imagem de fundo, grade e a lista de tokens nele. Guardar isso
 * separado da parte visual (JavaFX) e proposital -- quando a Fase 2 (rede)
 * chegar, e este objeto que vai ser serializado/enviado, nao os Nodes da tela. */
public class SceneModel {
    private String name = "Novo mapa";
    private String backgroundImagePath;
    /** Bytes da imagem de fundo em Base64 (ver com.vttcaseiro.net.ImageCodec).
     * Ainda nao e preenchido em lugar nenhum do codigo -- isso e o proximo
     * passo depois da reestruturacao de servicos: codificar aqui quando o
     * mestre hospeda, e preferir isto a backgroundImagePath ao desenhar o
     * mapa num cliente que nao e o criador. */
    private String backgroundImageData;
    /** Id do asset no ImageAssetStore da mesa (ver com.vttcaseiro.library.
     * ImageAssetStore) -- mesma ideia de TokenModel.imageAssetId. Se dois
     * mapas usarem a mesma imagem de fundo (uma reaproveitada de outra
     * sessao, por exemplo), os bytes ficam guardados uma unica vez. */
    private String backgroundImageAssetId;
    private GridSettings grid = new GridSettings();
    private List<TokenModel> tokens = new ArrayList<>();
    /** Combate em andamento (rodada/turno/efeitos com duracao). */
    private Encounter encounter = new Encounter();

    public Encounter getEncounter() {
        if (encounter == null) encounter = new Encounter();
        return encounter;
    }

    /** Neblina de guerra (casas reveladas). */
    private Fog fog = new Fog();
    /** Paredes e portas (bloqueiam visao e movimento). */
    private List<Wall> walls = new ArrayList<>();
    /** Visao dinamica: cada jogador so ve o que os tokens dele enxergam. */
    private boolean visionEnabled;
    /** Cena escura: so enxerga ate a visao no escuro / luz do token. */
    private boolean dark;
    public static final int MAX_WALLS = 3000;

    /** Objetos soltos no mapa (imagens por cima do fundo). Ordem = ordem de desenho. */
    private List<MapObject> objects = new ArrayList<>();
    public static final int MAX_OBJECTS = 500;

    public List<MapObject> getObjects() {
        if (objects == null) objects = new ArrayList<>();
        return objects;
    }

    public void setObjects(List<MapObject> list) {
        objects = new ArrayList<>();
        if (list == null) return;
        for (MapObject o : list) {
            if (o == null || objects.size() >= MAX_OBJECTS) continue;
            o.sanitize();
            if (o.getAssetId() != null) objects.add(o);
        }
    }

    public java.util.Optional<MapObject> findObject(String id) {
        return getObjects().stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    public List<MapObject> visibleObjects(boolean gm) {
        if (gm) return new ArrayList<>(getObjects());
        List<MapObject> out = new ArrayList<>();
        for (MapObject o : getObjects()) if (!o.isHidden()) out.add(o);
        return out;
    }

    /** Pins (pontos de informacao / atalhos pra outras cenas). */
    private List<MapPin> pins = new ArrayList<>();
    public static final int MAX_PINS = 500;

    public List<MapPin> getPins() {
        if (pins == null) pins = new ArrayList<>();
        return pins;
    }

    public void setPins(List<MapPin> list) {
        pins = new ArrayList<>();
        if (list == null) return;
        for (MapPin p : list) {
            if (p == null || pins.size() >= MAX_PINS) continue;
            p.sanitize();
            pins.add(p);
        }
    }

    public java.util.Optional<MapPin> findPin(String id) {
        return getPins().stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    /** Os pins que um jogador pode ver (sem os ocultos). */
    public List<MapPin> visiblePins(boolean gm) {
        if (gm) return new ArrayList<>(getPins());
        List<MapPin> out = new ArrayList<>();
        for (MapPin p : getPins()) if (!p.isHidden()) out.add(p);
        return out;
    }

    public List<Wall> getWalls() {
        if (walls == null) walls = new ArrayList<>();
        return walls;
    }

    public void setWalls(List<Wall> list) {
        walls = new ArrayList<>(list == null ? List.of() : list);
        if (walls.size() > MAX_WALLS) walls = new ArrayList<>(walls.subList(0, MAX_WALLS));
    }

    public java.util.Optional<Wall> findWall(String id) {
        return getWalls().stream().filter(w -> w.getId().equals(id)).findFirst();
    }

    public boolean isVisionEnabled() { return visionEnabled; }

    /** Cena escondida (mestre preparando): jogador recebe so uma cena vazia com o aviso, nada do que tem nela. */
    private boolean hiddenFromPlayers;

    public boolean isHiddenFromPlayers() { return hiddenFromPlayers; }
    public void setHiddenFromPlayers(boolean hidden) { this.hiddenFromPlayers = hidden; }
    public void setVisionEnabled(boolean visionEnabled) { this.visionEnabled = visionEnabled; }

    public boolean isDark() { return dark; }
    public void setDark(boolean dark) { this.dark = dark; }

    /** Cena em penumbra (crepusculo, luar forte): tudo e penumbra, a visao no escuro vira luz plena. Escura manda. */
    private boolean dim;
    /** A visao dos tokens dos jogadores revela a neblina do mestre sozinha (e fica revelado). */
    private boolean fogByVision;

    /** Luzes fixas no mapa (tocha na parede, fogueira...). */
    private List<MapLight> lights = new ArrayList<>();
    public static final int MAX_LIGHTS = 300;

    public List<MapLight> getLights() {
        if (lights == null) lights = new ArrayList<>();
        return lights;
    }

    public void setLights(List<MapLight> list) {
        List<MapLight> out = new ArrayList<>();
        if (list != null) {
            for (MapLight l : list) {
                if (l == null || out.size() >= MAX_LIGHTS) continue;
                l.sanitize();
                out.add(l);
            }
        }
        lights = out;
    }

    public java.util.Optional<MapLight> findLight(String id) {
        return getLights().stream().filter(l -> l.getId().equals(id)).findFirst();
    }

    public boolean isDim() { return dim && !dark; }
    public void setDim(boolean dim) { this.dim = dim; }
    public boolean isFogByVision() { return fogByVision; }
    public void setFogByVision(boolean fogByVision) { this.fogByVision = fogByVision; }

    /** Desenhos a mao livre e textos no mapa. */
    private List<Drawing> drawings = new ArrayList<>();
    public static final int MAX_DRAWINGS = 2000;

    public List<Drawing> getDrawings() {
        if (drawings == null) drawings = new ArrayList<>();
        return drawings;
    }

    /** Acrescenta (ou troca, mesmo id). false se a cena ja esta cheia de desenhos. */
    public boolean addDrawing(Drawing d) {
        if (d == null || d.getId() == null) return false;
        getDrawings().removeIf(x -> d.getId().equals(x.getId()));
        if (drawings.size() >= MAX_DRAWINGS) return false;
        drawings.add(d);
        return true;
    }

    public boolean removeDrawing(String id) {
        return id != null && getDrawings().removeIf(x -> id.equals(x.getId()));
    }

    /** Apaga os desenhos de {@code ownerId}, ou todos se null. Devolve quantos. */
    public int clearDrawings(String ownerId) {
        int before = getDrawings().size();
        if (ownerId == null) drawings.clear();
        else drawings.removeIf(x -> ownerId.equals(x.getOwnerId()));
        return before - drawings.size();
    }

    public java.util.Optional<Drawing> findDrawing(String id) {
        return getDrawings().stream().filter(x -> x.getId().equals(id)).findFirst();
    }

    public Fog getFog() {
        if (fog == null) fog = new Fog();
        return fog;
    }

    public void setFog(Fog fog) { this.fog = fog == null ? new Fog() : fog; }

    public void setEncounter(Encounter encounter) { this.encounter = encounter == null ? new Encounter() : encounter; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBackgroundImagePath() { return backgroundImagePath; }
    public void setBackgroundImagePath(String backgroundImagePath) { this.backgroundImagePath = backgroundImagePath; }

    public String getBackgroundImageData() { return backgroundImageData; }
    public void setBackgroundImageData(String backgroundImageData) { this.backgroundImageData = backgroundImageData; }

    public String getBackgroundImageAssetId() { return backgroundImageAssetId; }
    public void setBackgroundImageAssetId(String backgroundImageAssetId) { this.backgroundImageAssetId = backgroundImageAssetId; }

    public GridSettings getGrid() { return grid; }

    public List<TokenModel> getTokens() { return tokens; }

    /** Um token com o mesmo id ja presente e substituido (nunca duplica --
     * a mesma mensagem add_token pode chegar duas vezes numa reconexao). */
    public void addToken(TokenModel token) {
        if (token == null) return;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getId().equals(token.getId())) {
                tokens.set(i, token);
                return;
            }
        }
        tokens.add(token);
    }

    public java.util.Optional<TokenModel> findToken(String id) {
        if (id == null) return java.util.Optional.empty();
        return tokens.stream().filter(t -> id.equals(t.getId())).findFirst();
    }

    public void removeToken(TokenModel token) { tokens.remove(token); }
}

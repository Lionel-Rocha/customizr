package com.vttcaseiro.model;

import java.util.UUID;

/**
 * Zona do mapa: um retangulo que faz algo quando um token entra (ou comeca o turno dentro).
 * <ul>
 *   <li>{@link #TRAP} armadilha: avisa o mestre e oferece a salvaguarda (com o dano);</li>
 *   <li>{@link #PORTAL} escada/portal: leva o jogador dono do token pra outra cena;</li>
 *   <li>{@link #DAMAGE} dano: quem comeca o turno dentro leva o dano (lava, fogo, acido);</li>
 *   <li>{@link #TERRAIN} terreno dificil: a regua de movimento conta cada casa dentro em dobro;</li>
 *   <li>{@link #NOTE} aviso: um texto pro mestre (e opcionalmente pro jogador) quando alguem entra.</li>
 *   <li>{@link #SOUND} som: quando alguem entra, toca um efeito, troca a musica ou o ambiente (pra todos).</li>
 * </ul>
 * So o mestre ve as zonas; o servidor nem manda pro jogador, a nao ser as marcadas como visiveis (o terreno
 * dificil nasce visivel).
 */
public class MapZone {
    public static final String TRAP = "armadilha";
    public static final String PORTAL = "portal";
    public static final String DAMAGE = "dano";
    public static final String TERRAIN = "terreno";
    public static final String NOTE = "aviso";
    public static final String SOUND = "som";
    public static final String[] KINDS = {TRAP, PORTAL, DAMAGE, TERRAIN, NOTE, SOUND};
    public static final int MAX_TEXT = 1000;

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String kind = TRAP;
    private double x;
    private double y;
    private double width;
    private double height;
    /** Armadilha: atributo da salvaguarda (FOR, DES, CON, INT, SAB, CAR) e CD. */
    private String ability = "DES";
    private int dc = 13;
    /** Armadilha e dano: dados (ex.: "2d6") e tipo (fogo, perfurante...). */
    private String damage = "";
    private String damageType = "";
    /** Armadilha: metade do dano em quem passa. */
    private boolean halfOnSave = true;
    /** Portal: nome da cena da mesa pra onde leva. */
    private String targetScene;
    /** Aviso (e texto extra das outras): o que aparece. */
    private String text = "";
    /** Aviso: manda o texto tambem pro jogador que entrou. */
    private boolean tellPlayer;
    /** Dispara so uma vez (armadilha desarma depois). */
    private boolean once = true;
    private boolean triggered;
    /** Jogadores veem a zona no mapa (terreno dificil, area marcada). */
    private boolean visibleToPlayers;
    /** Som: id do som da biblioteca da mesa (null = parar o canal), nome e canal (sfx, music, ambient). */
    private String soundId;
    private String soundName = "";
    private String soundChannel = "sfx";
    /** So dispara com token de jogador (monstro andando nao troca a musica). */
    private boolean playersOnly;

    public MapZone() {
    }

    public MapZone(double x, double y, double width, double height, String kind) {
        setBounds(x, y, width, height);
        setKind(kind);
    }

    public String getId() { return id; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = clip(name, 80); }
    public String getKind() { return kind == null ? TRAP : kind; }

    public void setKind(String kind) {
        for (String k : KINDS) {
            if (k.equals(kind)) {
                this.kind = k;
                if (TERRAIN.equals(k)) visibleToPlayers = true;
                return;
            }
        }
        this.kind = TRAP;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    public void setBounds(double x, double y, double w, double h) {
        this.x = Math.min(x, x + w);
        this.y = Math.min(y, y + h);
        this.width = Math.abs(w);
        this.height = Math.abs(h);
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public String getAbility() { return ability == null ? "DES" : ability; }
    public void setAbility(String ability) { this.ability = clip(ability, 3); }
    public int getDc() { return dc; }
    public void setDc(int dc) { this.dc = Math.max(1, Math.min(40, dc)); }
    public String getDamage() { return damage == null ? "" : damage; }
    public void setDamage(String damage) { this.damage = clip(damage, 40); }
    public String getDamageType() { return damageType == null ? "" : damageType; }
    public void setDamageType(String t) { this.damageType = clip(t, 30); }
    public boolean isHalfOnSave() { return halfOnSave; }
    public void setHalfOnSave(boolean half) { this.halfOnSave = half; }
    public String getTargetScene() { return targetScene; }
    public void setTargetScene(String s) { this.targetScene = s == null || s.isBlank() ? null : clip(s, 120); }
    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = clip(text, MAX_TEXT); }
    public boolean isTellPlayer() { return tellPlayer; }
    public void setTellPlayer(boolean tell) { this.tellPlayer = tell; }
    public boolean isOnce() { return once; }
    public void setOnce(boolean once) { this.once = once; }
    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
    public String getSoundId() { return soundId; }
    public void setSoundId(String id) { this.soundId = id == null || id.isBlank() ? null : clip(id, 128); }
    public String getSoundName() { return soundName == null ? "" : soundName; }
    public void setSoundName(String n) { this.soundName = clip(n, 120); }
    public String getSoundChannel() {
        return "music".equals(soundChannel) || "ambient".equals(soundChannel) ? soundChannel : "sfx";
    }
    public void setSoundChannel(String c) { this.soundChannel = c; }
    public boolean isPlayersOnly() { return playersOnly; }
    public void setPlayersOnly(boolean p) { this.playersOnly = p; }
    public boolean isVisibleToPlayers() { return visibleToPlayers; }
    public void setVisibleToPlayers(boolean v) { this.visibleToPlayers = v; }

    /** Dispara ao entrar? (o dano e no comeco do turno; o terreno so mexe na regua) */
    public boolean triggersOnEnter() {
        String k = getKind();
        return (TRAP.equals(k) || PORTAL.equals(k) || NOTE.equals(k) || SOUND.equals(k)) && !(once && triggered);
    }

    /** Nome pra mostrar ("Armadilha", "Fosso" etc.). */
    public String label() {
        if (!getName().isBlank()) return getName();
        return switch (getKind()) {
            case PORTAL -> "Portal";
            case DAMAGE -> "Área de dano";
            case TERRAIN -> "Terreno difícil";
            case NOTE -> "Aviso";
            case SOUND -> "Som";
            default -> "Armadilha";
        };
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }
}

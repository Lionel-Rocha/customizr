package com.vttcaseiro.model;

/**
 * Ordem de som do mestre: tocar/parar num canal. "music" e "ambient" ficam
 * em loop (e o servidor lembra, pra quem entrar depois); "sfx" toca uma vez.
 */
public class AudioCommand {
    public static final String MUSIC = "music";
    public static final String AMBIENT = "ambient";
    public static final String SFX = "sfx";

    private String channel = MUSIC;
    private String action = "play";
    private String assetId;
    private String name;
    /** Musica de uma lista: toca uma vez (o mestre manda a proxima quando acabar). */
    private boolean once;

    public AudioCommand() {
    }

    public static AudioCommand play(String channel, String assetId, String name) {
        AudioCommand c = new AudioCommand();
        c.channel = channel;
        c.assetId = assetId;
        c.name = name;
        return c;
    }

    public static AudioCommand stop(String channel) {
        AudioCommand c = new AudioCommand();
        c.channel = channel;
        c.action = "stop";
        return c;
    }

    public String getChannel() {
        return SFX.equals(channel) || AMBIENT.equals(channel) ? channel : MUSIC;
    }

    public boolean isStop() { return "stop".equals(action); }
    public String getAssetId() { return assetId; }
    public String getName() { return name == null ? "" : name; }
    public boolean loops() { return !SFX.equals(getChannel()) && !once; }

    public boolean isOnce() { return once; }

    /** Musica que toca uma vez so (lista em sequencia). */
    public static AudioCommand playOnce(String channel, String assetId, String name) {
        AudioCommand c = play(channel, assetId, name);
        c.once = true;
        return c;
    }
}

package com.vttcaseiro.app;

import com.vttcaseiro.model.AudioCommand;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Toca os sons da mesa: musica e ambiente em loop (um de cada), efeitos uma
 * vez. Cada pessoa tem o proprio volume por canal e o "silenciar".
 */
public class SoundPlayer {
    private static final Log log = Log.of(SoundPlayer.class);

    private final Map<String, MediaPlayer> loops = new HashMap<>();
    private final Map<String, String> nowPlaying = new HashMap<>();
    private final Map<String, Double> volumes = new HashMap<>(Map.of(
            AudioCommand.MUSIC, AppSettings.getDouble("volume.music", 0.6),
            AudioCommand.AMBIENT, AppSettings.getDouble("volume.ambient", 0.6),
            AudioCommand.SFX, AppSettings.getDouble("volume.sfx", 0.8)));
    private boolean muted;
    private Runnable onChange = () -> { };

    public void setOnChange(Runnable r) {
        this.onChange = r == null ? () -> { } : r;
    }

    /** uri: arquivo local (file:) ou o link do host (http:). null = so parar. */
    public void apply(AudioCommand cmd, String uri) {
        String ch = cmd.getChannel();
        if (cmd.isStop() || uri == null) {
            stop(ch);
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer(new Media(uri));
            player.setVolume(muted ? 0 : volumes.getOrDefault(ch, 0.7));
            player.setOnError(() -> log.warn("Não foi possível tocar " + cmd.getName() + ": " + player.getError()));
            if (cmd.loops()) {
                stop(ch);
                player.setCycleCount(MediaPlayer.INDEFINITE);
                loops.put(ch, player);
                nowPlaying.put(ch, cmd.getName());
            } else {
                player.setOnEndOfMedia(player::dispose);
            }
            player.play();
        } catch (RuntimeException ex) {
            log.warn("Som não tocou (" + cmd.getName() + "): " + ex.getMessage());
        }
        onChange.run();
    }

    public void stop(String channel) {
        MediaPlayer old = loops.remove(channel);
        if (old != null) {
            old.stop();
            old.dispose();
        }
        nowPlaying.remove(channel);
        onChange.run();
    }

    public void stopAll() {
        for (String ch : java.util.List.copyOf(loops.keySet())) stop(ch);
    }

    public String nowPlaying(String channel) {
        return nowPlaying.get(channel);
    }

    public double volume(String channel) {
        return volumes.getOrDefault(channel, 0.7);
    }

    public void setVolume(String channel, double v) {
        double vol = Math.max(0, Math.min(1, v));
        volumes.put(channel, vol);
        AppSettings.setDouble("volume." + channel, vol);
        MediaPlayer p = loops.get(channel);
        if (p != null && !muted) p.setVolume(vol);
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        loops.forEach((ch, p) -> p.setVolume(muted ? 0 : volumes.getOrDefault(ch, 0.7)));
    }
}

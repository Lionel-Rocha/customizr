package com.vttcaseiro.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Sons da mesa (musica, ambiente, efeitos) em {@code assets/audio}, com nome
 * = hash do conteudo + extensao -- igual as imagens. Quem hospeda serve os
 * arquivos por HTTP (WebGateway, {@code /audio/<id>}) e todo mundo toca dali:
 * nada de mandar 10 MB de musica pela conexao do jogo.
 */
public class AudioAssetStore {
    public static final long MAX_BYTES = 40L * 1024 * 1024;

    private final Path dir;

    public AudioAssetStore(Path mesaRoot) throws IOException {
        this(mesaRoot.resolve("assets").resolve("audio"), true);
    }

    private AudioAssetStore(Path dir, boolean create) throws IOException {
        this.dir = dir;
        if (create) Files.createDirectories(dir);
    }

    /** Sons ao lado da pasta de imagens (a ponte web so conhece o ImageAssetStore). */
    public static AudioAssetStore besideImages(ImageAssetStore images) throws IOException {
        return new AudioAssetStore(images.getDir().getParent().resolve("audio"), true);
    }

    public Path getDir() { return dir; }

    public static boolean isAudioName(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".m4a")
                || n.endsWith(".aac") || n.endsWith(".webm");
    }

    /** Id valido (so hash + extensao de audio): o que a ponte aceita servir. */
    public static boolean isValidId(String id) {
        return id != null && id.matches("[0-9a-f]{64}\\.(mp3|ogg|wav|m4a|aac|webm)");
    }

    public String importFile(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (!isAudioName(name)) throw new IOException("Use mp3, ogg, wav, m4a, aac ou webm.");
        long size = Files.size(file);
        if (size > MAX_BYTES) throw new IOException("Arquivo grande demais (máximo " + (MAX_BYTES / 1024 / 1024) + " MB).");
        byte[] bytes = Files.readAllBytes(file);
        String ext = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        String id = sha256(bytes) + ext;
        Path target = dir.resolve(id);
        if (!Files.exists(target)) AtomicFiles.writeBytes(target, bytes);
        return id;
    }

    public boolean exists(String id) {
        return isValidId(id) && Files.exists(dir.resolve(id));
    }

    public Path path(String id) throws IOException {
        if (!isValidId(id)) throw new IOException("id de som inválido");
        return dir.resolve(id);
    }

    public static String contentType(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".m4a") || n.endsWith(".aac")) return "audio/mp4";
        if (n.endsWith(".webm")) return "audio/webm";
        return "application/octet-stream";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

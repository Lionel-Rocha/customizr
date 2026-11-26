package com.vttcaseiro.net;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quem e quem na mesa. Todo mundo entra com a mesma senha da mesa, e o id de cada jogador e publico (vai no dono dos
 * tokens e na lista de quem esta online) -- sem isto, quem soubesse o id de outro entrava "como" ele e controlava os
 * tokens e as fichas dele. Agora cada instalacao tem tambem uma chave secreta: na primeira vez que um id entra, o
 * servidor guarda a impressao (SHA-256) da chave; dali em diante aquele id so entra com a mesma chave. O mestre pode
 * esquecer um id (o jogador trocou de computador ou apagou as configuracoes).
 */
public final class PlayerKeys {

    /** O que o servidor lembra de um jogador. */
    public static final class Entry {
        public String id;
        public String hash;
        public String name;
        public long lastSeen;
    }

    public enum Result {
        /** Chave confere. */
        OK,
        /** Id novo com chave: guardado. */
        REGISTERED,
        /** Id novo sem chave (programa antigo): entra, mas o id fica sem protecao. */
        LEGACY,
        /** Id protegido e chave diferente (ou nenhuma): nao entra. */
        MISMATCH
    }

    /** Quantos jogadores o arquivo guarda (os mais antigos saem). */
    static final int MAX_ENTRIES = 500;

    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final Path file;

    /** file null = so na memoria (testes; servidor sem mesa). */
    public PlayerKeys(Path file) {
        this.file = file;
        load();
    }

    /** Confere (e, se for a primeira vez, guarda) a chave de quem esta entrando. */
    public synchronized Result check(String playerId, String secret, String name, boolean trusted) {
        if (playerId == null || playerId.isBlank()) return Result.LEGACY;
        boolean hasKey = secret != null && secret.length() >= 16 && secret.length() <= 200;
        Entry e = byId.get(playerId);
        if (e != null && !trusted) {
            if (!hasKey || !e.hash.equals(hash(secret))) return Result.MISMATCH;
            touch(e, name);
            return Result.OK;
        }
        if (!hasKey) return Result.LEGACY;
        // novo, ou o mestre (que provou a senha de mestre) entrando de um computador novo: guarda essa chave
        Entry n = e != null ? e : new Entry();
        n.id = playerId;
        n.hash = hash(secret);
        touch(n, name);
        byId.put(playerId, n);
        trim();
        save();
        return e != null ? Result.OK : Result.REGISTERED;
    }

    /** Esquece um id (o proximo que entrar com ele vira o dono). Devolve se existia. */
    public synchronized boolean forget(String playerId) {
        boolean had = byId.remove(playerId) != null;
        if (had) save();
        return had;
    }

    /** Jogadores lembrados, o mais recente primeiro. */
    public synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) {
            Entry c = new Entry();
            c.id = e.id;
            c.name = e.name;
            c.lastSeen = e.lastSeen;
            out.add(c); // (sem a impressao da chave)
        }
        out.sort(Comparator.comparingLong((Entry x) -> x.lastSeen).reversed());
        return out;
    }

    private void touch(Entry e, String name) {
        if (name != null && !name.isBlank()) e.name = name.length() > 60 ? name.substring(0, 60) : name;
        e.lastSeen = System.currentTimeMillis();
    }

    private void trim() {
        while (byId.size() > MAX_ENTRIES) {
            String oldest = byId.values().stream().min(Comparator.comparingLong(x -> x.lastSeen)).map(x -> x.id).orElse(null);
            if (oldest == null) break;
            byId.remove(oldest);
        }
    }

    static String hash(String secret) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("customizr-player:" + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            List<Entry> list = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Entry>>() { }.getType());
            if (list != null) for (Entry e : list) if (e != null && e.id != null && e.hash != null) byId.put(e.id, e);
        } catch (IOException | RuntimeException ex) {
            // arquivo estragado: comeca vazio (todo mundo se registra de novo na proxima entrada)
        }
    }

    private void save() {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            com.vttcaseiro.library.AtomicFiles.writeString(file, new Gson().toJson(new ArrayList<>(byId.values())));
        } catch (IOException ex) {
            // sem disco: continua valendo na memoria ate o servidor fechar
        }
    }
}

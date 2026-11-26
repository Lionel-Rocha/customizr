package com.vttcaseiro.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Aviso de versao nova: o criar-launcher grava ao lado do programa o commit e a data dele (versao.txt); ao abrir, o
 * programa pergunta ao GitHub (so leitura, sem mandar nada de quem usa) qual e o ultimo commit do programa. Se for mais
 * novo, aparece o botao "Versao nova". Rodando do codigo (sem versao.txt) ou sem internet: nao avisa nada.
 */
public final class UpdateCheck {

    /** Ultimos commits da pasta do programa (o site e o resto do repositorio nao contam). */
    static final String API = "https://api.github.com/repos/Lionel-Rocha/customizr/commits?sha=seccond-branch&path=mesa-pc/vtt-client&per_page=1";
    /** Pagina com o que mudou (pra quem quiser ver). */
    public static final String HISTORY_PAGE = "https://github.com/Lionel-Rocha/customizr/commits/seccond-branch/mesa-pc/vtt-client";

    /** Um commit: id, data e a primeira linha da mensagem. */
    public record Version(String commit, OffsetDateTime date, String message) {
    }

    private UpdateCheck() {
    }

    /** A versao deste programa (versao.txt ao lado do customizr.jar), se tiver. */
    public static Optional<Version> local() {
        try {
            Path jar = Path.of(UpdateCheck.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            return dir == null ? Optional.empty() : parseLocal(dir.resolve("versao.txt"));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** "sha data" (como o git log grava). */
    static Optional<Version> parseLocal(Path file) {
        try {
            if (!Files.exists(file)) return Optional.empty();
            String[] p = Files.readString(file, StandardCharsets.UTF_8).strip().split("\\s+");
            if (p.length < 2 || !p[0].matches("[0-9a-f]{40}")) return Optional.empty();
            return Optional.of(new Version(p[0], OffsetDateTime.parse(p[1]), null));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** O ultimo commit do programa no GitHub (vazio sem internet ou se a resposta vier estranha). */
    public static Optional<Version> latest() {
        // HttpURLConnection (e nao o HttpClient): o HttpClient abre um "cano" interno que falha em alguns Windows
        // (pasta temporaria com espaco no nome) -- e ai o aviso nunca apareceria
        java.net.HttpURLConnection con = null;
        try {
            con = (java.net.HttpURLConnection) URI.create(API).toURL().openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(8000);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("User-Agent", "Customizr");
            if (con.getResponseCode() != 200) return Optional.empty();
            try (java.io.InputStream in = con.getInputStream()) {
                byte[] body = in.readNBytes(512 * 1024);
                return parseLatest(new String(body, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Resposta da API de commits: o primeiro da lista. */
    static Optional<Version> parseLatest(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return Optional.empty();
            JsonArray list = root.getAsJsonArray();
            if (list.isEmpty()) return Optional.empty();
            JsonObject c = list.get(0).getAsJsonObject();
            String sha = c.get("sha").getAsString();
            JsonObject commit = c.getAsJsonObject("commit");
            String date = commit.getAsJsonObject("committer").get("date").getAsString();
            String msg = commit.get("message").getAsString().lines().findFirst().orElse("");
            return Optional.of(new Version(sha, OffsetDateTime.parse(date), msg));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Tem versao mais nova? (outro commit e data depois da deste programa) */
    static boolean isNewer(Version local, Version latest) {
        return local != null && latest != null && !local.commit().equals(latest.commit())
                && latest.date().isAfter(local.date());
    }

    /** Pergunta ao GitHub e devolve a versao nova, se tiver (chamar fora da tela: pode demorar ate uns segundos). */
    public static Optional<Version> check() {
        Optional<Version> mine = local();
        if (mine.isEmpty()) return Optional.empty();
        return latest().filter(l -> isNewer(mine.get(), l));
    }
}

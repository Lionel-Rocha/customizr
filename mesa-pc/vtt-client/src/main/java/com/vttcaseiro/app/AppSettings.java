package com.vttcaseiro.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Configuracao minima que sobrevive entre uma abertura do programa e
 * outra -- por enquanto, so a pasta onde ficam as mesas (pra nao precisar
 * perguntar de novo toda vez que abrir). Guardado como um arquivo de
 * texto simples (uma linha) em {@code <pasta do usuario>/.vttcaseiro/settings.txt}
 * -- nao precisa de Gson pra isso, e um unico valor.
 */
public final class AppSettings {

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".vttcaseiro", "settings.txt");

    /** Arquivo SEPARADO (nao reusa o de cima, pra nao complicar o formato
     * "uma linha so" de settings.txt) com o id estavel deste jogador
     * nesta maquina -- ver com.vttcaseiro.net.GameClient pra entender por
     * que isso precisa ser estavel entre uma conexao e outra (retomada
     * de sessao: o servidor identifica dono de token por este id). */
    private static final Path PLAYER_ID_FILE = Path.of(System.getProperty("user.home"), ".vttcaseiro", "player-id.txt");
    /** Chave secreta desta instalacao: prova que o id de jogador e mesmo seu (o id e publico, a chave nao sai daqui). */
    private static final Path PLAYER_KEY_FILE = Path.of(System.getProperty("user.home"), ".vttcaseiro", "player-key.txt");

    private AppSettings() {
    }

    // ---- Preferencias pessoais (volume, animacao de dados): prefs.properties ----

    private static final Path PREFS = Path.of(System.getProperty("user.home"), ".vttcaseiro", "prefs.properties");
    private static java.util.Properties prefs;

    private static synchronized java.util.Properties prefs() {
        if (prefs == null) {
            prefs = new java.util.Properties();
            try (var in = Files.newInputStream(PREFS)) {
                prefs.load(in);
            } catch (IOException ignored) {
                // sem arquivo ainda: tudo no padrao
            }
        }
        return prefs;
    }

    private static synchronized void savePrefs() {
        try {
            Files.createDirectories(PREFS.getParent());
            try (var out = Files.newOutputStream(PREFS)) {
                prefs().store(out, "Customizr - preferências desta máquina");
            }
        } catch (IOException ignored) {
            // preferencia nao gravada: so volta ao padrao na proxima vez
        }
    }

    public static double getDouble(String key, double def) {
        try {
            return Double.parseDouble(prefs().getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public static void setDouble(String key, double value) {
        prefs().setProperty(key, String.valueOf(value));
        savePrefs();
    }

    public static String getString(String key, String def) {
        return prefs().getProperty(key, def);
    }

    public static void setString(String key, String value) {
        if (value == null) prefs().remove(key);
        else prefs().setProperty(key, value);
        savePrefs();
    }

    public static boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(prefs().getProperty(key, String.valueOf(def)));
    }

    public static void setBool(String key, boolean value) {
        prefs().setProperty(key, String.valueOf(value));
        savePrefs();
    }

    public static Optional<Path> loadMesasFolder() {
        try {
            if (!Files.exists(FILE)) {
                return Optional.empty();
            }
            String line = Files.readString(FILE, StandardCharsets.UTF_8).strip();
            if (line.isEmpty()) {
                return Optional.empty();
            }
            Path path = Path.of(line);
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (IOException | java.nio.file.InvalidPathException ex) {
            return Optional.empty();
        }
    }

    public static void saveMesasFolder(Path folder) {
        try {
            Files.createDirectories(FILE.getParent());
            com.vttcaseiro.library.AtomicFiles.writeString(FILE, folder.toString());
        } catch (IOException ex) {
            // nao e critico -- so significa que vai perguntar nalguma pergunta de novo da proxima vez
            System.err.println("Não foi possível salvar a configuração: " + ex.getMessage());
        }
    }

    /** Devolve o id salvo, ou gera um novo (UUID), salva e devolve --
     * numa falha ao SALVAR (disco sem permissao, etc.), ainda devolve um
     * id novo gerado na hora (so que ele nao vai sobreviver a um
     * reinicio do programa -- a sessao atual continua funcionando
     * normal, so a retomada entre uma abertura e outra do programa que
     * fica comprometida). Chamar isto de novo sempre devolve o MESMO id
     * depois da primeira vez (lido do arquivo, nao gerado de novo). */
    public static String getOrCreatePlayerId() {
        try {
            if (Files.exists(PLAYER_ID_FILE)) {
                String saved = Files.readString(PLAYER_ID_FILE, StandardCharsets.UTF_8).strip();
                if (!saved.isBlank()) {
                    return saved;
                }
            }
        } catch (IOException ignored) {
            // cai pro gerar-e-tentar-salvar abaixo
        }
        String generated = UUID.randomUUID().toString();
        try {
            Files.createDirectories(PLAYER_ID_FILE.getParent());
            com.vttcaseiro.library.AtomicFiles.writeString(PLAYER_ID_FILE, generated);
        } catch (IOException ex) {
            System.err.println("Não foi possível salvar o id de jogador: " + ex.getMessage());
        }
        return generated;
    }

    /** A chave secreta desta instalacao (32 bytes aleatorios em hex), criada na primeira vez. */
    public static String getOrCreatePlayerKey() {
        try {
            if (Files.exists(PLAYER_KEY_FILE)) {
                String saved = Files.readString(PLAYER_KEY_FILE, StandardCharsets.UTF_8).strip();
                if (saved.length() >= 32) return saved;
            }
        } catch (IOException ignored) {
            // gera uma nova abaixo
        }
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        String generated = java.util.HexFormat.of().formatHex(b);
        try {
            Files.createDirectories(PLAYER_KEY_FILE.getParent());
            com.vttcaseiro.library.AtomicFiles.writeString(PLAYER_KEY_FILE, generated);
        } catch (IOException ex) {
            System.err.println("Não foi possível salvar a chave de jogador: " + ex.getMessage());
        }
        return generated;
    }
}

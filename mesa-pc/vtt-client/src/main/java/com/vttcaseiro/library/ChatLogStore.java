package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.app.LogEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Historico do chat da mesa: uma linha JSON por mensagem em
 * {@code chat-log.jsonl}, na pasta da mesa. Antes o chat sumia ao fechar o
 * programa (rolagens, dano, quem fez o que na sessao passada). Cada maquina
 * guarda o que ELA viu -- sussurros e rolagens escondidas so ficam no
 * historico de quem os recebeu.
 * <p>
 * Acrescentar no fim (append) e de proposito: uma linha cortada no meio
 * (programa fechado a forca) so faz aquela linha ser ignorada ao ler.
 */
public class ChatLogStore {
    private static final Log log = Log.of(ChatLogStore.class);
    private static final Gson GSON = new Gson();
    /** Acima disto o arquivo e encurtado pras ultimas KEEP linhas. */
    private static final int MAX_LINES = 5000;
    private static final int KEEP_LINES = 2000;

    private final Path file;

    /** Uma linha do arquivo: quando + a mensagem. */
    record Line(long time, LogEntry entry) {
    }

    public ChatLogStore(Path mesaRoot) {
        this.file = mesaRoot.resolve("chat-log.jsonl");
    }

    public void append(LogEntry entry) {
        try {
            // Se a ultima linha ficou cortada (programa fechado no meio), comeca
            // numa linha nova -- senao esta mensagem grudaria na quebrada e as
            // duas se perderiam.
            String prefix = endsWithoutNewline() ? "\n" : "";
            Files.writeString(file, prefix + GSON.toJson(new Line(System.currentTimeMillis(), entry)) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.warn("Nao foi possivel guardar a mensagem no historico: " + ex.getMessage());
        }
    }

    private boolean endsWithoutNewline() throws IOException {
        if (!Files.exists(file)) return false;
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) return false;
            java.nio.ByteBuffer last = java.nio.ByteBuffer.allocate(1);
            channel.read(last, size - 1);
            return last.get(0) != '\n';
        }
    }

    /** As ultimas {@code max} mensagens (mais antiga primeiro). */
    public List<LogEntry> loadLast(int max) {
        List<LogEntry> result = new ArrayList<>();
        if (!Files.exists(file)) return result;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() > MAX_LINES) {
                // Arquivo grande demais: guarda so o fim (gravacao atomica).
                lines = new ArrayList<>(lines.subList(lines.size() - KEEP_LINES, lines.size()));
                AtomicFiles.writeString(file, String.join("\n", lines) + "\n");
            }
            // De tras pra frente ate juntar "max" mensagens VALIDAS (linha
            // cortada/invalida nao conta), depois volta pra ordem do chat.
            for (int i = lines.size() - 1; i >= 0 && result.size() < max; i--) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                try {
                    Line parsed = GSON.fromJson(line, Line.class);
                    if (parsed != null && parsed.entry() != null && parsed.entry().type() != null) {
                        result.add(parsed.entry());
                    }
                } catch (RuntimeException ignored) {
                    // linha cortada/invalida: pula
                }
            }
            java.util.Collections.reverse(result);
        } catch (IOException ex) {
            log.warn("Nao foi possivel ler o historico do chat: " + ex.getMessage());
        }
        return result;
    }
}

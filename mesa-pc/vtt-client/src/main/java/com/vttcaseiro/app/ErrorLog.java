package com.vttcaseiro.app;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Registro de erros em arquivo. O programa montado (Customizr.exe) nao tem console: sem isto, um erro na casa de um
 * jogador nao deixava rastro nenhum. Agora tudo que o Log escreve (e o que cai em System.err) vai pra
 * {@code <pasta do usuario>/.vttcaseiro/registros/customizr-0.log} (ate 5 arquivos de 2 MB, o mais novo e o 0), e
 * qualquer erro inesperado (excecao que ninguem tratou) fica gravado com o caminho completo do erro.
 */
public final class ErrorLog {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int FILES = 5;
    private static volatile boolean installed;
    /** Quem mostra o aviso na tela (o Main liga quando a janela existe). */
    private static volatile java.util.function.Consumer<Throwable> onUnexpected;

    private ErrorLog() {
    }

    /** Pasta dos registros. */
    public static Path folder() {
        return Path.of(System.getProperty("user.home"), ".vttcaseiro", "registros");
    }

    /** Liga o arquivo e o "pega erro inesperado" (chamar uma vez, antes da janela abrir). */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Files.createDirectories(folder());
            FileHandler file = new FileHandler(folder().resolve("customizr-%g.log").toString(), MAX_BYTES, FILES, true);
            file.setEncoding("UTF-8");
            file.setFormatter(new LineFormatter());
            file.setLevel(Level.INFO);
            Logger root = Logger.getLogger("");
            root.addHandler(file);
            // o que alguma biblioteca (ou codigo antigo) escreve direto em System.err tambem vai pro arquivo
            System.setErr(new PrintStream(new Tee(System.err, Logger.getLogger("stderr")), true, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ex) {
            System.err.println("Sem registro em arquivo: " + ex.getMessage());
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> report(thread.getName(), error));
        Logger.getLogger("customizr").info("Customizr aberto -- " + versionText() + ", Java " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    /** O Main diz como avisar na tela (alerta com "Abrir pasta de registros"). */
    public static void setOnUnexpected(java.util.function.Consumer<Throwable> handler) {
        onUnexpected = handler;
    }

    /** Grava um erro inesperado e avisa a tela (se ela ja existe). */
    static void report(String where, Throwable error) {
        Logger.getLogger("customizr").log(Level.SEVERE, "Erro inesperado (" + where + ")", error);
        java.util.function.Consumer<Throwable> h = onUnexpected;
        if (h != null) {
            try {
                h.accept(error);
            } catch (RuntimeException ignored) {
                // o aviso nao pode causar outro erro
            }
        }
    }

    /** "versao 33fd367 de 2026-11-26" (do versao.txt ao lado do programa) ou "rodando do codigo". */
    static String versionText() {
        return UpdateCheck.local().map(v -> "versão " + v.commit().substring(0, 7) + " de " + v.date().toLocalDate())
                .orElse("rodando do código");
    }

    /** Uma linha por registro: data, nivel, de onde, mensagem (e o erro completo, se tiver). */
    static final class LineFormatter extends Formatter {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord r) {
            StringBuilder sb = new StringBuilder();
            sb.append(TIME.format(LocalDateTime.now())).append(' ').append(r.getLevel().getName()).append(' ');
            String name = r.getLoggerName() == null ? "" : r.getLoggerName();
            sb.append(name.substring(name.lastIndexOf('.') + 1)).append(": ").append(formatMessage(r)).append(System.lineSeparator());
            if (r.getThrown() != null) {
                java.io.StringWriter w = new java.io.StringWriter();
                r.getThrown().printStackTrace(new java.io.PrintWriter(w));
                sb.append(w).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }

    /** Escreve no console de sempre e, linha a linha, no registro. */
    static final class Tee extends OutputStream {
        private final PrintStream console;
        private final Logger logger;
        private final java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();

        Tee(PrintStream console, Logger logger) {
            this.console = console;
            this.logger = logger;
        }

        @Override
        public synchronized void write(int b) {
            console.write(b);
            if (b == '\n') flushLine();
            else if (line.size() < 8192) line.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) write(b[i]);
        }

        /** (se o proprio registro escrever em System.err, nao entra em laco) */
        private final ThreadLocal<Boolean> busy = ThreadLocal.withInitial(() -> false);

        private void flushLine() {
            String s = line.toString(StandardCharsets.UTF_8).stripTrailing();
            line.reset();
            if (s.isEmpty() || busy.get()) return;
            busy.set(true);
            try {
                logger.warning(s);
            } finally {
                busy.set(false);
            }
        }
    }
}

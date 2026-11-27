package com.vttcaseiro.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/** Registro de erros: formato da linha, System.err indo pro registro, e o aviso de erro inesperado. */
class ErrorLogTest {

    @Test
    void lineHasLevelSourceMessageAndTheWholeError() {
        LogRecord r = new LogRecord(Level.SEVERE, "Erro inesperado (JavaFX)");
        r.setLoggerName("com.vttcaseiro.app.Main");
        r.setThrown(new IllegalStateException("textura nula"));
        String line = new ErrorLog.LineFormatter().format(r);
        assertTrue(line.contains(" SEVERE Main: Erro inesperado (JavaFX)"), line);
        assertTrue(line.contains("IllegalStateException: textura nula"));
        assertTrue(line.contains("at com.vttcaseiro.app.ErrorLogTest"), "caminho do erro");
    }

    @Test
    void stderrLinesGoToTheLog() {
        List<String> got = new CopyOnWriteArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(LogRecord record) { got.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        java.io.ByteArrayOutputStream console = new java.io.ByteArrayOutputStream();
        java.io.PrintStream err = new java.io.PrintStream(new ErrorLog.Tee(new java.io.PrintStream(console), logger), true);
        err.println("aviso do JavaFX");
        err.print("sem quebra ainda");
        assertEquals(List.of("aviso do JavaFX"), got);
        assertTrue(console.toString().contains("aviso do JavaFX"), "continua no console tambem");
    }

    @Test
    void unexpectedErrorsReachTheScreenHandler() {
        List<Throwable> seen = new CopyOnWriteArrayList<>();
        ErrorLog.setOnUnexpected(seen::add);
        try {
            ErrorLog.report("teste", new RuntimeException("boom"));
            assertEquals("boom", seen.get(0).getMessage());
        } finally {
            ErrorLog.setOnUnexpected(null);
        }
    }
}

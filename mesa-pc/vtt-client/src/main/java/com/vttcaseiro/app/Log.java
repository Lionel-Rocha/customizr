package com.vttcaseiro.app;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Log centralizado -- antes cada classe usava System.err.println direto,
 * sem nivel, sem saber de onde veio a mensagem, sem jeito de desligar
 * ruido de debug em producao. Usa java.util.logging (parte do JDK, zero
 * dependencia nova) em vez de trazer uma biblioteca externa so pra isso.
 * <p>
 * Uso: {@code private static final Log log = Log.of(MinhaClasse.class);}
 * no topo da classe, depois {@code log.info("...")}, {@code log.warn("...")},
 * {@code log.error("...", excecao)}.
 */
public final class Log {
    private final Logger logger;

    private Log(Class<?> owner) {
        this.logger = Logger.getLogger(owner.getName());
    }

    public static Log of(Class<?> owner) {
        return new Log(owner);
    }

    public void info(String message) {
        logger.log(Level.INFO, message);
    }

    public void warn(String message) {
        logger.log(Level.WARNING, message);
    }

    public void error(String message) {
        logger.log(Level.SEVERE, message);
    }

    public void error(String message, Throwable cause) {
        logger.log(Level.SEVERE, message, cause);
    }
}

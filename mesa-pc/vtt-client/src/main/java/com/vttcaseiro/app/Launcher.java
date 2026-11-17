package com.vttcaseiro.app;

/**
 * Ponto de entrada do Customizr.exe (jpackage) e do jar. Existe porque a JVM recusa
 * abrir direto uma classe que estende javafx.application.Application quando o JavaFX
 * esta no classpath ("JavaFX runtime components are missing"); por esta classe comum funciona.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}

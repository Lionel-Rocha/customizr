package com.vttcaseiro.app;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/** "Connection refused" ao abrir na ponte vira uma explicacao do que falta (a ponte rodando num servidor). */
class RelayErrorTextTest {

    @Test
    void refusedExplainsThatTheBridgeMustBeRunning() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        Exception real = assertThrows(Exception.class, () ->
                new com.vttcaseiro.relay.RelayHostLink("127.0.0.1", port, "ABC-123", "segredo", (sock, id) -> { }, null, m -> { }).start());
        String text = Main.relayErrorText("127.0.0.1", port, real);
        assertTrue(text.contains("Não tem ponte rodando"), text);
        assertTrue(text.contains("Customizr-Ponte"), text);
        assertTrue(text.contains("este próprio computador"), "endereco local ganha a dica extra");
        assertTrue(Main.relayErrorText("meuservidor.com", 55070, new java.net.UnknownHostException("x"))
                .contains("não encontrado"));
    }
}

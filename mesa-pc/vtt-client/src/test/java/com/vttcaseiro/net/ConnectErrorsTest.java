package com.vttcaseiro.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/** Conectar num lugar errado da um erro que explica o que conferir (e nao trava pra sempre). */
class ConnectErrorsTest {

    @Test
    void closedPortSaysRefusedWithHint() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort(); // porta que estava livre e agora fechou
        }
        GameClient c = new GameClient(new GameServerSecurityTest.Recorder(), null, "x");
        long start = System.currentTimeMillis();
        IOException ex = assertThrows(IOException.class, () -> c.connect("127.0.0.1", port, "Ana", false));
        assertTrue(ex.getMessage().contains("recusada") && ex.getMessage().contains("Hospedar aqui"), ex.getMessage());
        assertTrue(System.currentTimeMillis() - start < GameClient.CONNECT_TIMEOUT_MS + 2000, "nao esperou demais");
    }

    @Test
    void unknownHostSaysNotFound() {
        GameClient c = new GameClient(new GameServerSecurityTest.Recorder(), null, "x");
        IOException ex = assertThrows(IOException.class, () -> c.connect("mesa-que-nao-existe.invalid", 55055, "Ana", false));
        assertTrue(ex.getMessage().contains("não encontrado"), ex.getMessage());
    }
}

package com.vttcaseiro.app;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.net.GameServer;
import com.vttcaseiro.view.MesaSelectorView;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/** playit.gg: endereco colado como "algo.ply.gg:12345" e o teste do tunel (acha a mesa do outro lado). */
class PlayitTunnelTest {

    @Test
    void pastedAddressSplitsIntoHostAndPort() {
        assertArrayEquals(new String[]{"cool-name.gl.at.ply.gg", "12345"}, MesaSelectorView.splitHostPort("cool-name.gl.at.ply.gg:12345"));
        assertArrayEquals(new String[]{"algo.ply.gg", "999"}, MesaSelectorView.splitHostPort(" tcp://algo.ply.gg:999/ "));
        assertNull(MesaSelectorView.splitHostPort("algo.ply.gg"), "sem porta: fica como esta");
        assertNull(MesaSelectorView.splitHostPort("algo.ply.gg:"));
        assertNull(MesaSelectorView.splitHostPort("algo:99999"), "porta fora do intervalo");
        assertNull(MesaSelectorView.splitHostPort("fe80::1"), "IPv6 nao e quebrado");
    }

    @Test
    void tunnelTestFindsTheTableOrExplains() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        try {
            assertNull(Main.testTunnel("127.0.0.1", server.getPort()), "a mesa respondeu");
        } finally {
            server.stop();
        }
        int closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = s.getLocalPort();
        }
        String problem = Main.testTunnel("127.0.0.1", closed);
        assertNotNull(problem);
        assertTrue(problem.contains("55055"), problem);
        assertTrue(Main.testTunnel("nao-existe.invalid", 1234).contains("não encontrado"));
    }
}

package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Condicao Invisivel: os outros jogadores nem recebem o token; o dono e o mestre continuam vendo. */
class InvisibleNetTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void invisibleTokenHidesFromOtherPlayers() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GameServerSecurityTest.Recorder gm = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder ana = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder beto = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");
            SceneModel s = new SceneModel();
            TokenModel lyra = new TokenModel("Lyra", null);
            lyra.setOwnerId("ana");
            lyra.setConditions(List.of("INVISIBLE"));
            s.addToken(lyra);
            s.addToken(new TokenModel("Orc", null));
            gmC.sendSceneOpen(s, "Cena");
            waitFor(() -> ana.scene.getTokens().size() == 2 && beto.scene.getTokens().size() == 1
                    && gm.scene.getTokens().size() == 2, "Beto nao recebe a Lyra invisivel; Ana e o mestre sim");
            assertEquals("Orc", beto.scene.getTokens().get(0).getName());

            // ficou visivel de novo: o Beto passa a receber
            TokenModel visible = NetCodec.deepCopy(lyra, TokenModel.class);
            visible.setConditions(List.of());
            anaC.sendUpdateToken(visible);
            waitFor(() -> beto.tokensAdded.stream().anyMatch(t -> t.getName().equals("Lyra")), "Lyra apareceu pro Beto");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}

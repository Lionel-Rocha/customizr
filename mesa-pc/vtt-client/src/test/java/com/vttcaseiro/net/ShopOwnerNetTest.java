package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Loja: o servidor so passa pedido feito com a propria ficha. */
class ShopOwnerNetTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<NetMessage.ShopRequest> requests = new CopyOnWriteArrayList<>();

        @Override public void onShopRequest(NetMessage.ShopRequest r) { requests.add(r); }
    }

    @Test
    void shopOnlyAcceptsYourOwnSheet() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec beto = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "conectados");
            CharacterSheet lyra = new CharacterSheet("Lyra");
            anaC.sendCharacterSync(lyra);
            Thread.sleep(200);
            NetMessage.ShopRequest steal = new NetMessage.ShopRequest();
            steal.shopId = "s";
            steal.item = "Corda";
            steal.sheetId = lyra.getId();
            betoC.sendShopRequest(steal);
            NetMessage.ShopRequest mine = new NetMessage.ShopRequest();
            mine.shopId = "s";
            mine.item = "Corda";
            mine.sheetId = lyra.getId();
            anaC.sendShopRequest(mine);
            waitFor(() -> gm.requests.size() == 1, "o pedido da Ana chegou");
            Thread.sleep(200);
            assertEquals(1, gm.requests.size(), "o do Beto com a ficha da Ana nao");
            assertEquals("Ana", gm.requests.get(0).player);
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}

package com.vttcaseiro.net;

import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Drawing;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Revisao de 26/11: o que um jogador conectado nao pode mais fazer. */
class HardeningNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<MobSheet> mobs = new CopyOnWriteArrayList<>();
        final List<Drawing> drawings = new CopyOnWriteArrayList<>();
        @Override public void onMobSync(MobSheet m) { mobs.add(m); }
        @Override public void onDrawingAdded(Drawing d) { drawings.add(d); }
    }

    private static void waitFor(BooleanSupplier cond, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timeout esperando: " + what);
    }

    @Test
    void notANumberIsRejectedBeforeItReachesTheTable() {
        assertThrows(RuntimeException.class,
                () -> NetCodec.decode("{\"type\":\"move_token\",\"tokenId\":\"t\",\"x\":NaN,\"y\":1}"));
        assertThrows(RuntimeException.class,
                () -> NetCodec.decode("{\"type\":\"move_token\",\"tokenId\":\"t\",\"x\":1,\"y\":Infinity}"));
        assertEquals(5.0, NetCodec.decode("{\"type\":\"move_token\",\"tokenId\":\"t\",\"x\":5,\"y\":1}").x);
    }

    @Test
    void playersCannotTouchMonstersOthersDrawingsOrForgeTokenOwners() throws Exception {
        GameServer server = new GameServer(0, new SceneModel(), null, "senha", "mestre");
        server.start();
        Rec gm = new Rec(), ana = new Rec(), beto = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm-id");
        GameClient anaC = new GameClient(ana, null, "ana-id");
        GameClient betoC = new GameClient(beto, null, "beto-id");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true, "senha", "mestre");
            anaC.connect("localhost", server.getPort(), "Ana", false, "senha", null);
            betoC.connect("localhost", server.getPort(), "Beto", false, "senha", null);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "3 conectados");

            // monstro: so o mestre
            anaC.sendMobSync(new MobSheet("Dragão Falso"));
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Só o mestre mexe nos monstros")), "recusa monstro");
            assertTrue(gm.mobs.isEmpty(), "o mestre nao recebe (e nao grava) monstro de jogador");
            MobSheet goblin = new MobSheet("Goblin");
            goblin.setHiddenFromPlayers(false); // monstro novo nasce escondido dos jogadores
            gmC.sendMobSync(goblin);
            waitFor(() -> ana.mobs.size() == 1, "monstro do mestre chega");

            // desenho do mestre: jogador nao troca mandando o mesmo id
            Drawing gmDrawing = Drawing.text(10, 10, "Aqui é o covil", "#E2483F");
            gmC.sendDrawingAdd(gmDrawing);
            waitFor(() -> beto.drawings.size() == 1, "desenho do mestre");
            Drawing fake = Drawing.text(10, 10, "pichação", "#E2483F");
            java.lang.reflect.Field id = Drawing.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(fake, gmDrawing.getId());
            anaC.sendDrawingAdd(fake);
            Drawing own = Drawing.text(50, 50, "meu", "#E2483F");
            anaC.sendDrawingAdd(own); // referencia de ordem: o proprio desenho passa
            waitFor(() -> beto.drawings.stream().anyMatch(d -> "meu".equals(d.getText())), "desenho proprio");
            assertTrue(beto.drawings.stream().noneMatch(d -> "pichação".equals(d.getText())), "nao trocou o do mestre");

            // token de jogador e sempre dele, com imagem so no formato seguro
            TokenModel t = new TokenModel("Espião", null);
            t.setOwnerId("gm-id");
            t.setImageAssetId("../../segredo.png");
            anaC.sendAddToken(t);
            waitFor(() -> gm.tokensAdded.size() == 1, "token da Ana");
            assertEquals("ana-id", gm.tokensAdded.get(0).getOwnerId());
            assertNull(gm.tokensAdded.get(0).getImageAssetId());
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }

    @Test
    void visionAndLightFromTheNetworkStayInsideTheLimit() {
        TokenModel mine = new TokenModel("Lyra", null);
        TokenModel sent = new com.google.gson.Gson().fromJson("{\"name\":\"Lyra\",\"visionFeet\":999999,\"lightFeet\":-5}", TokenModel.class);
        mine.copyStateFrom(sent);
        assertEquals(1000, mine.getVisionFeet());
        assertEquals(0, mine.getLightFeet());
    }
}

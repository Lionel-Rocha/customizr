package com.vttcaseiro.net;

import com.vttcaseiro.model.Drawing;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Desenhos e diario com servidor e clientes reais. */
class DrawingsAndJournalNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Drawing> added = new CopyOnWriteArrayList<>();
        final List<String> removed = new CopyOnWriteArrayList<>();
        final List<String> cleared = new CopyOnWriteArrayList<>();
        final List<Handout> handouts = new CopyOnWriteArrayList<>();
        final List<String> handoutsRemoved = new CopyOnWriteArrayList<>();

        @Override public void onDrawingAdded(Drawing d) { added.add(d); }
        @Override public void onDrawingRemoved(String id) { removed.add(id); }
        @Override public void onDrawingsCleared(String owner) { cleared.add(String.valueOf(owner)); }
        @Override public void onHandoutSync(Handout h) { handouts.add(h); }
        @Override public void onHandoutRemoved(String id) { handoutsRemoved.add(id); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void desenhosComDonoCarimbadoEPermissaoDeApagar() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            Drawing fake = Drawing.path(List.of(0.0, 0.0, 30.0, 30.0), "#00ff00", 3);
            fake.setOwnerId("gm"); // tentando se passar pelo mestre
            anaC.sendDrawingAdd(fake);
            waitFor(() -> gm.added.size() == 1, "desenho chegou no mestre");
            assertEquals("ana", gm.added.get(0).getOwnerId(), "dono e quem mandou");

            Drawing gmText = Drawing.text(5, 5, "Saida", "#ffffff");
            gmC.sendDrawingAdd(gmText);
            waitFor(() -> ana.added.size() == 1, "texto do mestre chegou");

            anaC.sendDrawingRemove(gmText.getId());
            Thread.sleep(250);
            assertTrue(gm.removed.isEmpty(), "jogador nao apaga desenho do mestre");

            lateC.connect("localhost", server.getPort(), "Atrasado", false);
            waitFor(() -> late.scene != null, "atrasado entrou");
            assertEquals(2, late.scene.getDrawings().size(), "quem entra depois ve os desenhos");

            anaC.sendDrawingClear(null); // jogador pedindo "apagar tudo" -> so os dele
            waitFor(() -> gm.cleared.size() == 1, "limpeza chegou");
            assertEquals("ana", gm.cleared.get(0));

            gmC.sendDrawingRemove(gmText.getId());
            waitFor(() -> ana.removed.contains(gmText.getId()), "mestre apaga");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }

    @Test
    void diarioReveladoChegaNosJogadoresEEsconderTira() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            Handout secret = new Handout("Plano do vilao", "...");
            gmC.sendHandout(secret); // nao revelada: ninguem recebe
            Handout fakeFromPlayer = new Handout("Falso", "x");
            fakeFromPlayer.setRevealed(true);
            anaC.sendHandout(fakeFromPlayer); // jogador nao publica no diario
            Handout letter = new Handout("Carta", "Venham sozinhos.");
            letter.setRevealed(true);
            gmC.sendHandout(letter);
            waitFor(() -> ana.handouts.size() == 1, "carta chegou");
            Thread.sleep(200);
            assertEquals("Carta", ana.handouts.get(0).getTitle());
            assertEquals(1, ana.handouts.size(), "so a revelada, e nada do jogador");

            lateC.connect("localhost", server.getPort(), "Atrasado", false);
            waitFor(() -> late.handouts.size() == 1, "quem entra depois recebe as reveladas");

            letter.setRevealed(false);
            gmC.sendHandout(letter);
            waitFor(() -> ana.handoutsRemoved.contains(letter.getId()), "esconder tira dos jogadores");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }
}

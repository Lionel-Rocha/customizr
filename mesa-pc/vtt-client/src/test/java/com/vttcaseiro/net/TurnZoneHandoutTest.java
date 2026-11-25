package com.vttcaseiro.net;

import com.vttcaseiro.model.Encounter;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.MapZone;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.TurnTimer;
import com.vttcaseiro.service.ZoneRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Relogio do turno, zona de som e material so pra alguns jogadores. */
class TurnZoneHandoutTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Handout> handouts = new CopyOnWriteArrayList<>();
        final List<Handout> shown = new CopyOnWriteArrayList<>();
        final List<String> removed = new CopyOnWriteArrayList<>();

        @Override public void onHandoutSync(Handout h) { handouts.add(h); }
        @Override public void onHandoutShow(Handout h) { shown.add(h); }
        @Override public void onHandoutRemoved(String id) { removed.add(id); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void turnTimerRestartsOnNewTurnPausesAndWarnsOnce() {
        Encounter e = new Encounter();
        e.setActive(true);
        e.setRound(1);
        e.setCurrentTokenId("a");
        TurnTimer t = new TurnTimer();
        t.update(e, 0);
        assertFalse(t.running(), "sem tempo escolhido: desligado");
        e.setTurnSeconds(30);
        t.update(e, 1000);
        assertEquals(30_000, t.leftMs(1000));
        assertEquals("0:20", TurnTimer.format(t.leftMs(11_000)));
        assertEquals("warn", t.state(25_000));
        e.setTimerPaused(true);
        t.update(e, 21_000);
        assertEquals(10_000, t.leftMs(99_000), "pausado nao anda");
        e.setTimerPaused(false);
        t.update(e, 100_000);
        assertEquals(5_000, t.leftMs(105_000), "volta de onde parou");
        assertFalse(t.checkExpired(105_000));
        assertTrue(t.checkExpired(110_000), "acabou: avisa");
        assertFalse(t.checkExpired(111_000), "uma vez so");
        assertEquals("over", t.state(111_000));
        e.setCurrentTokenId("b");
        t.update(e, 200_000);
        assertEquals(30_000, t.leftMs(200_000), "vez de outro: comeca de novo");
        e.restartTimer();
        t.update(e, 210_000);
        assertEquals(30_000, t.leftMs(210_000), "reiniciar do mestre");
        assertEquals("1 min 30 s", TurnTimer.label(90));
        assertEquals("Desligado", TurnTimer.label(0));
    }

    @Test
    void soundZoneTriggersOnlyForPlayerTokensWhenAsked() {
        SceneModel scene = new SceneModel();
        MapZone z = new MapZone(100, 100, 100, 100, MapZone.SOUND);
        z.setPlayersOnly(true);
        z.setSoundId("abc");
        z.setSoundChannel("music");
        z.setOnce(false);
        scene.getZones().add(z);
        TokenModel goblin = new TokenModel();
        goblin.setX(150);
        goblin.setY(150);
        assertTrue(ZoneRules.entered(scene, goblin, 0, 0).isEmpty(), "monstro nao troca a musica");
        TokenModel hero = new TokenModel();
        hero.setOwnerId("ana");
        hero.setX(150);
        hero.setY(150);
        assertEquals(List.of(z), ZoneRules.entered(scene, hero, 0, 0));
        assertEquals("Som", z.label());
        assertEquals("music", z.getSoundChannel());
        z.setSoundChannel("qualquer");
        assertEquals("sfx", z.getSoundChannel(), "canal desconhecido vira efeito");
    }

    @Test
    void handoutGoesOnlyToChosenPlayersAndOpensOnTheirScreen() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec beto = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        GameClient lateC = new GameClient(late, null, "ana2");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "conectados");

            Handout note = new Handout("Bilhete", "Te encontro na ponte.");
            note.setRevealed(true);
            note.setRecipients(List.of("Ana"));
            gmC.sendHandoutShow(note);
            waitFor(() -> ana.shown.size() == 1, "abriu na tela da Ana");
            assertEquals(1, ana.handouts.size());
            Thread.sleep(200);
            assertTrue(beto.handouts.isEmpty(), "Beto nao recebe");
            assertTrue(beto.shown.isEmpty());

            Handout open = new Handout("Mapa", "x");
            open.setRevealed(true);
            gmC.sendHandout(open);
            waitFor(() -> beto.handouts.size() == 1, "sem lista: todos");
            assertTrue(beto.shown.isEmpty(), "so revelar nao abre na tela");

            lateC.connect("localhost", server.getPort(), "Carla", false);
            waitFor(() -> late.handouts.size() == 1, "quem entra depois so recebe o que e dele");
            assertEquals("Mapa", late.handouts.get(0).getTitle());

            note.setRecipients(List.of("Beto"));
            gmC.sendHandout(note);
            waitFor(() -> ana.removed.contains(note.getId()), "saiu da lista: some da Ana");
            waitFor(() -> beto.handouts.stream().anyMatch(h -> h.getId().equals(note.getId())), "e chega no Beto");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }
}

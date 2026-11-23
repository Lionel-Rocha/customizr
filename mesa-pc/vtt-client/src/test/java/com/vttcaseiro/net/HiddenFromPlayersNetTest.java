package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Cena escondida (mestre preparando), ficha e monstro escondidos: nada disso sai do servidor pro jogador. */
class HiddenFromPlayersNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<MobSheet> mobs = new CopyOnWriteArrayList<>();
        final List<List<MapPin>> pins = new CopyOnWriteArrayList<>();
        final List<SceneModel> scenes = new CopyOnWriteArrayList<>();
        @Override public void onMobSync(MobSheet m) { mobs.add(m); }
        @Override public void onPinsSync(List<MapPin> p) { pins.add(p); }
        @Override public void onSceneSync(SceneModel s) {
            super.onSceneSync(s);
            scenes.add(s);
        }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void hiddenSceneShowsOnlyACurtainUntilRevealed() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "entraram");

            SceneModel lair = new SceneModel();
            lair.setName("Covil do dragao");
            lair.setHiddenFromPlayers(true);
            TokenModel dragon = new TokenModel("Dragao", null);
            lair.addToken(dragon);
            lair.getPins().add(new MapPin(10, 10, "Tesouro", "tesouro"));
            int before = ana.scenes.size();
            gmC.sendSceneSwitch(lair);
            waitFor(() -> ana.scenes.size() > before, "jogadora recebeu a troca");
            SceneModel seen = ana.scene;
            assertTrue(seen.isHiddenFromPlayers(), "jogadora sabe que esta escondida");
            assertTrue(seen.getTokens().isEmpty(), "nenhum token");
            assertTrue(seen.getPins().isEmpty(), "nenhum pin");
            assertEquals("", seen.getName(), "nem o nome da cena");

            // mestre mexendo na cena escondida: nada chega pra ela
            TokenModel goblin = new TokenModel("Goblin", null);
            gmC.sendAddToken(goblin);
            gmC.sendMoveToken(dragon.getId(), 300, 300);
            gmC.sendPins(lair);
            // uma mensagem que nao e da cena (chat) chega, e serve de "marcador" de ordem
            gmC.sendChat("Mestre", "preparando...");
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("preparando...")), "chat chegou");
            assertTrue(ana.tokensAdded.isEmpty(), "token novo nao vazou");
            assertTrue(ana.pins.isEmpty(), "pins nao vazaram");

            // quem entra durante a preparacao tambem so ve a cortina
            Rec beto = new Rec();
            GameClient betoC = new GameClient(beto, null, "beto");
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> beto.scene != null, "Beto entrou");
            assertTrue(beto.scene.isHiddenFromPlayers());
            assertTrue(beto.scene.getTokens().isEmpty());
            betoC.disconnect();

            // mestre mostra: chega tudo
            lair.setHiddenFromPlayers(false);
            lair.addToken(goblin);
            int n = ana.scenes.size();
            gmC.sendSceneSwitch(lair);
            waitFor(() -> ana.scenes.size() > n, "liberou");
            assertFalse(ana.scene.isHiddenFromPlayers());
            assertEquals(2, ana.scene.getTokens().size());
            assertEquals("Covil do dragao", ana.scene.getName());
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }

    @Test
    void hiddenSheetsGoOnlyToTheMasterAndTheOwner() throws Exception {
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
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");

            CharacterSheet villain = new CharacterSheet("Vilão secreto");
            villain.setHiddenFromPlayers(true);
            gmC.sendCharacterSync(villain);
            CharacterSheet spy = new CharacterSheet("Espiã da Ana");
            spy.setOwnerPlayerId("ana");
            spy.setHiddenFromPlayers(true);
            gmC.sendCharacterSync(spy);
            MobSheet boss = new MobSheet("Chefe");
            boss.setHiddenFromPlayers(true);
            gmC.sendMobSync(boss);
            MobSheet rat = new MobSheet("Rato");
            rat.setHiddenFromPlayers(false); // monstro nasce escondido; este o mestre liberou
            gmC.sendMobSync(rat);
            gmC.sendChat("Mestre", "fim");
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("fim"))
                    && beto.chats.stream().anyMatch(c -> c.contains("fim")), "chat chegou");
            waitFor(() -> !ana.sheets.isEmpty() && !beto.mobs.isEmpty(), "o que pode chegou");

            assertEquals(List.of("Espiã da Ana"), ana.sheets.stream().map(CharacterSheet::getName).toList(),
                    "Ana so recebe a ficha escondida que e dela");
            assertTrue(beto.sheets.isEmpty(), "Beto nao recebe nenhuma");
            assertEquals(List.of("Rato"), beto.mobs.stream().map(MobSheet::getName).toList(), "monstro escondido nao vai");

            // quem entra depois tambem nao recebe
            Rec late = new Rec();
            GameClient lateC = new GameClient(late, null, "late");
            lateC.connect("localhost", server.getPort(), "Caio", false);
            waitFor(() -> late.scene != null && !late.mobs.isEmpty(), "Caio entrou");
            assertTrue(late.sheets.isEmpty());
            assertEquals(List.of("Rato"), late.mobs.stream().map(MobSheet::getName).toList());
            lateC.disconnect();
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}

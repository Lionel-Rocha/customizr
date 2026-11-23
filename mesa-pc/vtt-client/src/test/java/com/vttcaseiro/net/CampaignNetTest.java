package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Campanha pela rede (jogador so recebe o publico) e ficha/monstro escondidos depois somem da lista do jogador. */
class CampaignNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Campaign> campaigns = new CopyOnWriteArrayList<>();
        final List<String> removedSheets = new CopyOnWriteArrayList<>();
        final List<String> removedMobs = new CopyOnWriteArrayList<>();
        final List<MobSheet> mobs = new CopyOnWriteArrayList<>();
        @Override public void onCampaignSync(Campaign c) { campaigns.add(c); }
        @Override public void onCharacterRemoved(String id) { removedSheets.add(id); }
        @Override public void onMobRemoved(String id) { removedMobs.add(id); }
        @Override public void onMobSync(MobSheet m) { mobs.add(m); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void playersGetOnlyThePublicCampaign() throws Exception {
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

            Campaign c = new Campaign();
            Campaign.Npc known = new Campaign.Npc();
            known.name = "Bento";
            known.visible = true;
            known.gmNotes = "espião";
            Campaign.Npc secret = new Campaign.Npc();
            secret.name = "Lorde Sombrio";
            c.getNpcs().add(known);
            c.getNpcs().add(secret);
            c.setGmNotes("plano secreto");
            gmC.sendCampaign(c);
            waitFor(() -> !ana.campaigns.isEmpty(), "jogadora recebeu");
            Campaign got = ana.campaigns.get(ana.campaigns.size() - 1);
            assertEquals(1, got.getNpcs().size());
            assertEquals("Bento", got.getNpcs().get(0).name);
            assertEquals("", got.getNpcs().get(0).gmNotes);
            assertEquals("", got.getGmNotes());

            // jogador nao muda a campanha
            anaC.sendCampaign(new Campaign());
            waitFor(() -> ana.chats.stream().anyMatch(m -> m.contains("Só o mestre mexe na campanha")), "recusou");

            // quem entra depois recebe a publica; mestre que reentra recebe tudo
            Rec late = new Rec();
            GameClient lateC = new GameClient(late, null, "late");
            lateC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> !late.campaigns.isEmpty(), "Beto recebeu");
            assertEquals(1, late.campaigns.get(0).getNpcs().size());
            lateC.disconnect();
            Rec gm2 = new Rec();
            GameClient gm2C = new GameClient(gm2, null, "gm2");
            gm2C.connect("localhost", server.getPort(), "Mestre2", true);
            waitFor(() -> !gm2.campaigns.isEmpty(), "mestre 2 recebeu");
            assertEquals(2, gm2.campaigns.get(0).getNpcs().size());
            assertEquals("plano secreto", gm2.campaigns.get(0).getGmNotes());
            gm2C.disconnect();
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }

    @Test
    void hidingLaterRemovesFromPlayersAndMonstersStartHidden() throws Exception {
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

            CharacterSheet npc = new CharacterSheet("Duque");
            gmC.sendCharacterSync(npc);
            waitFor(() -> !ana.sheets.isEmpty(), "ficha visível chegou");
            npc.setHiddenFromPlayers(true);
            gmC.sendCharacterSync(npc);
            waitFor(() -> ana.removedSheets.contains(npc.getId()), "escondida depois: some da lista dela");

            MobSheet goblin = new MobSheet("Goblin");
            assertTrue(goblin.isHiddenFromPlayers(), "monstro nasce escondido");
            gmC.sendMobSync(goblin);
            waitFor(() -> ana.removedMobs.contains(goblin.getId()), "jogadora recebe só a remoção");
            assertTrue(ana.mobs.isEmpty(), "a ficha do monstro não chega");
            goblin.setHiddenFromPlayers(false);
            gmC.sendMobSync(goblin);
            waitFor(() -> !ana.mobs.isEmpty(), "mestre liberou: chega");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }
}

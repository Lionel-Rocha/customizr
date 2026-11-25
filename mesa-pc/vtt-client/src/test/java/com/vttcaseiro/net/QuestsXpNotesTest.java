package com.vttcaseiro.net;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.Money;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.RandomTable;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.service.CoinSplit;
import com.vttcaseiro.service.XpService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Etapas de missao, tabelas com dados/tabela dentro, moedas do bau, XP e o diario dos jogadores. */
class QuestsXpNotesTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<Handout> notes = new CopyOnWriteArrayList<>();
        final List<String> removed = new CopyOnWriteArrayList<>();

        @Override public void onGroupNote(Handout h) { notes.add(h); }
        @Override public void onGroupNoteRemoved(String id) { removed.add(id); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void questStepsShowProgressAndGoToPlayers() {
        Campaign c = new Campaign();
        Campaign.Adventure a = new Campaign.Adventure();
        a.name = "O ferreiro sumido";
        a.giver = "Barão";
        a.reward = "200 po";
        a.visible = true;
        a.gmNotes = "foi o barão";
        a.steps().add(new Campaign.Step("Falar com a filha"));
        a.steps().add(new Campaign.Step("Achar a mina"));
        a.steps().get(0).done = true;
        c.getAdventures().add(a);
        assertEquals("1/2 etapas", a.progress());
        Campaign pub = c.publicCopy();
        Campaign.Adventure seen = pub.getAdventures().get(0);
        assertEquals(2, seen.steps().size(), "jogador ve as etapas");
        assertEquals("200 po", seen.reward);
        assertEquals("", seen.gmNotes);
        assertEquals("", new Campaign.Adventure().progress());
    }

    @Test
    void tablesRollDiceAndOtherTablesInTheResult() {
        RandomTable weather = new RandomTable("Clima", "Chuva");
        RandomTable road = new RandomTable("Estrada", "1: 2d4+1 lobos e [[Clima]]\n2: [[Nada]]");
        String r = road.rollExpanded(n -> 1, name -> "Clima".equals(name) ? Optional.of(weather) : Optional.empty());
        assertEquals("d2 = 1: 2d4+1 [3] lobos e Chuva", r);
        String ok = RandomTable.expand("2d4+1 lobos e [[Clima]]", n -> 1, name -> Optional.of(weather));
        assertEquals("2d4+1 [3] lobos e Chuva", ok);
        assertEquals("[[Nada]]", RandomTable.expand("[[Nada]]", n -> 1, name -> Optional.empty()), "tabela que nao existe fica");
        RandomTable loop = new RandomTable("Laco", "[[Laco]]");
        String deep = RandomTable.expand("[[Laco]]", n -> 1, name -> Optional.of(loop));
        assertEquals("[[Laco]]", deep, "laco para no limite");
        assertEquals("usa d20s", RandomTable.expand("usa d20s", n -> 1, null), "palavra grudada nao e dado");
    }

    @Test
    void coinsSplitEvenlyAndTheRestStaysInTheChest() {
        Money chest = new Money();
        chest.add(Currency.GOLD, 31);
        chest.add(Currency.SILVER, 2);
        Money a = new Money();
        Money b = new Money();
        Money c = new Money();
        String r = CoinSplit.split(chest, List.of(a, b, c));
        assertEquals(10, a.get(Currency.GOLD));
        assertEquals(10, c.get(Currency.GOLD));
        assertEquals(1, chest.get(Currency.GOLD), "sobra fica no bau");
        assertEquals(2, chest.get(Currency.SILVER), "prata nao da uma pra cada");
        assertEquals("10 po pra cada um; ficou no baú: 1 po, 2 pp", r);
        assertEquals("1 po, 2 pp", CoinSplit.describe(chest));
        assertEquals("", CoinSplit.split(new Money(), List.of(a)));
    }

    @Test
    void xpSplitsAndTellsWhoCanLevelUp() {
        CharacterSheet lyra = new CharacterSheet("Lyra");
        lyra.setExperiencePoints(250);
        CharacterSheet bram = new CharacterSheet("Bram");
        List<XpService.Award> awards = XpService.award(List.of(lyra, bram), 100, true);
        assertEquals(300, lyra.getExperiencePoints());
        assertEquals(50, bram.getExperiencePoints());
        assertTrue(awards.get(0).canLevelUp(), "300 XP: nivel 2");
        assertFalse(awards.get(1).canLevelUp());
        String text = XpService.summary(awards, "Covil");
        assertTrue(text.contains("Lyra +50 XP (total 300, pode subir pro nível 2!)"), text);
        assertEquals(5, XpService.levelForXp(6500));
        assertEquals(20, XpService.levelForXp(999_999));
        assertEquals(600, XpService.toNextLevel(300));
        XpService.award(List.of(bram), 100, false);
        assertEquals(150, bram.getExperiencePoints(), "cada um recebe o valor");
    }

    @Test
    void groupNotesAreStampedSharedAndOnlyTheAuthorChangesThem() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec beto = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "conectados");

            Handout note = new Handout("Pistas", "o culto usa capas verdes");
            note.setAuthor("Mestre"); // tentando se passar por outro
            anaC.sendGroupNote(note);
            waitFor(() -> beto.notes.size() == 1 && ana.notes.size() == 1, "chegou em todos, inclusive de volta pra Ana");
            assertEquals("Ana", beto.notes.get(0).getAuthor(), "o servidor carimba quem escreveu");

            Handout hijack = new Handout("Pistas", "mentira");
            java.lang.reflect.Field id = Handout.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(hijack, note.getId());
            betoC.sendGroupNote(hijack);
            betoC.sendGroupNoteRemove(note.getId());
            Thread.sleep(250);
            assertEquals(1, ana.notes.size(), "Beto nao muda a da Ana");
            assertTrue(ana.removed.isEmpty(), "nem apaga");

            lateC.connect("localhost", server.getPort(), "Carla", false);
            waitFor(() -> late.notes.size() == 1, "quem entra depois recebe");

            anaC.sendGroupNoteRemove(note.getId());
            waitFor(() -> beto.removed.contains(note.getId()), "a autora apaga");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }
}

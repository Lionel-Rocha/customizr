package com.vttcaseiro.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Gerenciador de campanha: o que os jogadores veem, calendario, clima e arquivo. */
class CampaignTest {

    @TempDir
    Path dir;

    private static Campaign sample() {
        Campaign c = new Campaign();
        Campaign.PlayerRecord ana = new Campaign.PlayerRecord();
        ana.player = "Ana";
        ana.gmNotes = "deve favor ao duque";
        c.getPlayers().add(ana);
        Campaign.PlayerRecord secret = new Campaign.PlayerRecord();
        secret.player = "Convidado surpresa";
        secret.visible = false;
        c.getPlayers().add(secret);
        Campaign.Npc taverneiro = new Campaign.Npc();
        taverneiro.name = "Bento";
        taverneiro.visible = true;
        taverneiro.gmNotes = "espião do culto";
        c.getNpcs().add(taverneiro);
        Campaign.Npc villain = new Campaign.Npc();
        villain.name = "Lorde Sombrio";
        c.getNpcs().add(villain);
        Campaign.Adventure a = new Campaign.Adventure();
        a.name = "Resgate na mina";
        a.visible = true;
        a.gmNotes = "o refém é o traidor";
        c.getAdventures().add(a);
        Campaign.Adventure next = new Campaign.Adventure();
        next.name = "Próximo arco";
        c.getAdventures().add(next);
        c.setGmNotes("ideias secretas");
        c.getCalendar().notes = "festival dia 15";
        return c;
    }

    @Test
    void publicCopyHasOnlyVisibleItemsAndNoSecrets() {
        Campaign c = sample();
        Campaign pub = c.publicCopy();
        assertEquals(1, pub.getPlayers().size());
        assertEquals("", pub.getPlayers().get(0).gmNotes);
        assertEquals(1, pub.getNpcs().size(), "o vilão escondido não vai");
        assertEquals("Bento", pub.getNpcs().get(0).name);
        assertEquals("", pub.getNpcs().get(0).gmNotes, "segredo do PdM não vai");
        assertEquals(1, pub.getAdventures().size());
        assertEquals("", pub.getAdventures().get(0).gmNotes);
        assertEquals("", pub.getGmNotes());
        assertEquals("", pub.getCalendar().notes);
        assertEquals(2, c.getNpcs().size(), "o original continua inteiro");

        c.getCalendar().visible = false;
        assertTrue(c.publicCopy().getCalendar().months.isEmpty(), "calendário escondido não manda nem os meses");
    }

    @Test
    void calendarDatesSeasonsAndWeather() {
        Campaign.Calendar cal = new Campaign.Calendar();
        cal.day = 0;
        assertEquals("Domingo, 1 de Janeiro do ano 1", cal.dateText());
        cal.day = 360 + 31; // ano 2, 2 de fevereiro (meses de 30 dias)
        assertArrayEquals(new long[]{2, 1, 2, 32}, cal.date());
        assertEquals("Verão", cal.season().name);
        cal.day = 200;
        assertEquals("Inverno", cal.season().name);
        String w = cal.weather();
        assertEquals(w, cal.weather(), "mesmo dia, mesmo clima");
        assertTrue(w.startsWith("Inverno: "), w);
        assertTrue(w.contains("°C"));
        cal.moonCycle = 28;
        cal.day = 14;
        assertEquals("Cheia", cal.moonPhase());
    }

    @Test
    void xpLedgerLevelsAndFile() throws Exception {
        Campaign c = sample();
        c.giveXpToAll(300, "01/01/2026", "Sessão 1");
        c.giveXpToAll(700, "08/01/2026", "Sessão 2");
        c.giveGoldToAll(25.5, "08/01/2026", "Tesouro");
        Campaign.PlayerRecord ana = c.getPlayers().get(0);
        assertEquals(1000, ana.totalXp());
        assertEquals(3, Campaign.level5e(ana.totalXp()), "900 XP = nível 3");
        assertEquals(20, Campaign.level5e(1_000_000));
        assertEquals(25.5, ana.totalGold());
        assertEquals(1, c.nextAdventureNumber());

        c.save(dir);
        Campaign back = Campaign.load(dir);
        assertEquals(2, back.getPlayers().size());
        assertEquals(1000, back.getPlayers().get(0).totalXp());
        assertEquals("ideias secretas", back.getGmNotes());
        assertNotNull(Campaign.load(dir.resolve("nao-existe")), "sem arquivo: campanha vazia");
    }

    @Test
    void sanitizeClampsAbsurdValues() {
        Campaign c = new Campaign();
        c.getCalendar().day = -50;
        c.getCalendar().moonCycle = 0;
        c.getCalendar().months.get(0).days = 0;
        c.setGmNotes("x".repeat(Campaign.MAX_TEXT + 500));
        c.sanitize();
        assertEquals(0, c.getCalendar().day);
        assertEquals(2, c.getCalendar().moonCycle);
        assertEquals(1, c.getCalendar().months.get(0).days);
        assertEquals(Campaign.MAX_TEXT, c.getGmNotes().length());
    }
}

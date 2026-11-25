package com.vttcaseiro.service;

import com.vttcaseiro.app.DiceStats;
import com.vttcaseiro.app.LogEntry;
import com.vttcaseiro.model.Campaign;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Faccoes, linha do tempo (o que os jogadores veem), gerador de PdM e estatisticas dos dados. */
class CampaignExtrasTest {

    @Test
    void playersOnlySeeVisibleFactionsAndEventsWithoutSecrets() {
        Campaign c = new Campaign();
        Campaign.Faction guild = new Campaign.Faction();
        guild.name = "Guilda dos Ladrões";
        guild.reputation = 2;
        guild.gmNotes = "o líder é um doppelganger";
        guild.visible = true;
        Campaign.Faction cult = new Campaign.Faction();
        cult.name = "Culto";
        c.getFactions().add(guild);
        c.getFactions().add(cult);
        Campaign.TimelineEvent fall = new Campaign.TimelineEvent();
        fall.title = "A ponte caiu";
        fall.visible = true;
        fall.gmNotes = "foi sabotagem";
        Campaign.TimelineEvent hidden = new Campaign.TimelineEvent();
        hidden.title = "O rei morreu";
        c.getTimeline().add(fall);
        c.getTimeline().add(hidden);

        Campaign pub = c.publicCopy();
        assertEquals(1, pub.getFactions().size(), "faccao escondida fica de fora");
        assertEquals("", pub.getFactions().get(0).gmNotes, "segredo da faccao nao vai");
        assertEquals(1, pub.getTimeline().size());
        assertEquals("", pub.getTimeline().get(0).gmNotes);
        assertEquals("Aliados (+2)", Campaign.reputationText(2));
        assertEquals("Inimigos jurados (-3)", Campaign.reputationText(-9), "fora da escala: no limite");
    }

    @Test
    void npcGeneratorFillsEverythingAndKeepsTheSecretForTheMaster() {
        Campaign.Npc n = NpcGenerator.random(new Random(7));
        assertTrue(n.name.contains(" "), "nome e sobrenome");
        assertFalse(n.role.isBlank());
        assertTrue(n.description.contains("Aparência:") && n.description.contains("Quer:"));
        assertTrue(n.gmNotes.startsWith("Segredo:"), "o segredo fica nas anotacoes do mestre");
        assertFalse(n.visible, "nasce escondido");
        assertNotEquals(NpcGenerator.random(new Random(1)).name + NpcGenerator.random(new Random(2)).description,
                NpcGenerator.random(new Random(1)).name + NpcGenerator.random(new Random(1)).description);
    }

    @Test
    void diceStatsCountNaturalTwentiesAndOnes() {
        List<LogEntry> log = List.of(
                LogEntry.roll("Ana", "Ataque: 1d20(20)+5 = 25", 25, 20),
                LogEntry.roll("Ana", "Teste: 1d20(10)+2 = 12", 12, 10),
                LogEntry.roll("Beto", "Ataque: 1d20(1)+4 = 5", 5, 1),
                LogEntry.roll("Beto", "Dano: 2d6 = 7", 7, null),
                LogEntry.hiddenRoll("Mestre", "1d20(20)", 20, 20));
        List<DiceStats.Line> lines = DiceStats.of(log);
        assertEquals(2, lines.size(), "escondida e dano ficam de fora");
        DiceStats.Line ana = lines.stream().filter(l -> l.who().equals("Ana")).findFirst().orElseThrow();
        assertEquals(2, ana.rolls());
        assertEquals(1, ana.crits());
        assertEquals(15.0, ana.average(), 0.01);
        String text = DiceStats.summary(lines, "Dados da sessão");
        assertTrue(text.contains("Mais 20 naturais: Ana"));
        assertTrue(text.contains("Beto: 1 rolagem"));
    }
}

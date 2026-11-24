package com.vttcaseiro.service;

import com.vttcaseiro.app.LogEntry;
import com.vttcaseiro.app.SessionSummary;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.AudioCommand;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Monstros iguais agrupados na iniciativa, resumo da sessao pelo chat e musica de lista que toca uma vez. */
class GroupsSummaryMusicTest {

    private static TokenModel goblin(int n) {
        TokenModel t = new TokenModel("Goblin " + n, null);
        t.setMobSheetId("mob-goblin");
        t.setInitiativeBonus(2);
        return t;
    }

    @Test
    void sameMonstersShareOneTurn(@TempDir Path dir) throws Exception {
        SceneModel s = new SceneModel();
        TokenModel lyra = new TokenModel("Lyra", null);
        lyra.setCharacterSheetId("sheet-lyra");
        s.addToken(lyra);
        for (int i = 1; i <= 3; i++) s.addToken(goblin(i));
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(new SceneService(s), new CharacterService(new LibraryStorage(dir)), port);
        table.setGroupMonsters(true);
        table.rollInitiativeForAllAndStart();
        Integer first = s.getTokens().get(1).getInitiative();
        for (int i = 2; i <= 3; i++) assertEquals(first, s.getTokens().get(i).getInitiative(), "uma iniciativa pro grupo");

        List<List<TokenModel>> groups = EncounterService.groupedOrder(s);
        assertEquals(2, groups.size(), "Lyra e o grupo dos goblins");
        assertTrue(groups.stream().anyMatch(g -> g.size() == 3));

        // dois turnos fecham a rodada (Lyra + o grupo), nao quatro
        int round = s.getEncounter().getRound();
        table.nextTurn();
        table.nextTurn();
        assertEquals(round + 1, s.getEncounter().getRound());

        s.getEncounter().setGroupMonsters(false);
        assertEquals(4, EncounterService.groupedOrder(s).size(), "desligado: cada um na sua linha");
    }

    @Test
    void summaryCollectsTheSessionHighlights() {
        List<LogEntry> log = List.of(
                LogEntry.system("Cena aberta: Taverna"),
                LogEntry.system("Combate! Rodada 1. Ordem: Lyra (18) > Goblin (12)"),
                LogEntry.system("--- Rodada 2 ---"),
                LogEntry.roll("Ana", "Lyra - Ataque: Espada longa: 1d20(20)+5 = 25", 25, 20),
                LogEntry.roll("Beto", "Kael - Ataque: Arco: 1d20(1)+4 = 5", 5, 1),
                LogEntry.system("Dano aplicado: Goblin 7 → 0 PV."),
                LogEntry.hiddenRoll("Mestre", "Orc escondido: 1d20(20)+3", 23, 20),
                LogEntry.chat("Ana", "Eu vou na frente."),
                LogEntry.chat("Ana", "Cuidado!"));
        String s = SessionSummary.build(log, LocalDate.of(2026, 11, 24), true);
        assertTrue(s.startsWith("Sessão de 24/11/2026"));
        assertTrue(s.contains("Cenas: Taverna"));
        assertTrue(s.contains("Combates: 1 (2 rodadas"));
        assertTrue(s.contains("Ana: Lyra - Ataque") && s.contains("Falhas críticas"));
        assertTrue(s.contains("Dano aplicado: Goblin 7 → 0 PV."));
        assertFalse(s.contains("Orc escondido"), "rolagem escondida fica de fora");
        assertTrue(s.contains("Ana (2)"));
    }

    @Test
    void playlistTrackPlaysOnce() {
        assertTrue(AudioCommand.play(AudioCommand.MUSIC, "a", "Tema").loops());
        AudioCommand once = AudioCommand.playOnce(AudioCommand.MUSIC, "a", "Tema");
        assertFalse(once.loops(), "faixa de lista nao fica em loop");
        assertTrue(once.isOnce());
        assertEquals(AudioCommand.MUSIC, once.getChannel());
    }
}

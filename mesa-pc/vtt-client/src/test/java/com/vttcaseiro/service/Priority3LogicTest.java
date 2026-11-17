package com.vttcaseiro.service;

import com.vttcaseiro.app.LogEntry;
import com.vttcaseiro.library.ChatLogStore;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.Fog;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Priority3LogicTest {

    @Test
    void neblinaRevelaCobreEsobreviveAoJson() {
        Fog fog = new Fog();
        fog.setArea(3, 3, 1, 1, true); // cantos em qualquer ordem
        assertEquals(9, fog.revealedCount());
        fog.setArea(2, 2, 2, 2, false);
        assertFalse(fog.isRevealed(2, 2));
        Fog copy = com.vttcaseiro.net.NetCodec.deepCopy(fog, Fog.class);
        assertTrue(copy.isRevealed(1, 1));
        assertFalse(copy.isRevealed(2, 2));
        copy.coverAll();
        assertEquals(0, copy.revealedCount());
        copy.revealAll(4, 3);
        assertEquals(12, copy.revealedCount());
    }

    @Test
    void desfazerGuardaAntesEDepoisSemCompartilharObjeto() {
        UndoService undo = new UndoService();
        TokenModel t = new TokenModel("Orc", null);
        t.setCurrentHp(15);
        TokenModel before = UndoService.copy(t);
        t.setCurrentHp(7);
        undo.record("Dano", before, t);
        t.setCurrentHp(1); // mexer depois nao altera o que foi guardado
        var entry = undo.pop().orElseThrow();
        assertEquals(15, entry.before().getCurrentHp());
        assertEquals(7, entry.after().getCurrentHp());
        assertTrue(undo.pop().isEmpty());
        for (int i = 0; i < UndoService.MAX + 10; i++) undo.record("x", null, t);
        assertEquals(UndoService.MAX, undo.size(), "pilha tem limite");
    }

    @Test
    void historicoDoChatVoltaEIgnoraLinhaCortada(@TempDir Path dir) throws Exception {
        ChatLogStore store = new ChatLogStore(dir);
        store.append(LogEntry.chat("Ana", "ola"));
        store.append(LogEntry.roll("Ana", "Furtividade: 1d20[18] + 5 = 23", 23, 18));
        Files.writeString(dir.resolve("chat-log.jsonl"), "{\"time\":1,\"entry\":{\"ty", StandardOpenOption.APPEND);
        store.append(LogEntry.whisper("Beto", "mestre", "segredo"));
        List<LogEntry> back = new ChatLogStore(dir).loadLast(10);
        assertEquals(3, back.size(), "a linha cortada e ignorada");
        assertEquals(23, back.get(1).total());
        assertEquals(18, back.get(1).naturalD20());
        assertTrue(back.get(2).isPrivate());
        assertEquals(2, new ChatLogStore(dir).loadLast(2).size());
    }

    @Test
    void tokenOcultoSoOMestreEsconde_eDanoNelaNaoVazaNome(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TokenModel dragon = new TokenModel("Dragao", null);
        dragon.setCurrentHp(100);
        scenes.addToken(dragon);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, chars, port);

        port.gm = false;
        port.connected = true;
        table.setHidden(dragon, true);
        assertFalse(dragon.isHiddenFromPlayers(), "jogador nao esconde");

        port.gm = true;
        table.setHidden(dragon, true);
        assertTrue(dragon.isHiddenFromPlayers());
        port.announced.clear();
        table.damage(dragon, 10);
        assertTrue(port.announced.stream().noneMatch(m -> m.contains("Dragao")), "chat publico nao cita o oculto");
        assertTrue(port.announcedGm.stream().anyMatch(m -> m.contains("Dragao")));
    }

    @Test
    void neblinaPeloTableServiceSoMestreERevelarLiga(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, chars, port);
        table.editFog(0, 0, 1, 1, true);
        assertTrue(scenes.getScene().getFog().isEnabled(), "revelar liga a neblina");
        assertEquals(4, scenes.getScene().getFog().revealedCount());
        port.gm = false;
        port.connected = true;
        table.coverAllFog();
        assertEquals(4, scenes.getScene().getFog().revealedCount(), "jogador nao mexe");
    }
}

package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.Condition;
import com.vttcaseiro.model.Encounter;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras da mesa sem tela nem rede: TableService com uma Port falsa. */
class TableAndEncounterTest {

    static class FakePort implements TableService.Port {
        boolean connected;
        boolean gm = true;
        String me = "eu";
        final List<String> announced = new ArrayList<>();
        final List<String> announcedGm = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<TokenModel> sentTokens = new ArrayList<>();
        final List<CharacterSheet> sentSheets = new ArrayList<>();
        int encountersSent;
        final List<CharacterSheet> reloaded = new ArrayList<>();

        @Override public boolean isConnected() { return connected; }
        @Override public boolean isGm() { return gm; }
        @Override public String myPlayerId() { return me; }
        @Override public void sendUpdateToken(TokenModel t) { sentTokens.add(t); }
        @Override public void sendRemoveToken(String id) { }
        @Override public void sendCharacterSync(CharacterSheet s) { sentSheets.add(s); }
        @Override public void sendEncounter(Encounter e) { encountersSent++; }
        @Override public void announce(String text) { announced.add(text); }
        @Override public void error(String text) { errors.add(text); }
        @Override public void announceGm(String text) { announcedGm.add(text); }
        @Override public void sheetChangedOutside(CharacterSheet s) { reloaded.add(s); }
        final List<String> doorToggles = new ArrayList<>();
        int wallsSent;
        @Override public void sendDoorToggle(String id) { doorToggles.add(id); }
        @Override public void sendWalls(SceneModel s) { wallsSent++; }
        final List<com.vttcaseiro.model.GmRequest> gmRequests = new ArrayList<>();
        @Override public void requestGm(com.vttcaseiro.model.GmRequest r) { gmRequests.add(r); }
    }

    private static TokenModel token(String name, Integer init, int bonus) {
        TokenModel t = new TokenModel(name, null);
        t.setInitiative(init);
        t.setInitiativeBonus(bonus);
        return t;
    }

    @Test
    void ordemPorIniciativaComEmpatePeloBonusESemIniciativaNoFim() {
        SceneModel scene = new SceneModel();
        scene.addToken(token("Sem", null, 9));
        scene.addToken(token("A", 12, 1));
        scene.addToken(token("B", 15, 0));
        scene.addToken(token("C", 12, 3));
        List<String> names = EncounterService.order(scene).stream().map(TokenModel::getName).toList();
        assertEquals(List.of("B", "C", "A", "Sem"), names);
    }

    @Test
    void turnosViramRodadaEEfeitoComDuracaoVence() {
        SceneModel scene = new SceneModel();
        TokenModel a = token("A", 20, 0);
        TokenModel b = token("B", 10, 0);
        scene.addToken(a);
        scene.addToken(b);
        EncounterService.start(scene);
        EncounterService.addTimedEffect(scene, b, "Bencao", 1);
        assertEquals(a.getId(), scene.getEncounter().getCurrentTokenId());
        var r1 = EncounterService.next(scene);
        assertEquals(b, r1.current());
        assertFalse(r1.newRound());
        var r2 = EncounterService.next(scene);
        assertEquals(a, r2.current());
        assertTrue(r2.newRound());
        assertEquals(2, r2.round());
        assertEquals(1, r2.expired().size());
        assertTrue(scene.getEncounter().getTimedEffects().isEmpty());
    }

    @Test
    void danoNoTokenLigadoPassaPelaFichaEVoltaProToken(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        CharacterSheet sheet = chars.create("Elora");
        sheet.setHpMax(20);
        sheet.setHpCurrent(20);
        sheet.setConcentratingOn("Bencao");
        SceneService scenes = new SceneService(new SceneModel());
        TokenModel t = TokenLink.tokenFromSheet(sheet);
        scenes.addToken(t);
        FakePort port = new FakePort();
        TableService table = new TableService(scenes, chars, port);

        table.damage(t, 8);
        assertEquals(12, sheet.getHpCurrent());
        assertEquals(12, t.getCurrentHp());
        assertTrue(port.announced.get(0).contains("CD 10"), port.announced.toString());
        assertEquals(List.of(sheet), port.reloaded);

        table.setConditions(t, List.of("PRONE"));
        assertTrue(sheet.hasCondition(Condition.PRONE));
        assertTrue(t.hasCondition(Condition.PRONE));
    }

    @Test
    void permissoesPorDonoExplicito(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        CharacterSheet sheet = chars.create("Elora");
        sheet.setOwnerPlayerId("ana");
        SceneService scenes = new SceneService(new SceneModel());
        TokenModel linked = TokenLink.tokenFromSheet(sheet);
        TokenModel goblin = new TokenModel("Goblin", null);
        scenes.addToken(linked);
        scenes.addToken(goblin);
        FakePort port = new FakePort();
        port.connected = true;
        port.gm = false;
        port.me = "ana";
        TableService table = new TableService(scenes, chars, port);

        assertTrue(table.canControl(linked), "dona da ficha controla o token dela");
        assertFalse(table.canControl(goblin));
        assertTrue(table.canEditSheet(sheet));
        port.me = "beto";
        assertFalse(table.canControl(linked));
        assertFalse(table.canEditSheet(sheet));

        table.giveControl(goblin, "beto", "Beto");
        assertFalse(port.errors.isEmpty(), "jogador nao passa controle");
        port.gm = true;
        table.giveControl(linked, "beto", "Beto");
        assertEquals("beto", linked.getOwnerId());
        assertEquals("beto", sheet.getOwnerPlayerId());
        assertEquals("Beto", sheet.getPlayerName());
    }

    @Test
    void proximoTurnoTiraEfeitoVencidoDoTokenEsoMestreAvanca(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TokenModel a = token("A", 20, 0);
        TokenModel b = token("B", 10, 0);
        scenes.addToken(a);
        scenes.addToken(b);
        FakePort port = new FakePort();
        TableService table = new TableService(scenes, chars, port);

        table.startEncounter();
        table.addTimedEffect(b, "Furia", 1);
        assertTrue(b.getEffects().contains("Furia"));
        table.nextTurn(); // B
        table.nextTurn(); // A, rodada 2 -> Furia vence
        assertFalse(b.getEffects().contains("Furia"));
        assertTrue(port.announced.stream().anyMatch(m -> m.contains("Furia terminou")), port.announced.toString());
        assertTrue(port.announced.contains("--- Rodada 2 ---"));

        port.gm = false;
        port.connected = true;
        table.nextTurn();
        assertTrue(port.errors.stream().anyMatch(e -> e.contains("mestre")));
    }

    @Test
    void comandosDoChat() {
        assertEquals(ChatCommands.Type.ROLL, ChatCommands.parse("/r 2d6+3").type());
        assertEquals("1d20+5", ChatCommands.parse("/gr 1d20+5").text());
        var w = ChatCommands.parse("/w \"Ana Clara\" oi tudo bem");
        assertEquals(ChatCommands.Type.WHISPER, w.type());
        assertEquals("Ana Clara", w.target());
        assertEquals("oi tudo bem", w.text());
        assertEquals(ChatCommands.Type.ERROR, ChatCommands.parse("/w Ana").type());
        assertEquals(ChatCommands.Type.ERROR, ChatCommands.parse("/xyz").type());
        assertEquals(ChatCommands.Type.TEXT, ChatCommands.parse("ola mesa").type());
        assertEquals(ChatCommands.Type.EMPTY, ChatCommands.parse("   ").type());
    }
}

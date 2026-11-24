package com.vttcaseiro.service;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.MapZone;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Zonas do mapa: entrar dispara, terreno dificil na regua, dano no comeco do turno, chegada pelo portal. */
class ZonesTest {

    private static SceneModel scene() {
        SceneModel s = new SceneModel();
        s.getGrid().setCellSize(50);
        return s;
    }

    private static TokenModel token(String name, double x, double y) {
        TokenModel t = new TokenModel(name, null);
        t.setX(x);
        t.setY(y);
        return t;
    }

    @Test
    void enteringTriggersOnlyOnTheWayIn() {
        SceneModel s = scene();
        MapZone trap = new MapZone(200, 0, 100, 100, MapZone.TRAP);
        s.getZones().add(trap);
        TokenModel t = token("Lyra", 225, 25); // centro em (250, 50): dentro
        assertEquals(List.of(trap), ZoneRules.entered(s, t, 0, 0), "veio de fora: dispara");
        assertTrue(ZoneRules.entered(s, t, 210, 10).isEmpty(), "ja estava dentro: nao");
        trap.setTriggered(true);
        assertTrue(ZoneRules.entered(s, t, 0, 0).isEmpty(), "uma vez so: desarmada");
        trap.setOnce(false);
        assertEquals(1, ZoneRules.entered(s, t, 0, 0).size(), "rearmavel dispara sempre");

        MapZone terrain = new MapZone(0, 200, 100, 100, MapZone.TERRAIN);
        s.getZones().add(terrain);
        assertTrue(terrain.isVisibleToPlayers(), "terreno nasce visivel");
        assertFalse(terrain.triggersOnEnter(), "terreno nao dispara, so mexe na regua");
        assertFalse(trap.isVisibleToPlayers(), "armadilha nasce secreta");
        assertEquals(List.of(terrain), s.visibleZones(false));
    }

    @Test
    void difficultTerrainCountsDouble() {
        SceneModel s = scene();
        s.getZones().add(new MapZone(100, 0, 100, 50, MapZone.TERRAIN)); // casas 3 e 4 (x de 100 a 200)
        // de x=25 a x=325 (6 casas) passando pelas duas casas de terreno
        assertEquals(2, ZoneRules.terrainExtra(s, 25, 25, 325, 25, 6));
        assertEquals(0, ZoneRules.terrainExtra(s, 25, 125, 325, 125, 6), "linha de baixo: fora do terreno");
    }

    @Test
    void damageZoneHurtsAtTurnStart(@TempDir Path dir) throws Exception {
        SceneModel s = scene();
        MapZone lava = new MapZone(0, 0, 100, 100, MapZone.DAMAGE);
        lava.setName("Lava");
        lava.setDamage("5");
        lava.setDamageType("fogo");
        s.getZones().add(lava);
        TokenModel a = token("Lyra", 10, 10);
        a.setMaxHp(20);
        a.setCurrentHp(20);
        a.setInitiative(15);
        TokenModel b = token("Goblin", 500, 500);
        b.setInitiative(10);
        s.addToken(a);
        s.addToken(b);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(new SceneService(s), new CharacterService(new LibraryStorage(dir)), port);
        table.startEncounter();
        table.nextTurn(); // Goblin
        table.nextTurn(); // Lyra de novo: comeca o turno na lava
        assertEquals(15, a.getCurrentHp(), "erros: " + port.errors + " anuncios: " + port.announced);
        assertTrue(port.announced.stream().anyMatch(x -> x.contains("Lyra começa o turno em Lava") && x.contains("fogo")));
    }

    @Test
    void trapSaveAppliesDamage(@TempDir Path dir) throws Exception {
        SceneModel s = scene();
        MapZone pit = new MapZone(0, 0, 100, 100, MapZone.TRAP);
        pit.setName("Fosso");
        pit.setDc(40); // ninguem passa
        pit.setDamage("6");
        TokenModel a = token("Lyra", 10, 10);
        a.setMaxHp(20);
        a.setCurrentHp(20);
        s.addToken(a);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(new SceneService(s), new CharacterService(new LibraryStorage(dir)), port);
        var r = table.trapSave(pit, a);
        assertFalse(r.get(0).success());
        assertEquals(14, a.getCurrentHp());
    }

    @Test
    void portalArrivesNextToTheWayBack() {
        SceneModel cellar = scene();
        MapZone up = new MapZone(400, 400, 100, 50, MapZone.PORTAL);
        up.setTargetScene("Taverna");
        MapZone other = new MapZone(0, 0, 50, 50, MapZone.PORTAL);
        other.setTargetScene("Esgoto");
        cellar.getZones().add(other);
        cellar.getZones().add(up);
        TokenModel t = token("Lyra", 0, 0);
        double[] at = ZoneRules.arrivalPoint(cellar, "Taverna", t);
        assertEquals(425, at[0], 0.01, "centralizado no portal de volta");
        assertTrue(at[1] > 450, "logo fora dele (nao dispara de volta)");
        assertNull(ZoneRules.arrivalPoint(scene(), "Taverna", t), "sem portal: fica onde estava");
    }
}

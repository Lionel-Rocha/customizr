package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.Resource;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Efeitos mudando CA e resistencias na mesa; descanso curto do grupo recupera recursos. */
class EffectsOnTableTest {

    @Test
    void efeitosNaCaENasResistencias(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, chars, port);

        MobSheet mob = new MobSheet("Cultista");
        mob.setArmorClass(12);
        table.setMobLookup(id -> id.equals(mob.getId()) ? mob : null);
        TokenModel cultist = TokenLink.tokenFromMob(mob, "Cultista");
        cultist.setCurrentHp(30);
        cultist.setMaxHp(30);
        scenes.addToken(cultist);
        cultist.setEffects(List.of("Escudo da Fe"));
        assertEquals(14, table.armorClassOf(cultist).orElseThrow(), "monstro: CA do bloco + efeito do token");

        CharacterSheet brok = chars.create("Brok");
        brok.setHpMax(40);
        brok.setHpCurrent(40);
        brok.setEffects(List.of("Furia"));
        TokenModel brokTok = TokenLink.tokenFromSheet(brok);
        scenes.addToken(brokTok);
        table.damageTargets(List.of(brokTok), 11, "slashing", "Machado");
        assertEquals(35, brok.getHpCurrent(), "furia: resistente a cortante");
        table.damageTargets(List.of(brokTok), 10, "fire", null);
        assertEquals(25, brok.getHpCurrent(), "fogo nao");

        // 19 contra CA 14 (12 + Escudo da Fe) acerta; 13 erra.
        assertTrue(table.resolveAttack("k", "Brok", 14, 10, List.of(cultist)).get(0).hit());
        assertFalse(table.resolveAttack("k", "Brok", 13, 10, List.of(cultist)).get(0).hit());
    }

    @Test
    void descansoCurtoDoGrupoRecuperaRecursosDeCurto(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, chars, port);
        CharacterSheet s = chars.create("Kael");
        s.setHpMax(20);
        s.setHpCurrent(20);
        s.getResources().add(new Resource("Segundo Folego", 1, Resource.SHORT));
        s.getResources().add(new Resource("Surto de Acao", 1, Resource.LONG));
        s.getResources().forEach(Resource::use);
        scenes.addToken(TokenLink.tokenFromSheet(s));

        table.groupShortRest();
        assertEquals(1, s.findResource("Segundo Folego").orElseThrow().getCurrent());
        assertEquals(0, s.findResource("Surto de Acao").orElseThrow().getCurrent());
        assertTrue(port.announced.get(0).contains("Kael recupera Segundo Folego"), port.announced.toString());
        table.groupLongRest();
        assertEquals(1, s.findResource("Surto de Acao").orElseThrow().getCurrent());
    }
}

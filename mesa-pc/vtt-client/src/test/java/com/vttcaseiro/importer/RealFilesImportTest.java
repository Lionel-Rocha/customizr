package com.vttcaseiro.importer;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Importadores com arquivos REAIS exportados das ferramentas (ver src/test/resources/importer/LEIA-ME.txt). */
class RealFilesImportTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = RealFilesImportTest.class.getResourceAsStream("/importer/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void heroiDeExemploDoFoundry() throws Exception {
        SheetImport.Result r = SheetImport.fromJson(resource("foundry-randal-human-fighter.json"));
        CharacterSheet s = r.sheet();
        assertEquals("Randal (Human Fighter)", s.getName());
        assertEquals("Fighter", s.getClassName());
        assertEquals(16, s.getStrength());
        assertEquals(12, s.getHpMax(), "d10 + CON 2 no nivel 1 (o Foundry nao grava, calcula)");
        assertEquals(18, s.getArmorClass(), "cota de malha 16 + escudo 2");
        assertTrue(s.isSavingThrowProficient(Ability.STRENGTH));
        assertTrue(s.isSkillProficient(Skill.ATHLETICS));
        assertEquals("+5", s.getAttacks().get(0).getAttackBonusOrDc(), "espada longa: FOR +3 + prof +2");
        assertEquals("1d8+3 slashing", s.getAttacks().get(0).getDamageAndType());
        assertEquals("+4", s.getAttacks().get(1).getAttackBonusOrDc(), "arco longo usa DES");
        assertTrue(s.getAttacks().get(0).getNotes().contains("versátil"));
        assertFalse(s.getInventory().getItems().isEmpty());
    }

    @Test
    void masmorraDeVerdadeDoOnePageDungeon() throws Exception {
        WatabouDungeon.Result d = WatabouDungeon.parse(resource("watabou-thunderfire-labyrinth.json"));
        assertEquals("Thunderfire Labyrinth", d.title());
        assertEquals(8, d.pins().size(), "uma nota = um pin oculto");
        assertTrue(d.pins().stream().allMatch(MapPin::isHidden));
        long doors = d.walls().stream().filter(Wall::isDoor).count();
        assertEquals(d.doors(), doors);
        assertTrue(doors >= 15 && doors <= 25, "portas de verdade (1, 4, 5, 6), nao as passagens: " + doors);
        assertTrue(d.walls().size() > 100);
        assertTrue(d.png().length < 4 * 1024 * 1024, "cabe no limite de imagem da mesa");
        assertTrue(d.journalText().startsWith("The labyrinth is situated"));
    }

    @Test
    void mundoDeVerdadeDoAzgaar() throws Exception {
        AzgaarWorld.Result w = AzgaarWorld.parse(resource("azgaar-solia-recorte.json"));
        assertEquals("Solia", w.mapName());
        assertEquals(1024, w.mapWidth());
        List<MapPin> pins = w.pins(1024, 768, 60);
        assertEquals(41, pins.size());
        assertEquals("capital", pins.get(0).getKind());
        for (MapPin p : pins) {
            assertTrue(p.getX() > 0 && p.getX() < 1024 && p.getY() > 0 && p.getY() < 768, p.getTitle());
            assertNotNull(p.getGeneratorUrl(), p.getTitle());
        }
        assertTrue(w.journalText().contains("Exarchate of Senostos (capital: Nera)"));
    }
}

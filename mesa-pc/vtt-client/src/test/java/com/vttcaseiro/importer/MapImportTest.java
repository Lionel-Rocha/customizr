package com.vttcaseiro.importer;

import com.vttcaseiro.model.Drawing;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapImportTest {

    /** Duas salas 3x3 ligadas por um corredor de 2 celulas com uma porta. */
    private static final String DUNGEON = """
            {"version": "1.2.0", "title": "Cripta do Rei Sapo", "story": "Um rei amaldicoado dorme aqui.",
             "rects": [{"x": 0, "y": 0, "w": 3, "h": 3}, {"x": 3, "y": 1, "w": 2, "h": 1},
                       {"x": 5, "y": 0, "w": 3, "h": 3}, {"x": -4, "y": -4, "w": 3, "h": 3, "rotunda": true}],
             "doors": [{"x": 3, "y": 1, "dir": {"x": 1, "y": 0}, "type": 1},
                       {"x": 4, "y": 1, "dir": {"x": 1, "y": 0}, "type": 2}],
             "notes": [{"text": "Um sapo de ouro no altar.", "ref": "1", "pos": {"x": 6.5, "y": 1.5}}],
             "columns": [], "water": []}""";

    @Test
    void masmorraDoWatabouViraCenaComParedesEPortas() throws Exception {
        WatabouDungeon.Result r = WatabouDungeon.parse(DUNGEON);
        assertEquals("Cripta do Rei Sapo", r.title());
        assertEquals(1, r.doors(), "abertura (tipo 2) nao vira porta");
        assertEquals(3, r.rooms());
        List<Wall> doors = r.walls().stream().filter(Wall::isDoor).toList();
        assertEquals(1, doors.size());
        Wall door = doors.get(0);
        assertEquals(door.getX1(), door.getX2(), 0.001, "passagem horizontal -> porta em pe");
        assertEquals(r.cellPx(), Math.abs(door.getY2() - door.getY1()), 0.001);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.png()));
        assertNotNull(img);
        // 3 celulas de largura... de -4 ate 7 = 12 celulas + 2 de margem.
        assertEquals(Math.round(14 * r.cellPx()), img.getWidth());
        assertTrue(r.journalText().contains("Um rei amaldicoado"));
        assertTrue(r.journalText().contains("1. Um sapo de ouro"));

        // Paredes juntadas em linhas retas: o topo da sala 1 (3 celulas) e UMA parede.
        double c = r.cellPx();
        double top = 4 * c + c; // y=0 com deslocamento (1 - minY) = 5 celulas
        long topWalls = r.walls().stream().filter(w -> !w.isDoor() && w.getY1() == top && w.getY2() == top
                && w.getX1() == 5 * c && w.getX2() == 8 * c).count();
        assertEquals(1, topWalls, "o topo da primeira sala virou uma parede so");
        // A nota virou um pin OCULTO em cima do numero.
        assertEquals(1, r.pins().size());
        assertTrue(r.pins().get(0).isHidden());
        assertEquals((6.5 + 5) * c, r.pins().get(0).getX(), 0.001);
        assertEquals("Um sapo de ouro no altar.", r.pins().get(0).getText());
        // Toda parede fica nas bordas da grade (sem diagonais).
        for (Wall w : r.walls()) assertTrue(w.getX1() == w.getX2() || w.getY1() == w.getY2());
    }

    @Test
    void jsonQueNaoEMasmorraDaErroExplicando() {
        IOException ex = assertThrows(IOException.class, () -> WatabouDungeon.parse("{\"type\":\"FeatureCollection\"}"));
        assertTrue(ex.getMessage().contains("PNG"));
    }

    @Test
    void mundoDoAzgaar() throws Exception {
        String json = """
                {"info": {"mapName": "Eldoria", "width": 1000, "height": 500, "seed": "1"},
                 "settings": {"populationRate": 1000, "urbanization": 1},
                 "pack": {
                   "states": [{"i": 0, "name": "Neutrals"}, {"i": 1, "name": "Valor", "fullName": "Reino de Valor", "capital": 1}],
                   "burgs": [{}, {"i": 1, "name": "Alta Torre", "x": 500, "y": 250, "capital": 1, "state": 1, "population": 12.5},
                             {"i": 2, "name": "Vila Baixa", "x": 100, "y": 50, "capital": 0, "state": 1, "population": 0.8},
                             {"i": 3, "name": "Ruina", "x": 1, "y": 1, "removed": true}]
                 }}""";
        AzgaarWorld.Result w = AzgaarWorld.parse(json);
        assertEquals("Eldoria", w.mapName());
        assertEquals(2, w.burgs().size(), "o primeiro vazio e o removido ficam de fora");
        assertTrue(w.journalText().contains("Reino de Valor (capital: Alta Torre)"));
        assertTrue(w.journalText().contains("Alta Torre, Valor (~12.500 hab.)"));
        List<Drawing> labels = w.labels(2000, 1000, true, 10);
        assertEquals(1, labels.size());
        assertEquals(1000, labels.get(0).getPoints().get(0), 0.001, "x escalado pra imagem 2x maior");
        assertEquals(500, labels.get(0).getPoints().get(1), 0.001);
        var pins = w.pins(2000, 1000, 10);
        assertEquals(2, pins.size());
        assertEquals("capital", pins.get(0).getKind(), "capital primeiro");
        assertEquals(1000, pins.get(0).getX(), 0.001);
        assertTrue(pins.get(0).getText().contains("Capital de Valor"));
        assertTrue(pins.get(0).getGeneratorUrl().startsWith("https://watabou.github.io/city-generator/"));
        assertTrue(pins.get(0).getGeneratorUrl().contains("name=Alta%20Torre"));
        assertEquals("vila", pins.get(1).getKind(), "800 habitantes = vila");
        assertThrows(IOException.class, () -> AzgaarWorld.parse("{\"info\": {}}"));
    }

    @Test
    void linksDosGeradores() {
        assertNull(GeneratorLinks.forPlace("Forja", "loja", 0), "loja nao tem gerador");
        String a = GeneratorLinks.forPlace("Pedra Alta", "cidade", 5000);
        assertEquals(a, GeneratorLinks.forPlace("Pedra Alta", "cidade", 5000), "mesmo nome, mesma cidade");
        assertTrue(GeneratorLinks.forPlace("X", "masmorra", 0).startsWith("https://watabou.github.io/one-page-dungeon/?seed="));
        assertTrue(GeneratorLinks.citySize(1_000_000) <= 40 && GeneratorLinks.citySize(10) >= 6);
    }

    @Test
    void imagemGrandeEReduzidaPraCaber() throws Exception {
        BufferedImage big = new BufferedImage(9000, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(big, "png", out);
        MapImageFit.Fitted f = MapImageFit.fit(out.toByteArray(), "mundo.png");
        assertTrue(f.changed());
        assertEquals(MapImageFit.MAX_SIDE, f.width());
        assertTrue(f.bytes().length <= MapImageFit.MAX_BYTES);

        BufferedImage small = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        out.reset();
        ImageIO.write(small, "png", out);
        MapImageFit.Fitted same = MapImageFit.fit(out.toByteArray(), "x.png");
        assertFalse(same.changed());
        assertThrows(IOException.class, () -> MapImageFit.fit("<svg/>".getBytes(), "mapa.svg"));
    }
}

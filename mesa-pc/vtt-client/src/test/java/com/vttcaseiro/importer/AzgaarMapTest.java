package com.vttcaseiro.importer;

import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.MapPin;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Mundo salvo do Azgaar (.map): formato novo (JSON nas configuracoes, gzip) e antigo (notas separadas). */
class AzgaarMapTest {

    /** Monta um .map com as secoes nas posicoes certas (o resto vazio). */
    static String mapFile(String params, String settings, String notes, String cultures, String states, String burgs,
                          String religions, String markers) {
        List<String> d = new ArrayList<>();
        for (int i = 0; i < 56; i++) d.add("");
        d.set(0, params);
        d.set(1, settings);
        d.set(4, notes);
        d.set(5, "<svg id=\"map\" width=\"1000\" height=\"500\"><rect width=\"1000\" height=\"500\"/></svg>");
        d.set(13, cultures);
        d.set(14, states);
        d.set(15, burgs);
        d.set(29, religions);
        d.set(35, markers);
        return String.join("\r\n", d);
    }

    static final String STATES = "[{\"i\":0,\"name\":\"Neutrals\"},"
            + "{\"i\":1,\"name\":\"Ardor\",\"fullName\":\"Reino de Ardor\",\"form\":\"Monarquia\",\"capital\":1,"
            + "\"diplomacy\":[\"x\",\"x\",\"Ally\",\"Enemy\"],\"note\":\"<p>Rei <b>doente</b></p>\"},"
            + "{\"i\":2,\"name\":\"Brisa\",\"capital\":2,\"diplomacy\":[\"x\",\"Ally\",\"x\",\"Suspicion\"]},"
            + "{\"i\":3,\"name\":\"Cinza\",\"capital\":0,\"diplomacy\":[\"x\",\"Enemy\",\"Suspicion\",\"x\"]},"
            + "{\"i\":4,\"name\":\"Morto\",\"removed\":true}]";
    static final String BURGS = "[0,{\"i\":1,\"name\":\"Fortaleza\",\"x\":100,\"y\":100,\"capital\":1,\"state\":1,\"population\":12.5},"
            + "{\"i\":2,\"name\":\"Porto Brisa\",\"x\":800,\"y\":300,\"capital\":1,\"state\":2,\"population\":3},"
            + "{\"i\":3,\"name\":\"Vila Nova\",\"x\":120,\"y\":130,\"state\":1,\"population\":0.4,\"note\":\"Moinho assombrado\"}]";

    @Test
    void readsTheNewFormatEvenGzipped() throws Exception {
        String settings = "{\"lore\":{\"name\":\"Terras Altas\",\"description\":\"<p>Um mundo frio</p>\",\"calendar\":{\"year\":1312,\"era\":\"Era do Gelo\"}},"
                + "\"units\":{\"distance\":{\"unit\":\"km\",\"scale\":2},\"population\":{\"scale\":1000,\"urbanization\":{\"rate\":1}}}}";
        String text = mapFile("1.158.0|File can be loaded in azgaar.github.io/Fantasy-Map-Generator|2026-11-26|123|1000|500|1",
                settings, "", "[{\"i\":0,\"name\":\"Wildlands\"},{\"i\":1,\"name\":\"Nórdicos\",\"type\":\"Highland\"}]",
                STATES, BURGS, "[{\"i\":0,\"name\":\"No religion\"},{\"i\":1,\"name\":\"Fé do Sol\",\"form\":\"Church\",\"note\":\"<i>Sol eterno</i>\"}]",
                "[{\"i\":1,\"type\":\"dungeons\",\"icon\":\"x\",\"x\":110,\"y\":120,\"cell\":5,\"name\":\"Cripta do Rei\",\"note\":\"<p>3 andares</p>\"},"
                        + "{\"i\":2,\"type\":\"inns\",\"icon\":\"y\",\"x\":900,\"y\":400,\"cell\":9,\"name\":\"Taverna do Fim\"}]");
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(AzgaarMap.looksLike(gz.toByteArray()));
        AzgaarMap.World w = AzgaarMap.read(gz.toByteArray());
        assertEquals("Terras Altas", w.mapName());
        assertEquals(1000, w.width());
        assertEquals(3, w.states().size(), "0 (sem reino) e o apagado ficam de fora");
        assertEquals(3, w.burgs().size());
        assertEquals(12500, w.burgs().get(0).population(), 0.1, "populacao x escala");
        assertEquals("Moinho assombrado", w.burgs().get(2).note());
        assertEquals("Rei doente", w.states().get(0).note(), "HTML da nota vira texto");
        assertEquals(2 * 3280.84, w.feetPerPixel(), 0.01);
        assertNotNull(w.svg());

        List<Campaign.Faction> f = w.factions();
        assertEquals("Reino de Ardor", f.get(0).name);
        assertTrue(f.get(0).description.contains("Monarquia") && f.get(0).description.contains("Capital: Fortaleza"));
        assertEquals("Brisa", f.get(0).allies);
        assertEquals("Cinza (guerra)", f.get(0).enemies);
        assertEquals("Cinza (desconfiança)", f.get(1).enemies);
        assertFalse(f.get(0).visible, "nasce escondida");

        List<MapPin> pins = w.pins(500, 250, 60, true);
        assertEquals(5, pins.size(), "3 povoados + 2 marcadores");
        MapPin crypt = pins.stream().filter(p -> p.getTitle().equals("Cripta do Rei")).findFirst().orElseThrow();
        assertEquals("masmorra", crypt.getKind());
        assertTrue(crypt.isHidden(), "marcador nasce escondido");
        assertEquals(55, crypt.getX(), 0.01, "escala pra imagem");
        assertEquals("taverna", pins.stream().filter(p -> p.getTitle().equals("Taverna do Fim")).findFirst().orElseThrow().getKind());

        String journal = w.journalText();
        assertTrue(journal.contains("Um mundo frio") && journal.contains("Ano 1312 (Era do Gelo)"));
        assertTrue(journal.contains("CULTURAS") && journal.contains("Nórdicos (Highland)"));
        assertTrue(journal.contains("Fé do Sol (Church): Sol eterno"));
    }

    @Test
    void readsTheOldFormatWithSeparateNotes() throws Exception {
        String settings = "mi|1.5|square|ft|2|°F|||||||2000|1|50|30|||40|{}|Velho Mundo|0|default|1|10|0|1";
        String notes = "[{\"id\":\"burg3\",\"name\":\"Vila Nova\",\"legend\":\"Festa da colheita\"},"
                + "{\"id\":\"marker1\",\"name\":\"Ruínas Antigas\",\"legend\":\"<p>Cuidado</p>\"}]";
        String text = mapFile("1.99|File can be loaded in azgaar.github.io/Fantasy-Map-Generator|2024-1-1|9|1000|500|2",
                settings, notes, "[]", STATES, BURGS.replace(",\"note\":\"Moinho assombrado\"", ""), "",
                "[{\"i\":1,\"type\":\"ruins\",\"icon\":\"r\",\"x\":500,\"y\":250,\"cell\":3}]");
        AzgaarMap.World w = AzgaarMap.read(text.getBytes(StandardCharsets.UTF_8));
        assertEquals("Velho Mundo", w.mapName());
        assertEquals("mi", w.distanceUnit());
        assertEquals(1.5 * 5280, w.feetPerPixel(), 0.01);
        assertEquals(25000, w.burgs().get(0).population(), 0.1, "escala de populacao das antigas");
        assertEquals("Festa da colheita", w.burgs().get(2).note());
        assertEquals("Ruínas Antigas", w.markers().get(0).name(), "nome do marcador vem da nota antiga");
        assertEquals("Cuidado", w.markers().get(0).note());
    }

    @Test
    void rejectsSomethingElse() {
        assertThrows(java.io.IOException.class, () -> AzgaarMap.read("{\"pack\":{}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void regionStaysInsideTheMapAndComesOutBig() throws Exception {
        double[] r = AzgaarRegion.square(10, 10, 80, 1000, 500);
        assertEquals(0, r[0], 0.001, "perto da borda escorrega pra dentro");
        assertEquals(160, r[2], 0.001);
        BufferedImage world = new BufferedImage(500, 250, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(world, "png", png);
        AzgaarRegion.Cut cut = AzgaarRegion.crop(png.toByteArray(), 1000, 500, r);
        assertEquals(AzgaarRegion.OUT_SIDE, cut.outW());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(cut.png()));
        assertEquals(AzgaarRegion.OUT_SIDE, out.getWidth());

        AzgaarMap.World w = AzgaarMap.read(mapFile("1.158.0|x|d|1|1000|500|1", "{}", "", "[]", STATES, BURGS, "", "[]")
                .getBytes(StandardCharsets.UTF_8));
        List<MapPin> pins = w.pinsIn(r[0], r[1], r[2], r[3], 2048, 2048, 80, true);
        assertEquals(List.of("Fortaleza", "Vila Nova"), pins.stream().map(MapPin::getTitle).toList(), "so o que esta no recorte");
        assertEquals(1280, pins.get(0).getX(), 0.01);

        String svg = AzgaarMap.svgRegion("<svg id=\"map\" width=\"1000\" height=\"500\" viewBox=\"0 0 1000 500\"><g/></svg>",
                0, 0, 100, 100, 2048, 2048);
        assertTrue(svg.startsWith("<svg id=\"map\" width=\"2048\" height=\"2048\" viewBox=\"0.00 0.00 100.00 100.00\">"), svg);
    }
}

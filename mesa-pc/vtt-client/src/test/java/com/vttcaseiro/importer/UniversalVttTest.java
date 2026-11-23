package com.vttcaseiro.importer;

import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Mapa do Dungeondraft (Universal VTT): imagem, grade, paredes, portas e luzes viram cena. */
class UniversalVttTest {

    private static String png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String file(int cells, int ppg, String image) {
        return """
                {"format": 0.3,
                 "resolution": {"map_origin": {"x": 0, "y": 0}, "map_size": {"x": %d, "y": %d}, "pixels_per_grid": %d},
                 "line_of_sight": [[{"x": 0, "y": 0}, {"x": 4, "y": 0}, {"x": 4, "y": 3}]],
                 "objects_line_of_sight": [[{"x": 1, "y": 1}, {"x": 2, "y": 1}]],
                 "portals": [{"position": {"x": 2, "y": 3}, "bounds": [{"x": 1.5, "y": 3}, {"x": 2.5, "y": 3}],
                              "rotation": 0, "closed": true, "freestanding": false},
                             {"bounds": [{"x": 0, "y": 2}, {"x": 0, "y": 3}], "closed": false}],
                 "lights": [{"position": {"x": 2, "y": 2}, "range": 6, "intensity": 1, "color": "ffeccd8b", "shadows": true}],
                 "image": "%s"}
                """.formatted(cells, cells, ppg, image);
    }

    @Test
    void readsWallsDoorsAndLightsInPixels() throws Exception {
        UniversalVtt.Result r = UniversalVtt.parse(file(8, 100, png(800, 800)));
        assertEquals(100, r.cellPx(), 0.001);
        assertEquals(8, r.widthCells());
        assertEquals(2, r.doors());
        assertEquals(5, r.walls().size(), "2 trechos + 1 do objeto + 2 portas");
        Wall first = r.walls().get(0);
        assertEquals(400, first.getX2(), 0.001, "4 casas = 400 px");
        Wall door = r.walls().get(3);
        assertTrue(door.isDoor());
        assertEquals(150, door.getX1(), 0.001);
        assertFalse(door.isOpen(), "closed: true");
        assertTrue(r.walls().get(4).isOpen(), "closed: false = aberta");
        assertEquals(1, r.lights().size());
        assertEquals(200, r.lights().get(0).getX(), 0.001);
        assertEquals(30, r.lights().get(0).getDimFeet(), "6 casas = 30 pés");
    }

    @Test
    void bigImageIsShrunkAndCoordinatesFollow() throws Exception {
        // 100 casas de 100 px = 10000 px: passa do lado maximo (8192), a imagem diminui e as paredes acompanham
        UniversalVtt.Result r = UniversalVtt.parse(file(100, 100, png(10000, 10000)));
        assertTrue(r.cellPx() < 100, "casa menor junto com a imagem: " + r.cellPx());
        assertEquals(4 * r.cellPx(), r.walls().get(0).getX2(), 0.5);
    }

    @Test
    void clearErrorsForBadFiles() {
        IOException webp = assertThrows(IOException.class, () -> UniversalVtt.parse(file(4, 50,
                Base64.getEncoder().encodeToString("RIFF0000WEBPVP8 xxxxxxxx".getBytes()))));
        assertTrue(webp.getMessage().contains("PNG"));
        assertThrows(IOException.class, () -> UniversalVtt.parse("{}"));
        assertThrows(IOException.class, () -> UniversalVtt.parse("isso nao e json"));
        assertThrows(IOException.class, () -> UniversalVtt.parse(
                "{\"resolution\": {\"pixels_per_grid\": -5}, \"image\": \"\"}"));
    }
}

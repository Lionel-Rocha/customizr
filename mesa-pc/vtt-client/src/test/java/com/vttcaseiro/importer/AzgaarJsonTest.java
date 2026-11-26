package com.vttcaseiro.importer;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** JSON "Full" do Azgaar: le a geometria e desenha o mapa (inteiro e uma regiao) sem PNG. */
class AzgaarJsonTest {

    /**
     * Mundo de mentira no formato do export: grade de 40 x 20 celulas quadradas (20 px cada) de um mapa 800 x 400,
     * uma ilha no meio (altura cai do centro pra fora), dois reinos (esquerda/direita), um rio e uma estrada.
     */
    static String fakeFull() {
        int cols = 40, rows = 20, cell = 20;
        StringBuilder cells = new StringBuilder();
        StringBuilder verts = new StringBuilder();
        for (int y = 0; y <= rows; y++) {
            for (int x = 0; x <= cols; x++) {
                if (verts.length() > 0) verts.append(',');
                verts.append("{\"i\":").append(y * (cols + 1) + x).append(",\"p\":[").append(x * cell).append(',').append(y * cell).append("]}");
            }
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int i = y * cols + x;
                int v0 = y * (cols + 1) + x, v1 = v0 + 1, v2 = v0 + cols + 2, v3 = v0 + cols + 1;
                double dx = (x - cols / 2.0) / (cols / 2.0), dy = (y - rows / 2.0) / (rows / 2.0);
                int h = (int) Math.max(5, Math.round(85 - 90 * Math.sqrt(dx * dx + dy * dy)));
                int biome = h < 20 ? 0 : h > 70 ? 11 : h > 50 ? 8 : 5;
                int state = h < 20 ? 0 : x < cols / 2 ? 1 : 2;
                StringBuilder nb = new StringBuilder();
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] k : d) {
                    int nx = x + k[0], ny = y + k[1];
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                    if (nb.length() > 0) nb.append(',');
                    nb.append(ny * cols + nx);
                }
                if (cells.length() > 0) cells.append(',');
                cells.append(String.format(Locale.ROOT, "{\"i\":%d,\"v\":[%d,%d,%d,%d],\"c\":[%s],\"p\":[%d,%d],\"h\":%d,\"biome\":%d,\"state\":%d}",
                        i, v0, v1, v2, v3, nb, x * cell + cell / 2, y * cell + cell / 2, h, biome, state));
            }
        }
        String river = "[" + (10 * cols + 20) + "," + (11 * cols + 21) + "," + (12 * cols + 22) + "," + (13 * cols + 24) + "," + (15 * cols + 26) + "," + (18 * cols + 27) + "]";
        return "{\"info\":{\"version\":\"1.158.0\",\"description\":\"Azgaar's Fantasy Map Generator output: azgaar.github.io/Fantasy-map-generator\","
                + "\"mapName\":\"Ilha Teste\",\"width\":800,\"height\":400},"
                + "\"settings\":{\"distanceUnit\":\"km\",\"distanceScale\":2,\"populationRate\":1000,\"urbanization\":1,\"mapName\":\"Ilha Teste\"},"
                + "\"grid\":{\"cells\":[1,2,3]},"
                + "\"pack\":{\"cells\":[" + cells + "],\"vertices\":[" + verts + "],"
                + "\"biomes\":[{\"i\":0,\"color\":\"#466eab\"},{\"i\":1,\"color\":\"#fbe79f\"},{\"i\":2,\"color\":\"#b5b887\"},{\"i\":3,\"color\":\"#d2d082\"},"
                + "{\"i\":4,\"color\":\"#c8d68f\"},{\"i\":5,\"color\":\"#b6d95d\"},{\"i\":6,\"color\":\"#29bc56\"},{\"i\":7,\"color\":\"#7dcb35\"},"
                + "{\"i\":8,\"color\":\"#409c43\"},{\"i\":9,\"color\":\"#4b6b32\"},{\"i\":10,\"color\":\"#96784b\"},{\"i\":11,\"color\":\"#d5e7eb\"}],"
                + "\"states\":[{\"i\":0,\"name\":\"Neutrals\"},{\"i\":1,\"name\":\"Oeste\",\"fullName\":\"Reino do Oeste\",\"color\":\"#c96b5b\",\"capital\":1,\"pole\":[300,200],\"diplomacy\":[\"x\",\"x\",\"Rival\"]},"
                + "{\"i\":2,\"name\":\"Leste\",\"color\":\"#5b8bc9\",\"capital\":2,\"pole\":[500,200],\"diplomacy\":[\"x\",\"Rival\",\"x\"]}],"
                + "\"burgs\":[0,{\"i\":1,\"name\":\"Ponta Oeste\",\"x\":260,\"y\":190,\"capital\":1,\"state\":1,\"population\":8},"
                + "{\"i\":2,\"name\":\"Porto Leste\",\"x\":540,\"y\":230,\"capital\":1,\"state\":2,\"population\":5},"
                + "{\"i\":3,\"name\":\"Vau\",\"x\":430,\"y\":250,\"state\":2,\"population\":1}],"
                + "\"rivers\":[{\"i\":1,\"name\":\"Rio Claro\",\"discharge\":300,\"cells\":" + river + "}],"
                + "\"routes\":[{\"i\":0,\"group\":\"roads\",\"points\":[[260,190,1],[400,210,2],[540,230,3]]}],"
                + "\"markers\":[{\"i\":0,\"type\":\"dungeons\",\"x\":380,\"y\":150,\"cell\":3,\"name\":\"Cova\"}],"
                + "\"cultures\":[{\"i\":0,\"name\":\"Wildlands\"},{\"i\":1,\"name\":\"Ilhéus\",\"type\":\"Naval\"}],"
                + "\"religions\":[{\"i\":0,\"name\":\"No religion\"}]},"
                + "\"nameBases\":[]}";
    }

    @Test
    void readsTheGeometryAndTheWorld() throws Exception {
        AzgaarJson.Result r = AzgaarJson.read(new StringReader(fakeFull()));
        assertEquals("Ilha Teste", r.world().mapName());
        assertEquals(2, r.world().states().size());
        assertEquals("Leste (rival)", r.world().factions().get(0).enemies);
        assertEquals(3, r.world().burgs().size());
        assertEquals(800, r.geometry().cells());
        assertEquals(21 * 41, r.geometry().vertexX().length);
        assertEquals(1, r.geometry().rivers().size());
        assertEquals(1, r.geometry().routes().size());
        assertEquals("#c96b5b", r.geometry().stateColors()[1]);
        assertArrayEquals(new int[]{1, 42}, AzgaarRender.sharedEdge(new int[]{0, 1, 42, 41}, new int[]{1, 2, 43, 42}));
    }

    @Test
    void drawsTheWholeWorldAndARegionSharp() throws Exception {
        AzgaarJson.Result r = AzgaarJson.read(new StringReader(fakeFull()));
        int[] size = AzgaarRender.outSize(800, 400, 1600);
        assertArrayEquals(new int[]{1600, 800}, size);
        byte[] png = AzgaarRender.png(r.world(), r.geometry(), 0, 0, 800, 400, size[0], size[1], AzgaarRender.Style.RELEVO);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(1600, img.getWidth());
        int sea = img.getRGB(5, 5), land = img.getRGB(800, 420);
        assertNotEquals(sea, land, "mar e terra com cores diferentes");
        assertTrue((sea & 0xff) > ((sea >> 16) & 0xff), "canto e mar (azul)");

        byte[] region = AzgaarRender.png(r.world(), r.geometry(), 200, 100, 400, 200, 1600, 800, AzgaarRender.Style.POLITICO);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(region)));

        String snap = System.getProperty("vtt.snap");
        if (snap != null) {
            java.nio.file.Files.write(java.nio.file.Path.of(snap, "azgaar-mundo.png"), png);
            java.nio.file.Files.write(java.nio.file.Path.of(snap, "azgaar-regiao.png"), region);
        }
    }

    @Test
    void rejectsJsonThatIsNotAzgaar() {
        assertThrows(java.io.IOException.class, () -> AzgaarJson.read(new StringReader("{\"foo\":1}")));
    }
}

package com.vttcaseiro.importer;

import com.vttcaseiro.view.ImageFiles;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** JPG, JPEG, PNG e GIF em tudo; mapa em GIF continua GIF (nao vira .jpg). */
class ImageFormatsTest {

    @Test
    void acceptedTypes() {
        for (String ok : new String[]{"mapa.png", "token.JPG", "objeto.jpeg", "efeito.gif"}) assertTrue(ImageFiles.accepts(ok), ok);
        for (String no : new String[]{"foto.webp", "mapa.svg", "x.gif.exe", null}) assertFalse(ImageFiles.accepts(no), String.valueOf(no));
    }

    @Test
    void gifMapKeepsItsType() throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (String format : new String[]{"gif", "png", "jpeg"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(img, format, out), format);
            MapImageFit.Fitted fitted = MapImageFit.fit(out.toByteArray(), "mapa." + format);
            assertFalse(fitted.changed(), "pequeno: nao recomprime");
            assertEquals("." + format, fitted.extension());
            assertArrayEquals(out.toByteArray(), fitted.bytes(), "bytes intactos (GIF animado continua animado)");
        }
    }
}

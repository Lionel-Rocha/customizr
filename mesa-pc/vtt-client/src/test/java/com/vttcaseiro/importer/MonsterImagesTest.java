package com.vttcaseiro.importer;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Imagem de monstro do 5etools por link: endereco certo e .webp virando PNG (que o programa abre). */
class MonsterImagesTest {

    @Test
    void buildsTheFiveToolsAddress() {
        assertEquals("https://5e.tools/img/bestiary/tokens/XMM/Goblin%20Warrior.webp",
                MonsterImages.url(null, MonsterImages.Kind.TOKEN, "XMM", "Goblin Warrior"));
        assertEquals("https://espelho.exemplo/img/bestiary/MM/Aboleth.webp",
                MonsterImages.url("https://espelho.exemplo/img/", MonsterImages.Kind.ART, "MM", " Aboleth "));
        assertEquals("https://5e.tools/img/bestiary/tokens/XMM/Ki-rin%27s%20Friend.webp",
                MonsterImages.url("", MonsterImages.Kind.TOKEN, "XMM", "Ki-rin's Friend"));
    }

    @Test
    void webpBecomesPng() throws Exception {
        // 1x1 webp (lossless): o formato que o 5etools serve
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        byte[] png = MonsterImages.toPng(webp);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(1, img.getWidth());
        assertThrows(java.io.IOException.class, () -> MonsterImages.toPng(new byte[]{1, 2, 3}));
    }

    @Test
    void onlyHttps() {
        assertThrows(java.io.IOException.class, () -> MonsterImages.downloadPng("http://5e.tools/img/x.webp"));
        assertThrows(java.io.IOException.class, () -> MonsterImages.downloadPng("file:///C:/segredo.png"));
    }
}

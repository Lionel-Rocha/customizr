package com.vttcaseiro.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Imagem por link: baixa, confere pelo conteudo, guarda com nome pelo hash; pagina HTML e erro claro. */
class ImagePickerTest {

    @TempDir
    Path dir;

    @Test
    void downloadsRealImagesAndRejectsPages() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] pngBytes = png.toByteArray();
        java.net.ServerSocket server = new java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (java.net.Socket c = server.accept()) {
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
                    String first = r.readLine();
                    for (String l = r.readLine(); l != null && !l.isEmpty(); l = r.readLine()) { }
                    String path = first == null ? "" : first.split(" ")[1];
                    byte[] body;
                    String status = "200 OK";
                    if (path.equals("/goblin")) body = pngBytes;
                    else if (path.equals("/pagina")) body = "<!DOCTYPE html><html><body>imagem aqui</body></html>".getBytes();
                    else { body = new byte[0]; status = "404 Not Found"; }
                    var out = c.getOutputStream();
                    out.write(("HTTP/1.1 " + status + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes());
                    out.write(body);
                    out.flush();
                } catch (IOException ignored) {
                    // fechou
                }
            }
        });
        t.setDaemon(true);
        t.start();
        String base = "http://127.0.0.1:" + server.getLocalPort();
        try {
            File f = ImagePicker.download(base + "/goblin", dir);
            assertTrue(f.getName().endsWith(".png"), "extensao pelo conteudo, mesmo sem .png no link");
            assertArrayEquals(png.toByteArray(), Files.readAllBytes(f.toPath()));
            assertEquals(f, ImagePicker.download(base + "/goblin", dir), "mesmo conteudo = mesmo arquivo");

            IOException page = assertThrows(IOException.class, () -> ImagePicker.download(base + "/pagina", dir));
            assertTrue(page.getMessage().contains("Copiar endereço da imagem"), page.getMessage());
            IOException gone = assertThrows(IOException.class, () -> ImagePicker.download(base + "/sumiu", dir));
            assertTrue(gone.getMessage().contains("404"));
            assertThrows(IOException.class, () -> ImagePicker.download("file:///C:/Windows/win.ini", dir));
            assertThrows(IOException.class, () -> ImagePicker.download("ftp://x/y.png", dir));
        } finally {
            server.close();
        }
    }

    @Test
    void urlAndSignatureChecks() {
        assertTrue(ImagePicker.looksLikeUrl("https://exemplo.com/a.png"));
        assertFalse(ImagePicker.looksLikeUrl("C:\\imagens\\a.png"));
        assertFalse(ImagePicker.looksLikeUrl("https://com espaco"));
        assertEquals(".gif", ImagePicker.extensionOf("GIF89a....".getBytes()));
        assertEquals(".jpg", ImagePicker.extensionOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertNull(ImagePicker.extensionOf("RIFF0000WEBP".getBytes()));
    }
}

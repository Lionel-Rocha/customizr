package com.vttcaseiro.importer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Mapas exportados de geradores (Azgaar em escala alta, cidades do Watabou) passam facil do
 * limite de imagem da mesa (4 MB, que tambem viaja pela rede pros jogadores). Aqui a imagem
 * e reduzida/recomprimida ate caber, mantendo a proporcao.
 */
public final class MapImageFit {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    /** Lado maximo: acima disso JavaFX gasta memoria demais e ninguem ve a diferenca. */
    public static final int MAX_SIDE = 8192;

    public record Fitted(byte[] bytes, String extension, int width, int height, boolean changed) {
    }

    private MapImageFit() {
    }

    public static Fitted fit(byte[] original, String fileName) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) {
            throw new IOException("Não consegui abrir essa imagem. Use JPG, JPEG, PNG ou GIF (SVG não: no gerador, exporte como PNG).");
        }
        // mantem o tipo do arquivo (GIF continua GIF, animado); so vira JPG se precisar reduzir
        String lower = fileName == null ? "" : fileName.toLowerCase();
        String ext = lower.endsWith(".png") ? ".png" : lower.endsWith(".gif") ? ".gif" : lower.endsWith(".jpeg") ? ".jpeg" : ".jpg";
        if (original.length <= MAX_BYTES && Math.max(img.getWidth(), img.getHeight()) <= MAX_SIDE) {
            return new Fitted(original, ext, img.getWidth(), img.getHeight(), false);
        }
        double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(img.getWidth(), img.getHeight()));
        for (int attempt = 0; attempt < 8; attempt++) {
            BufferedImage scaled = scale(img, scale);
            byte[] jpg = jpeg(scaled, 0.88f);
            if (jpg.length <= MAX_BYTES) return new Fitted(jpg, ".jpg", scaled.getWidth(), scaled.getHeight(), true);
            scale *= 0.8;
        }
        throw new IOException("Imagem grande demais mesmo reduzida; exporte numa escala menor.");
    }

    private static BufferedImage scale(BufferedImage src, double s) {
        int w = Math.max(1, (int) Math.round(src.getWidth() * s)), h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

package com.vttcaseiro.importer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Recorte de um mundo do Azgaar: a regiao em volta de um ponto (em pixels do mapa) vira uma imagem nova, ampliada
 * pra jogar de perto. O retangulo fica dentro do mapa (perto da borda ele escorrega pra dentro).
 */
public final class AzgaarRegion {
    private AzgaarRegion() {
    }

    /** Lado maior da imagem do recorte. */
    public static final int OUT_SIDE = 2048;

    /** Recorte em pixels do mapa (x, y, largura, altura) e a imagem pronta. */
    public record Cut(double x, double y, double w, double h, byte[] png, int outW, int outH) {
    }

    /** O retangulo (quadrado, lado 2*raio) em volta de (cx, cy), escorregado pra caber no mapa de mapW x mapH. */
    public static double[] square(double cx, double cy, double radius, int mapW, int mapH) {
        double side = Math.max(8, Math.min(2 * radius, Math.min(mapW, mapH)));
        double x = Math.max(0, Math.min(mapW - side, cx - side / 2));
        double y = Math.max(0, Math.min(mapH - side, cy - side / 2));
        return new double[]{x, y, side, side};
    }

    /** Recorta da imagem do mundo (imageBytes, que cobre o mapa inteiro de mapW x mapH) e amplia ate OUT_SIDE. */
    public static Cut crop(byte[] imageBytes, int mapW, int mapH, double[] rect) throws IOException {
        BufferedImage world = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (world == null) throw new IOException("Não deu pra ler a imagem do mundo.");
        double sx = world.getWidth() / (double) mapW, sy = world.getHeight() / (double) mapH;
        int px = (int) Math.floor(rect[0] * sx), py = (int) Math.floor(rect[1] * sy);
        int pw = Math.max(1, Math.min(world.getWidth() - px, (int) Math.ceil(rect[2] * sx)));
        int ph = Math.max(1, Math.min(world.getHeight() - py, (int) Math.ceil(rect[3] * sy)));
        BufferedImage part = world.getSubimage(Math.max(0, px), Math.max(0, py), pw, ph);
        double k = OUT_SIDE / (double) Math.max(pw, ph);
        int outW = Math.max(1, (int) Math.round(pw * k)), outH = Math.max(1, (int) Math.round(ph * k));
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(part, 0, 0, outW, outH, null);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return new Cut(rect[0], rect[1], rect[2], rect[3], bytes.toByteArray(), outW, outH);
    }
}

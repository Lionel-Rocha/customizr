package com.vttcaseiro.view;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Carrega imagens sem estourar a placa de video. Uma imagem (ou um Canvas) maior que o limite de
 * textura da GPU (8192 a 16384 px, dependendo da placa) ou que ocupa memoria de video demais faz o
 * JavaFX quebrar na hora de desenhar ("Texture ... is null"), e a tela para de atualizar. Aqui o
 * tamanho e lido so pelo cabecalho do arquivo e, se passar do limite, a imagem ja e decodificada
 * reduzida. Quem desenha estica de volta pro tamanho original (ImageView.fitWidth), entao as
 * coordenadas do mapa nao mudam.
 */
public final class SafeImage {
    /** Lado maximo de uma imagem carregada (o importador de mapas ja reduz pra isso tambem). */
    public static final int MAX_SIDE = 8192;
    /** Area maxima (~ 8192 x 4096 = 128 MB de textura). */
    public static final long MAX_PIXELS = 8192L * 4096L;

    /** Imagem carregada + o tamanho original dela (o que o mapa usa como medida). */
    public record Loaded(Image image, double width, double height) {
        public boolean reduced() {
            return image.getWidth() < width - 0.5 || image.getHeight() < height - 0.5;
        }
    }

    private SafeImage() {
    }

    public static Loaded read(byte[] bytes) {
        return read(bytes, MAX_SIDE, MAX_PIXELS);
    }

    public static Loaded read(byte[] bytes, int maxSide, long maxPixels) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            int[] size = headerSize(new ByteArrayInputStream(bytes));
            double[] fit = fit(size, maxSide, maxPixels);
            Image img = fit == null
                    ? new Image(new ByteArrayInputStream(bytes))
                    : new Image(new ByteArrayInputStream(bytes), fit[0], fit[1], false, true);
            return wrap(img, size, maxSide, maxPixels, () -> new ByteArrayInputStream(bytes));
        } catch (RuntimeException | OutOfMemoryError ex) {
            return null;
        }
    }

    public static Loaded read(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            int[] size;
            try (InputStream in = Files.newInputStream(file.toPath())) {
                size = headerSize(in);
            }
            double[] fit = fit(size, MAX_SIDE, MAX_PIXELS);
            String url = file.toURI().toString();
            Image img = fit == null ? new Image(url) : new Image(url, fit[0], fit[1], false, true);
            return wrap(img, size, MAX_SIDE, MAX_PIXELS, () -> {
                try {
                    return Files.newInputStream(file.toPath());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (IOException | RuntimeException | OutOfMemoryError ex) {
            return null;
        }
    }

    private static Loaded wrap(Image img, int[] size, int maxSide, long maxPixels,
                               java.util.function.Supplier<InputStream> again) {
        if (img == null || img.isError()) return null;
        if (size == null) {
            // formato que o ImageIO nao le (ex.: webp via plugin do JavaFX): confere depois de carregar
            size = new int[]{(int) img.getWidth(), (int) img.getHeight()};
            double[] fit = fit(size, maxSide, maxPixels);
            if (fit != null) {
                try (InputStream in = again.get()) {
                    Image smaller = new Image(in, fit[0], fit[1], false, true);
                    if (!smaller.isError()) img = smaller;
                } catch (IOException | RuntimeException ignored) {
                    // fica com a grande mesmo
                }
            }
        }
        return new Loaded(img, size[0], size[1]);
    }

    /** Tamanho reduzido pra caber nos limites, ou null se ja cabe (ou nao deu pra saber). */
    static double[] fit(int[] size, int maxSide, long maxPixels) {
        if (size == null || size[0] <= 0 || size[1] <= 0) return null;
        double w = size[0];
        double h = size[1];
        double s = Math.min(1.0, maxSide / Math.max(w, h));
        s = Math.min(s, Math.sqrt(maxPixels / (w * h)));
        if (s >= 1.0) return null;
        return new double[]{Math.max(1, Math.floor(w * s)), Math.max(1, Math.floor(h * s))};
    }

    /** Largura/altura lidas so do cabecalho (sem decodificar), ou null. */
    static int[] headerSize(InputStream in) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader r = readers.next();
            try {
                r.setInput(iis, true, true);
                return new int[]{r.getWidth(0), r.getHeight(0)};
            } finally {
                r.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}

package com.vttcaseiro.importer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Imagem de um monstro do 5etools baixada por link, so no computador de quem pediu (a arte dos livros nunca vai pro
 * repositorio). O 5etools serve as imagens em .webp; aqui elas viram PNG, que o programa abre.
 */
public final class MonsterImages {

    /** Endereco padrao das imagens (da pra trocar nas preferencias, se o site mudar de lugar). */
    public static final String DEFAULT_BASE = "https://5e.tools/img";
    /** Tamanho maximo de uma imagem baixada. */
    static final int MAX_BYTES = 12 * 1024 * 1024;

    public enum Kind {
        /** Token redondo (o que vai no mapa). */
        TOKEN,
        /** Ilustracao inteira. */
        ART
    }

    private MonsterImages() {
    }

    /** Ex.: https://5e.tools/img/bestiary/tokens/XMM/Goblin%20Warrior.webp */
    public static String url(String base, Kind kind, String source, String name) {
        String b = base == null || base.isBlank() ? DEFAULT_BASE : base.strip();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        String file = URLEncoder.encode(name.strip(), StandardCharsets.UTF_8).replace("+", "%20") + ".webp";
        String src = URLEncoder.encode(source.strip(), StandardCharsets.UTF_8);
        return b + "/bestiary/" + (kind == Kind.TOKEN ? "tokens/" : "") + src + "/" + file;
    }

    /** Baixa (so https) e devolve PNG. */
    public static byte[] downloadPng(String url) throws IOException {
        if (url == null || !url.startsWith("https://")) throw new IOException("Endereço inválido: " + url);
        return toPng(download(url));
    }

    static byte[] download(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "Customizr");
            int code = con.getResponseCode();
            if (code == 404) throw new IOException("Esse monstro não tem imagem lá (404).");
            if (code != 200) throw new IOException("O site respondeu " + code + ".");
            try (InputStream in = con.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Imagem grande demais.");
                return bytes;
            }
        } finally {
            con.disconnect();
        }
    }

    /** Qualquer imagem que o ImageIO le (inclusive .webp, com a biblioteca TwelveMonkeys) vira PNG. */
    public static byte[] toPng(byte[] bytes) throws IOException {
        ImageIO.scanForPlugins(); // garante o leitor de .webp registrado (programa montado com jpackage)
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("Não deu pra ler a imagem (formato desconhecido).");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}

package com.vttcaseiro.net;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Antes, um token/mapa so mandava pela rede o CAMINHO do arquivo no
 * computador de quem criou -- que nao existe nas outras maquinas. Isso
 * fazia qualquer imagem sumir pra todo mundo que nao fosse o dono do
 * arquivo. Agora os BYTES da imagem viajam dentro da propria mensagem
 * (como texto Base64 -- o Gson so sabe serializar String/numero/etc, nao
 * byte[] bruto direto em JSON de forma legivel).
 * <p>
 * Limite de tamanho de proposito: numa mesa caseira (LAN ou Tailscale),
 * mandar uma imagem de 10 MB pela rede socket simples que fizemos
 * (sem compressao de stream) ia travar a sessao por um tempo. 4 MB cobre
 * a esmagadora maioria de token/retrato/mapa em PNG/JPG comprimido.
 */
public final class ImageCodec {

    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private ImageCodec() {
    }

    /** @throws IOException se o arquivo nao existir, nao puder ser lido, ou passar do limite de tamanho */
    public static String encodeFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Imagem maior que " + (MAX_BYTES / 1024 / 1024)
                    + " MB (" + (bytes.length / 1024 / 1024) + " MB) -- escolha uma imagem menor.");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static Image decode(String base64) {
        return decode(base64, 0, 0);
    }

    /** Igual {@link #decode(String)}, mas pre-escala a imagem pro tamanho
     * pedido (mais leve pra desenhar um token pequeno a partir de uma
     * imagem grande) -- mesmo padrao que o carregamento direto de
     * arquivo ja usa em TokenNode/MapView. width/height <= 0 carrega no
     * tamanho original.
     * <p>
     * Devolve null (em vez de lancar excecao) se o texto nao for Base64
     * valido ou nao formar uma imagem valida -- quem recebeu uma
     * mensagem de rede nao deve travar por causa de um campo de imagem
     * malformado, so deve cair no retrato padrao (silhueta). */
    public static Image decode(String base64, double width, double height) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image image = (width > 0 && height > 0)
                    ? new Image(new ByteArrayInputStream(bytes), width, height, false, true)
                    : new Image(new ByteArrayInputStream(bytes));
            return image.isError() ? null : image;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

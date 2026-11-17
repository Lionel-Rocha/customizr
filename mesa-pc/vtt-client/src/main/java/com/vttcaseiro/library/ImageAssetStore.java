package com.vttcaseiro.library;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Antes (ver com.vttcaseiro.net.ImageCodec), cada TokenModel/CharacterSheet
 * guardava a PROPRIA copia inteira dos bytes da imagem em Base64. Isso
 * funciona pra imagem viajar pela rede, mas e um desperdicio enorme no
 * disco da mesa: se o mestre usa o mesmo retrato de "Goblin" em 10 tokens
 * diferentes, a mesa guardava 10 copias identicas da mesma imagem, uma
 * dentro de cada arquivo de token.
 * <p>
 * Esta classe guarda cada imagem UMA UNICA VEZ dentro da mesa, num
 * arquivo nomeado pelo hash SHA-256 dos proprios bytes (como o Git guarda
 * objetos, e como Foundry guarda cada arquivo de imagem uma vez na pasta
 * de assets do mundo em vez de duplicar por ator). Duas imagens com os
 * bytes exatamente iguais sempre caem no MESMO arquivo e no MESMO id,
 * mesmo vindas de maquinas diferentes -- e isso que permite, na Fase 2,
 * so transmitir um asset pela rede quando o outro lado ainda nao o tem
 * (ver NetworkService), em vez de mandar a mesma imagem de novo a cada
 * token que a usa.
 * <p>
 * TokenModel/CharacterSheet/MobSheet passam a guardar so o {@code
 * imageAssetId} (uma string curta) em vez do Base64 inteiro -- o campo
 * antigo {@code imageData} continua existindo pra compatibilidade com
 * fichas/mapas salvos antes desta mudanca e para o caso raro de uma
 * imagem que chegou pela rede mas ainda nao foi gravada no asset store
 * local.
 */
public class ImageAssetStore {

    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final Path dir;

    public ImageAssetStore(Path mesaRoot) throws IOException {
        this.dir = mesaRoot.resolve("assets").resolve("images");
        Files.createDirectories(dir);
    }

    public Path getDir() { return dir; }

    /**
     * Guarda os bytes dados (se um arquivo com o mesmo hash ainda nao
     * existir -- nesse caso nao reescreve nada, so devolve o id que ja
     * existia) e devolve o id do asset.
     *
     * @throws IOException se os bytes passarem do limite de tamanho ou a gravacao falhar
     */
    public String store(byte[] bytes, String originalFileName) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Imagem maior que " + (MAX_BYTES / 1024 / 1024)
                    + " MB (" + (bytes.length / 1024 / 1024) + " MB) -- escolha uma imagem menor.");
        }
        String id = sha256Hex(bytes) + extensionOf(originalFileName);
        Path file = dir.resolve(id);
        if (!Files.exists(file)) {
            AtomicFiles.writeBytes(file, bytes);
        }
        return id;
    }

    /** @throws IOException se o id nao corresponder a um asset guardado */
    public byte[] read(String assetId) throws IOException {
        return Files.readAllBytes(dir.resolve(safeAssetId(assetId)));
    }

    /** Todos os ids ja guardados nesta mesa, ordenados -- base da galeria
     * de tokens/retratos reutilizaveis (escolher uma imagem ja usada
     * antes em vez de reabrir o FileChooser toda vez). So lista arquivos
     * cujo nome bate com o formato esperado (hash + extensao); qualquer
     * outra coisa que tenha caido na pasta por fora deste codigo
     * (improvavel, mas nao impossivel) e ignorada em vez de quebrar a
     * listagem inteira. */
    public java.util.List<String> listAssetIds() {
        try (var stream = Files.list(dir)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("[0-9a-f]{64}\\.(png|jpg|jpeg|gif|webp|bmp)"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return java.util.List.of();
        }
    }

    public boolean exists(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return false;
        }
        try {
            return Files.exists(dir.resolve(safeAssetId(assetId)));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Hash hexadecimal (64 caracteres) dos bytes dados -- isso que
     * garante a deduplicacao: bytes iguais sempre produzem o mesmo id,
     * entao guardar a "mesma" imagem duas vezes nunca cria um segundo
     * arquivo. */
    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 e garantido pela JVM (java.security.Security padrao) --
            // isso so pode acontecer numa JVM quebrada, e nao ha nada de
            // razoavel a fazer alem de parar.
            throw new UncheckedIOException(new IOException("SHA-256 nao disponivel nesta JVM", ex));
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return ".png";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".png";
        }
        String extension = fileName.substring(dot).toLowerCase();
        // So aceita extensoes conhecidas de imagem -- um nome de arquivo
        // malicioso tipo "foto.jpg.exe" nao deve virar extensao do asset.
        return switch (extension) {
            case ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp" -> extension;
            default -> ".png";
        };
    }

    /** Garante que o id so contem hex + extensao conhecida, pra um id
     * vindo da rede ou de um arquivo de save nunca poder "escapar" da
     * pasta assets/images (ataque de path traversal tipo
     * "../../../etc/passwd"). */
    private static String safeAssetId(String assetId) {
        if (!assetId.matches("[0-9a-f]{64}\\.(png|jpg|jpeg|gif|webp|bmp)")) {
            throw new IllegalArgumentException("Id de asset de imagem invalido: " + assetId);
        }
        return assetId;
    }
}

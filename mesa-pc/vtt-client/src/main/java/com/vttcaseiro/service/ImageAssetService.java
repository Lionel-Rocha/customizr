package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ponte entre a tela (que tem um arquivo escolhido pelo usuario no disco,
 * via FileChooser) e o ImageAssetStore da mesa (que guarda bytes
 * deduplicados por hash -- ver ImageAssetStore). A tela nunca chama
 * ImageAssetStore direto, igual nunca chama LibraryStorage direto: so
 * conhece este service, que so conhece o MesaRepository.
 */
public class ImageAssetService {
    private static final Log log = Log.of(ImageAssetService.class);

    private final MesaRepository repository;

    public ImageAssetService(MesaRepository repository) {
        this.repository = repository;
    }

    /**
     * Le o arquivo escolhido pelo usuario e guarda os bytes no
     * ImageAssetStore da mesa, devolvendo o id do asset (que e o que vai
     * no campo imageAssetId/portraitImageAssetId de TokenModel/
     * CharacterSheet/MobSheet a partir daqui).
     *
     * @throws IOException se o arquivo nao existir, nao puder ser lido, ou passar do limite de tamanho do asset store
     */
    public String importFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String fileName = file.getFileName() != null ? file.getFileName().toString() : "";
        String assetId = repository.getImageAssetStore().store(bytes, fileName);
        log.info("Imagem importada pro asset store: " + fileName + " -> " + assetId);
        return assetId;
    }

    /** @throws IOException se o id nao corresponder a um asset guardado */
    public byte[] readBytes(String assetId) throws IOException {
        return repository.getImageAssetStore().read(assetId);
    }

    public boolean exists(String assetId) {
        return repository.getImageAssetStore().exists(assetId);
    }

    /**
     * Guarda uma imagem que estava embutida em base64 (formato antigo de
     * token/cena) no asset store e devolve o id. A extensao vem dos bytes
     * (PNG/JPEG/GIF/WEBP), ja que base64 solto nao tem nome de arquivo.
     */
    public String importBase64(String base64) throws IOException {
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Imagem embutida inválida: " + ex.getMessage());
        }
        return repository.getImageAssetStore().store(bytes, "imagem" + extensionFromMagic(bytes));
    }

    static String extensionFromMagic(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return ".png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return ".jpg";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return ".gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[8] == 'W' && b[9] == 'E') return ".webp";
        return ".png";
    }

    /**
     * Converte as imagens embutidas em base64 de uma cena (fundo e tokens)
     * pra assets por hash: a cena (e cada mensagem de rede com ela) encolhe
     * de megabytes pra alguns kilobytes, e a imagem viaja uma vez so, pelo
     * sistema de assets. Devolve true se mudou algo (vale salvar a cena).
     */
    public boolean migrateInlineImages(com.vttcaseiro.model.SceneModel scene) {
        boolean changed = false;
        if (scene.getBackgroundImageData() != null && !scene.getBackgroundImageData().isBlank()) {
            try {
                if (scene.getBackgroundImageAssetId() == null) {
                    scene.setBackgroundImageAssetId(importBase64(scene.getBackgroundImageData()));
                }
                scene.setBackgroundImageData(null);
                changed = true;
            } catch (IOException ex) {
                log.warn("Fundo da cena continua embutido: " + ex.getMessage());
            }
        }
        for (com.vttcaseiro.model.TokenModel token : scene.getTokens()) {
            if (token.getImageData() == null || token.getImageData().isBlank()) continue;
            try {
                if (token.getImageAssetId() == null) token.setImageAssetId(importBase64(token.getImageData()));
                token.setImageData(null);
                changed = true;
            } catch (IOException ex) {
                log.warn("Imagem do token " + token.getName() + " continua embutida: " + ex.getMessage());
            }
        }
        return changed;
    }

    /** Todos os assets ja guardados nesta mesa -- base da galeria de
     * tokens/retratos reutilizaveis (ver ImageGalleryView). */
    public java.util.List<String> list() {
        return repository.getImageAssetStore().listAssetIds();
    }
}

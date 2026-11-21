package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.ImageResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.ImageAsset;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.ImageAssetRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Imagens da campanha (mapas, retratos). O tipo e conferido pelos primeiros bytes (PNG, JPEG,
 * WebP, GIF), nao pela extensao; a mesma imagem enviada de novo devolve a que ja existe.
 */
@Service
public class ImageService {

    private final ImageAssetRepository imageAssetRepository;
    private final CampaignRepository campaignRepository;
    private final MasterGuard masterGuard;

    public ImageService(ImageAssetRepository imageAssetRepository, CampaignRepository campaignRepository,
                        MasterGuard masterGuard) {
        this.imageAssetRepository = imageAssetRepository;
        this.campaignRepository = campaignRepository;
        this.masterGuard = masterGuard;
    }

    /** Qualquer membro (nao espectador) pode enviar: jogador sobe o retrato do proprio personagem. */
    @Transactional
    public ImageResponse upload(UUID campaignId, byte[] data, String fileName, String username) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campanha não encontrada"));
        masterGuard.requireMember(username, campaign);
        if (data == null || data.length == 0) {
            throw badRequest("A imagem está vazia");
        }
        if (data.length > ImageAsset.MAX_BYTES) {
            throw badRequest("Imagem grande demais (máximo 8 MB)");
        }
        String type = detectType(data);
        if (type == null) {
            throw badRequest("Só aceita imagem PNG, JPEG, WebP e GIF");
        }
        String hash = sha256(data);
        ImageAsset image = imageAssetRepository.findByCampaignAndSha256(campaign, hash)
                .orElseGet(() -> imageAssetRepository.save(new ImageAsset(campaign, data, type, hash, fileName)));
        return ImageResponse.from(image);
    }

    /** Devolve a imagem pra quem e da campanha. */
    @Transactional
    public ImageAsset read(UUID imageId, String username) {
        ImageAsset image = imageAssetRepository.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada"));
        masterGuard.requireMember(username, image.getCampaign());
        return image;
    }

    static String detectType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return "image/gif";
        return null;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

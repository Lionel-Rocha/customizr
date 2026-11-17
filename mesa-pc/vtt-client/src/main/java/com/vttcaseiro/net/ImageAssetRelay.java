package com.vttcaseiro.net;

import com.vttcaseiro.library.ImageAssetStore;

import java.io.IOException;
import java.util.Base64;

/**
 * Converte entre o {@link ImageAssetStore} local (bytes guardados em
 * disco, deduplicados por hash -- ver essa classe) e as mensagens
 * "image_data"/"image_request" do protocolo de rede (bytes em Base64
 * dentro de um NetMessage). Usado tanto por {@link GameServer} (que tem
 * seu proprio ImageAssetStore, da mesa de quem hospeda) quanto por
 * {@link GameClient} (o ImageAssetStore da mesa de cada jogador) -- fica
 * aqui em vez de duplicado nos dois pra decodificar/validar Base64 de um
 * jeito so.
 */
public final class ImageAssetRelay {

    private ImageAssetRelay() {
    }

    /** @throws IOException se o id nao corresponder a um asset guardado localmente */
    public static NetMessage encode(ImageAssetStore store, String assetId) throws IOException {
        byte[] bytes = store.read(assetId);
        return NetMessage.imageData(assetId, Base64.getEncoder().encodeToString(bytes));
    }

    /**
     * Guarda os bytes recebidos (decodificando o Base64 da mensagem) no
     * asset store local, se ainda nao existir -- note que o id final e
     * SEMPRE recalculado a partir dos bytes de verdade (ImageAssetStore
     * nunca confia num id so porque alguem afirmou que os bytes
     * correspondem a ele), entao isto e seguro mesmo recebendo de um
     * jogador que mandou algo errado por engano.
     *
     * @return true se guardou (ou se o asset ja existia local); false se
     *         nao tinha store disponivel (offline) ou o Base64 veio
     *         corrompido/incompleto.
     */
    public static boolean storeIncoming(ImageAssetStore store, NetMessage msg) {
        if (store == null || msg == null || msg.assetId == null || msg.assetBase64 == null) {
            return false;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(msg.assetBase64);
            // msg.assetId ja e "hash.extensao" (foi calculado assim por quem
            // mandou) -- passar ele como "nome de arquivo" so serve pra
            // ImageAssetStore extrair a extensao certa; o hash em si e
            // recalculado de novo a partir dos bytes recebidos.
            store.store(bytes, msg.assetId);
            return true;
        } catch (IllegalArgumentException | IOException ex) {
            return false;
        }
    }
}

package com.vttcaseiro.library;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.ImageAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class StorageSafetyTest {

    @Test
    void gravacaoAtomicaSubstituiENaoDeixaTemporario(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ficha.json");
        AtomicFiles.writeString(f, "{\"v\":1}");
        AtomicFiles.writeString(f, "{\"v\":2, \"acento\":\"ação\"}");
        assertEquals("{\"v\":2, \"acento\":\"ação\"}", Files.readString(f));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "nenhum .tmp sobrando");
        }
    }

    @Test
    void cenaAntigaComImagensEmBase64ViraAsset(@TempDir Path dir) throws Exception {
        LibraryStorage lib = new LibraryStorage(dir);
        ImageAssetService assets = new ImageAssetService(lib);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};
        String b64 = Base64.getEncoder().encodeToString(png);

        SceneModel scene = new SceneModel();
        scene.setBackgroundImageData(b64);
        TokenModel t = new TokenModel("Orc", null);
        t.setImageData(b64);
        scene.addToken(t);

        assertTrue(assets.migrateInlineImages(scene));
        assertNull(scene.getBackgroundImageData());
        assertNull(t.getImageData());
        assertNotNull(scene.getBackgroundImageAssetId());
        assertTrue(scene.getBackgroundImageAssetId().endsWith(".png"));
        assertEquals(scene.getBackgroundImageAssetId(), t.getImageAssetId(), "mesma imagem = mesmo asset (dedup)");
        assertArrayEquals(png, assets.readBytes(t.getImageAssetId()));
        assertFalse(assets.migrateInlineImages(scene), "segunda vez nao muda nada");

        lib.saveMap(scene, "Velha");
        assertFalse(Files.readString(dir.resolve("mapas").resolve("Velha.json")).contains(b64));
    }
}

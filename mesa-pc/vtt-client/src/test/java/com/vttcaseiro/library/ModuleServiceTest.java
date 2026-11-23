package com.vttcaseiro.library;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Modulo: exporta parte de uma mesa e importa em outra sem apagar nada. */
class ModuleServiceTest {

    @TempDir
    Path dir;

    @Test
    void roundTripBetweenTwoMesas() throws Exception {
        LibraryStorage a = new LibraryStorage(dir.resolve("mesaA"));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        String asset = a.getImageAssetStore().store(png.toByteArray(), "covil.png");
        SceneModel lair = new SceneModel();
        lair.setBackgroundImageAssetId(asset);
        lair.addToken(new TokenModel("Dragão", null));
        a.saveMap(lair, "Covil");
        a.saveMap(new SceneModel(), "Taverna");
        MobSheet dragon = new MobSheet("Dragão Vermelho");
        a.saveMob(dragon, "Dragão Vermelho");
        CharacterSheet duke = new CharacterSheet("Duque");
        duke.setOwnerPlayerId("ana");
        a.saveCharacter(duke, "Duque");
        HandoutStore ha = new HandoutStore(a.getRoot());
        ha.save(List.of(new Handout("Carta do rei", "Venham ao castelo.")));
        Campaign ca = new Campaign();
        Campaign.Npc npc = new Campaign.Npc();
        npc.name = "Bento";
        ca.getNpcs().add(npc);
        Campaign.Parcel loot = new Campaign.Parcel();
        loot.name = "Tesouro do covil";
        loot.delivered = true;
        ca.getParcels().add(loot);

        Path zip = dir.resolve("covil" + ModuleService.EXTENSION);
        var sel = new ModuleService.Selection(List.of("Covil"), List.of("Dragão Vermelho"), List.of("Duque"), true, true);
        var out = ModuleService.export(a, ha, ca, sel, "O Covil", zip);
        assertEquals(1, out.scenes());
        assertEquals(1, out.images());

        // mesa B ja tem uma cena "Covil": a importada vira "Covil (2)"
        LibraryStorage b = new LibraryStorage(dir.resolve("mesaB"));
        b.saveMap(new SceneModel(), "Covil");
        HandoutStore hb = new HandoutStore(b.getRoot());
        Campaign cb = new Campaign();
        var in = ModuleService.importInto(b, hb, cb, zip);
        assertEquals(1, in.scenes());
        assertEquals(List.of("Covil", "Covil 2"), b.listMaps(), "nome repetido ganha 2");
        SceneModel imported = b.loadMap("Covil 2");
        assertEquals(asset, imported.getBackgroundImageAssetId());
        assertTrue(b.getImageAssetStore().exists(asset), "a imagem veio junto");
        assertEquals(1, imported.getTokens().size());
        assertEquals(List.of(MesaRepositoryNames.of("Dragão Vermelho")), b.listMobs());
        CharacterSheet d = b.loadCharacter(b.listCharacters().get(0));
        assertNull(d.getOwnerPlayerId(), "ficha chega sem dono");
        assertEquals(1, hb.load().size());
        assertEquals("Bento", cb.getNpcs().get(0).name);
        assertFalse(cb.getParcels().get(0).delivered, "tesouro chega como não entregue");
        assertEquals(List.of("Taverna", "Covil").size(), 2);
    }

    @Test
    void rejectsNonModulesAndPathTricks() throws Exception {
        LibraryStorage b = new LibraryStorage(dir.resolve("mesa"));
        Path bad = dir.resolve("x.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(bad))) {
            z.putNextEntry(new ZipEntry("imagens/../../fora.png"));
            z.write(new byte[]{1, 2, 3});
            z.closeEntry();
        }
        assertThrows(java.io.IOException.class, () -> ModuleService.importInto(b, null, new Campaign(), bad));
        assertFalse(Files.exists(dir.resolve("fora.png")), "nome com ../ nunca vira arquivo");
    }

    /** O nome que a lista mostra (acentos viram _). */
    static final class MesaRepositoryNames {
        static String of(String n) {
            return com.vttcaseiro.repository.MesaRepository.fileNameOf(n);
        }
    }
}

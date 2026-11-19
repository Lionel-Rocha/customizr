package com.vttcaseiro.vision;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Luz e visao do 5.5e: luz plena/penumbra, visao no escuro, as cegas, verdadeira, sentido sismico e altura. */
class Vision55Test {

    /** Cena 70 px por casa de 5 pes; token na casa (col, row). */
    private static SceneModel scene(boolean dark, boolean dim) {
        SceneModel s = new SceneModel();
        s.getGrid().setCellSize(70);
        s.getGrid().setFeetPerCell(5);
        s.setVisionEnabled(true);
        s.setDark(dark);
        s.setDim(dim);
        return s;
    }

    private static TokenModel token(SceneModel s, String name, int col, int row) {
        TokenModel t = new TokenModel(name, null);
        t.setX(col * 70);
        t.setY(row * 70);
        s.addToken(t);
        return t;
    }

    /** Centro da casa (col, row). */
    private static double c(int cell) {
        return cell * 70 + 35;
    }

    @Test
    void lightLevelsAndDarkvisionRule() {
        TokenModel elf = new TokenModel("Elfa", null);
        elf.setVisionFeet(60);
        assertEquals(Perception.Light.DIM, Perception.seen(Perception.Light.DARK, 30, elf), "escuridao vira penumbra");
        assertEquals(Perception.Light.BRIGHT, Perception.seen(Perception.Light.DIM, 30, elf), "penumbra vira luz plena");
        assertEquals(Perception.Light.DARK, Perception.seen(Perception.Light.DARK, 65, elf), "fora do alcance");

        TokenModel torch = new TokenModel("Tocha", null);
        torch.setLightFeet(20);
        torch.setDimLightFeet(20);
        assertEquals(Perception.Light.BRIGHT, Perception.fromSource(torch, 20));
        assertEquals(Perception.Light.DIM, Perception.fromSource(torch, 35));
        assertEquals(Perception.Light.DARK, Perception.fromSource(torch, 45));

        TokenModel devil = new TokenModel("Diabo", null);
        devil.setTruesightFeet(120);
        assertEquals(Perception.Light.BRIGHT, Perception.seen(Perception.Light.DARK, 100, devil), "visao verdadeira");
    }

    @Test
    void darkScenesWithDarkvisionTorchesAndWalls() {
        SceneModel s = scene(true, false);
        TokenModel human = token(s, "Humano", 0, 0);
        VisionCheck human0 = VisionCheck.of(s, List.of(human));
        assertFalse(human0.canSee(c(2), c(0)), "humano sem luz nao ve nada no escuro");

        TokenModel elf = token(s, "Elfa", 0, 2);
        elf.setVisionFeet(30);
        VisionCheck elfEyes = VisionCheck.of(s, List.of(elf));
        assertEquals(Perception.Light.DIM, elfEyes.seenAt(c(4), c(2)), "20 pes no escuro: penumbra pela visao no escuro");
        assertFalse(elfEyes.canSee(c(8), c(2)), "40 pes: alem do alcance");

        // tocha do humano: 20 plena + 20 penumbra; quem esta na linha de visao ve o lugar iluminado
        human.setLightFeet(20);
        human.setDimLightFeet(20);
        VisionCheck lit = VisionCheck.of(s, List.of(human));
        assertEquals(Perception.Light.BRIGHT, lit.seenAt(c(3), c(0)));
        assertEquals(Perception.Light.DIM, lit.seenAt(c(7), c(0)), "35 pes: penumbra");
        assertFalse(lit.canSee(c(10), c(0)), "50 pes: escuro");

        // parede entre o humano e a casa 3: nao ve, mesmo com a tocha
        s.getWalls().add(new Wall(140, -100, 140, 60, false));
        assertFalse(VisionCheck.of(s, List.of(human)).canSee(c(3), c(0)));
    }

    @Test
    void dimSceneTruesightBlindsightTremorsenseAndElevation() {
        SceneModel dim = scene(false, true);
        TokenModel human = token(dim, "Humano", 0, 0);
        assertEquals(Perception.Light.DIM, VisionCheck.of(dim, List.of(human)).seenAt(c(10), c(0)), "penumbra: ve, mais escuro");
        human.setVisionFeet(60);
        assertEquals(Perception.Light.BRIGHT, VisionCheck.of(dim, List.of(human)).seenAt(c(10), c(0)), "visao no escuro: luz plena");

        SceneModel dark = scene(true, false);
        TokenModel bat = token(dark, "Morcego", 0, 0);
        bat.setBlindsightFeet(60);
        TokenModel worm = token(dark, "Verme", 0, 4);
        worm.setTremorsenseFeet(60);
        TokenModel target = token(dark, "Alvo", 6, 0);   // 30 pes do morcego
        assertTrue(VisionCheck.of(dark, List.of(bat)).canSee(target), "visao as cegas no escuro");

        // voando 60 pes acima: 30 na horizontal, ~67 no total -> fora das cegas de 60
        target.setElevationFeet(60);
        assertFalse(VisionCheck.of(dark, List.of(bat)).canSee(target), "a altura conta na distancia");

        // sentido sismico: so quem esta no chao, e atravessa parede
        target.setElevationFeet(0);
        dark.getWalls().add(new Wall(0, 200, 1000, 200, false));   // parede entre o verme e o alvo
        assertTrue(VisionCheck.of(dark, List.of(worm)).canSee(target), "sente pelo chao, atras da parede");
        target.setElevationFeet(10);
        assertFalse(VisionCheck.of(dark, List.of(worm)).canSee(target), "voando, o sismico nao sente");
    }

    @Test
    void networkCopyCarriesTheNewFieldsAndOnlySafeImageIds() {
        TokenModel a = new TokenModel("A", null);
        TokenModel b = new TokenModel("A", null);
        b.setElevationFeet(30);
        b.setFacingDegrees(-90.0);
        b.setBlindsightFeet(10);
        b.setTremorsenseFeet(20);
        b.setTruesightFeet(30);
        b.setDimLightFeet(20);
        b.setRotation(45);
        b.setImageAssetId("a".repeat(64) + ".gif");
        a.copyStateFrom(b);
        assertEquals(30, a.getElevationFeet());
        assertEquals(270.0, a.getFacingDegrees());
        assertEquals(20, a.getTremorsenseFeet());
        assertEquals(45.0, a.getRotation());
        assertEquals("a".repeat(64) + ".gif", a.getImageAssetId(), "imagem nova viaja pela rede");
        b.setImageAssetId("../../etc/passwd");
        a.copyStateFrom(b);
        assertEquals("a".repeat(64) + ".gif", a.getImageAssetId(), "id estranho e ignorado");
    }
}

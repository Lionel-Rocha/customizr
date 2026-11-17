package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.ClassResources;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.GmRequest;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import com.vttcaseiro.vision.VisionCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackageATest {

    @Test
    void visaoDoServidorComEscuroELuz() {
        SceneModel scene = new SceneModel();
        scene.setVisionEnabled(true);
        scene.setDark(true);
        TokenModel hero = new TokenModel("Elora", null);
        hero.setX(0);
        hero.setY(0);
        hero.setLightFeet(20); // 4 casas = 280 px
        TokenModel near = new TokenModel("Rato", null);
        near.setX(140);
        TokenModel far = new TokenModel("Orc", null);
        far.setX(1400);
        TokenModel torch = new TokenModel("Guarda com tocha", null);
        torch.setX(1400);
        torch.setY(700);
        torch.setLightFeet(40);
        scene.addToken(hero);
        scene.addToken(near);
        scene.addToken(far);
        scene.addToken(torch);
        VisionCheck eyes = VisionCheck.of(scene, List.of(hero));
        assertTrue(eyes.canSee(near), "dentro da luz");
        assertFalse(eyes.canSee(far), "longe no escuro");
        assertTrue(eyes.canSee(torch), "a tocha aparece de longe no escuro");
        scene.getWalls().add(new Wall(700, -100, 700, 2000, false));
        assertFalse(VisionCheck.of(scene, List.of(hero)).canSee(torch), "mas nao atras da parede");
        scene.setDark(false);
        scene.getWalls().clear();
        assertTrue(VisionCheck.of(scene, List.of(hero)).canSee(far), "cena clara: ve longe");
    }

    @Test
    void recursosDaClassePorNivelMantendoGastos() {
        CharacterSheet brok = new CharacterSheet("Brok");
        brok.setClassName("Barbarian");
        brok.setLevel(1);
        assertEquals(List.of("Furia 2/2"), ClassResources.apply(brok));
        brok.findResource("Furia").orElseThrow().use();
        brok.setLevel(6);
        ClassResources.apply(brok);
        assertEquals("3/4", brok.findResource("Furia").orElseThrow().usesText(), "subiu de nivel, gasto continua gasto");
        assertEquals(1, brok.getResources().size(), "nao duplica");

        CharacterSheet kael = new CharacterSheet("Kael");
        kael.setClassName("Guerreiro");
        kael.setLevel(2);
        ClassResources.apply(kael);
        assertTrue(kael.findResource("Surto de Acao").isPresent());
        assertEquals(2, kael.findResource("Retomar o Folego").orElseThrow().getMax());

        CharacterSheet lira = new CharacterSheet("Lira");
        lira.setClassName("Bard");
        lira.setCharisma(16);
        lira.setLevel(5);
        ClassResources.apply(lira);
        var insp = lira.findResource("Inspiracao Bardica").orElseThrow();
        assertEquals(3, insp.getMax(), "CAR +3");
        assertEquals("curto", insp.getRecharge(), "Fonte de Inspiracao no nivel 5");
        assertTrue(ClassResources.forClass("Rogue", 5, lira).isEmpty());
        assertEquals(10, ClassResources.spellRounds("Bless"));
        assertEquals(10, ClassResources.spellRounds("Bênção"));
        assertTrue(ClassResources.selfOnly("Shield"));
        assertEquals(0, ClassResources.spellRounds("Fireball"));
    }

    @Test
    void conjurarLigaOEfeitoComDuracaoOuPedeProMestre(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        TokenModel a = new TokenModel("A", null);
        a.setInitiative(15);
        TokenModel b = new TokenModel("B", null);
        b.setInitiative(10);
        scenes.addToken(a);
        scenes.addToken(b);

        table.applySpellEffect(List.of(a), "Bless", 10);
        assertTrue(a.getEffects().contains("Bless"), "fora de combate: efeito simples");
        assertTrue(scenes.getScene().getEncounter().getTimedEffects().isEmpty());

        table.startEncounter();
        table.applySpellEffect(List.of(b), "Shield", 1);
        assertTrue(b.getEffects().contains("Shield"));
        assertEquals(1, scenes.getScene().getEncounter().getTimedEffects().size(), "em combate: com duracao");
        table.nextTurn();
        table.nextTurn();
        assertFalse(b.getEffects().contains("Shield"), "Escudo sai sozinho na rodada seguinte");

        port.gm = false;
        port.connected = true;
        table.applySpellEffect(List.of(a, b), "Bless", 10);
        GmRequest req = port.gmRequests.get(0);
        assertEquals("effect", req.getKind());
        assertEquals("Bless", req.getLabel());
        assertEquals(10, req.getAmount());

        port.gm = true;
        port.connected = false;
        a.setEffects(List.of());
        table.handleGmRequest(req, "Ana");
        assertTrue(a.getEffects().contains("Bless"), "mestre aplica o pedido");
    }
}

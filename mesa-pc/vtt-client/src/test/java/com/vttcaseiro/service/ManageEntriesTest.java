package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.service.TokenLink;
import com.vttcaseiro.view.MobEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Apagar, renomear e duplicar fichas, monstros e cenas; monstro feito do zero no editor. */
class ManageEntriesTest {

    @TempDir
    Path dir;

    @Test
    void charactersRenameDuplicateDelete() throws Exception {
        LibraryStorage repo = new LibraryStorage(dir);
        CharacterService chars = new CharacterService(repo);
        CharacterSheet ana = chars.create("Ana");
        chars.create("Bia");

        CharacterSheet renamed = chars.rename("Ana", "Ana Clara", null);
        assertEquals(ana.getId(), renamed.getId());
        assertEquals(List.of("Ana Clara", "Bia"), chars.list());
        assertThrows(java.io.IOException.class, () -> chars.rename("Bia", "Ana Clara", null), "nome ja usado");
        assertEquals(List.of("Ana Clara", "Bia"), chars.list(), "nada mudou");

        CharacterSheet copy = chars.duplicate("Bia", "Bia 2");
        assertNotEquals(chars.open("Bia").getId(), copy.getId(), "copia e outro personagem");
        assertEquals(List.of("Ana Clara", "Bia", "Bia 2"), chars.list());

        chars.delete("Bia");
        assertEquals(List.of("Ana Clara", "Bia 2"), chars.list());
        assertThrows(java.io.IOException.class, () -> chars.delete("Bia"));
    }

    @Test
    void mobsRenameCloneDelete() throws Exception {
        LibraryStorage repo = new LibraryStorage(dir);
        MobService mobs = new MobService(repo);
        MobSheet goblin = mobs.create("Goblin");
        goblin.setArmorClass(15);
        mobs.save(goblin);

        assertEquals("Goblin (2)", mobs.freeCopyName("Goblin"));
        MobSheet chief = mobs.duplicate("Goblin", "Goblin chefe");
        assertEquals(15, chief.getArmorClass(), "copia leva tudo");
        assertNotEquals(goblin.getId(), chief.getId());
        assertThrows(IllegalArgumentException.class, () -> mobs.duplicate("Goblin", "Goblin chefe"));

        mobs.rename("Goblin chefe", "Chefe goblin");
        assertEquals(List.of("Chefe goblin", "Goblin"), mobs.list());
        assertEquals("Chefe goblin", mobs.open("Chefe goblin").getName());

        mobs.delete("Goblin");
        assertEquals(List.of("Chefe goblin"), mobs.list());
    }

    @Test
    void scenesRenameKeepsPinLinksAndDeleteUnlinks() throws Exception {
        LibraryStorage repo = new LibraryStorage(dir);
        MesaService mesa = new MesaService(repo);
        mesa.saveMap(new SceneModel(), "Taverna");
        SceneModel world = new SceneModel();
        MapPin pin = new MapPin(10, 10, "Taverna do Pônei", "taverna");
        pin.setLinkedScene("Taverna");
        world.getPins().add(pin);
        mesa.saveMap(world, "Mundo");

        assertEquals("Taverna nova", mesa.renameMap("Taverna", "Taverna nova"));
        assertEquals(List.of("Mundo", "Taverna nova"), mesa.listMaps());
        assertEquals("Taverna nova", mesa.loadMap("Mundo").getPins().get(0).getLinkedScene(), "pin segue a cena");
        assertThrows(IllegalArgumentException.class, () -> mesa.renameMap("Mundo", "Taverna nova"));

        assertEquals("Mundo 2", mesa.duplicateMap("Mundo", "Mundo 2"));
        assertEquals(1, mesa.loadMap("Mundo 2").getPins().size());

        mesa.deleteMap("Taverna nova");
        assertEquals(List.of("Mundo", "Mundo 2"), mesa.listMaps());
        assertNull(mesa.loadMap("Mundo").getPins().get(0).getLinkedScene(), "pin vira so informacao");
    }

    @Test
    void editorTextRoundTrips() {
        List<CharacterTrait> parsed = MobEditor.parseEntries(
                "Mordida. Ataque corpo a corpo: +4 para acertar. Acerto: 6 (1d8 + 2) de dano perfurante.\n\n"
                        + "Faro Aguçado. Vantagem em Percepção.\n\n\nTexto sem nome que é bem comprido e não tem ponto final",
                "action");
        assertEquals(3, parsed.size());
        assertEquals("Mordida", parsed.get(0).getName());
        assertTrue(parsed.get(0).getDescription().startsWith("Ataque corpo a corpo: +4"));
        assertEquals("Ação", parsed.get(2).getName());
        assertEquals(parsed.size(), MobEditor.parseEntries(MobEditor.toText(parsed), "action").size());
        assertEquals("Mordida", MobEditor.parseEntries(MobEditor.toText(parsed), "action").get(0).getName());
        assertEquals(4, TokenLink.cellsForSize("Gigantesco"), "Gigantesco do 5etools = 4 casas");
    }
}

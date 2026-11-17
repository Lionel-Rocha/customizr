package com.vttcaseiro.library;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.party.Party;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LibraryStorageTest {

    @Test
    void criaAsDuasPastasAoConstruir(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        assertTrue(Files.isDirectory(lib.getMapsDir()));
        assertTrue(Files.isDirectory(lib.getCharactersDir()));
    }

    @Test
    void nomeDaMesaEhONomeDaPastaRaiz(@TempDir Path tempDir) throws IOException {
        Path mesaFolder = tempDir.resolve("Campanha dos Herois");
        LibraryStorage lib = new LibraryStorage(mesaFolder);
        assertEquals("Campanha dos Herois", lib.getName());
        assertEquals(mesaFolder, lib.getRoot());
    }

    @Test
    void salvaListaECarregaUmMapa(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        SceneModel scene = new SceneModel();
        scene.setName("Masmorra do Lich");
        TokenModel token = new TokenModel("Esqueleto", "/img/esqueleto.png");
        scene.addToken(token);

        lib.saveMap(scene, "masmorra-do-lich");

        List<String> maps = lib.listMaps();
        assertEquals(1, maps.size());
        assertEquals("masmorra-do-lich", maps.get(0));

        SceneModel loaded = lib.loadMap("masmorra-do-lich");
        assertEquals("Masmorra do Lich", loaded.getName());
        assertEquals(1, loaded.getTokens().size());
        assertEquals("Esqueleto", loaded.getTokens().get(0).getName());
    }

    @Test
    void salvaListaECarregaUmaFicha(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        CharacterSheet sheet = new CharacterSheet("Aria");
        sheet.setClassName("Ranger");
        sheet.setLevel(5);
        sheet.addTrait(new CharacterTrait("Tiro Certeiro", "Vantagem em ataques a distancia.", "feature"));

        lib.saveCharacter(sheet, "aria");

        List<String> characters = lib.listCharacters();
        assertEquals(1, characters.size());
        assertEquals("aria", characters.get(0));

        CharacterSheet loaded = lib.loadCharacter("aria");
        assertEquals("Aria", loaded.getName());
        assertEquals("Ranger", loaded.getClassName());
        assertEquals(5, loaded.getLevel());
        assertEquals(1, loaded.getTraits().size());
        assertEquals("Tiro Certeiro", loaded.getTraits().get(0).getName());
    }

    @Test
    void mapasEFichasNaoSeMisturam(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        lib.saveMap(new SceneModel(), "mapa1");
        lib.saveCharacter(new CharacterSheet("Personagem1"), "ficha1");

        assertEquals(List.of("mapa1"), lib.listMaps());
        assertEquals(List.of("ficha1"), lib.listCharacters());
    }

    @Test
    void mesaNovaNaoTemPartySalva(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        assertTrue(lib.loadParty().isEmpty());
    }

    @Test
    void salvaECarregaAPartyComInventarioCompartilhado(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        Party party = new Party("Os Heróis de Vale Sombrio");
        party.addMember("char-1");
        party.addMember("char-2");
        party.getSharedInventory().getMoney().add(Currency.GOLD, 50);
        party.getSharedInventory().addItem("Corda (15m)", 2, 10, 100);

        lib.saveParty(party);

        Optional<Party> loaded = lib.loadParty();
        assertTrue(loaded.isPresent());
        assertEquals("Os Heróis de Vale Sombrio", loaded.get().getName());
        assertEquals(2, loaded.get().getCharacterIds().size());
        assertTrue(loaded.get().hasMember("char-1"));
        assertEquals(50, loaded.get().getSharedInventory().getMoney().get(Currency.GOLD));
        assertEquals(2, loaded.get().getSharedInventory().findByName("Corda (15m)").get().getQuantity());
    }

    @Test
    void nomeComCaracterEstranhoVaraValidoNoDisco(@TempDir Path tempDir) throws IOException {
        LibraryStorage lib = new LibraryStorage(tempDir);
        lib.saveMap(new SceneModel(), "Masmorra: Nivel 2 / Secreto?");
        List<String> maps = lib.listMaps();
        assertEquals(1, maps.size());
        // nao importa o nome exato que o sanitizador escolheu, importa que
        // deu pra salvar e listar sem explodir com caracteres invalidos de arquivo
        assertDoesNotThrow(() -> lib.loadMap(maps.get(0)));
    }
}

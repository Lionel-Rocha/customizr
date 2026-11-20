package com.vttcaseiro.character;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.service.CharacterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Bastiao na ficha: sempre 11 linhas basicas e 6 instalacoes especiais, valores conhecidos, salva com a ficha. */
class BastionTest {

    @Test
    void shapeAndKnownValues() {
        Bastion b = new Bastion();
        assertEquals(Bastion.BASIC_LINES, b.getBasicFacilities().size());
        assertEquals(Bastion.SPECIAL_SLOTS, b.getSpecialFacilities().size());
        Bastion.SpecialFacility f = b.getSpecialFacilities().get(0);
        f.setSpace("Espaçoso");
        f.setOrder("Pesquisar");
        assertEquals("roomy", f.getSpace(), "guarda a chave");
        assertEquals("research", f.getOrder());
        f.setOrder("Dominar o mundo");
        assertNull(f.getOrder(), "ordem desconhecida fica vazia");
        assertEquals("Vasto", Bastion.label(Bastion.SPACES, "vast"));
        assertTrue(new Bastion.SpecialFacility().isEmpty());
    }

    @Test
    void savedAndLoadedWithTheSheet(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        CharacterSheet sheet = chars.create("Elora");
        Bastion b = sheet.getBastion();
        b.setName("Torre do Corvo");
        b.setDefenders("4 guardas");
        b.setBasicFacility(0, "Quarto (Espaçoso)");
        Bastion.SpecialFacility lib = b.getSpecialFacilities().get(2);
        lib.setName("Biblioteca");
        lib.setSpace("roomy");
        lib.setOrder("research");
        lib.setHirelings("Mestre Oren");
        lib.setNotes("Pesquisando o dragão");
        chars.save(sheet);

        CharacterService again = new CharacterService(new LibraryStorage(dir));
        Bastion loaded = again.findById(sheet.getId()).orElseThrow().getBastion();
        assertEquals("Torre do Corvo", loaded.getName());
        assertEquals("4 guardas", loaded.getDefenders());
        assertEquals("Quarto (Espaçoso)", loaded.getBasicFacilities().get(0));
        assertEquals("Biblioteca", loaded.getSpecialFacilities().get(2).getName());
        assertEquals("research", loaded.getSpecialFacilities().get(2).getOrder());
        assertEquals(Bastion.SPECIAL_SLOTS, loaded.getSpecialFacilities().size());
    }
}

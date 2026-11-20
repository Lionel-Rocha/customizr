package com.vttcaseiro.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pastas das cenas: criar, renomear (leva subpastas e cenas), apagar (cenas sobem), mover, guardar. */
class SceneFoldersTest {

    @Test
    void foldersOrganizeScenesWithoutTouchingThem(@TempDir Path mesa) throws Exception {
        SceneFolders f = new SceneFolders();
        String city = f.addFolder("", "Cidade");
        String tavern = f.addFolder(city, "Taverna");
        f.addFolder("", "Masmorra");
        assertEquals("Cidade/Taverna", tavern);
        assertEquals(List.of("Cidade", "Masmorra"), f.subfolders(""));
        assertEquals(List.of("Cidade/Taverna"), f.subfolders("Cidade"));

        f.moveScene("Salão do Pônei", tavern);
        f.moveScene("Mapa da cidade", city);
        assertEquals("Cidade/Taverna", f.folderOf("Salão do Pônei"));
        assertEquals("", f.folderOf("Cena solta"), "sem pasta = raiz");

        assertEquals("Vila", f.renameFolder(city, "Vila"));
        assertEquals("Vila/Taverna", f.folderOf("Salão do Pônei"), "renomear leva as cenas das subpastas");
        assertEquals(List.of("Vila/Taverna"), f.subfolders("Vila"));

        f.save(mesa);
        SceneFolders back = SceneFolders.load(mesa);
        assertEquals("Vila/Taverna", back.folderOf("Salão do Pônei"), "guarda no arquivo da mesa");

        back.removeFolder("Vila");
        assertEquals("", back.folderOf("Salão do Pônei"), "apagar a pasta: a cena volta pra raiz");
        assertEquals("", back.folderOf("Mapa da cidade"));
        assertEquals(List.of("Masmorra"), back.folders());

        back.renameScene("Cena solta", "Cena nova");
        back.moveScene("Cena nova", "Masmorra");
        back.renameScene("Cena nova", "Cena renomeada");
        assertEquals("Masmorra", back.folderOf("Cena renomeada"));
    }

    @Test
    void refusesBadNamesAndSurvivesABrokenFile(@TempDir Path mesa) throws Exception {
        SceneFolders f = new SceneFolders();
        assertThrows(IllegalArgumentException.class, () -> f.addFolder("", "a/b"));
        assertThrows(IllegalArgumentException.class, () -> f.addFolder("", " "));
        assertThrows(IllegalArgumentException.class, () -> f.addFolder("", ".."));
        assertThrows(IllegalArgumentException.class, () -> f.addFolder("Nao existe", "x"));
        f.addFolder("", "Cidade");
        assertThrows(IllegalArgumentException.class, () -> f.addFolder("", "cidade"), "repetida (sem diferenca de maiuscula)");
        assertThrows(IllegalArgumentException.class, () -> f.moveScene("Cena", "Nao existe"));

        Files.writeString(mesa.resolve(SceneFolders.FILE), "{ isso nao e json");
        assertTrue(SceneFolders.load(mesa).folders().isEmpty(), "arquivo estragado: comeca sem pastas");
        Files.writeString(mesa.resolve(SceneFolders.FILE), "{\"folders\":[\"ok\",\"../fora\",\"\"]}");
        assertEquals(List.of("ok"), SceneFolders.load(mesa).folders(), "caminho estranho fica de fora");
    }
}

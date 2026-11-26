package com.vttcaseiro.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Aviso de versao nova: le a versao gravada ao lado do programa e compara com a resposta do GitHub. */
class UpdateCheckTest {

    static final String SHA_A = "a".repeat(40), SHA_B = "b".repeat(40);

    @Test
    void readsTheStampWrittenByTheLauncher(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("versao.txt");
        Files.writeString(f, SHA_A + " 2026-11-26T14:14:18-03:00\r\n");
        UpdateCheck.Version v = UpdateCheck.parseLocal(f).orElseThrow();
        assertEquals(SHA_A, v.commit());
        assertEquals(OffsetDateTime.parse("2026-11-26T14:14:18-03:00"), v.date());
        assertTrue(UpdateCheck.parseLocal(dir.resolve("nao-existe.txt")).isEmpty());
        Files.writeString(f, "lixo");
        assertTrue(UpdateCheck.parseLocal(f).isEmpty());
    }

    @Test
    void comparesWithTheLatestCommitFromGitHub() {
        String api = "[{\"sha\":\"" + SHA_B + "\",\"commit\":{\"message\":\"Mesa: coisa nova\n\nCo-Authored-By: x\","
                + "\"committer\":{\"date\":\"2026-11-27T10:00:00Z\"}}}]";
        UpdateCheck.Version latest = UpdateCheck.parseLatest(api).orElseThrow();
        assertEquals("Mesa: coisa nova", latest.message());
        UpdateCheck.Version mine = new UpdateCheck.Version(SHA_A, OffsetDateTime.parse("2026-11-26T14:14:18-03:00"), null);
        assertTrue(UpdateCheck.isNewer(mine, latest));
        assertFalse(UpdateCheck.isNewer(new UpdateCheck.Version(SHA_B, latest.date(), null), latest), "mesmo commit");
        assertFalse(UpdateCheck.isNewer(new UpdateCheck.Version(SHA_A, OffsetDateTime.parse("2026-12-01T00:00:00Z"), null), latest),
                "programa montado de um commit mais novo (ex.: local, ainda sem enviar)");
        assertTrue(UpdateCheck.parseLatest("{\"message\":\"API rate limit exceeded\"}").isEmpty());
        assertTrue(UpdateCheck.parseLatest("[]").isEmpty());
    }
}

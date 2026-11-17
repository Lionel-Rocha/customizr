package com.vttcaseiro.importer;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.GameSystem;
import com.vttcaseiro.model.Fog;
import com.vttcaseiro.model.GridSettings;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemImportTest {

    @Test
    void pdfDeOrdemParanormal(@TempDir Path dir) throws Exception {
        String[][] fields = {{"Nome", "Dante"}, {"Classe", "Especialista"}, {"NEX", "25"}, {"Origem", "Investigador"},
                {"AGI", "3"}, {"FOR", "1"}, {"INT", "2"}, {"PRE", "2"}, {"VIG", "1"}, {"PV", "22"}, {"PE", "8"},
                {"SAN", "20"}, {"Defesa", "14"}, {"Pontaria", "10"}, {"Ocultismo", "5"}, {"Atletismo", "0"},
                {"Arma1", "Pistola"}, {"Teste1", "Pontaria"}, {"Dano1", "1d12"}, {"Critico1", "18/x2"}};
        Path pdf = dir.resolve("ordem.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources res = new PDResources();
            res.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(res);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");
            int y = 760;
            for (String[] kv : fields) {
                PDTextField f = new PDTextField(form);
                f.setPartialName(kv[0]);
                PDAnnotationWidget w = f.getWidgets().get(0);
                w.setRectangle(new PDRectangle(40, y, 200, 14));
                w.setPage(page);
                page.getAnnotations().add(w);
                form.getFields().add(f);
                f.setValue(kv[1]);
                y -= 18;
            }
            doc.save(pdf.toFile());
        }
        SheetImport.Result r = SheetImport.fromFile(pdf);
        CharacterSheet s = r.sheet();
        assertSame(GameSystem.ORDEM, GameSystem.of(s), r.format());
        assertEquals("Dante", s.getName());
        assertEquals(3, s.getCustomAttributes().get("AGI"));
        assertEquals("25%", s.getCustomFields().get("NEX"));
        assertEquals(22, s.getHpMax());
        assertEquals(8, s.findResource("PE").orElseThrow().getMax());
        assertEquals(20, s.findResource("SAN").orElseThrow().getMax());
        assertEquals(14, s.getArmorClass());
        assertEquals(2, s.getCustomSkills().get("Pontaria"), "10 = veterano");
        assertEquals(1, s.getCustomSkills().get("Ocultismo"), "5 = treinado");
        assertNull(s.getCustomSkills().get("Atletismo"));
        assertEquals("3d20kh1+10", GameSystem.ORDEM.attackTest(s, s.getAttacks().get(0)));
    }

    @Test
    void camposDeTormenta20() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Nome do Personagem", "Aldric");
        f.put("Raça", "Humano");
        f.put("Classe", "Guerreiro");
        f.put("Nível", "5");
        f.put("Divindade", "Khalmyr");
        f.put("Força", "16");
        f.put("Destreza", "12");
        f.put("Constituição", "14");
        f.put("Inteligência", "10");
        f.put("Sabedoria", "13");
        f.put("Carisma", "8");
        f.put("PV", "44");
        f.put("PM", "15");
        f.put("Defesa", "19");
        f.put("Luta", "7");        // 2 (metade do nivel) + 3 (FOR) + 2 = treinado
        f.put("Furtividade", "3"); // 2 + 1 = destreinado
        f.put("Ladinagem", "3");
        SheetImport.Result r = SystemPdfMapper.map(f, "teste");
        assertNotNull(r);
        CharacterSheet s = r.sheet();
        assertSame(GameSystem.TORMENTA20, GameSystem.of(s));
        assertEquals(5, s.getLevel());
        assertEquals("Humano", s.getCustomFields().get("Raça"));
        assertEquals("Khalmyr", s.getCustomFields().get("Divindade"));
        assertEquals(16, s.getCustomAttributes().get("FOR"));
        assertEquals(15, s.findResource("PM").orElseThrow().getMax());
        assertEquals(19, s.getArmorClass());
        assertEquals(1, s.getCustomSkills().get("Luta"));
        assertNull(s.getCustomSkills().get("Furtividade"));
    }

    @Test
    void fichaDeDndNaoViraOutroSistema() {
        Map<String, String> f = Map.of("charactername", "Kael", "str", "8", "dex", "14", "race", "Elf", "ac", "12");
        assertNull(SystemPdfMapper.detect(f));
    }

    @Test
    void neblinaEmHexagonos() {
        GridSettings g = new GridSettings();
        g.setType(GridSettings.HEX);
        double[] a = g.hexCenter(1, 1), b = g.hexCenter(3, 1);
        List<int[]> cells = g.hexesInRect(a[0] - 5, a[1] - 5, b[0] + 5, b[1] + 5);
        assertEquals(3, cells.size(), "os tres hexagonos da linha com o centro dentro do retangulo");
        Fog fog = new Fog();
        fog.setCells(cells, true);
        assertTrue(fog.isRevealed(2, 1));
        assertFalse(fog.isRevealed(2, 2));
        fog.setCells(List.of(new int[]{-1, 4}), true);
        assertTrue(fog.isRevealed(-1, 4), "hexagono com coordenada negativa tambem vale");
        assertEquals(1, g.hexesInRect(a[0], a[1], a[0] + 1, a[1] + 1).size(), "clique simples = o hexagono do ponto");
        assertTrue(g.hexesCovering(700, 700).size() > 80);
    }
}

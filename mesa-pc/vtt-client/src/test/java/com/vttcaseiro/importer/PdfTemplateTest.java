package com.vttcaseiro.importer;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.library.TemplateStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.service.TemplateSync;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** PDF editavel vira modelo de ficha: paginas como imagem, campos com posicao, papeis, PDF preenchido de volta. */
public class PdfTemplateTest {

    @TempDir
    Path dir;

    /** Uma "ficha oficial" de mentira: A4 com 5 campos de texto, uma caixa e uma lista. */
    public static byte[] fillablePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDResources res = new PDResources();
            res.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(res);
            form.setDefaultAppearance("/Helv 10 Tf 0 g");
            String[][] texts = {{"Nome do Personagem", "50", "780"}, {"PV Atual", "50", "740"}, {"PV Maximo", "200", "740"},
                    {"CA", "350", "740"}, {"Iniciativa", "450", "740"}, {"Historia", "50", "500"}};
            for (String[] t : texts) {
                PDTextField f = new PDTextField(form);
                f.setPartialName(t[0]);
                f.setDefaultAppearance("/Helv 10 Tf 0 g");
                if (t[0].equals("Historia")) f.setMultiline(true);
                addWidget(f, page, Float.parseFloat(t[1]), Float.parseFloat(t[2]), 120, t[0].equals("Historia") ? 120 : 18);
                form.getFields().add(f);
            }
            PDCheckBox inspiration = new PDCheckBox(form);
            inspiration.setPartialName("Inspiracao");
            addWidget(inspiration, page, 50, 700, 12, 12);
            form.getFields().add(inspiration);
            PDComboBox cls = new PDComboBox(form);
            cls.setPartialName("Classe");
            cls.setDefaultAppearance("/Helv 10 Tf 0 g");
            cls.setOptions(List.of("Guerreiro", "Mago", "Ladino"));
            addWidget(cls, page, 200, 700, 120, 18);
            form.getFields().add(cls);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void addWidget(PDField f, PDPage page, float x, float y, float w, float h) throws Exception {
        PDAnnotationWidget widget = f.getWidgets().get(0);
        widget.setRectangle(new PDRectangle(x, y, w, h));
        widget.setPage(page);
        page.getAnnotations().add(widget);
    }

    @Test
    void readsFieldsPositionsAndGuessesRoles() throws Exception {
        ImageAssetStore store = new ImageAssetStore(dir);
        SheetTemplate t = PdfTemplateReader.read(fillablePdf(), "Ficha de teste", SheetTemplate.CHARACTER, store);
        assertEquals(1, t.getPages().size());
        assertTrue(store.exists(t.getPages().get(0).assetId), "a página virou imagem no acervo da mesa");
        assertEquals(PDRectangle.A4.getHeight(), t.getPages().get(0).height, 0.5);
        assertEquals(8, t.getFields().size());

        SheetTemplate.Field name = t.findField("Nome do Personagem");
        assertEquals(50, name.x, 0.5);
        // y do PDF conta de baixo pra cima: no modelo, de cima pra baixo
        assertEquals(PDRectangle.A4.getHeight() - 798, name.y, 0.5);
        assertEquals(10, name.fontSize, 0.01);
        assertTrue(t.findField("Historia").multiline);
        assertEquals("caixa", t.findField("Inspiracao").type);
        assertEquals(List.of("Guerreiro", "Mago", "Ladino"), t.findField("Classe").options);

        Map<String, String> roles = t.getRoles();
        assertEquals("Nome do Personagem", roles.get(SheetTemplate.ROLE_NAME));
        assertEquals("PV Atual", roles.get(SheetTemplate.ROLE_HP));
        assertEquals("PV Maximo", roles.get(SheetTemplate.ROLE_HP_MAX));
        assertEquals("CA", roles.get(SheetTemplate.ROLE_AC));
        assertEquals("Iniciativa", roles.get(SheetTemplate.ROLE_INIT));
    }

    @Test
    void fillsThePdfBackAndKeepsTheStore() throws Exception {
        byte[] pdf = fillablePdf();
        SheetTemplate t = PdfTemplateReader.read(pdf, "Ficha", SheetTemplate.CHARACTER, new ImageAssetStore(dir));
        TemplateStore ts = new TemplateStore(dir);
        ts.save(t, pdf);
        assertEquals(1, ts.list(SheetTemplate.CHARACTER).size());
        assertTrue(ts.list(SheetTemplate.MOB).isEmpty());
        assertNotNull(ts.pdf(t.getId()));

        List<String> skipped = new ArrayList<>();
        byte[] filled = PdfTemplateReader.fill(pdf, Map.of("Nome do Personagem", "Lyra", "PV Atual", "12",
                "Inspiracao", "sim", "Classe", "Mago"), skipped);
        assertTrue(skipped.isEmpty(), skipped.toString());
        try (PDDocument doc = Loader.loadPDF(filled)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertEquals("Lyra", form.getField("Nome do Personagem").getValueAsString());
            assertEquals("12", form.getField("PV Atual").getValueAsString());
            assertTrue(((PDCheckBox) form.getField("Inspiracao")).isChecked());
            assertEquals(List.of("Mago"), ((PDComboBox) form.getField("Classe")).getValue());
        }
        ts.delete(t.getId());
        assertTrue(ts.list().isEmpty());
    }

    @Test
    void rolesDriveTheTokenDataBothWays() throws Exception {
        SheetTemplate t = PdfTemplateReader.read(fillablePdf(), "Ficha", SheetTemplate.CHARACTER, new ImageAssetStore(dir));
        CharacterSheet s = new CharacterSheet("Sem nome");
        s.setTemplate(t);
        s.getTemplateValues().put("Nome do Personagem", "Lyra");
        s.getTemplateValues().put("PV Maximo", "24");
        s.getTemplateValues().put("PV Atual", "20");
        s.getTemplateValues().put("CA", "16 (cota de malha)");
        s.getTemplateValues().put("Iniciativa", "+3");
        TemplateSync.toCharacter(s);
        assertEquals("Lyra", s.getName());
        assertEquals(24, s.getHpMax());
        assertEquals(20, s.getHpCurrent());
        assertEquals(16, s.getArmorClass());
        assertEquals(3, s.initiative(), "a iniciativa do PDF vale no rastreador");

        s.setHpCurrent(7); // dano no token
        assertTrue(TemplateSync.fromCharacter(s));
        assertEquals("7", s.getTemplateValues().get("PV Atual"), "o PV volta pro PDF");
        assertFalse(TemplateSync.fromCharacter(s));

        MobSheet m = new MobSheet("x");
        m.setTemplate(t);
        m.getTemplateValues().put("Nome do Personagem", "Ogro da ponte");
        m.getTemplateValues().put("PV Maximo", "59");
        m.getTemplateValues().put("CA", "11");
        TemplateSync.toMob(m);
        assertEquals("Ogro da ponte", m.getName());
        assertEquals(59, m.getHitPointsAverage());
        assertEquals(11, m.getArmorClass());
    }

    @Test
    void pdfWithoutFormIsRejected() throws Exception {
        byte[] plain;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            plain = out.toByteArray();
        }
        java.io.IOException ex = assertThrows(java.io.IOException.class,
                () -> PdfTemplateReader.read(plain, "x", SheetTemplate.CHARACTER, new ImageAssetStore(dir)));
        assertTrue(ex.getMessage().contains("preenchível"));
    }
}

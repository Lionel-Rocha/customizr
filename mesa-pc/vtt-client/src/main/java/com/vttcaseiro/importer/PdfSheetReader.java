package com.vttcaseiro.importer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ficha em PDF: le os campos preenchiveis (AcroForm); sem campos, tenta pelo texto. */
public final class PdfSheetReader {
    private PdfSheetReader() {
    }

    public static SheetImport.Result read(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
            Map<String, String> fields = fields(doc);
            if (!fields.isEmpty()) {
                // Tormenta20 / Ordem Paranormal primeiro (reconhecidos pelos nomes dos campos)
                SheetImport.Result sys = SystemPdfMapper.map(fields, "PDF preenchivel (" + fields.size() + " campos)");
                if (sys != null) return sys;
                SheetImport.Result r = PdfFieldMapper.map(fields, "PDF preenchivel (" + fields.size() + " campos)");
                if (r != null) return r;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(4, doc.getNumberOfPages()));
            SheetImport.Result r = PdfFieldMapper.fromText(stripper.getText(doc));
            if (r != null) return r;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            throw new IOException("O PDF tem senha; tire a senha e tente de novo.");
        }
        throw new IOException("Não consegui ler a ficha desse PDF. Funciona com PDF preenchivel (ficha oficial de 5e, "
                + "D&D Beyond \"Exportar para PDF\" e parecidas). PDF escaneado ou \"impresso como imagem\" não tem "
                + "os dados dentro; nesse caso, use JSON ou crie a ficha na mão.");
    }

    static Map<String, String> fields(PDDocument doc) {
        Map<String, String> out = new LinkedHashMap<>();
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form == null) return out;
        for (PDField field : form.getFieldTree()) {
            String name = field.getFullyQualifiedName();
            if (name == null) continue;
            try {
                if (field instanceof PDCheckBox cb) {
                    out.put(name, cb.isChecked() ? "Yes" : "Off");
                } else {
                    String v = field.getValueAsString();
                    if (v != null && !v.isBlank()) out.put(name, v);
                }
            } catch (RuntimeException ignored) {
                // campo estranho: pula
            }
        }
        return out;
    }
}

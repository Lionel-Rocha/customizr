package com.vttcaseiro.importer;

import com.vttcaseiro.character.SheetTemplate;
import com.vttcaseiro.library.ImageAssetStore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF editavel -> modelo de ficha ({@link SheetTemplate}) e o caminho de volta (PDF preenchido com os valores).
 */
public final class PdfTemplateReader {
    /** Resolucao das imagens das paginas (pontos * escala = pixels). */
    static final float RENDER_SCALE = 1.6f;

    private PdfTemplateReader() {
    }

    /**
     * Le o PDF: guarda cada pagina como imagem no acervo da mesa e anota os campos do formulario com a posicao.
     *
     * @throws IOException PDF sem campos editaveis, com senha, ou ilegivel
     */
    public static SheetTemplate read(byte[] pdf, String name, String kind, ImageAssetStore store) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            if (form == null || !form.getFieldIterator().hasNext()) {
                throw new IOException("Esse PDF não tem campos editáveis. Use a versão \"preenchível\" da ficha (a que dá "
                        + "pra digitar dentro no leitor de PDF).");
            }
            int pageCount = Math.min(doc.getNumberOfPages(), SheetTemplate.MAX_PAGES);
            SheetTemplate t = new SheetTemplate();
            t.setName(name);
            t.setKind(kind);
            PDFRenderer renderer = new PDFRenderer(doc);
            renderer.setSubsamplingAllowed(true);
            Map<org.apache.pdfbox.cos.COSDictionary, Integer> pageIndex = new IdentityHashMap<>(); // por objeto interno: getPage() cria um PDPage novo a cada chamada
            for (int i = 0; i < pageCount; i++) {
                PDPage page = doc.getPage(i);
                pageIndex.put(page.getCOSObject(), i);
                PDRectangle box = page.getCropBox();
                BufferedImage img = renderer.renderImage(i, RENDER_SCALE, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                byte[] bytes = out.toByteArray();
                if (bytes.length > 3_900_000) bytes = jpeg(img);
                SheetTemplate.Page p = new SheetTemplate.Page();
                p.assetId = store.store(bytes, bytes.length > 0 && bytes[0] == (byte) 0x89 ? "pagina.png" : "pagina.jpg");
                p.width = box.getWidth();
                p.height = box.getHeight();
                t.getPages().add(p);
            }
            for (PDField field : form.getFieldTree()) {
                if (field instanceof PDPushButton || field instanceof PDSignatureField) continue;
                if (field.getFullyQualifiedName() == null) continue;
                if (!(field instanceof PDTextField || field instanceof PDButton || field instanceof PDChoice)) continue;
                List<PDAnnotationWidget> widgets = field.getWidgets();
                if (widgets.isEmpty()) continue;
                // um campo pode aparecer em mais de um lugar (ex.: nome no topo de cada pagina): cada lugar vira um
                // controle; todos editam o mesmo valor
                for (PDAnnotationWidget w : widgets) {
                    Integer pi = w.getPage() == null ? null : pageIndex.get(w.getPage().getCOSObject());
                    if (pi == null) pi = findPage(doc, w, pageCount);
                    if (pi == null || pi < 0 || pi >= pageCount) continue;
                    PDRectangle r = w.getRectangle();
                    if (r == null || r.getWidth() <= 0 || r.getHeight() <= 0) continue;
                    PDRectangle crop = doc.getPage(pi).getCropBox();
                    SheetTemplate.Field f = new SheetTemplate.Field();
                    f.name = field.getFullyQualifiedName();
                    String tip = field.getAlternateFieldName();
                    f.label = tip != null && !tip.isBlank() ? tip.trim() : prettify(field.getPartialName());
                    f.page = pi;
                    f.x = r.getLowerLeftX() - crop.getLowerLeftX();
                    f.y = crop.getUpperRightY() - r.getUpperRightY();
                    f.w = r.getWidth();
                    f.h = r.getHeight();
                    if (field instanceof PDCheckBox) {
                        f.type = "caixa";
                        f.defaultValue = ((PDCheckBox) field).isChecked() ? "sim" : "";
                    } else if (field instanceof PDRadioButton radio) {
                        f.type = "lista";
                        f.options.addAll(radio.getExportValues());
                        f.defaultValue = radio.getValueAsString();
                    } else if (field instanceof PDChoice choice) {
                        f.type = "lista";
                        f.options.addAll(choice.getOptionsDisplayValues());
                        List<String> chosen = choice.getValue(); // getValueAsString() devolve "[x]"
                        f.defaultValue = chosen == null || chosen.isEmpty() ? "" : chosen.get(0);
                    } else if (field instanceof PDTextField tf) {
                        f.multiline = tf.isMultiline();
                        f.defaultValue = tf.getValueAsString();
                        f.fontSize = fontSizeOf(tf.getDefaultAppearance());
                    }
                    if (f.defaultValue == null || "Off".equals(f.defaultValue)) f.defaultValue = "";
                    t.getFields().add(f);
                    if (t.getFields().size() >= SheetTemplate.MAX_FIELDS) break;
                }
            }
            if (t.getFields().isEmpty()) throw new IOException("Não achei campos de digitar nesse PDF.");
            t.guessRoles();
            return t.sanitize();
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            throw new IOException("O PDF tem senha; tire a senha e tente de novo.");
        }
    }

    private static Integer findPage(PDDocument doc, PDAnnotationWidget w, int pageCount) throws IOException {
        for (int i = 0; i < pageCount; i++) {
            for (PDAnnotation a : doc.getPage(i).getAnnotations()) {
                if (a.getCOSObject() == w.getCOSObject()) return i;
            }
        }
        return null;
    }

    /** "CharacterName" / "nome_personagem" -> "Character Name" / "nome personagem". */
    static String prettify(String s) {
        if (s == null) return "";
        return s.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    /** "/Helv 9 Tf 0 g" -> 9. 0 = automatico. */
    static double fontSizeOf(String da) {
        if (da == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9.]+)\\s+Tf").matcher(da);
        if (!m.find()) return 0;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /**
     * O PDF original preenchido com os valores da ficha (pra imprimir ou mandar). Campo que nao aceita o texto
     * (letra que a fonte do PDF nao tem) fica em branco e entra na lista {@code skipped}.
     */
    public static byte[] fill(byte[] pdf, Map<String, String> values, List<String> skipped) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            if (form == null) throw new IOException("O PDF do modelo não tem formulário.");
            form.setNeedAppearances(true);
            for (Map.Entry<String, String> e : values.entrySet()) {
                PDField field = form.getField(e.getKey());
                if (field == null) continue;
                String v = e.getValue() == null ? "" : e.getValue();
                try {
                    if (field instanceof PDCheckBox cb) {
                        if (isYes(v)) cb.check();
                        else cb.unCheck();
                    } else if (field instanceof PDRadioButton radio) {
                        if (!v.isBlank()) radio.setValue(v);
                    } else if (field instanceof PDChoice choice) {
                        if (!v.isBlank()) choice.setValue(v);
                    } else if (field instanceof PDTextField tf) {
                        tf.setValue(v);
                    }
                } catch (IOException | RuntimeException ex) {
                    if (skipped != null) skipped.add(e.getKey());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static boolean isYes(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("sim") || s.equals("yes") || s.equals("true") || s.equals("on") || s.equals("x") || s.equals("1");
    }

    @SuppressWarnings("unused")
    private static List<String> none() {
        return new ArrayList<>();
    }
}

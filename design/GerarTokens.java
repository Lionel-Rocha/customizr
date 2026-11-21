import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Gera as folhas de estilo dos temas a partir de design/tokens.properties, pro programa de PC (JavaFX) e pro site.
 * Rodar na raiz do repositorio: {@code java design/GerarTokens.java} (so precisa do JDK, sem instalar nada).
 */
public class GerarTokens {

    static final String[] BASE = {"bg", "surface", "raised", "border", "accent", "accent-deep", "accent-text", "text",
            "muted", "danger", "success"};
    static final String INICIO = "/* tokens:inicio (gerado por design/GerarTokens.java a partir de design/tokens.properties; nao edite aqui) */";
    static final String FIM = "/* tokens:fim */";

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(root.resolve("design/tokens.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        String[] temas = p.getProperty("temas").split(",");
        String padrao = p.getProperty("padrao", temas[0]).trim();

        Map<String, Map<String, String>> cores = new LinkedHashMap<>();
        for (String t : temas) cores.put(t.trim(), resolve(p, t.trim()));

        Path fx = root.resolve("mesa-pc/vtt-client/src/main/resources");
        // PC: tema padrao dentro do style.css
        Path style = fx.resolve("style.css");
        String css = Files.readString(style, StandardCharsets.UTF_8);
        String bloco = INICIO + "\n" + fxRoot(cores.get(padrao), null) + FIM;
        int a = css.indexOf(INICIO.substring(0, 16));
        int b = css.indexOf(FIM);
        if (a >= 0 && b > a) {
            css = css.substring(0, a) + bloco + css.substring(b + FIM.length());
        } else {
            // primeira vez: troca o primeiro bloco .root { ... } (as variaveis) pelo bloco marcado
            int r0 = css.indexOf(".root {");
            int r1 = css.indexOf('}', r0);
            int c0 = css.lastIndexOf("/*", r0);
            int start = c0 >= 0 && css.substring(c0, r0).contains("Cores do tema") ? c0 : r0;
            css = css.substring(0, start) + bloco + css.substring(r1 + 1);
        }
        Files.writeString(style, css, StandardCharsets.UTF_8);
        // PC: os outros temas, com todos os tons ja calculados (nao precisa mais de theme-derived.css)
        for (String t : cores.keySet()) {
            if (t.equals(padrao)) continue;
            String nome = p.getProperty(t + ".nome", t);
            String out = "/* Tema " + nome + " (gerado por design/GerarTokens.java a partir de design/tokens.properties). */\n"
                    + fxRoot(cores.get(t), p.getProperty(t + ".fonte"));
            Files.writeString(fx.resolve("theme-" + t + ".css"), out, StandardCharsets.UTF_8);
        }
        Files.deleteIfExists(fx.resolve("theme-derived.css"));

        // Site: todos os temas como variaveis CSS (--c-*), trocando pelo atributo data-theme do <html>
        StringBuilder web = new StringBuilder();
        web.append("/* Cores dos temas do Customizr pro site (gerado por design/GerarTokens.java a partir de design/tokens.properties).\n")
                .append(" * Uso: <html data-theme=\"claro\"> (sem atributo = ").append(padrao).append("). Mesmas cores do programa de PC. */\n");
        for (String t : cores.keySet()) {
            String sel = t.equals(padrao) ? ":root, [data-theme=\"" + t + "\"]" : "[data-theme=\"" + t + "\"]";
            web.append(sel).append(" {\n");
            web.append("    --theme-name: \"").append(p.getProperty(t + ".nome", t)).append("\";\n");
            String fonte = p.getProperty(t + ".fonte");
            web.append("    --font-family: ").append(fonte != null ? fonte + ", " : "").append("\"Segoe UI\", system-ui, sans-serif;\n");
            for (var e : cores.get(t).entrySet()) web.append("    --c-").append(e.getKey()).append(": ").append(e.getValue()).append(";\n");
            web.append("}\n\n");
        }
        Files.createDirectories(root.resolve("design/web"));
        Files.writeString(root.resolve("design/web/tokens.css"), web.toString(), StandardCharsets.UTF_8);

        // lista dos temas pro site (nome mostrado), na ordem
        StringBuilder js = new StringBuilder("// Gerado por design/GerarTokens.java. Temas na ordem do seletor (chave -> nome).\n"
                + "export const TEMAS = [\n");
        for (String t : cores.keySet()) js.append("  { chave: '").append(t).append("', nome: '").append(p.getProperty(t + ".nome", t)).append("' },\n");
        js.append("];\nexport const TEMA_PADRAO = '").append(padrao).append("';\n");
        Files.writeString(root.resolve("design/web/temas.js"), js.toString(), StandardCharsets.UTF_8);

        // Icones: os mesmos desenhos (SVG 24x24) do programa de PC, lidos do Icons.java
        Path iconsJava = root.resolve("mesa-pc/vtt-client/src/main/java/com/vttcaseiro/view/Icons.java");
        StringBuilder ic = new StringBuilder("// Gerado por design/GerarTokens.java a partir do Icons.java do programa de PC (mesmos desenhos).\n"
                + "export const ICONES = {\n");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Map\\.entry\\(\"([a-z]+)\", \"([^\"]+)\"\\)")
                .matcher(Files.readString(iconsJava, StandardCharsets.UTF_8));
        int n = 0;
        while (m.find()) {
            ic.append("  ").append(m.group(1)).append(": '").append(m.group(2)).append("',\n");
            n++;
        }
        ic.append("};\n\n/** <svg> de um icone, no tamanho pedido (a cor vem do texto em volta: currentColor). */\n"
                + "export function icone(nome, tamanho = 16) {\n"
                + "  const d = ICONES[nome] || '';\n"
                + "  return `<svg class=\"icon\" width=\"${tamanho}\" height=\"${tamanho}\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">`\n"
                + "    + `<path fill=\"currentColor\" d=\"${d}\"/></svg>`;\n"
                + "}\n");
        Files.writeString(root.resolve("design/web/icones.js"), ic.toString(), StandardCharsets.UTF_8);
        // Front em Angular (pasta front/): os mesmos temas e icones, em TypeScript
        Path front = root.resolve("front/src/app/design");
        if (Files.isDirectory(root.resolve("front/src/app"))) {
            Files.createDirectories(front);
            StringBuilder ts = new StringBuilder("// Gerado por design/GerarTokens.java a partir de design/tokens.properties. Nao edite aqui.\n"
                    + "export interface Tema { chave: string; nome: string; }\n\nexport const TEMAS: Tema[] = [\n");
            for (String t : cores.keySet()) ts.append("  { chave: '").append(t).append("', nome: '").append(p.getProperty(t + ".nome", t)).append("' },\n");
            ts.append("];\n\nexport const TEMA_PADRAO = '").append(padrao).append("';\n");
            Files.writeString(front.resolve("temas.ts"), ts.toString(), StandardCharsets.UTF_8);
            String icTs = ic.toString()
                    .replace("export const ICONES = {", "export const ICONES: Record<string, string> = {")
                    .replace("export function icone(nome, tamanho = 16) {", "export function icone(nome: string, tamanho = 16): string {");
            Files.writeString(front.resolve("icones.ts"), icTs, StandardCharsets.UTF_8);
            // o Angular so aceita arquivos dentro da pasta dele: copia o kit (estilos) e o logo
            Path kit = root.resolve("front/src/kit");
            Files.createDirectories(kit);
            String aviso = "/* COPIA gerada por design/GerarTokens.java. Edite em design/web/ e rode o gerador de novo. */\n";
            for (String f : new String[]{"tokens.css", "customizr.css"}) {
                Files.writeString(kit.resolve(f), aviso + Files.readString(root.resolve("design/web/" + f), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
            }
            Path pub = root.resolve("front/public/img");
            Files.createDirectories(pub);
            for (String f : new String[]{"customizr-64.png", "customizr-128.png", "customizr-logo.png"}) {
                Files.copy(fx.resolve("icons/" + f), pub.resolve(f), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // logo
        Files.createDirectories(root.resolve("design/web/img"));
        for (String f : new String[]{"customizr-64.png", "customizr-128.png", "customizr-logo.png"}) {
            Files.copy(fx.resolve("icons/" + f), root.resolve("design/web/img/" + f), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Temas gerados: " + String.join(", ", cores.keySet()) + "; " + n + " icones.");
    }

    /** Todas as cores de um tema: base, tons derivados (ou fixados no tema) e transparencias. */
    static Map<String, String> resolve(Properties p, String t) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : BASE) {
            String v = p.getProperty(t + "." + k);
            if (v == null) throw new IllegalArgumentException("Tema " + t + " sem a cor base " + k);
            out.put(k, v.trim().toUpperCase());
        }
        for (String k : orderedKeys(p, "geral.")) {
            String nome = k.substring("geral.".length());
            String v = p.getProperty(t + "." + nome, p.getProperty(k));
            out.put(nome, v.trim().toUpperCase());
        }
        List<String> derivados = new ArrayList<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith("derivado.")) derivados.add(k);
        derivados.sort(null);
        for (String k : orderedKeys(p, "derivado.")) {
            String nome = k.substring("derivado.".length());
            String fixo = p.getProperty(t + "." + nome);
            if (fixo != null) {
                out.put(nome, fixo.trim().toUpperCase());
                continue;
            }
            String[] regra = p.getProperty(k).split(",");
            out.put(nome, derive(out.get(regra[0].trim()), Double.parseDouble(regra[1].trim()) / 100.0));
        }
        for (String k : orderedKeys(p, "alfa.")) {
            String nome = k.substring("alfa.".length());
            String fixo = p.getProperty(t + "." + nome);
            if (fixo != null) {
                out.put(nome, fixo.trim().toUpperCase());
                continue;
            }
            String[] regra = p.getProperty(k).split(",");
            out.put(nome, out.get(regra[0].trim()) + regra[1].trim().toUpperCase());
        }
        return out;
    }

    /** Chaves com o prefixo na ordem em que aparecem no arquivo (Properties nao guarda a ordem). */
    static List<String> orderedKeys(Properties p, String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of("design/tokens.properties"), StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (l.startsWith(prefix) && l.contains("=")) keys.add(l.substring(0, l.indexOf('=')).trim());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return keys;
    }

    static String fxRoot(Map<String, String> cores, String fonte) {
        StringBuilder sb = new StringBuilder(".root {\n");
        for (var e : cores.entrySet()) sb.append("    -c-").append(e.getKey()).append(": ").append(e.getValue()).append(";\n");
        if (fonte != null) sb.append("    -fx-font-family: ").append(fonte).append(";\n");
        return sb.append("}\n").toString();
    }

    /** Mesma conta do derive() do CSS do JavaFX (brilho ajustado pela luminosidade percebida da cor). */
    static String derive(String hex, double brightness) {
        int rgb = Integer.parseInt(hex.substring(1, 7), 16);
        double r = ((rgb >> 16) & 255) / 255.0, g = ((rgb >> 8) & 255) / 255.0, b = (rgb & 255) / 255.0;
        double base = 0.3 * r + 0.59 * g + 0.11 * b;
        double calc = brightness;
        if (brightness > 0) {
            if (base > 0.85) calc *= 1.6;
            else if (base > 0.6) calc *= 1.0;
            else if (base > 0.5) calc *= 0.9;
            else if (base > 0.4) calc *= 0.8;
            else if (base > 0.3) calc *= 0.7;
            else calc *= 0.6;
        } else if (base < 0.2) {
            calc *= 0.6;
        }
        calc = Math.max(-1, Math.min(1, calc));
        float[] hsb = java.awt.Color.RGBtoHSB((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255), null);
        double h = hsb[0], s = hsb[1], v = hsb[2];
        if (calc > 0) {
            s *= 1 - calc;
            v += (1 - v) * calc;
        } else {
            v *= calc + 1;
        }
        int out = java.awt.Color.HSBtoRGB((float) h, (float) Math.max(0, Math.min(1, s)), (float) Math.max(0, Math.min(1, v)));
        return String.format("#%06X", out & 0xFFFFFF);
    }
}

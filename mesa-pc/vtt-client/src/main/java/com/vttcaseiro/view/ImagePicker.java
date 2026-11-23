package com.vttcaseiro.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Escolher imagem: do computador (a janela de arquivo de sempre) ou por link (cola o endereco de uma imagem da
 * internet). O link e baixado uma vez pra {@code ~/.vttcaseiro/imagens-baixadas} e dali segue o mesmo caminho de
 * um arquivo escolhido (vai pro acervo da mesa, viaja pros jogadores etc.).
 */
public final class ImagePicker {
    /** Maior imagem baixada por link. */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private ImagePicker() {
    }

    /** No lugar de {@code chooser.showOpenDialog(owner)}: mesma resposta (arquivo ou null), com a opcao de link. */
    public static File open(FileChooser chooser, Window owner) {
        Choice c = ask(owner, chooser.getTitle());
        if (c == null) return null;
        if (c.url == null) return chooser.showOpenDialog(owner);
        return downloadOrWarn(c.url, owner);
    }

    /** No lugar de {@code chooser.showOpenMultipleDialog(owner)}. Por link vem uma imagem so. */
    public static List<File> openMultiple(FileChooser chooser, Window owner) {
        Choice c = ask(owner, chooser.getTitle());
        if (c == null) return null;
        if (c.url == null) return chooser.showOpenMultipleDialog(owner);
        File f = downloadOrWarn(c.url, owner);
        return f == null ? null : List.of(f);
    }

    private record Choice(String url) {
    }

    private static Choice ask(Window owner, String title) {
        Dialog<ButtonType> d = new Dialog<>();
        if (owner != null) d.initOwner(owner);
        d.setTitle(title == null || title.isBlank() ? "Escolher imagem" : title);
        d.setHeaderText(null);
        ButtonType fromPc = new ButtonType("Escolher do computador...", ButtonBar.ButtonData.OTHER);
        ButtonType fromLink = new ButtonType("Usar o link", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(fromPc, fromLink, ButtonType.CANCEL);
        TextField url = new TextField();
        url.setPromptText("https://... (endereço de uma imagem JPG, PNG ou GIF)");
        String clip = clipboardUrl();
        if (clip != null) url.setText(clip);
        HBox.setHgrow(url, Priority.ALWAYS);
        Button paste = new Button("Colar");
        paste.setOnAction(e -> {
            String c = Clipboard.getSystemClipboard().getString();
            if (c != null) url.setText(c.trim());
        });
        Label how = new Label("Ou cole o link de uma imagem da internet (botão direito na imagem > \"Copiar endereço "
                + "da imagem\"). Ela é baixada uma vez e fica guardada na mesa.");
        how.setWrapText(true);
        how.getStyleClass().add("label-muted");
        VBox box = new VBox(8, how, new HBox(6, url, paste));
        box.setPadding(new Insets(10));
        box.setPrefWidth(520);
        d.getDialogPane().setContent(box);
        Button linkButton = (Button) d.getDialogPane().lookupButton(fromLink);
        linkButton.disableProperty().bind(url.textProperty().map(t -> !looksLikeUrl(t)));
        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() == ButtonType.CANCEL) return null;
        if (r.get() == fromPc) return new Choice(null);
        return new Choice(url.getText().trim());
    }

    static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return (t.startsWith("https://") || t.startsWith("http://")) && t.length() > 10 && !t.contains(" ");
    }

    private static String clipboardUrl() {
        try {
            String c = Clipboard.getSystemClipboard().getString();
            return c != null && looksLikeUrl(c) ? c.trim() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static File downloadOrWarn(String url, Window owner) {
        try {
            return download(url, defaultFolder());
        } catch (IOException ex) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Não deu pra usar esse link: " + ex.getMessage());
            if (owner != null) a.initOwner(owner);
            a.setHeaderText(null);
            a.showAndWait();
            return null;
        }
    }

    public static Path defaultFolder() {
        return Path.of(System.getProperty("user.home"), ".vttcaseiro", "imagens-baixadas");
    }

    /**
     * Baixa a imagem do link pra {@code folder} (nome = hash do conteudo + extensao certa). Recusa o que nao for
     * JPG/PNG/GIF de verdade (pelo conteudo, nao pelo nome) e o que passar de {@link #MAX_BYTES}.
     */
    public static File download(String url, Path folder) throws IOException {
        if (!looksLikeUrl(url)) throw new IOException("o endereço precisa começar com https:// ou http://");
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IOException("endereço inválido");
        }
        java.net.HttpURLConnection con;
        try {
            con = (java.net.HttpURLConnection) uri.toURL().openConnection();
        } catch (IllegalArgumentException | java.net.MalformedURLException | ClassCastException ex) {
            throw new IOException("endereço inválido");
        }
        byte[] bytes;
        try {
            con.setConnectTimeout(10_000);
            con.setReadTimeout(20_000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Customizr (mesa de RPG)");
            con.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/*;q=0.8");
            int status = con.getResponseCode();
            if (status / 100 != 2) throw new IOException("o site respondeu " + status);
            if (con.getContentLengthLong() > MAX_BYTES) throw new IOException("imagem grande demais (mais de 25 MB)");
            try (InputStream in = con.getInputStream()) {
                bytes = in.readNBytes((int) MAX_BYTES + 1);
            }
        } finally {
            con.disconnect();
        }
        if (bytes.length > MAX_BYTES) throw new IOException("imagem grande demais (mais de 25 MB)");
        String ext = extensionOf(bytes);
        if (ext == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 200), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .toLowerCase(Locale.ROOT);
            if (head.contains("<html") || head.contains("<!doctype")) {
                throw new IOException("o link abre uma página, não a imagem. Clique com o botão direito na imagem e "
                        + "use \"Copiar endereço da imagem\".");
            }
            throw new IOException("não é uma imagem JPG, PNG ou GIF");
        }
        Files.createDirectories(folder);
        Path target = folder.resolve(sha256(bytes).substring(0, 32) + ext);
        if (!Files.exists(target)) Files.write(target, bytes);
        return target.toFile();
    }

    /** Extensao pelo conteudo (assinatura do arquivo), ou null se nao for um tipo aceito. */
    static String extensionOf(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return ".png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return ".jpg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return ".gif";
        return null;
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

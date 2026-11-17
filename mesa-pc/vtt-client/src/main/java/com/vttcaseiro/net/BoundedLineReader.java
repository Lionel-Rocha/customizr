package com.vttcaseiro.net;

import java.io.IOException;
import java.io.Reader;

/**
 * Le uma mensagem (uma linha de JSON) por vez, com TAMANHO MAXIMO. O
 * BufferedReader.readLine() que era usado antes aceitava uma linha de
 * qualquer tamanho: um cliente com defeito (ou mal-intencionado) mandando
 * uma linha sem fim enchia a memoria do servidor ate derrubar a mesa.
 * <p>
 * O limite cabe a maior mensagem legitima: uma imagem de 4 MB (limite do
 * ImageAssetStore) vira ~5,6 MB de base64.
 */
final class BoundedLineReader {

    /** 8 milhoes de caracteres por mensagem. */
    static final int MAX_LINE_CHARS = 8 * 1024 * 1024;

    /** Linha passou do limite -- quem le deve encerrar a conexao. */
    static final class LineTooLongException extends IOException {
        LineTooLongException() {
            super("Mensagem maior que " + (MAX_LINE_CHARS / 1024 / 1024) + " MB -- conexao encerrada.");
        }
    }

    private final Reader in;
    private final int maxChars;
    private final char[] buffer = new char[8192];
    private int pos;
    private int len;

    BoundedLineReader(Reader in) {
        this(in, MAX_LINE_CHARS);
    }

    BoundedLineReader(Reader in, int maxChars) {
        this.in = in;
        this.maxChars = maxChars;
    }

    /** A proxima linha sem o '\n' (e sem '\r' final), ou null no fim da conexao. */
    String readLine() throws IOException {
        StringBuilder sb = null;
        while (true) {
            if (pos >= len) {
                len = in.read(buffer, 0, buffer.length);
                pos = 0;
                if (len <= 0) {
                    len = 0;
                    return sb == null || sb.isEmpty() ? null : stripCr(sb);
                }
            }
            int start = pos;
            while (pos < len && buffer[pos] != '\n') pos++;
            if (sb == null) sb = new StringBuilder(Math.min(1024, maxChars));
            if (sb.length() + (pos - start) > maxChars) {
                throw new LineTooLongException();
            }
            sb.append(buffer, start, pos - start);
            if (pos < len) { // achou '\n'
                pos++;
                return stripCr(sb);
            }
        }
    }

    private static String stripCr(StringBuilder sb) {
        int n = sb.length();
        if (n > 0 && sb.charAt(n - 1) == '\r') sb.setLength(n - 1);
        return sb.toString();
    }
}

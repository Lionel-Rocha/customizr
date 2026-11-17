package com.vttcaseiro.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.vttcaseiro.model.SceneModel;

import com.vttcaseiro.library.AtomicFiles;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Salva/carrega um SceneModel inteiro (mapa, grade e tokens) como um
 * arquivo JSON legivel. E por isso que model/ nao depende de nada do
 * JavaFX -- essas classes sao so dados, o Gson le/escreve elas direto por
 * reflexao, sem precisar de nenhum codigo de conversao escrito a mao.
 * <p>
 * JsonSyntaxException (do Gson) nao e checked -- se o arquivo estiver
 * corrompido ou nao for um save valido, ela sobe pra quem chamou load()
 * tratar (ex.: mostrar um alerta na tela em vez de abrir um mapa quebrado).
 */
public final class SceneStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SceneStorage() {
    }

    public static void save(SceneModel scene, Path file) throws IOException {
        AtomicFiles.writeString(file, SchemaMigrations.write(GSON, scene));
    }

    /** @throws JsonSyntaxException se o arquivo nao for um save valido */
    public static SceneModel load(Path file) throws IOException {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            SceneModel scene = SchemaMigrations.read(GSON, reader, SceneModel.class, SchemaMigrations.Kind.SCENE);
            return scene != null ? scene : new SceneModel();
        }
    }
}

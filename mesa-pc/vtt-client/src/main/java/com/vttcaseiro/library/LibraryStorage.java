package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.prerequisite.Prerequisite;
import com.vttcaseiro.character.prerequisite.PrerequisiteAdapter;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.content.SourceConfig;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.party.Party;
import com.vttcaseiro.persistence.SceneStorage;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Uma pasta na maquina com duas subpastas de verdade: "mapas" e "fichas".
 * E literalmente o que o nome diz -- um lugar no disco onde ficam
 * guardados os mapas e as fichas, pra escolher dali em vez de ter que
 * reabrir um arquivo solto toda vez. Cada mapa/ficha e um arquivo .json
 * (o mesmo formato que SceneStorage ja usa pra salvar/abrir um mapa
 * avulso -- aqui e so organizado numa pasta fixa com uma lista pra
 * escolher, em vez de um FileChooser toda vez).
 */
public class LibraryStorage implements MesaRepository {
    // registerTypeAdapter(Prerequisite...) e obrigatorio aqui -- CharacterSheet
    // tem List<Feat>, e Feat tem List<Prerequisite> (interface com varias
    // classes concretas por baixo). Sem isso, carregar uma ficha com feats
    // que tem pre-requisito quebra (ver PrerequisiteAdapter).
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Prerequisite.class, new PrerequisiteAdapter())
            .create();

    private final Path root;
    private final Path mapsDir;
    private final Path charactersDir;
    private final Path mobsDir;
    private final Path partyFile;
    private final Path classesFile;
    private final Path sourceConfigFile;
    private final Path compendiumFile;
    private final ImageAssetStore imageAssetStore;

    public LibraryStorage(Path root) throws IOException {
        this.root = root;
        this.mapsDir = root.resolve("mapas");
        this.charactersDir = root.resolve("fichas");
        this.mobsDir = root.resolve("mobs");
        this.partyFile = root.resolve("party.json");
        this.classesFile = root.resolve("classes.json");
        this.sourceConfigFile = root.resolve("fontes.json");
        this.compendiumFile = root.resolve("compendio.json");
        Files.createDirectories(mapsDir);
        Files.createDirectories(charactersDir);
        Files.createDirectories(mobsDir);
        this.imageAssetStore = new ImageAssetStore(root);
    }

    @Override
    public ImageAssetStore getImageAssetStore() { return imageAssetStore; }

    @Override
    public void saveCompendium(List<CompendiumEntry> entries) throws IOException {
        AtomicFiles.writeString(compendiumFile, GSON.toJson(entries));
    }

    @Override
    public List<CompendiumEntry> loadCompendium() throws IOException {
        if (!Files.exists(compendiumFile)) {
            return new ArrayList<>();
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(compendiumFile, java.nio.charset.StandardCharsets.UTF_8)) {
            List<CompendiumEntry> entries = GSON.fromJson(reader, COMPENDIUM_ENTRY_LIST_TYPE);
            return entries != null ? entries : new ArrayList<>();
        }
    }

    @Override
    public Path getRoot() { return root; }

    /** O nome da mesa e so o nome da propria pasta raiz -- nao guardamos
     * um "mesa.json" separado de proposito, pra nao duplicar a mesma
     * informacao que o sistema de arquivos ja da de graca. */
    @Override
    public String getName() {
        Path fileName = root.getFileName();
        return fileName != null ? fileName.toString() : root.toString();
    }

    public Path getMapsDir() { return mapsDir; }
    public Path getCharactersDir() { return charactersDir; }
    public Path getMobsDir() { return mobsDir; }

    @Override
    public List<String> listMaps() throws IOException {
        return listJsonNames(mapsDir);
    }

    @Override
    public List<String> listCharacters() throws IOException {
        return listJsonNames(charactersDir);
    }

    @Override
    public List<String> listMobs() throws IOException {
        return listJsonNames(mobsDir);
    }

    private List<String> listJsonNames(Path dir) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.substring(0, fileName.length() - ".json".length());
                    })
                    .sorted(Comparator.naturalOrder())
                    .forEach(names::add);
        }
        return names;
    }

    @Override
    public void saveMap(SceneModel scene, String name) throws IOException {
        SceneStorage.save(scene, mapsDir.resolve(safeFileName(name) + ".json"));
    }

    @Override
    public SceneModel loadMap(String name) throws IOException {
        return SceneStorage.load(mapsDir.resolve(safeFileName(name) + ".json"));
    }

    @Override
    public void saveCharacter(CharacterSheet sheet, String name) throws IOException {
        AtomicFiles.writeString(charactersDir.resolve(safeFileName(name) + ".json"), com.vttcaseiro.persistence.SchemaMigrations.write(GSON, sheet));
    }

    @Override
    public void deleteMap(String name) throws IOException {
        java.nio.file.Files.deleteIfExists(mapsDir.resolve(safeFileName(name) + ".json"));
    }

    @Override
    public void deleteMob(String name) throws IOException {
        java.nio.file.Files.deleteIfExists(mobsDir.resolve(safeFileName(name) + ".json"));
    }

    @Override
    public void deleteCharacter(String name) throws IOException {
        java.nio.file.Files.deleteIfExists(charactersDir.resolve(safeFileName(name) + ".json"));
    }

    /** @throws com.google.gson.JsonSyntaxException se o arquivo nao for uma ficha valida */
    @Override
    public CharacterSheet loadCharacter(String name) throws IOException {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(charactersDir.resolve(safeFileName(name) + ".json"), java.nio.charset.StandardCharsets.UTF_8)) {
            CharacterSheet sheet = com.vttcaseiro.persistence.SchemaMigrations.read(GSON, reader, CharacterSheet.class, com.vttcaseiro.persistence.SchemaMigrations.Kind.CHARACTER);
            return sheet != null ? sheet : new CharacterSheet();
        }
    }

    /** A ficha no mesmo formato do arquivo da mesa (pra exportar e importar em outra mesa). */
    public static String characterToJson(CharacterSheet sheet) {
        return com.vttcaseiro.persistence.SchemaMigrations.write(GSON, sheet);
    }

    /** Le uma ficha exportada (o mesmo formato do arquivo da mesa), migrando versoes antigas. */
    public static CharacterSheet parseCharacterJson(String json) throws IOException {
        CharacterSheet sheet = com.vttcaseiro.persistence.SchemaMigrations.read(GSON, new java.io.StringReader(json),
                CharacterSheet.class, com.vttcaseiro.persistence.SchemaMigrations.Kind.CHARACTER);
        if (sheet == null) throw new IOException("Arquivo vazio.");
        return sheet;
    }

    @Override
    public void saveMob(MobSheet mob, String name) throws IOException {
        AtomicFiles.writeString(mobsDir.resolve(safeFileName(name) + ".json"), com.vttcaseiro.persistence.SchemaMigrations.write(GSON, mob));
    }

    @Override
    public MobSheet loadMob(String name) throws IOException {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(mobsDir.resolve(safeFileName(name) + ".json"), java.nio.charset.StandardCharsets.UTF_8)) {
            MobSheet mob = com.vttcaseiro.persistence.SchemaMigrations.read(GSON, reader, MobSheet.class, com.vttcaseiro.persistence.SchemaMigrations.Kind.MOB);
            return mob != null ? mob : new MobSheet();
        }
    }

    @Override
    public void saveParty(Party party) throws IOException {
        AtomicFiles.writeString(partyFile, com.vttcaseiro.persistence.SchemaMigrations.write(GSON, party));
    }

    /** @throws com.google.gson.JsonSyntaxException se o arquivo nao for uma party valida */
    @Override
    public Optional<Party> loadParty() throws IOException {
        if (!Files.exists(partyFile)) {
            return Optional.empty();
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(partyFile, java.nio.charset.StandardCharsets.UTF_8)) {
            Party party = com.vttcaseiro.persistence.SchemaMigrations.read(GSON, reader, Party.class, com.vttcaseiro.persistence.SchemaMigrations.Kind.PARTY);
            return Optional.ofNullable(party);
        }
    }

    private static final Type DND_CLASS_LIST_TYPE = new TypeToken<List<DndClass>>() {}.getType();
    private static final Type SOURCE_CONFIG_LIST_TYPE = new TypeToken<List<SourceConfig>>() {}.getType();
    private static final Type COMPENDIUM_ENTRY_LIST_TYPE = new TypeToken<List<CompendiumEntry>>() {}.getType();

    @Override
    public void saveClasses(List<DndClass> classes) throws IOException {
        AtomicFiles.writeString(classesFile, GSON.toJson(classes));
    }

    @Override
    public List<DndClass> loadClasses() throws IOException {
        if (!Files.exists(classesFile)) {
            return new ArrayList<>();
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(classesFile, java.nio.charset.StandardCharsets.UTF_8)) {
            List<DndClass> classes = GSON.fromJson(reader, DND_CLASS_LIST_TYPE);
            return classes != null ? classes : new ArrayList<>();
        }
    }

    @Override
    public void saveSourceConfigs(List<SourceConfig> configs) throws IOException {
        AtomicFiles.writeString(sourceConfigFile, GSON.toJson(configs));
    }

    @Override
    public List<SourceConfig> loadSourceConfigs() throws IOException {
        if (!Files.exists(sourceConfigFile)) {
            return new ArrayList<>();
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(sourceConfigFile, java.nio.charset.StandardCharsets.UTF_8)) {
            List<SourceConfig> configs = GSON.fromJson(reader, SOURCE_CONFIG_LIST_TYPE);
            return configs != null ? configs : new ArrayList<>();
        }
    }

    /** Troca qualquer caractere que nao seja letra/numero/traco por "_",
     * pra dar pra usar o nome do mapa/personagem direto como nome de
     * arquivo sem dar erro no sistema operacional. */
    public static String safeFileName(String name) {
        String safe = name.trim().replaceAll("[^a-zA-Z0-9-_ ]", "_");
        return safe.isEmpty() ? "sem_nome" : safe;
    }
}

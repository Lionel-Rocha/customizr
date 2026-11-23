package com.vttcaseiro.repository;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.content.SourceConfig;
import com.vttcaseiro.library.ImageAssetStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.party.Party;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Tudo que uma Mesa precisa saber guardar: cenas e fichas. Esta e a
 * abstracao que faltava -- antes o resto do codigo chamava
 * {@code com.vttcaseiro.library.LibraryStorage} diretamente, amarrando
 * tudo no jeito especifico como ela guarda as coisas (pasta + JSON via
 * Gson). Com essa interface no meio, o {@code service} so conhece
 * "MesaRepository", nunca "LibraryStorage" -- trocar o armazenamento por
 * outra coisa (banco embutido tipo SQLite, por exemplo, quando o
 * compendio importado do 5etools crescer demais pra arquivo solto) nao
 * exige mexer em nenhuma regra de negocio, so escrever uma nova classe
 * que implementa esta interface.
 * <p>
 * {@link com.vttcaseiro.library.LibraryStorage} e hoje a unica
 * implementacao (pasta com "mapas/" e "fichas/", um arquivo .json por
 * item).
 */
public interface MesaRepository {

    String getName();

    Path getRoot();

    List<String> listMaps() throws IOException;

    List<String> listCharacters() throws IOException;

    void saveMap(SceneModel scene, String name) throws IOException;

    SceneModel loadMap(String name) throws IOException;

    void saveCharacter(CharacterSheet sheet, String name) throws IOException;

    CharacterSheet loadCharacter(String name) throws IOException;

    /** Apaga o arquivo da ficha com esse nome (sem erro se nao existir) --
     * usado quando o personagem e renomeado, pra nao sobrar o arquivo com
     * o nome antigo na lista de fichas da mesa. */
    void deleteCharacter(String name) throws IOException;

    /** Apaga o arquivo da cena com esse nome (sem erro se nao existir). */
    void deleteMap(String name) throws IOException;

    /** Apaga o arquivo do monstro com esse nome (sem erro se nao existir). */
    void deleteMob(String name) throws IOException;

    /** Nome do arquivo que um nome de ficha/cena/monstro vira (acentos e simbolos viram "_"). As listas mostram isso. */
    static String fileNameOf(String name) {
        return com.vttcaseiro.library.LibraryStorage.safeFileName(name == null ? "" : name);
    }

    /** Uma mesa tem no maximo uma Party (o suficiente pra maioria das
     * campanhas -- um grupo so). */
    void saveParty(Party party) throws IOException;

    /** Vazio se a mesa ainda nao tem Party salva (mesa nova). */
    Optional<Party> loadParty() throws IOException;

    /** Substitui TODAS as classes salvas da mesa pela lista dada --
     * quem chama e responsavel por juntar com o que ja existia (ver
     * com.vttcaseiro.service.ClassImportService), isto aqui so grava. */
    void saveClasses(List<DndClass> classes) throws IOException;

    /** Lista vazia (nunca null) se a mesa ainda nao importou nenhuma classe. */
    List<DndClass> loadClasses() throws IOException;

    void saveSourceConfigs(List<SourceConfig> configs) throws IOException;

    /** Lista vazia (nunca null) se a mesa ainda nao configurou nenhuma fonte. */
    List<SourceConfig> loadSourceConfigs() throws IOException;

    /** Mesma ideia de listCharacters()/saveCharacter()/loadCharacter(),
     * mas pra monstros/NPCs -- guardados numa pasta propria ("mobs/") pra
     * nao misturar com as fichas de jogador. */
    List<String> listMobs() throws IOException;

    void saveMob(MobSheet mob, String name) throws IOException;

    MobSheet loadMob(String name) throws IOException;

    /** Guarda/le os bytes de imagem compartilhados da mesa (tokens,
     * retratos de ficha, retratos de monstro, mapas) de forma deduplicada
     * -- ver ImageAssetStore pra entender por que isso existe em vez de
     * cada objeto guardar a propria copia Base64 da imagem. Mesmo assim
     * fica exposto so por aqui (igual todo o resto desta interface),
     * nunca direto como "new LibraryStorage(...).getImageAssetStore()"
     * em código de servico/tela -- se um dia trocarmos o armazenamento
     * por outra coisa, essa troca so acontece aqui. */
    ImageAssetStore getImageAssetStore();

    /** Substitui TODAS as entradas de compendio salvas da mesa -- igual
     * saveClasses, quem chama (com.vttcaseiro.service.CompendiumService)
     * e responsavel por mesclar com o que ja existia antes de chamar
     * isto. */
    void saveCompendium(List<CompendiumEntry> entries) throws IOException;

    /** Lista vazia (nunca null) se a mesa ainda nao tem nada no compendio. */
    List<CompendiumEntry> loadCompendium() throws IOException;
}

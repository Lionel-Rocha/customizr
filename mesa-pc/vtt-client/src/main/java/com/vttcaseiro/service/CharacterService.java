package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dono das fichas abertas nesta sessao e da ponte com o
 * {@link MesaRepository}. Antes isso era um {@code Map} solto dentro do
 * Main.java, com cada metodo (criar, abrir, salvar) decidindo por conta
 * propria o que fazer com ele. Agora e um unico lugar com uma API clara.
 */
public class CharacterService {
    private static final Log log = Log.of(CharacterService.class);

    private final MesaRepository repository;
    private final Map<String, CharacterSheet> openCharacters = new LinkedHashMap<>();
    /** id da ficha -> nome com que o arquivo dela esta salvo agora. */
    private final Map<String, String> savedNames = new LinkedHashMap<>();

    public CharacterService(MesaRepository repository) {
        this.repository = repository;
    }

    /** Cria uma ficha nova (com um traco de exemplo, pra ter o que testar
     * ate existir uma tela de edicao de verdade) e ja salva na mesa.
     *
     * @throws IllegalArgumentException se o nome for vazio
     * @throws IOException              se nao conseguir salvar
     */
    public CharacterSheet create(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do personagem não pode ser vazio.");
        }
        CharacterSheet sheet = new CharacterSheet(name.trim());
        save(sheet);
        return sheet;
    }

    public void save(CharacterSheet sheet) throws IOException {
        if (sheet.getName() == null || sheet.getName().isBlank()) {
            throw new IOException("O nome do personagem não pode ser vazio.");
        }
        String currentFileName = savedNames.get(sheet.getId());
        boolean renamed = currentFileName != null && !currentFileName.equals(sheet.getName());
        if (renamed && repository.listCharacters().contains(sheet.getName())) {
            // Nao sobrescreve a ficha de OUTRO personagem que ja usa esse nome.
            throw new IOException("Já existe uma ficha chamada \"" + sheet.getName() + "\" nesta mesa.");
        }
        repository.saveCharacter(sheet, sheet.getName());
        // O arquivo e nomeado pelo nome do personagem: se ele foi renomeado
        // na ficha desde o ultimo save, apaga o arquivo com o nome antigo
        // (senao a lista de fichas ficaria com as duas versoes).
        String previousName = savedNames.put(sheet.getId(), sheet.getName());
        if (previousName != null && !previousName.equals(sheet.getName())) {
            repository.deleteCharacter(previousName);
        }
        openCharacters.put(sheet.getId(), sheet);
        log.info("Ficha salva: " + sheet.getName());
    }

    public CharacterSheet open(String name) throws IOException {
        CharacterSheet sheet = repository.loadCharacter(name);
        openCharacters.put(sheet.getId(), sheet);
        savedNames.put(sheet.getId(), name);
        return sheet;
    }

    public Optional<CharacterSheet> findOpen(String characterId) {
        return Optional.ofNullable(openCharacters.get(characterId));
    }

    /**
     * Acha a ficha pelo id: primeiro nas ja carregadas nesta sessao, depois
     * lendo as fichas da mesa do disco (e guardando no cache). Usado pelo
     * token ligado a uma ficha que ninguem abriu ainda nesta sessao.
     */
    public Optional<CharacterSheet> findById(String characterId) {
        if (characterId == null) return Optional.empty();
        CharacterSheet cached = openCharacters.get(characterId);
        if (cached != null) return Optional.of(cached);
        for (String name : list()) {
            try {
                CharacterSheet sheet = repository.loadCharacter(name);
                if (characterId.equals(sheet.getId())) {
                    openCharacters.put(sheet.getId(), sheet);
                    savedNames.put(sheet.getId(), name);
                    return Optional.of(sheet);
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("Ficha ilegivel ignorada (" + name + "): " + ex.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Guarda uma ficha que chegou da REDE. Diferente de save(), aceita trocar
     * o objeto em memoria (a versao da rede e a nova), e nunca recusa por
     * nome repetido -- quem manda nela e o dono, nao esta maquina.
     */
    public void storeFromNetwork(CharacterSheet sheet) throws IOException {
        String previous = savedNames.get(sheet.getId());
        repository.saveCharacter(sheet, sheet.getName());
        if (previous != null && !previous.equals(sheet.getName())) {
            repository.deleteCharacter(previous);
        }
        savedNames.put(sheet.getId(), sheet.getName());
        openCharacters.put(sheet.getId(), sheet);
    }

    /** Apaga a ficha da mesa (o arquivo). Tokens ligados a ela ficam no mapa, so sem ficha. */
    public void delete(String name) throws IOException {
        if (name == null || !list().contains(name)) throw new IOException("Ficha não encontrada: " + name);
        repository.deleteCharacter(name);
        savedNames.entrySet().removeIf(e -> {
            if (!name.equals(MesaRepository.fileNameOf(e.getValue()))) return false;
            openCharacters.remove(e.getKey());
            return true;
        });
        log.info("Ficha apagada: " + name);
    }

    /**
     * Troca o nome do personagem (e do arquivo). Se a ficha estiver aberta, mude pela instancia aberta
     * ({@code openInstance}) pra janela e a mesa nao divergirem.
     *
     * @throws IllegalArgumentException nome vazio
     * @throws IOException              nome ja usado por outra ficha, ou erro ao salvar
     */
    public CharacterSheet rename(String name, String newName, CharacterSheet openInstance) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome do personagem não pode ser vazio.");
        CharacterSheet sheet = openInstance != null ? openInstance : open(name);
        savedNames.putIfAbsent(sheet.getId(), name);
        String before = sheet.getName();
        sheet.setName(newName.trim());
        try {
            save(sheet);
        } catch (IOException ex) {
            sheet.setName(before);
            throw ex;
        }
        return sheet;
    }

    /** Copia a ficha com outro nome (outro personagem: id novo). Bom pra NPCs parecidos. */
    public CharacterSheet duplicate(String name, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome do personagem não pode ser vazio.");
        if (list().contains(MesaRepository.fileNameOf(newName.trim()))) {
            throw new IOException("Já existe uma ficha chamada \"" + newName.trim() + "\" nesta mesa.");
        }
        CharacterSheet copy = repository.loadCharacter(name);
        copy.assignNewId();
        copy.setName(newName.trim());
        save(copy);
        return copy;
    }

    public List<String> list() {
        try {
            return repository.listCharacters();
        } catch (IOException ex) {
            log.warn("Não foi possível listar fichas: " + ex.getMessage());
            return List.of();
        }
    }
}

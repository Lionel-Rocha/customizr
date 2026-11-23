package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Espelha CharacterService, so que para MobSheet -- mesma logica de "dono
 * do que esta aberto nesta sessao + ponte com o MesaRepository", so que
 * pra monstros/NPCs em vez de fichas de jogador. Separado de proposito em
 * vez de generalizar os dois services num so: as regras de negocio de
 * criar um monstro (sem trait de exemplo, CR inicial padrao) sao
 * diferentes das de criar uma ficha de jogador.
 */
public class MobService {
    private static final Log log = Log.of(MobService.class);

    private final MesaRepository repository;
    private final Map<String, MobSheet> openMobs = new LinkedHashMap<>();

    public MobService(MesaRepository repository) {
        this.repository = repository;
    }

    /**
     * @throws IllegalArgumentException se o nome for vazio
     * @throws IOException              se nao conseguir salvar
     */
    public MobSheet create(String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do monstro nao pode ser vazio.");
        }
        MobSheet mob = new MobSheet(name.trim());
        save(mob);
        return mob;
    }

    public void save(MobSheet mob) throws IOException {
        repository.saveMob(mob, mob.getName());
        openMobs.put(mob.getId(), mob);
        log.info("Monstro salvo: " + mob.getName());
    }

    public MobSheet open(String name) throws IOException {
        MobSheet mob = repository.loadMob(name);
        openMobs.put(mob.getId(), mob);
        return mob;
    }

    public Optional<MobSheet> findOpen(String mobId) {
        return Optional.ofNullable(openMobs.get(mobId));
    }

    /** Pelo id: cache primeiro, depois os arquivos da mesa (mesmo jeito do
     * CharacterService.findById). */
    public Optional<MobSheet> findById(String mobId) {
        if (mobId == null) return Optional.empty();
        Optional<MobSheet> open = findOpen(mobId);
        if (open.isPresent()) return open;
        for (String name : list()) {
            try {
                MobSheet mob = open(name);
                if (mobId.equals(mob.getId())) return Optional.of(mob);
            } catch (IOException | RuntimeException ignored) {
                // arquivo ilegivel: pula
            }
        }
        return Optional.empty();
    }

    /** Apaga o monstro da mesa (o arquivo). Tokens que ja estao no mapa continuam. */
    public void delete(String name) throws IOException {
        if (name == null || !list().contains(name)) throw new IOException("Monstro não encontrado: " + name);
        repository.deleteMob(name);
        openMobs.values().removeIf(m -> name.equals(MesaRepository.fileNameOf(m.getName())));
        log.info("Monstro apagado: " + name);
    }

    /**
     * Troca o nome do monstro (e do arquivo).
     *
     * @throws IllegalArgumentException nome vazio ou ja usado
     */
    public MobSheet rename(String name, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome do monstro não pode ser vazio.");
        String target = MesaRepository.fileNameOf(newName.trim());
        MobSheet mob = open(name);
        if (target.equals(name)) {
            mob.setName(newName.trim());
            save(mob);
            return mob;
        }
        if (list().contains(target)) throw new IllegalArgumentException("Já existe um monstro chamado \"" + target + "\".");
        mob.setName(newName.trim());
        save(mob);
        repository.deleteMob(name);
        return mob;
    }

    /** Copia o monstro com outro nome (ex.: "Goblin chefe" a partir do "Goblin"), com id novo, pra editar a copia. */
    public MobSheet duplicate(String name, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("O nome do monstro não pode ser vazio.");
        if (list().contains(MesaRepository.fileNameOf(newName.trim()))) {
            throw new IllegalArgumentException("Já existe um monstro chamado \"" + newName.trim() + "\".");
        }
        MobSheet copy = repository.loadMob(name);
        copy.assignNewId();
        copy.setName(newName.trim());
        save(copy);
        return copy;
    }

    /** Sugestao de nome livre pra copia: "Goblin 2", "Goblin 3"... */
    public String freeCopyName(String name) {
        int n = 2;
        while (list().contains(MesaRepository.fileNameOf(name + " " + n))) n++;
        return name + " " + n;
    }

    /** Apaga a copia local do monstro com esse id (o mestre escondeu dos jogadores). Devolve o nome, ou null. */
    public String removeById(String mobId) throws IOException {
        Optional<MobSheet> m = findById(mobId);
        if (m.isEmpty()) return null;
        repository.deleteMob(m.get().getName());
        openMobs.remove(mobId);
        return m.get().getName();
    }

    public List<String> list() {
        try {
            return repository.listMobs();
        } catch (IOException ex) {
            log.warn("Nao foi possivel listar monstros: " + ex.getMessage());
            return List.of();
        }
    }
}

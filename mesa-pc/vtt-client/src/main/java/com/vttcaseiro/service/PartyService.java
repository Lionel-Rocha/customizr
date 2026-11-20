package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.party.Party;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;

/**
 * Dono da Party da mesa atual. Carrega a Party salva assim que a mesa
 * abre (ou cria uma vazia, se a mesa for nova -- {@link MesaRepository#loadParty()}
 * devolve vazio nesse caso) e fala com o repositorio pra persistir
 * mudancas. Mesmo padrao do CharacterService/MesaService.
 */
public class PartyService {
    private static final Log log = Log.of(PartyService.class);

    private final MesaRepository repository;
    private Party party;

    public PartyService(MesaRepository repository) {
        this.repository = repository;
        Party loaded;
        try {
            loaded = repository.loadParty().orElseGet(Party::new);
        } catch (IOException ex) {
            log.warn("Não foi possível carregar a party salva, começando uma nova: " + ex.getMessage());
            loaded = new Party();
        }
        this.party = loaded;
    }

    public Party getParty() {
        return party;
    }

    public void save() throws IOException {
        repository.saveParty(party);
        log.info("Party salva: " + party.getName());
    }

    /** Substitui a Party inteira por uma recebida da rede (ver
     * com.vttcaseiro.net.NetMessage "party_sync") -- mesma ideia de
     * SceneService.replaceScene pra cena. Nao salva sozinho; quem
     * recebeu pela rede decide se quer persistir local tambem. */
    public void replaceParty(Party party) {
        this.party = party != null ? party : new Party();
    }

    public void addMember(String characterId) {
        party.addMember(characterId);
    }

    public void removeMember(String characterId) {
        party.removeMember(characterId);
    }
}

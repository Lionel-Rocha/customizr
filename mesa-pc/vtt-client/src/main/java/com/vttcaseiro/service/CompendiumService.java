package com.vttcaseiro.service;

import com.vttcaseiro.app.Log;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumEntryType;
import com.vttcaseiro.compendium.CompendiumReference;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Dono do {@link Compendium} desta mesa e ponte entre ele e as fichas.
 * E aqui (nao em CharacterSheet, nem em Compendium) que mora a logica de
 * "anexar uma entrada na ficha" e "resolver o anexo pro texto que vai pro
 * chat quando clicado" -- porque isso precisa das DUAS coisas ao mesmo
 * tempo (a ficha e o compendio), e nem a ficha nem o compendio devem
 * conhecer o outro diretamente.
 */
public class CompendiumService {
    private static final Log log = Log.of(CompendiumService.class);

    private final MesaRepository repository;
    private final Compendium compendium;

    public CompendiumService(MesaRepository repository) {
        this.repository = repository;
        List<CompendiumEntry> loaded;
        try {
            loaded = repository.loadCompendium();
        } catch (IOException ex) {
            log.warn("Não foi possível carregar o compêndio salvo, começando do zero: " + ex.getMessage());
            loaded = new java.util.ArrayList<>();
        }
        this.compendium = new Compendium(loaded);
    }

    public Compendium getCompendium() {
        return compendium;
    }

    public List<CompendiumEntry> byType(CompendiumEntryType type) {
        return compendium.byType(type);
    }

    public List<CompendiumEntry> search(String text) {
        return compendium.search(text);
    }

    /** Acrescenta entradas novas ao compendio da mesa (pulando duplicatas
     * por tipo+nome+fonte) e salva o resultado. Usado pelos importadores
     * do 5e.tools e tambem pra criar uma entrada homebrew a mao. */
    public Compendium.MergeResult addEntries(List<CompendiumEntry> incoming) throws IOException {
        Compendium.MergeResult result = compendium.merge(incoming);
        repository.saveCompendium(compendium.getEntries());
        log.info("Compêndio: " + result.added().size() + " entradas novas, "
                + result.skipped().size() + " puladas (já existiam).");
        return result;
    }

    /** Anexa a entrada na ficha (so a referencia, ver CompendiumReference)
     * -- quem chama ainda precisa salvar a ficha depois (mesmo padrao do
     * resto do projeto: o service muda o objeto em memoria, salvar e
     * uma chamada separada e explicita pro CharacterService). */
    /** Junta um compendio.json inteiro (lista de entradas, mesmo formato do
     * arquivo da mesa) -- usado pelo conteudo 2024 pronto que vem no programa. */
    public Compendium.MergeResult importCompendiumJson(String json) throws IOException {
        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<CompendiumEntry>>() { }.getType();
        List<CompendiumEntry> entries = new com.google.gson.Gson().fromJson(json, listType);
        if (entries == null) entries = List.of();
        lastRefreshed = compendium.refreshExisting(entries);
        return addEntries(entries);
    }

    private int lastRefreshed;

    /** Quantas entradas a ultima importCompendiumJson atualizou (texto corrigido). */
    public int lastRefreshedCount() {
        return lastRefreshed;
    }

    public void attachToSheet(CharacterSheet sheet, CompendiumEntry entry) {
        sheet.attachCompendiumEntry(CompendiumReference.of(entry));
    }

    /**
     * Resolve uma referencia anexada numa ficha pro texto atual da
     * entrada (sempre busca no compendio de novo, nunca usa um texto
     * congelado) e devolve pronto pra mandar no chat -- ou uma mensagem
     * de aviso se a entrada original foi apagada do compendio depois do
     * anexo.
     */
    public String chatTextFor(CompendiumReference reference) {
        Optional<CompendiumEntry> entry = compendium.findById(reference.getEntryId());
        if (entry.isEmpty()) {
            return reference.getDisplayName() + " (entrada removida do compêndio)";
        }
        return entry.get().toChatText();
    }
}

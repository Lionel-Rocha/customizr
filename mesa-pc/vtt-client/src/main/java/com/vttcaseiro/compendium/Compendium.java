package com.vttcaseiro.compendium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Todo o conteudo importado (ou escrito a mao) desta mesa: classes,
 * subclasses, antecedentes, especies, feats, itens e magias, numa lista
 * so. "Compendio" no mesmo sentido que Roll20/Foundry usam a palavra --
 * um catalogo que a ficha referencia (ver CompendiumReference) em vez de
 * copiar.
 * <p>
 * Nao guarda {@code com.vttcaseiro.content.DndClass} aqui ainda -- isso
 * continua no arquivo separado classes.json/ClassImportService de uma
 * rodada anterior, pra nao arriscar um refactor grande sem tela pra
 * testar. Juntar os dois (fazer DndClass virar uma CompendiumEntry de
 * verdade, nao so compartilhar o conceito) e um passo futuro razoavel,
 * nao feito de proposito aqui.
 */
public class Compendium {

    private final List<CompendiumEntry> entries;

    public Compendium() {
        this(new ArrayList<>());
    }

    public Compendium(List<CompendiumEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public List<CompendiumEntry> getEntries() {
        return entries;
    }

    public List<CompendiumEntry> byType(CompendiumEntryType type) {
        List<CompendiumEntry> result = new ArrayList<>();
        for (CompendiumEntry entry : entries) {
            if (entry.getType() == type) {
                result.add(entry);
            }
        }
        return result;
    }

    public Optional<CompendiumEntry> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return entries.stream().filter(e -> id.equals(e.getId())).findFirst();
    }

    /** Busca livre por nome ou tag, sem diferenciar maiusculas/minusculas
     * nem acento (acento nao e normalizado de proposito ainda -- a maior
     * parte do conteudo do 5e.tools vem em ingles sem acento; se isso
     * incomodar com conteudo em portugues, normalizar acento e um ajuste
     * pequeno aqui). */
    public List<CompendiumEntry> search(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>(entries);
        }
        String needle = text.toLowerCase(Locale.ROOT).trim();
        List<CompendiumEntry> result = new ArrayList<>();
        for (CompendiumEntry entry : entries) {
            boolean nameMatches = entry.getName() != null && entry.getName().toLowerCase(Locale.ROOT).contains(needle);
            boolean tagMatches = entry.getTags().stream()
                    .anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
            if (nameMatches || tagMatches) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Busca EXATA (sem diferenciar maiusculas/minusculas, sem
     * normalizar acento) por tipo+nome -- usado pelos importadores do
     * 5e.tools pra ligar entradas de tipos diferentes que se referenciam
     * entre si (ex.: um antecedente que concede um feat, uma especie que
     * concede uma magia -- ver BackgroundImporter/SpeciesImporter). So
     * acha a relacionada se ela JA tiver sido importada antes (ver
     * HANDOFF.md pra ordem recomendada de importacao). */
    public Optional<CompendiumEntry> findByTypeAndName(CompendiumEntryType type, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String needle = name.toLowerCase(Locale.ROOT).trim();
        for (CompendiumEntry entry : entries) {
            if (entry.getType() == type && entry.getName() != null
                    && entry.getName().toLowerCase(Locale.ROOT).trim().equals(needle)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Entradas relacionadas a uma entrada dada (ver
     * CompendiumEntry.getRelatedEntryIds()) -- isto e o que a tela usa
     * pra desenhar "ver tambem" dentro da descricao de um item/feat/magia. */
    public List<CompendiumEntry> relatedTo(CompendiumEntry entry) {
        List<CompendiumEntry> result = new ArrayList<>();
        for (String relatedId : entry.getRelatedEntryIds()) {
            findById(relatedId).ifPresent(result::add);
        }
        return result;
    }

    public record MergeResult(List<CompendiumEntry> added, List<CompendiumEntry> skipped) {
    }

    /**
     * Acrescenta as entradas novas que ainda nao existem (mesmo
     * tipo+nome+fonte = duplicata -- reimportar o mesmo arquivo do
     * 5e.tools duas vezes nao duplica nada) e devolve o que foi
     * realmente adicionado vs. pulado, igual ClassImporter.importClasses
     * ja faz pra classes. Muda esta instancia (a lista interna), nao
     * devolve uma copia.
     */
    public MergeResult merge(List<CompendiumEntry> incoming) {
        List<CompendiumEntry> added = new ArrayList<>();
        List<CompendiumEntry> skipped = new ArrayList<>();
        for (CompendiumEntry candidate : incoming) {
            boolean duplicate = entries.stream().anyMatch(existing -> sameContent(existing, candidate));
            if (duplicate) {
                skipped.add(candidate);
            } else {
                entries.add(candidate);
                added.add(candidate);
            }
        }
        return new MergeResult(added, skipped);
    }

    /**
     * Pras entradas que JA existem (mesmo tipo+nome+fonte), troca o
     * conteudo pelo novo quando o texto mudou -- mantendo o id antigo,
     * que e o que as fichas guardam. Usado pelo conteudo 2024 que vem no
     * programa: uma versao nova corrige texto (ex.: o aumento por nivel
     * das magias) e "Carregar conteudo 2024" de novo atualiza a mesa.
     * Devolve quantas foram atualizadas.
     */
    public int refreshExisting(List<CompendiumEntry> incoming) {
        int updated = 0;
        for (CompendiumEntry candidate : incoming) {
            for (int i = 0; i < entries.size(); i++) {
                CompendiumEntry existing = entries.get(i);
                if (sameContent(existing, candidate)
                        && !Objects.equals(existing.getFullText(), candidate.getFullText())) {
                    candidate.setId(existing.getId());
                    entries.set(i, candidate);
                    updated++;
                    break;
                }
            }
        }
        return updated;
    }

    private static boolean sameContent(CompendiumEntry a, CompendiumEntry b) {
        return a.getType() == b.getType()
                && equalsIgnoreCaseSafe(a.getName(), b.getName())
                && equalsIgnoreCaseSafe(a.getSource(), b.getSource());
    }

    private static boolean equalsIgnoreCaseSafe(String a, String b) {
        return Objects.equals(
                a == null ? "" : a.toLowerCase(Locale.ROOT).trim(),
                b == null ? "" : b.toLowerCase(Locale.ROOT).trim());
    }
}

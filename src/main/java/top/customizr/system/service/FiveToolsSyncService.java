package top.customizr.system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import top.customizr.system.dto.FiveToolsSyncResponse;
import top.customizr.system.dto.UpdateItemsFromDumpResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Source;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.SourceRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Atualiza o catalogo de itens direto dos dados publicos do 5etools.
 *
 * <ol>
 *   <li>Le {@code books.json} e {@code adventures.json}: cada livro/aventura vira (ou atualiza) uma {@link Source},
 *       com o nome completo e se e oficial (autor da Wizards: "Wizards RPG Team", "Wizards of the Coast").</li>
 *   <li>Le {@code items-base.json} e {@code items.json} e manda pro {@link ItemService#updateFromDump}: cria o que
 *       falta e atualiza o resto, com preco oficial, valor do DMG 2024 e categoria.</li>
 * </ol>
 * Material nao oficial (parceiros, terceiros, fontes que nao estao na lista) so entra se o mestre pedir.
 * Os itens proprios do mestre ficam na campanha dele e nao sao tocados. O conteudo vai so pro banco.
 */
@Service
public class FiveToolsSyncService {

    private static final List<String> SOURCE_FILES = List.of("books.json:book", "adventures.json:adventure");
    private static final List<String> ITEM_FILES = List.of("items-base.json", "items.json");

    private final SourceRepository sourceRepository;
    private final CampaignRepository campaignRepository;
    private final ItemService itemService;
    private final MasterGuard masterGuard;
    private final ObjectMapper json = new ObjectMapper();
    private final String dataUrl;
    /** Criado so na primeira atualizacao (abrir o cliente HTTP na subida do servidor nao serve pra nada). */
    private HttpClient http;

    public FiveToolsSyncService(SourceRepository sourceRepository, CampaignRepository campaignRepository,
                                ItemService itemService, MasterGuard masterGuard,
                                @Value("${fivetools.data-url:https://raw.githubusercontent.com/5etools-mirror-3/5etools-src/main/data/}")
                                String dataUrl) {
        this.sourceRepository = sourceRepository;
        this.campaignRepository = campaignRepository;
        this.itemService = itemService;
        this.masterGuard = masterGuard;
        this.dataUrl = dataUrl.endsWith("/") ? dataUrl : dataUrl + "/";
    }

    /** Mestre de uma campanha dispara a atualizacao (o catalogo e de todo o servidor). */
    public FiveToolsSyncResponse sync(UUID campaignId, boolean includeUnofficial, String username) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        masterGuard.requireMaster(username, campaign);
        return syncWith(this::download, includeUnofficial);
    }

    /** O mesmo, com quem le os arquivos trocavel (testes, ou uma pasta local do 5etools). */
    public FiveToolsSyncResponse syncWith(Function<String, JsonNode> loader, boolean includeUnofficial) {
        // 1) fontes: nome e oficial/nao oficial
        Map<String, Boolean> officialByCode = new HashMap<>();
        int officialSources = 0;
        int unofficialSources = 0;
        for (String spec : SOURCE_FILES) {
            String[] parts = spec.split(":");
            JsonNode list = loader.apply(parts[0]).path(parts[1]);
            for (JsonNode book : list) {
                String code = upper(text(book, "source"));
                if (code == null) continue;
                boolean official = isWizards(text(book, "author"));
                officialByCode.put(code, official);
                if (official) officialSources++; else unofficialSources++;
                if (official || includeUnofficial) {
                    upsertSource(code, firstNonBlank(text(book, "name"), code), official);
                }
            }
        }

        // 2) itens: filtra os nao oficiais e manda pro importador (cria ou atualiza)
        ObjectNode payload = json.createObjectNode();
        int filteredOut = 0;
        for (String file : ITEM_FILES) {
            JsonNode data = loader.apply(file);
            for (String key : List.of("baseitem", "item")) {
                ArrayNode target = payload.has(key) ? (ArrayNode) payload.get(key) : payload.putArray(key);
                for (JsonNode entry : data.path(key)) {
                    String code = upper(text(entry, "source"));
                    if (code == null) continue;
                    Boolean official = officialByCode.get(code);
                    if (!Boolean.TRUE.equals(official)) {
                        if (!includeUnofficial) {
                            filteredOut++;
                            continue;
                        }
                        if (official == null) upsertSource(code, code, false);   // fonte fora da lista: nao oficial
                    }
                    target.add(entry);
                }
            }
        }
        UpdateItemsFromDumpResponse result = itemService.updateFromDump(payload);
        return new FiveToolsSyncResponse(officialSources, unofficialSources, result.created().size(), result.updated().size(),
                result.skipped().size(), filteredOut, includeUnofficial);
    }

    private void upsertSource(String code, String name, boolean official) {
        sourceRepository.getSourceByCode(code).ifPresentOrElse(source -> {
            source.describe(name, official);
            sourceRepository.save(source);
        }, () -> sourceRepository.save(new Source(name, code, official)));
    }

    /** Livro da Wizards? (o 5etools usa "Wizards RPG Team"; versoes antigas, "Wizards of the Coast"). */
    static boolean isWizards(String author) {
        return author != null && author.toLowerCase(Locale.ROOT).contains("wizards");
    }

    private JsonNode download(String file) {
        if (dataUrl.startsWith("file:")) {   // pasta data/ local do 5etools (sem internet)
            try {
                return json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(URI.create(dataUrl + file))));
            } catch (IOException | RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read " + file + " from " + dataUrl);
            }
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataUrl + file)).timeout(Duration.ofSeconds(90))
                .header("User-Agent", "Customizr").GET().build();
        try {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "5etools returned " + response.statusCode() + " for " + file);
            }
            return json.readTree(response.body());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach 5etools: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while reading 5etools");
        }
    }

    private synchronized HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return http;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }
}

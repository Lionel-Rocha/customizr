package com.vttcaseiro.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.MapObject;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.repository.MesaRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Modulo: um pacote com parte de uma mesa (cenas, monstros, fichas de PdM, diario e o conteudo do mestre da
 * Campanha) pra levar pra outra mesa ou outra campanha. Diferente do backup (a mesa inteira, vira mesa nova):
 * o modulo entra na mesa aberta, sem apagar nada; nome repetido ganha " 2".
 * <p>
 * Formato: zip com {@code modulo.json} e as imagens usadas em {@code imagens/<id>}.
 */
public final class ModuleService {
    public static final String EXTENSION = ".customizr-modulo";
    static final String JSON = "modulo.json";
    static final String IMAGES = "imagens/";
    private static final long MAX_TOTAL = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModuleService() {
    }

    /** O que vai no modulo (nomes como aparecem nas listas da mesa). */
    public record Selection(List<String> scenes, List<String> mobs, List<String> characters, boolean handouts,
                            boolean campaignContent) {
    }

    /** O conteudo do modulo.json. */
    static class Module {
        String format = "customizr-modulo";
        int version = 1;
        String name = "";
        List<NamedScene> scenes = new ArrayList<>();
        List<MobSheet> mobs = new ArrayList<>();
        List<CharacterSheet> characters = new ArrayList<>();
        List<Handout> handouts = new ArrayList<>();
        List<Campaign.Npc> npcs = new ArrayList<>();
        List<Campaign.Adventure> adventures = new ArrayList<>();
        List<Campaign.Parcel> parcels = new ArrayList<>();
        List<Campaign.PreparedEncounter> encounters = new ArrayList<>();
    }

    static class NamedScene {
        String name;
        SceneModel scene;

        NamedScene() {
        }

        NamedScene(String name, SceneModel scene) {
            this.name = name;
            this.scene = scene;
        }
    }

    public record Result(int scenes, int mobs, int characters, int handouts, int campaignItems, int images) {
        public String text() {
            List<String> parts = new ArrayList<>();
            if (scenes > 0) parts.add(scenes + " cena(s)");
            if (mobs > 0) parts.add(mobs + " monstro(s)");
            if (characters > 0) parts.add(characters + " ficha(s)");
            if (handouts > 0) parts.add(handouts + " texto(s) do diário");
            if (campaignItems > 0) parts.add(campaignItems + " item(ns) da Campanha");
            if (images > 0) parts.add(images + " imagem(ns)");
            return parts.isEmpty() ? "nada" : String.join(", ", parts);
        }
    }

    // ------------------------------------------------------------------ exportar

    public static Result export(MesaRepository repo, HandoutStore handouts, Campaign campaign, Selection sel,
                                String moduleName, Path zip) throws IOException {
        Module m = new Module();
        m.name = moduleName == null ? "" : moduleName;
        Set<String> assets = new LinkedHashSet<>();
        for (String name : sel.scenes()) {
            SceneModel s = repo.loadMap(name);
            m.scenes.add(new NamedScene(name, s));
            collect(assets, s.getBackgroundImageAssetId());
            for (TokenModel t : s.getTokens()) collect(assets, t.getImageAssetId());
            for (MapObject o : s.getObjects()) collect(assets, o.getAssetId());
        }
        for (String name : sel.mobs()) {
            MobSheet mob = repo.loadMob(name);
            m.mobs.add(mob);
            collect(assets, mob.getPortraitImageAssetId());
            collectTemplate(assets, mob.getTemplate());
        }
        for (String name : sel.characters()) {
            CharacterSheet c = repo.loadCharacter(name);
            m.characters.add(c);
            collect(assets, c.getPortraitImageAssetId());
            collectTemplate(assets, c.getTemplate());
        }
        if (sel.handouts() && handouts != null) {
            for (Handout h : handouts.load()) {
                m.handouts.add(h);
                collect(assets, h.getImageAssetId());
            }
        }
        if (sel.campaignContent() && campaign != null) {
            m.npcs.addAll(campaign.getNpcs());
            m.adventures.addAll(campaign.getAdventures());
            m.parcels.addAll(campaign.getParcels());
            m.encounters.addAll(campaign.getEncounters());
            for (Campaign.PreparedEncounter e : campaign.getEncounters()) {
                for (TokenModel t : e.tokens) collect(assets, t.getImageAssetId());
            }
        }
        int images = 0;
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
            out.putNextEntry(new ZipEntry(JSON));
            out.write(GSON.toJson(m).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (String id : assets) {
                if (!repo.getImageAssetStore().exists(id)) continue; // imagem que ainda nao chegou: vai sem
                out.putNextEntry(new ZipEntry(IMAGES + id));
                out.write(repo.getImageAssetStore().read(id));
                out.closeEntry();
                images++;
            }
        }
        Files.move(tmp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Result(m.scenes.size(), m.mobs.size(), m.characters.size(), m.handouts.size(),
                m.npcs.size() + m.adventures.size() + m.parcels.size() + m.encounters.size(), images);
    }

    private static void collectTemplate(Set<String> out, com.vttcaseiro.character.SheetTemplate t) {
        if (t == null) return;
        for (com.vttcaseiro.character.SheetTemplate.Page p : t.getPages()) collect(out, p.assetId);
    }

    private static void collect(Set<String> out, String assetId) {
        if (assetId != null && ImageAssetStore.isValidAssetId(assetId)) out.add(assetId);
    }

    // ------------------------------------------------------------------ importar

    /**
     * Junta o modulo na mesa aberta. Nada e apagado nem sobrescrito: nome repetido ganha " 2", ficha/monstro
     * com o mesmo id de um que ja existe ganha id novo. O conteudo da Campanha entra na {@code campaign} (quem
     * chama grava).
     */
    public static Result importInto(MesaRepository repo, HandoutStore handouts, Campaign campaign, Path zip) throws IOException {
        Module m = null;
        int images = 0;
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Módulo com arquivos demais.");
                String name = e.getName();
                if (e.isDirectory()) continue;
                byte[] bytes = readLimited(in, MAX_TOTAL - total);
                total += bytes.length;
                if (name.equals(JSON)) {
                    try {
                        m = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Module.class);
                    } catch (RuntimeException ex) {
                        throw new IOException("O modulo.json está corrompido: " + ex.getMessage());
                    }
                } else if (name.startsWith(IMAGES)) {
                    String id = name.substring(IMAGES.length());
                    // so nomes de asset validos (hash + extensao): nada de "../" escapando da pasta
                    if (ImageAssetStore.isValidAssetId(id)) {
                        repo.getImageAssetStore().store(bytes, id);
                        images++;
                    }
                }
            }
        }
        if (m == null || !"customizr-modulo".equals(m.format)) throw new IOException("Esse arquivo não é um módulo do Customizr.");
        int scenes = 0;
        Set<String> sceneNames = new HashSet<>(repo.listMaps());
        for (NamedScene ns : nonNull(m.scenes)) {
            if (ns == null || ns.scene == null) continue;
            String name = free(MesaRepository.fileNameOf(ns.name == null || ns.name.isBlank() ? "Cena" : ns.name), sceneNames);
            ns.scene.setName(name);
            repo.saveMap(ns.scene, name);
            sceneNames.add(name);
            scenes++;
        }
        int mobs = 0;
        Set<String> mobNames = new HashSet<>(repo.listMobs());
        Set<String> mobIds = new HashSet<>();
        for (String n : repo.listMobs()) {
            try {
                mobIds.add(repo.loadMob(n).getId());
            } catch (IOException | RuntimeException ignored) {
                // ilegivel
            }
        }
        for (MobSheet mob : nonNull(m.mobs)) {
            if (mob == null) continue;
            if (mob.getId() == null || mobIds.contains(mob.getId())) mob.assignNewId();
            String display = freeDisplay(mob.getName() == null || mob.getName().isBlank() ? "Monstro" : mob.getName(), mobNames);
            mob.setName(display);
            String name = MesaRepository.fileNameOf(display);
            repo.saveMob(mob, name);
            mobNames.add(name);
            mobIds.add(mob.getId());
            mobs++;
        }
        int chars = 0;
        Set<String> charNames = new HashSet<>(repo.listCharacters());
        Set<String> charIds = new HashSet<>();
        for (String n : repo.listCharacters()) {
            try {
                charIds.add(repo.loadCharacter(n).getId());
            } catch (IOException | RuntimeException ignored) {
                // ilegivel
            }
        }
        for (CharacterSheet c : nonNull(m.characters)) {
            if (c == null) continue;
            if (c.getId() == null || charIds.contains(c.getId())) c.assignNewId();
            c.setOwnerPlayerId(null); // a ficha chega sem dono (e PdM do mestre ate ele entregar)
            String display = freeDisplay(c.getName() == null || c.getName().isBlank() ? "Ficha" : c.getName(), charNames);
            c.setName(display);
            String name = MesaRepository.fileNameOf(display);
            repo.saveCharacter(c, name);
            charNames.add(name);
            charIds.add(c.getId());
            chars++;
        }
        int hs = 0;
        if (handouts != null && !nonNull(m.handouts).isEmpty()) {
            List<Handout> all = new ArrayList<>(handouts.load());
            Set<String> ids = new HashSet<>();
            for (Handout h : all) ids.add(h.getId());
            for (Handout h : m.handouts) {
                if (h == null || ids.contains(h.getId())) continue;
                all.add(h);
                hs++;
            }
            handouts.save(all);
        }
        int camp = 0;
        if (campaign != null) {
            for (Campaign.Npc n : nonNull(m.npcs)) {
                if (n == null) continue;
                n.id = UUID.randomUUID().toString();
                campaign.getNpcs().add(n);
                camp++;
            }
            for (Campaign.Adventure a : nonNull(m.adventures)) {
                if (a == null) continue;
                a.id = UUID.randomUUID().toString();
                a.number = campaign.nextAdventureNumber();
                campaign.getAdventures().add(a);
                camp++;
            }
            for (Campaign.Parcel p : nonNull(m.parcels)) {
                if (p == null) continue;
                p.id = UUID.randomUUID().toString();
                p.delivered = false;
                p.deliveredTo = "";
                campaign.getParcels().add(p);
                camp++;
            }
            for (Campaign.PreparedEncounter e : nonNull(m.encounters)) {
                if (e == null) continue;
                e.id = UUID.randomUUID().toString();
                campaign.getEncounters().add(e);
                camp++;
            }
            campaign.sanitize();
        }
        return new Result(scenes, mobs, chars, hs, camp, images);
    }

    private static <T> List<T> nonNull(List<T> l) {
        return l == null ? List.of() : l;
    }

    /** "Nome", ou "Nome 2", "Nome 3"... o primeiro que nao esta em {@code taken}. */
    /**
     * Nome livre mantendo acento e virgula ("Dragão" -> "Dragão 2"); taken sao nomes de arquivo (que perdem
     * acento). Antes o nome ficava o do arquivo: "Drag_o 2".
     */
    static String freeDisplay(String display, Set<String> taken) {
        if (!taken.contains(MesaRepository.fileNameOf(display))) return display;
        int n = 2;
        while (taken.contains(MesaRepository.fileNameOf(display + " " + n))) n++;
        return display + " " + n;
    }

    static String free(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;
        int n = 2;
        while (taken.contains(MesaRepository.fileNameOf(name + " " + n))) n++;
        return MesaRepository.fileNameOf(name + " " + n);
    }

    private static byte[] readLimited(InputStream in, long budget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        long read = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            read += n;
            if (read > budget) throw new IOException("Módulo grande demais.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unused")
    private static void copy(InputStream in, OutputStream out) throws IOException {
        in.transferTo(out);
    }
}

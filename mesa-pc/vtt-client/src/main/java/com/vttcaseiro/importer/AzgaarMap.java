package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.MapPin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * O arquivo de salvar do Azgaar's Fantasy Map Generator (".map", o "Save" do gerador). E texto (as vezes em gzip),
 * uma secao por linha (separadas por CRLF), sempre na mesma posicao: 0 parametros ("versao|licenca|data|semente|
 * largura|altura|id"), 1 configuracoes (JSON nas versoes novas; "a|b|c" nas antigas), 4 notas (versoes antigas),
 * 5 o desenho do mapa em SVG, 13 culturas, 14 reinos, 15 povoados, 29 religioes, 35 marcadores.
 * Coordenadas em pixels do mapa (largura x altura dos parametros). Daqui saem as faccoes da campanha, os pins
 * (povoados e marcadores), o texto do diario e o SVG pra gerar a imagem (do mundo inteiro ou de uma regiao).
 */
public final class AzgaarMap {
    private AzgaarMap() {
    }

    /** Origem dos pins (MapPin.ref), pra o editor do mundo achar o pin certo. */
    public static final String REF_STATE = "azgaar:state:";
    public static final String REF_BURG = "azgaar:burg:";
    public static final String REF_MARKER = "azgaar:marker:";

    /** Reino: nome curto e completo, forma de governo, capital (povoado), anotacao e diplomacia com os outros. */
    public record State(int id, String name, String fullName, String form, int capital, String note, List<String> diplomacy) {
        public String title() {
            return fullName != null && !fullName.isBlank() ? fullName : name;
        }
    }

    public record Burg(int id, String name, double x, double y, boolean capital, double population, int state, String note) {
    }

    public record Marker(int id, String type, String name, double x, double y, String note) {
    }

    /** Cultura ou religiao: nome, tipo e anotacao. */
    public record Group(String name, String type, String note) {
    }

    public record World(String version, String mapName, String description, int width, int height,
                        double distanceScale, String distanceUnit, int year, String era,
                        List<State> states, List<Burg> burgs, List<Marker> markers,
                        List<Group> cultures, List<Group> religions, String svg) {

        /** Nome do reino pelo numero (null = terras sem reino). */
        public String stateName(int id) {
            for (State s : states) if (s.id == id) return s.name;
            return null;
        }

        /** Quantos pes vale um pixel do mapa (pra regua: distancia de viagem). */
        public double feetPerPixel() {
            double scale = distanceScale > 0 ? distanceScale : 3;
            String u = distanceUnit == null ? "km" : distanceUnit.toLowerCase(Locale.ROOT);
            double feet = u.startsWith("mi") ? 5280 : u.equals("ft") ? 1 : u.equals("m") ? 3.28084 : 3280.84;
            return scale * feet;
        }

        /** Mesmo formato do importador do JSON "Full" (pins das cidades e texto curto ja testados la). */
        public AzgaarWorld.Result asWorldResult() {
            List<AzgaarWorld.Burg> list = new ArrayList<>();
            for (Burg b : burgs) list.add(new AzgaarWorld.Burg(b.name, b.x, b.y, b.capital, b.population, stateName(b.state)));
            return new AzgaarWorld.Result(mapName, width, height, list, journalText());
        }

        /** Reinos viram faccoes da campanha (escondidas ate o mestre revelar), com amigos e inimigos da diplomacia. */
        public List<Campaign.Faction> factions() {
            Map<Integer, Burg> burgById = new HashMap<>();
            for (Burg b : burgs) burgById.put(b.id, b);
            Map<Integer, State> byId = new HashMap<>();
            for (State s : states) byId.put(s.id, s);
            List<Campaign.Faction> out = new ArrayList<>();
            for (State s : states) {
                Campaign.Faction f = new Campaign.Faction();
                f.name = s.title();
                StringBuilder d = new StringBuilder();
                if (s.form != null && !s.form.isBlank()) d.append(s.form).append(". ");
                Burg cap = burgById.get(s.capital);
                if (cap != null) d.append("Capital: ").append(cap.name).append(". ");
                if (s.note != null) d.append("\n\n").append(s.note);
                f.description = d.toString().strip();
                List<String> allies = new ArrayList<>();
                List<String> enemies = new ArrayList<>();
                List<String> dip = s.diplomacy == null ? List.of() : s.diplomacy;
                for (int other = 0; other < dip.size(); other++) {
                    State o = byId.get(other);
                    if (o == null || o.id == s.id) continue;
                    switch (dip.get(other) == null ? "" : dip.get(other)) {
                        case "Ally", "Friendly" -> allies.add(o.name);
                        case "Vassal" -> allies.add(o.name + " (suserano)");
                        case "Suzerain" -> allies.add(o.name + " (vassalo)");
                        case "Enemy" -> enemies.add(o.name + " (guerra)");
                        case "Rival" -> enemies.add(o.name + " (rival)");
                        case "Suspicion" -> enemies.add(o.name + " (desconfiança)");
                        default -> { }
                    }
                }
                f.allies = String.join(", ", allies);
                f.enemies = String.join(", ", enemies);
                f.reputation = 0;
                f.visible = false;
                out.add(f);
            }
            return out;
        }

        /** Pins: povoados (capitais e maiores primeiro, ate maxBurgs) e marcadores (escondidos), na escala da imagem. */
        public List<MapPin> pins(int imageWidth, int imageHeight, int maxBurgs, boolean withMarkers) {
            return pinsIn(0, 0, width, height, imageWidth, imageHeight, maxBurgs, withMarkers);
        }

        /** Pins de um recorte (x, y, w, h em pixels do mapa) desenhado em imageWidth x imageHeight. */
        public List<MapPin> pinsIn(double rx, double ry, double rw, double rh, int imageWidth, int imageHeight,
                                   int maxBurgs, boolean withMarkers) {
            List<MapPin> out = new ArrayList<>();
            if (rw <= 0 || rh <= 0) return out;
            double sx = imageWidth / rw, sy = imageHeight / rh;
            List<Burg> sorted = new ArrayList<>(burgs);
            sorted.sort(Comparator.comparing(Burg::capital).thenComparingDouble(Burg::population).reversed());
            int n = 0;
            for (Burg b : sorted) {
                if (n >= maxBurgs) break;
                if (b.x < rx || b.y < ry || b.x > rx + rw || b.y > ry + rh) continue;
                String kind = b.capital ? "capital" : b.population >= 2000 ? "cidade" : "vila";
                MapPin pin = new MapPin((b.x - rx) * sx, (b.y - ry) * sy, b.name, kind);
                pin.setText(burgText(b));
                pin.setRef(REF_BURG + b.id);
                pin.setGeneratorUrl(GeneratorLinks.forPlace(b.name, kind, b.population));
                out.add(pin);
                n++;
            }
            if (withMarkers) {
                for (Marker m : markers) {
                    if (m.x < rx || m.y < ry || m.x > rx + rw || m.y > ry + rh) continue;
                    boolean generic = genericName(m.name, m.type);
                    MapPin pin = new MapPin((m.x - rx) * sx, (m.y - ry) * sy, generic ? typeLabel(m.type) : m.name,
                            markerKind(m.type));
                    pin.setText(((generic ? "" : typeLabel(m.type) + ".\n") + (m.note == null ? "" : m.note)).strip());
                    pin.setRef(REF_MARKER + m.id);
                    pin.setHidden(true); // marcador costuma ser segredo (masmorra, tesouro, monstro): o mestre revela
                    out.add(pin);
                }
            }
            return out;
        }

        /** Texto do cartao de um povoado: capital de quem / reino, populacao e a anotacao. */
        public String burgText(Burg b) {
            StringBuilder t = new StringBuilder();
            String st = stateName(b.state);
            if (b.capital) t.append("Capital").append(st != null ? " de " + st : "").append(".\n");
            else if (st != null) t.append("Reino: ").append(st).append("\n");
            if (b.population > 0) t.append("População: ~").append(thousands(b.population)).append(" habitantes\n");
            if (b.note != null) t.append("\n").append(b.note);
            return t.toString().strip();
        }

        /** Texto do diario: o mundo, os reinos, as culturas, as religioes e as maiores cidades. */
        public String journalText() {
            StringBuilder t = new StringBuilder("Mundo gerado no Azgaar's Fantasy Map Generator.\n");
            if (description != null && !description.isBlank()) t.append("\n").append(description).append("\n");
            if (year > 0) t.append("\nAno ").append(year).append(era == null || era.isBlank() ? "" : " (" + era + ")").append(".\n");
            Map<Integer, Burg> burgById = new HashMap<>();
            for (Burg b : burgs) burgById.put(b.id, b);
            if (!states.isEmpty()) {
                t.append("\nREINOS\n");
                for (State s : states) {
                    Burg cap = burgById.get(s.capital);
                    t.append("- ").append(s.title()).append(cap != null ? " (capital: " + cap.name + ")" : "").append('\n');
                }
            }
            appendGroups(t, "CULTURAS", cultures);
            appendGroups(t, "RELIGIÕES", religions);
            List<Burg> biggest = new ArrayList<>(burgs);
            biggest.sort(Comparator.comparingDouble(Burg::population).reversed());
            if (!biggest.isEmpty()) {
                t.append("\nMAIORES CIDADES\n");
                for (Burg b : biggest.subList(0, Math.min(25, biggest.size()))) {
                    t.append("- ").append(b.name);
                    String st = stateName(b.state);
                    if (st != null) t.append(", ").append(st);
                    if (b.population > 0) t.append(" (~").append(thousands(b.population)).append(" hab.)");
                    t.append('\n');
                }
                t.append("\n").append(burgs.size()).append(" povoados no total.");
            }
            return t.toString().strip();
        }

        private static void appendGroups(StringBuilder t, String title, List<Group> groups) {
            if (groups.isEmpty()) return;
            t.append("\n").append(title).append("\n");
            for (Group g : groups) {
                t.append("- ").append(g.name);
                if (g.type != null && !g.type.isBlank()) t.append(" (").append(g.type).append(")");
                if (g.note != null) t.append(": ").append(g.note.replace('\n', ' '));
                t.append('\n');
            }
        }
    }

    /** O .map e um arquivo do Azgaar? (texto que comeca com "versao|File can be loaded in azgaar...", ou gzip) */
    public static boolean looksLike(byte[] raw) {
        try {
            String head = text(raw);
            return head.contains("azgaar.github.io") || head.split("\r\n", 2)[0].matches("[0-9][0-9.]*\\|.*");
        } catch (IOException ex) {
            return false;
        }
    }

    public static World read(byte[] raw) throws IOException {
        String content = text(raw);
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        String[] d = content.split("\r\n", -1);
        if (d.length < 16 || !d[0].contains("|")) {
            throw new IOException("Esse arquivo não parece um mapa salvo do Azgaar (o \".map\" do Save).");
        }
        String[] params = d[0].split("\\|", -1);
        String version = params[0];
        int width = parseInt(params.length > 4 ? params[4] : null, 0);
        int height = parseInt(params.length > 5 ? params[5] : null, 0);

        String mapName = null;
        String description = null;
        double distanceScale = 3;
        String distanceUnit = "km";
        double popRate = 1000;
        double urbanization = 1;
        int year = 0;
        String era = null;
        String settings = d[1];
        if (settings.startsWith("{")) {
            JsonObject s = parseObject(settings);
            if (s != null) {
                mapName = Json.str(s, "lore.name");
                description = Json.stripHtml(Json.str(s, "lore.description"));
                year = Json.intOr(s, "lore.calendar.year", 0);
                era = Json.str(s, "lore.calendar.era");
                Double ds = Json.num(s, "units.distance.scale");
                if (ds != null && ds > 0) distanceScale = ds;
                String du = Json.str(s, "units.distance.unit");
                if (du != null) distanceUnit = du;
                Double pr = Json.num(s, "units.population.scale");
                if (pr != null && pr > 0) popRate = pr;
                Double ur = Json.num(s, "units.population.urbanization.rate");
                if (ur != null && ur > 0) urbanization = ur;
            }
        } else {
            // versoes antigas: unidade|escala|...|populacao(12)|urbanizacao(13)|...|nome do mapa(20)
            String[] s = settings.split("\\|", -1);
            if (s.length > 1) distanceUnit = s[0].isBlank() ? distanceUnit : s[0];
            distanceScale = parseDouble(s.length > 1 ? s[1] : null, distanceScale);
            popRate = parseDouble(s.length > 12 ? s[12] : null, popRate);
            urbanization = parseDouble(s.length > 13 ? s[13] : null, urbanization);
            if (s.length > 20 && !s[20].isBlank()) mapName = s[20].strip();
        }
        if (mapName == null || mapName.isBlank()) mapName = "Mundo";

        String svg = d.length > 5 && d[5].contains("<svg") ? d[5] : null;
        return build(version, mapName, description, width, height, distanceScale, distanceUnit, popRate, urbanization,
                year, era, parseArray(d.length > 4 ? d[4] : ""), parseArray(d[14]), parseArray(d[15]),
                parseArray(d.length > 35 ? d[35] : ""), parseArray(d[13]), parseArray(d.length > 29 ? d[29] : ""), svg);
    }

    /** Monta o mundo a partir das listas (o .map e o JSON do Azgaar trazem os mesmos objetos). */
    static World build(String version, String mapName, String description, int width, int height, double distanceScale,
                       String distanceUnit, double popRate, double urbanization, int year, String era, JsonArray notes,
                       JsonArray stateArr, JsonArray burgArr, JsonArray markerArr, JsonArray cultureArr, JsonArray religionArr,
                       String svg) {
        // anotacoes das versoes antigas: [{id:"burg3"|"state2"|"marker5", name, legend}]
        Map<String, String> oldNotes = new HashMap<>();
        Map<String, String> oldNames = new HashMap<>();
        for (JsonElement el : notes) {
            if (!el.isJsonObject()) continue;
            JsonObject n = el.getAsJsonObject();
            String id = Json.str(n, "id");
            String legend = Json.stripHtml(Json.str(n, "legend"));
            if (id != null && legend != null) oldNotes.put(id, legend);
            if (id != null && Json.str(n, "name") != null) oldNames.put(id, Json.str(n, "name"));
        }

        List<State> states = new ArrayList<>();
        for (JsonElement el : stateArr) {
            if (!el.isJsonObject()) continue;
            JsonObject s = el.getAsJsonObject();
            int i = Json.intOr(s, "i", 0);
            String name = Json.str(s, "name");
            if (i == 0 || name == null || s.has("removed")) continue; // 0 = terras sem reino
            List<String> dip = new ArrayList<>();
            for (JsonElement x : Json.arr(s, "diplomacy")) dip.add(x.isJsonPrimitive() ? x.getAsString() : null);
            states.add(new State(i, name, Json.str(s, "fullName"), Json.str(s, "form"), Json.intOr(s, "capital", 0),
                    note(s, oldNotes, "state" + i), dip));
        }

        List<Burg> burgs = new ArrayList<>();
        for (JsonElement el : burgArr) {
            if (!el.isJsonObject()) continue;
            JsonObject b = el.getAsJsonObject();
            String name = Json.str(b, "name");
            Double x = Json.num(b, "x"), y = Json.num(b, "y");
            if (name == null || x == null || y == null || b.has("removed")) continue; // burgs[0] e um espaco vazio
            int i = Json.intOr(b, "i", 0);
            double pop = (Json.num(b, "population") == null ? 0 : Json.num(b, "population")) * popRate * urbanization;
            burgs.add(new Burg(i, name, x, y, Json.intOr(b, "capital", 0) == 1, pop, Json.intOr(b, "state", 0),
                    note(b, oldNotes, "burg" + i)));
        }

        List<Marker> markers = new ArrayList<>();
        for (JsonElement el : markerArr) {
            if (!el.isJsonObject()) continue;
            JsonObject m = el.getAsJsonObject();
            Double x = Json.num(m, "x"), y = Json.num(m, "y");
            if (x == null || y == null) continue;
            int i = Json.intOr(m, "i", 0);
            String name = Json.str(m, "name");
            if (name == null) name = oldNames.get("marker" + i); // nas versoes antigas o nome do marcador fica na nota
            markers.add(new Marker(i, Json.str(m, "type"), name, x, y, note(m, oldNotes, "marker" + i)));
        }

        return new World(version, mapName == null || mapName.isBlank() ? "Mundo" : mapName, description, width, height,
                distanceScale, distanceUnit, year, era, states, burgs, markers,
                groups(cultureArr, oldNotes, "culture"), groups(religionArr, oldNotes, "religion"), svg);
    }

    private static List<Group> groups(JsonArray arr, Map<String, String> oldNotes, String prefix) {
        List<Group> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            int i = Json.intOr(c, "i", 0);
            String name = Json.str(c, "name");
            if (i == 0 || name == null || c.has("removed")) continue; // 0 = "Wildlands" / "No religion"
            String type = Json.str(c, "type");
            String form = Json.str(c, "form");
            out.add(new Group(name, form != null ? form : type, note(c, oldNotes, prefix + i)));
        }
        return out;
    }

    /** Anotacao da entidade: o campo "note" (versoes novas, HTML) ou a nota antiga com o mesmo id. */
    private static String note(JsonObject o, Map<String, String> oldNotes, String key) {
        String n = Json.stripHtml(Json.str(o, "note"));
        return n != null ? n : oldNotes.get(key);
    }

    /** Tipos de marcador do Azgaar em portugues. */
    private static final Map<String, String> TYPES_PT = Map.ofEntries(
            Map.entry("volcanoes", "Vulcão"), Map.entry("hot-springs", "Fontes termais"), Map.entry("water-sources", "Nascente"),
            Map.entry("mines", "Mina"), Map.entry("bridges", "Ponte"), Map.entry("inns", "Taverna"),
            Map.entry("lighthouses", "Farol"), Map.entry("waterfalls", "Cachoeira"), Map.entry("battlefields", "Campo de batalha"),
            Map.entry("dungeons", "Masmorra"), Map.entry("lake-monsters", "Monstro do lago"), Map.entry("sea-monsters", "Monstro marinho"),
            Map.entry("hill-monsters", "Monstro das colinas"), Map.entry("sacred-mountains", "Montanha sagrada"),
            Map.entry("sacred-forests", "Floresta sagrada"), Map.entry("sacred-pineries", "Pinheiral sagrado"),
            Map.entry("sacred-palm-groves", "Palmeiral sagrado"), Map.entry("brigands", "Bandidos"), Map.entry("pirates", "Piratas"),
            Map.entry("statues", "Estátua"), Map.entry("ruins", "Ruínas"), Map.entry("libraries", "Biblioteca"),
            Map.entry("circuses", "Circo"), Map.entry("jousts", "Torneio"), Map.entry("fairs", "Feira"), Map.entry("canoes", "Canoas"),
            Map.entry("migration", "Migração"), Map.entry("dances", "Festa"), Map.entry("mirage", "Miragem"), Map.entry("caves", "Caverna"),
            Map.entry("portals", "Portal"), Map.entry("rifts", "Fenda"), Map.entry("disturbed-burials", "Túmulos violados"),
            Map.entry("necropolises", "Necrópole"), Map.entry("encounters", "Encontro"), Map.entry("custom", "Marcador"));

    static String typeLabel(String type) {
        if (type == null || type.isBlank()) return "Marcador";
        String pt = TYPES_PT.get(type.toLowerCase(Locale.ROOT));
        if (pt != null) return pt;
        String t = type.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Nome pra mostrar do marcador: o nome dele, ou o tipo em portugues quando o nome e generico. */
    public static String markerTitle(Marker m) {
        return genericName(m.name(), m.type()) ? typeLabel(m.type()) : m.name();
    }

    /** O nome e so o tipo em ingles ("Volcanoes", "Hot springs")? Entao nao e um nome de verdade. */
    static boolean genericName(String name, String type) {
        if (name == null || name.isBlank()) return true;
        if (type == null) return false;
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        String t = type.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return n.equals(t);
    }

    /** Tipo de marcador do Azgaar -> icone de pin daqui. */
    static String markerKind(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.matches(".*(dungeon|cave|ruin|necropol|burial|rift|portal|tomb).*")) return "masmorra";
        if (t.matches(".*(inn|tavern).*")) return "taverna";
        if (t.matches(".*(sacred|temple|shrine|monaster|church|statue).*")) return "templo";
        if (t.matches(".*(treasure|mine|hoard).*")) return "tesouro";
        if (t.matches(".*(fair|market|trade).*")) return "loja";
        if (t.matches(".*(monster|brigand|pirate|encounter|battle|volcano|bandit|disturbed).*")) return "perigo";
        return "info";
    }

    private static String thousands(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v)).replace(',', '.');
    }

    /** Texto do arquivo (descompacta se vier em gzip). Limite pra arquivo malfeito nao estourar a memoria. */
    static String text(byte[] raw) throws IOException {
        if (raw == null) throw new IOException("Arquivo vazio.");
        if (raw.length > 1 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                byte[] out = in.readNBytes(400 * 1024 * 1024);
                return new String(out, StandardCharsets.UTF_8);
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static JsonObject parseObject(String s) {
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static JsonArray parseArray(String s) {
        if (s == null || !s.startsWith("[")) return new JsonArray();
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
        } catch (RuntimeException ex) {
            return new JsonArray();
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * O SVG do mapa ajustado pra desenhar so um recorte (x, y, w, h em pixels do mapa) em outW x outH: troca a
     * largura, a altura e o viewBox da tag raiz.
     */
    public static String svgRegion(String svg, double x, double y, double w, double h, int outW, int outH) {
        int start = svg.indexOf("<svg");
        int end = start < 0 ? -1 : svg.indexOf('>', start);
        if (start < 0 || end < 0) return svg;
        String tag = svg.substring(start, end);
        tag = tag.replaceAll("\\s(width|height|viewBox)=\"[^\"]*\"", "");
        tag = tag + String.format(Locale.ROOT, " width=\"%d\" height=\"%d\" viewBox=\"%.2f %.2f %.2f %.2f\"",
                outW, outH, x, y, w, h);
        return svg.substring(0, start) + tag + svg.substring(end);
    }
}

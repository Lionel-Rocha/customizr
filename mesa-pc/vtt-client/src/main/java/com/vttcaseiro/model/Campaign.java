package com.vttcaseiro.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gerenciador de campanha do mestre (inspirado no "Gerenciador de Campanha" do Firecast): jogadores com XP e ouro
 * lancados, calendario com clima, PdMs, aventuras, registro das sessoes e anotacoes. Cada item tem "visivel aos
 * jogadores"; o servidor so manda pros jogadores {@link #publicCopy()} (o resto nem sai do PC do mestre).
 * Fica em {@code campanha.json} na pasta da mesa.
 */
public class Campaign {
    public static final String FILE = "campanha.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Teto de itens por lista (arquivo malfeito/da rede nao estoura memoria). */
    public static final int MAX_ITEMS = 2000;
    public static final int MAX_TEXT = 20000;

    public static final String[] ATTITUDES = {"Aliado", "Amigável", "Neutro", "Desconfiado", "Hostil"};
    public static final String[] ADVENTURE_STATES = {"Ativa", "Pausada", "Completa", "Falhou"};

    // ---------------------------------------------------------------- tipos

    /** Um lancamento (XP ou ouro) com motivo. */
    public static class Entry {
        public String date = "";
        public double amount;
        public String reason = "";

        public Entry() {
        }

        public Entry(String date, double amount, String reason) {
            this.date = date;
            this.amount = amount;
            this.reason = reason;
        }
    }

    public static class PlayerRecord {
        public String id = UUID.randomUUID().toString();
        public String player = "";
        public String character = "";
        public List<Entry> xp = new ArrayList<>();
        public List<Entry> gold = new ArrayList<>();
        public int presences;
        public int absences;
        public String gmNotes = "";
        public boolean visible = true;

        public double totalXp() {
            return xp.stream().mapToDouble(e -> e.amount).sum();
        }

        public double totalGold() {
            return gold.stream().mapToDouble(e -> e.amount).sum();
        }
    }

    public static class Npc {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public String attitude = "Neutro";
        public String place = "";
        public String role = "";
        /** O que os jogadores sabem (vai pra eles se o PdM estiver visivel). */
        public String description = "";
        /** So o mestre. */
        public String gmNotes = "";
        public boolean visible;
    }

    public static class Adventure {
        public String id = UUID.randomUUID().toString();
        public int number;
        public String name = "";
        public String state = "Ativa";
        public String start = "";
        public String end = "";
        public String description = "";
        public String results = "";
        /** So o mestre (segredos, ganchos). */
        public String gmNotes = "";
        public boolean visible;
    }

    public static class SessionLog {
        public String id = UUID.randomUUID().toString();
        public int number;
        public String date = "";
        public String summary = "";
        public String link = "";
        public boolean visible = true;
    }

    public static class Month {
        public String name;
        public int days;

        public Month() {
        }

        public Month(String name, int days) {
            this.name = name;
            this.days = days;
        }
    }

    public static class Season {
        public String name;
        public int startDay;
        public int tempMin;
        public int tempMax;
        /** Chance de chover num dia, 0 a 100. */
        public int rainChance;

        public Season() {
        }

        public Season(String name, int startDay, int tempMin, int tempMax, int rainChance) {
            this.name = name;
            this.startDay = startDay;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.rainChance = rainChance;
        }
    }

    public static class Calendar {
        public List<Month> months = new ArrayList<>(List.of(
                new Month("Janeiro", 30), new Month("Fevereiro", 30), new Month("Março", 30), new Month("Abril", 30),
                new Month("Maio", 30), new Month("Junho", 30), new Month("Julho", 30), new Month("Agosto", 30),
                new Month("Setembro", 30), new Month("Outubro", 30), new Month("Novembro", 30), new Month("Dezembro", 30)));
        public List<String> weekdays = new ArrayList<>(List.of("Domingo", "Segunda", "Terça", "Quarta", "Quinta",
                "Sexta", "Sábado"));
        public List<Season> seasons = new ArrayList<>(List.of(
                new Season("Verão", 1, 22, 34, 55), new Season("Outono", 91, 14, 26, 30),
                new Season("Inverno", 181, 4, 18, 15), new Season("Primavera", 271, 14, 28, 40)));
        public String moonName = "Lua";
        public int moonCycle = 28;
        /** Dias desde o dia 1 do mes 1 do ano 1 (0 = esse dia). */
        public long day;
        public String era = "";
        public long seed = new Random().nextLong();
        public boolean visible = true;
        public String notes = "";

        public int yearLength() {
            int n = 0;
            for (Month m : months) n += Math.max(1, m.days);
            return Math.max(1, n);
        }

        /** {ano, indice do mes (0..), dia do mes (1..), dia do ano (1..)}. */
        public long[] date() {
            int len = yearLength();
            long d = Math.max(0, day);
            long year = d / len + 1;
            int doy = (int) (d % len);
            int dayOfYear = doy + 1;
            for (int i = 0; i < months.size(); i++) {
                int md = Math.max(1, months.get(i).days);
                if (doy < md) return new long[]{year, i, doy + 1, dayOfYear};
                doy -= md;
            }
            return new long[]{year, 0, 1, dayOfYear};
        }

        public String dateText() {
            if (months.isEmpty()) return "Dia " + (day + 1);
            long[] d = date();
            String wd = weekdays.isEmpty() ? "" : weekdays.get((int) (Math.max(0, day) % weekdays.size())) + ", ";
            return wd + d[2] + " de " + months.get((int) d[1]).name + " do ano " + d[0] + (era.isBlank() ? "" : " " + era);
        }

        public Season season() {
            if (seasons.isEmpty()) return null;
            int doy = (int) date()[3];
            Season best = null;
            for (Season s : seasons) {
                if (s.startDay <= doy && (best == null || s.startDay > best.startDay)) best = s;
            }
            if (best != null) return best;
            // antes da primeira: e a ultima do ano anterior
            Season last = seasons.get(0);
            for (Season s : seasons) if (s.startDay > last.startDay) last = s;
            return last;
        }

        public String moonPhase() {
            String[] phases = {"Nova", "Crescente", "Quarto crescente", "Crescente gibosa", "Cheia", "Minguante gibosa",
                    "Quarto minguante", "Minguante"};
            int cycle = Math.max(2, moonCycle);
            int pos = (int) (Math.max(0, day) % cycle);
            return phases[Math.min(7, pos * 8 / cycle)];
        }

        /** Clima do dia, sempre o mesmo pra mesma data (semente da campanha). */
        public String weather() {
            Season s = season();
            Random r = new Random(seed * 31 + day);
            int min = s == null ? 15 : s.tempMin;
            int max = s == null ? 28 : Math.max(s.tempMin, s.tempMax);
            int low = min + r.nextInt(Math.max(1, (max - min) / 2 + 1));
            int high = Math.max(low + 2, max - r.nextInt(Math.max(1, (max - min) / 3 + 1)));
            int rain = s == null ? 30 : Math.max(0, Math.min(100, s.rainChance));
            String sky;
            if (r.nextInt(100) < rain) {
                String[] wet = {"garoa", "chuva", "chuva", "chuva forte", "tempestade"};
                sky = wet[r.nextInt(wet.length)];
                if (high <= 1) sky = sky.startsWith("tempestade") ? "nevasca" : "neve";
            } else {
                String[] dry = {"céu limpo", "céu limpo", "poucas nuvens", "nublado", "neblina pela manhã"};
                sky = dry[r.nextInt(dry.length)];
            }
            String[] winds = {"sem vento", "brisa leve", "brisa leve", "vento moderado", "vento forte"};
            String wind = winds[r.nextInt(winds.length)];
            if (sky.equals("tempestade") && r.nextBoolean()) wind = "ventania";
            return (s == null ? "" : s.name + ": ") + sky + ", " + low + " a " + high + " °C, " + wind
                    + ". " + moonName + ": " + moonPhase().toLowerCase() + ".";
        }
    }

    // ---------------------------------------------------------------- dados

    private List<PlayerRecord> players = new ArrayList<>();
    private Calendar calendar = new Calendar();
    private List<Npc> npcs = new ArrayList<>();
    private List<Adventure> adventures = new ArrayList<>();
    private List<SessionLog> sessions = new ArrayList<>();
    /** Anotacoes do mestre: nunca saem do PC dele. */
    private String gmNotes = "";

    public List<PlayerRecord> getPlayers() { return players == null ? players = new ArrayList<>() : players; }
    public Calendar getCalendar() { return calendar == null ? calendar = new Calendar() : calendar; }
    public List<Npc> getNpcs() { return npcs == null ? npcs = new ArrayList<>() : npcs; }
    public List<Adventure> getAdventures() { return adventures == null ? adventures = new ArrayList<>() : adventures; }
    public List<SessionLog> getSessions() { return sessions == null ? sessions = new ArrayList<>() : sessions; }
    public String getGmNotes() { return gmNotes == null ? "" : gmNotes; }
    public void setGmNotes(String notes) { this.gmNotes = clip(notes); }

    /** Proximo numero de aventura/sessao (maior + 1). */
    public int nextAdventureNumber() {
        return getAdventures().stream().mapToInt(a -> a.number).max().orElse(0) + 1;
    }

    public int nextSessionNumber() {
        return getSessions().stream().mapToInt(a -> a.number).max().orElse(0) + 1;
    }

    /** XP pra todos os jogadores de uma vez (ex.: fim da sessao). */
    public void giveXpToAll(double amount, String date, String reason) {
        for (PlayerRecord p : getPlayers()) p.xp.add(new Entry(date, amount, reason));
    }

    public void giveGoldToAll(double amount, String date, String reason) {
        for (PlayerRecord p : getPlayers()) p.gold.add(new Entry(date, amount, reason));
    }

    /** Nivel de D&D 5e pela XP total (1 a 20). */
    public static int level5e(double xp) {
        int[] table = {0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000, 85000, 100000, 120000, 140000,
                165000, 195000, 225000, 265000, 305000, 355000};
        int lvl = 1;
        for (int i = 0; i < table.length; i++) if (xp >= table[i]) lvl = i + 1;
        return lvl;
    }

    /**
     * O que os jogadores podem ver: so itens marcados como visiveis, sem nenhuma anotacao do mestre. E isso que o
     * servidor manda pra quem nao e mestre.
     */
    public Campaign publicCopy() {
        Campaign c = GSON.fromJson(GSON.toJson(this), Campaign.class);
        c.sanitize();
        c.gmNotes = "";
        c.getPlayers().removeIf(p -> !p.visible);
        c.getPlayers().forEach(p -> p.gmNotes = "");
        c.getNpcs().removeIf(n -> !n.visible);
        c.getNpcs().forEach(n -> n.gmNotes = "");
        c.getAdventures().removeIf(a -> !a.visible);
        c.getAdventures().forEach(a -> a.gmNotes = "");
        c.getSessions().removeIf(s -> !s.visible);
        if (!c.getCalendar().visible) {
            Calendar hidden = new Calendar();
            hidden.visible = false;
            hidden.months.clear();
            hidden.weekdays.clear();
            hidden.seasons.clear();
            c.calendar = hidden;
        }
        c.getCalendar().notes = "";
        return c;
    }

    /** Corta listas e textos absurdos (arquivo malfeito ou vindo da rede). */
    public Campaign sanitize() {
        trim(getPlayers());
        trim(getNpcs());
        trim(getAdventures());
        trim(getSessions());
        for (PlayerRecord p : getPlayers()) {
            if (p.xp == null) p.xp = new ArrayList<>();
            if (p.gold == null) p.gold = new ArrayList<>();
            trim(p.xp);
            trim(p.gold);
            p.gmNotes = clip(p.gmNotes);
        }
        for (Npc n : getNpcs()) {
            n.description = clip(n.description);
            n.gmNotes = clip(n.gmNotes);
        }
        for (Adventure a : getAdventures()) {
            a.description = clip(a.description);
            a.results = clip(a.results);
            a.gmNotes = clip(a.gmNotes);
        }
        for (SessionLog s : getSessions()) s.summary = clip(s.summary);
        Calendar cal = getCalendar();
        if (cal.months == null) cal.months = new ArrayList<>();
        if (cal.weekdays == null) cal.weekdays = new ArrayList<>();
        if (cal.seasons == null) cal.seasons = new ArrayList<>();
        trim(cal.months);
        trim(cal.weekdays);
        trim(cal.seasons);
        for (Month m : cal.months) m.days = Math.max(1, Math.min(1000, m.days));
        cal.moonCycle = Math.max(2, Math.min(1000, cal.moonCycle));
        cal.day = Math.max(0, Math.min(100_000_000L, cal.day));
        gmNotes = clip(gmNotes);
        return this;
    }

    private static <T> void trim(List<T> list) {
        list.removeIf(java.util.Objects::isNull);
        while (list.size() > MAX_ITEMS) list.remove(list.size() - 1);
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    // ---------------------------------------------------------------- arquivo

    public static Campaign load(Path mesaRoot) {
        Path f = mesaRoot.resolve(FILE);
        if (!Files.exists(f)) return new Campaign();
        try {
            Campaign c = GSON.fromJson(Files.readString(f), Campaign.class);
            return c == null ? new Campaign() : c.sanitize();
        } catch (IOException | RuntimeException ex) {
            return new Campaign();
        }
    }

    public void save(Path mesaRoot) throws IOException {
        com.vttcaseiro.library.AtomicFiles.writeString(mesaRoot.resolve(FILE), GSON.toJson(this));
    }
}

package com.vttcaseiro.importer;

import com.vttcaseiro.model.Campaign;
import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.WorldEdits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Edicoes do mestre num mundo do Azgaar (nomes, cores, populacao) e os nomes do mapa como rotulos editaveis da
 * mesa. O arquivo do mundo nao muda: as edicoes ficam guardadas a parte e sao aplicadas toda vez que ele e lido.
 */
public final class AzgaarEditor {
    private AzgaarEditor() {
    }

    /** O mundo (e a geometria, se tiver) com as edicoes aplicadas. */
    public static AzgaarJson.Result apply(AzgaarJson.Result r, WorldEdits e) {
        if (e == null || e.isEmpty()) return r;
        AzgaarMap.World w = r.world();
        List<AzgaarMap.State> states = new ArrayList<>();
        for (AzgaarMap.State s : w.states()) {
            WorldEdits.StateEdit se = e.states().get(s.id());
            if (se == null) {
                states.add(s);
                continue;
            }
            states.add(new AzgaarMap.State(s.id(), pick(se.name, s.name()), se.fullName != null ? se.fullName.strip() : s.fullName(),
                    s.form(), s.capital(), s.note(), s.diplomacy()));
        }
        List<AzgaarMap.Burg> burgs = new ArrayList<>();
        for (AzgaarMap.Burg b : w.burgs()) {
            WorldEdits.BurgEdit be = e.burgs().get(b.id());
            if (be == null) {
                burgs.add(b);
                continue;
            }
            double pop = be.population != null && be.population >= 0 ? be.population : b.population();
            burgs.add(new AzgaarMap.Burg(b.id(), pick(be.name, b.name()), b.x(), b.y(), b.capital(), pop, b.state(), b.note()));
        }
        AzgaarMap.World nw = new AzgaarMap.World(w.version(), w.mapName(), w.description(), w.width(), w.height(),
                w.distanceScale(), w.distanceUnit(), w.year(), w.era(), states, burgs, w.markers(), w.cultures(),
                w.religions(), w.svg());
        AzgaarJson.Geometry g = r.geometry();
        if (g != null) {
            String[] colors = g.stateColors().clone();
            List<String> names = new ArrayList<>(g.stateNames());
            for (Map.Entry<Integer, WorldEdits.StateEdit> en : e.states().entrySet()) {
                int id = en.getKey();
                WorldEdits.StateEdit se = en.getValue();
                if (se.color != null && se.color.matches("#[0-9a-fA-F]{6}") && id > 0 && id < colors.length) colors[id] = se.color;
                int idx = g.stateIds().indexOf(id);
                if (idx >= 0 && se.name != null && !se.name.isBlank()) names.set(idx, se.name.strip());
            }
            g = g.withStates(colors, names);
        }
        return new AzgaarJson.Result(nw, g);
    }

    private static String pick(String edited, String original) {
        return edited != null && !edited.isBlank() ? edited.strip() : original;
    }

    /**
     * Os nomes do recorte (rx, ry, rw, rh) como rotulos da mesa. O desenho foi feito em renderW x renderH e a imagem
     * da cena ficou com imageW de largura (pode ter sido reduzida pra caber): as posicoes e letras acompanham.
     * Reinos: titulo grande. Povoados: o nome, com o cartao do povoado (populacao, reino) ao clicar.
     */
    public static List<MapPin> labelPins(AzgaarMap.World w, AzgaarJson.Geometry g, double rx, double ry, double rw, double rh,
                                         int renderW, int renderH, int imageW) {
        double k = imageW / (double) renderW;
        Map<Integer, AzgaarMap.Burg> byId = new HashMap<>();
        for (AzgaarMap.Burg b : w.burgs()) byId.put(b.id(), b);
        List<MapPin> out = new ArrayList<>();
        for (AzgaarRender.Placed p : AzgaarRender.placeLabels(w, g, rx, ry, rw, rh, renderW, renderH)) {
            MapPin pin = new MapPin(p.centerX() * k, p.centerY() * k, p.text(), MapPin.LABEL);
            pin.setLabelSize(Math.max(6, Math.round(p.font().getSize() * k)));
            if (p.state()) {
                pin.setLabelStyle(MapPin.STYLE_TITLE);
                pin.setRef(AzgaarMap.REF_STATE + p.id());
            } else {
                pin.setLabelStyle(MapPin.STYLE_PLACE);
                pin.setRef(AzgaarMap.REF_BURG + p.id());
                AzgaarMap.Burg b = byId.get(p.id());
                if (b != null) {
                    pin.setText(w.burgText(b));
                    String kind = b.capital() ? "capital" : b.population() >= 2000 ? "cidade" : "vila";
                    pin.setGeneratorUrl(GeneratorLinks.forPlace(b.name(), kind, b.population()));
                }
            }
            out.add(pin);
        }
        return out;
    }

    /**
     * Passa as mudancas de nome (before -> after) pros pins: acha pelo ref (ou, nos pins antigos sem ref, pelo nome
     * igual) e troca o titulo; o cartao do povoado e refeito se o mestre nao tinha mexido nele. Devolve quantos mudaram.
     */
    public static int renamePins(List<MapPin> pins, AzgaarMap.World before, AzgaarMap.World after) {
        Map<Integer, AzgaarMap.State> oldStates = new HashMap<>(), newStates = new HashMap<>();
        for (AzgaarMap.State s : before.states()) oldStates.put(s.id(), s);
        for (AzgaarMap.State s : after.states()) newStates.put(s.id(), s);
        Map<Integer, AzgaarMap.Burg> oldBurgs = new HashMap<>(), newBurgs = new HashMap<>();
        for (AzgaarMap.Burg b : before.burgs()) oldBurgs.put(b.id(), b);
        for (AzgaarMap.Burg b : after.burgs()) newBurgs.put(b.id(), b);
        int changed = 0;
        for (MapPin pin : pins) {
            String ref = pin.getRef();
            boolean done = false;
            if (ref != null && ref.startsWith(AzgaarMap.REF_STATE)) {
                AzgaarMap.State o = oldStates.get(refId(ref)), n = newStates.get(refId(ref));
                if (o != null && n != null) done = renameState(pin, o, n);
            } else if (ref != null && ref.startsWith(AzgaarMap.REF_BURG)) {
                AzgaarMap.Burg o = oldBurgs.get(refId(ref)), n = newBurgs.get(refId(ref));
                if (o != null && n != null) done = renameBurg(pin, before, after, o, n);
            } else if (ref == null) {
                for (AzgaarMap.Burg o : before.burgs()) {
                    if (!o.name().equals(pin.getTitle())) continue;
                    AzgaarMap.Burg n = newBurgs.get(o.id());
                    if (n != null) done = renameBurg(pin, before, after, o, n);
                    break;
                }
                if (!done) {
                    for (AzgaarMap.State o : before.states()) {
                        if (!o.name().equalsIgnoreCase(pin.getTitle())) continue;
                        AzgaarMap.State n = newStates.get(o.id());
                        if (n != null) done = renameState(pin, o, n);
                        break;
                    }
                }
            }
            if (done) changed++;
        }
        return changed;
    }

    private static boolean renameState(MapPin pin, AzgaarMap.State o, AzgaarMap.State n) {
        if (o.name().equals(n.name())) return false;
        boolean upper = pin.getTitle().equals(o.name().toUpperCase(Locale.ROOT)) || pin.getTitle().equals(o.name().toUpperCase());
        if (!upper && !pin.getTitle().equals(o.name())) return false; // o mestre ja tinha renomeado na mao
        pin.setTitle(upper ? n.name().toUpperCase() : n.name());
        return true;
    }

    private static boolean renameBurg(MapPin pin, AzgaarMap.World before, AzgaarMap.World after, AzgaarMap.Burg o, AzgaarMap.Burg n) {
        boolean any = false;
        if (!o.name().equals(n.name()) && pin.getTitle().equals(o.name())) {
            pin.setTitle(n.name());
            any = true;
        }
        String oldText = before.burgText(o), newText = after.burgText(n);
        if (!oldText.equals(newText) && pin.getText().equals(oldText)) {
            pin.setText(newText);
            any = true;
        }
        return any;
    }

    private static int refId(String ref) {
        try {
            return Integer.parseInt(ref.substring(ref.lastIndexOf(':') + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Faccoes com o nome antigo do reino passam pro novo (inclusive nos amigos/inimigos). Devolve quantas mudaram. */
    public static int renameFactions(List<Campaign.Faction> factions, AzgaarMap.World before, AzgaarMap.World after) {
        Map<String, String> titles = new HashMap<>(), names = new HashMap<>();
        Map<Integer, AzgaarMap.State> newStates = new HashMap<>();
        for (AzgaarMap.State s : after.states()) newStates.put(s.id(), s);
        for (AzgaarMap.State o : before.states()) {
            AzgaarMap.State n = newStates.get(o.id());
            if (n == null) continue;
            if (!o.title().equals(n.title())) titles.put(o.title(), n.title());
            if (!o.name().equals(n.name())) names.put(o.name(), n.name());
        }
        if (titles.isEmpty() && names.isEmpty()) return 0;
        int changed = 0;
        for (Campaign.Faction f : factions) {
            boolean any = false;
            if (f.name != null && titles.containsKey(f.name)) {
                f.name = titles.get(f.name);
                any = true;
            }
            for (Map.Entry<String, String> en : names.entrySet()) {
                String allies = replaceWord(f.allies, en.getKey(), en.getValue());
                String enemies = replaceWord(f.enemies, en.getKey(), en.getValue());
                if (!java.util.Objects.equals(allies, f.allies) || !java.util.Objects.equals(enemies, f.enemies)) any = true;
                f.allies = allies;
                f.enemies = enemies;
            }
            if (any) changed++;
        }
        return changed;
    }

    /** Troca o nome na lista "A (rival), B" so quando e o item inteiro (nao pedaco de outro nome). */
    static String replaceWord(String list, String from, String to) {
        if (list == null || list.isBlank()) return list;
        String[] parts = list.split(", ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(from)) parts[i] = to;
            else if (parts[i].startsWith(from + " (")) parts[i] = to + parts[i].substring(from.length());
        }
        return String.join(", ", parts);
    }
}

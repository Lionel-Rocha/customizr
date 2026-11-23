package com.vttcaseiro.view;

import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.mob.MobSheet;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Janela pra criar um monstro/NPC do zero ou editar um (ex.: a copia de um monstro importado). Os tracos e acoes
 * ficam em texto livre: um por paragrafo, "Nome. Descricao". Ataque escrito como os do livro
 * ("Ataque corpo a corpo: +4 ... Acerto: 5 (1d6 + 2) de dano cortante") vira botao de rolar na ficha.
 */
public final class MobEditor {
    public static final String[] SIZES = {"Minusculo", "Pequeno", "Medio", "Grande", "Enorme", "Imenso"};
    public static final String[] CRS = {"0", "1/8", "1/4", "1/2", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28",
            "29", "30"};

    private MobEditor() {
    }

    /**
     * Abre a janela. Devolve true se o usuario confirmou (e o {@code mob} ja foi alterado); false se cancelou
     * (o mob fica como estava).
     */
    public static boolean edit(Window owner, MobSheet mob, String title) {
        Dialog<ButtonType> d = new Dialog<>();
        if (owner != null) d.initOwner(owner);
        d.setTitle(title);
        d.setHeaderText(null);
        ButtonType ok = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField name = new TextField(mob.getName());
        ComboBox<String> size = new ComboBox<>();
        size.getItems().addAll(SIZES);
        size.setEditable(true);
        size.setValue(mob.getSize());
        TextField type = new TextField(mob.getCreatureType());
        TextField alignment = new TextField(mob.getAlignment());
        Spinner<Integer> ac = spinner(0, 40, mob.getArmorClass());
        TextField acNote = new TextField(mob.getArmorNote());
        acNote.setPromptText("ex.: armadura de couro");
        Spinner<Integer> hp = spinner(1, 9999, mob.getHitPointsAverage());
        TextField hpFormula = new TextField(mob.getHitPointsFormula());
        hpFormula.setPromptText("ex.: 2d8+2");
        Spinner<Integer> walk = spinner(0, 500, mob.getSpeedWalk());
        Spinner<Integer> fly = spinner(0, 500, mob.getSpeedFly());
        Spinner<Integer> swim = spinner(0, 500, mob.getSpeedSwim());
        Spinner<Integer> climb = spinner(0, 500, mob.getSpeedClimb());
        Spinner<Integer> burrow = spinner(0, 500, mob.getSpeedBurrow());
        Spinner<Integer> str = spinner(1, 30, mob.getStrength());
        Spinner<Integer> dex = spinner(1, 30, mob.getDexterity());
        Spinner<Integer> con = spinner(1, 30, mob.getConstitution());
        Spinner<Integer> intel = spinner(1, 30, mob.getIntelligence());
        Spinner<Integer> wis = spinner(1, 30, mob.getWisdom());
        Spinner<Integer> cha = spinner(1, 30, mob.getCharisma());
        ComboBox<String> cr = new ComboBox<>();
        cr.getItems().addAll(CRS);
        cr.setValue(crText(mob.getChallengeRating()));
        TextField senses = new TextField(String.join(", ", mob.getSenses()));
        senses.setPromptText("ex.: visão no escuro 18 m, Percepção passiva 12");
        TextField languages = new TextField(String.join(", ", mob.getLanguages()));
        TextField resist = new TextField(String.join(", ", mob.getDamageResistances()));
        TextField immune = new TextField(String.join(", ", mob.getDamageImmunities()));
        TextField vuln = new TextField(String.join(", ", mob.getDamageVulnerabilities()));
        TextField condImmune = new TextField(String.join(", ", mob.getConditionImmunities()));
        TextArea traits = area(toText(mob.getTraits()), "Faro Aguçado. Vantagem em Percepção que use o olfato.");
        TextArea actions = area(toText(mob.getActions()),
                "Mordida. Ataque corpo a corpo: +4 para acertar, alcance 1,5 m. Acerto: 6 (1d8 + 2) de dano perfurante.");
        TextArea legendary = area(toText(mob.getLegendaryActions()), "(opcional)");

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        g.addRow(r++, new Label("Nome"), name);
        g.addRow(r++, new Label("Tamanho"), size);
        g.addRow(r++, new Label("Tipo"), type);
        g.addRow(r++, new Label("Tendência"), alignment);
        g.addRow(r++, new Label("CA"), ac, new Label("nota"), acNote);
        g.addRow(r++, new Label("PV médio"), hp, new Label("fórmula"), hpFormula);
        g.addRow(r++, new Label("Desloc. (pés)"), walk, new Label("voo"), fly);
        g.addRow(r++, new Label("natação"), swim, new Label("escalada"), climb);
        g.addRow(r++, new Label("escavação"), burrow);
        g.addRow(r++, new Label("FOR"), str, new Label("DES"), dex);
        g.addRow(r++, new Label("CON"), con, new Label("INT"), intel);
        g.addRow(r++, new Label("SAB"), wis, new Label("CAR"), cha);
        g.addRow(r++, new Label("ND"), cr);
        g.addRow(r++, new Label("Sentidos"), senses);
        g.addRow(r++, new Label("Idiomas"), languages);
        g.addRow(r++, new Label("Resistências"), resist);
        g.addRow(r++, new Label("Imunidades"), immune);
        g.addRow(r++, new Label("Vulnerabilidades"), vuln);
        g.addRow(r++, new Label("Imune a condições"), condImmune);
        for (TextField wide : List.of(name, type, alignment, senses, languages, resist, immune, vuln, condImmune)) {
            GridPane.setColumnSpan(wide, 3);
        }
        Label how = new Label("Um por parágrafo (linha em branco entre eles): \"Nome. O que faz\". Ataque com "
                + "\"+4 para acertar\" e dano entre parênteses \"(1d8 + 2)\" vira botão de rolar.");
        how.setWrapText(true);
        how.getStyleClass().add("label-muted");
        VBox box = new VBox(8, g, new Label("Traços"), traits, new Label("Ações"), actions,
                new Label("Ações lendárias"), legendary, how);
        box.setPadding(new Insets(10));
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setPrefSize(620, 640);
        d.getDialogPane().setContent(sp);
        d.setResizable(true);
        javafx.application.Platform.runLater(name::requestFocus);

        Optional<ButtonType> res = d.showAndWait();
        if (res.isEmpty() || res.get() != ok) return false;
        String newName = name.getText().trim();
        mob.setName(newName.isEmpty() ? "Novo monstro" : newName);
        mob.setSize(blankTo(size.getEditor().getText(), size.getValue()));
        mob.setCreatureType(type.getText().trim());
        mob.setAlignment(alignment.getText().trim());
        mob.setArmorClass(ac.getValue());
        mob.setArmorNote(acNote.getText().trim());
        mob.setHitPointsAverage(hp.getValue());
        mob.setHitPointsFormula(hpFormula.getText().trim());
        mob.setSpeedWalk(walk.getValue());
        mob.setSpeedFly(fly.getValue());
        mob.setSpeedSwim(swim.getValue());
        mob.setSpeedClimb(climb.getValue());
        mob.setSpeedBurrow(burrow.getValue());
        mob.setStrength(str.getValue());
        mob.setDexterity(dex.getValue());
        mob.setConstitution(con.getValue());
        mob.setIntelligence(intel.getValue());
        mob.setWisdom(wis.getValue());
        mob.setCharisma(cha.getValue());
        mob.setChallengeRating(parseCr(cr.getValue()));
        setList(mob.getSenses(), senses.getText());
        setList(mob.getLanguages(), languages.getText());
        setList(mob.getDamageResistances(), resist.getText());
        setList(mob.getDamageImmunities(), immune.getText());
        setList(mob.getDamageVulnerabilities(), vuln.getText());
        setList(mob.getConditionImmunities(), condImmune.getText());
        replace(mob.getTraits(), parseEntries(traits.getText(), "trait"));
        replace(mob.getActions(), parseEntries(actions.getText(), "action"));
        replace(mob.getLegendaryActions(), parseEntries(legendary.getText(), "legendary"));
        return true;
    }

    private static Spinner<Integer> spinner(int min, int max, int value) {
        Spinner<Integer> s = new Spinner<>(min, max, Math.max(min, Math.min(max, value)));
        s.setEditable(true);
        s.setPrefWidth(90);
        // texto digitado sem Enter tambem vale (e lixo volta pro valor anterior)
        s.focusedProperty().addListener((o, was, now) -> {
            if (!now) commit(s);
        });
        return s;
    }

    private static void commit(Spinner<Integer> s) {
        try {
            s.getValueFactory().setValue(Integer.parseInt(s.getEditor().getText().trim()));
        } catch (NumberFormatException ex) {
            s.getEditor().setText(String.valueOf(s.getValue()));
        }
    }

    private static TextArea area(String text, String prompt) {
        TextArea a = new TextArea(text);
        a.setPromptText(prompt);
        a.setWrapText(true);
        a.setPrefRowCount(5);
        return a;
    }

    private static String blankTo(String text, String fallback) {
        return text == null || text.isBlank() ? (fallback == null ? "Medio" : fallback) : text.trim();
    }

    static String crText(double cr) {
        if (cr == 0.125) return "1/8";
        if (cr == 0.25) return "1/4";
        if (cr == 0.5) return "1/2";
        return String.valueOf((int) Math.round(cr));
    }

    static double parseCr(String s) {
        if (s == null || s.isBlank()) return 0;
        s = s.trim();
        try {
            if (s.contains("/")) {
                String[] p = s.split("/");
                return Double.parseDouble(p[0].trim()) / Double.parseDouble(p[1].trim());
            }
            return Math.max(0, Math.min(30, Double.parseDouble(s.replace(',', '.'))));
        } catch (NumberFormatException | ArithmeticException | ArrayIndexOutOfBoundsException ex) {
            return 0;
        }
    }

    private static void setList(List<String> target, String text) {
        target.clear();
        Arrays.stream(text.split("[,;]")).map(String::trim).filter(x -> !x.isEmpty()).forEach(target::add);
    }

    private static void replace(List<CharacterTrait> target, List<CharacterTrait> fresh) {
        target.clear();
        target.addAll(fresh);
    }

    /** Lista de tracos como texto: "Nome. Descricao", um por paragrafo. */
    public static String toText(List<CharacterTrait> list) {
        StringBuilder sb = new StringBuilder();
        for (CharacterTrait t : list) {
            if (sb.length() > 0) sb.append("\n\n");
            String n = t.getName() == null ? "" : t.getName().trim();
            String desc = t.getDescription() == null ? "" : t.getDescription().trim();
            sb.append(n.isEmpty() ? desc : n + ". " + desc);
        }
        return sb.toString();
    }

    /** O inverso de {@link #toText}: paragrafos separados por linha em branco; o nome vai ate o primeiro ponto. */
    public static List<CharacterTrait> parseEntries(String text, String category) {
        List<CharacterTrait> out = new ArrayList<>();
        if (text == null) return out;
        for (String para : text.replace("\r", "").split("\n\\s*\n")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            int dot = p.indexOf(". ");
            String n;
            String desc;
            if (dot > 0 && dot <= 60) {
                n = p.substring(0, dot).trim();
                desc = p.substring(dot + 2).trim();
            } else if (p.endsWith(".") && p.length() <= 60 && !p.substring(0, p.length() - 1).contains(".")) {
                n = p.substring(0, p.length() - 1);
                desc = "";
            } else {
                n = category.toLowerCase(Locale.ROOT).startsWith("action") ? "Ação" : "Traço";
                desc = p;
            }
            out.add(new CharacterTrait(n, desc, category));
        }
        return out;
    }
}

package com.vttcaseiro.view;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.RollKind;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.mob.MobAttack;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.mob.MobSheet;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Janela mostrando a ficha de um monstro/NPC importado (ver
 * {@code MobImporter}) ou criado na mao -- mesmo papel que
 * {@link CharacterSheetView} tem pra ficha de jogador, so que pros campos
 * que {@link MobSheet} tem (tamanho, tipo, CA/PV, as 5 velocidades,
 * resistencia/imunidade/vulnerabilidade a dano, sentidos, idiomas, CR/XP)
 * em vez dos de personagem. Clicar num traco/acao/acao-legendaria manda
 * pro chat igual a ficha de jogador (mesmo CharacterTrait.toChatText()).
 * <p>
 * So de LEITURA por enquanto -- nao tem edicao de campo nem transferencia
 * do loot pro inventario de um personagem ainda (ver HANDOFF.md).
 */
public class MobSheetView {

    /** O que a ficha de monstro pede pro resto do programa (rolar e falar no
     * chat). Quem implementa (Main) aplica as condicoes do TOKEN ligado --
     * monstro Envenenado rola ataque com desvantagem, igual personagem. */
    public interface Actions {
        void rollD20(String label, int modifier, RollKind kind, Ability ability, RollMode mode, boolean hidden);

        void roll(String label, String expression, boolean hidden);

        void chat(String text);

        /** Avisa que o ataque {@code attackLabel} tem este dano (pro botao "Rolar dano" do chat). */
        default void followUp(String attackLabel, String damageLabel, String expression) {
        }

        /**
         * Ataque num botao so: acerto e dano juntos; 20 natural dobra os dados do dano; 1 natural nao rola dano.
         * {@code damageExpression} pode ser null (ataque sem dado de dano).
         */
        default void attack(String attackLabel, int bonus, String damageLabel, String damageExpression, RollMode mode,
                            boolean hidden) {
            if (damageExpression != null) followUp(attackLabel, damageLabel, damageExpression);
            rollD20(attackLabel, bonus, RollKind.ATTACK, null, mode, hidden);
        }

        /** Iniciativa do token (entra na ordem de combate). */
        default void rollInitiative() {
        }

        /** PV do token ("12 / 18"), ou null sem token. */
        default String hpText() {
            return null;
        }

        /** Troca a imagem do token deste monstro. */
        default void changeImage() {
        }
    }

    private final MobSheet mob;
    private final String displayName;
    private final Actions actions;
    private final Consumer<CharacterTrait> onTraitClicked;
    private VBox root;
    private RollMode mode = RollMode.NORMAL;
    private boolean hidden = true;

    /** So leitura + clicar manda pro chat (como era antes). */
    public MobSheetView(MobSheet mob, Consumer<CharacterTrait> onTraitClicked) {
        this(mob, mob.getName(), null, onTraitClicked);
    }

    /**
     * @param displayName nome do token ("Goblin 2") -- vai nos rotulos das rolagens
     * @param actions     null = ficha so de leitura
     */
    public MobSheetView(MobSheet mob, String displayName, Actions actions, Consumer<CharacterTrait> onTraitClicked) {
        this.mob = mob;
        this.displayName = displayName == null ? mob.getName() : displayName;
        this.actions = actions;
        this.onTraitClicked = onTraitClicked;
    }

    public void show() {
        Stage stage = new Stage();
        Icons.applyTo(stage);
        stage.setTitle(displayName);

        root = new VBox(10);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: -c-bg;");
        refresh();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        // sem fitToHeight: com ele o conteudo era espremido na altura da janela e as listas de acoes sumiam
        scroll.setStyle("-fx-background: -c-bg; -fx-background-color: -c-bg;");
        Scene scene = new Scene(scroll, 600, 760);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void refresh() {
        root.getChildren().setAll(buildHeader(), buildCombatChips());
        if (actions != null) root.getChildren().add(buildRollControls());
        root.getChildren().addAll(sectionLabel(actions != null ? "Atributos, salvaguardas e perícias (clique pra rolar)"
                : "Atributos, salvaguardas e perícias"), buildAbilityGrid());
        String extras = buildExtrasText();
        if (!extras.isBlank()) {
            root.getChildren().add(sectionLabel("Sentidos, idiomas e resistências"));
            Label extrasLabel = new Label(extras);
            extrasLabel.setWrapText(true);
            extrasLabel.getStyleClass().add("label-muted");
            root.getChildren().add(extrasLabel);
        }
        if (!mob.getTraits().isEmpty()) {
            root.getChildren().add(sectionLabel("Traços"));
            root.getChildren().add(buildEntryCards(mob.getTraits(), false));
        }
        if (!mob.getActions().isEmpty()) {
            root.getChildren().add(sectionLabel("Ações"));
            root.getChildren().add(buildEntryCards(mob.getActions(), true));
        }
        if (!mob.getLegendaryActions().isEmpty()) {
            root.getChildren().add(sectionLabel("Ações lendárias"));
            root.getChildren().add(buildEntryCards(mob.getLegendaryActions(), true));
        }
        if (!mob.getLootInventory().getItems().isEmpty()) {
            root.getChildren().add(sectionLabel("Loot (ainda sem transferir pro grupo por aqui)"));
            root.getChildren().add(buildLootList());
        }
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-section");
        return label;
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        Node portrait = buildPortrait();
        if (actions != null) {
            // como o retrato da ficha de personagem: clicar troca a imagem (vale pro token no mapa)
            portrait.setCursor(javafx.scene.Cursor.HAND);
            javafx.scene.control.Tooltip.install(portrait, new javafx.scene.control.Tooltip("Trocar a imagem do token"));
            portrait.setOnMouseClicked(e -> actions.changeImage());
        }
        header.getChildren().add(portrait);

        VBox info = new VBox(4);
        Label name = new Label(mob.getName());
        name.getStyleClass().add("label-title");
        String subtitle = String.join(" - ", nonBlank(mob.getSize()), nonBlank(mob.getCreatureType()),
                nonBlank(mob.getAlignment()));
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("label-muted");
        info.getChildren().addAll(name, sub);

        header.getChildren().add(info);
        return header;
    }

    private String nonBlank(String s) {
        return (s == null || s.isBlank()) ? "?" : s;
    }

    private Node buildPortrait() {
        Node visual;
        Image image = tryLoadImage(mob.getPortraitImagePath());
        if (image != null) {
            ImageView iv = new ImageView(image);
            iv.setFitWidth(64);
            iv.setFitHeight(64);
            visual = iv;
        } else {
            visual = SilhouetteIcon.create(64);
        }
        return visual;
    }

    private static Image tryLoadImage(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            Image candidate = new Image(new File(path).toURI().toString(), 64, 64, false, true);
            return candidate.isError() ? null : candidate;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Faixa de combate: CA, PV (do token), deslocamento, iniciativa (clique rola), percepcao passiva, ND e XP. */
    private FlowPane buildCombatChips() {
        FlowPane pane = new FlowPane(8, 8);
        String ac = mob.getArmorClass() + (mob.getArmorNote() == null || mob.getArmorNote().isBlank()
                ? "" : " (" + mob.getArmorNote() + ")");
        pane.getChildren().add(statLabel("CA", ac));
        String hp = actions == null ? null : actions.hpText();
        pane.getChildren().add(statLabel("PV", hp != null ? hp
                : mob.getHitPointsAverage() + " (" + mob.getHitPointsFormula() + ")"));
        StringBuilder speed = new StringBuilder(Distances.feetAndMeters(mob.getSpeedWalk()));
        if (mob.getSpeedFly() > 0) speed.append(" · voar ").append(Distances.feetAndMeters(mob.getSpeedFly()));
        if (mob.getSpeedSwim() > 0) speed.append(" · nadar ").append(Distances.feetAndMeters(mob.getSpeedSwim()));
        if (mob.getSpeedClimb() > 0) speed.append(" · escalar ").append(Distances.feetAndMeters(mob.getSpeedClimb()));
        if (mob.getSpeedBurrow() > 0) speed.append(" · escavar ").append(Distances.feetAndMeters(mob.getSpeedBurrow()));
        pane.getChildren().add(statLabel("Deslocamento", speed.toString()));
        Node init = statLabel("Iniciativa", signed(MobSheet.abilityModifier(mob.getDexterity())));
        if (actions != null) {
            init.getStyleClass().add("stat-chip-clickable");
            init.setCursor(javafx.scene.Cursor.HAND);
            javafx.scene.control.Tooltip.install(init, new javafx.scene.control.Tooltip("Rolar iniciativa"));
            init.setOnMouseClicked(e -> actions.rollInitiative());
        }
        pane.getChildren().add(init);
        pane.getChildren().add(statLabel("Percepção passiva", String.valueOf(passivePerception())));
        pane.getChildren().add(statLabel("ND", formatCr(mob.getChallengeRating())));
        pane.getChildren().add(statLabel("XP", String.valueOf(mob.getExperiencePoints())));
        return pane;
    }

    /** "passive Perception 14" dos sentidos; senao 10 + Percepcao (ou SAB). */
    private int passivePerception() {
        for (String s : mob.getSenses()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)passiv[ae]\\s+(?:perception|percep\\S*)\\s+(\\d+)").matcher(s);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        for (Map.Entry<String, Integer> e : mob.getSkillBonuses().entrySet()) {
            var skill = com.vttcaseiro.service.CompendiumToSheet.skillFromToolsName(e.getKey());
            if (skill == com.vttcaseiro.character.Skill.PERCEPTION) return 10 + e.getValue();
        }
        return 10 + MobSheet.abilityModifier(mob.getWisdom());
    }

    /** Seis caixas (3 x 2), como a ficha de personagem: modificador, valor, salvaguarda e as pericias do atributo. */
    private javafx.scene.layout.GridPane buildAbilityGrid() {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        for (int i = 0; i < 3; i++) {
            javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
            c.setPercentWidth(33.3);
            grid.getColumnConstraints().add(c);
        }
        Ability[] all = Ability.values();
        for (int i = 0; i < all.length; i++) grid.add(abilityCard(all[i]), i % 3, i / 3);
        return grid;
    }

    private VBox abilityCard(Ability a) {
        int score = abilityScore(a);
        int mod = MobSheet.abilityModifier(score);
        Label title = new Label(fullName(a));
        title.getStyleClass().add("stat-chip-label");
        Label modLabel = new Label(signed(mod));
        modLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: -c-accent-text;");
        Label scoreLabel = new Label(String.valueOf(score));
        scoreLabel.getStyleClass().add("label-muted");
        HBox top = new HBox(8, modLabel, scoreLabel);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (actions != null) {
            modLabel.setCursor(javafx.scene.Cursor.HAND);
            modLabel.setOnMouseClicked(e -> actions.rollD20(displayName + " - Teste de " + a.abbreviationPt(), mod,
                    RollKind.ABILITY_CHECK, a, mode, hidden));
        }
        VBox card = new VBox(4, title, top);
        card.getStyleClass().add("stat-chip");
        card.setMaxWidth(Double.MAX_VALUE);
        Integer listed = mob.getSavingThrowBonuses().get(codeOf(a));
        int save = listed != null ? listed : mod;
        card.getChildren().add(line("◆ Salvaguarda", save, listed != null, () -> actions.rollD20(
                displayName + " - Salvaguarda de " + a.abbreviationPt(), save, RollKind.SAVING_THROW, a, mode, hidden)));
        for (Map.Entry<String, Integer> e : mob.getSkillBonuses().entrySet()) {
            var skill = com.vttcaseiro.service.CompendiumToSheet.skillFromToolsName(e.getKey());
            if (skill == null || skill.getAbility() != a) continue;
            int bonus = e.getValue();
            card.getChildren().add(line("● " + skill.getDisplayNamePt(), bonus, true, () -> actions.rollD20(
                    displayName + " - " + skill.getDisplayNamePt(), bonus, RollKind.ABILITY_CHECK, a, mode, hidden)));
        }
        return card;
    }

    /** Linha "◆ Salvaguarda +5": botao quando da pra rolar, rotulo quando a ficha e so de leitura. */
    private Node line(String label, int bonus, boolean proficient, Runnable roll) {
        String text = label + "  " + signed(bonus);
        if (actions == null) {
            Label l = new Label(text);
            if (proficient) l.setStyle("-fx-font-weight: bold;");
            return l;
        }
        Button b = new Button(text);
        b.getStyleClass().add("button-flat");
        if (proficient) b.setStyle("-fx-font-weight: bold;");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        b.setOnAction(e -> roll.run());
        return b;
    }

    private static String fullName(Ability a) {
        return switch (a) {
            case STRENGTH -> "FORÇA";
            case DEXTERITY -> "DESTREZA";
            case CONSTITUTION -> "CONSTITUIÇÃO";
            case INTELLIGENCE -> "INTELIGÊNCIA";
            case WISDOM -> "SABEDORIA";
            case CHARISMA -> "CARISMA";
        };
    }

    private HBox buildAbilityRow() {
        HBox row = new HBox(6);
        row.getChildren().add(abilityBox("FOR", mob.getStrength()));
        row.getChildren().add(abilityBox("DES", mob.getDexterity()));
        row.getChildren().add(abilityBox("CON", mob.getConstitution()));
        row.getChildren().add(abilityBox("INT", mob.getIntelligence()));
        row.getChildren().add(abilityBox("SAB", mob.getWisdom()));
        row.getChildren().add(abilityBox("CAR", mob.getCharisma()));
        return row;
    }

    private VBox abilityBox(String label, int score) {
        int mod = MobSheet.abilityModifier(score);
        VBox box = new VBox(0);
        box.getStyleClass().addAll("stat-chip", "stat-chip-clickable");
        box.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label l = new Label(label);
        l.getStyleClass().add("stat-chip-label");
        Label v = new Label((mod >= 0 ? "+" : "") + mod + "  (" + score + ")");
        v.getStyleClass().add("stat-chip-value");
        box.getChildren().addAll(l, v);
        Ability ability = abilityFromPt(label);
        if (actions != null && ability != null) {
            box.setCursor(javafx.scene.Cursor.HAND);
            box.setOnMouseClicked(e -> actions.rollD20(displayName + " - Teste de " + label, mod,
                    RollKind.ABILITY_CHECK, ability, mode, hidden));
        }
        return box;
    }

    private static Ability abilityFromPt(String abbr) {
        for (Ability a : Ability.values()) if (a.abbreviationPt().equals(abbr)) return a;
        return null;
    }

    private static String codeOf(Ability a) {
        return switch (a) {
            case STRENGTH -> "str";
            case DEXTERITY -> "dex";
            case CONSTITUTION -> "con";
            case INTELLIGENCE -> "int";
            case WISDOM -> "wis";
            case CHARISMA -> "cha";
        };
    }

    private int abilityScore(Ability a) {
        return switch (a) {
            case STRENGTH -> mob.getStrength();
            case DEXTERITY -> mob.getDexterity();
            case CONSTITUTION -> mob.getConstitution();
            case INTELLIGENCE -> mob.getIntelligence();
            case WISDOM -> mob.getWisdom();
            case CHARISMA -> mob.getCharisma();
        };
    }

    private Node buildRollControls() {
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().addAll("Normal", "Vantagem", "Desvantagem");
        modeBox.setValue("Normal");
        modeBox.valueProperty().addListener((o, a, v) -> mode = switch (v) {
            case "Vantagem" -> RollMode.ADVANTAGE;
            case "Desvantagem" -> RollMode.DISADVANTAGE;
            default -> RollMode.NORMAL;
        });
        CheckBox hiddenBox = new CheckBox("Escondida (só você e o mestre)");
        hiddenBox.setSelected(hidden);
        hiddenBox.selectedProperty().addListener((o, a, v) -> hidden = v);
        HBox row = new HBox(10, new Label("Rolagens:"), modeBox, hiddenBox);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** Salvaguarda: bonus do bloco do monstro se tiver, senao o modificador. */
    private Node buildSaveRow() {
        FlowPane pane = new FlowPane(6, 6);
        for (Ability a : Ability.values()) {
            Integer listed = mob.getSavingThrowBonuses().get(codeOf(a));
            int bonus = listed != null ? listed : MobSheet.abilityModifier(abilityScore(a));
            Button b = new Button(a.abbreviationPt() + " " + signed(bonus));
            if (listed != null) b.setStyle("-fx-font-weight: bold;");
            b.setOnAction(e -> actions.rollD20(displayName + " - Salvaguarda de " + a.abbreviationPt(), bonus,
                    RollKind.SAVING_THROW, a, mode, hidden));
            pane.getChildren().add(b);
        }
        return pane;
    }

    private Node buildSkillRow() {
        FlowPane pane = new FlowPane(6, 6);
        for (Map.Entry<String, Integer> e : mob.getSkillBonuses().entrySet()) {
            int bonus = e.getValue();
            com.vttcaseiro.character.Skill skill = com.vttcaseiro.service.CompendiumToSheet.skillFromToolsName(e.getKey());
            String name = skill != null ? skill.getDisplayNamePt() : e.getKey();
            Button b = new Button(name + " " + signed(bonus));
            b.setOnAction(ev -> actions.rollD20(displayName + " - " + name, bonus, RollKind.ABILITY_CHECK,
                    skill != null ? skill.getAbility() : null, mode, hidden));
            pane.getChildren().add(b);
        }
        return pane;
    }

    /** Lista de acoes + botoes de rolar a selecionada (ataque/dano/critico). */
    /**
     * Um cartao por traco/acao: nome em destaque, acerto / CD / dano a vista, o texto inteiro e os botoes daquela acao
     * (Atacar ou Rolar dano, e Chat). Antes era uma lista onde so o nome aparecia (e as vezes nem isso).
     */
    private Node buildEntryCards(List<CharacterTrait> list, boolean rollable) {
        VBox box = new VBox(6);
        for (CharacterTrait t : list) box.getChildren().add(entryCard(t, rollable));
        return box;
    }

    private Node entryCard(CharacterTrait t, boolean rollable) {
        Label name = new Label(t.getName() == null ? "(sem nome)" : t.getName());
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: -c-accent-text;");
        name.setWrapText(true);
        javafx.scene.layout.FlowPane head = new javafx.scene.layout.FlowPane(6, 4, name);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        String desc = t.getDescription() == null ? "" : t.getDescription();
        var bonus = MobAttack.attackBonus(desc);
        var dc = MobAttack.saveDc(desc);
        var dmg = MobAttack.damage(desc);
        String type = MobAttack.damageType(desc);
        bonus.ifPresent(b -> head.getChildren().add(chip(signed(b) + " pra acertar")));
        dc.ifPresent(d -> head.getChildren().add(chip("CD " + d)));
        dmg.ifPresent(d -> head.getChildren().add(chip(d + (type == null ? ""
                : " " + com.vttcaseiro.character.DamageTypes.namePt(type)))));
        VBox card = new VBox(4, head);
        if (!desc.isBlank()) {
            Label text = new Label(desc);
            text.setWrapText(true);
            text.getStyleClass().add("label-muted");
            text.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            card.getChildren().add(text);
        }
        HBox buttons = new HBox(6);
        if (actions != null && rollable && (bonus.isPresent() || dmg.isPresent())) {
            Button roll = new Button(bonus.isPresent() ? "Atacar" : "Rolar dano");
            roll.getStyleClass().add("button-primary");
            roll.setTooltip(new javafx.scene.control.Tooltip("Acerto e dano juntos. Crítico (20) dobra os dados do dano; "
                    + "falha crítica (1) não rola dano. Ação com CD rola só o dano."));
            roll.setOnAction(e -> {
                String typeText = type == null ? "" : " (" + com.vttcaseiro.character.DamageTypes.namePt(type) + ")";
                if (bonus.isPresent()) {
                    actions.attack(displayName + " - Ataque: " + t.getName(), bonus.getAsInt(),
                            displayName + " - Dano: " + t.getName() + typeText, dmg.orElse(null), mode, hidden);
                } else {
                    rollDamage(t, false);
                }
            });
            buttons.getChildren().add(roll);
        }
        if (actions != null || onTraitClicked != null) {
            Button chat = new Button("Chat");
            chat.setTooltip(new javafx.scene.control.Tooltip("Manda o texto inteiro pro chat"));
            chat.setOnAction(e -> {
                if (actions != null) actions.chat(displayName + " usa " + t.toChatText());
                else onTraitClicked.accept(t);
            });
            buttons.getChildren().add(chat);
        }
        if (!buttons.getChildren().isEmpty()) card.getChildren().add(buttons);
        card.setStyle("-fx-background-color: -c-surface; -fx-background-radius: 8; -fx-border-color: -c-raised-2;"
                + " -fx-border-radius: 8; -fx-padding: 8 10 8 10;");
        return card;
    }

    private static Label chip(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: -c-raised-2b; -fx-text-fill: -c-text; -fx-background-radius: 10;"
                + " -fx-padding: 1 8 1 8; -fx-font-size: 12px;");
        return l;
    }

    private void rollDamage(CharacterTrait t, boolean critical) {
        if (t == null) return;
        String type = MobAttack.damageType(t.getDescription());
        String typeText = type == null ? "" : " (" + com.vttcaseiro.character.DamageTypes.namePt(type) + ")";
        MobAttack.damage(t.getDescription()).ifPresentOrElse(expr -> actions.roll(
                        displayName + " - " + (critical ? "Dano CRÍTICO: " : "Dano: ") + t.getName() + typeText,
                        critical ? com.vttcaseiro.dice.DiceRoller.doubleDice(expr) : expr, hidden),
                () -> actions.chat(displayName + " usa " + t.toChatText()));
    }

    private static String signed(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }

    private HBox buildStatsRow() {
        HBox row = new HBox(8);
        String ac = mob.getArmorClass() + (mob.getArmorNote() == null || mob.getArmorNote().isBlank()
                ? "" : " (" + mob.getArmorNote() + ")");
        row.getChildren().add(statLabel("CA", ac));
        row.getChildren().add(statLabel("PV", mob.getHitPointsAverage() + " (" + mob.getHitPointsFormula() + ")"));
        return row;
    }

    private HBox buildSpeedRow() {
        HBox row = new HBox(8);
        row.getChildren().add(statLabel("Andar", Distances.feetAndMeters(mob.getSpeedWalk())));
        if (mob.getSpeedFly() > 0) row.getChildren().add(statLabel("Voar", Distances.feetAndMeters(mob.getSpeedFly())));
        if (mob.getSpeedSwim() > 0) row.getChildren().add(statLabel("Nadar", Distances.feetAndMeters(mob.getSpeedSwim())));
        if (mob.getSpeedClimb() > 0) row.getChildren().add(statLabel("Escalar", Distances.feetAndMeters(mob.getSpeedClimb())));
        if (mob.getSpeedBurrow() > 0) row.getChildren().add(statLabel("Escavar", Distances.feetAndMeters(mob.getSpeedBurrow())));
        return row;
    }

    /** CA, PV, deslocamento, CR e XP numa faixa so de cartoezinhos. */
    private javafx.scene.layout.FlowPane statChips() {
        javafx.scene.layout.FlowPane pane = new javafx.scene.layout.FlowPane(8, 8);
        pane.getChildren().addAll(buildStatsRow().getChildren());
        pane.getChildren().addAll(buildSpeedRow().getChildren());
        pane.getChildren().addAll(buildCrRow().getChildren());
        return pane;
    }

    private HBox buildCrRow() {
        HBox row = new HBox(8);
        row.getChildren().add(statLabel("CR", formatCr(mob.getChallengeRating())));
        row.getChildren().add(statLabel("XP", String.valueOf(mob.getExperiencePoints())));
        return row;
    }

    private static String formatCr(double cr) {
        if (cr == Math.floor(cr)) {
            return String.valueOf((int) cr);
        }
        if (Math.abs(cr - 0.125) < 1e-6) return "1/8";
        if (Math.abs(cr - 0.25) < 1e-6) return "1/4";
        if (Math.abs(cr - 0.5) < 1e-6) return "1/2";
        return String.valueOf(cr);
    }

    /** "CA 17" como um cartaozinho: rotulo pequeno em cima, valor grande embaixo. */
    private javafx.scene.Node statLabel(String name, String value) {
        Label n = new Label(name.toUpperCase());
        n.getStyleClass().add("stat-chip-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-chip-value");
        VBox chip = new VBox(0, n, v);
        chip.getStyleClass().add("stat-chip");
        return chip;
    }

    /** Monta um unico bloco de texto com os bonus de salvaguarda/pericia,
     * resistencia/imunidade/vulnerabilidade a dano, imunidade a condicao,
     * sentidos e idiomas -- tudo o que a ficha de monstro tem que a de
     * jogador nao tem igual, num so paragrafo (nao justifica uma secao
     * separada pra cada lista, a maioria vem vazia na pratica). */
    private String buildExtrasText() {
        StringBuilder sb = new StringBuilder();
        appendListLine(sb, "Vulnerabilidade a dano", damagePt(mob.getDamageVulnerabilities()));
        appendListLine(sb, "Resistência a dano", damagePt(mob.getDamageResistances()));
        appendListLine(sb, "Imunidade a dano", damagePt(mob.getDamageImmunities()));
        appendListLine(sb, "Imunidade a condição", conditionPt(mob.getConditionImmunities()));
        appendListLine(sb, "Sentidos", mob.getSenses());
        appendListLine(sb, "Idiomas", mob.getLanguages());
        return sb.toString().trim();
    }

    private static List<String> damagePt(List<String> values) {
        return values.stream().map(v -> {
            String key = v == null ? "" : v.trim().toLowerCase(java.util.Locale.ROOT);
            return com.vttcaseiro.character.DamageTypes.namePt(key).equals(key) ? v
                    : com.vttcaseiro.character.DamageTypes.namePt(key);
        }).toList();
    }

    private static List<String> conditionPt(List<String> values) {
        return values.stream().map(v -> {
            try {
                return com.vttcaseiro.model.Condition.valueOf(v.trim().toUpperCase(java.util.Locale.ROOT)).label();
            } catch (RuntimeException ex) {
                return v;
            }
        }).toList();
    }

    private void appendBonusLine(StringBuilder sb, String label, Map<String, Integer> bonuses) {
        if (bonuses.isEmpty()) return;
        StringBuilder parts = new StringBuilder();
        for (Map.Entry<String, Integer> entry : bonuses.entrySet()) {
            if (parts.length() > 0) parts.append(", ");
            int v = entry.getValue();
            parts.append(entry.getKey()).append(" ").append(v >= 0 ? "+" : "").append(v);
        }
        sb.append(label).append(": ").append(parts).append("\n");
    }

    private void appendListLine(StringBuilder sb, String label, List<String> values) {
        if (values.isEmpty()) return;
        sb.append(label).append(": ").append(String.join(", ", values)).append("\n");
    }

    private ListView<InventoryItem> buildLootList() {
        ListView<InventoryItem> list = new ListView<>();
        list.getItems().addAll(mob.getLootInventory().getItems());
        list.setPrefHeight(Math.min(120, 28 * Math.max(1, mob.getLootInventory().getItems().size())));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " x" + item.getQuantity());
            }
        });
        return list;
    }
}

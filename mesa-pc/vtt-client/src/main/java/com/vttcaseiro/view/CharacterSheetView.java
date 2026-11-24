package com.vttcaseiro.view;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.Attack;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.CharacterTrait;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.character.RollKind;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumEntryType;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.content.Subclass;
import com.vttcaseiro.model.Condition;
import com.vttcaseiro.service.CompendiumToSheet;
import com.vttcaseiro.service.TokenLink;
import com.vttcaseiro.character.Skill;
import com.vttcaseiro.character.SpellSlots;
import com.vttcaseiro.compendium.CompendiumReference;
import com.vttcaseiro.inventory.Currency;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.service.ImageAssetService;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A ficha de personagem no layout da ficha oficial de D&D 2024
 * (DnD_2024_Character-Sheet.pdf), so que "viva": tudo editavel, tudo que
 * a ficha impressa obriga a calcular de cabeca (modificadores, salvaguardas,
 * pericias, iniciativa, percepcao passiva, CD e ataque de magia, CA com
 * escudo) recalculado na hora, e cada numero clicavel rola no chat --
 * igual as fichas do Roll20/Foundry.
 * <p>
 * Organizacao (abas = blocos da ficha oficial):
 * <ul>
 *   <li>Atributos: os 6 atributos, salvaguardas e as 18 pericias (pag. 1, esquerda)</li>
 *   <li>Combate: CA, PV, dados de vida, testes contra a morte, iniciativa,
 *       deslocamento, tamanho, percepcao passiva, armas e truques de dano</li>
 *   <li>Tracos: Class Features, Species Traits, Feats, treinamento e proficiencias</li>
 *   <li>Magias: conjuracao, slots e "Cantrips &amp; Prepared Spells" (pag. 2)</li>
 *   <li>Personagem: aparencia, historia/personalidade, idiomas, equipamento,
 *       sintonia com itens magicos e moedas (pag. 2)</li>
 * </ul>
 * <p>
 * Eficiencia: a janela e montada UMA vez. Editar um campo so atualiza os
 * rotulos derivados (lista {@code derivedUpdaters}) -- nada de reconstruir a
 * arvore inteira a cada clique como a versao anterior fazia -- e salva
 * sozinho (com {@code SAVE_DELAY} de espera pra nao gravar a cada tecla).
 * <p>
 * O retrato continua sendo a "alca" pra arrastar a ficha pro mapa (ver
 * MapView.setCharacterDropHandler()).
 */
public class CharacterSheetView {

    /** Formato customizado colocado no Dragboard junto com o texto puro
     * (algumas plataformas exigem pelo menos um formato "padrao" no
     * dragboard pra aceitar o drag). */
    public static final DataFormat CHARACTER_ID_FORMAT = new DataFormat("application/x-vtt-character-id");

    private static final Duration SAVE_DELAY = Duration.millis(800);
    private static final String CARD_STYLE = "-fx-background-color: -c-surface; -fx-padding: 8;"
            + " -fx-background-radius: 6; -fx-border-color: -c-border; -fx-border-radius: 6;";
    private static final String BIG_NUMBER_STYLE = "-fx-text-fill: -c-text; -fx-font-size: 20px; -fx-font-weight: bold;";
    private static final List<String> HIT_DICE = List.of("d6", "d8", "d10", "d12");

    /**
     * O que a ficha precisa do resto do programa. A tela nao sabe nada de
     * rede/chat/disco -- so pede. Implementado no Main.
     */
    public interface Actions {
        /** Toda conjuracao (mesmo sem dados): Bencao, Escudo... viram efeito no token. */
        default void spellCast(CharacterSheet sheet, PreparedSpell spell, int slotLevel) {
        }

        /** Efeito da magia conjurada (dano/cura com os dados ja do espaco certo).
         * O Main usa os alvos marcados; sem isso, so rola no chat. */
        default void castSpellEffect(CharacterSheet sheet, PreparedSpell spell, int slotLevel, String dice,
                                     boolean hidden) {
            roll(sheet, spell.getName(), dice, hidden);
        }

        /** Rola 1d20 + modificador. {@code kind}/{@code ability} dizem que
         * tipo de teste e (as condicoes da ficha decidem vantagem,
         * desvantagem, falha automatica, exaustao); {@code mode} e o que o
         * jogador escolheu no topo da ficha. */
        void rollD20(CharacterSheet sheet, String label, int modifier, RollKind kind, Ability ability, RollMode mode,
                     boolean hidden);

        /** Igual a rollD20, mas avisa o d20 natural quando a rolagem voltar (pela rede, quem rola e o servidor). */
        default void rollD20Then(CharacterSheet sheet, String label, int modifier, RollKind kind, Ability ability,
                                 RollMode mode, boolean hidden, java.util.function.IntConsumer onNatural) {
            rollD20(sheet, label, modifier, kind, ability, mode, hidden);
        }

        /**
         * Ataque completo num botao so: rola o acerto e, em seguida, o dano. 20 natural dobra todos os dados do
         * dano; 1 natural nao rola dano. Ataque sem bonus (CD de salvaguarda) so rola o dano.
         */
        default void attack(CharacterSheet sheet, Attack attack, RollMode mode, boolean hidden) {
            var bonus = attack.attackBonus();
            if (bonus.isPresent()) rollD20(sheet, "Ataque: " + attack.getName(), bonus.getAsInt(), RollKind.ATTACK, null, mode, hidden);
            attack.damageExpression().ifPresent(expr -> roll(sheet, "Dano: " + attack.getName(), expr, hidden));
        }

        /** Iniciativa: com o token no mapa, entra na ordem de combate; sem token, so rola no chat. */
        default void rollInitiative(CharacterSheet sheet, RollMode mode, boolean hidden) {
            rollD20(sheet, "Iniciativa", sheet.initiative(), RollKind.INITIATIVE, Ability.DEXTERITY, mode, hidden);
            if (!sheet.getInitiativeDice().isBlank()) roll(sheet, "Iniciativa (dados extras)", sheet.getInitiativeDice(), hidden);
        }

        /** Rola uma expressao livre ("1d8+3") com um rotulo. hidden = so quem
         * rolou e o mestre veem. */
        void roll(CharacterSheet sheet, String label, String expression, boolean hidden);

        /** Manda um texto pro chat em nome do personagem. */
        void chat(CharacterSheet sheet, String text);

        /** Manda um anexo do compendio pro chat (texto resolvido no compendio atual). */
        void compendium(CharacterSheet sheet, CompendiumReference reference);

        /** Grava a ficha (disco da mesa + sincroniza pela rede). Lanca
         * excecao com mensagem amigavel se nao der. */
        void save(CharacterSheet sheet) throws Exception;

        /** Chamado a cada edicao (sem esperar o autosave): atualiza na hora
         * o token ligado no mapa (PV, condicoes, efeitos). */
        default void sheetChanged(CharacterSheet sheet) {
        }

        /** Compendio da mesa (5e.tools) -- null se a mesa nao tiver. */
        default Compendium compendium() {
            return null;
        }

        /** Classes importadas na mesa (class-*.json do 5e.tools). */
        default List<DndClass> classes() {
            return List.of();
        }
    }

    private CharacterSheet sheet;
    private final Actions actions;
    private final ImageAssetService imageAssetService;

    private final List<Runnable> derivedUpdaters = new ArrayList<>();
    private final PauseTransition saveDebounce = new PauseTransition(SAVE_DELAY);
    private boolean dirty;
    private RollMode rollMode = RollMode.NORMAL;
    /** Rolagens desta ficha escondidas (so o jogador e o mestre veem). */
    private boolean hiddenRolls;

    private Stage stage;
    private Label saveStatus;
    private VBox portraitBox;

    public CharacterSheetView(CharacterSheet sheet, Actions actions, ImageAssetService imageAssetService) {
        this.sheet = sheet;
        this.actions = actions;
        this.imageAssetService = imageAssetService;
        saveDebounce.setOnFinished(e -> flushSave());
    }

    private TabPane tabs;
    private Runnable onClosed;

    public void show() {
        stage = new Stage();
        Icons.applyTo(stage);
        stage.setTitle(sheet.getName());
        Scene scene = new Scene(buildRoot(), 1240, 840);
        var css = getClass().getResource("/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        // Fechar a janela grava o que estiver pendente na hora.
        stage.setOnHidden(e -> {
            flushSave();
            if (onClosed != null) onClosed.run();
        });
        stage.show();
    }

    private VBox buildRoot() {
        derivedUpdaters.clear();
        if (com.vttcaseiro.character.GameSystem.of(sheet).isCustom()) {
            // Tormenta20 / Ordem Paranormal: ficha propria, mesma janela/salvamento/sincronizacao.
            saveStatus = savedLabel();
            return new SystemSheetPane(sheet, actions, this::changed, newName -> {
                sheet.setName(newName);
                if (stage != null) stage.setTitle(newName);
                changed();
                flushSave();
            }, new HBox(8, saveStatus));
        }
        int selectedTab = tabs == null ? 0 : tabs.getSelectionModel().getSelectedIndex();
        // tres paginas, como uma ficha de papel: a primeira com tudo que se usa na mesa
        tabs = new TabPane(
                tab("Principal", buildMainPage()),
                tab("Traços e magias", new VBox(14, buildTraitsTab(), new javafx.scene.control.Separator(), buildSpellsTab())),
                tab("Personagem", buildCharacterTab()),
                tab("Bastião", buildBastionTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(Math.max(0, selectedTab));

        VBox root = new VBox(10, buildHeader(), tabs);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: -c-bg;");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        recalc();
        return root;
    }

    /** Chamado quando a janela fecha (o Main tira ela da lista de abertas). */
    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    /** Fecha a janela (grava o pendente, ver setOnHidden em show()). */
    public void close() {
        if (stage != null) stage.close();
    }

    public CharacterSheet getSheet() {
        return sheet;
    }

    public void toFront() {
        if (stage != null) {
            stage.setIconified(false);
            stage.toFront();
        }
    }

    /**
     * A ficha mudou por fora (dano no token pelo mapa, sincronizacao de
     * outro jogador): remonta a janela com os valores novos, mantendo a aba
     * aberta. {@code newSheet} pode ser outro objeto (versao da rede).
     */
    public void reload(CharacterSheet newSheet) {
        // Mesma ficha com edicao pendente nesta janela: grava antes, senao a
        // remontagem jogaria fora o que o jogador acabou de digitar. (Versao
        // nova vinda da rede = outro objeto: a da rede vale.)
        if (dirty && (newSheet == null || newSheet == sheet)) flushSave();
        if (newSheet != null) this.sheet = newSheet;
        if (stage == null || stage.getScene() == null) return;
        saveDebounce.stop();
        dirty = false;
        stage.setTitle(sheet.getName());
        stage.getScene().setRoot(buildRoot());
        if (saveStatus != null) saveStatus.setText("Atualizado");
    }

    // ==================================================================
    // Infra: recalculo, autosave, campos ligados a ficha
    // ==================================================================

    /** Atualiza todos os rotulos derivados (modificadores, bonus...). */
    private void recalc() {
        for (Runnable r : derivedUpdaters) r.run();
    }

    /** Chamado a cada edicao: recalcula e agenda o save. */
    private void changed() {
        recalc();
        actions.sheetChanged(sheet);
        dirty = true;
        if (saveStatus != null) saveStatus.setText("Salvando...");
        saveDebounce.playFromStart();
    }

    /** A ficha salva sozinha (um instante depois de cada mudança): so um aviso discreto, sem botao. */
    private static Label savedLabel() {
        Label l = new Label("✓ Salvo");
        l.getStyleClass().add("label-muted");
        l.setStyle("-fx-font-size: 12px;");
        l.setTooltip(new Tooltip("A ficha salva sozinha logo depois de cada mudança."));
        return l;
    }

    private void flushSave() {
        saveDebounce.stop();
        if (!dirty) return;
        try {
            actions.save(sheet);
            dirty = false;
            if (saveStatus != null) saveStatus.setText("✓ Salvo");
        } catch (Exception ex) {
            if (saveStatus != null) saveStatus.setText("Erro ao salvar: " + ex.getMessage());
        }
    }

    /** Registra um rotulo cujo texto depende da ficha. */
    private Label derived(Supplier<String> text) {
        Label label = new Label();
        derivedUpdaters.add(() -> label.setText(text.get()));
        return label;
    }

    /** Botao cujo texto depende da ficha (ex.: "+5" que rola ao clicar). */
    private Button derivedButton(Supplier<String> text, Runnable onClick) {
        Button button = new Button();
        button.getStyleClass().add("button-flat");
        derivedUpdaters.add(() -> button.setText(text.get()));
        button.setOnAction(e -> onClick.run());
        return button;
    }

    private TextField textField(Supplier<String> get, Consumer<String> set) {
        TextField field = new TextField(orEmpty(get.get()));
        field.textProperty().addListener((obs, old, value) -> {
            set.accept(blankToNull(value));
            changed();
        });
        return field;
    }

    private TextArea textArea(Supplier<String> get, Consumer<String> set, int rows) {
        TextArea area = new TextArea(orEmpty(get.get()));
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        area.textProperty().addListener((obs, old, value) -> {
            set.accept(blankToNull(value));
            changed();
        });
        return area;
    }

    /** Campo numerico: aplica a cada tecla se for numero valido; ao sair do
     * campo, volta pro valor guardado se tiver ficado invalido/em branco. */
    private TextField intField(IntSupplier get, IntConsumer set, int width) {
        TextField field = new TextField(String.valueOf(get.getAsInt()));
        field.setPrefColumnCount(width);
        field.setAlignment(Pos.CENTER);
        field.textProperty().addListener((obs, old, value) -> {
            Integer parsed = parseInt(value);
            if (parsed != null && parsed != get.getAsInt()) {
                set.accept(parsed);
                changed();
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) field.setText(String.valueOf(get.getAsInt()));
        });
        return field;
    }

    /** Igual intField, mas pra Integer que pode ficar vazio (PV/CA nao preenchidos). */
    private TextField nullableIntField(Supplier<Integer> get, Consumer<Integer> set, int width) {
        TextField field = new TextField(get.get() == null ? "" : String.valueOf(get.get()));
        field.setPrefColumnCount(width);
        field.setAlignment(Pos.CENTER);
        field.textProperty().addListener((obs, old, value) -> {
            if (value == null || value.isBlank()) {
                set.accept(null);
                changed();
                return;
            }
            Integer parsed = parseInt(value);
            if (parsed != null) {
                set.accept(parsed);
                changed();
            }
        });
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) field.setText(get.get() == null ? "" : String.valueOf(get.get()));
        });
        return field;
    }

    private CheckBox checkBox(String text, Supplier<Boolean> get, Consumer<Boolean> set) {
        CheckBox box = new CheckBox(text);
        box.setSelected(get.get());
        box.selectedProperty().addListener((obs, old, value) -> {
            set.accept(value);
            changed();
        });
        return box;
    }

    // ==================================================================
    // Cabecalho (nome, classe, nivel... + modo de rolagem + status)
    // ==================================================================

    private Node buildHeader() {
        portraitBox = new VBox(4);
        rebuildPortrait();

        TextField nameField = new TextField(sheet.getName());
        nameField.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        // O nome vira o nome do arquivo da ficha: so aplica ao confirmar
        // (Enter ou sair do campo), nao a cada tecla -- senao cada letra
        // digitada viraria um save com um nome intermediario.
        Runnable commitName = () -> {
            String value = nameField.getText() == null ? "" : nameField.getText().trim();
            if (value.isEmpty()) {
                nameField.setText(sheet.getName());
            } else if (!value.equals(sheet.getName())) {
                sheet.setName(value);
                stage.setTitle(value);
                changed();
                flushSave();
            }
        };
        nameField.setOnAction(e -> commitName.run());
        nameField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitName.run();
        });

        GridPane identity = new GridPane();
        identity.setHgap(8);
        identity.setVgap(4);
        identity.add(nameField, 0, 0, 4, 1);
        addLabeled(identity, 0, 1, "Classe", textField(sheet::getClassName, sheet::setClassName));
        addLabeled(identity, 1, 1, "Subclasse", textField(sheet::getSubclass, sheet::setSubclass));
        addLabeled(identity, 2, 1, "Espécie", textField(sheet::getSpecies, sheet::setSpecies));
        addLabeled(identity, 3, 1, "Antecedente", textField(sheet::getBackground, sheet::setBackground));

        Spinner<Integer> levelSpinner = new Spinner<>(1, 20, sheet.getLevel());
        levelSpinner.setPrefWidth(70);
        levelSpinner.valueProperty().addListener((obs, old, value) -> {
            sheet.setLevel(value);
            sheet.setHitDiceSpent(sheet.getHitDiceSpent()); // recorta se o nivel caiu
            changed();
        });
        addLabeled(identity, 0, 3, "Nível", levelSpinner);
        addLabeled(identity, 1, 3, "XP", intField(sheet::getExperiencePoints, sheet::setExperiencePoints, 6));
        addLabeled(identity, 2, 3, "Jogador", textField(sheet::getPlayerName, sheet::setPlayerName));

        // Modo de rolagem: vale pra todos os d20 da ficha.
        ToggleGroup modeGroup = new ToggleGroup();
        ToggleButton normal = modeToggle("Normal", RollMode.NORMAL, modeGroup);
        ToggleButton adv = modeToggle("Vantagem", RollMode.ADVANTAGE, modeGroup);
        ToggleButton dis = modeToggle("Desvantagem", RollMode.DISADVANTAGE, modeGroup);
        normal.setSelected(true);
        HBox modeBox = new HBox(2, normal, adv, dis);
        CheckBox hiddenBox = new CheckBox("Escondida (só você e o mestre)");
        hiddenBox.setSelected(hiddenRolls);
        hiddenBox.setTooltip(new Tooltip("O resultado não aparece pros outros jogadores."));
        hiddenBox.selectedProperty().addListener((o, a, v) -> hiddenRolls = v);

        saveStatus = savedLabel();

        VBox right = new VBox(6, sectionLabel("Rolagens de d20"), modeBox, hiddenBox,
                bigStat("Bônus de proficiência", derived(() -> signed(sheet.proficiencyBonus()))),
                checkBox("Inspiração heroica", sheet::hasHeroicInspiration, sheet::setHeroicInspiration),
                classButton(),
                saveStatus);
        right.setMinWidth(Region.USE_PREF_SIZE);

        HBox header = new HBox(12, portraitBox, identity, right);
        HBox.setHgrow(identity, Priority.ALWAYS);
        return header;
    }

    private ToggleButton modeToggle(String text, RollMode mode, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setToggleGroup(group);
        button.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) rollMode = mode;
        });
        // Nao deixa ficar sem nenhum selecionado (clicar no ja marcado).
        button.setOnAction(e -> {
            if (group.getSelectedToggle() == null) button.setSelected(true);
        });
        return button;
    }

    private void rollD20(String label, int modifier, RollKind kind, Ability ability) {
        actions.rollD20(sheet, label, modifier, kind, ability, rollMode, hiddenRolls);
    }

    // ==================================================================
    // Aba Atributos
    // ==================================================================

    private Node buildAbilitiesTab() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(33.3);
            grid.getColumnConstraints().add(c);
        }
        extraRows.clear();
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            grid.add(abilityCard(abilities[i]), i % 3, i / 3);
        }
        Label hint = new Label("Clique no número ou no nome para rolar. Marca: ● proficiente · ◉ especialista · "
                + "◐ pau pra toda obra · ○ nada (cada clique muda).");
        hint.setWrapText(true);
        hint.getStyleClass().add("label-muted");
        // extras gerais: somam em todas as pericias / todas as salvaguardas
        HBox extras = row(new Label("Extra em todas as perícias"), intField(sheet::getSkillBonusAll, sheet::setSkillBonusAll, 3),
                new Label("Extra em todas as salvaguardas"), intField(sheet::getSaveBonusAll, sheet::setSaveBonusAll, 3));
        extraRows.add(extras);
        // campos de extra quase nunca sao usados: ficam escondidos (aparecem sozinhos se algum tiver valor)
        boolean anyExtra = sheet.getSkillBonusAll() != 0 || sheet.getSaveBonusAll() != 0
                || java.util.Arrays.stream(Ability.values()).anyMatch(a -> sheet.getSkillBonusFor(a) != 0);
        CheckBox showExtras = new CheckBox("Bônus extras");
        showExtras.setMinWidth(Region.USE_PREF_SIZE);
        showExtras.setStyle("-fx-font-size: 12px;");
        showExtras.setTooltip(new Tooltip("Bônus extra em todas as perícias, todas as salvaguardas ou nas perícias de um atributo"
                + " (itens mágicos, bênçãos...)."));
        showExtras.setSelected(anyExtra);
        Runnable applyExtras = () -> extraRows.forEach(r -> {
            r.setVisible(showExtras.isSelected());
            r.setManaged(showExtras.isSelected());
        });
        showExtras.setOnAction(e -> applyExtras.run());
        applyExtras.run();
        HBox top = new HBox(12, hint, showExtras);
        HBox.setHgrow(hint, Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, top, extras, grid);
    }

    /** Linhas de "bônus extra" (escondidas por padrão). */
    private final List<Node> extraRows = new ArrayList<>();

    private static final String[] SKILL_MARKS = {"○", "●", "◉", "◐"};
    private static final String[] SKILL_MARK_TIPS = {"Sem proficiência", "Proficiente", "Especialista (proficiência em dobro)",
            "Pau pra toda obra (metade da proficiência)"};

    /** Botao pequeno com a marca de treino; cada clique avanca (ver CharacterSheet.cycleSkillLevel). */
    private Button markButton(Supplier<Integer> level, Runnable cycle, String[] marks, String[] tips) {
        Button b = new Button();
        b.getStyleClass().add("button-flat");
        b.setMinWidth(28);
        Runnable refresh = () -> {
            int l = Math.max(0, Math.min(marks.length - 1, level.get()));
            b.setText(marks[l]);
            b.setStyle("-fx-font-size: 18px; -fx-padding: 0 4 0 4; -fx-text-fill: " + (l == 0 ? "#8C7B6B" : "#FFCF7F") + ";");
            b.setTooltip(new Tooltip(tips[l] + " -- clique para trocar"));
        };
        refresh.run();
        derivedUpdaters.add(refresh);
        b.setOnAction(e -> {
            cycle.run();
            refresh.run();
            changed();
        });
        return b;
    }

    /** Nome clicavel que rola o mesmo que o numero ao lado. */
    private Button rollLabel(String text, Runnable roll, boolean bold) {
        Button b = new Button(text);
        b.getStyleClass().add("button-flat");
        b.setStyle("-fx-padding: 1 4 1 4;" + (bold ? " -fx-font-weight: bold;" : ""));
        b.setTooltip(new Tooltip("Rolar " + text));
        b.setOnAction(e -> roll.run());
        return b;
    }

    private Node abilityCard(Ability ability) {
        String name = abilityNamePt(ability);
        VBox card = new VBox(4);
        card.setStyle(CARD_STYLE);

        Runnable rollCheck = () -> rollD20("Teste de " + name, CharacterSheet.abilityModifier(sheet.abilityScore(ability)),
                RollKind.ABILITY_CHECK, ability);
        Button title = rollLabel(name.toUpperCase(), rollCheck, true);
        TextField score = intField(() -> sheet.abilityScore(ability), v -> setAbilityScore(ability, v), 3);
        Button mod = derivedButton(() -> signed(CharacterSheet.abilityModifier(sheet.abilityScore(ability))), rollCheck);
        mod.setStyle(BIG_NUMBER_STYLE);
        mod.setTooltip(new Tooltip("Rolar teste de " + name));
        HBox top = new HBox(8, mutedLabel("Valor"), score, spacer(), mutedLabel("mod."), mod);
        top.setAlignment(Pos.CENTER_LEFT);
        title.setMinWidth(Region.USE_PREF_SIZE);
        mod.setMinWidth(Region.USE_PREF_SIZE);
        card.getChildren().addAll(title, top);

        // salvaguarda: marca de um clique (proficiente ou nao), numero e nome rolam
        Runnable rollSave = () -> rollD20("Salvaguarda de " + name, sheet.savingThrowBonus(ability), RollKind.SAVING_THROW, ability);
        Button saveMark = markButton(() -> sheet.isSavingThrowProficient(ability) ? 1 : 0,
                () -> sheet.setSavingThrowProficient(ability, !sheet.isSavingThrowProficient(ability)),
                new String[]{"○", "●"}, new String[]{"Sem proficiência na salvaguarda", "Proficiente na salvaguarda"});
        Button saveRoll = derivedButton(() -> signed(sheet.savingThrowBonus(ability)), rollSave);
        saveRoll.setStyle("-fx-padding: 1 4 1 4;");
        card.getChildren().add(row(saveMark, saveRoll, rollLabel("Salvaguarda", rollSave, true)));

        boolean hasSkills = false;
        for (Skill skill : Skill.values()) {
            if (skill.getAbility() != ability) continue;
            hasSkills = true;
            Runnable rollSkill = () -> rollD20(skill.getDisplayNamePt(), sheet.skillBonus(skill), RollKind.ABILITY_CHECK,
                    skill.getAbility());
            Button mark = markButton(() -> sheet.skillLevel(skill), () -> sheet.cycleSkillLevel(skill), SKILL_MARKS, SKILL_MARK_TIPS);
            Button roll = derivedButton(() -> signed(sheet.skillBonus(skill)), rollSkill);
            roll.setStyle("-fx-padding: 1 4 1 4;");
            card.getChildren().add(row(mark, roll, rollLabel(skill.getDisplayNamePt(), rollSkill, false)));
        }
        if (hasSkills) {
            // extra so nas pericias deste atributo
            HBox extraRow = row(mutedLabel("Extra nas perícias de " + ability.abbreviationPt()),
                    intField(() -> sheet.getSkillBonusFor(ability), v -> sheet.setSkillBonusFor(ability, v), 3));
            extraRows.add(extraRow);
            card.getChildren().add(extraRow);
        }
        return card;
    }

    private void setAbilityScore(Ability ability, int value) {
        int v = Math.max(1, Math.min(30, value));
        switch (ability) {
            case STRENGTH -> sheet.setStrength(v);
            case DEXTERITY -> sheet.setDexterity(v);
            case CONSTITUTION -> sheet.setConstitution(v);
            case INTELLIGENCE -> sheet.setIntelligence(v);
            case WISDOM -> sheet.setWisdom(v);
            case CHARISMA -> sheet.setCharisma(v);
        }
    }

    // ==================================================================
    // Aba Combate
    // ==================================================================

    private Node buildCombatTab() {
        // --- CA --- (sem a caixa de escudo: escudo e itens magicos entram no valor da CA)
        if (sheet.migrateShieldIntoArmorClass()) changedLater();
        VBox ac = card(sectionLabel("CLASSE DE ARMADURA"),
                row(new Label("CA"), nullableIntField(sheet::getArmorClass, sheet::setArmorClass, 3)),
                wrapped(mutedLabel("Some armadura, escudo e itens (ex.: Anel de Proteção).")),
                bigStat("CA total", derived(() -> String.valueOf(sheet.totalArmorClass()))));

        // --- PV ---
        Label hpNow = derived(() -> (sheet.getHpCurrent() == null ? "?" : sheet.getHpCurrent())
                + " / " + (sheet.getHpMax() == null ? "?" : sheet.getHpMax())
                + (sheet.getHpTemp() > 0 ? "  (+" + sheet.getHpTemp() + " temp)" : ""));
        hpNow.setStyle(BIG_NUMBER_STYLE);
        TextField hpCurrent = nullableIntField(sheet::getHpCurrent, sheet::setHpCurrent, 4);
        TextField hpMax = nullableIntField(sheet::getHpMax, sheet::setHpMax, 4);
        TextField hpTemp = intField(sheet::getHpTemp, sheet::setHpTemp, 3);
        // Os campos acima nao escutam mudancas feitas por dano/cura, entao
        // eles mesmos entram na lista de derivados (sem disparar changed de novo
        // quando o texto ja esta certo).
        derivedUpdaters.add(() -> syncText(hpCurrent, sheet.getHpCurrent() == null ? "" : String.valueOf(sheet.getHpCurrent())));
        derivedUpdaters.add(() -> syncText(hpTemp, String.valueOf(sheet.getHpTemp())));
        TextField amount = new TextField();
        amount.setPromptText("qtd");
        amount.setPrefColumnCount(4);
        Button damage = new Button("Dano");
        damage.setOnAction(e -> applyAmount(amount, true));
        Button heal = new Button("Cura");
        heal.setOnAction(e -> applyAmount(amount, false));
        amount.setOnAction(e -> applyAmount(amount, true));
        VBox hp = card(sectionLabel("PONTOS DE VIDA"), hpNow,
                row(new Label("Atual"), hpCurrent, new Label("Max"), hpMax, new Label("Temp"), hpTemp),
                row(amount, damage, heal));

        // --- Dados de vida: quantos de cada tipo (multiclasse) e gastar o tipo escolhido ---
        GridPane dice = new GridPane();
        dice.setHgap(8);
        dice.setVgap(4);
        dice.add(mutedLabel("Dado"), 0, 0);
        dice.add(mutedLabel("Total"), 1, 0);
        dice.add(mutedLabel("Restam"), 2, 0);
        int diceRow = 1;
        for (String die : CharacterSheet.HIT_DIE_TYPES) {
            Spinner<Integer> total = new Spinner<>(0, 40, sheet.hitDiceTotals().getOrDefault(die, 0));
            total.setPrefWidth(70);
            total.setEditable(true);
            total.valueProperty().addListener((obs, old, v) -> {
                if (v == null || v.equals(sheet.hitDiceTotals().getOrDefault(die, 0))) return;
                sheet.setHitDiceCount(die, v);
                changed();
            });
            derivedUpdaters.add(() -> {
                int now = sheet.hitDiceTotals().getOrDefault(die, 0);
                if (!Integer.valueOf(now).equals(total.getValue())) total.getValueFactory().setValue(now);
            });
            Button spend = new Button("Gastar");
            spend.setTooltip(new Tooltip("Descanso curto: rola 1" + die + " + CON e cura"));
            spend.setOnAction(e -> onSpendHitDie(die));
            derivedUpdaters.add(() -> spend.setDisable(sheet.hitDiceRemainingOf(die) <= 0));
            dice.add(new Label(die), 0, diceRow);
            dice.add(total, 1, diceRow);
            dice.add(derived(() -> sheet.hitDiceRemainingOf(die) + " / " + sheet.hitDiceTotals().getOrDefault(die, 0)), 2, diceRow);
            dice.add(spend, 3, diceRow);
            diceRow++;
        }
        Button longRest = new Button("Descanso longo");
        longRest.setOnAction(e -> {
            sheet.longRest();
            actions.chat(sheet, sheet.getName() + " terminou um descanso longo (PV, dados de vida e slots recuperados).");
            changed();
        });
        VBox hitDice = card(sectionLabel("DADOS DE VIDA"), dice,
                derived(() -> "Restam " + sheet.hitDiceRemaining() + " de " + sheet.hitDiceMax()), row(longRest));

        // --- Testes contra a morte: a rolagem ja marca e aplica (20 volta com 1 PV; 3 sucessos = estavel) ---
        HBox successes = deathSaveBoxes(true);
        HBox failures = deathSaveBoxes(false);
        Button deathRoll = new Button("Rolar teste contra a morte");
        deathRoll.setOnAction(e -> onDeathSave());
        derivedUpdaters.add(() -> deathRoll.setDisable(sheet.getHpCurrent() == null || sheet.getHpCurrent() > 0
                || sheet.getDeathSaveFailures() >= 3));
        VBox death = card(sectionLabel("TESTES CONTRA A MORTE"),
                row(new Label("Sucessos"), successes), row(new Label("Falhas    "), failures), deathRoll,
                mutedLabel("10+ sucesso · 1 = duas falhas · 20 = volta com 1 PV"));

        // --- Iniciativa: DES + bonus extra + dados extras, e o botao de rolar ---
        Button initRoll = new Button("Rolar iniciativa");
        initRoll.getStyleClass().add("button-primary");
        initRoll.setOnAction(e -> actions.rollInitiative(sheet, rollMode, hiddenRolls));
        TextField initDice = textField(sheet::getInitiativeDice, sheet::setInitiativeDice);
        initDice.setPromptText("ex.: 1d4");
        initDice.setPrefColumnCount(6);
        Label initTotal = derived(() -> signed(sheet.initiative())
                + (sheet.getInitiativeDice().isBlank() ? "" : " + " + sheet.getInitiativeDice()));
        initTotal.setStyle(BIG_NUMBER_STYLE);
        VBox misc = card(sectionLabel("INICIATIVA / DESLOCAMENTO"),
                row(new Label("Iniciativa"), initTotal, initRoll),
                row(new Label("DES"), derived(() -> signed(CharacterSheet.abilityModifier(sheet.getDexterity()))),
                        new Label("bônus extra"), intField(sheet::getInitiativeBonus, sheet::setInitiativeBonus, 2),
                        new Label("dados"), initDice),
                row(new Label("Deslocamento"), textField(sheet::getSpeed, sheet::setSpeed)),
                row(new Label("Tamanho"), textField(sheet::getSize, sheet::setSize)),
                bigStat("Percepção passiva", derived(() -> String.valueOf(sheet.passivePerception()))));

        // --- Resistencias (o dano com tipo ja chega cortado / zerado / dobrado) ---
        VBox defenses = card(sectionLabel("RESISTÊNCIAS A DANO"),
                row(new Label("Resistente"), listField(sheet::getDamageResistances, sheet::setDamageResistances)),
                row(new Label("Imune       "), listField(sheet::getDamageImmunities, sheet::setDamageImmunities)),
                row(new Label("Vulnerável"), listField(sheet::getDamageVulnerabilities, sheet::setDamageVulnerabilities)),
                mutedLabel("ex.: fogo, veneno"));

        // coluna da direita da pagina principal: o que mais se olha em combate primeiro
        VBox column = new VBox(10, hp, ac, misc, hitDice, death, defenses);
        for (Node n : column.getChildren()) if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return column;
    }

    /**
     * Pagina principal (como a frente de uma ficha de papel): atributos com salvaguardas e pericias a esquerda;
     * PV, CA, iniciativa, dados de vida, testes contra a morte a direita; ataques, recursos e efeitos embaixo.
     */
    private Node buildMainPage() {
        Node abilities = buildAbilitiesTab();
        Node combat = buildCombatTab();
        if (combat instanceof Region r) {
            r.setMinWidth(350);
            r.setPrefWidth(380);
            r.setMaxWidth(400);
        }
        // esquerda: atributos, ataques e recursos; direita: combate; embaixo, de ponta a ponta: condicoes e efeitos
        VBox left = new VBox(12, abilities, sectionLabel("ARMAS E TRUQUES DE DANO"), buildAttacksTable(), buildResourcesCard());
        HBox top = new HBox(12, left, combat);
        HBox.setHgrow(left, Priority.ALWAYS);
        return new VBox(12, top, buildEffectsCard());
    }

    /**
     * Condicoes, exaustao, concentracao e efeitos livres -- tudo isso vai
     * pro token ligado no mapa e muda as rolagens desta ficha (ver
     * CharacterSheet.rollAdjustment).
     */
    private Node buildEffectsCard() {
        FlowPane conditions = new FlowPane(10, 4);
        for (Condition c : Condition.values()) {
            if (c == Condition.EXHAUSTION) continue; // exaustao tem nivel proprio, logo abaixo
            CheckBox box = new CheckBox(c.label());
            box.setSelected(sheet.hasCondition(c));
            box.selectedProperty().addListener((obs, old, v) -> {
                sheet.setCondition(c, v);
                changed();
            });
            derivedUpdaters.add(() -> {
                if (box.isSelected() != sheet.hasCondition(c)) box.setSelected(sheet.hasCondition(c));
            });
            conditions.getChildren().add(box);
        }
        Spinner<Integer> exhaustion = new Spinner<>(0, 6, sheet.getExhaustionLevel());
        exhaustion.setPrefWidth(70);
        exhaustion.valueProperty().addListener((obs, old, v) -> {
            sheet.setExhaustionLevel(v);
            changed();
        });
        TextField concentration = textField(sheet::getConcentratingOn, sheet::setConcentratingOn);
        concentration.setPromptText("magia (vazio = nenhuma)");
        derivedUpdaters.add(() -> syncText(concentration, orEmpty(sheet.getConcentratingOn())));
        Button endConcentration = new Button("Encerrar");
        endConcentration.setOnAction(e -> {
            if (sheet.getConcentratingOn() != null) {
                actions.chat(sheet, sheet.getName() + " encerra a concentração em " + sheet.getConcentratingOn() + ".");
            }
            sheet.setConcentratingOn(null);
            concentration.setText("");
            changed();
        });
        TextField effects = new TextField(String.join(", ", sheet.getEffects()));
        effects.setPromptText("Bênção, Escudo, Fúria, Anel [ca+1, salv+1]...");
        effects.textProperty().addListener((obs, old, v) -> {
            sheet.setEffects(com.vttcaseiro.character.EffectModifiers.splitList(v));
            changed();
        });
        derivedUpdaters.add(() -> syncText(effects, String.join(", ", sheet.getEffects())));
        Label active = derived(() -> {
            String d = com.vttcaseiro.character.EffectModifiers.describe(sheet.getEffects());
            return d.isEmpty() ? "" : "Efeitos ativos: " + d;
        });
        active.setStyle("-fx-text-fill: -c-accent-text; -fx-font-weight: bold;");
        active.setWrapText(true);
        Label hint = mutedLabel("Condições, exaustão e efeitos já mudam as rolagens e a CA desta ficha. Conhecidos: "
                + "Bless, Bane, Shield (+5 CA), Shield of Faith, Guidance, Rage, Haste, Dodge. "
                + "Outros com números entre colchetes: ca+1, ataque+1d4, salv+1, teste+1d4, dano+2, "
                + "resist:fogo, vant:salv-des.");
        hint.setWrapText(true);
        return card(sectionLabel("CONDIÇÕES E EFEITOS"), conditions,
                row(new Label("Exaustão (0-6)"), exhaustion, derived(() -> sheet.getExhaustionLevel() == 0 ? ""
                        : "Deslocamento: " + sheet.speedWithExhaustion())),
                row(new Label("Concentrando em"), concentration, endConcentration),
                labeled("Outros efeitos", effects), active, hint);
    }

    // ------------------------------------------------------------------
    // Recursos com usos (Furia 3/3, cargas, pocoes)
    // ------------------------------------------------------------------

    private static String resourcesSignature(CharacterSheet s) {
        StringBuilder sb = new StringBuilder();
        for (com.vttcaseiro.character.Resource r : s.getResources()) {
            sb.append(r.getName()).append(r.usesText()).append(r.getRecharge()).append(';');
        }
        return sb.toString();
    }

    private Node buildResourcesCard() {
        VBox rows = new VBox(4);
        String[] shown = {""};
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            shown[0] = resourcesSignature(sheet);
            rows.getChildren().clear();
            if (sheet.getResources().isEmpty()) {
                rows.getChildren().add(mutedLabel("Nenhum recurso ainda. Ex.: Fúria 3 (longo), Segundo Fôlego 1 (curto),"
                        + " Poção de cura 2 (consumível)."));
            }
            for (com.vttcaseiro.character.Resource r : new ArrayList<>(sheet.getResources())) {
                Label name = new Label(r.getName());
                name.setMinWidth(160);
                name.setStyle("-fx-font-weight: bold;");
                Label uses = new Label(r.usesText());
                uses.setMinWidth(46);
                uses.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: "
                        + (r.getCurrent() == 0 ? "#D9544C" : "#FFCF7F") + ";");
                Button use = new Button("Usar");
                use.getStyleClass().add("button-primary");
                use.setDisable(r.getCurrent() <= 0);
                use.setOnAction(e -> {
                    if (!r.use()) return;
                    String extra = "";
                    // Recurso com nome de efeito conhecido (Furia) ja liga o efeito.
                    if (com.vttcaseiro.character.EffectModifiers.termsOf(r.getName()) != null
                            && sheet.getEffects().stream().noneMatch(x -> x.equalsIgnoreCase(r.getName()))) {
                        List<String> list = new ArrayList<>(sheet.getEffects());
                        list.add(r.getName());
                        sheet.setEffects(list);
                        extra = " -- efeito ativo";
                    }
                    actions.chat(sheet, sheet.getName() + " usa " + r.getName() + " (" + r.usesText() + " restantes)" + extra + ".");
                    changed();
                    rebuild[0].run();
                });
                Button plus = new Button("+");
                plus.setTooltip(new Tooltip("Recuperar 1"));
                plus.setOnAction(e -> {
                    r.setCurrent(r.getCurrent() + 1);
                    changed();
                    rebuild[0].run();
                });
                Button remove = new Button("x");
                remove.setTooltip(new Tooltip("Apagar este recurso"));
                remove.setOnAction(e -> {
                    sheet.getResources().remove(r);
                    changed();
                    rebuild[0].run();
                });
                Label recharge = mutedLabel(com.vttcaseiro.character.Resource.NEVER.equals(r.getRecharge())
                        ? "consumivel" : "volta no descanso " + r.getRecharge());
                rows.getChildren().add(row(name, uses, use, plus, recharge, remove));
            }
        };
        rebuild[0].run();
        derivedUpdaters.add(() -> {
            if (!shown[0].equals(resourcesSignature(sheet))) rebuild[0].run();
        });

        // O texto de exemplo era confundido com um nome ja escrito (campo vazio = o botao nao fazia nada).
        TextField newName = new TextField();
        newName.setPromptText("nome do recurso");
        newName.setPrefColumnCount(12);
        Spinner<Integer> newMax = new Spinner<>(1, 99, 3);
        newMax.setPrefWidth(70);
        newMax.setEditable(true);
        javafx.scene.control.ComboBox<String> newRecharge = new javafx.scene.control.ComboBox<>();
        newRecharge.getItems().addAll("descanso longo", "descanso curto", "consumivel");
        newRecharge.setValue("descanso longo");
        Button add = new Button("Adicionar");
        add.disableProperty().bind(newName.textProperty().isEmpty());
        add.setTooltip(new Tooltip("Escreva o nome (ex.: Fúria) e os usos"));
        newName.setOnAction(e -> add.fire());
        add.setOnAction(e -> {
            if (newName.getText().isBlank()) return;
            newMax.increment(0);   // aplica o numero digitado no campo de usos
            String recharge = switch (newRecharge.getValue()) {
                case "descanso curto" -> com.vttcaseiro.character.Resource.SHORT;
                case "consumivel" -> com.vttcaseiro.character.Resource.NEVER;
                default -> com.vttcaseiro.character.Resource.LONG;
            };
            sheet.getResources().add(new com.vttcaseiro.character.Resource(newName.getText(), newMax.getValue(), recharge));
            newName.clear();
            changed();
            rebuild[0].run();
        });
        Button shortRest = new Button("Descanso curto");
        shortRest.setTooltip(new Tooltip("Recupera os recursos que voltam no descanso curto"
                + " (os dados de vida ficam no card Dados de Vida)."));
        shortRest.setOnAction(e -> {
            List<String> back = sheet.recoverResources(false);
            actions.chat(sheet, sheet.getName() + " faz um descanso curto" + (back.isEmpty() ? "." : " e recupera "
                    + String.join(", ", back) + "."));
            changed();
            rebuild[0].run();
        });
        return card(sectionLabel("RECURSOS E USOS"), rows,
                row(new Label("Novo"), newName, new Label("usos"), newMax, newRecharge, add), row(shortRest));
    }

    private void applyAmount(TextField amountField, boolean isDamage) {
        Integer value = parseInt(amountField.getText());
        if (value == null || value <= 0) return;
        if (isDamage) {
            CharacterSheet.DamageOutcome outcome = sheet.takeDamage(value);
            actions.chat(sheet, TokenLink.describeDamage(sheet.getName(), value, outcome));
        } else {
            int before = sheet.getHpCurrent() == null ? 0 : sheet.getHpCurrent();
            int after = sheet.heal(value);
            actions.chat(sheet, sheet.getName() + " recupera " + (after - before) + " PV.");
        }
        amountField.clear();
        changed();
    }

    /** Mudanca feita enquanto a tela monta (ex.: CA antiga com escudo): grava logo depois. */
    private void changedLater() {
        javafx.application.Platform.runLater(this::changed);
    }

    /** Teste contra a morte: rola, marca a caixa certa e aplica o efeito (o resultado vai pro chat). */
    private void onDeathSave() {
        CharacterSheet target = sheet;
        actions.rollD20Then(target, "Teste contra a morte", 0, RollKind.DEATH_SAVE, null, rollMode, hiddenRolls, natural -> {
            CharacterSheet.DeathSaveOutcome outcome = target.applyDeathSave(natural);
            String text = switch (outcome) {
                case REVIVED -> target.getName() + " tira 20 no teste contra a morte e volta com 1 PV!";
                case SUCCESS -> target.getName() + ": sucesso no teste contra a morte (" + target.getDeathSaveSuccesses() + "/3).";
                case STABLE -> target.getName() + " está estável (3 sucessos): fica a 0 PV, inconsciente, sem rolar mais.";
                case FAILURE -> target.getName() + ": falha no teste contra a morte (" + target.getDeathSaveFailures() + "/3)"
                        + (natural <= 1 ? " -- 1 natural conta como duas." : ".");
                case DEAD -> target.getName() + " morreu (3 falhas no teste contra a morte).";
                case NONE -> null;
            };
            if (text != null) actions.chat(target, text);
            if (target == sheet) changed();
        });
    }

    private void onSpendHitDie(String die) {
        if (!sheet.spendHitDie(die)) {
            actions.chat(sheet, sheet.getName() + " não tem mais " + die + " de vida.");
            return;
        }
        int con = CharacterSheet.abilityModifier(sheet.getConstitution());
        String expression = "1" + die + (con >= 0 ? "+" + con : String.valueOf(con));
        // O valor curado e aplicado aqui mesmo: rola localmente pra saber o
        // numero, e manda o resultado pro chat.
        try {
            var result = com.vttcaseiro.dice.DiceRoller.roll(expression);
            int healed = Math.max(0, result.total());
            sheet.heal(healed);
            actions.chat(sheet, sheet.getName() + " gasta um dado de vida: " + result.detail()
                    + " -> recupera " + healed + " PV.");
        } catch (com.vttcaseiro.dice.DiceException ex) {
            actions.chat(sheet, "Dado de vida inválido: " + ex.getMessage());
        }
        changed();
    }

    private HBox deathSaveBoxes(boolean successes) {
        HBox box = new HBox(4);
        CheckBox[] checks = new CheckBox[3];
        for (int i = 0; i < 3; i++) {
            final int count = i + 1;
            CheckBox c = new CheckBox();
            checks[i] = c;
            c.setOnAction(e -> {
                int current = successes ? sheet.getDeathSaveSuccesses() : sheet.getDeathSaveFailures();
                // Clicar na caixa N marca ate N; clicar na ultima marcada desmarca ela.
                int next = current >= count ? count - 1 : count;
                if (successes) sheet.setDeathSaveSuccesses(next); else sheet.setDeathSaveFailures(next);
                changed();
            });
            box.getChildren().add(c);
        }
        derivedUpdaters.add(() -> {
            int n = successes ? sheet.getDeathSaveSuccesses() : sheet.getDeathSaveFailures();
            for (int i = 0; i < 3; i++) checks[i].setSelected(i < n);
        });
        return box;
    }

    private Node buildAttacksTable() {
        TableView<Attack> table = new TableView<>();
        table.setPrefHeight(150);
        table.getColumns().add(column("Nome", Attack::getName, 180));
        table.getColumns().add(column("Bonus / CD", Attack::getAttackBonusOrDc, 90));
        table.getColumns().add(column("Dano e tipo", Attack::getDamageAndType, 160));
        table.getColumns().add(column("Notas", Attack::getNotes, 220));
        table.getItems().setAll(sheet.getAttacks());
        table.setPlaceholder(new Label("Nenhum ataque. Use \"Adicionar\"."));

        Runnable reload = () -> table.getItems().setAll(sheet.getAttacks());
        table.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                Attack selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && editAttack(selected)) {
                    reload.run();
                    changed();
                }
            }
        });

        Button add = new Button("Adicionar");
        add.setOnAction(e -> {
            Attack attack = new Attack();
            if (editAttack(attack)) {
                sheet.addAttack(attack);
                reload.run();
                changed();
            }
        });
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            Attack selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sheet.getAttacks().remove(selected);
                reload.run();
                changed();
            }
        });
        // Um botao so: acerto e dano juntos. 20 natural dobra os dados do dano; 1 natural nao rola dano.
        Button attackBtn = new Button("Atacar");
        attackBtn.getStyleClass().add("button-primary");
        attackBtn.setTooltip(new Tooltip("Rola o acerto e o dano juntos. Crítico (20) dobra todos os dados do dano; "
                + "falha crítica (1) não rola dano. Com CD (sem bônus de acerto), rola só o dano."));
        attackBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        attackBtn.setOnAction(e -> {
            Attack a = table.getSelectionModel().getSelectedItem();
            if (a == null) return;
            if (a.attackBonus().isEmpty() && a.damageExpression().isEmpty()) {
                actions.chat(sheet, sheet.getName() + " usa " + a.getName()
                        + (a.getAttackBonusOrDc().isBlank() ? "" : " (" + a.getAttackBonusOrDc() + ")"));
                return;
            }
            actions.attack(sheet, a, rollMode, hiddenRolls);
        });
        Label hint = new Label("Clique duplo para editar (ou para atacar com o botão). Bônus como \"+5\" rola o d20; "
                + "\"1d8+3 cortante\" rola o dano. Com alvos marcados no mapa, o dano cai sozinho em quem foi acertado.");
        hint.setWrapText(true);
        hint.getStyleClass().add("label-muted");
        return new VBox(6, table, row(attackBtn, add, remove), hint);
    }

    private boolean editAttack(Attack attack) {
        TextField name = new TextField(attack.getName());
        TextField bonus = new TextField(attack.getAttackBonusOrDc());
        bonus.setPromptText("+5 ou CD 13");
        TextField damage = new TextField(attack.getDamageAndType());
        damage.setPromptText("1d8+3 cortante");
        TextField notes = new TextField(attack.getNotes());
        // Qualquer atributo (arma com FOR, DES, CAR do Pacto da Lâmina...) ou o de conjuração.
        ComboBox<String> ability = new ComboBox<>();
        ability.getItems().add("(digitar o bônus)");
        for (Ability a : Ability.values()) ability.getItems().add(a.abbreviationPt());
        ability.getItems().add("Magia");
        ability.setValue("(digitar o bônus)");
        CheckBox proficient = new CheckBox("proficiente");
        proficient.setSelected(true);
        CheckBox addToDamage = new CheckBox("somar o atributo no dano");
        addToDamage.setSelected(true);
        Runnable fill = () -> {
            String v = ability.getValue();
            if (v == null || v.startsWith("(")) return;
            if (v.equals("Magia")) {
                bonus.setText(signed(sheet.spellAttackBonus()));
                return;
            }
            Ability a = abilityByAbbreviation(v);
            bonus.setText(signed(CharacterSheet.abilityModifier(sheet.abilityScore(a))
                    + (proficient.isSelected() ? sheet.proficiencyBonus() : 0)));
        };
        ability.valueProperty().addListener((obs, old, v) -> fill.run());
        proficient.selectedProperty().addListener((obs, old, v) -> fill.run());
        boolean ok = formDialog("Ataque", List.of(
                labeled("Nome", name),
                labeled("Atributo", new HBox(8, ability, proficient)),
                labeled("Bonus / CD", bonus),
                labeled("Dano e tipo", new HBox(8, damage, addToDamage)),
                labeled("Notas", notes)));
        if (ok) {
            attack.setName(name.getText().trim());
            attack.setAttackBonusOrDc(bonus.getText().trim());
            String dmg = damage.getText().trim();
            String chosen = ability.getValue();
            if (addToDamage.isSelected() && chosen != null && !chosen.startsWith("(") && !chosen.equals("Magia")) {
                dmg = withDamageModifier(dmg, CharacterSheet.abilityModifier(sheet.abilityScore(abilityByAbbreviation(chosen))));
            }
            attack.setDamageAndType(dmg);
            attack.setNotes(notes.getText().trim());
        }
        return ok;
    }

    private static Ability abilityByAbbreviation(String abbreviation) {
        for (Ability a : Ability.values()) if (a.abbreviationPt().equals(abbreviation)) return a;
        return Ability.STRENGTH;
    }

    /**
     * Troca o modificador fixo do dano pelo do atributo: "1d8+2 cortante" com +4 vira "1d8+4 cortante";
     * "2d6 fogo" vira "2d6+4 fogo". Os dados e o tipo ficam como estao.
     */
    static String withDamageModifier(String damage, int modifier) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^((?:\\d*d\\d+)(?:\\s*\\+\\s*\\d*d\\d+)*)(\\s*[+-]\\s*\\d+)?(.*)$")
                .matcher(damage == null ? "" : damage.trim());
        if (!m.matches()) return damage;
        String mod = modifier == 0 ? "" : (modifier > 0 ? "+" + modifier : String.valueOf(modifier));
        return m.group(1).replaceAll("\\s+", "") + mod + m.group(3);
    }

    // ==================================================================
    // Aba Tracos (Class Features, Species Traits, Feats, proficiencias)
    // ==================================================================

    private Node buildTraitsTab() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            grid.getColumnConstraints().add(c);
        }
        grid.add(traitBox("CARACTERÍSTICAS DE CLASSE", "class"), 0, 0);
        grid.add(traitBox("TRAÇOS DE ESPÉCIE", "species"), 1, 0);
        grid.add(traitBox("TALENTOS (FEATS)", "feat"), 0, 1);
        grid.add(traitBox("OUTROS", "other"), 1, 1);

        VBox proficiencies = card(sectionLabel("TREINAMENTO E PROFICIÊNCIAS"),
                row(new Label("Armaduras:"),
                        checkBox("Leve", sheet::isArmorTrainingLight, sheet::setArmorTrainingLight),
                        checkBox("Media", sheet::isArmorTrainingMedium, sheet::setArmorTrainingMedium),
                        checkBox("Pesada", sheet::isArmorTrainingHeavy, sheet::setArmorTrainingHeavy),
                        checkBox("Escudos", sheet::isArmorTrainingShields, sheet::setArmorTrainingShields)),
                labeled("Armas", textField(sheet::getWeaponProficiencies, sheet::setWeaponProficiencies)),
                labeled("Ferramentas", textField(sheet::getToolProficiencies, sheet::setToolProficiencies)));

        VBox box = new VBox(10, grid, proficiencies);
        if (!sheet.getCompendiumAttachments().isEmpty()) {
            box.getChildren().addAll(sectionLabel("ANEXOS DO COMPÊNDIO (clique duplo manda ao chat)"), buildCompendiumList());
        }
        return box;
    }

    private Node traitBox(String title, String category) {
        ListView<CharacterTrait> list = new ListView<>();
        list.setPrefHeight(150);
        list.getItems().setAll(sheet.traitsInCategory(category));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CharacterTrait trait, boolean empty) {
                super.updateItem(trait, empty);
                setText(empty || trait == null ? null : trait.getName());
                setTooltip(empty || trait == null || trait.getDescription() == null ? null : new Tooltip(trait.getDescription()));
            }
        });
        Runnable reload = () -> list.getItems().setAll(sheet.traitsInCategory(category));
        list.setOnMouseClicked(e -> {
            CharacterTrait selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 2) {
                actions.chat(sheet, sheet.getName() + " usa " + selected.toChatText());
            }
        });
        Button add = new Button("+");
        add.setOnAction(e -> {
            CharacterTrait trait = new CharacterTrait("", "", category);
            if (editTrait(trait)) {
                sheet.addTrait(trait);
                reload.run();
                changed();
            }
        });
        Button edit = new Button("Editar");
        edit.setOnAction(e -> {
            CharacterTrait selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && editTrait(selected)) {
                reload.run();
                changed();
            }
        });
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            CharacterTrait selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sheet.removeTrait(selected);
                reload.run();
                changed();
            }
        });
        CompendiumEntryType fromType = switch (category) {
            case "species" -> CompendiumEntryType.SPECIES;
            case "feat" -> CompendiumEntryType.FEAT;
            case "other" -> CompendiumEntryType.BACKGROUND;
            default -> null;
        };
        HBox buttons = row(add, edit, remove);
        if (fromType != null) {
            Button fromCompendium = new Button("Do compêndio...");
            fromCompendium.setOnAction(e -> addFromCompendium(fromType));
            buttons.getChildren().add(fromCompendium);
        } else {
            Button apply = new Button("Aplicar classe...");
            apply.setOnAction(e -> onApplyClass());
            buttons.getChildren().add(apply);
        }
        buttons.getChildren().add(mutedLabel("clique duplo = chat"));
        return card(sectionLabel(title), list, buttons);
    }

    private boolean editTrait(CharacterTrait trait) {
        TextField name = new TextField(orEmpty(trait.getName()));
        TextArea description = new TextArea(orEmpty(trait.getDescription()));
        description.setWrapText(true);
        description.setPrefRowCount(6);
        boolean ok = formDialog("Traço", List.of(labeled("Nome", name), new Label("Descrição"), description));
        if (ok && !name.getText().isBlank()) {
            trait.setName(name.getText().trim());
            trait.setDescription(description.getText().trim());
            return true;
        }
        return false;
    }

    private ListView<CompendiumReference> buildCompendiumList() {
        ListView<CompendiumReference> list = new ListView<>();
        list.setPrefHeight(120);
        list.getItems().addAll(sheet.getCompendiumAttachments());
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CompendiumReference ref, boolean empty) {
                super.updateItem(ref, empty);
                setText(empty || ref == null ? null
                        : ref.getDisplayName() + (ref.getEntryType() == null ? "" : " (" + ref.getEntryType() + ")"));
            }
        });
        list.setOnMouseClicked(e -> {
            CompendiumReference selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 2) {
                actions.compendium(sheet, selected);
            }
        });
        return list;
    }

    // ==================================================================
    // Aba Magias
    // ==================================================================

    private Node buildSpellsTab() {
        ComboBox<String> abilityBox = new ComboBox<>();
        abilityBox.getItems().add("Nenhuma");
        for (Ability a : Ability.values()) abilityBox.getItems().add(abilityNamePt(a));
        abilityBox.setValue(sheet.getSpellcastingAbility() == null ? "Nenhuma" : abilityNamePt(sheet.getSpellcastingAbility()));
        abilityBox.valueProperty().addListener((obs, old, v) -> {
            Ability chosen = null;
            for (Ability a : Ability.values()) if (abilityNamePt(a).equals(v)) chosen = a;
            sheet.setSpellcastingAbility(chosen);
            changed();
        });
        Button spellAttack = derivedButton(() -> sheet.isSpellcaster() ? signed(sheet.spellAttackBonus()) : "--",
                () -> {
                    if (sheet.isSpellcaster()) rollD20("Ataque de magia", sheet.spellAttackBonus(), RollKind.ATTACK, null);
                });
        spellAttack.setStyle(BIG_NUMBER_STYLE);
        HBox casting = new HBox(16,
                card(sectionLabel("HABILIDADE DE CONJURAÇÃO"), abilityBox),
                card(sectionLabel("MODIFICADOR"), bigNumber(derived(() -> sheet.isSpellcaster() ? signed(sheet.spellcastingModifier()) : "--"))),
                card(sectionLabel("CD DE MAGIA"), bigNumber(derived(() -> sheet.isSpellcaster() ? String.valueOf(sheet.spellSaveDc()) : "--"))),
                card(sectionLabel("ATAQUE DE MAGIA"), spellAttack));

        return new VBox(10, casting, sectionLabel("ESPAÇOS DE MAGIA (slots)"), buildSlotsGrid(),
                sectionLabel("TRUQUES E MAGIAS PREPARADAS"), buildSpellsTable());
    }

    private Node buildSlotsGrid() {
        SpellSlots slots = sheet.getSpellSlots();
        FlowPane pane = new FlowPane(8, 8);
        for (int level = SpellSlots.MIN_LEVEL; level <= SpellSlots.MAX_LEVEL; level++) {
            final int lvl = level;
            TextField total = intField(() -> slots.getMax(lvl), v -> {
                int value = Math.max(0, v);
                int spent = slots.getMax(lvl) - slots.getCurrent(lvl);
                slots.setMax(lvl, value);
                slots.setCurrent(lvl, Math.max(0, value - spent));
            }, 2);
            Label remaining = derived(() -> slots.getCurrent(lvl) + " restantes");
            Button use = new Button("Usar");
            use.setOnAction(e -> {
                if (slots.useSlot(lvl)) changed();
            });
            Button restore = new Button("+");
            restore.setTooltip(new Tooltip("Recuperar 1"));
            restore.setOnAction(e -> {
                slots.restoreSlot(lvl);
                changed();
            });
            VBox box = card(sectionLabel("Nível " + lvl), row(new Label("Total"), total), remaining, row(use, restore));
            box.setPrefWidth(150);
            pane.getChildren().add(box);
        }
        return pane;
    }

    private Node buildSpellsTable() {
        TableView<PreparedSpell> table = new TableView<>();
        table.setPrefHeight(240);
        table.getColumns().add(column("Nível", PreparedSpell::levelText, 60));
        table.getColumns().add(column("Nome", PreparedSpell::getName, 180));
        table.getColumns().add(column("Tempo", PreparedSpell::getCastingTime, 90));
        table.getColumns().add(column("Alcance", PreparedSpell::getRange, 80));
        table.getColumns().add(column("C/R/M", PreparedSpell::flagsText, 60));
        table.getColumns().add(column("Dados", sp -> sp.getDice().isBlank() ? ""
                : sp.getDice() + (sp.getDicePerLevel().isBlank() ? "" : " (+" + sp.getDicePerLevel() + "/nv)"), 110));
        table.getColumns().add(column("Notas", PreparedSpell::getNotes, 220));
        table.setPlaceholder(new Label("Nenhuma magia. Use \"Adicionar\"."));

        Runnable reload = () -> {
            List<PreparedSpell> sorted = new ArrayList<>(sheet.getSpells());
            sorted.sort((a, b) -> a.getLevel() != b.getLevel()
                    ? Integer.compare(a.getLevel(), b.getLevel())
                    : String.CASE_INSENSITIVE_ORDER.compare(orEmpty(a.getName()), orEmpty(b.getName())));
            table.getItems().setAll(sorted);
        };
        reload.run();
        table.setOnMouseClicked(e -> {
            PreparedSpell selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 2) {
                actions.chat(sheet, sheet.getName() + " conjura " + selected.toChatText());
            }
        });
        Button add = new Button("Adicionar");
        add.setOnAction(e -> {
            PreparedSpell spell = new PreparedSpell();
            if (editSpell(spell)) {
                sheet.getSpells().add(spell);
                reload.run();
                changed();
            }
        });
        Button edit = new Button("Editar");
        edit.setOnAction(e -> {
            PreparedSpell selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && editSpell(selected)) {
                reload.run();
                changed();
            }
        });
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            PreparedSpell selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sheet.getSpells().remove(selected);
                reload.run();
                changed();
            }
        });
        Button cast = new Button("Conjurar");
        cast.getStyleClass().add("button-primary");
        cast.setTooltip(new Tooltip("Gasta o espaço de magia (pergunta o nível se der pra conjurar mais alto),"
                + " marca concentração e rola os dados da magia."));
        cast.setOnAction(e -> {
            PreparedSpell selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) castSpell(selected);
        });
        Button fromCompendium = new Button("Do compêndio...");
        fromCompendium.setOnAction(e -> addFromCompendium(CompendiumEntryType.SPELL));
        return new VBox(6, table, row(add, fromCompendium, edit, remove, cast, mutedLabel("clique duplo = chat")));
    }

    /**
     * Conjurar: escolhe o espaco (o da magia, ou mais alto se o jogador
     * quiser / se o do nivel acabou), gasta, cuida da concentracao, manda ao
     * chat e rola os dados (com os extras por nivel acima).
     */
    private void castSpell(PreparedSpell spell) {
        if (castSpell(sheet, spell, actions, stage, hiddenRolls)) changed();
    }

    /**
     * Conjura {@code spell} da ficha (escolhe e gasta o espaco, concentracao, chat, dados). Usado pela janela da
     * ficha e pela barra de acoes do token. Devolve true se conjurou (a ficha mudou).
     */
    public static boolean castSpell(CharacterSheet sheet, PreparedSpell spell, Actions actions, javafx.stage.Window owner,
                                    boolean hiddenRolls) {
        SpellSlots slots = sheet.getSpellSlots();
        int slotLevel = 0;
        if (spell.getLevel() > 0) {
            List<Integer> available = new ArrayList<>();
            for (int lvl = spell.getLevel(); lvl <= SpellSlots.MAX_LEVEL; lvl++) {
                if (slots.getCurrent(lvl) > 0) available.add(lvl);
            }
            if (available.isEmpty()) {
                actions.chat(sheet, sheet.getName() + " não tem espaço de magia de nível " + spell.getLevel()
                        + " ou maior sobrando pra " + spell.getName() + ".");
                return false;
            }
            if (available.size() == 1) {
                slotLevel = available.get(0);
            } else {
                List<String> labels = new ArrayList<>();
                for (int lvl : available) labels.add("Nível " + lvl + " (" + slots.getCurrent(lvl) + " sobrando)");
                javafx.scene.control.ChoiceDialog<String> choose = new javafx.scene.control.ChoiceDialog<>(labels.get(0), labels);
                choose.setTitle("Conjurar " + spell.getName());
                choose.setHeaderText("Em qual espaço de magia?");
                choose.setContentText("Espaço:");
                if (owner != null) choose.initOwner(owner);
                java.util.Optional<String> picked = choose.showAndWait();
                if (picked.isEmpty()) return false;
                slotLevel = available.get(labels.indexOf(picked.get()));
            }
            slots.useSlot(slotLevel);
        }
        String extra = slotLevel > spell.getLevel() && spell.getLevel() > 0 ? " no nível " + slotLevel : "";
        if (spell.isConcentration()) {
            if (sheet.getConcentratingOn() != null && !sheet.getConcentratingOn().equalsIgnoreCase(spell.getName())) {
                extra += " (encerra a concentração em " + sheet.getConcentratingOn() + ")";
            }
            sheet.setConcentratingOn(spell.getName());
        }
        actions.chat(sheet, sheet.getName() + " conjura " + spell.toChatText() + extra);
        actions.spellCast(sheet, spell, Math.max(slotLevel, spell.getLevel()));
        String dice = spell.diceForSlot(Math.max(slotLevel, spell.getLevel()));
        if (!dice.isBlank()) {
            actions.castSpellEffect(sheet, spell, Math.max(slotLevel, spell.getLevel()), dice, hiddenRolls);
        }
        return true;
    }

    /** Campo de texto "fogo, veneno" ligado a uma lista. */
    private TextField listField(java.util.function.Supplier<List<String>> getter,
                                java.util.function.Consumer<List<String>> setter) {
        TextField field = new TextField(String.join(", ", getter.get()));
        field.setPrefColumnCount(12);
        field.textProperty().addListener((o, a, v) -> {
            List<String> parts = new ArrayList<>();
            for (String p : v.split(",")) if (!p.isBlank()) parts.add(p.trim());
            setter.accept(parts);
            changed();
        });
        return field;
    }

    private boolean editSpell(PreparedSpell spell) {
        Spinner<Integer> level = new Spinner<>(0, 9, spell.getLevel());
        level.setPrefWidth(70);
        TextField name = new TextField(spell.getName());
        TextField time = new TextField(spell.getCastingTime());
        time.setPromptText("1 ação");
        TextField range = new TextField(spell.getRange());
        range.setPromptText("18 m");
        CheckBox conc = new CheckBox("Concentração");
        conc.setSelected(spell.isConcentration());
        CheckBox ritual = new CheckBox("Ritual");
        ritual.setSelected(spell.isRitual());
        CheckBox material = new CheckBox("Material exigido");
        material.setSelected(spell.isMaterialRequired());
        TextField notes = new TextField(spell.getNotes());
        TextField dice = new TextField(spell.getDice());
        dice.setPromptText("8d6 (vazio = não rola)");
        TextField perLevel = new TextField(spell.getDicePerLevel());
        perLevel.setPromptText("1d6");
        TextField dmgType = new TextField(spell.isHealing() ? "cura"
                : com.vttcaseiro.character.DamageTypes.namePt(spell.getDamageType().isBlank() ? null : spell.getDamageType()));
        dmgType.setPromptText("fogo / cura");
        javafx.scene.control.ComboBox<String> saveBox = new javafx.scene.control.ComboBox<>();
        saveBox.getItems().add("Nenhuma");
        for (Ability a : Ability.values()) saveBox.getItems().add(a.abbreviationPt());
        Ability currentSave = spell.saveAbilityOrNull();
        saveBox.setValue(currentSave == null ? "Nenhuma" : currentSave.abbreviationPt());
        CheckBox half = new CheckBox("Metade se passar");
        half.setSelected(spell.isHalfOnSave());
        CheckBox attackRoll = new CheckBox("Pede jogada de ataque");
        attackRoll.setSelected(spell.isSpellAttack());
        boolean ok = formDialog("Magia", List.of(
                labeled("Nivel (0 = truque)", level), labeled("Nome", name),
                labeled("Tempo de conjuração", time), labeled("Alcance", range),
                new HBox(10, conc, ritual, material),
                labeled("Dados ao conjurar", dice), labeled("A mais por nível acima", perLevel),
                labeled("Tipo de dano", dmgType), new HBox(10, new Label("Salvaguarda"), saveBox, half),
                attackRoll,
                labeled("Notas", notes)));
        if (ok && !name.getText().isBlank()) {
            spell.setLevel(level.getValue());
            spell.setName(name.getText().trim());
            spell.setCastingTime(time.getText().trim());
            spell.setRange(range.getText().trim());
            spell.setConcentration(conc.isSelected());
            spell.setRitual(ritual.isSelected());
            spell.setMaterialRequired(material.isSelected());
            spell.setNotes(notes.getText().trim());
            spell.setDice(dice.getText());
            spell.setDicePerLevel(perLevel.getText());
            String typeText = dmgType.getText().trim();
            spell.setDamageType(typeText.equalsIgnoreCase("cura") ? "healing"
                    : java.util.Objects.requireNonNullElse(com.vttcaseiro.character.DamageTypes.find(typeText), ""));
            spell.setSaveAbility("");
            for (Ability a : Ability.values()) if (a.abbreviationPt().equals(saveBox.getValue())) spell.setSaveAbility(a.name());
            spell.setHalfOnSave(half.isSelected());
            spell.setSpellAttack(attackRoll.isSelected());
            return true;
        }
        return false;
    }

    // ==================================================================
    // Aba Personagem (pag. 2: aparencia, historia, idiomas, equipamento...)
    // ==================================================================

    private Node buildCharacterTab() {
        GridPane looks = new GridPane();
        looks.setHgap(8);
        looks.setVgap(4);
        addLabeled(looks, 0, 0, "Idade", textField(sheet::getAge, sheet::setAge));
        addLabeled(looks, 1, 0, "Altura", textField(sheet::getHeight, sheet::setHeight));
        addLabeled(looks, 2, 0, "Peso", textField(sheet::getWeight, sheet::setWeight));
        addLabeled(looks, 3, 0, "Alinhamento", textField(sheet::getAlignment, sheet::setAlignment));
        addLabeled(looks, 0, 2, "Olhos", textField(sheet::getEyes, sheet::setEyes));
        addLabeled(looks, 1, 2, "Pele", textField(sheet::getSkin, sheet::setSkin));
        addLabeled(looks, 2, 2, "Cabelo", textField(sheet::getHair, sheet::setHair));

        TextField languages = new TextField(String.join(", ", sheet.getLanguages()));
        languages.setPromptText("Comum, Élfico, ...");
        languages.textProperty().addListener((obs, old, v) -> {
            sheet.getLanguages().clear();
            Arrays.stream(v.split(",")).forEach(sheet::addLanguage);
            changed();
        });

        VBox appearance = card(sectionLabel("APARÊNCIA"), looks,
                textArea(sheet::getAppearance, sheet::setAppearance, 3));
        VBox personality = card(sectionLabel("HISTÓRIA E PERSONALIDADE"),
                new Label("Traços de personalidade"), textArea(sheet::getPersonalityTraits, sheet::setPersonalityTraits, 2),
                new Label("Ideais"), textArea(sheet::getIdeals, sheet::setIdeals, 2),
                new Label("Vínculos"), textArea(sheet::getBonds, sheet::setBonds, 2),
                new Label("Defeitos"), textArea(sheet::getFlaws, sheet::setFlaws, 2),
                new Label("História"), textArea(sheet::getBackstory, sheet::setBackstory, 6));
        VBox langs = card(sectionLabel("IDIOMAS"), languages);

        VBox attunement = card(sectionLabel("SINTONIA COM ITENS MÁGICOS"));
        List<String> attuned = sheet.getAttunedItems();
        for (int i = 0; i < 3; i++) {
            final int index = i;
            attunement.getChildren().add(textField(() -> attuned.get(index), v -> attuned.set(index, v == null ? "" : v)));
        }

        return new VBox(10, appearance, personality, langs, buildEquipmentBox(), attunement);
    }

    /** Aba do Bastiao: nome, instalacoes basicas, defensores e as seis instalacoes especiais (duas colunas). */
    private Node buildBastionTab() {
        com.vttcaseiro.character.Bastion b = sheet.getBastion();
        GridPane top = new GridPane();
        top.setHgap(10);
        top.setVgap(4);
        TextField bastionName = textField(b::getName, b::setName);
        bastionName.setPromptText("Nome do bastião");
        GridPane.setHgrow(bastionName, Priority.ALWAYS);
        Label who = new Label(orEmpty(sheet.getName()) + "  •  nível " + sheet.getLevel());
        who.setStyle("-fx-text-fill: -c-accent-text; -fx-font-weight: bold;");
        addLabeled(top, 0, 0, "Nome do bastião", bastionName);
        addLabeled(top, 1, 0, "Personagem", who);
        Label rule = mutedLabel(sheet.getLevel() < 5 ? "Bastiões começam no nível 5." : "");
        rule.setVisible(sheet.getLevel() < 5);
        rule.setManaged(sheet.getLevel() < 5);
        VBox header = card(sectionLabel("BASTIÃO"), top, rule);

        GridPane basics = new GridPane();
        basics.setHgap(10);
        basics.setVgap(4);
        javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        basics.getColumnConstraints().addAll(half, half);
        List<String> basicLines = b.getBasicFacilities();
        String[] examples = {"Quarto", "Cozinha", "Depósito", "Pátio", "Sala de jantar", "Sala de estar"};
        for (int i = 0; i < com.vttcaseiro.character.Bastion.BASIC_LINES; i++) {
            final int index = i;
            TextField line = textField(() -> basicLines.get(index), v -> b.setBasicFacility(index, v));
            if (i < examples.length) line.setPromptText("ex.: " + examples[i] + " (Espaçoso)");
            // seis na esquerda, cinco na direita (a sexta da direita e a linha dos defensores)
            basics.add(line, i < 6 ? 0 : 1, i < 6 ? i : i - 6);
        }
        TextField defenders = textField(b::getDefenders, b::setDefenders);
        defenders.setPromptText("quantos e quem");
        HBox defendersRow = new HBox(8, mutedLabel("Defensores do bastião"), defenders);
        defendersRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(defenders, Priority.ALWAYS);
        basics.add(defendersRow, 1, 5);
        VBox basicCard = card(sectionLabel("INSTALAÇÕES BÁSICAS"), basics);

        GridPane specials = new GridPane();
        specials.setHgap(10);
        specials.setVgap(10);
        javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
        col.setPercentWidth(50);
        specials.getColumnConstraints().addAll(col, col);
        List<com.vttcaseiro.character.Bastion.SpecialFacility> facilities = b.getSpecialFacilities();
        for (int i = 0; i < facilities.size(); i++) {
            specials.add(specialFacilityCard(facilities.get(i)), i % 2, i / 2);
        }
        return new VBox(10, header, basicCard, specials);
    }

    private Node specialFacilityCard(com.vttcaseiro.character.Bastion.SpecialFacility f) {
        TextField name = textField(f::getName, f::setName);
        name.setPromptText("ex.: Arsenal, Biblioteca, Jardim");
        ComboBox<String> space = new ComboBox<>();
        space.getItems().add("—");
        for (String[] kv : com.vttcaseiro.character.Bastion.SPACES) space.getItems().add(kv[1]);
        String spaceLabel = com.vttcaseiro.character.Bastion.label(com.vttcaseiro.character.Bastion.SPACES, f.getSpace());
        space.setValue(spaceLabel == null ? "—" : spaceLabel);
        space.setOnAction(e -> {
            f.setSpace(com.vttcaseiro.character.Bastion.key(com.vttcaseiro.character.Bastion.SPACES, space.getValue()));
            changed();
        });
        ComboBox<String> order = new ComboBox<>();
        order.getItems().add("—");
        for (String[] kv : com.vttcaseiro.character.Bastion.ORDERS) order.getItems().add(kv[1]);
        String orderLabel = com.vttcaseiro.character.Bastion.label(com.vttcaseiro.character.Bastion.ORDERS, f.getOrder());
        order.setValue(orderLabel == null ? "—" : orderLabel);
        order.setOnAction(e -> {
            f.setOrder(com.vttcaseiro.character.Bastion.key(com.vttcaseiro.character.Bastion.ORDERS, order.getValue()));
            changed();
        });
        TextField hirelings = textField(f::getHirelings, f::setHirelings);
        hirelings.setPromptText("nomes ou quantidade");
        TextArea notes = textArea(f::getNotes, f::setNotes, 3);
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(4);
        g.addRow(0, mutedLabel("Espaço"), space);
        g.addRow(1, mutedLabel("Ordem"), order);
        g.addRow(2, mutedLabel("Contratados"), hirelings);
        GridPane.setHgrow(hirelings, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        return card(sectionLabel("INSTALAÇÃO ESPECIAL"), name, g, mutedLabel("Anotações"), notes);
    }

    private Node buildEquipmentBox() {
        ListView<InventoryItem> list = new ListView<>();
        list.setPrefHeight(160);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getQuantity() + "x " + item.getName());
            }
        });
        Runnable reload = () -> list.getItems().setAll(sheet.getInventory().getItems());
        reload.run();

        TextField itemName = new TextField();
        itemName.setPromptText("Item");
        TextField qty = new TextField("1");
        qty.setPrefColumnCount(3);
        Button add = new Button("Adicionar");
        Runnable addItem = () -> {
            Integer q = parseInt(qty.getText());
            if (itemName.getText().isBlank() || q == null || q <= 0) return;
            sheet.getInventory().addItem(itemName.getText().trim(), q, 0, 0);
            itemName.clear();
            qty.setText("1");
            reload.run();
            changed();
        };
        add.setOnAction(e -> addItem.run());
        itemName.setOnAction(e -> addItem.run());
        Button remove = new Button("Remover 1");
        remove.setOnAction(e -> {
            InventoryItem selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sheet.getInventory().removeItem(selected.getName(), 1);
                reload.run();
                changed();
            }
        });

        HBox coins = new HBox(6);
        coins.setAlignment(Pos.CENTER_LEFT);
        for (Currency c : Currency.values()) {
            TextField field = intField(() -> sheet.getInventory().getMoney().get(c), v -> {
                var money = sheet.getInventory().getMoney();
                int diff = Math.max(0, v) - money.get(c);
                if (diff > 0) money.add(c, diff); else if (diff < 0) money.subtract(c, -diff);
            }, 4);
            coins.getChildren().addAll(new Label(currencyAbbreviation(c)), field);
        }
        Button fromCompendium = new Button("Do compêndio...");
        fromCompendium.setOnAction(e -> addFromCompendium(CompendiumEntryType.ITEM));
        return card(sectionLabel("EQUIPAMENTO"), list, row(itemName, qty, add, remove, fromCompendium),
                sectionLabel("MOEDAS"), coins);
    }

    // ==================================================================
    // Compendio (5e.tools) -> ficha
    // ==================================================================

    private Node classButton() {
        Button b = new Button("Aplicar classe do compêndio...");
        b.setOnAction(e -> onApplyClass());
        return b;
    }

    private void addFromCompendium(CompendiumEntryType type) {
        String title = switch (type) {
            case SPELL -> "Adicionar magias";
            case FEAT -> "Adicionar talentos";
            case SPECIES -> "Escolher espécie";
            case BACKGROUND -> "Escolher antecedente";
            case ITEM -> "Adicionar itens";
            default -> "Compêndio";
        };
        List<CompendiumEntry> picked = CompendiumPickerDialog.pick(stage, actions.compendium(), type, title + " - " + sheet.getName());
        if (picked.isEmpty()) return;
        List<String> done = new ArrayList<>();
        for (CompendiumEntry entry : picked) {
            done.add(CompendiumToSheet.addEntry(sheet, entry, actions.compendium()));
        }
        actions.chat(sheet, sheet.getName() + ": " + String.join(" | ", done));
        changed();
        flushSave(); // grava antes de remontar (reload zera o "pendente")
        reload(null);
    }

    /**
     * Aplica uma classe importada (class-*.json do 5e.tools): dado de vida,
     * salvaguardas, armaduras, armas, conjuracao, pericias escolhidas e as
     * caracteristicas ate o nivel atual. Rodar de novo depois de subir de
     * nivel so acrescenta as caracteristicas novas.
     */
    private void onApplyClass() {
        List<DndClass> classes = actions.classes();
        if (classes.isEmpty()) {
            Dialog<ButtonType> d = new Dialog<>();
            d.initOwner(stage);
            d.setTitle("Nenhuma classe importada");
            Label msg = new Label("Importe um class-*.json do 5e.tools na janela principal (aba Fichas, "
                    + "\"Importar classes\") e tente de novo.");
            msg.setWrapText(true);
            d.getDialogPane().setContent(msg);
            d.getDialogPane().getButtonTypes().add(ButtonType.OK);
            d.showAndWait();
            return;
        }
        ComboBox<DndClass> classBox = new ComboBox<>();
        classBox.getItems().addAll(classes);
        classes.stream().filter(c -> c.getClassName().equalsIgnoreCase(orEmpty(sheet.getClassName())))
                .findFirst().ifPresentOrElse(classBox::setValue, () -> classBox.setValue(classes.get(0)));
        ComboBox<Object> subclassBox = new ComboBox<>();
        FlowPane skillsPane = new FlowPane(8, 4);
        Label skillsLabel = new Label();
        List<CheckBox> skillChecks = new ArrayList<>();
        Runnable fillForClass = () -> {
            DndClass c = classBox.getValue();
            subclassBox.getItems().setAll("(nenhuma)");
            if (c != null) subclassBox.getItems().addAll(c.getSubclasses());
            subclassBox.setValue("(nenhuma)");
            if (c != null) {
                for (Subclass sc : c.getSubclasses()) {
                    if (sc.getName().equalsIgnoreCase(orEmpty(sheet.getSubclass()))) subclassBox.setValue(sc);
                }
            }
            subclassBox.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Object o) { return o instanceof Subclass sc ? sc.getName() : String.valueOf(o); }
                @Override public Object fromString(String text) { return text; }
            });
            skillsPane.getChildren().clear();
            skillChecks.clear();
            if (c != null) {
                skillsLabel.setText("Perícias da classe (escolha " + c.getSkillChoiceCount() + "):");
                for (Skill sk : CompendiumToSheet.skillChoices(c)) {
                    CheckBox cb = new CheckBox(sk.getDisplayNamePt());
                    cb.setUserData(sk);
                    cb.setSelected(sheet.isSkillProficient(sk));
                    skillChecks.add(cb);
                    skillsPane.getChildren().add(cb);
                }
            }
        };
        classBox.valueProperty().addListener((o, a, b) -> fillForClass.run());
        fillForClass.run();
        Label info = mutedLabel("Usa o nível atual da ficha (" + sheet.getLevel() + "). Subiu de nível? Aplique de novo: "
                + "só as características novas entram.");
        info.setWrapText(true);
        boolean ok = formDialog("Aplicar classe", List.of(labeled("Classe", classBox), labeled("Subclasse", subclassBox),
                skillsLabel, skillsPane, info));
        if (!ok || classBox.getValue() == null) return;
        Subclass subclass = subclassBox.getValue() instanceof Subclass sc ? sc : null;
        List<Skill> chosen = new ArrayList<>();
        for (CheckBox cb : skillChecks) if (cb.isSelected()) chosen.add((Skill) cb.getUserData());
        var result = CompendiumToSheet.applyClass(sheet, classBox.getValue(), subclass, chosen);
        actions.chat(sheet, sheet.getName() + " agora e " + classBox.getValue().getClassName()
                + (subclass == null ? "" : " (" + subclass.getName() + ")") + " nível " + sheet.getLevel()
                + ": " + result.summary() + ".");
        changed();
        flushSave(); // grava antes de remontar (reload zera o "pendente")
        reload(null);
    }

    // ==================================================================
    // Retrato (mesma logica de antes: clique troca, arrastar cria token)
    // ==================================================================

    private void rebuildPortrait() {
        portraitBox.getChildren().setAll(buildPortrait());
        if (imageAssetService != null) {
            Button galleryButton = new Button("Galeria");
            galleryButton.getStyleClass().add("button-flat");
            galleryButton.setOnAction(e -> new ImageGalleryView(imageAssetService, assetId -> {
                sheet.setPortraitImageAssetId(assetId);
                rebuildPortrait();
                changed();
            }).show());
            portraitBox.getChildren().add(galleryButton);
        }
        Label dragHint = new Label("arraste p/ o mapa");
        dragHint.getStyleClass().add("label-muted");
        dragHint.setStyle("-fx-font-size: 11px;");
        portraitBox.getChildren().add(dragHint);
    }

    private Node buildPortrait() {
        Node visual;
        Image image = tryLoadPortraitImage();
        if (image != null) {
            ImageView iv = new ImageView(image);
            iv.setFitWidth(96);
            iv.setFitHeight(96);
            visual = iv;
        } else {
            visual = SilhouetteIcon.create(96);
        }
        visual.setCursor(javafx.scene.Cursor.HAND);
        visual.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) onPortraitClicked();
        });
        visual.setOnDragDetected(e -> {
            Dragboard db = visual.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(sheet.getId());
            content.put(CHARACTER_ID_FORMAT, sheet.getId());
            db.setContent(content);
            e.consume();
        });
        return visual;
    }

    private void onPortraitClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher retrato de " + sheet.getName());
        chooser.getExtensionFilters().add(com.vttcaseiro.view.ImageFiles.filter());
        File file = ImagePicker.open(chooser, stage);
        if (file == null) return;
        sheet.setPortraitImagePath(file.getAbsolutePath());
        if (imageAssetService != null) {
            try {
                sheet.setPortraitImageAssetId(imageAssetService.importFile(file.toPath()));
            } catch (java.io.IOException ignored) {
                // continua funcionando pelo portraitImagePath direto
            }
        }
        rebuildPortrait();
        changed();
    }

    private Image tryLoadPortraitImage() {
        if (imageAssetService != null && sheet.getPortraitImageAssetId() != null) {
            try {
                byte[] bytes = imageAssetService.readBytes(sheet.getPortraitImageAssetId());
                Image fromAsset = new Image(new java.io.ByteArrayInputStream(bytes), 96, 96, false, true);
                if (!fromAsset.isError()) return fromAsset;
            } catch (java.io.IOException ignored) {
                // asset ainda nao existe localmente -- cai pro path abaixo.
            }
        }
        String path = sheet.getPortraitImagePath();
        if (path == null || path.isBlank()) return null;
        try {
            Image candidate = new Image(new File(path).toURI().toString(), 96, 96, false, true);
            return candidate.isError() ? null : candidate;
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================================================================
    // Pequenos construtores de layout
    // ==================================================================

    private static Tab tab(String title, Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(10));
        return new Tab(title, scroll);
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(6, children);
        box.setStyle(CARD_STYLE);
        return box;
    }

    private static Label wrapped(Label label) {
        label.setWrapText(true);
        label.setMaxWidth(320);
        return label;
    }

    private static HBox row(Node... children) {
        HBox box = new HBox(6, children);
        box.setAlignment(Pos.CENTER_LEFT);
        // rotulos curtos nunca encolhem pra "..." (quem cede espaco sao os campos)
        for (Node n : children) {
            if (n instanceof Label l && !l.isWrapText()) l.setMinWidth(Region.USE_PREF_SIZE);
        }
        return box;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-section");
        return label;
    }

    private static Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-muted");
        return label;
    }

    private static Label boldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static Node bigNumber(Label label) {
        label.setStyle(BIG_NUMBER_STYLE);
        return label;
    }

    private static Node bigStat(String title, Label value) {
        value.setStyle(BIG_NUMBER_STYLE);
        HBox box = new HBox(8, mutedLabel(title), value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox labeled(String label, Node field) {
        Label l = new Label(label);
        l.setMinWidth(Region.USE_PREF_SIZE);
        HBox box = new HBox(8, l, field);
        box.setAlignment(Pos.CENTER_LEFT);
        if (field instanceof Region region) HBox.setHgrow(region, Priority.ALWAYS);
        return box;
    }

    private static void addLabeled(GridPane grid, int col, int row, String label, Node field) {
        grid.add(mutedLabel(label), col, row);
        grid.add(field, col, row + 1);
    }

    private static <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> getter, double width) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(orEmpty(getter.apply(data.getValue()))));
        col.setPrefWidth(width);
        col.setSortable(false);
        return col;
    }

    /** Dialogo simples de formulario (OK/Cancelar). true se confirmou. */
    private boolean formDialog(String title, List<Node> rows) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title + " - " + sheet.getName());
        dialog.initOwner(stage);
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(rows);
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /** Troca o texto de um campo so se for diferente (evita disparar o
     * listener dele a toa e mover o cursor de quem esta digitando). */
    private static void syncText(TextInputControl field, String value) {
        if (!field.isFocused() && !value.equals(field.getText())) field.setText(value);
    }

    // ==================================================================
    // Utilitarios de texto
    // ==================================================================

    private static String abilityNamePt(Ability a) {
        return switch (a) {
            case STRENGTH -> "Força";
            case DEXTERITY -> "Destreza";
            case CONSTITUTION -> "Constituição";
            case INTELLIGENCE -> "Inteligência";
            case WISDOM -> "Sabedoria";
            case CHARISMA -> "Carisma";
        };
    }

    private static String currencyAbbreviation(Currency c) {
        return switch (c) {
            case COPPER -> "PC";
            case SILVER -> "PP";
            case ELECTRUM -> "PE";
            case GOLD -> "PO";
            case PLATINUM -> "PL";
        };
    }

    static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static Integer parseInt(String text) {
        if (text == null) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

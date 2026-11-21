package com.vttcaseiro.view;

import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.CompendiumEntry;
import com.vttcaseiro.compendium.CompendiumEntryType;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Busca no compendio da mesa (conteudo importado do 5e.tools) com filtro de
 * tipo e nivel de magia, previa do texto e selecao multipla. Devolve as
 * entradas escolhidas (lista vazia se cancelar).
 */
public final class CompendiumPickerDialog {

    private CompendiumPickerDialog() {
    }

    public static List<CompendiumEntry> pick(Window owner, Compendium compendium, CompendiumEntryType type, String title) {
        if (compendium == null || compendium.getEntries().isEmpty()) {
            Dialog<ButtonType> empty = new Dialog<>();
            empty.initOwner(owner);
            empty.setTitle(title);
            Label msg = new Label("O compêndio desta mesa está vazio.\n"
                    + "Na janela principal, aba Fichas, use \"Carregar conteúdo 2024 pronto\" "
                    + "ou importe arquivos do 5e.tools.");
            msg.setWrapText(true);
            empty.getDialogPane().setContent(msg);
            empty.getDialogPane().getButtonTypes().add(ButtonType.OK);
            empty.showAndWait();
            return List.of();
        }

        TextField search = new TextField();
        search.setPromptText("Buscar por nome...");
        ComboBox<String> levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("Todos os níveis", "Truques", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        levelFilter.setValue("Todos os níveis");
        levelFilter.setVisible(type == CompendiumEntryType.SPELL);
        levelFilter.setManaged(type == CompendiumEntryType.SPELL);

        ListView<CompendiumEntry> list = new ListView<>();
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPrefHeight(320);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CompendiumEntry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText(null);
                    return;
                }
                String extra = "";
                if (e.getType() == CompendiumEntryType.SPELL && e.getSpellLevel() != null) {
                    extra = e.getSpellLevel() == 0 ? "  - truque" : "  - nível " + e.getSpellLevel();
                } else if (e.getType() == CompendiumEntryType.ITEM && e.getItemRarity() != null && !e.getItemRarity().isBlank()) {
                    extra = "  - " + e.getItemRarity();
                }
                setText(e.getName() + extra + (e.getSource() == null || e.getSource().isBlank() ? "" : "  [" + e.getSource() + "]"));
            }
        });
        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefWidth(380);

        Runnable refresh = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String lvl = levelFilter.getValue();
            List<CompendiumEntry> result = new ArrayList<>();
            for (CompendiumEntry e : compendium.byType(type)) {
                if (!q.isEmpty() && !e.getName().toLowerCase(Locale.ROOT).contains(q)) continue;
                if (type == CompendiumEntryType.SPELL && lvl != null && !lvl.startsWith("Todos")) {
                    int wanted = lvl.equals("Truques") ? 0 : Integer.parseInt(lvl);
                    if (e.getSpellLevel() == null || e.getSpellLevel() != wanted) continue;
                }
                result.add(e);
            }
            result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            list.getItems().setAll(result);
        };
        search.textProperty().addListener((o, a, b) -> refresh.run());
        levelFilter.valueProperty().addListener((o, a, b) -> refresh.run());
        list.getSelectionModel().selectedItemProperty().addListener((o, a, e) ->
                preview.setText(e == null ? "" : e.toChatText()));
        refresh.run();

        HBox filters = new HBox(8, search, levelFilter);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox body = new HBox(10, list, preview);
        HBox.setHgrow(list, Priority.ALWAYS);
        Label hint = new Label("Ctrl/Shift + clique seleciona varios.");
        hint.getStyleClass().add("label-muted");
        VBox content = new VBox(8, filters, body, hint);
        content.setPadding(new Insets(10));
        content.setPrefWidth(820);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // tema: o Theme poe na janela (folha no proprio DialogPane venceria o tema escolhido)
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(ButtonType.OK);
                dialog.close();
            }
        });
        boolean ok = dialog.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
        return ok ? new ArrayList<>(list.getSelectionModel().getSelectedItems()) : List.of();
    }
}

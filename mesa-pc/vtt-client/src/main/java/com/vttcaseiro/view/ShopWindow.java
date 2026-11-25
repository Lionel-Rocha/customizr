package com.vttcaseiro.view;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.inventory.InventoryItem;
import com.vttcaseiro.model.Shop;
import com.vttcaseiro.service.ShopService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * Lojas: o mestre cria lojas e itens (preco e estoque) e abre pros jogadores; o jogador escolhe a ficha, compra e vende.
 * Quem aplica a compra (dinheiro, inventario, estoque) e o programa do mestre (ver Main).
 */
public class ShopWindow {

    /** O que a janela pede pro resto do programa. */
    public interface Actions {
        /** Mestre: todas as lojas; jogador: as abertas. */
        List<Shop> shops();

        boolean isGm();

        /** Fichas de quem esta usando (mestre: todas as do grupo). */
        List<CharacterSheet> sheets();

        /** Mestre mudou as lojas (salvar e mandar as abertas). */
        void shopsChanged(List<Shop> shops);

        void buy(Shop shop, Shop.Item item, int qty, CharacterSheet sheet);

        void sell(Shop shop, InventoryItem item, int qty, CharacterSheet sheet);
    }

    private final Actions actions;
    private final Stage stage = new Stage();
    private final ListView<Shop> shopList = new ListView<>();
    private final ListView<Shop.Item> itemList = new ListView<>();
    private final ListView<InventoryItem> sellList = new ListView<>();
    private final ComboBox<CharacterSheet> sheetBox = new ComboBox<>();
    private final Label money = new Label();
    private final Label shopTitle = new Label();
    private final Label shopDesc = new Label();
    private final CheckBox openBox = new CheckBox("Aberta pros jogadores");
    private final Spinner<Integer> qty = new Spinner<>(1, 999, 1);

    public ShopWindow(Window owner, Actions actions) {
        this.actions = actions;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Lojas");
        Icons.applyTo(stage);

        shopList.setPrefWidth(200);
        shopList.getStyleClass().add("boxed-list");
        shopList.setPlaceholder(EmptyHint.of(actions.isGm() ? "Nenhuma loja.\nUse + Nova loja." : "Nenhuma loja aberta\npelo mestre."));
        shopList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Shop s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName() + (actions.isGm() && !s.isOpen() ? "  (fechada)" : ""));
            }
        });
        shopList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showShop());
        itemList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Shop.Item it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getName() + "  ·  " + ShopService.coins(it.getPriceCopper())
                        + (it.getStock() < 0 ? "" : "  ·  " + it.getStock() + " no estoque")
                        + (it.getDescription().isBlank() ? "" : "\n   " + it.getDescription()));
            }
        });
        itemList.getStyleClass().add("boxed-list");
        itemList.setPlaceholder(EmptyHint.of("Sem itens nesta loja."));
        sellList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryItem it, boolean empty) {
                super.updateItem(it, empty);
                Shop s = shopList.getSelectionModel().getSelectedItem();
                setText(empty || it == null ? null : it.getName() + " x" + it.getQuantity()
                        + (s == null ? "" : "  ·  a loja paga " + ShopService.coins(ShopService.sellPrice(s, it))));
            }
        });
        sellList.getStyleClass().add("boxed-list");
        sellList.setPlaceholder(EmptyHint.of("Nada no inventário."));
        sellList.setPrefHeight(150);
        sheetBox.setCellFactory(lv -> sheetCell());
        sheetBox.setButtonCell(sheetCell());
        sheetBox.setOnAction(e -> refreshSheet());
        qty.setPrefWidth(80);
        qty.setEditable(true);

        Button buy = new Button("Comprar", Icons.icon("add", 14));
        buy.getStyleClass().add("button-primary");
        buy.setOnAction(e -> {
            Shop s = shopList.getSelectionModel().getSelectedItem();
            Shop.Item it = itemList.getSelectionModel().getSelectedItem();
            CharacterSheet sh = sheetBox.getValue();
            if (s != null && it != null && sh != null) actions.buy(s, it, qty.getValue(), sh);
        });
        Button sell = new Button("Vender o escolhido", Icons.icon("send", 14));
        sell.setOnAction(e -> {
            Shop s = shopList.getSelectionModel().getSelectedItem();
            InventoryItem it = sellList.getSelectionModel().getSelectedItem();
            CharacterSheet sh = sheetBox.getValue();
            if (s != null && it != null && sh != null) actions.sell(s, it, Math.min(qty.getValue(), it.getQuantity()), sh);
        });

        shopTitle.getStyleClass().add("label-title");
        shopDesc.getStyleClass().add("label-muted");
        shopDesc.setWrapText(true);
        HBox who = new HBox(8, new Label("Ficha:"), sheetBox, money);
        who.setAlignment(Pos.CENTER_LEFT);
        HBox buyRow = new HBox(8, new Label("Quantidade"), qty, buy);
        buyRow.setAlignment(Pos.CENTER_LEFT);
        Label sellTitle = new Label("Vender (a loja paga parte do preço)");
        sellTitle.getStyleClass().add("label-section");
        VBox right = new VBox(8, shopTitle, shopDesc, who, itemList, buyRow, sellTitle, sellList, sell);
        rightPane = right;
        VBox.setVgrow(itemList, Priority.ALWAYS);
        right.setPadding(new Insets(10));
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox left = new VBox(8, shopList);
        VBox.setVgrow(shopList, Priority.ALWAYS);
        left.setPadding(new Insets(10));
        if (actions.isGm()) {
            Button newShop = new Button("Nova loja", Icons.icon("add", 14));
            newShop.setOnAction(e -> newShop());
            Button addItem = new Button("Adicionar item...", Icons.icon("add", 14));
            addItem.setOnAction(e -> editItem(null));
            Button editItem = new Button("Editar item...", Icons.icon("edit", 14));
            editItem.setOnAction(e -> { if (itemList.getSelectionModel().getSelectedItem() != null) editItem(itemList.getSelectionModel().getSelectedItem()); });
            Button delItem = new Button("Tirar item", Icons.icon("trash", 14));
            delItem.setOnAction(e -> {
                Shop s = shopList.getSelectionModel().getSelectedItem();
                Shop.Item it = itemList.getSelectionModel().getSelectedItem();
                if (s != null && it != null) {
                    s.getItems().remove(it);
                    changed();
                }
            });
            Button rename = new Button("Renomear...", Icons.icon("edit", 14));
            rename.setOnAction(e -> renameShop());
            Button delShop = new Button("Apagar loja", Icons.icon("trash", 14));
            delShop.setOnAction(e -> {
                Shop s = shopList.getSelectionModel().getSelectedItem();
                if (s == null) return;
                List<Shop> all = actions.shops();
                all.removeIf(x -> x.getId().equals(s.getId()));
                actions.shopsChanged(all);
                refresh();
            });
            openBox.setOnAction(e -> {
                Shop s = shopList.getSelectionModel().getSelectedItem();
                if (s != null) {
                    s.setOpen(openBox.isSelected());
                    changed();
                }
            });
            itemList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && itemList.getSelectionModel().getSelectedItem() != null) {
                    editItem(itemList.getSelectionModel().getSelectedItem());
                }
            });
            left.getChildren().addAll(newShop, rename, delShop);
            right.getChildren().add(1, openBox);
            HBox gmItems = new HBox(6, addItem, editItem, delItem);
            right.getChildren().add(right.getChildren().indexOf(buyRow) + 1, gmItems);
        }
        HBox root = new HBox(left, right);
        Scene sc = new Scene(root, 820, 600);
        stage.setScene(sc);
        refresh();
    }

    private ListCell<CharacterSheet> sheetCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CharacterSheet s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
            }
        };
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    /** Recarrega tudo (lojas, fichas e dinheiro), mantendo o que estava escolhido. */
    public void refresh() {
        String selShop = shopList.getSelectionModel().getSelectedItem() == null ? null : shopList.getSelectionModel().getSelectedItem().getId();
        shopList.getItems().setAll(actions.shops());
        if (shopList.getItems().isEmpty()) {
            showShop();
        } else {
            Shop again = shopList.getItems().stream().filter(s -> s.getId().equals(selShop)).findFirst().orElse(shopList.getItems().get(0));
            shopList.getSelectionModel().select(again);
        }
        String selSheet = sheetBox.getValue() == null ? null : sheetBox.getValue().getId();
        sheetBox.getItems().setAll(actions.sheets());
        sheetBox.getItems().stream().filter(s -> s.getId().equals(selSheet)).findFirst()
                .ifPresentOrElse(sheetBox::setValue, () -> { if (!sheetBox.getItems().isEmpty()) sheetBox.setValue(sheetBox.getItems().get(0)); });
        showShop();
        refreshSheet();
    }

    /** Lado direito (comprar/vender): apagado ate escolher uma loja. */
    private VBox rightPane;

    private void showShop() {
        Shop s = shopList.getSelectionModel().getSelectedItem();
        shopTitle.setText(s != null ? s.getName() : shopList.getItems().isEmpty()
                ? (actions.isGm() ? "Crie uma loja (+ Nova loja)" : "Nenhuma loja aberta") : "Escolha uma loja na lista");
        if (rightPane != null) for (javafx.scene.Node n : rightPane.getChildren()) if (n != shopTitle) n.setDisable(s == null);
        shopDesc.setText(s == null ? "" : s.getDescription());
        openBox.setSelected(s != null && s.isOpen());
        itemList.getItems().setAll(s == null ? List.of() : s.getItems());
        sellList.refresh();
    }

    private void refreshSheet() {
        CharacterSheet sh = sheetBox.getValue();
        long cp = sh == null ? 0 : sh.getInventory().getMoney().totalInCopper();
        money.setText(sh == null ? "(sem ficha)" : "Bolsa: " + (cp <= 0 ? "vazia" : ShopService.coins(cp)));
        sellList.getItems().setAll(sh == null ? List.of() : sh.getInventory().getItems());
    }

    private void changed() {
        actions.shopsChanged(actions.shops().stream()
                .map(x -> x.getId().equals(selectedId()) ? shopList.getSelectionModel().getSelectedItem() : x).toList());
        refresh();
    }

    private String selectedId() {
        Shop s = shopList.getSelectionModel().getSelectedItem();
        return s == null ? null : s.getId();
    }

    private void newShop() {
        javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog("Ferreiro");
        d.initOwner(stage);
        d.setHeaderText(null);
        d.setContentText("Nome da loja:");
        d.showAndWait().filter(n -> !n.isBlank()).ifPresent(n -> {
            List<Shop> all = new java.util.ArrayList<>(actions.shops());
            Shop s = new Shop(n);
            all.add(s);
            actions.shopsChanged(all);
            refresh();
            shopList.getItems().stream().filter(x -> x.getId().equals(s.getId())).findFirst().ifPresent(x -> shopList.getSelectionModel().select(x));
        });
    }

    private void renameShop() {
        Shop s = shopList.getSelectionModel().getSelectedItem();
        if (s == null) return;
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Loja");
        TextField name = new TextField(s.getName());
        TextField desc = new TextField(s.getDescription());
        desc.setPromptText("Ex.: Bruna, anã ranzinza; aceita troca");
        Spinner<Integer> rate = new Spinner<>(0, 100, (int) Math.round(s.getBuyBackRate() * 100), 5);
        rate.setEditable(true);
        VBox box = new VBox(8, new Label("Nome"), name, new Label("Descrição"), desc,
                new Label("Quanto paga quando o jogador vende (% do preço)"), rate);
        box.setPadding(new Insets(10));
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            s.setName(name.getText());
            s.setDescription(desc.getText());
            s.setBuyBackRate(Math.max(1, rate.getValue()) / 100.0);
            changed();
        });
    }

    private void editItem(Shop.Item existing) {
        Shop s = shopList.getSelectionModel().getSelectedItem();
        if (s == null) return;
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle(existing == null ? "Novo item" : "Editar item");
        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("Rope, Hempen (50 feet)");
        TextField price = new TextField(existing == null ? "" : String.valueOf(existing.getPriceCopper() / 100.0).replace('.', ','));
        price.setPromptText("em po (ex.: 1 ou 0,5)");
        TextField stock = new TextField(existing == null || existing.getStock() < 0 ? "" : String.valueOf(existing.getStock()));
        stock.setPromptText("vazio = sem fim");
        TextField desc = new TextField(existing == null ? "" : existing.getDescription());
        VBox box = new VBox(8, new Label("Nome"), name, new Label("Preço (po)"), price, new Label("Estoque"), stock,
                new Label("Descrição (opcional)"), desc);
        box.setPadding(new Insets(10));
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).ifPresent(b -> {
            if (name.getText().isBlank()) return;
            Shop.Item it = existing != null ? existing : new Shop.Item();
            it.setName(name.getText());
            it.setPriceCopper(ShopService.goldToCopper(price.getText()));
            int st;
            try {
                st = stock.getText().isBlank() ? -1 : Integer.parseInt(stock.getText().strip());
            } catch (NumberFormatException ex) {
                st = -1;
            }
            it.setStock(st);
            it.setDescription(desc.getText());
            if (existing == null) s.getItems().add(it);
            changed();
        });
    }
}

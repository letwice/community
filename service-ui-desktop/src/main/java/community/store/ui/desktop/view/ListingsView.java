package community.store.ui.desktop.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

public class ListingsView implements RefreshableView {

    private final ApiClient api;
    private final TableView<JsonNode> table = Ui.table();
    private final Label sellerStatus = new Label();
    private final Button add = new Button("New listing");
    private final VBox root;

    public ListingsView(ApiClient api) {
        this.api = api;
        table.getColumns().add(Ui.column("Title", "title", 240));
        table.getColumns().add(Ui.column("Category", "category", 120));
        table.getColumns().add(Ui.column("Condition", "condition", 100));
        table.getColumns().add(Ui.column("Price", n -> Ui.money(n.path("price")), 90));
        table.getColumns().add(Ui.column("Stock", "stockQuantity", 60));
        table.getColumns().add(Ui.column("Status", "status", 90));

        add.setOnAction(e -> edit(null));
        add.setDisable(true);
        sellerStatus.setWrapText(true);
        Button edit = new Button("Edit");
        edit.setOnAction(e -> selected().ifPresentOrElse(this::edit, () -> Ui.info("Select a listing first.")));
        Button remove = new Button("Remove");
        remove.setOnAction(e -> selected().ifPresentOrElse(p -> {
            if (Ui.confirm("Remove \"" + Ui.text(p, "title") + "\" from the store?")) {
                Ui.async(() -> api.delete(api.productUrl + "/frontend/products/" + Ui.text(p, "guid")), r -> refresh());
            }
        }, () -> Ui.info("Select a listing first.")));
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(table, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("My listings"),
                sellerStatus,
                new HBox(8, add, edit, remove, reload), table);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.productUrl + "/frontend/products?mine=true&size=100"),
                page -> table.getItems().setAll(Views.content(page)));
        Ui.async(() -> new SellerReadiness(api.get(api.userUrl + "/frontend/users/me"), Views.hasBankingDetails(api)), this::showReadiness);
    }

    /**
     * Listing is gated (and enforced server-side) on banking details being on file and the account being verified.
     */
    private void showReadiness(SellerReadiness readiness) {
        sellerStatus.getStyleClass().removeAll("simulated", "notice");
        String verification = Ui.text(readiness.me(), "verificationStatus");
        if (!readiness.hasBankingDetails()) {
            sellerStatus.setText("Add your banking details (Banking Details tab) before you can list items for sale.");
            sellerStatus.getStyleClass().add("simulated");
        } else if (!"VERIFIED".equals(verification)) {
            sellerStatus.setText("Banking details on file. Your account is " + verification
                    + " — an admin must verify it before you can list items.");
            sellerStatus.getStyleClass().add("notice");
        } else {
            sellerStatus.setText("Verified, with banking details on file — you can list items.");
        }
        add.setDisable(!readiness.hasBankingDetails() || !"VERIFIED".equals(verification));
    }

    private record SellerReadiness(JsonNode me, boolean hasBankingDetails) {
    }

    private java.util.Optional<JsonNode> selected() {
        return java.util.Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void edit(JsonNode existing) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New listing" : "Edit listing");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField title = new TextField(existing == null ? "" : Ui.text(existing, "title"));
        TextArea description = new TextArea(existing == null ? "" : Ui.text(existing, "description"));
        description.setPrefRowCount(3);
        TextField price = new TextField(existing == null ? "" : Ui.text(existing, "price"));
        ComboBox<String> condition = new ComboBox<>();
        condition.getItems().addAll("NEW", "SECOND_HAND");
        condition.setValue(existing == null ? "NEW" : Ui.text(existing, "condition"));
        TextField category = new TextField(existing == null ? "" : Ui.text(existing, "category"));
        TextField stock = new TextField(existing == null ? "1" : Ui.text(existing, "stockQuantity"));
        ComboBox<String> status = new ComboBox<>();
        status.getItems().addAll("ACTIVE", "DRAFT");
        status.setValue(existing == null || !"DRAFT".equals(Ui.text(existing, "status")) ? "ACTIVE" : "DRAFT");
        List<String> existingImages = new ArrayList<>();
        if (existing != null) {
            existing.path("imageUrls").forEach(u -> existingImages.add(u.asText()));
        }
        TextArea images = new TextArea(String.join("\n", existingImages));
        images.setPromptText("One image URL per line (optional)");
        images.setPrefRowCount(2);

        GridPane grid = LoginView.form();
        grid.addRow(0, new Label("Title"), title);
        grid.addRow(1, new Label("Description"), description);
        grid.addRow(2, new Label("Price (R)"), price);
        grid.addRow(3, new Label("Condition"), condition);
        grid.addRow(4, new Label("Category"), category);
        grid.addRow(5, new Label("Stock"), stock);
        if (existing != null) {
            grid.addRow(6, new Label("Status"), status);
        }
        grid.addRow(7, new Label("Image URLs"), images);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title.getText().trim());
            body.put("description", description.getText().trim());
            body.put("price", price.getText().trim());
            body.put("condition", condition.getValue());
            body.put("category", category.getText().trim());
            body.put("stockQuantity", stock.getText().trim());
            body.put("imageUrls", Arrays.stream(images.getText().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList());
            if (existing != null) {
                body.put("status", status.getValue());
            }
            return body;
        });
        dialog.showAndWait().ifPresent(body -> Ui.async(() -> existing == null
                        ? api.post(api.productUrl + "/frontend/products", body)
                        : api.put(api.productUrl + "/frontend/products/" + Ui.text(existing, "guid"), body),
                r -> refresh()));
    }
}

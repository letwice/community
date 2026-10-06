package community.store.ui.desktop.view;

import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

public class DisputesView implements RefreshableView {

    private final ApiClient api;
    private final TableView<JsonNode> table = Ui.table();
    private final VBox root;

    public DisputesView(ApiClient api) {
        this.api = api;
        table.getColumns().add(Ui.column("Order", "escrowReferenceNumber", 110));
        table.getColumns().add(Ui.column("Product", "productTitle", 220));
        table.getColumns().add(Ui.column("Amount", n -> Ui.money(n.path("totalAmount")), 100));
        table.getColumns().add(Ui.column("Buyer", "buyerUserGuid", 260));
        table.getColumns().add(Ui.column("Vendor", "vendorUserGuid", 260));

        Button refund = new Button("Refund buyer");
        refund.setOnAction(e -> resolve("REFUND"));
        Button release = new Button("Release to vendor");
        release.setOnAction(e -> resolve("RELEASE"));
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(table, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("Admin — Disputed orders"),
                Ui.simulatedBanner("refunds and releases only update escrow status and payout rollups; no money moves."),
                new HBox(8, refund, release, reload), table);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.productUrl + "/backoffice/orders?status=DISPUTED&size=100"),
                page -> table.getItems().setAll(Views.content(page)));
    }

    private void resolve(String outcome) {
        JsonNode order = table.getSelectionModel().getSelectedItem();
        if (order == null) {
            Ui.info("Select an order first.");
            return;
        }
        if (Ui.confirm(outcome + " order " + Ui.text(order, "escrowReferenceNumber") + "?")) {
            Ui.async(() -> api.post(api.productUrl + "/backoffice/orders/" + Ui.text(order, "guid") + "/resolve",
                    Map.of("outcome", outcome)), r -> refresh());
        }
    }
}

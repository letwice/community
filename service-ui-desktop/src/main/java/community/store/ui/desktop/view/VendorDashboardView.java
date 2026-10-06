package community.store.ui.desktop.view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

/**
 * Vendor/Faculty dashboard: payout summary (service-user) plus incoming orders (service-product).
 */
public class VendorDashboardView implements RefreshableView {

    private final ApiClient api;
    private final Label totalSales = new Label();
    private final Label salesCount = new Label();
    private final Label paidOut = new Label();
    private final Label pending = new Label();
    private final TableView<JsonNode> orders = Ui.table();
    private final VBox root;

    public VendorDashboardView(ApiClient api) {
        this.api = api;
        HBox stats = new HBox(12,
                Ui.statCard("Total Sales", totalSales),
                Ui.statCard("Orders", salesCount),
                Ui.statCard("Paid Out", paidOut),
                Ui.statCard("Pending Payout", pending));

        orders.getColumns().add(Ui.column("Order", "escrowReferenceNumber", 110));
        orders.getColumns().add(Ui.column("Product", "productTitle", 220));
        orders.getColumns().add(Ui.column("Qty", "quantity", 50));
        orders.getColumns().add(Ui.column("Status", "escrowStatus", 160));
        orders.getColumns().add(Ui.column("Amount", n -> Ui.money(n.path("totalAmount")), 100));
        orders.getColumns().add(Ui.column("Ordered", n -> Ui.dateTime(n.path("createdAt")), 140));

        Button ship = new Button("Mark shipped");
        ship.setOnAction(e -> act("mark-shipped", "Mark this order as shipped?"));
        Button cancel = new Button("Cancel order");
        cancel.setOnAction(e -> act("cancel", "Cancel this unpaid order? Stock will be returned."));
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(orders, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("Vendor dashboard"), stats,
                Ui.simulatedBanner("payments are buyer self-attested EFTs into a simulated escrow; no real money moves."),
                new HBox(8, ship, cancel, reload), orders);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.userUrl + "/frontend/users/me/payout-summary"), summary -> {
            totalSales.setText(Ui.money(summary.path("totalSalesAmount")));
            salesCount.setText(Ui.text(summary, "totalSalesCount"));
            paidOut.setText(Ui.money(summary.path("totalPaidOutAmount")));
            pending.setText(Ui.money(summary.path("totalPendingPayoutAmount")));
        });
        Ui.async(() -> api.get(api.productUrl + "/frontend/orders?role=vendor&size=100"),
                page -> orders.getItems().setAll(Views.content(page)));
    }

    private void act(String action, String question) {
        JsonNode order = orders.getSelectionModel().getSelectedItem();
        if (order == null) {
            Ui.info("Select an order first.");
            return;
        }
        if (Ui.confirm(question)) {
            Ui.async(() -> api.post(api.productUrl + "/frontend/orders/" + Ui.text(order, "guid") + "/" + action, null), r -> refresh());
        }
    }
}

package community.store.ui.desktop.view;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

public class SalesSummaryView implements RefreshableView {

    private final ApiClient api;
    private final ComboBox<JsonNode> sellers = new ComboBox<>();
    private final Label totalSales = new Label("-");
    private final Label paidOut = new Label("-");
    private final Label pending = new Label("-");
    private final Label orders = new Label("-");
    private final Label listings = new Label("-");
    private final Label byStatus = new Label();
    private final VBox root;

    public SalesSummaryView(ApiClient api) {
        this.api = api;
        sellers.setPrefWidth(320);
        sellers.setConverter(new StringConverter<>() {
            @Override
            public String toString(JsonNode u) {
                return u == null ? "" : Ui.text(u, "firstName") + " " + Ui.text(u, "lastName") + " (" + Ui.text(u, "userType") + ")";
            }

            @Override
            public JsonNode fromString(String s) {
                return null;
            }
        });
        sellers.setOnAction(e -> load());
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());
        byStatus.setWrapText(true);

        root = new VBox(12, Ui.heading("Admin — Vendor sales summary"),
                new HBox(8, new Label("Seller"), sellers, reload),
                new HBox(12, Ui.statCard("Total Sales", totalSales), Ui.statCard("Paid Out", paidOut),
                        Ui.statCard("Pending Payout", pending), Ui.statCard("Orders", orders), Ui.statCard("Active Listings", listings)),
                byStatus);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> {
            List<JsonNode> all = new ArrayList<>();
            all.addAll(Views.content(api.get(api.userUrl + "/backoffice/users?role=VENDOR&size=100")));
            all.addAll(Views.content(api.get(api.userUrl + "/backoffice/users?role=FACULTY&size=100")));
            return all;
        }, list -> sellers.getItems().setAll(list));
    }

    private void load() {
        JsonNode seller = sellers.getValue();
        if (seller == null) {
            return;
        }
        Ui.async(() -> api.get(api.productUrl + "/backoffice/vendors/" + Ui.text(seller, "guid") + "/sales-summary"), s -> {
            totalSales.setText(Ui.money(s.path("totalSalesAmount")));
            paidOut.setText(Ui.money(s.path("totalPaidOutAmount")));
            pending.setText(Ui.money(s.path("totalPendingPayoutAmount")));
            orders.setText(Ui.text(s, "totalOrders"));
            listings.setText(Ui.text(s, "activeListings"));
            StringBuilder sb = new StringBuilder("Orders by status: ");
            s.path("ordersByStatus").fields().forEachRemaining(e -> sb.append(e.getKey()).append("=").append(e.getValue().asText()).append("  "));
            byStatus.setText(sb.toString());
        });
    }
}

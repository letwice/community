package community.store.ui.desktop.view;

import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

/**
 * Admin backoffice: verify or reject vendor, faculty and community-leader accounts (mocked verification).
 */
public class VerificationQueueView implements RefreshableView {

    private final ApiClient api;
    private final ComboBox<String> status = new ComboBox<>();
    private final TableView<JsonNode> table = Ui.table();
    private final VBox root;

    public VerificationQueueView(ApiClient api) {
        this.api = api;
        status.getItems().addAll("PENDING_REVIEW", "REJECTED", "VERIFIED", "UNVERIFIED");
        status.setValue("PENDING_REVIEW");
        status.setOnAction(e -> refresh());

        table.getColumns().add(Ui.column("User", n -> Ui.text(n, "firstName") + " " + Ui.text(n, "lastName"), 160));
        table.getColumns().add(Ui.column("Email", "email", 220));
        table.getColumns().add(Ui.column("Type", "userType", 140));
        table.getColumns().add(Ui.column("Business no.", "businessRegistrationNumber", 130));
        table.getColumns().add(Ui.column("Status", "verificationStatus", 130));
        table.getColumns().add(Ui.column("Registered", n -> Ui.dateTime(n.path("createdAt")), 140));

        Button verify = new Button("Verify");
        verify.setOnAction(e -> withSelected(u -> Ui.async(
                () -> api.post(api.userUrl + "/backoffice/users/" + Ui.text(u, "guid") + "/verify", null), r -> refresh())));
        Button reject = new Button("Reject");
        reject.setOnAction(e -> withSelected(u -> Ui.prompt("Reject " + Ui.text(u, "email"), "Reason").ifPresent(reason ->
                Ui.async(() -> api.post(api.userUrl + "/backoffice/users/" + Ui.text(u, "guid") + "/reject", Map.of("reason", reason)),
                        r -> refresh()))));
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(table, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("Admin — Verification queue"),
                Ui.simulatedBanner("no university or business registry is consulted; verification is a manual decision."),
                new HBox(8, new Label("Status"), status, verify, reject, reload), table);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.userUrl + "/backoffice/users?size=100&status=" + status.getValue()),
                page -> table.getItems().setAll(Views.content(page).stream().filter(u -> !Ui.text(u, "userType").isEmpty()).toList()));
    }

    private void withSelected(java.util.function.Consumer<JsonNode> action) {
        JsonNode user = table.getSelectionModel().getSelectedItem();
        if (user == null) {
            Ui.info("Select a user first.");
        } else {
            action.accept(user);
        }
    }
}

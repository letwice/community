package community.store.ui.desktop.view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import community.store.ui.desktop.api.ApiClient;

public class ProfileView implements RefreshableView {

    private final ApiClient api;
    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final Label email = new Label();
    private final Label roles = new Label();
    private final Label status = new Label();
    private final Label verification = new Label();
    private final VBox root;

    public ProfileView(ApiClient api) {
        this.api = api;
        GridPane grid = LoginView.form();
        grid.addRow(0, new Label("Email"), email);
        grid.addRow(1, new Label("First name"), firstName);
        grid.addRow(2, new Label("Last name"), lastName);
        grid.addRow(3, new Label("Roles"), roles);
        grid.addRow(4, new Label("Account status"), status);
        grid.addRow(5, new Label("Verification"), verification);
        Button save = new Button("Save");
        save.setOnAction(e -> Ui.async(() -> api.put(api.userUrl + "/frontend/users/me",
                Map.of("firstName", firstName.getText().trim(), "lastName", lastName.getText().trim())), this::show));
        grid.add(save, 1, 6);
        root = new VBox(8, Ui.heading("My profile"),
                Ui.simulatedBanner("account verification is a manual admin action; no university or business registry is consulted."),
                grid);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.userUrl + "/frontend/users/me"), this::show);
    }

    private void show(JsonNode me) {
        email.setText(Ui.text(me, "email"));
        firstName.setText(Ui.text(me, "firstName"));
        lastName.setText(Ui.text(me, "lastName"));
        StringBuilder r = new StringBuilder();
        me.path("roles").forEach(role -> r.append(r.isEmpty() ? "" : ", ").append(role.asText()));
        roles.setText(r.toString());
        status.setText(Ui.text(me, "status"));
        verification.setText(Ui.text(me, "verificationStatus"));
    }
}

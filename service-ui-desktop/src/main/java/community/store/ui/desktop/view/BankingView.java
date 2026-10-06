package community.store.ui.desktop.view;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;
import community.store.ui.desktop.api.ApiException;

public class BankingView implements RefreshableView {

    private final ApiClient api;
    private final Label current = new Label();
    private final TextField holder = new TextField();
    private final TextField bank = new TextField();
    private final TextField account = new TextField();
    private final TextField branch = new TextField();
    private final ComboBox<String> type = new ComboBox<>();
    private final VBox root;

    public BankingView(ApiClient api) {
        this.api = api;
        type.getItems().addAll("CHEQUE", "SAVINGS");
        type.setValue("CHEQUE");
        account.setPromptText("Digits only");
        GridPane grid = LoginView.form();
        grid.addRow(0, new Label("Account holder"), holder);
        grid.addRow(1, new Label("Bank"), bank);
        grid.addRow(2, new Label("Account number"), account);
        grid.addRow(3, new Label("Branch code"), branch);
        grid.addRow(4, new Label("Account type"), type);
        Button save = new Button("Save banking details");
        save.setOnAction(e -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accountHolderName", holder.getText().trim());
            body.put("bankName", bank.getText().trim());
            body.put("accountNumber", account.getText().trim());
            body.put("branchCode", branch.getText().trim());
            body.put("accountType", type.getValue());
            Ui.async(() -> api.post(api.userUrl + "/frontend/users/me/banking-details", body), saved -> {
                account.clear();
                show(saved);
                Ui.info("Banking details saved (account number is encrypted at rest).");
            });
        });
        grid.add(save, 1, 5);
        root = new VBox(12, Ui.heading("Banking details"), current, grid);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> {
            try {
                return api.get(api.userUrl + "/frontend/users/me/banking-details");
            } catch (ApiException ex) {
                if (ex.getStatus() == 404) {
                    return null;
                }
                throw ex;
            }
        }, this::show);
    }

    private void show(JsonNode detail) {
        if (detail == null) {
            current.setText("No banking details on file yet — required before you can list products.");
            return;
        }
        current.setText("On file: " + Ui.text(detail, "bankName") + " " + Ui.text(detail, "maskedAccountNumber")
                + " (" + Ui.text(detail, "accountType") + ")");
        holder.setText(Ui.text(detail, "accountHolderName"));
        bank.setText(Ui.text(detail, "bankName"));
        branch.setText(Ui.text(detail, "branchCode"));
        type.setValue(Ui.text(detail, "accountType"));
    }
}

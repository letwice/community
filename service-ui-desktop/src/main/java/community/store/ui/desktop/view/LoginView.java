package community.store.ui.desktop.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;
import community.store.ui.desktop.api.Session;

public class LoginView {

    private final ApiClient api;
    private final Consumer<Session> onLogin;

    public LoginView(ApiClient api, Consumer<Session> onLogin) {
        this.api = api;
        this.onLogin = onLogin;
    }

    public Node root() {
        Label title = new Label("Community Store");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Desktop — Vendor · Community Leader · Faculty · Admin");
        subtitle.getStyleClass().add("subtitle");

        TextField email = new TextField();
        email.setPromptText("Email");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Button login = new Button("Log in");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setOnAction(e -> Ui.async(() -> api.login(email.getText().trim(), password.getText()), onLogin));

        Hyperlink register = new Hyperlink("Create an account");
        register.setOnAction(e -> showRegisterDialog());
        Hyperlink verify = new Hyperlink("Verify email");
        verify.setOnAction(e -> showVerifyDialog(null, null));

        VBox box = new VBox(12, title, subtitle, email, password, login, new HBox(8, register, verify));
        box.setMaxWidth(340);
        box.setPadding(new Insets(32));
        box.getStyleClass().add("login-card");
        VBox root = new VBox(box);
        root.setAlignment(Pos.CENTER);
        return root;
    }

    private void showRegisterDialog() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Register");
        dialog.setHeaderText("Create a Community Store account");
        ButtonType create = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("VENDOR", "COMMUNITY_LEADER", "FACULTY", "STUDENT");
        type.getSelectionModel().selectFirst();
        TextField first = new TextField();
        TextField last = new TextField();
        TextField email = new TextField();
        TextField confirmEmail = new TextField();
        TextField businessNumber = new TextField();
        businessNumber.setPromptText("e.g. 2019/123456/07");
        Label businessLabel = new Label("Business registration no.");
        PasswordField password = new PasswordField();
        Label emailHint = new Label();
        emailHint.getStyleClass().add("subtitle");
        Label errors = new Label();
        errors.getStyleClass().add("form-error");
        errors.setWrapText(true);
        errors.setMaxWidth(380);

        GridPane grid = form();
        grid.addRow(0, new Label("Account type"), type);
        grid.addRow(1, new Label("First name"), first);
        grid.addRow(2, new Label("Last name"), last);
        grid.addRow(3, new Label("Email"), email);
        grid.add(emailHint, 1, 4);
        grid.addRow(5, new Label("Confirm email"), confirmEmail);
        grid.addRow(6, businessLabel, businessNumber);
        grid.addRow(7, new Label("Password (8+ chars)"), password);
        grid.add(errors, 0, 8, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Runnable syncType = () -> {
            boolean vendor = "VENDOR".equals(type.getValue());
            businessLabel.setVisible(vendor);
            businessLabel.setManaged(vendor);
            businessNumber.setVisible(vendor);
            businessNumber.setManaged(vendor);
            emailHint.setText(RegistrationRules.requiresUniversityEmail(type.getValue())
                    ? "Use your @" + RegistrationRules.UNIVERSITY_DOMAIN + " address" : "Any email address");
            errors.setText("");
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        };
        type.setOnAction(e -> syncType.run());
        dialog.setOnShown(e -> syncType.run());

        // Keep the dialog open and show the problems instead of submitting an invalid form.
        dialog.getDialogPane().lookupButton(create).addEventFilter(ActionEvent.ACTION, event -> {
            List<String> problems = RegistrationRules.validate(type.getValue(), first.getText(), last.getText(),
                    email.getText(), confirmEmail.getText(), businessNumber.getText(), password.getText());
            if (!problems.isEmpty()) {
                errors.setText(String.join("\n", problems));
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != create) {
                return null;
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("firstName", first.getText().trim());
            request.put("lastName", last.getText().trim());
            request.put("email", email.getText().trim());
            request.put("password", password.getText());
            request.put("userType", type.getValue());
            if ("VENDOR".equals(type.getValue())) {
                request.put("businessNumber", businessNumber.getText().trim());
            }
            return request;
        });
        dialog.showAndWait().ifPresent(request -> Ui.async(() -> api.register(request), (JsonNode response) -> {
            String token = Ui.text(response, "simulatedVerificationToken");
            Ui.info("Account created. In this PoC the verification email is SIMULATED — the token has been pre-filled for you.");
            showVerifyDialog(token.isEmpty() ? null : token, (String) request.get("userType"));
        }));
    }

    private void showVerifyDialog(String token, String userType) {
        Ui.promptWithDefault("Verify your email (simulated)", "Verification token", token).ifPresent(value ->
                Ui.async(() -> {
                    api.verifyEmail(value);
                    return true;
                }, ok -> Ui.info(nextStepsAfterVerification(userType))));
    }

    private static String nextStepsAfterVerification(String userType) {
        if ("VENDOR".equals(userType) || "FACULTY".equals(userType)) {
            return "Email verified. Next: log in and add your banking details — you can't list items for sale until they're on file. "
                    + "An admin also needs to verify your account.";
        }
        if ("COMMUNITY_LEADER".equals(userType)) {
            return "Email verified. You can log in now; an admin must verify your account before you can post activities.";
        }
        return "Email verified. You can log in now. Vendors and faculty must add banking details before listing items.";
    }

    static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        return grid;
    }
}

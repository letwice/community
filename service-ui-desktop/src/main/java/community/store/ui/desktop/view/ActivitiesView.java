package community.store.ui.desktop.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
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

public class ActivitiesView implements RefreshableView {

    private final ApiClient api;
    private final TableView<JsonNode> table = Ui.table();
    private final VBox root;

    public ActivitiesView(ApiClient api) {
        this.api = api;
        table.getColumns().add(Ui.column("Title", "title", 240));
        table.getColumns().add(Ui.column("Date", n -> Ui.dateTime(n.path("activityDate")), 140));
        table.getColumns().add(Ui.column("Location", "location", 160));
        table.getColumns().add(Ui.column("Status", "status", 100));

        Button add = new Button("New activity");
        add.setOnAction(e -> edit(null));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> selected().ifPresentOrElse(this::edit, () -> Ui.info("Select an activity first.")));
        Button cancel = new Button("Cancel activity");
        cancel.setOnAction(e -> selected().ifPresentOrElse(a -> {
            if (Ui.confirm("Cancel \"" + Ui.text(a, "title") + "\"?")) {
                Ui.async(() -> api.delete(api.productUrl + "/frontend/activities/" + Ui.text(a, "guid")), r -> refresh());
            }
        }, () -> Ui.info("Select an activity first.")));
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(table, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("My community activities"),
                new Label("Publishing requires a VERIFIED Community Leader or Faculty account."),
                new HBox(8, add, edit, cancel, reload), table);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.productUrl + "/frontend/activities?mine=true&size=100"),
                page -> table.getItems().setAll(Views.content(page)));
    }

    private Optional<JsonNode> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void edit(JsonNode existing) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New activity" : "Edit activity");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        LocalDateTime when = existing == null ? LocalDateTime.now().plusDays(7).withHour(10).withMinute(0)
                : LocalDateTime.parse(Ui.text(existing, "activityDate"));
        TextField title = new TextField(existing == null ? "" : Ui.text(existing, "title"));
        TextArea description = new TextArea(existing == null ? "" : Ui.text(existing, "description"));
        description.setPrefRowCount(3);
        DatePicker date = new DatePicker(when.toLocalDate());
        TextField time = new TextField(when.toLocalTime().toString());
        time.setPromptText("HH:mm");
        TextField location = new TextField(existing == null ? "" : Ui.text(existing, "location"));

        GridPane grid = LoginView.form();
        grid.addRow(0, new Label("Title"), title);
        grid.addRow(1, new Label("Description"), description);
        grid.addRow(2, new Label("Date"), date);
        grid.addRow(3, new Label("Time (HH:mm)"), time);
        grid.addRow(4, new Label("Location"), location);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            LocalTime parsedTime;
            try {
                parsedTime = LocalTime.parse(time.getText().trim());
            } catch (DateTimeParseException ex) {
                Ui.error("Time must be in HH:mm format");
                return null;
            }
            LocalDate day = date.getValue();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title.getText().trim());
            body.put("description", description.getText().trim());
            body.put("activityDate", LocalDateTime.of(day, parsedTime).toString());
            body.put("location", location.getText().trim());
            return body;
        });
        dialog.showAndWait().ifPresent(body -> Ui.async(() -> existing == null
                        ? api.post(api.productUrl + "/frontend/activities", body)
                        : api.put(api.productUrl + "/frontend/activities/" + Ui.text(existing, "guid"), body),
                r -> refresh()));
    }
}

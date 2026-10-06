package community.store.ui.desktop.view;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Small helpers shared by the views: background calls, alerts, and JSON-backed table columns.
 */
public final class Ui {

    private static final NumberFormat RAND = NumberFormat.getNumberInstance(Locale.UK);

    static {
        RAND.setMinimumFractionDigits(2);
        RAND.setMaximumFractionDigits(2);
    }

    private Ui() {
    }

    /** Runs {@code work} off the FX thread, then {@code onSuccess} on it; failures show an error dialog. */
    public static <T> void async(Supplier<T> work, Consumer<T> onSuccess) {
        CompletableFuture.supplyAsync(work).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                error(cause.getMessage());
            } else {
                onSuccess.accept(result);
            }
        }));
    }

    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Something went wrong");
        alert.showAndWait();
    }

    public static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    public static Optional<String> prompt(String header, String label) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(header);
        dialog.setContentText(label);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    public static Optional<String> promptWithDefault(String header, String label, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue == null ? "" : defaultValue);
        dialog.setHeaderText(header);
        dialog.setContentText(label);
        dialog.getEditor().setPrefColumnCount(40);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    public static TableColumn<JsonNode, String> column(String title, Function<JsonNode, String> value, double width) {
        TableColumn<JsonNode, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    public static TableColumn<JsonNode, String> column(String title, String field, double width) {
        return column(title, n -> text(n, field), width);
    }

    public static TableView<JsonNode> table() {
        TableView<JsonNode> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nothing to show yet"));
        return table;
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    public static String money(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "R0.00";
        }
        return "R" + RAND.format(new BigDecimal(value.asText()));
    }

    public static String dateTime(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? "" : value.asText().replace('T', ' ').replaceAll(":\\d\\d(\\.\\d+)?$", "");
    }

    public static VBox statCard(String label, Label value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");
        VBox card = new VBox(4, caption, value);
        card.getStyleClass().add("stat-card");
        return card;
    }

    public static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("heading");
        return label;
    }

    public static Label simulatedBanner(String text) {
        Label label = new Label("SIMULATED — " + text);
        label.getStyleClass().add("simulated");
        label.setWrapText(true);
        return label;
    }
}

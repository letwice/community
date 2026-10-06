package community.store.ui.desktop.view;

import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;

import community.store.ui.desktop.api.ApiClient;

public class InboxView implements RefreshableView {

    private final ApiClient api;
    private final IntConsumer onUnreadCount;
    private final TableView<JsonNode> table = Ui.table();
    private final TextArea body = new TextArea();
    private final VBox root;

    public InboxView(ApiClient api, IntConsumer onUnreadCount) {
        this.api = api;
        this.onUnreadCount = onUnreadCount;
        table.getColumns().add(Ui.column("", n -> n.path("readFlag").asBoolean() ? "" : "●", 30));
        table.getColumns().add(Ui.column("Subject", "subject", 320));
        table.getColumns().add(Ui.column("Received", n -> Ui.dateTime(n.path("createdAt")), 140));
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("unread");
                if (!empty && item != null && !item.path("readFlag").asBoolean()) {
                    getStyleClass().add("unread");
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                return;
            }
            body.setText(Ui.text(selected, "body"));
            if (!selected.path("readFlag").asBoolean()) {
                Ui.async(() -> api.put(api.notificationUrl + "/frontend/notifications/" + Ui.text(selected, "id") + "/read", null),
                        r -> refresh());
            }
        });
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(4);
        Button reload = new Button("Refresh");
        reload.setOnAction(e -> refresh());

        VBox.setVgrow(table, Priority.ALWAYS);
        root = new VBox(12, Ui.heading("Inbox"), new HBox(8, reload), table, body);
        root.setPadding(new Insets(16));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Ui.async(() -> api.get(api.notificationUrl + "/frontend/notifications?size=50"),
                page -> table.getItems().setAll(Views.content(page)));
        Ui.async(() -> api.get(api.notificationUrl + "/frontend/notifications/unread-count"),
                count -> onUnreadCount.accept(count.path("count").asInt()));
    }
}

package community.store.ui.desktop;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import community.store.ui.desktop.api.ApiClient;
import community.store.ui.desktop.api.Session;
import community.store.ui.desktop.view.ActivitiesView;
import community.store.ui.desktop.view.BankingView;
import community.store.ui.desktop.view.DisputesView;
import community.store.ui.desktop.view.InboxView;
import community.store.ui.desktop.view.ListingsView;
import community.store.ui.desktop.view.LoginView;
import community.store.ui.desktop.view.ProfileView;
import community.store.ui.desktop.view.RefreshableView;
import community.store.ui.desktop.view.SalesSummaryView;
import community.store.ui.desktop.view.Ui;
import community.store.ui.desktop.view.VendorDashboardView;
import community.store.ui.desktop.view.VerificationQueueView;
import community.store.ui.desktop.view.Views;

/**
 * service-ui-desktop: JavaFX client for Vendor, Community Leader, Faculty and Admin. Tabs are chosen from the
 * roles in the access token (Faculty sees the union of Vendor and Community Leader).
 */
public class DesktopApp extends Application {

    private final ApiClient api = new ApiClient();
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Community Store — Desktop (PoC)");
        stage.setScene(new Scene(new Region(), 1100, 720));
        stage.getScene().getStylesheets().add(DesktopApp.class.getResource("app.css").toExternalForm());
        showLogin();
        stage.show();
    }

    private void showLogin() {
        setRoot((Parent) new LoginView(api, this::showMain).root());
    }

    private void showMain(Session session) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        List<RefreshableView> views = new ArrayList<>();

        Tab bankingTab = null;
        if (session.canSell()) {
            add(tabs, views, "Dashboard", new VendorDashboardView(api));
            add(tabs, views, "My Listings", new ListingsView(api));
            bankingTab = add(tabs, views, "Banking Details", new BankingView(api));
        }
        if (session.canOrganise()) {
            add(tabs, views, "My Activities", new ActivitiesView(api));
        }
        if (session.isAdmin()) {
            add(tabs, views, "Verification Queue", new VerificationQueueView(api));
            add(tabs, views, "Disputes", new DisputesView(api));
            add(tabs, views, "Sales Summary", new SalesSummaryView(api));
        }
        Tab inboxTab = new Tab("Inbox");
        InboxView inbox = new InboxView(api, count -> inboxTab.setText(count > 0 ? "Inbox (" + count + ")" : "Inbox"));
        inboxTab.setContent(inbox.root());
        tabs.getTabs().add(inboxTab);
        views.add(inbox);
        add(tabs, views, "Profile", new ProfileView(api));

        if (!session.canSell() && !session.canOrganise() && !session.isAdmin()) {
            Label hint = new Label("Students use the mobile web app (http://localhost:9700) to browse and buy.");
            hint.setPadding(new Insets(8, 16, 0, 16));
            tabs.getTabs().add(0, new Tab("Welcome", hint));
        }

        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, old, index) -> {
            Tab tab = tabs.getTabs().get(index.intValue());
            views.stream().filter(v -> v.root() == tab.getContent()).findFirst().ifPresent(RefreshableView::refresh);
        });
        views.forEach(RefreshableView::refresh);
        if (bankingTab != null) {
            // New sellers land on Banking Details: they can't list anything until it is on file.
            Tab banking = bankingTab;
            Ui.async(() -> Views.hasBankingDetails(api), has -> {
                if (!has) {
                    tabs.getSelectionModel().select(banking);
                    Ui.info("Welcome! Add your banking details before listing items for sale.");
                }
            });
        }

        Label who = new Label(session.name() + "  ·  " + String.join(", ", session.roles()));
        who.getStyleClass().add("who");
        Button logout = new Button("Log out");
        logout.setOnAction(e -> {
            api.logout();
            showLogin();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label brand = new Label("Community Store");
        brand.getStyleClass().add("brand");
        HBox top = new HBox(12, brand, spacer, who, logout);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("top-bar");

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);
        setRoot(root);
    }

    private static Tab add(TabPane tabs, List<RefreshableView> views, String title, RefreshableView view) {
        Tab tab = new Tab(title, view.root());
        tabs.getTabs().add(tab);
        views.add(view);
        return tab;
    }

    private void setRoot(Parent root) {
        stage.getScene().setRoot(root);
    }
}

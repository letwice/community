package community.store.ui.desktop.view;

import javafx.scene.Node;

public interface RefreshableView {

    Node root();

    void refresh();
}

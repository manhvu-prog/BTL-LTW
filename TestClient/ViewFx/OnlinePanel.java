package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.animation.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;

/** Danh sách người chơi online — return-onlineList,<username>,<nick>,<avatar>×N. Panel trong khung. */
public class OnlinePanel extends ContentPanel {
    private final ListView<String> list = new ListView<>();
    private Timeline refreshTimer;

    @Override protected String key() { return "online"; }
    @Override protected String title() { return "Đang online"; }

    @Override protected Parent content() {
        list.setPlaceholder(new Label("Không có ai online."));
        VBox.setVgrow(list, Priority.ALWAYS);

        Button back = new Button("⬅ Về sảnh");
        back.setOnAction(e -> FxUi.showLobby());
        HBox buttons = new HBox(back);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        Label header = new Label("🟢 Người chơi đang online");
        header.getStyleClass().add("title");

        VBox root = new VBox(8, header, list, buttons);
        root.setPadding(new Insets(8));
        return root;
    }

    @Override public void attach() {
        super.attach();
        requestOnline();
        if (refreshTimer != null) refreshTimer.stop();
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(10), e -> requestOnline()));
        refreshTimer.setCycleCount(Animation.INDEFINITE);
        refreshTimer.play();
    }

    private void requestOnline() {
        try { Client.clientHandler.write("onlineList"); }
        catch (IOException ignored) {}
    }

    /** ClientHandler gọi khi nhận return-onlineList. */
    public void setDataOnline(ArrayList<String> rows) {
        FxUi.runOnFx(() -> list.setItems(FXCollections.observableArrayList(rows)));
    }

    @Override public void onHide() {
        if (refreshTimer != null) refreshTimer.stop();
    }
}

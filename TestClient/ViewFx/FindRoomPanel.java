package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Tìm phòng nhanh — đếm ngược 20s; hết giờ hỏi thử lại (xác nhận NỘI TUYẾN, không popup).
 * Khi ghép thành công server chỉ gửi waiting-room-update → ClientHandler tự mở WaitingRoomPanel.
 */
public class FindRoomPanel extends ContentPanel {
    private Timeline timer;
    private int count = 20;
    private Label countdown;
    private ProgressBar bar;

    @Override protected String key() { return "findroom"; }
    @Override protected String title() { return "Tìm phòng nhanh"; }

    @Override protected Parent content() {
        Label title = new Label("🔍 Đang tìm đối thủ...");
        title.getStyleClass().add("title");
        countdown = new Label("00:20");
        countdown.getStyleClass().add("timer");

        bar = new ProgressBar(1.0);
        bar.setPrefWidth(280);

        Button cancelBtn = new Button("Huỷ tìm kiếm");
        cancelBtn.setOnAction(e -> {
            onHide();
            try { Client.clientHandler.write("cancel-room,"); } catch (IOException ignored) {}
            FxUi.showLobby();
        });

        VBox card = new VBox(16,
                title,
                new Label("Ghép phòng theo hạng ví của bạn"),
                countdown, bar,
                new ProgressIndicator(),
                cancelBtn);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(30));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(460);

        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        startFind();
        return root;
    }

    private void startFind() {
        count = 20;
        if (timer != null) timer.stop();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            count--;
            if (count >= 0) {
                countdown.setText(String.format("00:%02d", count));
                bar.setProgress(count / 20.0);
            } else {
                timer.stop();
                onTimeout();
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private void onTimeout() {
        try { Client.clientHandler.write("cancel-room,"); } catch (IOException ignored) {}
        // Xác nhận thử lại NỘI TUYẾN (không Alert popup)
        AppShell.ask("Tìm kiếm thất bại — thử lại lần nữa chứ?",
                this::startFind,
                () -> FxUi.showLobby());
    }

    /** Ghép thành công — ClientHandler gọi trước khi mở WaitingRoom. */
    public void showFoundRoom() {
        FxUi.runOnFx(() -> { if (countdown != null) countdown.setText("Đã tìm thấy!"); });
    }

    @Override public void onHide() {
        if (timer != null) timer.stop();
    }
}

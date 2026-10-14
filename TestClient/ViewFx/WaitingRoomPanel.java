package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/**
 * Phòng chờ — nhận waiting-room-update,<cur>,<max>,<tier>,<nick1>,...
 * Nick đầu = chủ phòng. Nút Bắt đầu chỉ bật khi host && ≥2 người.
 * Nút 🤖 chỉ hiện khi host && phòng có đúng 1 người thật.
 * Panel giữa trong khung 1 cửa sổ; thoát phòng bằng xác nhận nội tuyến.
 */
public class WaitingRoomPanel extends ContentPanel {
    private final VBox seats = new VBox(8);
    private Label roomInfo, status;
    private Button startBtn, botsBtn, exitBtn;
    private String roomName = "";
    private boolean readyLocked = false;

    @Override protected String key() { return "waiting"; }
    @Override protected String title() { return "Phòng chờ"; }

    @Override protected Parent content() {
        Label title = new Label("⏳ Phòng chờ");
        title.getStyleClass().add("title");
        roomInfo = new Label();
        roomInfo.getStyleClass().add("muted");
        roomInfo.setWrapText(true);

        seats.getStyleClass().add("card");
        seats.setPadding(new Insets(14));
        seats.setMinHeight(180);

        status = new Label("Đang chờ người chơi khác...");
        status.getStyleClass().add("muted");
        status.setWrapText(true);

        startBtn = new Button("▶ Bắt đầu đấu giá");
        startBtn.getStyleClass().add("primary");
        startBtn.setDisable(true);
        startBtn.setOnAction(e -> {
            try { Client.clientHandler.write("start-auction,"); } catch (IOException ignored) {}
        });

        botsBtn = new Button("🤖 Đấu thử với máy");
        botsBtn.getStyleClass().add("accent");
        botsBtn.setOnAction(e -> {
            try { Client.clientHandler.write("spawn-bots,"); } catch (IOException ignored) {}
        });

        exitBtn = new Button("Thoát phòng");
        exitBtn.setOnAction(e -> exitRoom());

        HBox actions = new HBox(12, startBtn, botsBtn, exitBtn);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, title, roomInfo, seats, status, actions);
        root.setPadding(new Insets(8));
        return root;
    }

    private void exitRoom() {
        if (readyLocked) {
            AppShell.bannerWarn("Phiên sắp bắt đầu — không thể thoát bây giờ.");
            return;
        }
        // Xác nhận NỘI TUYẾN (không Alert popup)
        AppShell.ask("Thoát phòng chờ?", () -> {
            try { Client.clientHandler.write("cancel-room,"); } catch (IOException ignored) {}
            FxUi.showLobby();
        }, null);
    }

    /**
     * ClientHandler gọi khi nhận waiting-room-update.
     * @param current số người hiện tại   @param max số ghế tối đa
     * @param tier hạng phòng (BINH_DAN...) @param nicks danh sách nick, phần tử đầu = host
     */
    public void updateWaitingRoom(int current, int max, String tier, String[] nicks) {
        FxUi.runOnFx(() -> {
            roomInfo.setText("Phòng " + roomName + "  ·  Hạng " + tierDisplay(tier)
                    + "  ·  " + current + "/" + max + " người");
            seats.getChildren().clear();
            for (int i = 0; i < max; i++) {
                Label seat;
                if (i < nicks.length) {
                    String nick = nicks[i];
                    boolean host = (i == 0);
                    boolean bot = nick.contains("🤖");
                    String prefix = bot ? "" : (host ? "👑 " : "👤 ");
                    seat = new Label(prefix + nick);
                    if (host) seat.getStyleClass().add("seat-host");
                } else {
                    seat = new Label("⬚ Ghế trống");
                    seat.getStyleClass().add("seat-empty");
                }
                seat.getStyleClass().add("seat");
                seats.getChildren().add(seat);
            }

            boolean iAmHost = nicks.length > 0 && Client.player != null
                    && nicks[0].equals(Client.player.getNickName());
            boolean onlyMe = (nicks.length == 1);
            startBtn.setDisable(!(iAmHost && current >= 2));
            botsBtn.setVisible(iAmHost && onlyMe);
            botsBtn.setManaged(iAmHost && onlyMe);
            status.setText(iAmHost
                    ? (current >= 2 ? "Bạn là chủ phòng — bấm Bắt đầu khi sẵn sàng."
                                    : "Bạn là chủ phòng — cần ít nhất 2 người để bắt đầu.")
                    : "Chờ chủ phòng bấm Bắt đầu...");
        });
    }

    private String tierDisplay(String tier) {
        return switch (tier) {
            case "BINH_DAN" -> "Bình Dân";
            case "DAI_GIA" -> "Đại Gia";
            default -> tier;
        };
    }

    public void setRoomName(String name) {
        FxUi.runOnFx(() -> roomName = name);
    }

    public void setRoomPassword(String pwd) {
        FxUi.runOnFx(() -> {
            if (pwd != null && !pwd.isBlank())
                roomInfo.setText(roomInfo.getText() + "  ·  🔒 có mật khẩu");
        });
    }

    /** ClientHandler gọi khi nhận auction-ready — khoá thoát, báo chuẩn bị vào phiên. */
    public void showFoundCompetitor() {
        FxUi.runOnFx(() -> {
            readyLocked = true;
            startBtn.setDisable(true);
            botsBtn.setDisable(true);
            exitBtn.setDisable(true);
            status.setText("✅ Đã tìm thấy đối thủ — chuẩn bị vào phiên đấu giá...");
        });
    }
}

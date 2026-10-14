package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import TestClient.ClientHandler;
import Server.Model.Player;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/**
 * Sảnh chính — hồ sơ, chat sảnh, các nút vào phòng.
 * Panel giữa trong khung 1 cửa sổ; menu chức năng đã chuyển lên AppShell (trái).
 * Tạo phòng / chọn hạng / mật khẩu: chọn nội tuyến trong panel (không popup).
 */
public class LobbyPanel extends ContentPanel {
    private TextArea chatArea;
    private TextField chatInput;
    private ComboBox<String> tierBox;
    private TextField passField;
    private Label nickLbl, walletLbl, statsLbl;
    private VBox profileCardRef;

    @Override protected String key() { return "lobby"; }
    @Override protected String title() { return "Sảnh chờ đấu giá"; }

    @Override protected Parent content() {
        Player me = Client.player;

        // ── Thẻ hồ sơ (giữ tham chiếu để update TẠI CHỖ — không dựng lại panel mất chat) ──
        nickLbl = new Label(me != null ? me.getNickName() : "?");
        nickLbl.getStyleClass().add("player-name");
        walletLbl = new Label(me != null ? "💰 " + FxUi.vnd(me.getWalletBalance()) : "");
        walletLbl.getStyleClass().add("wallet");
        statsLbl = new Label(me != null
                ? "Thắng " + me.getWin() + " · Thua " + me.getLose() + " · Hoà " + me.getDraw()
                        + " · Điểm " + me.getScore()
                : "");
        statsLbl.getStyleClass().add("muted");

        VBox profileCard = new VBox(6, new Label("👤 Hồ sơ"), nickLbl, walletLbl, statsLbl);
        profileCard.getStyleClass().add("card");
        profileCard.setPadding(new Insets(14));
        profileCardRef = profileCard;

        // ── Thẻ tạo phòng (chọn hạng + mật khẩu NỘI TUYẾN — thay 2 popup cũ) ──
        tierBox = new ComboBox<>();
        tierBox.getItems().addAll(
                "Bình Dân — ví 2–10M (trần thùng 15M)",
                "Đại Gia — ví >10M (trần thùng 50M)");
        tierBox.getSelectionModel().selectFirst();

        passField = new TextField();
        passField.setPromptText("Mật khẩu phòng (bỏ trống = công khai)");

        Button createBtn = new Button("➕ Tạo phòng ngay");
        createBtn.getStyleClass().add("primary");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> createRoom());

        Button findBtn = new Button("🔍 Tìm phòng nhanh (ghép tự động)");
        findBtn.setMaxWidth(Double.MAX_VALUE);
        findBtn.setOnAction(e -> startFind());

        VBox createCard = new VBox(8, new Label("🛒 Phòng đấu giá"), tierBox, passField, createBtn, findBtn);
        createCard.getStyleClass().add("card");
        createCard.setPadding(new Insets(14));

        // ── Chat sảnh ──
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        VBox chatBox = new VBox(6, new Label("💬 Chat sảnh"), chatArea, chatInputRow());
        chatBox.getStyleClass().add("card");
        chatBox.setPadding(new Insets(14));
        VBox.setVgrow(chatBox, Priority.ALWAYS);

        HBox top = new HBox(16, profileCard, createCard);
        top.setAlignment(Pos.TOP_LEFT);

        Label header = new Label("🎮 Sảnh chờ đấu giá");
        header.getStyleClass().add("title");

        VBox root = new VBox(14, header, top, chatBox);
        root.setPadding(new Insets(8));
        VBox.setVgrow(chatBox, Priority.ALWAYS);
        HBox.setHgrow(createCard, Priority.ALWAYS);

        Platform.runLater(this::refreshProfile);
        return root;
    }

    private HBox chatInputRow() {
        chatInput = new TextField();
        chatInput.setPromptText("Nhập tin nhắn...");

        Button send = new Button("Gửi");
        send.setOnAction(e -> sendChat());
        chatInput.setOnAction(e -> sendChat());
        HBox row = new HBox(8, chatInput, send);
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        return row;
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        try {
            Client.clientHandler.write("chat-server," + text);
            chatInput.clear();
        } catch (IOException ex) {
            AppShell.bannerError("Chat: " + ex.getMessage());
        }
    }

    /** Map hàng ComboBox → key tier gửi server. */
    private String selectedTier() {
        return tierBox.getSelectionModel().getSelectedIndex() == 0 ? "BINH_DAN" : "DAI_GIA";
    }

    private void createRoom() {
        String tier = selectedTier();
        String pass = passField.getText().trim();
        try {
            if (pass.isEmpty()) Client.clientHandler.write("create-room," + tier);
            else Client.clientHandler.write("create-room," + tier + "," + pass);
        } catch (IOException ex) {
            AppShell.bannerError("Tạo phòng: " + ex.getMessage());
        }
    }

    private void startFind() {
        String tier = selectedTier();
        try {
            FxUi.showFindRoom();
            Client.clientHandler.write("find-auction-room," + tier);
        } catch (IOException ex) {
            AppShell.bannerError("Tìm phòng: " + ex.getMessage());
        }
    }

    /** Làm mới hồ sơ — ClientHandler gọi sau khi nhận update / login-success mới. */
    public void setPlayerInformation() {
        FxUi.runOnFx(this::refreshProfile);
    }

    private void refreshProfile() {
        Player me = Client.player;
        if (me == null) return;
        FxUi.refreshShellProfile();
        // Update TẠI CHỖ trên các label đã dựng — không dựng lại panel (giữ nguyên chat)
        if (nickLbl != null) {
            nickLbl.setText(me.getNickName());
            walletLbl.setText("💰 " + FxUi.vnd(me.getWalletBalance()));
            statsLbl.setText("Thắng " + me.getWin() + " · Thua " + me.getLose()
                    + " · Hoà " + me.getDraw() + " · Điểm " + me.getScore());
        }
    }

    /** ClientHandler gọi khi nhận chat-server. */
    public void addMessage(String msg) {
        FxUi.runOnFx(() -> {
            if (chatArea != null) {
                chatArea.appendText(msg + "\n");
                chatArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }
}

package TestClient.ViewFx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

/**
 * AppShell — KHUNG MỘT CỬA SỔ DUY NHẤT của client.
 *
 * Bố cục:
 *   ┌──────────┬──────────────────────────────────────┐
 *   │ MENU TRÁI│  VÙNG NỘI DUNG (swap theo thao tác)  │
 *   │ (cố định)│  + banner thông báo nội tuyến        │
 *   └──────────┴──────────────────────────────────────┘
 *
 * Nguyên tắc (đã chốt với người dùng):
 *  - TUYỆT ĐỐI không mở Stage/popup Windows mới: mọi "màn hình" là 1 Parent
 *    được swap vào vùng nội dung (setContent).
 *  - Alert/Confirm cũng thay bằng banner + dòng xác nhận nội tuyến.
 *  - Stage vật lý duy nhất do Client.java tạo (primaryStage).
 */
public final class AppShell {

    // ── Menu trái ──
    private static VBox menuBox;
    private static Button btnLobby, btnRoomList, btnRankings, btnOnline, btnLogout;
    private static Label profileName, profileWallet;

    // ── Vùng nội dung phải + banner ──
    private static BorderPane root;          // left | center
    private static StackPane contentPane;    // chỗ swap Parent
    private static VBox bannerBox;           // banner thông báo nội tuyến
    private static VBox centerBox;           // banner + content

    private static String currentKey = "";    // key panel đang mở (để highlight menu)
    private static Parent currentPanel;
    private static Runnable currentOnHide;    // dừng timer của panel trước khi swap

    private AppShell() {}

    /** Gọi 1 lần từ Client.start — dựng khung và gán Scene cho primaryStage. */
    public static void init(javafx.stage.Stage primaryStage) {
        root = new BorderPane();
        root.setLeft(buildMenu());
        root.setCenter(buildCenter());
        root.setPrefSize(1060, 720);

        var scene = new javafx.scene.Scene(root);
        var css = AppShell.class.getResource("/fx.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        primaryStage.setTitle("Đấu Giá Thùng Hàng Bí Ẩn");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(640);
        primaryStage.centerOnScreen();
    }

    // ═══════════════════════ MENU TRÁI ═══════════════════════

    private static Parent buildMenu() {
        profileName = new Label("Chưa đăng nhập");
        profileName.getStyleClass().add("player-name");
        profileName.setWrapText(true);
        profileWallet = new Label("");
        profileWallet.getStyleClass().add("wallet");
        profileWallet.setWrapText(true);

        VBox profile = new VBox(4, profileName, profileWallet);
        profile.getStyleClass().add("card");
        profile.setPadding(new Insets(10));

        btnLobby    = menuButton("🏠  Sảnh chính",      "lobby");
        btnRoomList = menuButton("📋  Danh sách phòng", "roomlist");
        btnRankings = menuButton("🏆  Bảng xếp hạng",   "rankings");
        btnOnline   = menuButton("🟢  Đang online",     "online");
        btnLogout   = menuButton("🚪  Đăng xuất",       null);
        btnLogout.setOnAction(e -> doLogout());

        Label brand = new Label("📦 ĐẤU GIÁ BÍ ẨN");
        brand.getStyleClass().add("brand");

        menuBox = new VBox(8, brand, new Separator(), profile, new Separator(),
                btnLobby, btnRoomList, btnRankings, btnOnline, new Separator(), btnLogout);
        menuBox.setPadding(new Insets(14));
        menuBox.setPrefWidth(210);
        menuBox.getStyleClass().add("menu");
        // Menu mặc định ẩn (chỉ hiện khi đã đăng nhập / vào game)
        menuBox.setVisible(false);
        menuBox.setManaged(false);
        return menuBox;
    }

    private static Button menuButton(String text, String key) {
        Button b = new Button(text);
        b.getStyleClass().add("menu-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        if (key != null) b.setOnAction(e -> navigate(key));
        return b;
    }

    /** Bật/tắt menu theo trạng thái đăng nhập. */
    public static void setMenuVisible(boolean visible) {
        if (menuBox == null) return;
        menuBox.setVisible(visible);
        menuBox.setManaged(visible);
    }

    public static void setProfile(String nick, String walletVnd) {
        if (profileName == null) return;
        profileName.setText(nick);
        profileWallet.setText(walletVnd);
    }

    private static void doLogout() {
        try { TestClient.Client.clientHandler.write("offline"); } catch (Exception ignored) {}
        TestClient.Client.player = null;
        setMenuVisible(false);
        show("login");
    }

    // ═══════════════════════ VÙNG NỘI DUNG ═══════════════════════

    private static Parent buildCenter() {
        bannerBox = new VBox(6);
        bannerBox.getStyleClass().add("banner-area");
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        centerBox = new VBox(8, bannerBox, contentPane);
        centerBox.setPadding(new Insets(10));
        return centerBox;
    }

    /**
     * Swap panel mới vào vùng nội dung — điểm điều hướng DUY NHẤT của app.
     * @param key     tên màn hình ("login","register","lobby","roomlist","findroom",
     *                "waiting","auction","rankings","online")
     * @param panel   Parent dựng sẵn
     * @param onHide  Runnable dừng timer nền của panel cũ (gọi trước khi thay)
     */
    public static void setContent(String key, Parent panel, Runnable onHide) {
        // Dừng nền panel cũ (timer tìm phòng, refresh online, đồng hồ vòng...)
        if (currentOnHide != null) {
            try { currentOnHide.run(); } catch (Exception ignored) {}
        }
        currentOnHide = onHide;
        currentPanel = panel;
        currentKey = key;
        contentPane.getChildren().setAll(panel);
        highlight(key);
    }

    public static String currentKey() { return currentKey; }

    /** Đánh dấu menu đang sáng theo key; "" hoặc key không thuộc menu → không sáng item nào. */
    private static void highlight(String key) {
        setLit(btnLobby,    key.equals("lobby") || key.equals("findroom") || key.equals("waiting"));
        setLit(btnRoomList, key.equals("roomlist"));
        setLit(btnRankings, key.equals("rankings"));
        setLit(btnOnline,   key.equals("online"));
    }

    private static void setLit(Button b, boolean lit) {
        if (b == null) return;
        b.getStyleClass().remove("menu-item-active");
        if (lit) b.getStyleClass().add("menu-item-active");
    }

    /** Nút menu bấm — quay về panel tương ứng nếu đang ở chỗ cho phép quay. */
    private static void navigate(String key) {
        if (key.equals(currentKey)) return;
        // Trong phiên đấu giá không cho bấm lang thang (tránh mất màn chơi);
        // thoát phiên phải qua nút Thoát phòng của AuctionView.
        if (currentKey.equals("auction") && !key.equals("auction")) return;
        // Ở phòng chờ: chỉ cho quay về sảnh qua nút Thoát phòng (đồng bộ luồng mạng).
        if (currentKey.equals("waiting") && !key.equals("waiting")) return;
        switch (key) {
            case "lobby"    -> { if (TestClient.Client.player != null) show("lobby"); }
            case "roomlist" -> { try { TestClient.Client.clientHandler.write("view-room-list,"); } catch (Exception ignored) {} show("roomlist"); }
            case "rankings" -> { try { TestClient.Client.clientHandler.write("leaderboard"); } catch (Exception ignored) {} show("rankings"); }
            case "online"   -> { show("online"); }
        }
    }

    // ═══════════════════════ BANNER NỘI TUYẾN ═══════════════════════

    /** Banner thông báo thường (thay Alert INFORMATION/WARNING). */
    public static void banner(String styleClass, String message) {
        Label l = new Label(message);
        l.getStyleClass().addAll("banner", styleClass);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        bannerBox.getChildren().add(l);
        // Tự biến mất sau 6 giây
        javafx.animation.Timeline t = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(6), ev -> bannerBox.getChildren().remove(l)));
        t.setCycleCount(1);
        t.play();
    }

    public static void bannerInfo(String msg)    { banner("banner-info", msg); }
    public static void bannerSuccess(String msg) { banner("banner-success", msg); }
    public static void bannerWarn(String msg)    { banner("banner-warn", msg); }
    public static void bannerError(String msg)   { banner("banner-error", msg); }

    /** Xoá hết banner (khi swap màn hình). */
    public static void clearBanners() {
        if (bannerBox != null) bannerBox.getChildren().clear();
    }

    /**
     * Câu hỏi Yes/No nội tuyến (thay Alert CONFIRMATION): hiện hàng nút ngay đầu
     * vùng nội dung, callback chạy khi người dùng chọn. Chỉ 1 câu hỏi tại 1 thời điểm.
     */
    public static void ask(String message, Runnable onYes, Runnable onNo) {
        clearAsk();
        Label l = new Label("❓ " + message);
        l.getStyleClass().addAll("banner", "banner-ask");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        Button yes = new Button("Đồng ý");
        yes.getStyleClass().add("primary");
        Button no = new Button("Huỷ");
        HBox btns = new HBox(8, yes, no);
        btns.setAlignment(Pos.CENTER_LEFT);
        Runnable cleanup = () -> bannerBox.getChildren().removeAll(l, btns);
        yes.setOnAction(e -> { cleanup.run(); if (onYes != null) onYes.run(); });
        no.setOnAction(e -> { cleanup.run(); if (onNo != null) onNo.run(); });
        bannerBox.getChildren().addAll(l, btns);
    }

    /** Xoá câu hỏi đang treo (nếu có). */
    public static void clearAsk() {
        if (bannerBox == null) return;
        bannerBox.getChildren().removeIf(n ->
                n.getStyleClass().contains("banner-ask")
                        || (n instanceof HBox hb && !hb.getChildren().isEmpty()
                            && hb.getChildren().get(0) instanceof Button b
                            && "Đồng ý".equals(b.getText())));
    }

    /**
     * Hỏi kèm ô nhập nội tuyến (thay TextInputDialog — ví dụ mật khẩu phòng).
     * Callback onOk nhận text người dùng nhập; nút Huỷ bỏ qua.
     */
    public static void askInput(String message, java.util.function.Consumer<String> onOk) {
        clearAsk();
        Label l = new Label("❓ " + message);
        l.getStyleClass().addAll("banner", "banner-ask");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.control.TextField field = new javafx.scene.control.TextField();
        field.setPromptText("Nhập tại đây...");
        field.setPrefWidth(260);
        Button ok = new Button("Đồng ý");
        ok.getStyleClass().add("primary");
        Button no = new Button("Huỷ");
        HBox btns = new HBox(8, field, ok, no);
        btns.setAlignment(Pos.CENTER_LEFT);
        Runnable cleanup = () -> bannerBox.getChildren().removeAll(l, btns);
        ok.setOnAction(e -> {
            String val = field.getText();
            cleanup.run();
            if (onOk != null) onOk.accept(val);
        });
        field.setOnAction(e -> ok.fire());
        no.setOnAction(e -> cleanup.run());
        bannerBox.getChildren().addAll(l, btns);
        field.requestFocus();
    }

    // ═══════════════════════ ĐIỀU HƯỚNG CAO CẤP ═══════════════════════

    /** Hiện panel theo key (FxUi gọi — panel đã được build trước trong FxUi). */
    public static void show(String key) {
        TestClient.FxUi.showPanel(key);
    }

    /** Node đang hiển thị (nếu cần). */
    public static Node currentNode() { return currentPanel; }
}

package TestClient;

import TestClient.ViewFx.*;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.File;
import java.util.Locale;

/**
 * FxUi — trung tâm điều phối màn hình client (bản MỘT CỬA SỔ DUY NHẤT).
 *
 * GIỮ NGUYÊN public API cũ (tên phương thức + tên field) để ClientHandler không
 * phải đổi logic — chỉ thay bên trong: mọi "màn hình" là 1 ContentPanel được
 * swap vào AppShell (menu trái + vùng nội dung), TUYỆT ĐỐI không mở Stage/popup
 * Windows mới. Alert/Confirm cũng thay bằng banner nội tuyến.
 *
 * Toàn bộ phương thức phải được gọi trên FX Application Thread
 * (ClientHandler đã bọc bằng Platform.runLater trước khi gọi vào đây).
 */
public final class FxUi {
    private FxUi() {}

    // Tên field giữ như cũ — kiểu giờ là ContentPanel
    public static LoginPanel loginView;
    public static RegisterPanel registerView;
    public static LobbyPanel lobbyView;
    public static RoomListPanel roomListView;
    public static FindRoomPanel findRoomView;
    public static WaitingRoomPanel waitingRoomView;
    public static AuctionPanel auctionView;
    public static RankingsPanel rankingsView;
    public static OnlinePanel onlineListView;

    private static Stage primaryStage;

    public static void setPrimaryStage(Stage s) {
        primaryStage = s;
        AppShell.init(s);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // ── Điều hướng (API cũ giữ nguyên) ────────────────────────────────────

    /** Mở màn Đăng nhập (mặc định dùng cho luồng chính). */
    public static void showLogin() {
        loginView = new LoginPanel();
        loginView.attach();
    }

    /** Màn Đăng nhập kèm u/p đã nhập (sau lỗi) — giữ hành vi client cũ. */
    public static void showLogin(String u, String p) {
        loginView = new LoginPanel(u, p);
        loginView.attach();
    }

    public static void showRegister() {
        registerView = new RegisterPanel();
        registerView.attach();
    }

    public static void showLobby() {
        AppShell.setMenuVisible(true);
        refreshShellProfile();
        lobbyView = new LobbyPanel();
        lobbyView.attach();
    }

    public static void showRoomList() {
        roomListView = new RoomListPanel();
        roomListView.attach();
    }

    public static void showFindRoom() {
        findRoomView = new FindRoomPanel();
        findRoomView.attach();
    }

    public static void showWaitingRoom() {
        if (waitingRoomView != null && waitingRoomView.isShowing()) return;
        waitingRoomView = new WaitingRoomPanel();
        waitingRoomView.attach();
    }

    public static void showAuction(long sessionId, double totalWeight) {
        showAuction(sessionId, totalWeight, null, null);
    }

    /** Màn Đấu giá kèm chủ đề thùng hàng (server random mỗi phiên — token 4–5 của auction-start). */
    public static void showAuction(long sessionId, double totalWeight, String themeKey, String themeName) {
        auctionView = new AuctionPanel(sessionId, totalWeight, themeKey, themeName);
        auctionView.attach();
    }

    public static void showRankings() {
        rankingsView = new RankingsPanel();
        rankingsView.attach();
    }

    public static void showOnlineList() {
        onlineListView = new OnlinePanel();
        onlineListView.attach();
    }

    public static void closeAllViews() {
        // Panel cũ có timer sẽ bị AppShell.onHide dừng ngay khi panel khác swap vào;
        // ở đây chỉ xoá tham chiếu. Đồng thời ẩn menu khi về màn chưa đăng nhập.
        loginView = null;
        registerView = null;
        lobbyView = null;
        roomListView = null;
        findRoomView = null;
        waitingRoomView = null;
        auctionView = null;
        rankingsView = null;
        onlineListView = null;
    }

    public static boolean isWaitingRoomShowing() {
        return waitingRoomView != null && waitingRoomView.isShowing();
    }

    /** AppShell bấm menu cần panel theo key — điều phối trung tâm. */
    public static void showPanel(String key) {
        switch (key) {
            case "login"    -> { closeAllViews(); showLogin(); }
            case "lobby"    -> showLobby();
            case "roomlist" -> showRoomList();
            case "rankings" -> showRankings();
            case "online"   -> showOnlineList();
            default -> { /* findroom/waiting/auction không mở qua menu */ }
        }
    }

    /** Cập nhật hồ sơ mini trên menu trái. */
    public static void refreshShellProfile() {
        if (Client.player != null) {
            AppShell.setProfile(Client.player.getNickName(),
                    "💰 " + vnd(Client.player.getWalletBalance()));
        }
    }

    // ── Thông báo: banner nội tuyến thay Alert popup ─────────────────────

    public static void info(String title, String msg) {
        runOnFx(() -> AppShell.bannerInfo(msg));
    }

    public static void warn(String title, String msg) {
        runOnFx(() -> AppShell.bannerWarn(msg));
    }

    public static void error(String title, String msg) {
        runOnFx(() -> AppShell.bannerError(msg));
    }

    /** Chạy trên FX thread an toàn từ bất kỳ luồng nào (luồng mạng, timer...). */
    public static void runOnFx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    // ── Tiện ích hiển thị ─────────────────────────────────────────────────

    /** Định dạng tiền VNĐ có dấu phân cách: 1234567 → "1,234,567". */
    public static String vnd(double amount) {
        return String.format(Locale.US, "%,.0f", amount) + " VNĐ";
    }

    /** Ảnh avatar theo đường dẫn assets/avatar/<avatar> — fallback placeholder. */
    public static ImageView avatar(String avatarName, double size) {
        return loadImage("assets/avatar/" + (avatarName == null || avatarName.isBlank() ? "0.png" : avatarName), size);
    }

    /** Ảnh sản phẩm assets/product/<picture> — seed để NULL → placeholder 📦. */
    public static ImageView product(String pictureName, double size) {
        return loadImage("assets/product/" + (pictureName == null || pictureName.isBlank() ? "0.png" : pictureName), size);
    }

    private static ImageView loadImage(String path, double size) {
        ImageView iv = new ImageView();
        try {
            File f = new File(path);
            if (f.exists()) iv.setImage(new Image(f.toURI().toString()));
        } catch (Exception ignored) {}
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        return iv;
    }
}

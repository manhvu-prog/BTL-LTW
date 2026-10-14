package TestClient;

import Server.Model.Player;
import TestClient.ViewFx.AppShell;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Client — entry JavaFX (Launcher), GIAO DIỆN MỘT CỬA SỔ DUY NHẤT.
 * Stage gốc là cửa sổ vật lý DUY NHẤT: AppShell.init dựng khung (menu trái +
 * vùng nội dung) ngay trong start; mọi màn hình là panel swap trong đó —
 * TUYỆT ĐỐI không mở Stage/popup Windows mới (đã chốt).
 * Chạy bằng:
 *   java --module-path lib --add-modules javafx.controls -cp out;mysql-connector... TestClient.Client
 * (class kế thừa Application KHÔNG main → tránh bẫy "JavaFX runtime components are missing"
 * khi chạy từ classpath; JVM sẽ tự nạp JavaFX qua module-path).
 */
public class Client extends Application {
    public static Player player;
    public static ClientHandler clientHandler;

    @Override
    public void start(Stage stage) {
        FxUi.setPrimaryStage(stage); // AppShell.init dựng khung + gán Scene
        clientHandler = new ClientHandler();
        clientHandler.start();

        FxUi.showLogin();
        stage.show(); // hiển thị cửa sổ duy nhất
    }

    public static void main(String[] args) {
        // Bỏ launch(args) — Application.launch đủ; giữ main cho tương thích
        Application.launch(Client.class, args);
    }

    // ── Hỗ trợ luồng cũ ───────────────────────────────────────────────────

    /** Đăng ký xong/quay lại — RegisterPanel gọi (giữ API cho tương thích). */
    public static void closeFxRegister() {
        FxUi.registerView = null;
    }

    /** Mất kết nối → về màn Đăng nhập + banner nội tuyến (không Alert popup). */
    public static void showConnectionLost(boolean wasConnected) {
        javafx.application.Platform.runLater(() -> {
            player = null;
            FxUi.showLogin();
            AppShell.bannerError(wasConnected
                    ? "Mất kết nối với server. Vui lòng đăng nhập lại."
                    : "Không kết nối được server (127.0.0.1:7777). Hãy khởi động Server TRƯỚC khi mở Client.");
        });
    }
}

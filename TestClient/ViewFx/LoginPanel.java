package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/** Màn Đăng nhập — gửi client-verify,<u>,<p>. Panel trong khung 1 cửa sổ. */
public class LoginPanel extends ContentPanel {
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();
    private Label message;

    public LoginPanel() { super(); }
    public LoginPanel(String u, String p) {
        super();
        if (u != null) userField.setText(u);
        if (p != null) passField.setText(p);
    }

    @Override protected String key() { return "login"; }
    @Override protected String title() { return "Đăng nhập"; }

    @Override protected Parent content() {
        Label logo = new Label("📦 ĐẤU GIÁ THÙNG HÀNG BÍ ẨN");
        logo.getStyleClass().add("title");
        logo.setWrapText(true);

        Label sub = new Label("Đấu giá kín 4 vòng · Thùng hàng theo chủ đề · Phòng 3 người");
        sub.getStyleClass().add("muted");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.addRow(0, new Label("Tài khoản"), userField);
        form.addRow(1, new Label("Mật khẩu"), passField);
        userField.setPromptText("vd: test001");
        passField.setPromptText("••••••••");

        Button loginBtn = new Button("Đăng nhập");
        loginBtn.getStyleClass().add("primary");
        loginBtn.setDefaultButton(true);
        loginBtn.setOnAction(e -> doLogin());

        Button registerBtn = new Button("Tạo tài khoản mới");
        registerBtn.setOnAction(e -> FxUi.showRegister());

        HBox buttons = new HBox(12, loginBtn, registerBtn);
        buttons.setAlignment(Pos.CENTER);

        message = new Label();
        message.getStyleClass().add("error");
        message.setWrapText(true);

        VBox card = new VBox(16, logo, sub, new Separator(), form, buttons, message);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(520);

        // Panel login: menu trái ẩn, nội dung căn giữa
        AppShell.setMenuVisible(false);
        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        return root;
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = passField.getText();
        if (u.isEmpty() || p.isEmpty()) {
            message.setText("Vui lòng nhập tài khoản và mật khẩu.");
            return;
        }
        try {
            Client.clientHandler.write("client-verify," + u + "," + p);
        } catch (IOException ex) {
            message.setText("Lỗi gửi dữ liệu: " + ex.getMessage());
        }
    }

    /** ClientHandler gọi khi nhận wrong-player / dupplicate-login. */
    public void showMessage(String msg) {
        FxUi.runOnFx(() -> message.setText(msg));
    }
}

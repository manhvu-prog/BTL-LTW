package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/** Màn Đăng ký — gửi register,<u>,<p>,<nick>,<avatar>. Panel trong khung 1 cửa sổ. */
public class RegisterPanel extends ContentPanel {
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();
    private final TextField nickField = new TextField();

    @Override protected String key() { return "register"; }
    @Override protected String title() { return "Đăng ký"; }

    @Override protected Parent content() {
        Label title = new Label("📝 Tạo tài khoản");
        title.getStyleClass().add("title");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.addRow(0, new Label("Tài khoản"), userField);
        form.addRow(1, new Label("Mật khẩu"), passField);
        form.addRow(2, new Label("Biệt danh"), nickField);
        userField.setPromptText("tên đăng nhập duy nhất");
        nickField.setPromptText("tên hiển thị trong game");

        Button registerBtn = new Button("Đăng ký");
        registerBtn.getStyleClass().add("primary");
        registerBtn.setDefaultButton(true);
        registerBtn.setOnAction(e -> doRegister());

        Button backBtn = new Button("Quay lại đăng nhập");
        backBtn.setOnAction(e -> FxUi.showLogin());

        HBox buttons = new HBox(12, registerBtn, backBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox card = new VBox(16, title, form, buttons);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(520);

        AppShell.setMenuVisible(false);
        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        return root;
    }

    private void doRegister() {
        String u = userField.getText().trim();
        String p = passField.getText();
        String nick = nickField.getText().trim().isEmpty() ? u : nickField.getText().trim();
        if (u.isEmpty() || p.isEmpty()) {
            AppShell.bannerWarn("Vui lòng nhập tài khoản và mật khẩu.");
            return;
        }
        try {
            // avatar để rỗng "none.png" — server lưu VARCHAR trắng, client fallback placeholder
            Client.clientHandler.write("register," + u + "," + p + "," + nick + ",none.png");
        } catch (IOException ex) {
            AppShell.bannerError("Lỗi gửi dữ liệu: " + ex.getMessage());
        }
    }
}

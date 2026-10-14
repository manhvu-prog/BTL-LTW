package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/** Danh sách phòng chờ — room-list,<id>,<tier>,<cur>,<max>,<locked>×N. Panel trong khung. */
public class RoomListPanel extends ContentPanel {
    public static class RoomRow {
        public final int id; public final String tier;
        public final int cur, max; public final boolean locked;
        RoomRow(int id, String tier, int cur, int max, boolean locked) {
            this.id = id; this.tier = tier; this.cur = cur; this.max = max; this.locked = locked;
        }
        public SimpleIntegerProperty idP() { return new SimpleIntegerProperty(id); }
        public SimpleStringProperty tierP() { return new SimpleStringProperty(tier); }
        public SimpleStringProperty slotsP() { return new SimpleStringProperty(cur + "/" + max); }
        public SimpleStringProperty lockP() { return new SimpleStringProperty(locked ? "🔒" : "🔓"); }
    }

    private final TableView<RoomRow> table = new TableView<>();

    @Override protected String key() { return "roomlist"; }
    @Override protected String title() { return "Phòng đang chờ"; }

    @Override protected Parent content() {
        TableColumn<RoomRow, Number> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(d -> d.getValue().idP());
        TableColumn<RoomRow, String> cTier = new TableColumn<>("Hạng");
        cTier.setCellValueFactory(d -> d.getValue().tierP());
        TableColumn<RoomRow, String> cSlots = new TableColumn<>("Số người");
        cSlots.setCellValueFactory(d -> d.getValue().slotsP());
        TableColumn<RoomRow, String> cLock = new TableColumn<>("Mật khẩu");
        cLock.setCellValueFactory(d -> d.getValue().lockP());
        table.getColumns().setAll(cId, cTier, cSlots, cLock);
        table.setPlaceholder(new Label("Chưa có phòng nào đang chờ — bấm Làm mới hoặc tự tạo phòng ở Sảnh."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button refresh = new Button("🔄 Làm mới");
        refresh.getStyleClass().add("primary");
        refresh.setOnAction(e -> requestList());

        Button join = new Button("Vào phòng");
        join.setOnAction(e -> joinSelected());

        Button back = new Button("⬅ Về sảnh");
        back.setOnAction(e -> FxUi.showLobby());

        HBox buttons = new HBox(10, refresh, join, back);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        Label header = new Label("📋 Phòng đang chờ");
        header.getStyleClass().add("title");

        VBox root = new VBox(8, header, table, buttons);
        root.setPadding(new Insets(8));
        return root;
    }

    @Override public void attach() {
        super.attach();
        requestList(); // tự làm mới mỗi lần mở
    }

    private void requestList() {
        try { Client.clientHandler.write("view-room-list,"); }
        catch (IOException ex) { AppShell.bannerError("Danh sách phòng: " + ex.getMessage()); }
    }

    private void joinSelected() {
        RoomRow r = table.getSelectionModel().getSelectedItem();
        if (r == null) {
            AppShell.bannerWarn("Hãy chọn một phòng trong bảng trước.");
            return;
        }
        if (r.locked) {
            // Nhập mật khẩu NỘI TUYẾN (banner hỏi) — không mở popup
            AppShell.askInput("Nhập mật khẩu phòng " + r.id + ":", pass -> {
                try {
                    if (pass == null || pass.isEmpty()) Client.clientHandler.write("join-room," + r.id);
                    else Client.clientHandler.write("join-room," + r.id + "," + pass);
                } catch (IOException ex) {
                    AppShell.bannerError("Vào phòng: " + ex.getMessage());
                }
            });
        } else {
            try {
                Client.clientHandler.write("join-room," + r.id);
            } catch (IOException ex) {
                AppShell.bannerError("Vào phòng: " + ex.getMessage());
            }
        }
    }

    /** ClientHandler gọi khi nhận room-list. */
    public void updateRoomList(java.util.List<String> names, java.util.List<String> passwords) {
        FxUi.runOnFx(() -> {
            java.util.List<RoomRow> rows = new java.util.ArrayList<>();
            for (String n : names) {
                try {
                    // "Phòng <id> [<tier>] (<cur>/<max>)"
                    int id = Integer.parseInt(n.replaceAll("[^0-9]", " ").trim().split("\\s+")[0]);
                    String tier = n.contains("[") ? n.substring(n.indexOf('[') + 1, n.indexOf(']')) : "";
                    String slots = n.contains("(") ? n.substring(n.indexOf('(') + 1, n.indexOf(')')) : "";
                    int cur = 0, max = 3;
                    try {
                        String[] sp = slots.split("/");
                        cur = Integer.parseInt(sp[0].trim());
                        max = Integer.parseInt(sp[1].trim());
                    } catch (Exception ignored) {}
                    boolean locked = passwords.get(rows.size()).equals("*");
                    rows.add(new RoomRow(id, tier, cur, max, locked));
                } catch (Exception ignored) {}
            }
            table.setItems(FXCollections.observableArrayList(rows));
        });
    }
}

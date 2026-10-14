package TestClient.ViewFx;

import TestClient.FxUi;
import Server.Model.Player;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** Bảng xếp hạng — return-leaderboard,<10 trường player>×N (server đã sắp xếp). Panel trong khung. */
public class RankingsPanel extends ContentPanel {
    public static class Row {
        public final Player p;
        public final int rank;
        public Row(Player p, int rank) { this.p = p; this.rank = rank; }
        public SimpleStringProperty rankP() { return new SimpleStringProperty(String.valueOf(rank)); }
        public SimpleStringProperty nickP() { return new SimpleStringProperty(p.getNickName()); }
        public SimpleStringProperty statP() {
            return new SimpleStringProperty(p.getWin() + " - " + p.getLose() + " - " + p.getDraw());
        }
        public SimpleDoubleProperty scoreP() { return new SimpleDoubleProperty(p.getScore()); }
        public SimpleStringProperty walletP() { return new SimpleStringProperty(FxUi.vnd(p.getWalletBalance())); }
    }

    private final TableView<Row> table = new TableView<>();

    @Override protected String key() { return "rankings"; }
    @Override protected String title() { return "Bảng xếp hạng"; }

    @Override protected Parent content() {
        TableColumn<Row, String> cRank = new TableColumn<>("#");
        cRank.setCellValueFactory(d -> d.getValue().rankP());
        TableColumn<Row, String> cNick = new TableColumn<>("Biệt danh");
        cNick.setCellValueFactory(d -> d.getValue().nickP());
        TableColumn<Row, String> cStat = new TableColumn<>("Thắng - Thua - Hoà");
        cStat.setCellValueFactory(d -> d.getValue().statP());
        TableColumn<Row, Number> cScore = new TableColumn<>("Điểm");
        cScore.setCellValueFactory(d -> d.getValue().scoreP());
        TableColumn<Row, String> cWallet = new TableColumn<>("Ví");
        cWallet.setCellValueFactory(d -> d.getValue().walletP());
        table.getColumns().setAll(cRank, cNick, cStat, cScore, cWallet);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Đang tải dữ liệu..."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button back = new Button("⬅ Về sảnh");
        back.setOnAction(e -> FxUi.showLobby());
        HBox buttons = new HBox(back);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        Label header = new Label("🏆 Bảng xếp hạng");
        header.getStyleClass().add("title");

        VBox root = new VBox(8, header, table, buttons);
        root.setPadding(new Insets(8));
        return root;
    }

    /** ClientHandler gọi khi nhận return-leaderboard. */
    public void setDataRanking(List<Player> players) {
        FxUi.runOnFx(() -> {
            var rows = new java.util.ArrayList<Row>();
            int i = 1;
            for (Player p : players) rows.add(new Row(p, i++));
            table.setItems(FXCollections.observableArrayList(rows));
            table.refresh();
        });
    }
}

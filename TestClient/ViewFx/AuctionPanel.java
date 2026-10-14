package TestClient.ViewFx;

import TestClient.Client;
import TestClient.FxUi;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Locale;

/**
 * Màn Đấu giá — trái tim của game. Panel giữa trong khung 1 cửa sổ.
 * ĐẤU GIÁ KÍN THEO VÒNG: bid-accepted KHÔNG kèm tiền → chỉ hiện ai đã đặt;
 * giá hé lộ khi HẾT VÒNG qua bid-reveal (bảng công bố).
 * Xác nhận MUA ĐỨT / thoát phòng: NỘI TUYẾN (AppShell.ask) — không popup.
 */
public class AuctionPanel extends ContentPanel {
    private final long sessionId;
    private final double totalWeight;
    private final String themeKey;    // key chủ đề (CONG_NGHE...)
    private final String themeName;   // tên hiển thị ("Công nghệ"...)

    private Label header, roundLbl, timerLbl, estLbl, buyoutLbl, sealedNote;
    private VBox itemsBox, biddersBox, revealBox;
    private TextArea log;
    private TextField bidInput;
    private Button bidBtn;
    private final java.util.List<Button> quickButtons = new java.util.ArrayList<>();
    private Timeline timer;
    private int remainingSeconds;
    private double currentEstimate, currentBuyoutPrice;
    private boolean buyoutFrozen = false;
    private boolean hasBidThisRound = false;

    public AuctionPanel(long sessionId, double totalWeight) {
        this(sessionId, totalWeight, null, null);
    }

    public AuctionPanel(long sessionId, double totalWeight, String themeKey, String themeName) {
        this.sessionId = sessionId;
        this.totalWeight = totalWeight;
        this.themeKey = themeKey;
        this.themeName = (themeName == null || themeName.isBlank())
                ? (themeKey == null ? "Bí ẩn" : themeKey) : themeName;
    }

    @Override protected String key() { return "auction"; }
    @Override protected String title() { return "Phiên đấu giá #" + sessionId; }

    @Override protected Parent content() {
        // Header nhấn mạnh CHỦ ĐỀ của thùng (người chơi biết chủ đề ngay khi vào phiên)
        String themeEmoji = switch (themeKey == null ? "" : themeKey) {
            case "CONG_NGHE" -> "💻"; case "TRANG_SUC" -> "💎"; case "O_TO" -> "🚗";
            case "THOI_TRANG" -> "👕"; case "GIA_DUNG" -> "🏠"; case "THE_THAO" -> "⚽";
            case "THUC_PHAM" -> "🍜"; case "SACH_VAN_PHONG" -> "📚"; case "DO_CHOI" -> "🧸";
            case "MY_PHAM" -> "💄"; case "NHAC_CU" -> "🎸"; case "DO_CO" -> "🏺";
            case "THU_CUNG" -> "🐾"; case "DU_LICH" -> "🏕️"; default -> "📦";
        };
        Label themeLbl = new Label(themeEmoji + " Chủ đề: " + themeName);
        themeLbl.getStyleClass().add("theme-badge");

        header = new Label("📦 Thùng bí ẩn — " + String.format(Locale.US, "%.2f kg", totalWeight));
        header.getStyleClass().add("title");

        roundLbl = new Label("Vòng —/4");
        roundLbl.getStyleClass().add("round");
        timerLbl = new Label("⏱ --s");
        timerLbl.getStyleClass().add("timer");

        HBox topBar = new HBox(16, roundLbl, new Region(), timerLbl);
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        topBar.setPadding(new Insets(4));

        estLbl = new Label("Giá ước tính: --");
        buyoutLbl = new Label("Mua đứt: --");
        estLbl.getStyleClass().add("price");
        buyoutLbl.getStyleClass().add("price");
        VBox priceCard = new VBox(6, estLbl, buyoutLbl);
        priceCard.getStyleClass().add("card");
        priceCard.setPadding(new Insets(12));

        itemsBox = new VBox(8);
        itemsBox.getChildren().add(new Label("🎁 Vật phẩm đã hé lộ: (chưa có)"));
        ScrollPane itemsScroll = new ScrollPane(itemsBox);
        itemsScroll.setFitToWidth(true);
        itemsScroll.setPrefHeight(150);
        itemsScroll.getStyleClass().add("card");

        sealedNote = new Label("🔒 GIÁ NIÊM PHONG — hé lộ khi hết vòng");
        sealedNote.getStyleClass().add("sealed");
        biddersBox = new VBox(4);
        biddersBox.getChildren().add(new Label("Chưa ai đặt giá."));
        VBox sealedCard = new VBox(6, sealedNote, biddersBox);
        sealedCard.getStyleClass().add("card");
        sealedCard.setPadding(new Insets(12));

        revealBox = new VBox(4);
        ScrollPane revealScroll = new ScrollPane(revealBox);
        revealScroll.setFitToWidth(true);
        revealScroll.setPrefHeight(120);
        revealScroll.getStyleClass().add("reveal");
        Label revealTitle = new Label("🔓 CÔNG BỐ GIÁ (cuối mỗi vòng)");
        revealTitle.getStyleClass().add("reveal-title");
        VBox revealCard = new VBox(6, revealTitle, revealScroll);
        revealCard.getStyleClass().add("card");
        revealCard.setPadding(new Insets(12));

        bidInput = new TextField();
        bidInput.setPromptText("Nhập số tiền (VNĐ)");
        bidBtn = new Button("Đặt giá");
        bidBtn.getStyleClass().add("primary");
        bidBtn.setDefaultButton(true);
        bidBtn.setOnAction(e -> placeBid());

        Button buyoutBtn = new Button("💸 MUA ĐỨT");
        buyoutBtn.getStyleClass().add("danger");
        buyoutBtn.setOnAction(e -> confirmBuyout());

        HBox bidRow = new HBox(10, bidInput, bidBtn, buyoutBtn);
        HBox.setHgrow(bidInput, Priority.ALWAYS);
        bidRow.setPadding(new Insets(6, 0, 6, 0));

        HBox quick = new HBox(8);
        for (double pct : new double[]{0.8, 0.9, 1.0, 1.1}) {
            Button b = new Button(String.format("%.0f%%", pct * 100));
            double p = pct;
            b.setOnAction(e -> quickFill(p));
            quick.getChildren().add(b);
            quickButtons.add(b);
        }
        Label quickLbl = new Label("Tính % trên giá đã nhập:");
        quickLbl.getStyleClass().add("muted");
        HBox quickRow = new HBox(8, quickLbl, quick);
        quickRow.setPadding(new Insets(0, 0, 4, 0));

        log = new TextArea();
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefHeight(120);

        Button exitBtn = new Button("Thoát phòng");
        exitBtn.setOnAction(e -> exitGame());

        VBox left = new VBox(10, priceCard, itemsScroll, bidRow, quickRow);
        VBox right = new VBox(10, sealedCard, revealCard, new Label("📝 Nhật ký phiên"), log, exitBtn);
        HBox main = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox root = new VBox(10, header, themeLbl, topBar, main);
        root.setPadding(new Insets(8));
        return root;
    }

    // ── Hành động người chơi ─────────────────────────────────────────────

    private void placeBid() {
        if (hasBidThisRound) {
            AppShell.bannerWarn("Mỗi người chỉ được đấu giá 1 lần/vòng — chờ sang vòng sau.");
            return;
        }
        String input = bidInput.getText().trim();
        if (input.isEmpty()) {
            AppShell.bannerWarn("Vui lòng nhập số tiền muốn đặt giá.");
            return;
        }
        try {
            double amount = Double.parseDouble(input.replace(",", ""));
            Client.clientHandler.write("bid," + (long) amount);
            addLog("Bạn đã đặt giá (kín): " + FxUi.vnd(amount));
        } catch (NumberFormatException ex) {
            AppShell.bannerError("Số tiền không hợp lệ.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Nút % chỉ điền lại vào ô giá theo số đã nhập, KHÔNG bid luôn. */
    private void quickFill(double pct) {
        String input = bidInput.getText().trim();
        if (input.isEmpty()) {
            AppShell.bannerWarn("Nhập giá đấu giá trước rồi mới bấm nút %.");
            return;
        }
        try {
            double base = Double.parseDouble(input.replace(",", ""));
            if (base <= 0) {
                AppShell.bannerError("Số tiền phải lớn hơn 0.");
                return;
            }
            long filled = Math.round(base * pct / 1000.0) * 1000L;
            bidInput.setText(String.valueOf(filled));
            addLog(String.format("Đã tính %.0f%% của %s → %s (bấm Đặt giá để xác nhận)",
                    pct * 100, FxUi.vnd(base), FxUi.vnd(filled)));
        } catch (NumberFormatException ex) {
            AppShell.bannerError("Số tiền đã nhập không hợp lệ.");
        }
    }

    private void confirmBuyout() {
        // Xác nhận NỘI TUYẾN (banner + nút) — không Alert popup
        AppShell.ask(String.format(Locale.US,
                "MUA ĐỨT thùng hàng với giá %,.0f VNĐ? Giao dịch KHÔNG THỂ hoàn tác!", currentBuyoutPrice),
                () -> {
                    try { Client.clientHandler.write("buyout,"); }
                    catch (IOException ex) { ex.printStackTrace(); }
                },
                null);
    }

    private void exitGame() {
        AppShell.ask("Bạn có chắc chắn muốn thoát khỏi phòng đấu giá?", () -> {
            onHide();
            try {
                Client.clientHandler.write("leave-room,");
                FxUi.showLobby();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }, null);
    }

    // ── Cập nhật từ ClientHandler (đã marshal qua FxUi.runOnFx) ─────────

    public void updateRound(int round, int timeSec) {
        FxUi.runOnFx(() -> {
            roundLbl.setText("Vòng " + round + "/4");
            remainingSeconds = timeSec;
            timerLbl.setText("⏱ " + timeSec + "s");
            startTimer();
            // Vòng mới: reset lượt bid (mỗi người 1 bid/vòng)
            hasBidThisRound = false;
            bidInput.clear();
            setBidControlsDisabled(false);
            if (round >= 2) { // mỗi vòng mới: giá quay lại niêm phong
                biddersBox.getChildren().clear();
                biddersBox.getChildren().add(new Label("Chưa ai đặt giá."));
                sealedNote.setText("🔒 GIÁ NIÊM PHONG — hé lộ khi hết vòng " + round);
            }
        });
    }

    private void startTimer() {
        if (timer != null) timer.stop();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                timerLbl.setText("⏱ " + remainingSeconds + "s");
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    public void updatePrices(double est, double buy, String source) {
        FxUi.runOnFx(() -> {
            currentEstimate = est;
            currentBuyoutPrice = buy;
            buyoutFrozen = false;
            estLbl.setText("Giá ước tính: " + FxUi.vnd(est) + (source == null ? "" : "  (" + source + ")"));
            buyoutLbl.setText("Mua đứt: " + FxUi.vnd(buy));
        });
    }

    public void updateEstimate(double est) {
        FxUi.runOnFx(() -> {
            currentEstimate = est;
            estLbl.setText("Giá ước tính: " + FxUi.vnd(est));
        });
    }

    public void updateBuyoutPrice(double newBuyout) {
        FxUi.runOnFx(() -> {
            currentBuyoutPrice = newBuyout;
            buyoutLbl.setText("Mua đứt: " + FxUi.vnd(newBuyout));
        });
    }

    public void addRevealedItem(String name, String pic) {
        FxUi.runOnFx(() -> {
            // Chi xoa dong placeholder "(chua co)" — kiem tra instanceof truoc khi cast
            // (sau mon dau tien, children[0] la HBox: cast mu se nem ClassCastException
            // lam chet lambda va mat het cac mon he lo sau do).
            if (itemsBox.getChildren().size() == 1
                    && itemsBox.getChildren().get(0) instanceof Label placeholder
                    && placeholder.getText().startsWith("🎁 Vật phẩm")) {
                itemsBox.getChildren().clear();
            }
            HBox row = new HBox(10, FxUi.product(pic, 48), new Label(name));
            row.setAlignment(Pos.CENTER_LEFT);
            itemsBox.getChildren().add(row);
        });
    }

    /** Đấu giá kín: chỉ hiện ai đã đặt, KHÔNG tiền. */
    public void updateHighestBid(int pId, String nick) {
        FxUi.runOnFx(() -> {
            if (biddersBox.getChildren().size() == 1
                    && biddersBox.getChildren().get(0) instanceof Label placeholder
                    && placeholder.getText().startsWith("Chưa ai")) {
                biddersBox.getChildren().clear();
            }
            boolean me = Client.player != null && Client.player.getId() == pId;
            Label l = new Label("sealed ✓ " + (me ? "BẠN" : nick) + " đã đặt giá");
            l.getStyleClass().add("sealed-bid");
            biddersBox.getChildren().add(l);
            if (me) {
                // Bid của chính mình được chấp nhận → khóa bid tới hết vòng
                hasBidThisRound = true;
                bidInput.clear();
                setBidControlsDisabled(true);
            }
        });
    }

    /** Server từ chối bid: quota 1 lần/vòng thì giữ khóa, lỗi khác (ví...) thì mở lại. */
    public void onBidRejected(String reason) {
        FxUi.runOnFx(() -> {
            addLog("❌ Từ chối: " + reason);
            if (reason != null && reason.contains("1 lần/vòng")) {
                hasBidThisRound = true;
                setBidControlsDisabled(true);
            } else {
                if (!hasBidThisRound) setBidControlsDisabled(false);
            }
        });
    }

    private void setBidControlsDisabled(boolean disabled) {
        if (bidInput != null) bidInput.setDisable(disabled);
        if (bidBtn != null) bidBtn.setDisable(disabled);
        for (Button b : quickButtons) b.setDisable(disabled);
    }

    /** Hết vòng / hết phiên — công bố toàn bộ lượt giá. */
    public void showBidReveal(int count, String text) {
        FxUi.runOnFx(() -> {
            revealBox.getChildren().clear();
            if (count == 0) {
                revealBox.getChildren().add(new Label("(Không có lượt giá nào trong vòng này)"));
            } else {
                for (String line : text.split("\n")) {
                    Label l = new Label(line);
                    l.getStyleClass().add("reveal-line");
                    revealBox.getChildren().add(l);
                }
            }
        });
    }

    public void addLogMessage(String msg) {
        FxUi.runOnFx(() -> addLog(msg));
    }

    private void addLog(String msg) {
        if (log == null) return;
        log.appendText("• " + msg + "\n");
        log.setScrollTop(Double.MAX_VALUE);
    }

    @Override public void onHide() {
        if (timer != null) timer.stop();
    }
}

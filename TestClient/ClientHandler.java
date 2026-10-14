package TestClient;

import Server.Model.Player;
import TestClient.ViewFx.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/**
 * ClientHandler — luồng mạng client (giữ nguyên 100% logic socket + parse + tên lệnh
 * của bản Swing). Chỉ thay các lời gọi UI bằng FxUi / các View JavaFX.
 * Mọi tương tác UI đều bọc FxUi.runOnFx / Platform.runLater bên trong view.
 */
public class ClientHandler extends Thread {
    private PrintWriter output;
    private Socket clientSocket;

    // ── Cache kết quả phiên để hiện popup tổng kết ──
    // Server gửi 2 lệnh liên tiếp: auction-ended (chi tiết thùng) rồi
    // auction-result (ai thắng, giá trúng). Lưu phần 1 để gộp với phần 2.
    private String pendingUnboxDetail = "";
    private long pendingTotalReal = 0;
    private int pendingBoxCount = 0;
    private final java.util.Map<Integer, String> knownNicks = new java.util.HashMap<>();

    // ── Parse helpers (giữ nguyên) ────────────────────────────────────────
    // Format Player token: id,username,password,nickName,avatar,win,lose,draw,score,walletBalance
    public Player getPlayerFromString(int start, String[] msg) {
        double wallet = 3000000.0;
        if (msg.length > start + 9) {
            try { wallet = Double.parseDouble(msg[start + 9]); } catch (NumberFormatException ignored) {}
        }
        return new Player(
                Integer.parseInt(msg[start]),
                msg[start + 1],
                msg[start + 2],
                msg[start + 3],
                msg[start + 4],
                Integer.parseInt(msg[start + 5]),
                Integer.parseInt(msg[start + 6]),
                Integer.parseInt(msg[start + 7]),
                Double.parseDouble(msg[start + 8]),
                wallet,
                false, false
        );
    }

    public ArrayList<Player> getLeaderBoard(String[] msg) {
        ArrayList<Player> leaderBoard = new ArrayList<>();
        for (int i = 1; i + 9 < msg.length; i += 10) {
            leaderBoard.add(getPlayerFromString(i, msg));
        }
        return leaderBoard;
    }

    public ArrayList<String[]> getOnlineList(String[] msg) {
        ArrayList<String[]> onlineList = new ArrayList<>();
        for (int i = 1; i + 2 < msg.length; i += 3) {
            onlineList.add(new String[]{msg[i], msg[i + 1], msg[i + 2]});
        }
        return onlineList;
    }

    @Override
    public void run() {
        boolean connected = false;
        try {
            clientSocket = new Socket();
            clientSocket.connect(new java.net.InetSocketAddress("127.0.0.1", 7777), 5000);
            connected = true;
            System.out.println("Kết nối thành công!");
            output = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String message;

            while (true) {
                message = inputReader.readLine();
                if (message == null) break;
                final String[] m = message.split(",");
                final String cmd = m[0];

                // server-send-id,<clientNumber>
                if (cmd.equals("server-send-id")) { /* không dùng ở UI */ }

                // ── Auth ─────────────────────────────────────────────────────
                if (cmd.equals("login-success")) {
                    FxUi.runOnFx(() -> {
                        FxUi.closeAllViews();
                        Client.player = getPlayerFromString(1, m);
                        FxUi.showLobby();
                    });
                }

                if (cmd.equals("wrong-player")) {
                    FxUi.runOnFx(() -> {
                        FxUi.showLogin(m[1], m[2]);
                        if (FxUi.loginView != null)
                            FxUi.loginView.showMessage("Tài khoản hoặc mật khẩu không chính xác");
                    });
                }

                if (cmd.equals("dupplicate-login")) {
                    FxUi.runOnFx(() -> {
                        FxUi.showLogin(m[1], m[2]);
                        if (FxUi.loginView != null)
                            FxUi.loginView.showMessage("Tài khoản đã đăng nhập ở nơi khác");
                    });
                }

                if (cmd.equals("duplicate-username")) {
                    FxUi.runOnFx(() -> {
                        FxUi.showRegister();
                        FxUi.warn("Đăng ký", "Tên tài khoản đã được người khác sử dụng");
                    });
                }

                if (cmd.equals("update")) {
                    FxUi.runOnFx(() -> {
                        Client.player = getPlayerFromString(1, m);
                        if (FxUi.lobbyView != null) FxUi.lobbyView.setPlayerInformation();
                    });
                }

                // ── Lobby ────────────────────────────────────────────────────
                if (cmd.equals("return-leaderboard")) {
                    ArrayList<Player> board = getLeaderBoard(m);
                    FxUi.runOnFx(() -> {
                        if (FxUi.rankingsView != null) FxUi.rankingsView.setDataRanking(board);
                    });
                }

                if (cmd.equals("return-onlineList")) {
                    ArrayList<String[]> raw = getOnlineList(m);
                    FxUi.runOnFx(() -> {
                        if (FxUi.onlineListView != null) {
                            ArrayList<String> rows = new ArrayList<>();
                            for (String[] r : raw) rows.add(r[1] + "  (" + r[0] + ")");
                            FxUi.onlineListView.setDataOnline(rows);
                        }
                    });
                }

                if (cmd.equals("chat-server")) {
                    String line = m.length > 1 ? m[1] : "";
                    FxUi.runOnFx(() -> {
                        if (FxUi.lobbyView != null) FxUi.lobbyView.addMessage(line);
                    });
                }

                // ── Room & matchmaking ───────────────────────────────────────
                if (cmd.equals("waiting-room-update")) {
                    int current = Integer.parseInt(m[1]);
                    int max = Integer.parseInt(m[2]);
                    String tier = m[3];
                    String[] nicks = new String[Math.max(0, m.length - 4)];
                    for (int i = 4; i < m.length; i++) nicks[i - 4] = m[i];
                    FxUi.runOnFx(() -> {
                        // KHẮC PHỤC lỗ hổng client cũ: ghép phòng nhanh thành công
                        // (find-auction-room / join-room) chỉ gửi waiting-room-update →
                        // tự mở Phòng chờ, đóng màn tìm kiếm nếu đang mở.
                        if (!FxUi.isWaitingRoomShowing()) {
                            FxUi.showWaitingRoom();
                            if (FxUi.findRoomView != null) {
                                FxUi.findRoomView.showFoundRoom();
                                FxUi.findRoomView = null;
                            }
                        }
                        if (FxUi.waitingRoomView != null)
                            FxUi.waitingRoomView.updateWaitingRoom(current, max, tier, nicks);
                    });
                }

                if (cmd.equals("room-reject")) {
                    String reason = m.length > 1 ? m[1] : "Không thể vào phòng";
                    FxUi.runOnFx(() -> AppShell.bannerError("❌ Từ chối vào phòng: " + reason));
                    // Khi bị chặn khi đang ở màn tìm phòng → quay về sảnh để chọn lại
                    if (FxUi.findRoomView != null && FxUi.findRoomView.isShowing())
                        FxUi.runOnFx(() -> { FxUi.closeAllViews(); FxUi.showLobby(); });
                }

                // Lỗi vào phòng KHÔNG payload — client Swing cũ bỏ sót, bản mới xử lý
                if (cmd.equals("room-fully,".replace(",", ""))) {
                    FxUi.runOnFx(() -> AppShell.bannerWarn("Phòng đã đầy hoặc đang chơi."));
                }
                if (cmd.equals("room-not-found")) {
                    FxUi.runOnFx(() -> AppShell.bannerWarn("Không tìm thấy phòng."));
                }
                if (cmd.equals("room-wrong-password")) {
                    FxUi.runOnFx(() -> AppShell.bannerError("Mật khẩu phòng không đúng."));
                }

                if (cmd.equals("room-list")) {
                    ArrayList<String> names = new ArrayList<>();
                    ArrayList<String> passwords = new ArrayList<>();
                    int i = 1;
                    while (i + 4 < m.length) {
                        String roomId = m[i], tier = m[i + 1], cur = m[i + 2], max = m[i + 3], locked = m[i + 4];
                        String tierDisplay;
                        switch (tier) {
                            case "BINH_DAN": tierDisplay = "Bình Dân"; break;
                            case "KHA_GIA":  tierDisplay = "Khá Giả"; break;
                            case "DAI_GIA":  tierDisplay = "Đại Gia"; break;
                            default:         tierDisplay = tier;
                        }
                        names.add("Phòng " + roomId + " [" + tierDisplay + "] (" + cur + "/" + max + ")");
                        passwords.add(locked.equals("1") ? "*" : " ");
                        i += 5;
                    }
                    FxUi.runOnFx(() -> {
                        if (FxUi.roomListView != null)
                            FxUi.roomListView.updateRoomList(names, passwords);
                    });
                }

                if (cmd.equals("your-created-room")) {
                    FxUi.runOnFx(() -> {
                        FxUi.showWaitingRoom();
                        FxUi.waitingRoomView.setRoomName(m[1]);
                        if (m.length >= 3) FxUi.waitingRoomView.setRoomPassword(m[2]);
                    });
                }

                if (cmd.equals("auction-ready")) {
                    FxUi.runOnFx(() -> {
                        if (FxUi.waitingRoomView != null)
                            FxUi.waitingRoomView.showFoundCompetitor();
                    });
                }

                // ── Đấu giá ──────────────────────────────────────────────
                // auction-start,<sessionId>,<totalWeight>[,<themeKey>,<themeName>]
                // Token 4–5 MỚI: chủ đề thùng hàng (server random mỗi phiên) — thiếu → "Bí ẩn".
                if (cmd.equals("auction-start")) {
                    long sessionId = Long.parseLong(m[1]);
                    double weight = Double.parseDouble(m[2]);
                    String themeKey  = m.length >= 4 ? m[3] : null;
                    String themeName = m.length >= 5 ? m[4] : null;
                    if (themeName == null && themeKey != null) themeName = themeKey;
                    final String fKey = themeKey, fName = themeName;
                    System.out.println(String.format(
                            "[Client] Bắt đầu đấu giá: Session %d | Khối lượng: %.2f kg | Chủ đề: %s",
                            sessionId, weight, fName));
                    FxUi.runOnFx(() -> FxUi.showAuction(sessionId, weight, fKey, fName));
                }

                if (cmd.equals("estimate-update")) {
                    double est = Double.parseDouble(m[1]);
                    double buy = Double.parseDouble(m[2]);
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.updatePrices(est, buy, "Công bố giá");
                }

                if (cmd.equals("round-update")) {
                    int round = Integer.parseInt(m[1]);
                    int timeSec = Integer.parseInt(m[2]);
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.updateRound(round, timeSec);
                }

                if (cmd.equals("item-reveal")) {
                    String name = m[2];
                    String pic = m[3];
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.addRevealedItem(name, pic);
                }

                // bid-accepted,<playerId>,<nickName> — ĐẤU GIÁ KÍN: KHÔNG kèm số tiền
                if (cmd.equals("bid-accepted")) {
                    int pId = Integer.parseInt(m[1]);
                    String nick = m[2];
                    knownNicks.put(pId, nick);
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.updateHighestBid(pId, nick);
                }

                // bid-reveal,<soBid>,[<pid>,<nick>,<amount>,<round>,<bid|buyout>×soBid]
                if (cmd.equals("bid-reveal")) {
                    int n = Integer.parseInt(m[1]);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; 2 + i * 5 + 4 < m.length; i++) {
                        String pid = m[2 + i * 5];
                        String nick = m[3 + i * 5];
                        try { knownNicks.put(Integer.parseInt(pid), nick); } catch (NumberFormatException ignored) {}
                        long amt = Long.parseLong(m[4 + i * 5]);
                        String round = m[5 + i * 5];
                        String type = m[6 + i * 5];
                        sb.append(String.format("• %s (#%s): %,d VNĐ — %s, vòng %s%n",
                                nick, pid, amt, type.equals("buyout") ? "MUA ĐỨT" : "bid", round));
                    }
                    final int revealCount = n;
                    final String revealText = sb.toString();
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.showBidReveal(revealCount, revealText);
                }

                if (cmd.equals("buyout-update")) {
                    double newBuyout = Double.parseDouble(m[1]);
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.updateBuyoutPrice(newBuyout);
                }

                if (cmd.equals("random-event")) {
                    String eventType = m[1];
                    if (eventType.equals("EXTRA_REVEAL") || eventType.equals("HEAVY_REVEAL")) {
                        String name = m.length > 3 ? m[3] : "";
                        String pic = m.length > 4 ? m[4] : "none";
                        boolean heavy = eventType.equals("HEAVY_REVEAL");
                        if (FxUi.auctionView != null) {
                            FxUi.auctionView.addRevealedItem(
                                    (heavy ? "🏋️ [Nặng nhất] " : "⭐ [Hé lộ thêm] ") + name, pic);
                            if (m.length > 6) {
                                try {
                                    FxUi.auctionView.updatePrices(Double.parseDouble(m[5]), Double.parseDouble(m[6]),
                                            heavy ? "🏋️ Hé lộ món nặng nhất" : "⭐ Hé lộ ngẫu nhiên");
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    } else if (eventType.equals("BUYOUT_FREEZE")) {
                        double frozen = Double.parseDouble(m[2]);
                        if (FxUi.auctionView != null) {
                            FxUi.auctionView.updateBuyoutPrice(frozen);
                            FxUi.auctionView.addLogMessage("🧊 ĐÓNG BĂNG GIÁ MUA ĐỨT đến hết vòng — mua ngay hoặc chờ rủi ro!");
                        }
                    } else if (eventType.equals("FLASH_SALE")) {
                        double newBuyout = Double.parseDouble(m[2]);
                        if (FxUi.auctionView != null) {
                            FxUi.auctionView.updateBuyoutPrice(newBuyout);
                            FxUi.auctionView.addLogMessage("⚡ FLASH SALE! Giá mua đứt giảm 30% trong 10 giây — hết 10s GIỮ mức này!");
                        }
                    } else if (eventType.equals("FLASH_SALE_END")) {
                        double keptPrice = Double.parseDouble(m[2]);
                        if (FxUi.auctionView != null) {
                            FxUi.auctionView.addLogMessage(
                                    String.format("⚡ Flash Sale kết thúc — giá mua đứt giữ ở mức %,.0f VNĐ", keptPrice));
                        }
                    } else if (eventType.equals("APPRAISER")) {
                        double avg = Double.parseDouble(m[2]);
                        double newEstimate = Double.parseDouble(m[3]);
                        if (FxUi.auctionView != null) {
                            FxUi.auctionView.addLogMessage(String.format(
                                    "🔍 THẨM ĐỊNH: giá TB mỗi món %,.0f VNĐ | ước tính mới %,.0f VNĐ", avg, newEstimate));
                            if (m.length > 4) {
                                try {
                                    FxUi.auctionView.updatePrices(newEstimate, Double.parseDouble(m[4]), "🔍 Thẩm định");
                                } catch (NumberFormatException ex) {
                                    FxUi.auctionView.updateEstimate(newEstimate);
                                }
                            } else {
                                FxUi.auctionView.updateEstimate(newEstimate);
                            }
                        }
                    }
                }

                if (cmd.equals("bid-rejected")) {
                    String reason = m.length > 1 ? m[1] : "";
                    if (FxUi.auctionView != null)
                        FxUi.auctionView.onBidRejected(reason);
                    FxUi.runOnFx(() -> AppShell.bannerError("❌ Bid bị từ chối: " + reason));
                }

                // auction-ended,<winnerId>,<winPrice>,<byBuyout>,<newWallet>,<soMon>,
                //   [<productId>,<name>,<picture>,<realValue>,<weight>×soMon]
                // → chỉ LƯU lại, popup tổng kết sẽ hiện khi auction-result tới.
                if (cmd.equals("auction-ended")) {
                    int boxCount = Integer.parseInt(m[5]);
                    StringBuilder sb = new StringBuilder();
                    double totalReal = 0;
                    int idx = 6;
                    for (int i = 0; i < boxCount && idx + 5 <= m.length; i++) {
                        String name = m[idx + 1];
                        long realValue = Long.parseLong(m[idx + 3]);
                        double w = Double.parseDouble(m[idx + 4]);
                        totalReal += realValue;
                        sb.append(String.format("🎁 %s — %,d VNĐ (%.2f kg)%n", name, realValue, w));
                        idx += 5;
                    }
                    pendingUnboxDetail = sb.length() > 0 ? sb.toString() : "(Không có thông tin vật phẩm)";
                    pendingTotalReal = (long) totalReal;
                    pendingBoxCount = boxCount;
                }

                // auction-result,<winnerId>,<winPrice>,<endedByBuyout>,<newWallet>
                // winnerId = -1 → phiên bị HUỶ (không ai bid vượt ước tính cuối)
                // → hiện POPUP tổng kết: ai mua / giá bao nhiêu / gồm vật phẩm gì / giá thực tế.
                if (cmd.equals("auction-result")) {
                    int winnerId = Integer.parseInt(m[1]);
                    long winPrice = Long.parseLong(m[2]);
                    boolean byBuyout = "1".equals(m[3]);
                    long newWallet = m.length > 4 ? Long.parseLong(m[4]) : 0;
                    int meId = Client.player != null ? Client.player.getId() : -1;
                    if (winnerId > 0 && winnerId == meId && Client.player != null) {
                        Client.player.setWalletBalance(newWallet);
                    }
                    final String unboxDetail = pendingUnboxDetail;
                    final long totalRealFinal = pendingTotalReal;
                    final int boxCountFinal = pendingBoxCount;
                    pendingUnboxDetail = "";
                    pendingTotalReal = 0;
                    pendingBoxCount = 0;
                    FxUi.runOnFx(() -> {
                        showResultPopup(winnerId, meId, winPrice, byBuyout, newWallet,
                                unboxDetail, totalRealFinal, boxCountFinal);
                        try { write("update,"); } catch (IOException ignored) {}
                        FxUi.closeAllViews();
                        FxUi.showLobby();
                    });
                }

                // win | lose | draw (legacy)
                if (cmd.equals("win") || cmd.equals("lose") || cmd.equals("draw")) {
                    String kind = cmd;
                    FxUi.runOnFx(() -> {
                        if (FxUi.auctionView != null) FxUi.auctionView.onHide();
                        switch (kind) {
                            case "win" -> AppShell.bannerSuccess("Bạn là người chiến thắng! 🎉");
                            case "lose" -> AppShell.bannerWarn("Bạn đã thua! Cố lên lần sau nhé.");
                            default -> AppShell.bannerInfo("Hoà! Hai bên ngang tài ngang sức.");
                        }
                    });
                    try { write("update,"); } catch (IOException ignored) {}
                    FxUi.runOnFx(() -> { FxUi.closeAllViews(); FxUi.showLobby(); });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Client.showConnectionLost(connected);
        }
    }

    public void write(String message) throws IOException {
        if (output == null) throw new IOException("Chưa kết nối server");
        output.write(message);
        output.println();
        output.flush();
    }

    /**
     * POPUP tổng kết phiên: ai mua — giá bao nhiêu — gồm vật phẩm gì — giá thực tế.
     * Chạy trên FX thread, modal theo cửa sổ chính. Bấm OK mới về sảnh.
     */
    private void showResultPopup(int winnerId, int meId, long winPrice, boolean byBuyout,
                                 long newWallet, String unboxDetail, long totalReal, int boxCount) {
        javafx.scene.control.Alert alert;
        if (winnerId == -1) {
            alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Phiên bị huỷ");
            alert.setHeaderText("Phiên đấu giá bị HUỶ — không có bid nào vượt giá ước tính cuối.\nKhông ai mất tiền.");
        } else {
            boolean meWin = (winnerId == meId);
            String nick = knownNicks.getOrDefault(winnerId, "#" + winnerId);
            if (meWin && Client.player != null && Client.player.getNickName() != null) {
                nick = Client.player.getNickName() + " (BẠN)";
            } else if (meWin) {
                nick = "BẠN";
            }
            alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle(meWin ? "🎉 Bạn đã thắng!" : "Kết quả đấu giá");
            long profit = totalReal - winPrice;
            String profitLine = profit >= 0
                    ? String.format("Lãi: +%,d VNĐ", profit)
                    : String.format("Lỗ: %,d VNĐ", profit);
            alert.setHeaderText(String.format(
                    "🏆 Người mua: %s (#%d)\n💰 Giá trúng: %,d VNĐ (%s)\n📦 Giá trị thực thùng (%d món): %,d VNĐ\n%s",
                    nick, winnerId, winPrice, byBuyout ? "MUA ĐỨT" : "ĐẤU GIÁ",
                    boxCount, totalReal, profitLine));
        }

        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(
                (unboxDetail == null || unboxDetail.isBlank()) ? "(Không có thông tin vật phẩm)" : unboxDetail.trim()
                        + String.format("%n%n💰 Tổng giá trị thực: %,d VNĐ", totalReal));
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(420, 220);
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().setPrefWidth(480);
        if (FxUi.getPrimaryStage() != null) alert.initOwner(FxUi.getPrimaryStage());
        alert.showAndWait();
    }

    public Socket getClientSocket() {
        return clientSocket;
    }
}

package Server.Controller;

import Server.DAO.PlayerDAO;
import Server.Model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BotManager — "Đấu thử với máy": 1 người chơi thật + 3 máy tự động trong 1 phòng.
 *
 * NGUYÊN TẮC KIẾN TRÚC (quan trọng cho đồ án):
 *  - Bot KHÔNG tự gọi AuctionEngine.onBid/onBuyout trực tiếp. Bot enqueue RequestObject
 *    vào GameRequestQueue của phòng — ĐÚNG HÀNG ĐỢI FIFO mà người thật dùng (mục 4).
 *    → Bot cạnh tranh với người ở CÙNG pipeline: cùng bị chặn bởi lock worker duy nhất,
 *      cùng bị từ chối khi phiên kết thúc, kiểm chứng kiến trúc Message Queue hoạt động
 *      với mọi loại "người chơi".
 *  - Bot là ServerThread ẢO (constructor không socket): mọi broadcast tới bot chỉ là
 *    log console server; write() đã guard isBot.
 *  - Bot mượn tài khoản có sẵn trong DB (bot001/bot002/bot003 — seed migration) nên
 *    kiểm tra ví khi bid/buyout, trừ tiền người thắng... chạy nguyên quy tắc game.
 *
 * NÃO BOT (tham khảo chiến thuật):
 *  ĐẤU GIÁ KÍN THEO VÒNG (đã chốt lần 2): giá bị ẨN TRONG vòng; hết vòng server
 *  công bố tất cả → bot đọc giá đối thủ từ bidLog và dùng làm mạch đè giá vòng sau.
 *  Được đặt bất kỳ số tiền nào (không cần cao hơn mức dẫn đầu) → bot tự đặt theo
 *  định giá riêng quanh P_est, 75% khả năng đè nhẹ lên giá cao nhất vừa được công bố.
 *  V1: thăm dò nhẹ — 55% bid quanh [P_est×0.45, P_est×0.70], không đụng buyout.
 *  V2: tích cực — 70% bid quanh P_est, 8% mua đứt sớm nếu P_buyout < ví × 0.6.
 *  V3: quyết liệt — 80% bid thiên về định giá riêng, 15% mua đứt nếu P_buyout < ví × 0.75.
 *  V4: giành giật — 90% bid, 25% mua đứt nếu P_buyout < giá trị tự định giá × 1.05.
 *  Bot vẫn chấp nhận bị từ chối như người thật (hết ví / phiên kết thúc) — hành vi đáng quan sát khi test.
 */
public class BotManager {

    /** Số bot mỗi phòng "đấu thử với máy": 1 người thật + 2 máy = 3 người (đúng MAX_PLAYERS).
     *  LƯU Ý: nếu MAX_PLAYERS đổi lên 4 thì cần BOT_COUNT = 3 để vừa đủ phòng. */
    public static final int BOT_COUNT = 2;

    private final Room room;
    private final PlayerDAO playerDAO;
    private final List<Bot> bots = new ArrayList<>();
    private final Random random = new Random();

    // Giới hạn tần suất bot: mỗi bot chỉ 1 bid/vòng (công bằng với người).
    private static final int MAX_BIDS_PER_ROUND = 1;
    // Cờ tắt/mở mua đứt của bot — false = bot KHÔNG bao giờ mua đứt (chỉ bid).
    private static final boolean ALLOW_BOT_BUYOUT = false;
    private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> bidsThisRound = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int rateLimitRound = -1;

    // ── Trạng thái phiên mà bot quan sát (được engine đẩy vào qua callback) ──
    private volatile double currentEstimate = 0;
    private volatile double currentBuyout = 0;
    private volatile double totalWeight = 0;
    private volatile int currentRound = 0;

    private BotManager(Room room) {
        this.room = room;
        this.playerDAO = new PlayerDAO();
    }

    // ═════════════════════════════════════════════════════════════════════
    // SPAWN / STOP
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Mượn tối đa BOT_COUNT tài khoản bot từ DB, tạo ServerThread ảo và addPlayer
     * vào phòng như người thật. Trả về số bot đã vào được phòng (0 = DB chưa seed bot).
     */
    public static int spawnBotsInto(Room room) {
        PlayerDAO dao = new PlayerDAO();
        List<Player> all = dao.getAllPlayers();
        if (all == null) return 0;

        List<Player> botAccounts = new ArrayList<>();
        for (Player p : all) {
            if (p.getUsername() != null && p.getUsername().startsWith("bot")) botAccounts.add(p);
            if (botAccounts.size() >= BOT_COUNT) break;
        }
        if (botAccounts.isEmpty()) return 0;

        BotManager manager = new BotManager(room);
        int added = 0;
        for (Player acc : botAccounts) {
            if (room.isFull()) break;
            ServerThread botThread = new ServerThread(9000 + acc.getId()); // ID dải riêng, không trùng clientNumber người thật
            botThread.setBotIdentity(acc);
            if (!room.addPlayer(botThread)) break;
            dao.updateToPlaying(acc.getId());
            manager.bots.add(new Bot(manager, botThread));
            added++;
        }
        if (added > 0) {
            room.attachBotManager(manager);
            room.broadcastWaitingRoomUpdate(); // Người thật thấy slot "🤖 Máy ..." trong phòng chờ
            System.out.println("[BotManager] Room " + room.getId() + ": " + added + " bot đã vào phòng");
        }
        return added;
    }

    /** Dọn dẹp: bot rời trạng thái playing + bỏ tham chiếu (gọi khi phiên kết thúc). */
    public void releaseBots() {
        for (Bot b : bots) {
            Player p = b.thread.getPlayer();
            if (p != null) playerDAO.updateToNotPlaying(p.getId());
            b.thread.setRoom(null);
        }
        System.out.println("[BotManager] Room " + room.getId() + ": giải phóng " + bots.size() + " bot");
        bots.clear();
        room.detachBotManager();
    }

    // ═════════════════════════════════════════════════════════════════════
    // CALLBACK TỪ AUCTIONENGINE (engine gọi khi có sự kiện — bot nhìn cùng thông tin người thật)
    // ═════════════════════════════════════════════════════════════════════

    public void onRoundUpdate(int round, int timeLeftSec) {
        this.currentRound = round;
        if (round == 1) {
            // Mồi: tất cả bot chờ ngẫu nhiên 3–12 giây rồi mới hành động lần đầu
            for (Bot b : bots) b.scheduleFirstAction();
        }
    }

    public void onEstimateUpdate(double estimate, double buyout) {
        this.currentEstimate = estimate;
        this.currentBuyout = buyout;
        this.totalWeight = 0; // không dùng trực tiếp
        // Giá vừa được công bố lại → mỗi bot có thể hành động lại ở vòng hiện tại
        for (Bot b : bots) b.scheduleAction(2000 + random.nextInt(6000));
    }

    public void onItemRevealed(long productId, String name) {
        // Hé lộ làm thay đổi nhận định — bot có thể phản ứng (đã bắt qua estimate-update),
        // thêm hành động phụ để phòng khi estimate-update đến trước callback này.
        for (Bot b : bots) b.scheduleAction(1500 + random.nextInt(5000));
    }

    public void onBidAccepted(int playerId) {
        // Đấu giá kín: bot KHÔNG biết số tiền đối thủ đặt — chỉ biết CÓ NGƯỜI vừa đặt giá
        // → cân nhắc tự đặt theo định giá riêng (trừ bot vừa đặt).
        for (Bot b : bots) {
            Player p = b.thread.getPlayer();
            if (p != null && p.getId() != playerId) b.scheduleAction(1200 + random.nextInt(4500));
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // MỘT CON BOT
    // ═════════════════════════════════════════════════════════════════════

    private static class Bot {
        final BotManager manager;
        final ServerThread thread;
        final String personality;   // "chăm chỉ" / "săn deal" / "cọc cời" — mod tỷ lệ
        final Random rnd;

        Bot(BotManager manager, ServerThread thread) {
            this.manager = manager;
            this.thread = thread;
            this.personality = thread.getPlayer().getUsername();
            this.rnd = new Random(thread.getPlayer().getId());
        }

        String nickname() {
            Player p = thread.getPlayer();
            return p != null && p.getNickName() != null ? p.getNickName() : "Bot";
        }

        void scheduleFirstAction() { scheduleAction(3000 + rnd.nextInt(9000)); }

        /** Lên lịch hành động sau delayMs — bản chất bot vẫn là "client" bấm nút trễ nất. */
        void scheduleAction(int delayMs) {
            if (delayMs <= 0) return;
            new Thread(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { return; }
                manager.act(this);
            }, "bot-brain-" + thread.getClientNumber()).start();
        }

        /** Tỷ lệ hành động theo vòng × tính cách. */
        double bidProbability(int round) {
            double base;
            switch (Math.max(1, Math.min(4, round))) {
                case 1:  base = 0.55; break;
                case 2:  base = 0.70; break;
                case 3:  base = 0.80; break;
                default: base = 0.90; break;
            }
            if ("bot003".equals(personality)) base += 0.08;  // "cọc cời" hơi liều hơn
            if ("bot002".equals(personality)) base -= 0.05;  // "săn deal" kiên nhẫn hơn
            return Math.min(0.95, Math.max(0.1, base));
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // NÃO — RA QUYẾT ĐỊNH RỒI ENQUEUE VÀO HÀNG ĐỢI FIFO (đúng pipeline người)
    // ═════════════════════════════════════════════════════════════════════

    private void act(Bot bot) {
        Player me = bot.thread.getPlayer();
        if (me == null) return;
        AuctionEngine engine = room.getAuctionEngine();
        if (engine == null || engine.getState() == null || engine.getState().finished) return;
        int round = currentRound;
        if (round < 1) return;

        double est = currentEstimate;
        double buyout = currentBuyout;
        if (est <= 0) return;

        double wallet = playerDAO.getWalletBalance(me.getId());
        double selfValue = selfAppraisal(bot, est); // "định giá riêng" của bot quanh P_est

        // ── Quyết định MUA ĐỨT (ĐÃ TẮT: ALLOW_BOT_BUYOUT = false → bot chỉ bid, không mua đứt) ──
        double buyoutThreshold;
        switch (round) {
            case 1:  buyoutThreshold = wallet * 0.35; break; // V1 gần như không bao giờ mua đứt
            case 2:  buyoutThreshold = wallet * 0.60; break;
            case 3:  buyoutThreshold = wallet * 0.75; break;
            default: buyoutThreshold = selfValue * 1.05; break; // V4: giành giật khi rẻ hơn định giá riêng
        }
        boolean buyoutUrge;
        switch (bot.personality) {
            case "bot002": buyoutUrge = bot.rnd.nextDouble() < 0.05; break;  // săn deal: hiếm khi chốt sớm
            case "bot003": buyoutUrge = bot.rnd.nextDouble() < 0.18; break;  // cọc cời: dễ chốt hơn
            default:       buyoutUrge = bot.rnd.nextDouble() < 0.10; break;
        }
        if (ALLOW_BOT_BUYOUT && buyout > 0 && buyout <= buyoutThreshold && buyoutUrge) {
            System.out.println("[BotManager] " + bot.nickname() + " quyết định MUA ĐỨT " + (long) buyout);
            enqueueBuyout(engine, bot);
            return;
        }

        // ── Quyết định BID thăm dò / đè giá ──
        // Reset bộ đếm khi sang vòng mới + giới hạn số lượt bid mỗi bot mỗi vòng
        if (rateLimitRound != round) {
            bidsThisRound.clear();
            rateLimitRound = round;
        }
        if (bidsThisRound.merge(me.getId(), 1, Integer::sum) > MAX_BIDS_PER_ROUND) {
            return; // Bot "đám đông" — người thật muốn bid bao nhiêu lượt tùy ý
        }
        if (bot.rnd.nextDouble() > bot.bidProbability(round)) return; // Lần này "ngồi im"

        // Đấu giá kín theo vòng: hết vòng trước server đã công bố giá mọi người →
        // bot đọc giá cao nhất của ĐỐI THỦ từ nhật ký bidLog để tính mức đè.
        double minRival = 0;
        AuctionRoomState st = engine.getState();
        if (st != null) {
            for (AuctionRoomState.BidEntry e : st.bidLog) {
                if (e.playerId != me.getId() && e.amount > minRival) minRival = e.amount;
            }
        }
        double bid = est * (0.45 + bot.rnd.nextDouble() * 0.25);
        if (round >= 3) bid = Math.max(bid, selfValue * (0.9 + bot.rnd.nextDouble() * 0.15));
        // 75%: đè nhẹ lên giá cao nhất vừa được công bố (+1–6%) — 25% giữ ý riêng
        if (minRival > 0 && bot.rnd.nextDouble() < 0.75) {
            bid = Math.max(bid, minRival * (1.0 + (0.01 + bot.rnd.nextDouble() * 0.05)));
        }
        bid = Math.min(bid, Math.min(wallet, selfValue * 1.1));
        bid = roundToThousand(bid);

        if (bid <= 0 || bid > wallet) return; // Hết ví → ngừng chơi (giống người thật cạn tiền)
        System.out.println("[BotManager] " + bot.nickname() + " bid " + (long) bid
                + " (V" + round + ", est=" + (long) est + ")");
        enqueueBid(engine, bot, bid);
    }

    /** Định giá riêng của bot (bot002 thận trọng ×0.85, bot003 hào hứng ×1.05). */
    private double selfAppraisal(Bot bot, double estimate) {
        if (bot == null) return estimate;
        if ("bot002".equals(bot.personality)) return estimate * 0.85;
        if ("bot003".equals(bot.personality)) return estimate * 1.05;
        return estimate;
    }

    private double roundToThousand(double v) {
        return Math.round(v / 1000.0) * 1000.0;
    }

    // ── Enqueue — ĐI QUA MESSAGE QUEUE như người thật (mục 4) ─────────────
    private void enqueueBid(AuctionEngine engine, Bot bot, double amount) {
        // Luật mới: đặt lại giá của chính mình (cao hơn) vẫn hợp lệ — không cần guard
        engine.getRequestQueue().submit(new RequestObject(RequestObject.Type.BID, bot.thread, amount));
    }

    private void enqueueBuyout(AuctionEngine engine, Bot bot) {
        engine.getRequestQueue().submit(new RequestObject(RequestObject.Type.BUYOUT, bot.thread, 0));
    }
}

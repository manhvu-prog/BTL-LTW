package Server.Controller;

import Server.DAO.AuctionSessionDAO;
import Server.DAO.BidHistoryDAO;
import Server.DAO.PlayerDAO;
import Server.DAO.ProductDAO;
import Server.Model.AuctionSession;
import Server.Model.BidHistory;
import Server.Model.Player;
import Server.Model.Product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AuctionEngine — điều phối toàn bộ State Machine 4 vòng đấu giá.
 *
 * Định giá (mục 3 của thiết kế — "cú lừa tâm lý"):
 *  - P_est    = (ΣV_revealed / ΣW_revealed) × W_total × K_round, tính lại SAU MỖI lần hé lộ
 *  - P_buyout = P_est × M_buyout, M_buyout RANDOM trong khoảng của vòng hiện tại
 *               (V1 2.5–3.0 "vùng cấm địa" → V4 1.2–1.3 "phí bảo hiểm chốt hạ")
 *  - Buyout KHÔNG giảm theo giây — chỉ thay đổi khi hé lộ vật phẩm (P_est đổi)
 *               hoặc chuyển vòng (M_buyout random lại)
 *  - Vòng 1: Server BẮT BUỘC hé lộ 1 vật phẩm "mồi nhử" ngẫu nhiên (tránh chia 0)
 *
 * Sự kiện ngẫu nhiên: kích hoạt ở VÒNG 3 (giây 15), mỗi phiên 1 lần, 1 trong 5 loại:
 *  EXTRA_REVEAL  — hé lộ ngẫu nhiên 1 món ẩn (biến động P_est không lường trước)
 *  HEAVY_REVEAL  — hé lộ món NẶNG NHẤT còn ẩn (findMaxWeightItem) — manh mối loại trừ "rác nặng rẻ"
 *  APPRAISER     — hé giá trị trung bình 1 vật phẩm (nhiễu ±5%) để tự kết hợp khối lượng mà suy luận
 *  FLASH_SALE    — giảm 30% buyout trong 10s (hết 10s GIỮ mức sau giảm — đã chốt)
 *  BUYOUT_FREEZE — khoá cứng giá mua đứt đến hết vòng, ép mua ngay hoặc chờ rủi ro vòng sau
 *
 * Kết thúc: hết vòng 4 → người giữ BID cao nhất thắng NẾU bid đó vượt giá ước tính
 * cuối; ngược lại HUỶ PHIÊN (không ai thắng, không ai mất tiền) — đã chốt. BUYOUT
 * ghi đè mọi BID ở mọi vòng và kết thúc phiên NGAY LẬP TỨC.
 *
 * Kiến trúc đồng thời (mục 4 — novelty): lệnh BID/BUYOUT/start-auction từ client đi
 * qua GameRequestQueue (FIFO per-room, 1 worker duy nhất). Worker xử lý tuần tự →
 * 2 lệnh cùng mili-giây được xử lý theo THỨ TỰ XẾP HÀNG, không thể 2 người cùng
 * mua thành công một thùng. synchronized(state) giữ lại làm lớp bảo vệ thứ hai cho
 * các mutation từ timer vòng/sự kiện.
 *
 * endAuction dùng CAS trên state.finished → chỉ 1 luồng kết thúc phiên (chống
 * double-end / double-deduct). scheduler.shutdownNow() khi phiên kết thúc → không
 * leak thread nền.
 */
public class AuctionEngine {

    // ── Dependencies ──────────────────────────────────────────────────────
    private final Room room;
    private final ProductDAO productDAO;
    private final PlayerDAO playerDAO;
    private final AuctionSessionDAO auctionSessionDAO;
    private final BidHistoryDAO bidHistoryDAO;
    private final Random random = new Random();

    // ── State ─────────────────────────────────────────────────────────────
    private AuctionRoomState state;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> flashSaleRevertFuture;
    private final GameRequestQueue requestQueue; // Hàng đợi thông điệp per-room (mục 4)

    // ── Constructor ───────────────────────────────────────────────────────
    public AuctionEngine(Room room) {
        this.room = room;
        this.productDAO = new ProductDAO();
        this.playerDAO = new PlayerDAO();
        this.auctionSessionDAO = new AuctionSessionDAO();
        this.bidHistoryDAO = new BidHistoryDAO();
        this.requestQueue = new GameRequestQueue(room);
    }

    // ═════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Khởi tạo phiên: random 4–6 vật phẩm, tổng giá trị/khối lượng giữ BÍ MẬT trong RAM,
     * lưu DB, khởi động hàng đợi lệnh (worker) và bắt đầu vòng 1.
     */
    public void startAuction() {
        int count = GameConfig.MIN_ITEMS_PER_BOX
                + random.nextInt(GameConfig.MAX_ITEMS_PER_BOX - GameConfig.MIN_ITEMS_PER_BOX + 1);
        // ── CHỦ ĐỀ (đã chốt): server random 1 chủ đề mỗi phiên, thùng chỉ gồm món của chủ đề đó ──
        String[] themes = GameConfig.BOX_THEMES;
        String themeKey = themes[random.nextInt(themes.length)];

        // ── TRẦN THÙNG theo hạng phòng ("giá không vượt mức phòng" — đã chốt):
        // tổng realValue thùng ≤ cap; P_est/P_buyout sẽ bị clamp ≤ cap ở revealItemsForRound.
        double cap = (room.getTier() == Room.RoomTier.DAI_GIA)
                ? GameConfig.BOX_CAP_DAI_GIA : GameConfig.BOX_CAP_BINH_DAN;
        List<Product> items = productDAO.getRandomProducts(count, cap, themeKey);
        if (items == null || items.isEmpty()) {
            room.broadCast("auction-error,Không đủ vật phẩm trong database để tạo thùng hàng");
            room.setStatus(Room.RoomStatus.WAITING);
            return;
        }

        double totalReal   = items.stream().mapToDouble(Product::getRealValue).sum();
        double totalWeight = items.stream().mapToDouble(Product::getWeight).sum();

        state = new AuctionRoomState(items, totalReal, totalWeight);
        state.theme = themeKey;

        AuctionSession session = new AuctionSession();
        session.setRoomId(room.getId());
        session.setRoomTier(room.getTier().name());
        session.setTheme(themeKey);
        session.setTotalRealValue(totalReal);
        session.setTotalWeight(totalWeight);
        session.setInitialEstimate(0); // P_est thật sự được tính sau khi hé mồi nhử V1
        state.dbSessionId = auctionSessionDAO.createAuctionSession(session, items);

        room.setStatus(Room.RoomStatus.IN_PROGRESS);

        // Khởi động hàng đợi lệnh game (mục 4) — worker xử lý BID/BUYOUT FIFO
        requestQueue.start();

        // Format: auction-start,<sessionId>,<totalWeight>[,<themeKey>,<themeName>]
        // Token 4–5 MỚI: chủ đề thùng hàng — client cũ bỏ qua (tương thích ngược).
        // KHÔNG gửi totalRealValue hay danh sách vật phẩm — chống gian lận.
        // (P_est/P_buyout gửi riêng qua estimate-update sau khi tính trong vòng 1.)
        room.broadCast(String.format("auction-start,%d,%.2f,%s,%s",
                state.dbSessionId, totalWeight, themeKey, ItemsThemeHelper.displayName(themeKey)));

        System.out.println("[AuctionEngine] Room " + room.getId() + " | Chủ đề: " + ItemsThemeHelper.displayName(themeKey)
                + " | " + count + " món | Tổng giá trị thực: "
                + (long) totalReal + " (BÍ MẬT, cap " + (long) cap + ") | Tổng khối lượng: " + totalWeight);

        scheduler = Executors.newScheduledThreadPool(2);
        nextRound();
    }

    /**
     * Chuyển sang vòng tiếp theo, hé lộ vật phẩm theo quy tắc, tính lại P_est/P_buyout,
     * hẹn sự kiện ngẫu nhiên (V3) và chuyển vòng theo giờ.
     */
    private void nextRound() {
        // Hết vòng → CÔNG BỐ GIÁ (đấu giá kín — đã chốt lần 2): mọi người thấy toàn bộ
        // giá đã đặt của các vòng trước trước khi vòng mới bắt đầu.
        synchronized (state) {
            if (state.finished) return;
            if (state.currentRound >= GameConfig.TOTAL_ROUNDS) {  // Hết vòng 4 → chốt
                endAuctionLocked("ROUND_COMPLETE");
                return;
            }
            if (!state.bidLog.isEmpty()) broadcastBidRevealLocked();
            state.currentRound++;
            // Mở băng giá mua đứt khi sang vòng mới (sự kiện FREEZE chỉ có hiệu lực trong vòng nó nổ)
            state.isBuyoutFrozen = false;
        }

        int round = state.currentRound;
        System.out.println("[AuctionEngine] Room " + room.getId() + " — Vòng " + round + " bắt đầu");

        // Format: round-update,<round>,<timeLeftSec>
        room.broadCast("round-update," + round + "," + GameConfig.ROUND_DURATION_SEC);
        if (room.getBotManager() != null) room.getBotManager().onRoundUpdate(round, GameConfig.ROUND_DURATION_SEC);
        revealItemsForRound(round); // hé lộ trước, rồi tính P_est/P_buyout trên tập đã lộ

        // Sự kiện ngẫu nhiên (thiết kế mục 2): kích hoạt ở VÒNG 3, giây 15, mỗi phiên 1 lần,
        // 1 trong 5 loại (EXTRA_REVEAL / HEAVY_REVEAL / APPRAISER / FLASH_SALE / BUYOUT_FREEZE)
        if (!state.randomEventFired && round == 3) {
            state.randomEventFired = true;
            scheduler.schedule(this::triggerRandomEvent, GameConfig.RANDOM_EVENT_AT_SEC, TimeUnit.SECONDS);
        }

        // Chuyển vòng / kết thúc sau ROUND_DURATION_SEC
        scheduler.schedule(() -> {
            try {
                nextRound();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, GameConfig.ROUND_DURATION_SEC, TimeUnit.SECONDS);
    }

    /**
     * Hé lộ vật phẩm theo vòng (thiết kế mục 2):
     *  V1: 1 món "mồi nhử" ngẫu nhiên (BẮT BUỘC — tránh chia 0 trong công thức P_est)
     *  V2: +1 món
     *  V3: +1 món
     *  V4: hé hết phần còn lại trừ 1–2 món cuối (duy trì rủi ro & bất ngờ)
     */
    private void revealItemsForRound(int round) {
        List<Product> toReveal = new ArrayList<>();
        synchronized (state) {
            if (state.finished) return;
            switch (round) {
                case 1:
                    // "Mồi nhử": chọn NGẪU NHIÊN 1 món trong thùng (không phải luôn món đầu)
                    Product bait = state.hiddenItems.remove(random.nextInt(state.hiddenItems.size()));
                    toReveal.add(bait);
                    break;
                case 2:
                case 3:
                    if (!state.hiddenItems.isEmpty()) {
                        toReveal.add(state.hiddenItems.remove(random.nextInt(state.hiddenItems.size())));
                    }
                    break;
                case 4:
                    int keepHidden = Math.min(GameConfig.MAX_ITEMS_LEFT_HIDDEN, state.hiddenItems.size());
                    while (state.hiddenItems.size() > keepHidden) {
                        toReveal.add(state.hiddenItems.remove(random.nextInt(state.hiddenItems.size())));
                    }
                    break;
            }
            for (Product p : toReveal) {
                state.revealedItems.add(p);
                if (state.dbSessionId > 0) {
                    auctionSessionDAO.updateItemRevealRound(state.dbSessionId, p.getId(), round);
                }
            }

            // ── Công thức mục 3: tính lại P_est và P_buyout sau mỗi lần hé lộ ──
            double sumV = state.revealedItems.stream().mapToDouble(Product::getRealValue).sum();
            double sumW = state.revealedItems.stream().mapToDouble(Product::getWeight).sum();
            double pricePerKg = (sumW > 0) ? (sumV / sumW) : 0;
            int kIdx = Math.min(round, GameConfig.K_ROUND.length) - 1;
            state.currentEstimate = pricePerKg * state.totalWeight * GameConfig.K_ROUND[kIdx];

            // ── TRẦN GIÁ theo hạng phòng (đã chốt): P_est không được vượt trần thùng ──
            double cap = (room.getTier() == Room.RoomTier.DAI_GIA)
                    ? GameConfig.BOX_CAP_DAI_GIA : GameConfig.BOX_CAP_BINH_DAN;
            state.currentEstimate = Math.min(state.currentEstimate, cap);

            // M_buyout random trong khoảng của vòng hiện tại (đã chốt: random trong khoảng) —
            // trừ khi giá đang bị ĐÓNG BĂNG (sự kiện BUYOUT_FREEZE): giữ nguyên mức cũ.
            double[] mRange = GameConfig.M_BUYOUT_RANGE[kIdx];
            double mBuyout = mRange[0] + random.nextDouble() * (mRange[1] - mRange[0]);
            state.currentBuyoutPrice = state.isBuyoutFrozen
                    ? state.currentBuyoutPrice
                    : state.currentEstimate * mBuyout;
            // ── TRẦN GIÁ (đã chốt): giá MUA ĐỨT cũng không vượt trần thùng ──
            state.currentBuyoutPrice = Math.min(state.currentBuyoutPrice, cap);

            // Format: estimate-update,<P_est>,<P_buyout>
            room.broadCast("estimate-update," + String.format("%.0f", state.currentEstimate)
                    + "," + String.format("%.0f", state.currentBuyoutPrice));
            // P_est vòng 1 là "định giá ban đầu" chính thức của phiên — lưu lại vào DB
            if (round == 1 && state.dbSessionId > 0) {
                auctionSessionDAO.updateInitialEstimate(state.dbSessionId, state.currentEstimate);
            }
            if (room.getBotManager() != null) {
                room.getBotManager().onEstimateUpdate(state.currentEstimate, state.currentBuyoutPrice);
            }
            System.out.println("[AuctionEngine] P_est=" + (long) state.currentEstimate
                    + " | P_buyout=" + (long) state.currentBuyoutPrice
                    + " (M=" + String.format("%.2f", mBuyout) + ")");
        }
        // Broadcast item-reveal NGOÀI lock (socket I/O không nên giữ lock state)
        for (Product p : toReveal) {
            String picture = p.getPicture() != null ? p.getPicture() : "none";
            // Format: item-reveal,<productId>,<name>,<picture>
            room.broadCast("item-reveal," + p.getId() + "," + p.getName() + "," + picture);
            if (room.getBotManager() != null) room.getBotManager().onItemRevealed(p.getId(), p.getName());
            System.out.println("[AuctionEngine] Hé lộ: " + p.getName() + " (Vòng " + round + ")");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PHASE 5 — RANDOM EVENTS (5 loại) — kích hoạt ở VÒNG 3, giây 15
    // ═════════════════════════════════════════════════════════════════════

    private void triggerRandomEvent() {
        int eventType = random.nextInt(5); // 0..4 — đều nhau (5 loại sự kiện)
        synchronized (state) {
            if (state.finished) return;

            if (eventType == 0 || eventType == 1) {
                // ── SỰ KIỆN HÉ LỘ: món NGẪU NHIÊN (EXTRA_REVEAL) hoặc món NẶNG NHẤT (HEAVY_REVEAL) ──
                // Duy nhất cách CHỌN MÓN khác nhau; phần cập nhật P_est/P_buyout/broadcast là chung.
                Product target = null;
                if (!state.hiddenItems.isEmpty()) {
                    if (eventType == 1) {
                        // HEAVYWEIGHT REVEAL: findMaxWeightItem() trên mảng đồ ẩn — manh mối cực
                        // mạnh: món nặng nhưng rẻ = "rác", loại trừ xong người chơi tự tin tính phần còn lại.
                        for (Product p : state.hiddenItems) {
                            if (target == null || p.getWeight() > target.getWeight()) target = p;
                        }
                    } else {
                        // EXTRA REVEAL: lật mở 1 món NGẪU NHIÊN — biến động P_est không lường trước
                        target = state.hiddenItems.get(random.nextInt(state.hiddenItems.size()));
                    }
                    state.hiddenItems.remove(target);
                    state.revealedItems.add(target);
                    if (state.dbSessionId > 0) {
                        auctionSessionDAO.updateItemRevealRound(state.dbSessionId, target.getId(), state.currentRound);
                    }
                }
                // Tính lại P_est sau khi tập "đã lộ" thay đổi (mục 3)
                double sumV = state.revealedItems.stream().mapToDouble(Product::getRealValue).sum();
                double sumW = state.revealedItems.stream().mapToDouble(Product::getWeight).sum();
                double pricePerKg = (sumW > 0) ? (sumV / sumW) : 0;
                int kIdx = Math.min(state.currentRound, GameConfig.K_ROUND.length) - 1;
                state.currentEstimate = pricePerKg * state.totalWeight * GameConfig.K_ROUND[kIdx];
                applyBuyoutFromEstimateLocked();
                // TRẦN GIÁ: chốt cả P_est/P_buyout theo hạng phòng (EXTRA/HEAVY_REVEAL)
                double capReveal = (room.getTier() == Room.RoomTier.DAI_GIA)
                        ? GameConfig.BOX_CAP_DAI_GIA : GameConfig.BOX_CAP_BINH_DAN;
                state.currentEstimate = Math.min(state.currentEstimate, capReveal);
                state.currentBuyoutPrice = Math.min(state.currentBuyoutPrice, capReveal);

                // Format: random-event,<EXTRA_REVEAL|HEAVY_REVEAL>,<productId>,<name>,<picture>,<P_est>,<P_buyout>
                // (Giữ CỐ ĐỊNH 7 token — productId rỗng nếu không còn món ẩn để client parse an toàn)
                String pic = target != null && target.getPicture() != null ? target.getPicture() : "none";
                String name = target != null ? target.getName() : "Khong con vat pham bi mat nao";
                String revealType = (eventType == 1) ? "HEAVY_REVEAL" : "EXTRA_REVEAL";
                room.broadCast("random-event," + revealType + "," + (target != null ? target.getId() : "")
                        + "," + name + "," + pic + "," + String.format("%.0f", state.currentEstimate)
                        + "," + String.format("%.0f", state.currentBuyoutPrice));
                if (room.getBotManager() != null) {
                    room.getBotManager().onEstimateUpdate(state.currentEstimate, state.currentBuyoutPrice);
                }
                System.out.println("[AuctionEngine] ⭐ EVENT " + revealType + ": " + name
                        + (target != null ? " (" + target.getWeight() + " kg)" : ""));
            } else if (eventType == 2) {
                // ── FLASH SALE: giảm sốc 30% buyout trong 10 giây ──
                // Đã chốt: hết 10s GIỮ mức sau giảm (không revert).
                state.currentBuyoutPrice *= (1.0 - GameConfig.FLASH_SALE_DISCOUNT);
                state.flashSaleActive = true;
                // Format: random-event,FLASH_SALE,<newBuyoutPrice>
                room.broadCast(String.format("random-event,FLASH_SALE,%.0f", state.currentBuyoutPrice));
                if (room.getBotManager() != null) {
                    room.getBotManager().onEstimateUpdate(state.currentEstimate, state.currentBuyoutPrice);
                }
                System.out.println("[AuctionEngine] ⚡ EVENT FLASH_SALE: buyout giảm còn " + (long) state.currentBuyoutPrice);

                flashSaleRevertFuture = scheduler.schedule(() -> {
                    synchronized (state) {
                        if (state.finished) return;
                        state.flashSaleActive = false; // Hết 10s: tắt cờ, GIỮ mức giá
                        // Format: random-event,FLASH_SALE_END,<newBuyoutPrice>
                        room.broadCast("random-event,FLASH_SALE_END," + String.format("%.0f", state.currentBuyoutPrice));
                    }
                }, GameConfig.FLASH_SALE_DURATION_SEC, TimeUnit.SECONDS);
            } else if (eventType == 3) {
                // ── CHUYÊN GIA THẨM ĐỊNH (APPRAISER): "Giá trị trung bình của 1 vật phẩm" ──
                // Thiết kế mục 2: "hé lộ 'Giá trung bình' của cả thùng".
                // An toàn thông tin: giá TB/món × số món đang ẨN chỉ là thông tin phái sinh,
                // còn "giá trung bình cả thùng" literal = totalRealValue / số món — không lộ
                // tổng giá trị thật trừ khi client nhân ngược với số món (4–6 món là công khai).
                // → Giữ đúng tinh thần thiết kế nhưng payload là ước tính: giá TB/món × tổng số
                //   món × biên độ sai số ±5% — client không biết chính xác tổng giá trị thật.
                double avgValue = state.totalRealValue / Math.max(1, state.allItems.size());
                double noisedAvg = avgValue * (1.0 + (random.nextDouble() * 2 - 1) * GameConfig.APPRAISER_VARIANCE);
                double impliedTotal = noisedAvg * state.allItems.size();
                state.currentEstimate = impliedTotal;
                applyBuyoutFromEstimateLocked();
                // TRẦN GIÁ: APPRAISER cũng không được vượt trần thùng của phòng
                double capAppraiser = (room.getTier() == Room.RoomTier.DAI_GIA)
                        ? GameConfig.BOX_CAP_DAI_GIA : GameConfig.BOX_CAP_BINH_DAN;
                state.currentEstimate = Math.min(state.currentEstimate, capAppraiser);
                state.currentBuyoutPrice = Math.min(state.currentBuyoutPrice, capAppraiser);

                // Format: random-event,APPRAISER,<avgItemValue>,<newEstimate>,<newBuyout>
                room.broadCast(String.format("random-event,APPRAISER,%.0f,%.0f,%.0f",
                        noisedAvg, state.currentEstimate, state.currentBuyoutPrice));
                if (room.getBotManager() != null) {
                    room.getBotManager().onEstimateUpdate(state.currentEstimate, state.currentBuyoutPrice);
                }
                System.out.println("[AuctionEngine] 🔍 EVENT APPRAISER: giá TB/món ~" + (long) noisedAvg
                        + " | ước tính mới " + (long) state.currentEstimate);
            } else {
                // ── ĐÓNG BĂNG GIÁ MUA ĐỨT (BUYOUT_FREEZE): bật cờ isBuyoutFrozen ──
                // Đã chốt: khoá CẢ giá mua đứt (không tăng không giảm theo P_est) cho đến hết
                // vòng; sang vòng mới nextRound() tự mở băng. Cú tát trực diện vào chiến thuật
                // "chờ giá sập sàn": mua ngay với giá hiện tại hoặc chấp nhận rủi ro bị đối thủ
                // lật kèo ở vòng sau.
                state.isBuyoutFrozen = true;
                // Format: random-event,BUYOUT_FREEZE,<currentBuyoutPrice>
                room.broadCast("random-event,BUYOUT_FREEZE," + String.format("%.0f", state.currentBuyoutPrice));
                System.out.println("[AuctionEngine] 🧊 EVENT BUYOUT_FREEZE: giá mua đứt bị khoá ở "
                        + (long) state.currentBuyoutPrice + " đến hết vòng " + state.currentRound);
            }
        }
    }

    /**
     * Tính lại giá mua đứt = P_est × M trung bình của vòng hiện tại — CHỈ khi giá KHÔNG bị
     * đóng băng (sự kiện BUYOUT_FREEZE khoá cả M lẫn giá cho đến hết vòng — đã chốt).
     * PHẢI được gọi khi đang giữ synchronized(state).
     */
    private void applyBuyoutFromEstimateLocked() {
        if (state.isBuyoutFrozen) return; // Băng: giữ nguyên giá — không tăng, không giảm
        int kIdx = Math.min(state.currentRound, GameConfig.M_BUYOUT_RANGE.length) - 1;
        double[] mRange = GameConfig.M_BUYOUT_RANGE[kIdx];
        double mAvg = (mRange[0] + mRange[1]) / 2.0;
        state.currentBuyoutPrice = state.currentEstimate * mAvg;
    }

    // ═════════════════════════════════════════════════════════════════════
    // BID / BUYOUT — gọi từ GameRequestQueue worker (tuần tự FIFO theo mục 4)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Xử lý lệnh BID (luật mới: mỗi người chỉ 1 bid/vòng, buyout không tính):
     *  - Được đặt BẤT KỲ số tiền > 0: KHÔNG cần cao hơn mức dẫn đầu, KHÔNG bước tăng tối thiểu.
     *  - Đấu giá KÍN TRONG VÒNG: bid-accepted KHÔNG kèm số tiền — chỉ "ai đã đặt giá".
     *  - HẾT MỖI VÒNG: server CÔNG BỐ giá mọi người (lệnh bid-reveal trong nextRound).
     *  - Mỗi người 1 bid/vòng (check lastBidRound); sang vòng mới được bid tiếp.
     *    Đã bid rồi vẫn được BUYOUT (buyout độc lập, không tính vào quota).
     * Validate: phiên còn hoạt động, số tiền > 0, ví đủ, chưa bid vòng này.
     */
    public void onBid(ServerThread sender, double amount) {
        if (state == null) {
            sendToThread(sender, "bid-rejected,Phiên đấu giá chưa khởi tạo");
            return;
        }
        synchronized (state) {
            if (state.finished) {
                sendToThread(sender, "bid-rejected,Phiên đấu giá đã kết thúc");
                return;
            }
            if (state.currentRound < 1) {
                sendToThread(sender, "bid-rejected,Phiên đấu giá chưa bắt đầu");
                return;
            }

            Player p = sender.getPlayer();
            if (p == null) {
                sendToThread(sender, "bid-rejected,Chưa đăng nhập");
                return;
            }

            if (amount <= 0) {
                sendToThread(sender, "bid-rejected,Số tiền phải lớn hơn 0");
                return;
            }

            double wallet = playerDAO.getWalletBalance(p.getId());
            if (wallet < amount) {
                sendToThread(sender, "bid-rejected,Không đủ số dư ví (có " + (long) wallet + " VNĐ, cần " + (long) amount + " VNĐ)");
                return;
            }

            // Mỗi người chỉ 1 bid/vòng (buyout không tính vào quota)
            Integer lastRound = state.lastBidRound.get(p.getId());
            if (lastRound != null && lastRound == state.currentRound) {
                sendToThread(sender, "bid-rejected,Mỗi người chỉ được đấu giá 1 lần/vòng " + state.currentRound);
                return;
            }

            // Ghi nhận giá riêng (giữ mức CAO NHẤT của chính họ qua các vòng)
            // và tính lại mức dẫn đầu THUẦN TÚY: bid thấp hơn/bằng mức hiện tại vẫn ĐƯỢC
            // chấp nhận, chỉ không thay đổi người dẫn đầu.
            Double prevOwn = state.bids.get(p.getId());
            if (prevOwn == null || amount > prevOwn) state.bids.put(p.getId(), amount);
            state.lastBidRound.put(p.getId(), state.currentRound);
            if (amount > state.currentHighestBid) {
                state.currentHighestBid = amount;
                state.currentHighestBidderId = p.getId();
            }

            if (state.dbSessionId > 0) {
                bidHistoryDAO.addBid(new BidHistory(state.dbSessionId, p.getId(), state.currentRound, amount, false));
            }
            // Nhật ký in-memory phục vụ CÔNG BỐ TẤT CẢ GIÁ lúc kết thúc (đấu giá kín)
            state.bidLog.add(new AuctionRoomState.BidEntry(p.getId(), amount, state.currentRound, false));

            String nick = p.getNickName() != null ? p.getNickName() : p.getUsername();
            // ĐẤU GIÁ KÍN: KHÔNG gửi amount — format bid-accepted,<playerId>,<nickName>
            // (đại Karl bấm bid 2M, đại Bảo bấm 2.1M: cả hai chỉ thấy "đã có người đặt giá")
            room.broadCast("bid-accepted," + p.getId() + "," + nick);
            if (room.getBotManager() != null) room.getBotManager().onBidAccepted(p.getId());
            System.out.println("[AuctionEngine] BID (kín): " + nick + " → " + (long) amount + " (CHỈ SERVER BIẾT)");
        }
    }

    /**
     * Xử lý lệnh BUYOUT — mua đứt giá hiện tại, ghi đè mọi BID, kết thúc phiên NGAY LẬP TỨC.
     * Worker duy nhất của GameRequestQueue xử lý FIFO: lệnh xếp trước được ghi nhận,
     * lệnh xếp sau thấy phiên đã kết thúc → bị hủy ("bid-rejected,Phiên đã kết thúc").
     */
    public void onBuyout(ServerThread sender) {
        if (state == null) {
            sendToThread(sender, "bid-rejected,Phiên đấu giá chưa khởi tạo");
            return;
        }
        double buyoutPrice;
        synchronized (state) {
            if (state.finished) {
                sendToThread(sender, "bid-rejected,Phiên đấu giá đã kết thúc");
                return;
            }
            if (state.currentRound < 1) {
                sendToThread(sender, "bid-rejected,Phiên đấu giá chưa bắt đầu");
                return;
            }

            Player p = sender.getPlayer();
            if (p == null) {
                sendToThread(sender, "bid-rejected,Chưa đăng nhập");
                return;
            }

            buyoutPrice = state.currentBuyoutPrice;
            double wallet = playerDAO.getWalletBalance(p.getId());
            if (wallet < buyoutPrice) {
                sendToThread(sender, "bid-rejected,Không đủ số dư ví để mua đứt (cần " + (long) buyoutPrice + " VNĐ, có " + (long) wallet + " VNĐ)");
                return;
            }

            if (state.endedByBuyout) {
                // Luồng bị trễ do race với endAuction (release lock rồi re-lock):
                // BUYOUT của người khác đã ghi → bỏ qua để không double-deduct.
                sendToThread(sender, "bid-rejected,Buyout đã được ghi nhận trước đó");
                return;
            }

            state.currentHighestBidderId = p.getId();
            state.currentHighestBid = buyoutPrice;
            state.endedByBuyout = true;

            if (state.dbSessionId > 0) {
                bidHistoryDAO.addBid(new BidHistory(state.dbSessionId, p.getId(), state.currentRound, buyoutPrice, true));
            }
            state.bidLog.add(new AuctionRoomState.BidEntry(p.getId(), buyoutPrice, state.currentRound, true));
            String nick = p.getNickName() != null ? p.getNickName() : p.getUsername();
            System.out.println("[AuctionEngine] BUYOUT: " + nick + " → " + (long) buyoutPrice);
        }
        // endAuction tự re-lock, kiểm tra finished bên trong → chỉ 1 luồng kết thúc.
        endAuction("BUYOUT");
    }

    // ═════════════════════════════════════════════════════════════════════
    // END AUCTION
    // ═════════════════════════════════════════════════════════════════════

    /** Kết thúc phiên từ luồng ngoài (disconnect...). Tự re-lock và chống double-end. */
    public void endAuction(String reason) {
        if (state == null) return;
        synchronized (state) {
            endAuctionLocked(reason);
        }
    }

    /**
     * Kết thúc phiên. PHẢI được gọi khi đang giữ synchronized(state).
     * CAS trên state.finished → chỉ luồng đầu tiên thực sự kết thúc phiên.
     */
    private void endAuctionLocked(String reason) {
        if (state.finished) return; // Double-end guard
        state.finished = true;
        state.endReason = reason;

        // Chống leak thread nền: dừng timer + hàng đợi lệnh (mục 4 + Phase 6)
        if (flashSaleRevertFuture != null) flashSaleRevertFuture.cancel(true);
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdownNow();
        requestQueue.shutdown(); // poison pill — xử lý nốt request xếp hàng rồi worker thoát

        int winnerId    = state.currentHighestBidderId;
        double winPrice = state.currentHighestBid;
        boolean byBuyout = state.endedByBuyout;

        // ── Huỷ khi KHÔNG có bid hợp lệ nào ──
        if (!byBuyout && winnerId <= 0) {
            state.cancelled = true;
        }

        // ── Fallback đã chốt: hết vòng 4 mà bid cao nhất KHÔNG vượt giá ước tính cuối → HUỶ ──
        if (!byBuyout && GameConfig.CANCEL_IF_BID_BELOW_FINAL_ESTIMATE
                && winnerId > 0 && winPrice <= state.currentEstimate) {
            System.out.println("[AuctionEngine] Huỷ phiên: bid cao nhất (" + (long) winPrice
                    + ") không vượt giá ước tính cuối (" + (long) state.currentEstimate + ")");
            winnerId = -1;
            winPrice = 0;
            state.cancelled = true;
        }

        System.out.println("[AuctionEngine] Kết thúc phiên Room " + room.getId() + " | Lý do: " + reason
                + " | Winner: " + (winnerId > 0 ? winnerId : "không có") + " | Giá: " + (long) winPrice);

        if (winnerId > 0) {
            // Chỉ NGƯỜI THẮNG bị trừ tiền giá trúng (BID chỉ là lời cam kết không trừ tiền ngay)
            boolean deducted = playerDAO.deductWallet(winnerId, winPrice);
            if (!deducted) {
                // Lý thuyết không xảy ra (đã check ví khi bid/buyout) — nhưng phải an toàn
                System.out.println("[AuctionEngine] CẢNH BÁO: trừ ví thất bại cho người thắng #" + winnerId + " → huỷ phiên");
                winnerId = -1;
                winPrice = 0;
                state.cancelled = true;
            } else {
                playerDAO.addWin(winnerId);
                for (ServerThread st : room.getPlayers()) {
                    Player other = st.getPlayer();
                    if (other != null && other.getId() != winnerId) {
                        if (st.isBot()) continue; // Bot không cộng/thua điểm — dữ liệu test không bẩn bảng xếp hạng
                        playerDAO.addLose(other.getId());
                    }
                }
                // ── ĐẬP HỘP THÀNH TIỀN (đã chốt): giá trị thực của thùng chuyển thành tiền
                // của người thắng — CỘNG trực tiếp vào ví NGAY SAU khi trừ tiền trúng:
                // lãi/lỗ thực tế = Σ realValue − giá trúng. Kèm log server để đối soát.
                double boxRealValue = state.allItems.stream().mapToDouble(Product::getRealValue).sum();
                playerDAO.addWallet(winnerId, boxRealValue);
                System.out.println("[AuctionEngine] 📦 Đập hộp thành tiền: người thắng #" + winnerId
                        + " trả " + (long) winPrice + " → nhận thùng trị giá " + (long) boxRealValue
                        + " (lãi/lỗ: " + (long) (boxRealValue - winPrice) + ")");
            }
        }

        // ── CÔNG BỐ TẤT CẢ GIÁ lần cuối khi phiên chốt (gồm cả lượt mua đứt)
        if (!state.bidLog.isEmpty()) broadcastBidRevealLocked();

        if (state.dbSessionId > 0) {
            // Phiên bị HUỶ: KHÔNG ghi đè buyout hiện tại (tránh ghi nhầm giá bán thực tế)
            Double recordedFinalBuyout = state.cancelled ? null : state.currentBuyoutPrice;
            auctionSessionDAO.updateAuctionResult(state.dbSessionId,
                    recordedFinalBuyout,
                    winnerId > 0 ? winnerId : null,
                    winnerId > 0 ? winPrice : null,
                    byBuyout);
        }

        double newWallet = 0;
        if (winnerId > 0) {
            // Ví MỚI của người thắng = (đã trừ giá trúng) + (đã cộng giá trị thùng)
            newWallet = playerDAO.getWalletBalance(winnerId);
        }

        // ── MÀN "ĐẬP HỘP" (thiết kế mục 2): AUCTION_ENDED gửi TOÀN BỘ vật phẩm ──
        // Đây là thời điểm DUY NHẤT giá trị thật được gửi cho client (phiên đã kết thúc).
        StringBuilder unbox = new StringBuilder("auction-ended," + winnerId + "," + (long) winPrice + ","
                + (byBuyout ? 1 : 0) + "," + (long) newWallet + "," + state.allItems.size());
        for (Product item : state.allItems) {
            String pic = item.getPicture() != null ? item.getPicture() : "none";
            unbox.append(",").append(item.getId()).append(",").append(item.getName())
                 .append(",").append(pic).append(",").append((long) item.getRealValue())
                 .append(",").append(String.format("%.2f", item.getWeight()));
        }
        room.broadCast(unbox.toString());

        // Format: auction-result,<winnerId>,<winningPrice>,<endedByBuyout:0/1>,<walletMoiCuaNguoiThang>
        // winnerId=-1 → phiên bị huỷ, không ai mất tiền (giữ tương thích UI cũ)
        String resultMsg = "auction-result," + winnerId + "," + (long) winPrice + ","
                + (byBuyout ? 1 : 0) + "," + (long) newWallet;
        room.broadCast(resultMsg);

        room.setStatus(Room.RoomStatus.FINISHED);
        room.setPlayersToNotPlaying();
        room.releasePlayersAfterAuction(); // Client trở về Lobby và join phòng khác được ngay
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * CÔNG BỐ TẤT CẢ GIÁ đã đặt (đấu giá kín — đã chốt lần 2):
     * bid-reveal,<soBid>,[<playerId>,<nick>,<amount>,<round>,<bid|buyout> × soBid]
     * Gọi ở HẾT MỖI VÒNG (nextRound) và lúc phiên kết thúc (endAuctionLocked).
     * PHẢI được gọi khi đang giữ synchronized(state).
     */
    private void broadcastBidRevealLocked() {
        if (state == null || state.bidLog.isEmpty()) return;
        StringBuilder reveal = new StringBuilder("bid-reveal," + state.bidLog.size());
        for (AuctionRoomState.BidEntry b : state.bidLog) {
            String nick = "?";
            for (ServerThread st : room.getPlayers()) {
                Player bp = st.getPlayer();
                if (bp != null && bp.getId() == b.playerId) {
                    nick = bp.getNickName() != null ? bp.getNickName() : bp.getUsername();
                    break;
                }
            }
            reveal.append(",").append(b.playerId).append(",").append(nick)
                  .append(",").append((long) b.amount).append(",").append(b.round)
                  .append(b.buyout ? ",buyout" : ",bid");
        }
        room.broadCast(reveal.toString());
        System.out.println("[AuctionEngine] Công bố " + state.bidLog.size() + " giá đã đặt (hết vòng/kết thúc)");
    }

    private void sendToThread(ServerThread st, String msg) {
        try {
            st.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public AuctionRoomState getState() {
        return state;
    }

    public GameRequestQueue getRequestQueue() {
        return requestQueue;
    }
}

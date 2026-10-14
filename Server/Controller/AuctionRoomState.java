package Server.Controller;

import Server.Model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuctionRoomState — giữ trạng thái PHIÊN ĐẤU GIÁ trong RAM của server.
 *
 * Bảo mật: totalRealValue, hiddenItems và giá trị thực từng món là TUYỆT MẬT — không bao
 * giờ xuất hiện trong bất kỳ message nào gửi cho client TRƯỚC khi phiên kết thúc
 * (lúc kết thúc mới broadcast màn "Đập hộp" — thiết kế mục 2).
 *
 * Giá ước tính (currentEstimate) KHÔNG còn là giá trị thật ± random nữa — nó được tính
 * theo công thức "cú lừa tâm lý" mục 3 của thiết kế:
 *     P_est = (ΣV_revealed / ΣW_revealed) × W_total × K_round
 * do AuctionEngine tính lại sau MỖI lần hé lộ.
 *
 * Thread-safety: các trường mutable được truy cập QUA synchronized(state) trong
 * AuctionEngine. Lệnh BID/BUYOUT từ client vào qua GameRequestQueue (mục 4) — 1 worker
 * duy nhất mỗi phòng xử lý FIFO, nên về bản chất đã tuần tự; synchronized(state) giữ
 * lại như lớp bảo vệ thứ hai (timer vòng/sự kiện cũng mutation state).
 */
public class AuctionRoomState {

    // ── Thùng hàng ────────────────────────────────────────────────────────
    public final List<Product> allItems;       // Tất cả vật phẩm trong thùng (BÍ MẬT)
    public final List<Product> revealedItems;  // Vật phẩm đã hé lộ (công khai)
    public final List<Product> hiddenItems;    // Vật phẩm chưa lộ (BÍ MẬT với client)

    public final double totalRealValue;        // Tổng giá trị thực (BÍ MẬT với client)
    public final double totalWeight;           // Tổng khối lượng (công khai từ V1)

    // ── Giá hiển thị cho client (tính theo công thức mục 3) ──────────────
    public double currentEstimate;             // P_est — tính lại sau mỗi lần hé lộ
    public double currentBuyoutPrice;          // P_buyout = P_est × M_buyout (random/vòng)
    public boolean flashSaleActive = false;    // Đang trong 10s Flash Sale (sự kiện)
    /**
     * Sự kiện BUYOUT_FREEZE: khoá cứng giá mua đứt — KHÔNG đổi theo P_est (tăng hay giảm)
     * cho đến hết vòng hiện tại. Sang vòng mới engine tự mở băng (nextRound).
     * Cú tát vào chiến thuật "chờ giá sập sàn": phải mua ngay hoặc chấp nhận rủi ro vòng sau.
     */
    public boolean isBuyoutFrozen = false;

    // ── Vòng đấu ──────────────────────────────────────────────────────────
    public int currentRound;                   // Vòng hiện tại: 1..4
    public static final int TOTAL_ROUNDS = GameConfig.TOTAL_ROUNDS;

    // ── Bid ───────────────────────────────────────────────────────────────
    /** Map playerId → số tiền bid cao nhất hiện tại của người đó */
    public final ConcurrentHashMap<Integer, Double> bids = new ConcurrentHashMap<>();
    /** Map playerId → vòng đã bid (mỗi người chỉ 1 bid/vòng, buyout không tính) */
    public final ConcurrentHashMap<Integer, Integer> lastBidRound = new ConcurrentHashMap<>();
    public int currentHighestBidderId = -1;    // Player ID đang giữ bid cao nhất
    public double currentHighestBid = 0;       // Số tiền bid cao nhất hiện tại

    // ── Nhật ký bid IN-MEMORY (đấu giá kín) ─────────────────────────────
    /** Mọi lượt giá đã được CHẤP NHẬN trong phiên — nguồn cho lệnh bid-reveal
     *  (CÔNG BỐ TẤT CẢ GIÁ lúc phiên kết thúc). Không đọc lại từ DB để không phụ
     *  thuộc persist (phiên test dbSessionId=-1 vẫn công bố được). */
    public final List<BidEntry> bidLog = new ArrayList<>();

    /** Một lượt giá đã được chấp nhận (bid hoặc mua đứt) — phục vụ bid-reveal. */
    public static class BidEntry {
        public final int playerId;
        public final double amount;
        public final int round;
        public final boolean buyout;
        public BidEntry(int playerId, double amount, int round, boolean buyout) {
            this.playerId = playerId; this.amount = amount; this.round = round; this.buyout = buyout;
        }
    }

    // ── Phiên ─────────────────────────────────────────────────────────────
    public String theme;                       // Chủ đề thùng hàng (key trùng Product.category)
    public int dbSessionId = -1;               // ID lưu DB (sau khi persist)
    public volatile boolean finished = false;  // Phiên đã kết thúc
    public boolean endedByBuyout = false;      // Kết thúc do BUYOUT
    public boolean cancelled = false;          // HUỶ: không ai thắng, không ai mất tiền
    public String endReason = "";              // BUYOUT | ROUND_COMPLETE | ALL_DISCONNECTED | ...
    public boolean randomEventFired = false;   // Random event đã kích hoạt chưa

    // ─────────────────────────────────────────────────────────────────────

    public AuctionRoomState(List<Product> items, double totalRealValue, double totalWeight) {
        this.allItems = new ArrayList<>(items);
        this.revealedItems = new ArrayList<>();
        this.hiddenItems = new ArrayList<>(items); // Ban đầu tất cả đều ẩn

        this.totalRealValue = totalRealValue;
        this.totalWeight = totalWeight;
        this.currentRound = 0;
        this.currentEstimate = 0;      // Engine tính ngay khi vào vòng 1 (sau khi hé mồi nhử)
        this.currentBuyoutPrice = 0;
    }

    /**
     * Kiểm tra bid hợp lệ (đã chốt lần 2): KHÔNG còn ràng buộc về độ lớn — người
     * chơi được đặt BẤT KỲ số tiền nào > 0 (cao hơn, thấp hơn hay bằng mức hiện
     * tại đều được). Mức dẫn đầu chỉ được tính lại bởi engine.
     * (Chống gian lận: nếu từ chối bid thấp, kẻ xấu dò được mức cao nhất qua
     * các lần từ chối — bid càng càn thấp, từ chối càng tiết lộ giá hiện tại.)
     */
    public boolean isValidBid(double amount) {
        return amount > 0;
    }
}

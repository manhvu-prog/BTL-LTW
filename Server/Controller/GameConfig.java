package Server.Controller;

/**
 * GameConfig — gom TOÀN BỘ tham số gameplay của "Đấu Giá Thùng Hàng Bí Ẩn"
 * vào một lớp duy nhất để dễ cân bằng (balance) và sửa đổi.
 *
 * Các giá trị đã được CHỐT với người dùng (mục 8 của bản thiết kế):
 *  - Số dư ví mặc định:            3.000.000 VNĐ
 *  - Hạng phòng (RoomTier):        Bình dân [2M–5M], Khá giả [5M–20M], Đại gia [20M+]  (xem Room.RoomTier)
 *  - Công thức P_est:              (ΣV_đã_lộ / ΣW_đã_lộ) × W_total × K_round — K: 1.20→1.10→1.05→1.05
 *  - M_buyout (random/vòng):       V1 2.5–3.0, V2 2.0–2.2, V3 1.5–1.8, V4 1.2–1.3 (đã chốt: random trong khoảng)
 *  - Buyout:                       KHÔNG giảm theo giây — chỉ tính lại P_est × M_buyout khi chuyển vòng
 *  - Thời lượng mỗi vòng:          45 giây
 *  - Số vật phẩm mỗi thùng:        6–8
 *  - Flash Sale:                   giảm 30% buyout trong 10 giây, SAU 10s GIỮ mức giá sau giảm
 *                                  (decay bình thường tiếp tục chạy từ mức đó — đã chốt)
 *  - Fallback vòng 4:              nếu KHÔNG có bid hợp lệ nào vượt giá ước tính cuối → HUỶ PHIÊN
 *                                  (không ai thắng, không ai bị trừ tiền) — đã chốt
 *  - Bước bid tối thiểu:           5% so với bid cao nhất hiện tại (tự quyết — không có số trong thiết kế)
 *  - Host bấm "Bắt đầu" khi ≥2 người; KHÔNG tự start khi đủ phòng (chỉ host quyết định)
 */
public final class GameConfig {

    private GameConfig() {} // Chỉ chứa hằng số, không khởi tạo

    // ── Thùng hàng ────────────────────────────────────────────────────────
    public static final int    MIN_ITEMS_PER_BOX       = 6;      // Số vật phẩm tối thiểu mỗi thùng
    public static final int    MAX_ITEMS_PER_BOX       = 8;      // Số vật phẩm tối đa mỗi thùng
    public static final int    MAX_ITEMS_LEFT_HIDDEN   = 2;      // Vòng 4: chỉ giấu lại tối đa 1–2 món

    // ── Công thức định giá & Buyout (mục 3 của thiết kế) ─────────────────
    // P_est  = (ΣV_revealed / ΣW_revealed) × W_total × K_round — hệ số kỳ vọng ẢO giảm dần qua các vòng
    public static final double[] K_ROUND = {1.20, 1.10, 1.05, 1.05}; // V1..V4 (V4 giữ nguyên vì vẫn còn đồ ẩn)

    // P_buyout = P_est × M_buyout — M_buyout RANDOM trong khoảng mỗi vòng (đã chốt),
    // nguyên lý đấu giá ngược: V1 "vùng cấm địa" → V4 "phí bảo hiểm chốt hạ".
    public static final double[][] M_BUYOUT_RANGE = {
            {2.5, 3.0},   // Vòng 1 — vùng cấm địa, ngăn người giàu kết thúc sớm
            {2.0, 2.2},   // Vòng 2 — bắt đầu giảm, kích thích tâm lý
            {1.5, 1.8},   // Vòng 3 — điểm rơi chiến thuật, chớp cơ hội trước khi lật kèo
            {1.2, 1.3}    // Vòng 4 — phí bảo hiểm chốt hạ, trả chênh 20–30% mua an toàn
    };

    // Sự kiện Thẩm định (APPRAISER): sai số ±5% quanh giá trung bình mỗi món
    public static final double APPRAISER_VARIANCE      = 0.05;

    // ── Flash Sale (Phase 5, sự kiện 2) ───────────────────────────────────
    public static final double FLASH_SALE_DISCOUNT     = 0.30;   // Giảm 30% buyout
    public static final int    FLASH_SALE_DURATION_SEC = 10;     // Trong 10 giây
    // Đã chốt: sau Flash Sale, buyout GIỮ mức sau giảm, decay tiếp tục từ mức đó.

    // ── Vòng đấu ──────────────────────────────────────────────────────────
    public static final int    TOTAL_ROUNDS            = 4;
    public static final int    ROUND_DURATION_SEC      = 45;     // Đồng hồ đếm ngược mỗi vòng (đã chốt)
    public static final int    RANDOM_EVENT_AT_SEC     = 15;     // Random event nổ ở giây thứ 15 của VÒNG 3 (thiết kế mục 2)

    // ── Bid ───────────────────────────────────────────────────────────────
    // ĐẤU GIÁ KÍN (đã chốt): giá bid được ẨN suốt phiên — client chỉ thấy "ai đã đặt giá".
    // KHÔNG còn bước tăng tối thiểu: bid hợp lệ khi CAO HƠN mức dẫn đầu (bất kỳ lượng nào).
    // Hết giờ mỗi vòng/khi phiên chốt: server CÔNG BỐ tất cả giá đã đặt (lệnh bid-reveal).
    public static final boolean SEALED_BID_MODE        = true;   // Ẩn số tiền bid khi phiên đang chạy
    public static final double MIN_BID_STEP_RATIO      = 0.0;    // ĐÃ BỎ bước 5% (giữ hằng số cho tương thích)

    // ── Kết thúc phiên ────────────────────────────────────────────────────
    // Đã chốt: hết vòng 4, nếu bid cao nhất KHÔNG vượt giá ước tính cuối → huỷ phiên,
    // không ai thắng, không ai bị trừ tiền. (Xử lý trong AuctionEngine.endAuction)
    public static final boolean CANCEL_IF_BID_BELOW_FINAL_ESTIMATE = true;

    // ── Phòng ─────────────────────────────────────────────────────────────
    public static final int    MAX_PLAYERS             = 3;      // 3 người chơi / phòng
    public static final int    MIN_PLAYERS_TO_START    = 2;      // Chỉ host bấm Bắt đầu, cần ≥2 người

    // ── Trần giá thùng theo hạng phòng ("giá không vượt mức phòng" — đã chốt) ──
    // Áp dụng CHO CẢ BA: tổng realValue thùng, P_est và P_buyout (clamp trong AuctionEngine).
    public static final double BOX_CAP_BINH_DAN         = 15_000_000;  // Trần thùng phòng Bình Dân
    public static final double BOX_CAP_DAI_GIA          = 50_000_000;  // Trần thùng phòng Đại Gia

    // ── Chủ đề thùng hàng ─────────────────────────────────────────────────
    // Server random 1 chủ đề mỗi phiên (đã chốt). Key trùng cột Product.category
    // trong migration_themes.sql. Tên hiển thị gửi kèm auction-start (token 4).
    public static final String[] BOX_THEMES = {
            "CONG_NGHE", "TRANG_SUC", "O_TO", "THOI_TRANG", "GIA_DUNG", "THE_THAO",
            "THUC_PHAM", "SACH_VAN_PHONG", "DO_CHOI", "MY_PHAM", "NHAC_CU", "DO_CO",
            "THU_CUNG", "DU_LICH"
    };
}

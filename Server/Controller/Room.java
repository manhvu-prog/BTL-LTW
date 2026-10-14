package Server.Controller;

import Server.DAO.PlayerDAO;
import Server.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Room hỗ trợ tối đa 3 người chơi cho game Đấu Giá Thùng Hàng Bí Ẩn.
 * Mỗi phòng thuộc một hạng (RoomTier) dựa trên walletBalance người chơi.
 */
public class Room {

    // ======================== ENUM ========================

    public enum RoomTier {
        BINH_DAN(2_000_000, 10_000_000),                    // ví 2–10M
        DAI_GIA(10_000_000, Double.POSITIVE_INFINITY);      // ví >10M, KHÔNG trần trên
        // Lưu ý: 50M là trần THÙNG HÀNG (GameConfig.BOX_CAP_DAI_GIA), không phải trần ví —
        // chặn ví < 50M khiến tài khoản 100M không tạo được phòng nào (bug đã sửa).

        public final double minWallet;
        public final double maxWallet;

        RoomTier(double minWallet, double maxWallet) {
            this.minWallet = minWallet;
            this.maxWallet = maxWallet;
        }

        /** Kiểm tra wallet có hợp lệ với hạng phòng này không */
        public boolean matches(double walletBalance) {
            return walletBalance >= minWallet && walletBalance < maxWallet;
        }

        /** Trả về tier phù hợp với số dư ví, null nếu không thuộc hạng nào */
        public static RoomTier forWallet(double walletBalance) {
            for (RoomTier tier : values()) {
                if (tier.matches(walletBalance)) return tier;
            }
            return null; // Dưới mức tối thiểu Bình Dân
        }

        public String getDisplayName() {
            switch (this) {
                case BINH_DAN: return "Bình Dân";
                case DAI_GIA:  return "Đại Gia";
                default:       return name();
            }
        }
    }

    public enum RoomStatus {
        WAITING,      // Đang chờ đủ 3 người (chỉ host bấm Bắt đầu)
        IN_PROGRESS,  // Đang đấu giá
        FINISHED      // Kết thúc
    }

    // ======================== CONSTANTS ========================

    public static final int MAX_PLAYERS = GameConfig.MAX_PLAYERS;

    // ======================== FIELDS ========================

    private final int id;
    private final RoomTier tier;
    private final List<ServerThread> players;     // Tối đa 3 ServerThread (GameConfig.MAX_PLAYERS)
    private RoomStatus status;
    private String password;
    private final PlayerDAO playerDAO;
    private AuctionEngine auctionEngine;          // Engine đấu giá (khởi tạo khi bắt đầu)
    private BotManager botManager;                // Có giá trị ở phòng "đấu thử với máy"

    // ======================== CONSTRUCTOR ========================

    public Room(ServerThread creator, RoomTier tier) {
        System.out.println("Tạo phòng [" + tier.getDisplayName() + "] thành công, ID: " + Server.ROOM_ID);
        this.id = Server.ROOM_ID++;
        this.tier = tier;
        this.players = Collections.synchronizedList(new ArrayList<>());
        this.players.add(creator);
        this.status = RoomStatus.WAITING;
        this.password = " ";
        this.playerDAO = new PlayerDAO();
    }

    // ======================== PLAYER MANAGEMENT ========================

    public synchronized boolean addPlayer(ServerThread serverThread) {
        if (players.size() >= MAX_PLAYERS || status != RoomStatus.WAITING) {
            return false;
        }
        players.add(serverThread);
        return true;
    }

    public synchronized void removePlayer(ServerThread serverThread) {
        players.remove(serverThread);
    }

    public synchronized int getNumberOfPlayer() {
        return players.size();
    }

    public synchronized boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public synchronized boolean isEmpty() {
        return players.isEmpty();
    }

    public synchronized List<ServerThread> getPlayers() {
        return new ArrayList<>(players); // Trả về bản sao tránh ConcurrentModification
    }

    /** Kiểm tra xem một ServerThread có trong phòng không */
    public synchronized boolean containsPlayer(ServerThread st) {
        return players.contains(st);
    }

    // ======================== BROADCAST ========================

    /** Broadcast đến tất cả người chơi trong phòng.
     * KHÔNG giữ synchronized khi ghi socket: luồng đấu giá giữ synchronized(state)
     * rồi gọi broadCast — nếu broadCast lại giữ monitor của Room thì 1 client chậm
     * (socket đầy buffer) sẽ chặn toàn bộ onBid/onBuyout (lock Room→state nghịch đảo
     * với đường ServerThread→Room). Ghi socket trên bản sao chốt thời điểm gọi. */
    public synchronized void broadCast(String message) {
        for (ServerThread st : players.toArray(new ServerThread[0])) {
            try {
                st.write(message);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /** Broadcast đến tất cả ngoại trừ 1 người chơi */
    public synchronized void broadCastExcept(ServerThread except, String message) {
        for (ServerThread st : players) {
            if (st == except) continue;
            try {
                st.write(message);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // ======================== WAITING ROOM UPDATE ========================

    /**
     * Gửi lệnh `waiting-room-update` cho tất cả người trong phòng.
     * Format: waiting-room-update,<soNguoiHienTai>,<maxNguoi>,<tierName>,<nick1>,<nick2>,...
     * Quy ước: nick ĐẦU TIÊN trong danh sách là CHỦ PHÒNG (host) — client hiển thị 👑.
     */
    public synchronized void broadcastWaitingRoomUpdate() {
        StringBuilder sb = new StringBuilder("waiting-room-update,");
        sb.append(players.size()).append(",").append(MAX_PLAYERS).append(",").append(tier.name());
        for (ServerThread st : players) {
            String nick = st.getPlayer() != null ? st.getPlayer().getNickName() : "?";
            sb.append(",").append(nick);
        }
        broadCast(sb.toString());
    }

    // ======================== PLAYER STATE MANAGEMENT ========================

    public void setPlayersToPlaying() {
        for (ServerThread st : getPlayers()) {
            if (st.getPlayer() != null) {
                playerDAO.updateToPlaying(st.getPlayer().getId());
            }
        }
    }

    public void setPlayersToNotPlaying() {
        for (ServerThread st : getPlayers()) {
            if (st.getPlayer() != null) {
                playerDAO.updateToNotPlaying(st.getPlayer().getId());
            }
        }
    }

    /**
     * Giải phóng mọi người khỏi phòng sau khi phiên đấu giá kết thúc: client đã nhận
     * auction-result và trở về Lobby — cho phép join phòng khác ngay, đồng thời Room
     * rỗng giúp luồng dọn dẹp không gọi startAuction trên phòng FINISHED.
     */
    public synchronized void releasePlayersAfterAuction() {
        // Bot rời phòng TRƯỚC khi clear: releaseBots cập nhật isPlaying=0 về DB cho tài khoản bot
        if (botManager != null) botManager.releaseBots(); // tự detach bên trong
        for (ServerThread st : players.toArray(new ServerThread[0])) {
            st.setRoom(null);
        }
        players.clear();
        auctionEngine = null;
    }

    // ======================== BOT (đấu thử với máy) ========================

    public void attachBotManager(BotManager m) { this.botManager = m; }
    public void detachBotManager()             { this.botManager = null; }
    public BotManager getBotManager()          { return botManager; }

    // ======================== AUCTION ========================

    /**
     * Khởi động AuctionEngine cho phòng này (đã chốt: host bấm Bắt đầu khi ≥2 người,
     * hoặc tự động khi đủ 3 người qua matchmaking).
     * Guard: chỉ cho phép khi phòng đang WAITING và đủ số người tối thiểu — chống
     * double-start khi 2 luồng gọi cùng lúc.
     */
    public void startAuction() {
        // Guard chống double-start trong synchronized(room), nhưng CHẠY engine NGOÀI monitor:
        // engine.startAuction() sẽ chờ lock state (AuctionRoomState) — nếu giữ monitor Room
        // ở đây thì luồng decay/bid (giữ state, chờ monitor Room để broadCast) gây deadlock.
        synchronized (this) {
            if (status != RoomStatus.WAITING) {
                System.out.println("[Room " + id + "] Bỏ qua startAuction: phòng đang " + status);
                return;
            }
            if (players.size() < GameConfig.MIN_PLAYERS_TO_START) {
                System.out.println("[Room " + id + "] Bỏ qua startAuction: chỉ có " + players.size()
                        + " người (cần tối thiểu 2 người để host bấm Bắt đầu)");
                return;
            }
            auctionEngine = new AuctionEngine(this);
            status = RoomStatus.IN_PROGRESS; // chặn luồng start thứ hai NGAY TRONG guard
        }
        auctionEngine.startAuction(); // engine tự setStatus(IN_PROGRESS) lại — idempotent
    }

    /** Kiểm tra một ServerThread có phải chủ phòng (người đầu tiên vào) không */
    public synchronized boolean isHost(ServerThread st) {
        return !players.isEmpty() && players.get(0) == st;
    }

    public AuctionEngine getAuctionEngine() {
        return auctionEngine;
    }

    // ======================== LEGACY COMPAT (dùng lại cho Admin view) ========================

    /** Lấy ServerThread đầu tiên (player1 legacy) */
    public ServerThread getPlayer1() {
        synchronized (players) {
            return players.isEmpty() ? null : players.get(0);
        }
    }

    /** Lấy ServerThread thứ hai (player2 legacy) */
    public ServerThread getPlayer2() {
        synchronized (players) {
            return players.size() >= 2 ? players.get(1) : null;
        }
    }

    // ======================== GETTERS / SETTERS ========================

    public int getId() {
        return id;
    }

    public RoomTier getTier() {
        return tier;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(RoomStatus status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

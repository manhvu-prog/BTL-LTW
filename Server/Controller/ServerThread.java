package Server.Controller;

import Server.DAO.PlayerDAO;
import Server.Model.Player;
import Server.Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý một kết nối client. Mỗi lệnh nhận từ client có dạng CSV: "lenh,tham_so1,tham_so2,...\n"
 */
public class ServerThread extends Thread {

    private Player player;
    private final Socket clientSocket;
    private final int clientNumber;
    private final boolean isBot;          // true = ServerThread ẢO (bot) — không có socket
    private BufferedReader in;
    private PrintWriter out;
    private boolean isClosed;
    private Room room;
    private final String clientIP;
    private final PlayerDAO playerDAO;

    public ServerThread(Socket clientSocket, int clientNumber) throws IOException {
        this.clientSocket = clientSocket;
        this.clientNumber = clientNumber;
        this.isBot = false;
        this.playerDAO = new PlayerDAO();
        System.out.println("Server thread number " + clientNumber + " Started");
        this.isClosed = false;
        this.room = null;
        if (this.clientSocket.getInetAddress().getHostAddress().equals("127.0.0.1")) {
            clientIP = "127.0.0.1";
        } else {
            clientIP = this.clientSocket.getInetAddress().getHostAddress();
        }
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
    }

    /**
     * Constructor CHO BOT (đấu thử với máy): ServerThread ẢO KHÔNG có socket.
     * Vẫn đi qua ĐÚNG pipeline người thật: AuctionEngine gọi onBid/onBuyout →
     * enqueue RequestObject vào GameRequestQueue (FIFO) → worker xử lý như người.
     * "Ghi ra socket" của bot chỉ là log console server (write() đã guard isBot).
     */
    public ServerThread(int clientNumber) {
        this.clientSocket = null;
        this.clientNumber = clientNumber;
        this.isBot = true;
        this.playerDAO = new PlayerDAO();
        this.isClosed = false;
        this.room = null;
        this.clientIP = "bot";
        this.in = null;
        this.out = new PrintWriter(new StringWriter());
    }

    public void write(String message) throws IOException {
        if (isBot) {
            System.out.println("  [BOT #" + clientNumber + " nhận] " + message);
            return; // Bot không có socket — mọi broadcast chỉ là log
        }
        out.write(message);
        out.println();
        out.flush();
    }

    public String getStringFromPlayer(Player p) {
        return p.toString();
    }

    // =================== HELPERS ===================

    /**
     * Xây dựng chuỗi waiting-room-update để thông báo phòng chờ.
     * Format: waiting-room-update,<so_nguoi>,<max_nguoi>,<tier>,<nick1>,<nick2>,...
     */
    private String buildWaitingRoomUpdateMsg(Room r) {
        List<ServerThread> players = r.getPlayers();
        StringBuilder sb = new StringBuilder("waiting-room-update,");
        sb.append(players.size()).append(",").append(Room.MAX_PLAYERS).append(",").append(r.getTier().name());
        for (ServerThread st : players) {
            String nick = st.getPlayer() != null ? st.getPlayer().getNickName() : "?";
            sb.append(",").append(nick);
        }
        return sb.toString();
    }

    /**
     * Dùng chung cho go-to-room / join-room: vào phòng theo ID,
     * kiểm tra hạng ví (RoomTier), trạng thái WAITING và mật khẩu.
     */
    private void tryJoinRoom(int roomId, String pwd) {
        for (ServerThread st : Server.serverThreadBus.getListServerThreads()) {
            Room r = st.getRoom();
            if (r == null || r.getId() != roomId) continue;
            if (r.isFull() || r.getStatus() != Room.RoomStatus.WAITING) {
                try { write("room-fully,"); } catch (IOException e) { e.printStackTrace(); }
                return;
            }
            double wallet = playerDAO.getWalletBalance(this.player.getId());
            if (!r.getTier().matches(wallet)) {
                try { write("room-reject,Số dư ví không phù hợp hạng phòng " + r.getTier().getDisplayName()); } catch (IOException e) { e.printStackTrace(); }
                return;
            }
            if (!r.getPassword().trim().isEmpty() && !r.getPassword().equals(pwd)) {
                try { write("room-wrong-password,"); } catch (IOException e) { e.printStackTrace(); }
                return;
            }
            if (r.addPlayer(this)) {
                this.room = r;
                playerDAO.updateToPlaying(this.player.getId());
                r.broadcastWaitingRoomUpdate();
                System.out.println("Vào phòng " + r.getId());
                // Chốt: KHÔNG tự start khi đủ phòng — chỉ host bấm "start-auction" (≥2 người)
            } else {
                try { write("room-fully,"); } catch (IOException e) { e.printStackTrace(); }
            }
            return;
        }
        try { write("room-not-found,"); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Tìm phòng WAITING phù hợp với tier và password.
     * Trả về null nếu không tìm thấy.
     */
    private Room findWaitingRoom(Room.RoomTier tier, String password) {
        for (ServerThread st : Server.serverThreadBus.getListServerThreads()) {
            Room r = st.getRoom();
            if (r == null) continue;
            if (r.getTier() != tier) continue;
            if (r.getStatus() != Room.RoomStatus.WAITING) continue;
            if (r.isFull()) continue;
            // Kiểm tra password: phòng public (password = " ") không cần mật khẩu
            if (password == null || password.isEmpty()) {
                if (r.getPassword().trim().isEmpty()) return r;  // Tìm phòng không khóa
            } else {
                if (r.getPassword().equals(password)) return r;  // Khớp mật khẩu
            }
        }
        return null;
    }

    // =================== LEAVE ROOM ===================

    private void handleLeaveRoom(boolean notifyOthers) throws IOException {
        if (room == null) return;
        Room r = this.room;
        r.removePlayer(this);
        if (this.player != null) playerDAO.updateToNotPlaying(this.player.getId());
        this.room = null;

        if (r.getStatus() == Room.RoomStatus.IN_PROGRESS) {
            if (r.isEmpty()) {
                if (r.getAuctionEngine() != null) {
                    System.out.println("[ServerThread] Tất cả người chơi đã thoát phòng " + r.getId() + ". Kết thúc phiên.");
                    r.getAuctionEngine().endAuction("ALL_DISCONNECTED");
                }
            } else if (notifyOthers && this.player != null) {
                r.broadCast("chat-server,Người chơi " + this.player.getNickName() + " đã thoát khỏi phiên đấu giá.");
            }
        } else {
            if (notifyOthers && !r.isEmpty()) {
                r.broadcastWaitingRoomUpdate();
            }
        }
    }

    // =================== MAIN LOOP ===================

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            if (isBot) {
                // Bot KHÔNG chạy vòng đọc socket — nó chỉ là bản ghi trong phòng.
                // Não bot (BotManager) điều khiển qua enqueueBid()/enqueueBuyout().
                System.out.println("[BOT #" + clientNumber + "] ServerThread ảo khởi động (không socket)");
                return;
            }
            System.out.println("Khởi động luồng mới thành công, ID là: " + clientNumber);
            write("server-send-id," + this.clientNumber);

            String message;
            while (!isClosed) {
                message = in.readLine();
                if (message == null) {
                    // Client ngắt kết nối (readLine trả null) → ném IOException để chạy
                    // nhánh dọn dẹp bên dưới thay vì lặp vô hạn / NPE khi split(null).
                    throw new IOException("Client " + clientNumber + " ngắt kết nối");
                }
                String[] msg = message.split(",");
                String cmd = msg[0];

                // ── Auth ─────────────────────────────────────────────────────────────
                // Xác minh đăng nhập: "client-verify,<username>,<password>"
                if (cmd.equals("client-verify")) {
                    System.out.println(message);
                    Player found = playerDAO.verifyPlayer(new Player(msg[1], msg[2]));
                    if (found == null) {
                        write("wrong-player," + msg[1] + "," + msg[2]);
                    } else if (!found.isOnline()) {
                        write("login-success," + getStringFromPlayer(found));
                        this.player = found;
                        playerDAO.updateToOnline(this.player.getId());
                        Server.serverThreadBus.broadCast(clientNumber, "chat-server," + found.getNickName() + " đang online");
                        Server.admin.addMessage("[" + found.getId() + "] " + found.getNickName() + " đang online");
                    } else {
                        write("dupplicate-login," + msg[1] + "," + msg[2]);
                    }
                }

                // Đăng ký tài khoản: "register,<username>,<password>,<nickName>,<avatar>"
                if (cmd.equals("register")) {
                    boolean dup = playerDAO.checkDuplicated(msg[1]);
                    if (dup) {
                        write("duplicate-username,");
                    } else {
                        Player newP = new Player(msg[1], msg[2], msg[3], msg[4]);
                        playerDAO.addPlayer(newP);
                        this.player = playerDAO.verifyPlayer(newP);
                        playerDAO.updateToOnline(this.player.getId());
                        Server.serverThreadBus.broadCast(clientNumber, "chat-server," + this.player.getNickName() + " đang online");
                        write("login-success," + getStringFromPlayer(this.player));
                    }
                }

                // Đăng xuất: "offline"
                if (cmd.equals("offline")) {
                    if (this.player != null) {
                        playerDAO.updateToOffline(this.player.getId());
                        Server.admin.addMessage("[" + player.getId() + "] " + player.getNickName() + " đã offline");
                        Server.serverThreadBus.broadCast(clientNumber, "chat-server," + this.player.getNickName() + " đã offline");
                        this.player = null;
                    }
                }

                // Update thông tin bản thân: "update"
                if (cmd.equals("update")) {
                    write("update," + getStringFromPlayer(playerDAO.getPlayerByID(player.getId())));
                }

                // ── Lobby ────────────────────────────────────────────────────────────
                // Lấy bảng xếp hạng: "leaderboard"
                if (cmd.equals("leaderboard")) {
                    ArrayList<Player> ranks = playerDAO.getLeaderBoard();
                    StringBuilder res = new StringBuilder("return-leaderboard,");
                    for (Player u : ranks) res.append(getStringFromPlayer(u)).append(",");
                    write(res.toString());
                }

                // Danh sách online: "onlineList"
                if (cmd.equals("onlineList")) {
                    ArrayList<Player> onlineList = playerDAO.getOnlineList();
                    StringBuilder res = new StringBuilder("return-onlineList,");
                    for (Player p : onlineList) {
                        res.append(p.getUsername()).append(",").append(p.getNickName()).append(",").append(p.getAvatar()).append(",");
                    }
                    write(res.toString());
                }

                // Chat toàn server: "chat-server,<nội dung>"
                if (cmd.equals("chat-server")) {
                    Server.serverThreadBus.broadCast(clientNumber, "chat-server," + this.player.getNickName() + " : " + msg[1]);
                    Server.admin.addMessage("[" + player.getId() + "] " + player.getNickName() + " : " + msg[1]);
                }

                // ── Phase 2: Matchmaking theo hạng ví ───────────────────────────────
                /**
                 * Tìm phòng đấu giá theo hạng: "find-auction-room,<TIER>"
                 * TIER = BINH_DAN | KHA_GIA | DAI_GIA
                 * Server kiểm tra walletBalance người chơi có hợp lệ với tier không.
                 */
                if (cmd.equals("find-auction-room")) {
                    if (this.player == null) { write("room-reject,Chưa đăng nhập"); continue; }

                    Room.RoomTier requestedTier;
                    try {
                        requestedTier = Room.RoomTier.valueOf(msg[1]);
                    } catch (Exception e) {
                        write("room-reject,Hạng phòng không hợp lệ");
                        continue;
                    }

                    double wallet = playerDAO.getWalletBalance(this.player.getId());
                    Room.RoomTier playerTier = Room.RoomTier.forWallet(wallet);

                    // Kiểm tra điều kiện ví
                    if (playerTier == null) {
                        write("room-reject,Số dư ví quá thấp (cần tối thiểu 2.000.000 VNĐ)");
                        continue;
                    }
                    if (requestedTier != playerTier) {
                        write("room-reject,Số dư ví (" + (long) wallet + ") không thuộc hạng " + requestedTier.getDisplayName()
                                + ". Hạng phù hợp của bạn: " + playerTier.getDisplayName());
                        continue;
                    }

                    // Tìm phòng WAITING phù hợp (không khóa)
                    Room foundRoom = findWaitingRoom(requestedTier, null);
                    if (foundRoom != null) {
                        // Có phòng trống → vào ngay
                        boolean joined = foundRoom.addPlayer(this);
                        if (joined) {
                            this.room = foundRoom;
                            playerDAO.updateToPlaying(this.player.getId());
                            foundRoom.broadcastWaitingRoomUpdate();
                            System.out.println("Người chơi " + player.getNickName() + " vào phòng " + foundRoom.getId());

                            // Chốt: KHÔNG tự start khi đủ phòng — chỉ host bấm "start-auction" (≥2 người)
                        } else {
                            // Phòng đầy ngay tức khắc (race condition nhỏ) → tạo phòng mới
                            this.room = new Room(this, requestedTier);
                            playerDAO.updateToPlaying(this.player.getId());
                            write(buildWaitingRoomUpdateMsg(this.room));
                            System.out.println("Race: tạo phòng mới " + this.room.getId());
                        }
                    } else {
                        // Không có phòng → tạo phòng mới tự động
                        this.room = new Room(this, requestedTier);
                        playerDAO.updateToPlaying(this.player.getId());
                        write(buildWaitingRoomUpdateMsg(this.room));
                        System.out.println("Tạo phòng mới [" + requestedTier.getDisplayName() + "] ID " + this.room.getId());
                    }
                }

                /**
                 * Tạo phòng có hoặc không mật khẩu: "create-room,<TIER>[,<password>]"
                 */
                if (cmd.equals("create-room")) {
                    if (this.player == null) { write("room-reject,Chưa đăng nhập"); continue; }

                    Room.RoomTier tier;
                    try {
                        tier = Room.RoomTier.valueOf(msg[1]);
                    } catch (Exception e) {
                        write("room-reject,Hạng phòng không hợp lệ");
                        continue;
                    }

                    double wallet = playerDAO.getWalletBalance(this.player.getId());
                    if (!tier.matches(wallet)) {
                        write("room-reject,Số dư ví không đủ điều kiện hạng " + tier.getDisplayName());
                        continue;
                    }

                    this.room = new Room(this, tier);
                    playerDAO.updateToPlaying(this.player.getId());

                    if (msg.length >= 3 && !msg[2].trim().isEmpty()) {
                        room.setPassword(msg[2]);
                        write("your-created-room," + room.getId() + "," + msg[2]);
                        System.out.println("Tạo phòng mới có password: " + msg[2]);
                    } else {
                        write("your-created-room," + room.getId());
                        System.out.println("Tạo phòng mới không password");
                    }
                    write(buildWaitingRoomUpdateMsg(this.room));
                }

                /**
                 * Vào phòng theo ID (tìm phòng F): "go-to-room,<roomId>[,<password>]"
                 */
                if (cmd.equals("go-to-room")) {
                    if (this.player == null) { write("room-reject,Chưa đăng nhập"); continue; }
                    try {
                        tryJoinRoom(Integer.parseInt(msg[1]), msg.length >= 3 ? msg[2] : "");
                    } catch (NumberFormatException e) {
                        write("room-not-found,");
                    }
                }

                /**
                 * Xem danh sách phòng đang chờ: "view-room-list[,<TIER>]"
                 * Format trả về: room-list,<id>,<tier>,<so_nguoi>,<password>,...
                 */
                if (cmd.equals("view-room-list")) {
                    StringBuilder res = new StringBuilder("room-list,");
                    int count = 0;
                    Room.RoomTier filterTier = null;
                    if (msg.length >= 2) {
                        try { filterTier = Room.RoomTier.valueOf(msg[1]); } catch (Exception ignored) {}
                    }
                    for (ServerThread st : Server.serverThreadBus.getListServerThreads()) {
                        if (count >= 8) break;
                        Room r = st.getRoom();
                        if (r == null || r.getStatus() != Room.RoomStatus.WAITING) continue;
                        if (filterTier != null && r.getTier() != filterTier) continue;
                        boolean locked = !r.getPassword().trim().isEmpty();
                        // Mỗi phòng 5 trường: id,tier,soNguoiHienTai,maxNguoi,coMatKhau(0/1)
                        res.append(r.getId()).append(",")
                           .append(r.getTier().name()).append(",")
                           .append(r.getNumberOfPlayer()).append(",")
                           .append(Room.MAX_PLAYERS).append(",")
                           .append(locked ? 1 : 0).append(",");
                        count++;
                    }
                    write(res.toString());
                    System.out.println(res);
                }

                /**
                 * Hủy tìm/chờ phòng: "cancel-room"
                 */
                if (cmd.equals("cancel-room")) {
                    handleLeaveRoom(true);
                    System.out.println("Đã hủy phòng");
                }

                /**
                 * Thoát khỏi phòng đang chơi: "leave-room"
                 */
                if (cmd.equals("leave-room")) {
                    handleLeaveRoom(true);
                }

                // ── Legacy alias của go-to-room (tương thích client cũ) ─────────────
                /**
                 * Vào phòng theo ID (legacy): "join-room,<roomId>[,<password>]"
                 */
                if (cmd.equals("join-room")) {
                    if (this.player == null) { write("room-reject,Chưa đăng nhập"); continue; }
                    try {
                        tryJoinRoom(Integer.parseInt(msg[1]), msg.length >= 3 ? msg[2] : "");
                    } catch (NumberFormatException e) {
                        write("room-not-found,");
                    }
                }

                // ── Phase 3+ hooks (placeholder) ─────────────────────────────────────
                /**
                 * Trả giá: "bid,<amount>"
                 */
                if (cmd.equals("bid")) {
                    // Kiến trúc Message Queue (mục 4): luồng mạng KHÔNG xử lý logic game —
                    // đóng gói RequestObject kèm timestamp NHẬN rồi enqueue, rảnh tay ngay.
                    Room bidRoom = room;
                    AuctionEngine bidEngine = (bidRoom != null) ? bidRoom.getAuctionEngine() : null;
                    if (bidRoom == null || bidRoom.getStatus() != Room.RoomStatus.IN_PROGRESS || bidEngine == null) {
                        write("bid-rejected,Không có phiên đấu giá đang diễn ra");
                        continue;
                    }
                    if (msg.length < 2) {
                        write("bid-rejected,Thiếu số tiền bid");
                        continue;
                    }
                    try {
                        double amount = Double.parseDouble(msg[1]);
                        bidEngine.getRequestQueue()
                                .submit(new RequestObject(RequestObject.Type.BID, this, amount));
                    } catch (NumberFormatException e) {
                        write("bid-rejected,Số tiền không hợp lệ");
                    }
                }

                /**
                 * Mua đứt: "buyout"
                 */
                if (cmd.equals("buyout")) {
                    // Như lệnh bid: chỉ enqueue vào hàng đợi FIFO per-room — worker duy nhất xử lý.
                    Room buyRoom = room;
                    AuctionEngine buyEngine = (buyRoom != null) ? buyRoom.getAuctionEngine() : null;
                    if (buyRoom == null || buyRoom.getStatus() != Room.RoomStatus.IN_PROGRESS || buyEngine == null) {
                        write("bid-rejected,Không có phiên đấu giá đang diễn ra");
                        continue;
                    }
                    buyEngine.getRequestQueue().submit(new RequestObject(RequestObject.Type.BUYOUT, this, 0));
                }

                /**
                 * Đấu thử với máy: "spawn-bots" — chỉ HOST trong phòng chờ được phép.
                 * Server mượn 3 tài khoản bot (bot001..bot003) tạo ServerThread ảo,
                 * addPlayer vào phòng như người thật rồi khởi động phiên ngay.
                 */
                if (cmd.equals("spawn-bots")) {
                    if (room == null) { write("room-reject,Bạn chưa vào phòng nào"); continue; }
                    if (!room.isHost(this)) { write("room-reject,Chỉ chủ phòng mới được thêm máy"); continue; }
                    if (room.getStatus() != Room.RoomStatus.WAITING) { continue; }
                    int added = BotManager.spawnBotsInto(room);
                    if (added == 0) {
                        write("room-reject,Không thể thêm máy (tài khoản bot chưa có trong DB — hãy chạy lại migration_mystery_box.sql)");
                        continue;
                    }
                    room.broadCast("chat-server,🤖 " + added + " máy đã tham gia phòng — chuẩn bị đấu giá!");
                    room.broadCast("auction-ready," + room.getId());
                    room.startAuction();
                    continue;
                }

                /**
                 * Chủ phòng bấm Bắt đầu: "start-auction"
                 * Đã chốt: chỉ host (người đầu tiên vào phòng) được start, cần ≥2 người.
                 */
                if (cmd.equals("start-auction")) {
                    if (room == null) { write("room-reject,Bạn chưa vào phòng nào"); continue; }
                    if (!room.isHost(this)) { write("room-reject,Chỉ chủ phòng mới được bắt đầu phiên đấu giá"); continue; }
                    if (room.getStatus() != Room.RoomStatus.WAITING) { continue; }
                    if (room.getNumberOfPlayer() < GameConfig.MIN_PLAYERS_TO_START) {
                        write("room-reject,Cần ít nhất " + GameConfig.MIN_PLAYERS_TO_START
                                + " người trong phòng để bắt đầu (hiện có " + room.getNumberOfPlayer() + ")");
                        continue;
                    }
                    room.broadCast("auction-ready," + room.getId());
                    room.startAuction();
                }

            }
        } catch (IOException e) {
            isClosed = true;
            if (isBot) return; // Bot không có I/O — không bao giờ vào nhánh disconnect
            if (this.player != null) {
                playerDAO.updateToOffline(this.player.getId());
                playerDAO.updateToNotPlaying(this.player.getId());
                Server.serverThreadBus.broadCast(clientNumber, "chat-server," + this.player.getNickName() + " đã offline");
                Server.admin.addMessage("[" + player.getId() + "] " + player.getNickName() + " đã offline");
            }
            Server.serverThreadBus.remove(clientNumber);
            System.out.println(this.clientNumber + " đã thoát");

            // Dọn dẹp phòng: rời phòng, và nếu là phiên IN_PROGRESS mà trống hết người
            // thì kết thúc phiên → scheduler.shutdownNow() chống leak thread nền.
            try {
                handleLeaveRoom(false);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // =================== GETTERS / SETTERS ===================

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public boolean isBot() { return isBot; }

    /** Gán định danh Player cho bot (đã có trong DB — bot001/bot002/bot003). */
    public void setBotIdentity(Player botPlayer) {
        if (!isBot) return;
        this.player = botPlayer;
    }
    public int getClientNumber() { return clientNumber; }
    public BufferedReader getIn() { return in; }
    public PrintWriter getOut() { return out; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getClientIP() { return clientIP; }
}

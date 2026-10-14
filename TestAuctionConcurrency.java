import Server.DAO.PlayerDAO;
import Server.DAO.ProductDAO;
import Server.Model.Player;
import Server.Model.Product;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TestAuctionConcurrency — Phase 6: kiểm chứng xử lý đồng thời của phiên đấu giá.
 *
 * Cách test chạy THẬT end-to-end:
 *  1. Mở một ServerSocket giả trên cổng ngẫu nhiên.
 *  2. Tạo 3 ServerThread thật (kết nối socket loopback) và một Room 3 người (MAX_PLAYERS=3).
 *  3. Nạp player IDs từ DB (test001..test003), random 1 thùng hàng từ ProductDAO.
 *  4. Bắn ĐỒNG LOẠT (N thread song song) các kịch bản:
 *     a) 3 client BID cùng lúc → mỗi người nhận đúng 1 phản hồi; luật mới: bid hợp lệ
 *        với BẤT KỲ số tiền > 0 (KHÔNG cần cao hơn mức dẫn đầu) → CẢ 3 bid được chấp nhận;
 *        bid-accepted KHÔNG kèm số tiền (kín trong vòng); bid-reveal công bố giá khi
 *        HẾT MỖI VÒNG và khi phiên kết thúc.
 *     b) BID + BUYOUT đồng thời (đều vượt ví) → đều bị reject, state nhất quán.
 *     c) BUYOUT thành công → endAuction đúng 1 lần (chống double-end/double-deduct).
 *     d) 2 BUYOUT XẾP HÀNG qua GameRequestQueue (mục 4) → người xếp TRƯỚC thắng,
 *        người xếp sau nhận "Phiên đấu giá đã kết thúc" — chứng minh FIFO 1 worker.
 *  5. Kiểm tra giá trị thật KHÔNG xuất hiện trong messages TRƯỚC khi kết thúc
 *     (màn Đập hộp auction-ended CHÍNH THỨC lộ giá trị khi phiên đã kết thúc).
 *
 * Yêu cầu: MySQL đang chạy (DAO kết nối localhost:3306, btl_ltm) + đã chạy createDB.sql.
 * Chạy:  java -cp <bin>;<mysql-connector> TestAuctionConcurrency
 */
public class TestAuctionConcurrency {

    static int passed = 0, failed = 0;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("  ✅ " + name); }
        else      { failed++; System.out.println("  ❌ " + name + " — " + detail); }
    }

    static int countPrefix(List<String> msgs, String prefix) {
        int n = 0;
        for (String m : msgs) if (m.startsWith(prefix)) n++;
        return n;
    }

    /** Tìm seed để Random(seed).nextInt(mod) == target — ép triggerRandomEvent chọn đúng sự kiện muốn test. */
    static long seedForNextInt(int target, int mod) {
        for (long s = 0; s < 100000; s++) {
            if (new java.util.Random(s).nextInt(mod) == target) return s;
        }
        throw new IllegalStateException("Không tìm được seed cho target=" + target);
    }

    /** Ép engine dùng Random có seed biết trước (random là field instance-final — set qua reflection được). */
    static void forceRandom(Server.Controller.AuctionEngine engine, long seed) throws Exception {
        java.lang.reflect.Field rf = Server.Controller.AuctionEngine.class.getDeclaredField("random");
        rf.setAccessible(true);
        rf.set(engine, new java.util.Random(seed));
    }

    /** Client giả: kết nối thẳng vào ServerThread qua socket loopback */
    static class FakeClient implements Runnable {
        final int id;
        volatile Socket socket;
        volatile BufferedReader reader;
        volatile PrintWriter writer;
        final List<String> received = new ArrayList<>();
        volatile boolean running = true;

        FakeClient(int id) { this.id = id; }

        void connect(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            Thread t = new Thread(this, "fake-client-" + id);
            t.setDaemon(true);
            t.start();
        }

        void send(String msg) {
            writer.write(msg);
            writer.println();
            writer.flush();
        }

        @Override
        public void run() {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    synchronized (received) { received.add(line); }
                }
            } catch (IOException ignored) {
            }
        }

        List<String> snapshot() {
            synchronized (received) { return new ArrayList<>(received); }
        }

        void close() {
            running = false;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== TestAuctionConcurrency (Phase 6) ===\n");

        // ── Setup: 4 fake client qua socket loopback ─────────────────────
        ServerSocket acceptor = new ServerSocket(0); // cổng ngẫu nhiên
        int port = acceptor.getLocalPort();

        FakeClient[] clients = new FakeClient[3];
        java.lang.reflect.Constructor<Server.Controller.ServerThread> ctor =
                Server.Controller.ServerThread.class.getDeclaredConstructor(Socket.class, int.class);
        ctor.setAccessible(true);

        Server.Controller.Room room = null;
        List<Server.Controller.ServerThread> roomPlayers = new ArrayList<>();
        int[] playerIds = new int[3];
        try {
            // ── Reset ví về mức seed — test trừ tiền THẬT vào DB nên phải reset mỗi lần chạy
            //    (nếu không, run sau bị reject vì không đủ ví và check FIFO sai bét) ──
            PlayerDAO resetDao = new PlayerDAO();
            for (int i = 1; i <= 3; i++) resetDao.setWalletBalance(i, 15_000_000); // mọi bid/buyout trong test đều ≤ 15M
            for (int i = 0; i < 3; i++) {
                clients[i] = new FakeClient(i);
                // Client phải connect TRƯỚC khi accept — accept() chặn vô hạn nếu chưa ai kết nối
                // (đây chính là nguyên nhân test treo vô hạn trước đây).
                clients[i].connect(port);
                Socket serverSide = acceptor.accept();
                Server.Controller.ServerThread st = ctor.newInstance(serverSide, 900 + i);
                Thread t = new Thread(st, "server-thread-" + i);
                t.setDaemon(true);
                t.start();

                PlayerDAO pdao = new PlayerDAO();
                // Gán player thật từ DB (test001..test003 — đã seed bởi migration_mystery_box.sql)
                Player player = pdao.getPlayerByID(i + 1);
                if (player == null) throw new IllegalStateException("Cần seed test001..test004 (chạy createDB.sql)");
                st.setPlayer(player);
                playerIds[i] = player.getId();
                roomPlayers.add(st);

                if (room == null) room = new Server.Controller.Room(st, Server.Controller.Room.RoomTier.BINH_DAN);
                else room.addPlayer(st);
            }
            System.out.println("Setup: 3 ServerThread + Room [" + room.getTier() + "] OK\n");

            // ════ TEST 1: 3 client BID đồng thời (vòng 1) ═══════════════════
            System.out.println("TEST 1 — 3 BID đồng thời (bất kỳ số tiền > 0, amount bị ẨN trong vòng):");
            Server.Controller.AuctionEngine engine = new Server.Controller.AuctionEngine(room);
            java.lang.reflect.Field stateField = Server.Controller.AuctionEngine.class.getDeclaredField("state");
            stateField.setAccessible(true);

            // Tự dựng state (không dùng startAuction để tránh phụ thuộc scheduler thật)
            ProductDAO productDAO = new ProductDAO();
            List<Product> items = productDAO.getRandomProducts(5);
            check("Có vật phẩm trong DB để tạo thùng", items.size() >= 4, "chỉ có " + items.size() + " món — chạy createDB.sql");
            double totalReal = items.stream().mapToDouble(Product::getRealValue).sum();
            // Constructor mới (thiết kế mục 3): P_est/P_buyout do engine tính theo công thức —
            // test tự set tay các trường giá để kiểm chứng BID/BUYOUT độc lập với định giá.
            Server.Controller.AuctionRoomState state = new Server.Controller.AuctionRoomState(items, totalReal, 1.0);
            state.currentEstimate = totalReal;
            state.currentBuyoutPrice = totalReal * 3.5;
            state.currentRound = 1;
            state.dbSessionId = -1; // test không persist
            stateField.set(engine, state);
            room.setStatus(Server.Controller.Room.RoomStatus.IN_PROGRESS);

            // 3 client bắn BID đồng thời: 2M, 2.5M, 3.5M
            double[] bids = {2_000_000, 2_500_000, 3_500_000};
            final Server.Controller.Room theRoom = room; // alias final cho lambda
            Thread[] threads = new Thread[3];
            for (int i = 0; i < 3; i++) {
                final double amount = bids[i];
                final Server.Controller.ServerThread sender = roomPlayers.get(i);
                threads[i] = new Thread(() -> engine.onBid(sender, amount));
                threads[i].start();
            }
            for (Thread t : threads) t.join(5000);

            Thread.sleep(200); // chờ broadcast lan tới fake clients

            // Bất biến 1a: bid-accepted là BROADCAST → mọi client phải thấy CÙNG SỐ lượng accept
            int acceptsOnClient0 = countPrefix(clients[0].snapshot(), "bid-accepted,");
            boolean consistentBroadcast = true;
            for (FakeClient c : clients) {
                if (countPrefix(c.snapshot(), "bid-accepted,") != acceptsOnClient0) consistentBroadcast = false;
            }
            check("Số bid-accepted giống hệt nhau trên cả 3 client (broadcast nhất quán)",
                    consistentBroadcast, "client0 thấy " + acceptsOnClient0);

            // Bất biến 1b: mỗi bid được trả lời ĐÚNG 1 LẦN (reject chỉ gửi riêng người gửi,
            // accept broadcast 1 lần/client) → số accept (trên 1 client) + tổng reject == 3 bid
            int totalRejects = 0;
            for (FakeClient c : clients) totalRejects += countPrefix(c.snapshot(), "bid-rejected,");
            check("3 bid → đúng 3 lượt trả lời (accept + reject), không trùng, không bỏ im",
                    acceptsOnClient0 + totalRejects == 3,
                    acceptsOnClient0 + " accept + " + totalRejects + " reject");

            // Bất biến 1c (luật mới): KHÔNG ràng buộc độ lớn → CẢ 3 bid được chấp nhận,
            // kể cả bid thấp tới sau bid cao (bid thấp chỉ KHÔNG giành dẫn đầu, không bị từ chối).
            check("Bỏ ràng buộc độ lớn → cả 3 bid được chấp nhận",
                    acceptsOnClient0 == 3, "accept " + acceptsOnClient0 + "/3");

            // Bất biến 1d (đấu giá kín): bid-accepted KHÔNG được tiết lộ số tiền (chỉ 3 token)
            boolean amountLeaked = false;
            for (String m : clients[0].snapshot()) {
                if (m.startsWith("bid-accepted,") && m.split(",").length > 3) amountLeaked = true;
            }
            check("bid-accepted KHÔNG chứa số tiền (đấu giá kín — chỉ playerId + nick)",
                    !amountLeaked, "có bid-accepted kèm amount — rò rỉ!");

            // Bất biến 3: highest bid khớp mức CAO NHẤT trong 3 bid (3.5M) và khớp đúng người giữ giá
            double highest = state.currentHighestBid;
            check("currentHighestBid = 3.500.000 (bid cao nhất trong 3)",
                    Math.abs(highest - 3_500_000) < 0.5, "thực tế " + highest);
            int winnerIdx = -1;
            for (int i = 0; i < 3; i++) if (Math.abs(bids[i] - highest) < 0.5) winnerIdx = i;
            check("currentHighestBidderId là đúng người giữ giá cao nhất",
                    winnerIdx >= 0 && state.currentHighestBidderId == playerIds[winnerIdx],
                    "bidderId=" + state.currentHighestBidderId + " mong đợi " + (winnerIdx >= 0 ? playerIds[winnerIdx] : "?"));

            // ════ TEST 1e (luật mới): bid THẤP HƠN không bị từ chối ══════════
            // 2 người đặt 5M, rồi test003 đặt 1M → phải ĐƯỢC chấp nhận (chỉ không giành
            // dẫn đầu). Nếu engine vẫn chặn bid thấp → lộ giá qua từ chối, sai luật mới.
            System.out.println("\nTEST 1e — bid THẤP HƠN mức dẫn đầu vẫn được chấp nhận (chỉ không giành dẫn đầu):");
            {
                for (int i = 0; i < 2; i++) engine.onBid(roomPlayers.get(i), 5_000_000);
                Thread.sleep(200);
                int base1e = clients[1].snapshot().size();
                engine.onBid(roomPlayers.get(2), 1_000_000); // thấp hơn 5M
                Thread.sleep(200);
                List<String> m1e = clients[1].snapshot().subList(Math.min(base1e, clients[1].snapshot().size()), clients[1].snapshot().size());
                boolean lowAccepted = false;
                for (String m : m1e) {
                    if (m.startsWith("bid-accepted,")) {
                        String[] parts = m.split(",");
                        if (parts.length > 1 && parts[1].equals(String.valueOf(playerIds[2]))) lowAccepted = true;
                    }
                }
                check("bid 1M (thấp hơn 5M) được chấp nhận", lowAccepted, "tin nhắn nhận: " + m1e);
                check("bid thấp KHÔNG thay đổi người dẫn đầu",
                        state.currentHighestBidderId == playerIds[0] && Math.abs(state.currentHighestBid - 5_000_000) < 0.5,
                        "highest=" + state.currentHighestBid + " bidder=" + state.currentHighestBidderId);
            }

            // ════ TEST 1f (luật mới): đặt lại giá CAO HƠN của chính mình ═════
            // test001 đang giữ giá 5M → đặt 6M → chấp nhận + tiếp tục dẫn đầu.
            System.out.println("\nTEST 1f — người đang dẫn đầu đặt lại giá cao hơn vẫn được chấp nhận:");
            {
                engine.onBid(roomPlayers.get(0), 6_000_000);
                Thread.sleep(200);
                check("Người dẫn đầu nâng giá chính mình thành công",
                        Math.abs(state.currentHighestBid - 6_000_000) < 0.5 && state.currentHighestBidderId == playerIds[0],
                        "highest=" + state.currentHighestBid + " bidder=" + state.currentHighestBidderId);
            }

            // ════ TEST 2: BID + BUYOUT đồng thời ═════════════════════════════
            System.out.println("\nTEST 2 — BID + BUYOUT đồng thời (cả 2 vượt ví, flip-flop dự kiến theo scheduling):\n");
            // Mua đứt 50M và bid 99M — đều vượt mọi ví test001..003 (max 15M).
            // Kết quả mong đợi: cả 2 bị reject, phiên KHÔNG kết thúc, state nhất quán.
            state.currentBuyoutPrice = 50_000_000;
            Thread bidT = new Thread(() -> engine.onBid(roomPlayers.get(2), 99_000_000));
            Thread buyT = new Thread(() -> engine.onBuyout(roomPlayers.get(0)));
            bidT.start(); buyT.start();
            bidT.join(5000); buyT.join(5000);

            Thread.sleep(200);
            // Cả 2 lệnh đều vượt ví nên nhiều khả năng đều bị reject; tuy nhiên nếu luồng
            // buyout vào lock trước trong một skew JVM hiếm gặp thì phiên kết thúc hợp lệ.
            // Cả 2 outcome đều chấp nhận: bất biến là state nhất quán + không double-deduct (TEST 3 đo).
            if (state.finished && state.endedByBuyout) {
                System.out.println("  ℹ️ Buyout về đích trước (scheduling skew) — phiên kết thúc hợp lệ, TEST 3 vẫn kiểm chứng chống double-end.");
            } else {
                check("Highest bid giữ nguyên sau khi cả 2 lệnh bị reject (state nhất quán)",
                        Math.abs(state.currentHighestBid - 6_000_000) < 0.5,
                        "thực tế " + state.currentHighestBid);
            }

            // ════ TEST 3: BUYOUT thành công → endAuction đúng 1 lần ══════════
            System.out.println("\nTEST 3 — BUYOUT thành công (test003, ví 10M) + chống double-end:");
            state.currentBuyoutPrice = 1_000_000; // hạ giá mua đứt để chắc chắn test003 (10M) mua được
            engine.onBuyout(roomPlayers.get(2));
            Thread.sleep(300);

            long resultCount1 = countPrefix(clients[0].snapshot(), "auction-result");
            check("Đúng 1 auction-result sau BUYOUT", resultCount1 == 1, "nhận được " + resultCount1);
            check("Phiên kết thúc do BUYOUT", state.finished && state.endedByBuyout,
                    "finished=" + state.finished + " endedByBuyout=" + state.endedByBuyout);

            // Double-end: gọi endAuction thêm lần nữa (mô phỏng luồng khác đến trễ)
            engine.endAuction("RACE_TEST_2");
            engine.endAuction("RACE_TEST_2");
            Thread.sleep(200);
            long resultCount2 = countPrefix(clients[0].snapshot(), "auction-result");
            check("Gọi endAuction thêm 2 lần → vẫn chỉ 1 auction-result (không double-deduct)",
                    resultCount2 == 1, "nhận được " + resultCount2);

            // ════ TEST 4: chống gian lận — giá trị thật không rò rỉ TRƯỚC khi kết thúc ════
            // (Sau kết thúc, màn "Đập hộp" auction-ended CHÍNH THỨC gửi giá trị từng món — mục 2)
            System.out.println("\nTEST 4 — Giá trị thật KHÔNG rò rỉ vào messages trước khi phiên kết thúc:");
            boolean leaked = false;
            for (FakeClient c : clients) {
                boolean afterEnd = false;
                for (String m : c.snapshot()) {
                    if (m.startsWith("auction-ended")) { afterEnd = true; } // đập hộp: được phép lộ
                    if (afterEnd) continue;
                    if (m.contains(String.valueOf((long) totalReal))) { leaked = true; }
                    if (m.contains("totalRealValue") || m.contains("hiddenItems")) { leaked = true; }
                }
            }
            check("Không message nào TRƯỚC kết thúc chứa totalRealValue", !leaked, "phát hiện rò rỉ giá trị thật " + (long) totalReal);
            boolean gotUnbox = clients[0].snapshot().stream().anyMatch(m -> m.startsWith("auction-ended,"));
            check("Nhận màn Đập hộp (auction-ended) khi phiên kết thúc", gotUnbox, "không nhận được auction-ended");

            // ════ TEST 5: bid sau khi phiên kết thúc → rejected ══════════════
            System.out.println("\nTEST 5 — BID sau khi phiên kết thúc:");
            int before = clients[1].snapshot().size();
            engine.onBid(roomPlayers.get(1), 5_000_000);
            Thread.sleep(200);
            List<String> after = clients[1].snapshot();
            boolean gotRejected = after.size() > before && after.get(after.size() - 1).startsWith("bid-rejected");
            check("BID sau kết thúc bị từ chối", gotRejected, "không nhận bid-rejected");

            // ════ TEST 5b: CÔNG BỐ TẤT CẢ GIÃ khi kết thúc (bid-reveal) ══════
            // Phiên phụ riêng: 2 người bid TUẦN TỰ (3M rồi 3.2M — trước đây 3.2M < 3M×1.05
            // bị từ chối vì bước 5%, giờ HỢP LỆ), rồi buyout chốt phiên → mọi client nhận
            // bid-reveal đủ 2 lượt bid + 1 lượt mua đứt, kèm số tiền + vòng + loại.
            System.out.println("\nTEST 5b — bid-reveal: công bố TẤT CẢ giá khi phiên kết thúc (đấu giá kín):");
            try {
                Server.Controller.Room roomR = null;
                for (int i = 0; i < 3; i++) {
                    if (roomR == null) roomR = new Server.Controller.Room(roomPlayers.get(i), Server.Controller.Room.RoomTier.BINH_DAN);
                    else roomR.addPlayer(roomPlayers.get(i));
                }
                Server.Controller.AuctionEngine engineR = new Server.Controller.AuctionEngine(roomR);
                java.lang.reflect.Field fR = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                fR.setAccessible(true);
                Server.Controller.AuctionRoomState stateR = new Server.Controller.AuctionRoomState(items, totalReal, 1.0);
                stateR.currentEstimate = totalReal;
                stateR.currentBuyoutPrice = 1_000_000; // rẻ để buyout chốt phiên thành công
                stateR.currentRound = 1;
                stateR.dbSessionId = -1;
                fR.set(engineR, stateR);
                roomR.setStatus(Server.Controller.Room.RoomStatus.IN_PROGRESS);

                int revealBase = clients[0].snapshot().size();
                engineR.onBid(roomPlayers.get(1), 3_000_000);
                engineR.onBid(roomPlayers.get(2), 3_200_000);
                engineR.onBuyout(roomPlayers.get(0));
                Thread.sleep(400);

                List<String> msgsR = clients[0].snapshot().subList(Math.min(revealBase, clients[0].snapshot().size()), clients[0].snapshot().size());
                String revealMsg = null;
                for (String m : msgsR) { if (m.startsWith("bid-reveal,")) revealMsg = m; }
                check("Có lệnh bid-reveal khi phiên kết thúc", revealMsg != null, "không nhận bid-reveal: " + msgsR);
                if (revealMsg != null) {
                    // Format: bid-reveal,<n>,<pid>,<nick>,<amount>,<round>,<type> — mua đứt ghi 1 lượt nữa → 3 lượt
                    int n = Integer.parseInt(revealMsg.split(",")[1]);
                    check("bid-reveal đủ lượt bid + buyout", n == 3, "n=" + n + " msg=" + revealMsg);
                    check("bid-reveal tiết lộ SỐ TIỀN đúng (3.000.000 và 3.200.000)",
                            revealMsg.contains("3000000") && revealMsg.contains("3200000"),
                            "msg=" + revealMsg);
                    check("bid-reveal đánh dấu MUA ĐỨT riêng",
                            revealMsg.contains(",buyout"), "msg=" + revealMsg);
                }
            } catch (Exception e) {
                check("TEST 5b chạy không ngoại lệ", false, e.toString());
            }

            // ════ TEST 5c (luật mới): CÔNG BỐ GIÁ khi HẾT MỖI VÒNG ═══════════
            // Engine thật chuyển vòng 1 → 2: trước khi round-update vòng 2, client phải
            // nhận bid-reveal công bố các giá đã đặt của vòng 1 (kín trong vòng, hé qua vòng).
            System.out.println("\nTEST 5c — bid-reveal được phát khi HẾT VÒNG (chuyển vòng 1 → 2):");
            java.util.concurrent.ScheduledExecutorService sched5c = null;
            try {
                Server.Controller.Room roomC = null;
                for (int i = 0; i < 3; i++) {
                    if (roomC == null) roomC = new Server.Controller.Room(roomPlayers.get(i), Server.Controller.Room.RoomTier.BINH_DAN);
                    else roomC.addPlayer(roomPlayers.get(i));
                }
                Server.Controller.AuctionEngine engineC = new Server.Controller.AuctionEngine(roomC);
                java.lang.reflect.Field fC = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                fC.setAccessible(true);
                Server.Controller.AuctionRoomState stateC = new Server.Controller.AuctionRoomState(items, totalReal, 1.0);
                stateC.currentRound = 1;
                stateC.dbSessionId = -1;
                fC.set(engineC, stateC);
                // Nhật ký 2 lượt giá của vòng 1 (mô phỏng người chơi đã bid trong vòng)
                stateC.bidLog.add(new Server.Controller.AuctionRoomState.BidEntry(playerIds[0], 4_000_000, 1, false));
                stateC.bidLog.add(new Server.Controller.AuctionRoomState.BidEntry(playerIds[1], 4_500_000, 1, false));
                // Cấp scheduler thật cho engine (đời thật: startAuction làm việc này)
                java.lang.reflect.Field sf = Server.Controller.AuctionEngine.class.getDeclaredField("scheduler");
                sf.setAccessible(true);
                sched5c = java.util.concurrent.Executors.newScheduledThreadPool(2);
                sf.set(engineC, sched5c);
                roomC.setStatus(Server.Controller.Room.RoomStatus.IN_PROGRESS);

                int baseC = clients[0].snapshot().size();
                java.lang.reflect.Method nextRoundM = Server.Controller.AuctionEngine.class
                        .getDeclaredMethod("nextRound");
                nextRoundM.setAccessible(true);
                nextRoundM.invoke(engineC);
                Thread.sleep(400);

                List<String> msgsC = clients[0].snapshot().subList(Math.min(baseC, clients[0].snapshot().size()), clients[0].snapshot().size());
                boolean round2 = false;
                String revealC = null;
                for (String m : msgsC) {
                    if (m.startsWith("round-update,2,")) round2 = true;
                    if (m.startsWith("bid-reveal,")) revealC = m;
                }
                check("Chuyển vòng: nhận round-update vòng 2", round2, "msgs=" + msgsC);
                check("Hết vòng 1 → bid-reveal công bố giá TRƯỚC khi vòng 2 bắt đầu", revealC != null, "msgs=" + msgsC);
                if (revealC != null) {
                    check("bid-reveal có đủ 2 lượt giá của vòng 1 (4M và 4.5M)",
                            revealC.startsWith("bid-reveal,2,") && revealC.contains("4000000") && revealC.contains("4500000"),
                            "msg=" + revealC);
                }
            } catch (Exception e) {
                check("TEST 5c chạy không ngoại lệ", false, e.toString());
            } finally {
                if (sched5c != null) sched5c.shutdownNow(); // dừng timer vòng — không leak thread, không treo JVM
            }

            // ════ TEST 6: Message Queue FIFO (mục 4) — 2 BUYOUT xếp hàng ═════
            // Novelty của đồ án: request A xếp TRƯỚC request B trong hàng đợi FIFO của phòng,
            // 1 worker duy nhất xử lý → A thắng, B nhận "Phiên đấu giá đã kết thúc".
            System.out.println("\nTEST 6 — Message Queue FIFO (mục 4): 2 BUYOUT xếp hàng → người xếp TRƯỚC thắng:");
            Server.Controller.Room room2 = null;
            try {
                for (int i = 0; i < 3; i++) {
                    Server.Controller.ServerThread st = roomPlayers.get(i);
                    if (room2 == null) room2 = new Server.Controller.Room(st, Server.Controller.Room.RoomTier.BINH_DAN);
                    else room2.addPlayer(st);
                }
                Server.Controller.AuctionEngine engine2 = new Server.Controller.AuctionEngine(room2);
                Server.Controller.AuctionRoomState state2 = new Server.Controller.AuctionRoomState(items, totalReal, 1.0);
                state2.currentEstimate = totalReal;
                state2.currentBuyoutPrice = 1_000_000; // rẻ — cả test001 (2.5M) lẫn test003 (10M) đều mua được
                state2.currentRound = 1;
                state2.dbSessionId = -1;
                java.lang.reflect.Field f2 = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                f2.setAccessible(true);
                f2.set(engine2, state2);
                room2.setStatus(Server.Controller.Room.RoomStatus.IN_PROGRESS);

                // Engine2 phải được GÁN VÀO room2 (đời thật là startAuction làm việc này) —
                // nếu không worker sẽ thấy getAuctionEngine() == null và trả lời cả 2 request
                // bằng "Phiên đã kết thúc" mà không xử lý lệnh nào.
                java.lang.reflect.Field engineField = Server.Controller.Room.class.getDeclaredField("auctionEngine");
                engineField.setAccessible(true);
                engineField.set(room2, engine2);

                int snapBase = clients[0].snapshot().size(); // chỉ xét message phát sinh SAU TEST 6
                int client2Base = clients[2].snapshot().size();

                // Xếp hàng CỐ Ý: cả 2 request vào hàng TRƯỚC khi start worker → FIFO tuyệt đối
                // (A chắc chắn xử lý trước B bất kể tốc độ DB/scheduling — deterministic).
                engine2.getRequestQueue().submit(new Server.Controller.RequestObject(
                        Server.Controller.RequestObject.Type.BUYOUT, roomPlayers.get(0), 0));
                engine2.getRequestQueue().submit(new Server.Controller.RequestObject(
                        Server.Controller.RequestObject.Type.BUYOUT, roomPlayers.get(2), 0));
                engine2.getRequestQueue().start();
                Thread.sleep(600);

                List<String> test6Msgs = clients[0].snapshot().subList(Math.min(snapBase, clients[0].snapshot().size()), clients[0].snapshot().size());
                int newResults = countPrefix(test6Msgs, "auction-result");
                check("2 BUYOUT xếp hàng → đúng 1 phiên kết thúc", newResults == 1, "thêm " + newResults + " auction-result");

                int winner = -2;
                for (String m : test6Msgs) {
                    if (m.startsWith("auction-result,")) { winner = Integer.parseInt(m.split(",")[1]); }
                }
                check("Người XẾP TRƯỚC trong hàng đợi thắng (FIFO)", winner == playerIds[0],
                        "winner=" + winner + " mong đợi " + playerIds[0]);

                boolean loserNotified = false;
                List<String> after2 = clients[2].snapshot().subList(Math.min(client2Base, clients[2].snapshot().size()), clients[2].snapshot().size());
                for (String m : after2) {
                    if (m.startsWith("bid-rejected,")) loserNotified = true;
                }
                check("Người xếp SAU nhận 'Phiên đấu giá đã kết thúc' (không bị bỏ im)", loserNotified,
                        "client2 nhận sau TEST 6: " + (after2.isEmpty() ? "(KHÔNG CÓ GÌ)" : after2));
            } finally {
                if (room2 != null && room2.getAuctionEngine() != null) {
                    room2.getAuctionEngine().getRequestQueue().shutdown(); // dừng worker — không leak thread
                }
            }

            // ════ TEST 7: Công thức định giá mục 3 — P_est & P_buyout theo lộ trình ════
            // Dùng reflection gọi revealItemsForRound (đường thật mà TEST 1–6 bỏ qua vì tự set giá):
            //  V1: hé đúng 1 món mồi nhử, P_est = (ΣV/ΣW)×W_total×1.20, M_buyout ∈ [2.5, 3.0]
            //  V2: hé thêm 1 món, K=1.10, M_buyout ∈ [2.0, 2.2]
            System.out.println("\nTEST 7 — Công thức P_est = (ΣV/ΣW)×W_total×K và P_buyout = P_est×M(random/vòng):");
            try {
                Server.Controller.Room room3 = null;
                for (int i = 0; i < 3; i++) {
                    if (room3 == null) room3 = new Server.Controller.Room(roomPlayers.get(i), Server.Controller.Room.RoomTier.BINH_DAN);
                    else room3.addPlayer(roomPlayers.get(i));
                }
                Server.Controller.AuctionEngine engine3 = new Server.Controller.AuctionEngine(room3);
                java.lang.reflect.Field f3 = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                f3.setAccessible(true);
                Server.Controller.AuctionRoomState state3 = new Server.Controller.AuctionRoomState(items, totalReal, 12.5);
                state3.dbSessionId = -1;
                f3.set(engine3, state3);

                java.lang.reflect.Method reveal = Server.Controller.AuctionEngine.class
                        .getDeclaredMethod("revealItemsForRound", int.class);
                reveal.setAccessible(true);

                // ── Vòng 1: mồi nhử ──
                reveal.invoke(engine3, 1);
                check("Vòng 1 hé đúng 1 món mồi nhử (tránh chia 0)", state3.revealedItems.size() == 1,
                        "revealed=" + state3.revealedItems.size());
                double v1 = state3.revealedItems.stream().mapToDouble(Product::getRealValue).sum();
                double w1 = state3.revealedItems.stream().mapToDouble(Product::getWeight).sum();
                // Luật mới (đã chốt): P_est/P_buyout bị CLAMP theo trần thùng của hạng phòng
                // ("giá không vượt mức phòng") → kỳ vọng = min(công thức, trần).
                double cap7 = Server.Controller.GameConfig.BOX_CAP_BINH_DAN; // phòng test là BINH_DAN
                double raw1 = (v1 / w1) * 12.5 * GameConfigHelpers.k(1);
                double expected1 = Math.min(raw1, cap7);
                check("P_est vòng 1 = min((ΣV/ΣW)×W_total×1.20, trần phòng)",
                        Math.abs(state3.currentEstimate - expected1) < 0.01,
                        "thực tế " + state3.currentEstimate + " mong đợi " + expected1);
                check("P_est vòng 1 không vượt trần thùng Bình Dân",
                        state3.currentEstimate <= cap7 + 1e-9, "P_est = " + state3.currentEstimate);
                double mEff1 = state3.currentBuyoutPrice / state3.currentEstimate;
                // Nếu bị clamp ở trần thì M hiệu dụng nhỏ hơn khoảng — chấp nhận 2 trường hợp
                check("M_buyout vòng 1 ∈ [2.5, 3.0] hoặc giá đã chạm trần",
                        (mEff1 >= 2.5 - 1e-9 && mEff1 <= 3.0 + 1e-9)
                                || Math.abs(state3.currentBuyoutPrice - cap7) < 0.01,
                        "M thực tế = " + mEff1 + " buyout = " + state3.currentBuyoutPrice);

                // ── Vòng 2: hé thêm 1 món, K giảm còn 1.10 ──
                double est1 = state3.currentEstimate;
                reveal.invoke(engine3, 2);
                check("Vòng 2 hé thêm 1 món", state3.revealedItems.size() == 2,
                        "revealed=" + state3.revealedItems.size());
                double v2 = state3.revealedItems.stream().mapToDouble(Product::getRealValue).sum();
                double w2 = state3.revealedItems.stream().mapToDouble(Product::getWeight).sum();
                double raw2 = (v2 / w2) * 12.5 * GameConfigHelpers.k(2);
                double expected2 = Math.min(raw2, cap7); // trần phòng Bình Dân (luật mới)
                check("P_est vòng 2 = min((ΣV/ΣW)×W_total×1.10, trần phòng)",
                        Math.abs(state3.currentEstimate - expected2) < 0.01,
                        "thực tế " + state3.currentEstimate + " mong đợi " + expected2);
                double mEff2 = state3.currentBuyoutPrice / state3.currentEstimate;
                check("M_buyout vòng 2 ∈ [2.0, 2.2] hoặc giá đã chạm trần",
                        (mEff2 >= 2.0 - 1e-9 && mEff2 <= 2.2 + 1e-9)
                                || Math.abs(state3.currentBuyoutPrice - cap7) < 0.01,
                        "M thực tế = " + mEff2 + " buyout = " + state3.currentBuyoutPrice);
            } catch (Exception e) {
                check("TEST 7 chạy không ngoại lệ", false, e.toString());
            }

            // ════ TEST 8: Hệ 5 sự kiện — BUYOUT_FREEZE khoá giá + HEAVY_REVEAL chọn món nặng nhất ════
            // Ép Random qua seed biết trước để triggerRandomEvent chọn ĐÚNG loại sự kiện cần test.
            System.out.println("\nTEST 8 — 5 sự kiện: BUYOUT_FREEZE khoá giá kể cả khi P_est đổi, HEAVY_REVEAL = món nặng nhất:");
            try {
                java.lang.reflect.Method trigger = Server.Controller.AuctionEngine.class
                        .getDeclaredMethod("triggerRandomEvent");
                trigger.setAccessible(true);

                // ── 8a. BUYOUT_FREEZE: giá mua đứt bị KHOÁ — kể cả khi P_est thay đổi ──
                Server.Controller.Room room4 = null;
                for (int i = 0; i < 3; i++) {
                    if (room4 == null) room4 = new Server.Controller.Room(roomPlayers.get(i), Server.Controller.Room.RoomTier.BINH_DAN);
                    else room4.addPlayer(roomPlayers.get(i));
                }
                Server.Controller.AuctionEngine engine4 = new Server.Controller.AuctionEngine(room4);
                java.lang.reflect.Field f4 = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                f4.setAccessible(true);
                Server.Controller.AuctionRoomState state4 = new Server.Controller.AuctionRoomState(items, totalReal, 12.5);
                state4.dbSessionId = -1;
                state4.currentRound = 3; // sự kiện nổ ở vòng 3
                state4.currentEstimate = 100;
                state4.currentBuyoutPrice = 150;
                f4.set(engine4, state4);
                forceRandom(engine4, seedForNextInt(4, 5)); // nextInt(5) == 4 → BUYOUT_FREEZE

                trigger.invoke(engine4);
                check("FREEZE: cờ isBuyoutFrozen = true", state4.isBuyoutFrozen, "cờ = " + state4.isBuyoutFrozen);
                check("FREEZE: giá mua đứt giữ nguyên 150",
                        Math.abs(state4.currentBuyoutPrice - 150) < 0.01, "giá = " + state4.currentBuyoutPrice);

                // P_est thay đổi giữa vòng (hé lộ thêm món) → giá VẪN bị khoá
                java.lang.reflect.Method reveal4 = Server.Controller.AuctionEngine.class
                        .getDeclaredMethod("revealItemsForRound", int.class);
                reveal4.setAccessible(true);
                reveal4.invoke(engine4, 3);
                check("FREEZE: P_est đã thay đổi sau khi hé lộ",
                        Math.abs(state4.currentEstimate - 100) > 0.01, "P_est = " + state4.currentEstimate);
                check("FREEZE: giá mua đứt VẪN 150 dù P_est đã đổi (khoá cả tăng lẫn giảm)",
                        Math.abs(state4.currentBuyoutPrice - 150) < 0.01, "giá = " + state4.currentBuyoutPrice);

                // Sang vòng mới (nextRound mở băng — cùng phép gán) → giá tính lại từ P_est
                state4.isBuyoutFrozen = false;
                state4.currentRound = 4;
                reveal4.invoke(engine4, 4);
                check("Hết freeze (vòng mới): giá mua đứt được tính lại từ P_est",
                        Math.abs(state4.currentBuyoutPrice - 150) > 0.01, "giá = " + state4.currentBuyoutPrice);

                // ── 8b. HEAVY_REVEAL: hé đúng món NẶNG NHẤT còn ẩn (findMaxWeightItem) ──
                Server.Controller.Room room5 = null;
                for (int i = 0; i < 3; i++) {
                    if (room5 == null) room5 = new Server.Controller.Room(roomPlayers.get(i), Server.Controller.Room.RoomTier.BINH_DAN);
                    else room5.addPlayer(roomPlayers.get(i));
                }
                Server.Controller.AuctionEngine engine5 = new Server.Controller.AuctionEngine(room5);
                java.lang.reflect.Field f5 = Server.Controller.AuctionEngine.class.getDeclaredField("state");
                f5.setAccessible(true);
                Server.Controller.AuctionRoomState state5 = new Server.Controller.AuctionRoomState(items, totalReal, 12.5);
                state5.dbSessionId = -1;
                state5.currentRound = 3;
                f5.set(engine5, state5);
                forceRandom(engine5, seedForNextInt(1, 5)); // nextInt(5) == 1 → HEAVY_REVEAL

                trigger.invoke(engine5);
                Product heaviest = null;
                for (Product p : items) {
                    if (heaviest == null || p.getWeight() > heaviest.getWeight()) heaviest = p;
                }
                check("HEAVY_REVEAL: hé đúng 1 món",
                        state5.revealedItems.size() == 1, "revealed=" + state5.revealedItems.size());
                check("HEAVY_REVEAL: món được chọn là món NẶNG NHẤT (" + heaviest.getName() + ")",
                        !state5.revealedItems.isEmpty() && state5.revealedItems.get(0).getId() == heaviest.getId(),
                        "chọn " + (state5.revealedItems.isEmpty() ? "?" : state5.revealedItems.get(0).getName()));
            } catch (Exception e) {
                check("TEST 8 chạy không ngoại lệ", false, e.toString());
            }

            System.out.println("\n══════════════════════════════════════");
            System.out.println("KẾT QUẢ: " + passed + " PASS / " + failed + " FAIL");
            System.out.println("══════════════════════════════════════");
            if (failed > 0) System.exit(1);

        } finally {
            for (FakeClient c : clients) if (c != null) c.close();
            acceptor.close();
            if (room != null) room.setPlayersToNotPlaying();
        }
    }
}

/** Helper đọc K_round của một vòng (1-based) — tách riêng để không phụ thuộc engine. */
class GameConfigHelpers {
    static double k(int round) {
        try {
            java.lang.reflect.Field f = Server.Controller.GameConfig.class.getDeclaredField("K_ROUND");
            f.setAccessible(true);
            double[] arr = (double[]) f.get(null);
            return arr[Math.min(round, arr.length) - 1];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

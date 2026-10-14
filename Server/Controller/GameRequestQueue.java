package Server.Controller;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GameRequestQueue — Hàng đợi thông điệp PHI ĐỒNG BỘ riêng cho TỪNG phòng
 * (mục 4 của thiết kế — novelty của đồ án: Message Queue chống Race Condition).
 *
 * Kiến trúc:
 *  - Receiver threads (ServerThread) KHÔNG xử lý logic game: khi nhận BID/BUYOUT,
 *    chúng đóng gói thành RequestObject (kèm timestamp NHẬN) và enqueue rồi lập tức
 *    rảnh tay nhận gói tin tiếp theo.
 *  - DUY NHẤT 1 worker thread mỗi phòng bốc request theo FIFO nghiêm ngặt và gọi
 *    AuctionEngine. Ví dụ Request A (mua đứt) xếp trước Request B (mua đứt) trong
 *    hàng đợi: worker xử lý A → chấp nhận → phiên CLOSED; bốc B → thấy phiên đã
 *    kết thúc → hủy lệnh của B và trả về thông báo lỗi "Phiên đấu giá đã kết thúc".
 *    → Không bao giờ có 2 người cùng mua thành công một thùng hàng.
 *  - Lựa chọn ConcurrentLinkedQueue (lock-free CAS) thay vì LinkedBlockingQueue:
 *    producer (luồng mạng) KHÔNG BAO GIỜ bị chặn — đúng tinh thần "luồng mạng rảnh
 *    tay" của thiết kế. Tính tuần tự đến từ việc chỉ có 1 worker tiêu thụ.
 *  - Kết thúc bằng DRAIN-ON-EMPTY (không dùng poison pill — pill có race: nếu phiên
 *    kết thúc nhanh, pill vào hàng trước request xếp sau và worker thoát sớm làm
 *    request đó không bao giờ được trả lời). Worker chỉ thoát khi HÀNG ĐỢI RỖNG và
 *    shutdown() đã được gọi → mọi request đã vào hàng luôn được xử lý và phản hồi,
 *    không leak thread nền (Phase 6).
 *  - Trong luồng thật, request tới SAU khi phiên kết thúc bị ServerThread chặn ngay
 *    ở bước kiểm tra room status (trả lời trực tiếp) — không bao giờ vào hàng.
 *
 * Phạm vi (đã chốt): queue per-room, chỉ chứa lệnh game (BID/BUYOUT) — login/chat/
 * lobby vẫn xử lý trực tiếp trên luồng nhận, không xếp hàng chéo phòng.
 */
public class GameRequestQueue {

    private final ConcurrentLinkedQueue<RequestObject> queue = new ConcurrentLinkedQueue<>();
    private final Room room;
    private volatile boolean started = false;
    private volatile boolean shutdownRequested = false;
    private Thread worker;

    public GameRequestQueue(Room room) {
        this.room = room;
    }

    /** Khởi động worker của phòng (gọi khi bắt đầu phiên đấu giá). */
    public synchronized void start() {
        if (started) return;
        started = true;
        worker = new Thread(this::processLoop, "game-worker-room-" + room.getId());
        worker.setDaemon(true); // JVM thoát không bị giữ lại
        worker.start();
    }

    /** Receiver thread gọi: chỉ enqueue, KHÔNG xử lý — rảnh tay ngay, không bao giờ chặn. */
    public void submit(RequestObject request) {
        queue.offer(request);
    }

    /** Yêu cầu worker dừng KHI ĐÃ xử lý hết hàng đợi (drain-on-empty) — không leak thread. */
    public void shutdown() {
        shutdownRequested = true;
    }

    private void processLoop() {
        while (true) {
            RequestObject request = queue.poll();
            if (request == null) {
                if (shutdownRequested) return; // hàng rỗng + đã yêu cầu dừng → thoát sạch
                // ConcurrentLinkedQueue.poll() không block → ngủ ngắn tránh busy-wait
                try { Thread.sleep(20); } catch (InterruptedException e) { return; }
                continue;
            }

            AuctionEngine engine = room.getAuctionEngine();
            if (engine == null) {
                // Phiên đã dọn dẹp (kết thúc/disconnect) — trả lời sạch sẽ request còn tồn
                // thay vì bỏ im (client chờ hoài không thấy phản hồi).
                if (request.sender != null) {
                    try { request.sender.write("bid-rejected,Phiên đấu giá đã kết thúc"); }
                    catch (IOException ignored) {}
                }
                continue;
            }

            try {
                switch (request.type) {
                    case BID:    engine.onBid(request.sender, request.amount); break;
                    case BUYOUT: engine.onBuyout(request.sender); break;
                    default:     break;
                }
            } catch (Exception e) {
                // Worker phải sống sót qua mọi lỗi của 1 request — không được chết cùng phiên
                System.out.println("[GameRequestQueue Room " + room.getId() + "] Lỗi xử lý " + request.type + ": " + e);
                e.printStackTrace();
            }
        }
    }
}

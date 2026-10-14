package Server.Controller;

/**
 * RequestObject — gói tin lệnh game khi vào Hàng đợi thông điệp (mục 4 của thiết kế).
 *
 * Được đóng gói NGAY khi luồng mạng (ServerThread) nhận được message, kèm Timestamp
 * là nhãn thời gian NHẬN của server (System.nanoTime) — KHÔNG tin đồng hồ client.
 * Sau đó đẩy vào GameRequestQueue và luồng mạng lập tức rảnh tay nhận gói tiếp theo.
 *
 * Immutable → an toàn khi băng qua thread mà không cần lock.
 */
public class RequestObject {

    public enum Type { BID, BUYOUT, START_AUCTION, SHUTDOWN }

    public final Type type;
    public final ServerThread sender;    // null với SHUTDOWN
    public final double amount;          // BID: số tiền trả giá; loại khác: 0
    public final long receivedAtNanos;   // Timestamp server NHẬN lệnh

    public RequestObject(Type type, ServerThread sender, double amount) {
        this.type = type;
        this.sender = sender;
        this.amount = amount;
        this.receivedAtNanos = System.nanoTime();
    }

    /** Poison pill: đánh dấu worker dừng khi phiên kết thúc. */
    public static RequestObject poisonPill() {
        return new RequestObject(Type.SHUTDOWN, null, 0);
    }
}

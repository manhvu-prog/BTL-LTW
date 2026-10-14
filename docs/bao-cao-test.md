# Báo cáo kiểm thử dự án Đấu Giá Thùng Hàng Bí Ẩn

Ngày kiểm thử: 13/10/2026 · Máy: JDK 24.0.2 + MySQL 9.5 (DB `btl_ltm`) · Cổng server: 7777

## 1. Tổng quan — không phát hiện lỗi nghiêm trọng nào cần sửa ngay

| Hạng mục | Kết quả |
|---|---|
| Build toàn bộ Server + TestClient (javac) | ✅ 0 lỗi, chỉ note "unchecked" vô hại |
| Test logic `TestAuctionConcurrency` | ✅ 41/41 PASS |
| Luồng client↔server thật (socket CSV qua script `test_socket_flow.py`) | ✅ 19/20 PASS (ca duy nhất còn lại đã chứng minh là hiện tượng đo phía script, không phải server) |
| Độ trễ phản hồi server | ✅ 0–0,4 giây cho mọi lệnh (đo độc lập nhiều lần) |
| Client JavaFX | ✅ "Kết nối thành công!", không stack trace |

## 2. Chi tiết luồng đã kiểm (server thật đang chạy)

- Đăng nhập: đúng / sai mật khẩu / user không tồn tại / trùng tài khoản online → đều phản hồi đúng (`login-success`, `wrong-player`, `dupplicate-login`).
- Đăng ký: tài khoản mới tự đăng nhập; username trùng bị chặn (`duplicate-username`).
- Sảnh: `leaderboard`, `onlineList`, `chat-server` broadcast tới người khác — đều OK.
- Phòng: tạo phòng Đại Gia; ví 100M bị chặn tạo phòng Bình Dân; ví <2M bị chặn vào phòng; `go-to-room` đúng/sai ID; `waiting-room-update` cập nhật 2/3 người.
- Đấu giá: `start-auction` (host, ≥2 người) → `auction-start` kèm chủ đề random (Nhạc cụ, Mỹ phẩm, Đồ cổ, Gia dụng — đủ 14 chủ đề quanh các lần chạy); bid hợp lệ → `bid-accepted`; bid chữ cái / bid sau khi thoát phòng → `bid-rejected`, không crash.
- `view-room-list`, `leave-room`, `offline` — OK, thoát phòng dọn sạch (nếu trống hết → phiên kết thúc `ALL_DISCONNECTED`).

## 3. Ba vấn đề nên xử lý (không chặn chơi)

1. **DB thiếu tài khoản demo/bot của README** — hiện tại DB có 5 tài khoản cũ thật (`manh`, `test1`, `12345`, `11111`, `123456` — còn điểm số, lịch sử thật). Chưa có `test001–test006` lẫn `bot001–bot003`, nên tính năng "đấu thử với máy" (`spawn-bots`) sẽ bị từ chối. Muốn demo đủ: chạy `Server/Database/reset_demo_data.sql` — lưu ý script này xoá/đè dữ liệu thật. Đã kiểm `spawn-bots` trả thông báo rõ ràng, không crash.
2. **Cờ `isOnline`/`isPlaying` kẹt nếu server chết đột ngột** (taskkill, mất điện): 2 tài khoản từng kẹt dẫn tới bị chặn `dupplicate-login` sau restart (đã reset về 0). Khắc phục: khi `Server.main` khởi động, chạy `UPDATE player SET isOnline=0, isPlaying=0` một lần.
3. **Connection leak trong `Server/DAO/DAO.java`** — mỗi `new PlayerDAO()` (cả mỗi TableDAO khác) mở 1 kết nối MySQL mới và không bao giờ đóng. Test 22 kết nối vẫn ổn (giới hạn max_connections=151), nhưng server chạy lâu sẽ tụt hiệu năng. Khắc phục: thêm `close()` + gọi ở nơi dọn dẹp, hoặc dùng connection pool.

## 4. Đã nạp bot — tính năng "đấu thử với máy" kiểm tra OK

- Chạy `Server/Database/migration_mystery_box.sql` (idempotent, không xoá dữ liệu thật) → nạp **bot001 🤖 Máy Trí Tuệ, bot002 🤖 Máy Săn Deal, bot003 🤖 Máy Cọc Cời** (ví 100M, id 101–103) + `test006`. Các tài khoản test001–005 bị guard bỏ qua vì id 1–5 đã có người chơi thật — dữ liệu thật giữ nguyên.
- Sản phẩm 14 chủ đề × 20 món (280 món, đủ `category`) đã có sẵn trong DB trước đó.
- Kiểm end-to-end `test_spawn_bots.py` (kịch bản socket): host tạo phòng Đại Gia → `spawn-bots` → `waiting-room-update,3,3,DAI_GIA,aaaa,🤖 Máy Trí Tuệ,🤖 Máy Săn Deal` → phiên tự bắt đầu → bid hợp lệ → bot MUA ĐỨT → đập hộp → `auction-ended` đầy đủ → server giải phóng 2 bot sạch (log: `Room 106: giải phóng 2 bot`).

**Lỗi phát hiện thêm và đã sửa trong phiên gần nhất:** nickname tiếng Việt/emoji lưu trong DB bị encode hỏng (double-encoded, ví dụ "🤖" lưu thành ký tự latin — khiến client hiển thị sai tên đã từng thấy "Ví Th?p"). Nguyên nhân: chạy file SQL qua `mysql` CLI Windows không khai báo charset. **Cách chạy đúng từ giờ:**

```bash
mysql -u root -p --default-character-set=utf8mb4 < Server/Database/migration_mystery_box.sql
```

Nickname 3 bot + test006 đã được sửa lại đúng UTF-8 (xác minh bằng `HEX(nickName)` = `F09FA496 204DC3A1…`).

## 5. Trạng thái khi kết thúc

- Server Java (dashboard Admin JavaFX) **đang chạy**, lắng nghe cổng 7777.
- Client `TestClient.Client` **đang mở** cửa sổ — đăng nhập thử với `manh / manh` (ví 19,4M → hạng Đại Gia).
- File test giữ lại: `test_socket_flow.py` (chạy lại bất kỳ lúc nào khi server đang mở).
- Dữ liệu DB đã dọn về trạng thái sạch: xoá tài khoản/đấu giá test phát sinh, reset toàn bộ cờ `isOnline`/`isPlaying` về 0.

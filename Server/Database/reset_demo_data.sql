-- ============================================================================
-- RESET DATA DEMO — GAME "ĐẤU GIÁ THÙNG HÀNG BÍ ẨN" (Mystery Box Auction)
-- ============================================================================
-- MỤC ĐÍCH: đưa database về trạng thái "mới cài" để demo/nộp bài — KHÔNG đụng
-- đến cấu trúc bảng, KHÔNG xóa tài khoản, KHÔNG xóa vật phẩm.
--
-- Script này xử lý sạch các "rác" tích tụ khi test:
--   1. Lịch sử đấu giá (AuctionSession, AuctionSessionItem, BidHistory)
--   2. Lịch sử chế độ chơi cũ (GameMatch)
--   3. Thống kê win/lose/draw/score + ví tiền của 6 tài khoản test & 3 bot
--   4. Cờ isOnline/isPlaying bị "treo" khi server tắt đột ngột
--   5. Nickname bị lỗi UTF-8 (chèn nhầm qua kết nối latin1) — khôi phục đúng dấu
--
-- Cách chạy:
--   "/c/Program Files/MySQL/MySQL Server 9.5/bin/mysql" -u root -p < Server/Database/reset_demo_data.sql
--   (hoặc trong mysql client:  SOURCE Server/Database/reset_demo_data.sql;)
--
-- Chạy lại an toàn nhiều lần (idempotent). Khác với migration_mystery_box.sql
-- (dùng khi CHƯA có database), script này dùng khi ĐÃ có database đầy đủ.
-- ============================================================================

USE btl_ltm;

-- ============================================================================
-- 1) DỌN LỊCH SỬ ĐẤU GIÁ
--    BidHistory + AuctionSessionItem có ON DELETE CASCADE theo AuctionSession
--    nhưng DELETE trực tiếp từng bảng vẫn an toàn và chạy được kể cả khi
--    foreign key checks đang bật.
-- ============================================================================
DELETE FROM BidHistory;
DELETE FROM AuctionSessionItem;
DELETE FROM AuctionSession;
DELETE FROM GameMatch;

-- ============================================================================
-- 2) RESET THỐNG KÊ + VÍ 6 TÀI KHOẢN TEST (đã chốt: MỌI tài khoản 100.000.000)
--    Lưu ý: 100M thuộc hạng ĐẠI GIA (>10M) — hạng Bình Dân là 2–10M.
-- ============================================================================
UPDATE Player SET win=2, lose=1, draw=1, score=1.5,  walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Nguyễn Văn A' WHERE username='test001';
UPDATE Player SET win=1, lose=2, draw=1, score=0,    walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Trần Thị B'   WHERE username='test002';
UPDATE Player SET win=3, lose=0, draw=0, score=3,    walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Lê Văn C'     WHERE username='test003';
UPDATE Player SET win=0, lose=3, draw=0, score=-1.5, walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Phạm Văn D'   WHERE username='test004';
UPDATE Player SET win=1, lose=1, draw=2, score=0.5,  walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Hoàng Thị E'  WHERE username='test005';
UPDATE Player SET win=0, lose=0, draw=0, score=0,    walletBalance=100000000, isOnline=0, isPlaying=0, nickName='Vũ Thị F'     WHERE username='test006';

-- ============================================================================
-- 3) RESET 3 BOT "ĐẤU THỬ VỚI MÁY" (ví 100M như người chơi thật — đã chốt)
--    Server mượn BOT_COUNT = 2 bot (bot001–bot002) cho phòng 3 người; bot003 giữ
--    lại làm dự phòng. isOnline/isPlaying = 0 đề phòng bot bị "mượn" treo (server tắt giữa phiên
--    khiến updateToNotPlaying không kịp chạy — nếu treo thì spawn-bots sẽ không
--    mượn được tài khoản).
-- ============================================================================
UPDATE Player SET win=0, lose=0, draw=0, score=0, walletBalance=100000000, isOnline=0, isPlaying=0, nickName='🤖 Máy Trí Tuệ'  WHERE username='bot001';
UPDATE Player SET win=0, lose=0, draw=0, score=0, walletBalance=100000000, isOnline=0, isPlaying=0, nickName='🤖 Máy Săn Deal' WHERE username='bot002';
UPDATE Player SET win=0, lose=0, draw=0, score=0, walletBalance=100000000, isOnline=0, isPlaying=0, nickName='🤖 Máy Cọc Cời'  WHERE username='bot003';

-- An toàn bổ sung: mọi tài khoản không ai dùng thì không được đóng dấu đang chơi/online
UPDATE Player SET isOnline=0, isPlaying=0;

-- ============================================================================
-- 4) BẢO ĐẢM UTF-8 ĐÚNG CHO NICKNAME
--    UPDATE ở trên đã gán chuỗi UTF-8 trực tiếp; nếu file này được nạp qua kết
--    nối latin1 thì emoji 🤖 có thể vỡ — câu dưới ép lại bằng từ vựng hex
--    (F09FA496 = 🤖 trong UTF-8) để LUÔN đúng dù client kết nối bằng charset nào.
--    Khi cả hai cùng chạy, kết quả cuối vẫn là nickname đúng.
-- ============================================================================
UPDATE Player SET nickName=CONVERT(UNHEX('F09FA496') USING utf8mb4) WHERE username='bot001' AND nickName NOT LIKE '🤖%';

-- ============================================================================
-- KIỂM TRA SAU KHI CHẠY (copy từng câu):
--   USE btl_ltm;
--   SELECT COUNT(*) FROM AuctionSession;   -- 0
--   SELECT COUNT(*) FROM BidHistory;       -- 0
--   SELECT username, nickName, walletBalance, win, lose, isOnline, isPlaying
--     FROM Player WHERE username LIKE 'test%' OR username LIKE 'bot%';
--   → nickname có dấu + emoji đúng, ví đúng seed, mọi cờ = 0
-- ============================================================================

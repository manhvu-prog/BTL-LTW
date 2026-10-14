-- ============================================================================
-- MIGRATION — GAME "ĐẤU GIÁ THÙNG HÀNG BÍ ẨN" (Mystery Box Auction)
-- ============================================================================
-- ⭐ BẢN ALL-IN-ONE: chạy được trên máy CHƯA CÓ database (tự tạo DB + bảng + seed)
--    và chạy lại an toàn trên máy ĐÃ CÓ dữ liệu (idempotent, KHÔNG mất dữ liệu).
--
-- Cách dùng:
--   mysql -u root -p < Server/Database/migration_mystery_box.sql
--   (hoặc trong mysql client:  SOURCE Server/Database/migration_mystery_box.sql;)
--
-- Sản phẩm của file này:
--   Database btl_ltm với 6 bảng: Player, Product, GameMatch,
--   AuctionSession, AuctionSessionItem, BidHistory
--   + 6 tài khoản test + 10 vật phẩm mẫu.
--
-- Lưu ý: file này KHÔNG chứa mật khẩu MySQL — mật khẩu nằm trong
--        Server/DAO/DAO.java (JDBC_USER / JDBC_PASSWORD).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS btl_ltm
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE btl_ltm;

-- ============================================================================
-- 1) BẢNG PLAYER
-- ============================================================================
CREATE TABLE IF NOT EXISTS Player (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    nickName      VARCHAR(255) NULL,
    avatar        VARCHAR(255) NULL,
    win           INT          DEFAULT 0 NULL,
    lose          INT          DEFAULT 0 NULL,
    draw          INT          DEFAULT 0 NULL,
    score         DOUBLE       DEFAULT 0 NULL,
    walletBalance DOUBLE       DEFAULT 100000000 NULL,  -- số dư ví (mặc định 100 triệu — đã chốt)
    isOnline      TINYINT(1)   DEFAULT 0 NULL,
    isPlaying     TINYINT(1)   DEFAULT 0 NULL
);

-- Các máy cũ có thể thiếu cột mới → thêm nếu chưa có (idempotent)
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'btl_ltm' AND TABLE_NAME = 'Player'
               AND COLUMN_NAME = 'walletBalance');
SET @sql := IF(@col = 0,
    'ALTER TABLE Player ADD COLUMN walletBalance DOUBLE DEFAULT 100000000',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2) BẢNG PRODUCT (Master Data — vật phẩm gốc)
-- ============================================================================
CREATE TABLE IF NOT EXISTS Product (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    amount    VARCHAR(255) NULL,
    price     DOUBLE       NULL,                    -- giá thị trường tham khảo (hiển thị)
    weight    DOUBLE       NOT NULL DEFAULT 1.0,    -- khối lượng (kg)
    realValue DOUBLE       NOT NULL DEFAULT 50000,  -- giá trị thực (bí mật với client)
    picture   VARCHAR(255) NULL
);

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'btl_ltm' AND TABLE_NAME = 'Product'
               AND COLUMN_NAME = 'weight');
SET @sql := IF(@col = 0,
    'ALTER TABLE Product ADD COLUMN weight DOUBLE NOT NULL DEFAULT 1.0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'btl_ltm' AND TABLE_NAME = 'Product'
               AND COLUMN_NAME = 'realValue');
SET @sql := IF(@col = 0,
    'ALTER TABLE Product ADD COLUMN realValue DOUBLE NOT NULL DEFAULT 50000',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3) BẢNG GAMEMATCH (chế độ chơi cũ — giữ lại để tương thích Admin)
-- ============================================================================
CREATE TABLE IF NOT EXISTS GameMatch (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    player1ID INT NOT NULL,
    player2ID INT NOT NULL,
    winnerID  INT NULL,
    productID INT NULL,
    CONSTRAINT GameMatch_ibfk_1 FOREIGN KEY (productID) REFERENCES Product (id)
);

-- ============================================================================
-- 4) BẢNG AUCTIONSESSION (một phiên đấu giá thùng hàng bí ẩn)
-- ============================================================================
CREATE TABLE IF NOT EXISTS AuctionSession (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    roomId           INT         NOT NULL,
    roomTier         VARCHAR(50) NOT NULL,          -- BINH_DAN / KHA_GIA / DAI_GIA
    totalRealValue   DOUBLE      NOT NULL,          -- BÍ MẬT với client khi phiên diễn ra
    totalWeight      DOUBLE      NOT NULL,
    initialEstimate  DOUBLE      NOT NULL,          -- P_est của vòng 1
    finalBuyoutPrice DOUBLE      NULL,              -- NULL nếu phiên bị huỷ
    winnerPlayerId   INT         NULL,
    winningPrice     DOUBLE      NULL,              -- NULL nếu phiên bị huỷ
    endedByBuyout    TINYINT(1)  DEFAULT 0,
    createdAt        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 5) BẢNG AUCTIONSESSIONITEM (thùng hàng của một phiên)
-- ============================================================================
CREATE TABLE IF NOT EXISTS AuctionSessionItem (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    auctionSessionId INT NOT NULL,
    productId        INT NOT NULL,
    revealedAtRound  INT NULL,  -- vòng nào món được hé lộ (NULL = chưa lộ khi kết thúc)
    CONSTRAINT AuctionSessionItem_ibfk_1
        FOREIGN KEY (auctionSessionId) REFERENCES AuctionSession (id) ON DELETE CASCADE,
    CONSTRAINT AuctionSessionItem_ibfk_2
        FOREIGN KEY (productId) REFERENCES Product (id)
);

-- ============================================================================
-- 6) BẢNG BIDHISTORY (lịch sử trả giá / mua đứt)
-- ============================================================================
CREATE TABLE IF NOT EXISTS BidHistory (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    auctionSessionId INT        NOT NULL,
    playerId         INT        NOT NULL,
    round            INT        NOT NULL,
    amount           DOUBLE     NOT NULL,
    isBuyout         TINYINT(1) DEFAULT 0,
    createdAt        TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT BidHistory_ibfk_1
        FOREIGN KEY (auctionSessionId) REFERENCES AuctionSession (id) ON DELETE CASCADE,
    CONSTRAINT BidHistory_ibfk_2
        FOREIGN KEY (playerId) REFERENCES Player (id)
);

-- ============================================================================
-- SEED 1 — 10 VẬT PHẨM MẪU (chỉ thêm khi chưa tồn tại theo id)
-- ============================================================================
INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 1, 'Nước mắm Nam Ngư', '10 chai', 35000, 0.5, 35000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 1);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 2, 'Bánh Trung Thu Kinh Đô', '5 hộp', 250000, 1.2, 250000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 2);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 3, 'Cà phê G7', '20 gói', 50000, 0.3, 50000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 3);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 4, 'Mì Hảo Hảo', '30 gói', 80000, 2.0, 80000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 4);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 5, 'Nước ngọt Coca Cola', '12 lon', 150000, 3.5, 150000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 5);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 6, 'Tai nghe Bluetooth Sony', '1 cái', 1200000, 0.4, 1200000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 6);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 7, 'Đồng hồ thông minh Apple Watch', '1 cái', 5000000, 0.3, 4800000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 7);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 8, 'Nồi chiên không dầu Philips', '1 cái', 2200000, 5.0, 2100000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 8);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 9, 'Bàn phím cơ Akko', '1 cái', 1500000, 1.1, 1450000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 9);

INSERT INTO Product (id, name, amount, price, weight, realValue, picture)
SELECT 10, 'Sạc dự phòng Anker 20000mAh', '1 cái', 800000, 0.5, 750000, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = 10);

-- ============================================================================
-- SEED 2 — 6 TÀI KHOẢN TEST (MỌI TÀI KHOẢN ĐỀU CÓ VÍ 100.000.000 — đã chốt)
--   Lưu ý matchmaking theo hạng ví: 100M thuộc hạng ĐẠI GIA [20M+] — muốn chơi
--   Bình Dân/Khá Giả phải tự giảm ví hoặc nới ngưỡng hạng (Room.RoomTier).
-- ============================================================================
INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 1, 'test001', 'Test@001', 'Nguyễn Văn A', NULL, 2, 1, 1, 1.5, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 1);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 2, 'test002', 'Test@002', 'Trần Thị B', NULL, 1, 2, 1, 0, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 2);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 3, 'test003', 'Test@003', 'Lê Văn C', NULL, 3, 0, 0, 3, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 3);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 4, 'test004', 'Test@004', 'Phạm Văn D', NULL, 0, 3, 0, -1.5, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 4);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 5, 'test005', 'Test@005', 'Hoàng Thị E', NULL, 1, 1, 2, 0.5, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 5);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 6, 'test006', 'Test@006', 'Vũ Thị F', NULL, 0, 0, 0, 0, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 6);

-- ----------------------------------------------------------------------------
-- 3 TÀI KHOẢN BOT (đấu thử với máy): tất cả đều có ví 100M như người chơi thật.
-- ----------------------------------------------------------------------------
INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 101, 'bot001', 'Bot@001', '🤖 Máy Trí Tuệ', NULL, 0, 0, 0, 0, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 101);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 102, 'bot002', 'Bot@002', '🤖 Máy Săn Deal', NULL, 0, 0, 0, 0, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 102);

INSERT INTO Player (id, username, password, nickName, avatar, win, lose, draw, score, walletBalance, isOnline, isPlaying)
SELECT 103, 'bot003', 'Bot@003', '🤖 Máy Cọc Cời', NULL, 0, 0, 0, 0, 100000000, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Player WHERE id = 103);

-- Các máy cũ đã có user test001–test005 nhưng ví lệch → cân lại về 100M (idempotent)
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test001';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test002';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test003';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test004';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test005';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'test006';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'bot001';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'bot002';
UPDATE Player SET walletBalance = 100000000 WHERE username = 'bot003';

-- ============================================================================
-- KIỂM TRA NHANH SAU KHI CHẠY (copy từng câu để chạy):
--   USE btl_ltm;
--   SHOW TABLES;                                   -- 6 bảng
--   SELECT COUNT(*) FROM Product;                  -- >= 10
--   SELECT username, walletBalance FROM Player;    -- 6 user test
-- ============================================================================
-- LƯU Ý: file này chỉ cần chạy MỘT LẦN khi CHƯA có database.
-- Sau khi test/demo làm "bẩn" dữ liệu (lịch sử đấu giá, thống kê lệch, ví đổi,
-- nickname vỡ UTF-8...), chạy script reset để về trạng thái mới cài:
--   mysql -u root -p < Server/Database/reset_demo_data.sql

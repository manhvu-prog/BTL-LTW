-- ============================================================================
-- MIGRATION 2 — CHỦ ĐỀ THÙNG HÀNG (14 chủ đề × 20 món) + 2 MỨC PHÒNG
-- ============================================================================
-- Chạy:  mysql -u root -p < Server/Database/migration_themes.sql
-- Chạy lại an toàn nhiều lần (idempotent, KHÔNG mất dữ liệu player).
--
-- Nội dung:
--   1. Product:        thêm cột category VARCHAR(50) NULL
--   2. AuctionSession: thêm cột theme VARCHAR(100) NULL
--   3. Seed 14 chủ đề × 20 món = 280 sản phẩm (id 11–290)
--      10 món cũ id 1–10 giữ nguyên (gán chủ đề gần đúng).
--   4. Giá trị theo 2 mức phòng mới (trần thùng):
--      BINH_DAN (ví 2–10M):  trần 10.000.000
--      DAI_GIA  (ví >10M):   trần 50.000.000
--      → Mỗi chủ đề: 10 món THẤP (80k–180k) + 10 món CAO (0.6M–3.5M).
-- ============================================================================

SET NAMES utf8mb4;
USE btl_ltm;

-- ============================================================================
-- 1) CỘT MỚI (idempotent)
-- ============================================================================
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'btl_ltm' AND TABLE_NAME = 'Product'
               AND COLUMN_NAME = 'category');
SET @sql := IF(@col = 0,
    'ALTER TABLE Product ADD COLUMN category VARCHAR(50) NULL AFTER realValue',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'btl_ltm' AND TABLE_NAME = 'AuctionSession'
               AND COLUMN_NAME = 'theme');
SET @sql := IF(@col = 0,
    'ALTER TABLE AuctionSession ADD COLUMN theme VARCHAR(100) NULL AFTER roomTier',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Gán chủ đề gần đúng cho 10 món cũ
UPDATE Product SET category='THUC_PHAM' WHERE category IS NULL AND id IN (1,2,3,4,5);
UPDATE Product SET category='CONG_NGHE' WHERE category IS NULL AND id IN (6,7,9,10);
UPDATE Product SET category='GIA_DUNG'  WHERE category IS NULL AND id = 8;

-- ============================================================================
-- 2) 280 SẢN PHẨM (id 11–290) — 14 chủ đề × 20 món
--    Mỗi chủ đề: 10 món 80k–180k (phòng Bình Dân) + 10 món 0.6M–3.5M (Đại Gia)
-- ============================================================================
INSERT INTO Product (id, name, amount, price, weight, realValue, category, picture)
SELECT * FROM (
  -- ── CONG_NGHE — 💻 Công nghệ ────────────────────────────────────────────
  SELECT 11 AS id,'Tai nghe chụp tai Sony WH-1000' AS name,'1 cái' AS amount,850000.0 AS price,0.3 AS weight,820000.0 AS realValue,'CONG_NGHE' AS category,'theme_cong_nghe.png' AS picture
  UNION ALL SELECT 12,'Bàn phím cơ Keychron K2','1 cái',2400000,1.1,2250000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 13,'Chuột Logitech G Pro','1 cái',1900000,0.15,1800000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 14,'SSD NVMe 1TB Samsung 980','1 cái',2100000,0.1,2000000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 15,'Sạc dự phòng 20000mAh Anker','1 cái',950000,0.45,900000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 16,'Màn hình 24 inch Dell','1 cái',3200000,4.5,3000000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 17,'Webcam Logitech C920','1 cái',1400000,0.3,1300000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 18,'Loa bluetooth JBL Go 3','1 cái',890000,0.25,850000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 19,'Cáp HDMI 2.1 (2m)','1 sợi',350000,0.2,150000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 20,'Router WiFi 6 TP-Link','1 cái',1500000,0.8,1400000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 21,'Smartwatch Xiaomi Mi Band 8','1 cái',700000,0.05,650000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 22,'Ổ cứng di động 2TB WD','1 cái',2600000,0.2,2400000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 23,'Bộ loa máy tính 2.1','1 bộ',750000,3.0,700000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 24,'Đèn bàn LED thông minh','1 cái',600000,0.9,560000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 25,'Giá đỡ laptop nhôm','1 cái',550000,1.2,520000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 26,'Bộ phát WiFi di động 4G','1 cái',1100000,0.2,1000000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 27,'Sạc nhanh 65W GaN','1 cái',650000,0.15,600000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 28,'Bộ dụng cụ sửa máy tính 25 món','1 bộ',480000,1.0,120000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 29,'Tản nhiệt CPU Air Cooler','1 cái',950000,0.85,890000,'CONG_NGHE','theme_cong_nghe.png'
  UNION ALL SELECT 30,'Thẻ nhớ microSD 256GB','1 cái',420000,0.01,390000,'CONG_NGHE','theme_cong_nghe.png'
  -- ── TRANG_SUC — 💎 Trang sức ────────────────────────────────────────────
  UNION ALL SELECT 31,'Dây chuyền bạc Ý','1 sợi',450000,0.05,420000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 32,'Nhẫn bạc nữ đính đá CZ','1 cái',320000,0.01,290000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 33,'Bông tai ngọc trai nước ngọt','1 đôi',1500000,0.02,1400000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 34,'Lắc tay bạc hạt phong thủy','1 cái',520000,0.04,480000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 35,'Dây chuyền vàng Ý 18K','1 sợi',3200000,0.08,3000000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 36,'Nhẫn cưới platinum','1 cái',3500000,0.06,3300000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 37,'Vòng tay đá mã não','1 cái',680000,0.06,640000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 38,'Mặt dây chuyền đá ruby','1 cái',2800000,0.03,2600000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 39,'Bộ trang sức bạc trẻ em','1 bộ',900000,0.09,850000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 40,'Hộp đựng trang sức gỗ','1 cái',380000,0.8,150000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 41,'Nhẫn titan nam','1 cái',750000,0.02,700000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 42,'Lắc chân bạc chuông','1 cái',280000,0.03,100000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 43,'Dây chuyền đá thạch anh','1 sợi',1200000,0.05,1150000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 44,'Kiềng vàng 24K mini','1 cái',3100000,0.05,2900000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 45,'Bông tai bạc hình sao','1 đôi',190000,0.01,90000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 46,'Vòng tay cẩm thạch','1 cái',2400000,0.07,2200000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 47,'Nhẫn đá lưu ly','1 cái',850000,0.02,800000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 48,'Dây chuyền theo tên bạc','1 sợi',680000,0.04,630000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 49,'Bộ nhẫn + dây couple bạc','1 bộ',1100000,0.06,1050000,'TRANG_SUC','theme_trang_suc.png'
  UNION ALL SELECT 50,'Nhẫn kim cương nhỏ 0.2ct','1 cái',3500000,0.01,3200000,'TRANG_SUC','theme_trang_suc.png'
  -- ── O_TO — 🚗 Ô tô ──────────────────────────────────────────────────────
  UNION ALL SELECT 51,'Camera hành trình full HD','1 cái',1800000,0.4,1700000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 52,'Tinh dầu thơm ô tô','1 chai',350000,0.3,130000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 53,'Mô hình xe Ferrari tỷ lệ 1:24','1 cái',950000,0.9,890000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 54,'Bộ sạc nhanh cho ô tô','1 cái',650000,0.2,600000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 55,'Túi giữ nhiệt mini xe hơi','1 cái',480000,0.8,120000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 56,'Đèn pha LED trợ sáng','1 đôi',2200000,0.9,2050000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 57,'Máy hút bụi mini xe hơi','1 cái',1250000,1.1,1150000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 58,'Ghế tựa gối nhớ hình','1 bộ',720000,1.3,670000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 59,'Cảm biến áp suất lốp','1 bộ',1450000,0.3,1350000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 60,'Tấm lót sàn EVA cao cấp','1 bộ',2900000,4.0,2700000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 61,'Đồng hồ bấm giờ pit-lane','1 cái',880000,0.1,820000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 62,'Kính áp tròng lái xe ban đêm','1 cái',560000,0.1,520000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 63,'Bộ đồ nghề kiểm tra lốp','1 bộ',620000,1.0,140000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 64,'Sạc dự phòng jump starter 12V','1 cái',3100000,1.8,2900000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 65,'Mô hình Lamborghini 1:18','1 cái',3300000,2.2,3050000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 66,'Gương cầu lồi hỗ trợ','1 cái',260000,0.2,90000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 67,'Bình xịt vệ sinh nội thất','1 chai',320000,0.5,90000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 68,'Móc treo đồ ghế sau','1 cặp',180000,0.3,85000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 69,'Thảm sưởi ghế 12V','1 bộ',1650000,2.5,1550000,'O_TO','theme_o_to.png'
  UNION ALL SELECT 70,'Bộ sáp đánh bóng cao cấp','1 bộ',540000,0.7,110000,'O_TO','theme_o_to.png'
  -- ── THOI_TRANG — 👕 Thời trang ──────────────────────────────────────────
  UNION ALL SELECT 71,'Áo thun cotton local brand','1 cái',450000,0.3,140000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 72,'Giày sneaker trắng da','1 đôi',1500000,0.9,1400000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 73,'Túi đeo chéo da PU','1 cái',780000,0.5,730000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 74,'Mũ lưỡi trai thêu','1 cái',220000,0.1,90000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 75,'Áo khoác gió chống nước','1 cái',920000,0.6,860000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 76,'Đồng hồ quartz dây da','1 cái',1900000,0.3,1750000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 77,'Kính mát UV400','1 cái',680000,0.2,630000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 78,'Váy hoa dạo phố','1 cái',850000,0.4,790000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 79,'Quần jean slim fit','1 cái',950000,0.7,890000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 80,'Thắt lưng da bò chính hãng','1 cái',1250000,0.3,1150000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 81,'Balo laptop 15 inch','1 cái',1350000,0.8,1250000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 82,'Ví da nam gập','1 cái',760000,0.2,710000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 83,'Tất cotton cao cổ (5 đôi)','1 bộ',350000,0.3,110000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 84,'Áo polo pique','1 cái',580000,0.35,540000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 85,'Sandals đi mưa','1 đôi',420000,0.6,110000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 86,'Khăn quàng len lụa','1 cái',520000,0.25,480000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 87,'Đầm dạ hội mini','1 cái',2600000,0.7,2400000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 88,'Giày cao gót nhũ','1 đôi',1600000,0.8,1500000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 89,'Áo len cardigan','1 cái',1100000,0.5,1020000,'THOI_TRANG','theme_thoi_trang.png'
  UNION ALL SELECT 90,'Vali kéo cabin 20 inch','1 cái',3200000,3.5,2950000,'THOI_TRANG','theme_thoi_trang.png'
  -- ── GIA_DUNG — 🏠 Gia dụng ──────────────────────────────────────────────
  UNION ALL SELECT 91,'Nồi chiên không dầu 5L','1 cái',2800000,5.0,2600000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 92,'Máy xay sinh tố đa năng','1 cái',950000,2.2,880000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 93,'Bộ dao thớt thép không gỉ','1 bộ',520000,1.5,130000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 94,'Đèn bàn LED học tập','1 cái',380000,0.8,95000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 95,'Nồi cơm điện 1.8L','1 cái',1250000,3.0,1150000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 96,'Máy hút bụi cầm tay','1 cái',2200000,2.8,2050000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 97,'Bộ chén đĩa sứ 10 món','1 bộ',850000,4.0,790000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 98,'Quạt mini USB','1 cái',290000,0.5,85000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 99,'Ấm siêu tốc 1.7L','1 cái',620000,1.0,580000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 100,'Thùng rác thông minh cảm biến','1 cái',1100000,2.0,1020000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 101,'Máy ép chậm nhỏ','1 cái',3100000,4.5,2900000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 102,'Tủ giày nhựa 5 tầng','1 cái',980000,6.0,900000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 103,'Bộ hộp đựng thực phẩm (6 hộp)','1 bộ',320000,1.0,95000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 104,'Bàn ủi hơi nước mini','1 cái',780000,1.2,720000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 105,'Thảm trải phòng 160×230','1 tấm',1450000,5.5,1350000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 106,'Máy lọc không khí mini','1 cái',1900000,2.0,1750000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 107,'Kệ treo tường đa năng','1 cái',450000,2.5,110000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 108,'Bình thủy 2L thuỷ tinh','1 cái',260000,1.1,80000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 109,'Máy tạo độ ẩm 3L','1 cái',980000,1.6,910000,'GIA_DUNG','theme_gia_dung.png'
  UNION ALL SELECT 110,'Bộ chăn ga gối cotton','1 bộ',1350000,3.0,1250000,'GIA_DUNG','theme_gia_dung.png'
  -- ── THE_THAO — ⚽ Thể thao ──────────────────────────────────────────────
  UNION ALL SELECT 111,'Quả bóng đá số 5 da PU','1 quả',480000,0.45,140000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 112,'Găng tay tập gym','1 đôi',320000,0.2,95000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 113,'Dây nhảy có đồng hồ','1 cái',260000,0.3,85000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 114,'Bóng rổ composite số 7','1 quả',680000,0.6,630000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 115,'Vợt cầu lông carbon','1 cây',2200000,0.3,2050000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 116,'Quả cầu lông goose (1 hộp 12)','1 hộp',450000,0.25,130000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 117,'Thảm tập yoga TPE 6mm','1 tấm',620000,1.1,580000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 118,'Tạ tay.adjustable 10kg','1 đôi',1450000,10.0,1350000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 119,'Bóng bàn + vợt đôi','1 bộ',780000,1.5,720000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 120,'Giày chạy bộ nhẹ','1 đôi',1650000,0.7,1550000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 121,'Máy đo nhịp tim đeo tay','1 cái',850000,0.1,790000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 122,'Bình nước thể thao 1L','1 cái',280000,0.3,85000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 123,'Vợt tennis entry level','1 cây',1900000,0.4,1750000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 124,'Băng đô thấm mồ hôi (3 cái)','1 bộ',180000,0.1,80000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 125,'Xe trượt Skateboard maple','1 chiếc',1350000,3.0,1250000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 126,'Bóng chuyền da mục','1 quả',390000,0.3,110000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 127,'Kính bơi chống sương','1 cái',260000,0.1,85000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 128,'Máy tập bụng gập mini','1 cái',890000,4.0,830000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 129,'Dây kháng lực tập gym (5 mức)','1 bộ',420000,0.8,120000,'THE_THAO','theme_the_thao.png'
  UNION ALL SELECT 130,'Xe đạpfixed gear mini mô hình','1 cái',350000,0.8,95000,'THE_THAO','theme_the_thao.png'
  -- ── THUC_PHAM — 🍜 Thực phẩm ────────────────────────────────────────────
  UNION ALL SELECT 131,'Bánh trung thu nướng 4 viên','1 hộp',450000,0.9,140000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 132,'Cà phê rang xay Robusta 1kg','1 túi',350000,1.0,110000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 133,'Trà Shan tuyết cổ thụ 500g','1 túi',680000,0.5,630000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 134,'Mật ong rừng nguyên chất 1L','1 chai',520000,1.5,480000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 135,'Hộp quà hạt dinh dưỡng','1 hộp',780000,1.8,720000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 136,'Yến sào chưng đường phèn (6 hũ)','1 hộp',3200000,1.2,2950000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 137,'Nước mắm cá cơm 40 độ đạm','1 chai',260000,0.7,85000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 138,'Hạt điều rang muối 500g','1 túi',320000,0.5,95000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 139,'Bò khô Lạng Sơn 300g','1 túi',420000,0.3,120000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 140,'Rượu vang Ý Valpolicella','1 chai',850000,1.3,790000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 141,'Set gia vị nướng BBQ','1 bộ',380000,0.8,95000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 142,'Đường thốt nốt An Giang','1kg',180000,1.0,85000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 143,'Gạo ST25 hương lài 5kg','1 bao',420000,5.0,130000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 144,'Tuyết hoa sấy dẻo Mộc Châu','1 túi',290000,0.3,85000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 145,'Bánh mochi Nhật (10 viên)','1 hộp',350000,0.6,100000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 146,'Trà sữa gói pha sẵn (10 gói)','1 hộp',320000,0.6,90000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 147,'Tỏi đen đơn nhánh 500g','1 túi',890000,0.5,830000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 148,'Nước cốt dừa hữu cơ (6 lon)','1 thùng',450000,2.5,130000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 149,'Hộp quà Tết 4 ngăn cao cấp','1 hộp',1650000,2.2,1550000,'THUC_PHAM','theme_thuc_pham.png'
  UNION ALL SELECT 150,'Đồ hộp cá ngừ (10 lon)','1 thùng',520000,3.5,150000,'THUC_PHAM','theme_thuc_pham.png'
  -- ── SACH_VAN_PHONG — 📚 Sách & Văn phòng ───────────────────────────────
  UNION ALL SELECT 151,'Bộ sách Dám Nghĩ Lớn + khuyến nghị','2 cuốn',450000,1.0,140000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 152,'Bút ký thiên tài (2 bút)','1 hộp',320000,0.1,95000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 153,'Sổ kế hoạch A5 da PU','1 cuốn',280000,0.4,85000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 154,'Bàn phím số externalkhông dây','1 cái',650000,0.4,600000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 155,'Đế dựng sách gỗ','1 cái',380000,0.9,110000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 156,'Bộ 5 sách kỹ năng CEO','5 cuốn',980000,2.2,910000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 157,'Máy tính Casio FX-580VNX','1 cái',720000,0.2,670000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 158,'Kẹp giấy hình thú xinh','1 bộ',150000,0.3,80000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 159,'Bảng flipchart mini để bàn','1 cái',850000,2.0,790000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 160,'Đèn kẹp màn hình đọc sách','1 cái',420000,0.3,120000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 161,'Bộ kit đánh dấu highlight 10 màu','1 bộ',260000,0.2,85000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 162,'Bút bi nhựa (20 cây)','1 hộp',350000,0.4,100000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 163,'Giá đỡ điện thoại bàn nhôm','1 cái',290000,0.5,85000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 164,'Sách hôm nay tôi thành công','1 cuốn',190000,0.5,95000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 165,'Bộ bút dạ quang Pastel','1 bộ',180000,0.2,80000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 166,'Hộp bút gỗ khắc tên','1 cái',450000,0.6,110000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 167,'Máy bấm gáy mini','1 cái',560000,0.8,520000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 168,'Bộ thư pháp Trung Quốc','1 bộ',1250000,1.5,1150000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 169,'Cặp tài liệu da A4','1 cái',780000,0.7,720000,'SACH_VAN_PHONG','theme_sach.png'
  UNION ALL SELECT 170,'Sách hình xưa câu chuyện cổ tích','1 bộ',950000,1.8,880000,'SACH_VAN_PHONG','theme_sach.png'
  -- ── DO_CHOI — 🧸 Đồ chơi ────────────────────────────────────────────────
  UNION ALL SELECT 171,'Lego City 60380 mini','1 hộp',950000,0.8,890000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 172,'Mô hình Gundam HG 1/144','1 hộp',680000,0.5,630000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 173,'Rubik 3×3 speed cube Gan','1 quả',450000,0.1,420000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 174,'Gấu bông Teddy 40cm','1 con',580000,0.6,540000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 175,'Đồ chơi lắp ráp giáo dục STEM','1 hộp',780000,1.0,720000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 176,'Siêu nhânTransformer convertible','1 con',1350000,0.5,1250000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 177,'Xe điều khiển từ xa 1:16','1 chiếc',1650000,1.5,1550000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 178,'Bộ truyện Doraemon 10 tập','1 bộ',780000,2.0,720000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 179,'Búp bê Barbie thời trang','1 con',890000,0.3,830000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 180,'Lều chơi bậc thang bé','1 cái',1250000,2.5,1150000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 181,'Bộ trống nhạc cho bé','1 bộ',450000,1.2,130000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 182,'Đồ chơi nước bọt phun nước','1 cái',290000,0.4,85000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 183,'Puzzle 1000 mảnh landscape','1 bộ',520000,0.8,150000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 184,'Yo-yo chuyên nghiệp','1 quả',220000,0.1,80000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 185,'Bệ pháo hoa mini an toàn','1 bộ',680000,1.0,630000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 186,'Robot lập trình giáo dục','1 con',2900000,1.8,2700000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 187,'Bộ đồ nấu ăn đồ chơi bé','1 bộ',380000,1.0,95000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 188,'Mô hình máy bay Boeing 1:200','1 chiếc',850000,0.6,790000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 189,'Súng nước đạn gel an toàn','1 khẩu',560000,0.7,520000,'DO_CHOI','theme_do_choi.png'
  UNION ALL SELECT 190,'Bộ xếp hình wooden 100 chi tiết','1 hộp',620000,1.4,580000,'DO_CHOI','theme_do_choi.png'
  -- ── MY_PHAM — 💄 Mỹ phẩm ────────────────────────────────────────────────
  UNION ALL SELECT 191,'Son lì kem nhưng 3CE','1 cây',450000,0.05,420000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 192,'Kem chống nắng Anessa 60ml','1 chai',780000,0.1,720000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 193,'Nước hoa mini Gucci 5ml','1 lọ',890000,0.05,830000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 194,'Bộ cọ trang sức 12 cây','1 bộ',520000,0.3,150000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 195,'Mặt nạ dưỡng da Hàn Quốc (10 miếng)','1 hộp',380000,0.3,110000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 196,'Serum vitamin C 30ml','1 chai',650000,0.1,600000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 197,'Máy rửa mặt mini rung','1 cái',850000,0.2,790000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 198,'Bảng màu eyeshadow 14 ô','1 hộp',680000,0.2,630000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 199,'Dưỡng ẩm Cetaphil 100g','1 hũ',420000,0.15,390000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 200,'Kem nền BB cushion','1 hộp',560000,0.1,520000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 201,'Dầu tẩy trang Bioderma 500ml','1 chai',720000,0.5,670000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 202,'Lăn khử mùi nam Roll-on','1 chai',260000,0.1,85000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 203,'Bộ dưỡng da mini 4 bước','1 bộ',1250000,0.4,1150000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 204,'Máy xoay nail mini','1 bộ',1450000,0.8,1350000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 205,'Sáp vuốt tóc styling','1 hũ',320000,0.1,95000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 206,'Dưỡng tóc keratin 200ml','1 chai',480000,0.25,140000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 207,'Tẩy tế bào chết body scrub','1 hũ',390000,0.3,110000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 208,'Nước hoa nam mini Versace 5ml','1 lọ',950000,0.05,880000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 209,'Kẹp tóc styling set','1 bộ',290000,0.4,85000,'MY_PHAM','theme_my_pham.png'
  UNION ALL SELECT 210,'Máy duỗi tóc mini gấp gọn','1 cái',890000,0.4,830000,'MY_PHAM','theme_my_pham.png'
  -- ── NHAC_CU — 🎸 Nhạc cụ ────────────────────────────────────────────────
  UNION ALL SELECT 211,'Ukulele soprano gỗ mahogany','1 cây',850000,0.7,790000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 212,'Sáo trúc tre thủ công','1 cây',320000,0.2,95000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 213,'Kèn harmonica 24 lỗ','1 cây',380000,0.2,110000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 214,'Guitar acoustic giờ học','1 cây',2900000,2.5,2700000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 215,'Đàn kalimba 17 phím gỗ','1 cây',680000,0.4,630000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 216,'Trống Cajon mini','1 chiếc',1450000,4.0,1350000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 217,'Tai nghe monitor beat 40mm','1 cái',1250000,0.3,1150000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 218,'Đàn keyboard 61 phím mini','1 cây',3100000,4.5,2900000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 219,'Bộ trống Bongo','1 bộ',1250000,3.0,1150000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 220,'Metronome cơ học','1 cái',450000,0.4,140000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 221,'Đàn ocarina gốm','1 cây',290000,0.2,85000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 222,'Capo guitar thép','1 cái',180000,0.1,80000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 223,'Bộ dây đàn guitar Elixir','1 bộ',680000,0.1,630000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 224,'Đàn piano học mini 25 phím','1 cây',650000,0.8,600000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 225,'Cái kèn Kazoo kim loại','1 cây',150000,0.1,80000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 226,'Đàn bầu Quang Nhạc','1 cây',2200000,1.5,2050000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 227,'Bộ chày cối đánh nhịp Lào','1 bộ',380000,0.6,110000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 228,'Guitar đàn sigma classical','1 cây',1900000,2.0,1750000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 229,'Đàn guzheng mini 21 dây','1 cây',3500000,6.0,3250000,'NHAC_CU','theme_nhac_cu.png'
  UNION ALL SELECT 230,'Bộ phụ kiện đàn guitar (picks, strap, dây)','1 bộ',520000,0.3,150000,'NHAC_CU','theme_nhac_cu.png'
  -- ── DO_CO — 🏺 Đồ cổ & Sưu tầm ─────────────────────────────────────────
  UNION ALL SELECT 231,'Đồng xu cổ triều Nguyễn (bản sao)','1 xu',350000,0.05,95000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 232,'Bộ tem Việt Nam thập niên 1980','1 bộ',450000,0.1,130000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 233,'Tượng Phật Bà Quan Âm gốm Bát Tràng','1 tượng',1850000,2.5,1700000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 234,'Bình gốm men rạn 25cm','1 bình',2200000,3.0,2050000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 235,'Đồng hồ treo tường cổ kiểu Thụy Sĩ','1 chiếc',3200000,3.5,2950000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 236,'Tủ kính trưng bày mô hình','1 cái',980000,5.0,900000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 237,'Bộ ấm trà Tử Sa груn','1 bộ',1450000,1.5,1350000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 238,'Tranh Đông Hồ khắc gỗ','1 bức',850000,0.8,790000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 239,'Đĩa sứ men xanh cổ','1 chiếc',680000,0.9,630000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 240,'Bộ sưu tập tiền giấy Việt Nam (10 tờ)','1 bộ',720000,0.2,660000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 241,'Tượng đồng Cua Vàng nhỏ','1 tượng',1250000,1.2,1150000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 242,'Quạt tay sơn mài cầm tay','1 chiếc',420000,0.4,120000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 243,'Hộp đựng đồ cổ khắc rồng','1 hộp',950000,1.0,880000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 244,'Bộ sách cổ tập ký thập kỷ 1960','1 bộ',1650000,2.0,1550000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 245,'Sừng trâu thủ công mút khuyết','1 chiếc',780000,0.5,720000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 246,'Bát gốm men ngọc Tra Vĩnh','1 chiếc',560000,0.6,520000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 247,'Kiếm short cổ trang sức dưới 40cm','1 thanh',1450000,1.2,1350000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 248,'Tượng voi ngà giả khắc tinh xảo','1 tượng',1850000,1.0,1700000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 249,'Bộ án bút đá mực thư phòng','1 bộ',1250000,2.5,1150000,'DO_CO','theme_do_co.png'
  UNION ALL SELECT 250,'Đèn lồng Trung thu giấy dó','1 chiếc',380000,0.5,110000,'DO_CO','theme_do_co.png'
  -- ── THU_CUNG — 🐾 Thú cưng ──────────────────────────────────────────────
  UNION ALL SELECT 251,'Đồ ăn mèo vị cá hồi 1.5kg','1 túi',450000,1.5,130000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 252,'Vòng cổ chó da có tag tên','1 cái',280000,0.1,85000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 253,'Nhà mèo 2 tầng','1 cái',1850000,8.0,1700000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 254,'Cát vệ sinh bentonite 10kg','1 bao',320000,10.0,95000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 255,'Bàn chải chải lông mèo','1 cái',260000,0.2,80000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 256,'Bát ăn chống trượt inox','1 cặp',380000,0.5,110000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 257,'Đồ chơi dang tay cho chó','1 cái',220000,0.3,80000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 258,'Cũi di động cho thú cưng','1 cái',1450000,3.0,1350000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 259,'Máy cắt tỉa lông thú cưng','1 chiếc',890000,0.6,830000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 260,'Balo đeo thú cưng có cửa sổ','1 cái',1250000,1.5,1150000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 261,'Pate mèo cao cấp (24 gói)','1 thùng',780000,4.0,720000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 262,'Cây cào móng chóp','1 cây',650000,1.8,600000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 263,'Đồng hồ đo nhiệt độ cắm tai thú','1 cái',520000,0.1,480000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 264,'Sữa bổ dưỡng cho mèo con','1 hộp',380000,0.4,110000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 265,'Đệm ngủ lông nhân tạo','1 cái',680000,1.2,630000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 266,'Bao tiêu chuẩn GPS theo dõi thú','1 cái',2900000,0.1,2700000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 267,'Bộ chăm sóc răng miệng chó mèo','1 bộ',350000,0.3,100000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 268,'Đồ chơi thông minh phát đồ ăn','1 cái',560000,0.8,520000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 269,'Chuồng chim cóng có cửa sổ','1 chiếc',850000,2.5,790000,'THU_CUNG','theme_thu_cung.png'
  UNION ALL SELECT 270,'Quần áo mùa đông thú cưng','1 bộ',290000,0.2,85000,'THU_CUNG','theme_thu_cung.png'
  -- ── DU_LICH — 🏕️ Du lịch & Dã ngoại ────────────────────────────────────
  UNION ALL SELECT 271,'Balo trekking 40L chống nước','1 chiếc',1350000,1.2,1250000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 272,'Bình giữ nhiệt 1L inox','1 chiếc',450000,0.5,130000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 273,'Lều cắm trại 2 người','1 cái',1650000,2.5,1550000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 274,'Túi ngủ dã ngoại 15°C','1 cái',890000,1.5,830000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 275,'Đèn lồng cắm trại LED sạc USB','1 chiếc',380000,0.3,110000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 276,'Bộ nồi lều siêu nhẹ','1 bộ',950000,1.2,880000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 277,'Ghế xếp dã ngoại','1 chiếc',520000,1.0,150000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 278,'Thảm picnic chống nước 200×200','1 tấm',450000,0.8,130000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 279,'Gậy leo núi nhôm (2 cây)','1 cặp',680000,0.6,630000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 280,'Túi chống nước cho vali 20L','1 túi',290000,0.3,85000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 281,'Máy lọc nước sinh tồn cổng tiêu chuẩn','1 cái',850000,0.3,790000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 282,'La bàn quân dụng','1 cái',260000,0.1,80000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 283,'Dao gấp đa năng 13 chức năng','1 con',720000,0.2,670000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 284,'Kính áp tròng ngắm cảnh 8×42','1 đôi',1650000,0.7,1550000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 285,'Bộ bọc ghế camping gấp gọn','1 bộ',680000,0.9,630000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 286,'Hộp sơ cứu du lịch 45 món','1 hộp',520000,0.8,150000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 287,'Power station solar panel 20W','1 tấm',2200000,1.8,2050000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 288,'Máy ảnh action camera 4K','1 chiếc',3100000,0.3,2900000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 289,'Vòi xịt đa năng cắm trại','1 cái',350000,0.5,100000,'DU_LICH','theme_du_lich.png'
  UNION ALL SELECT 290,'Túi đựng đồ nghề BBQ có dây đeo','1 túi',620000,0.9,580000,'DU_LICH','theme_du_lich.png'
) t
WHERE NOT EXISTS (SELECT 1 FROM Product WHERE id = t.id);

-- ============================================================================
-- 3) KIỂM TRA NHANH
--   SELECT COUNT(*) FROM Product;                    -- 290
--   SELECT category, COUNT(*) FROM Product GROUP BY category;  -- 14 dòng × 20
-- ============================================================================

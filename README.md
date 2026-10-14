# 🎁 Đấu Giá Thùng Hàng Bí Ẩn — Mystery Box Auction

> **Bài tập lớn môn Lập trình Mạng — PTIT**
> Trò chơi đấu giá thùng hàng bí ẩn theo thời gian thực qua mạng LAN, nhiều người chơi đồng thời.

---

## 📋 Mục lục

- [Tổng quan hệ thống](#-tổng-quan-hệ-thống)
- [Cơ chế game](#-cơ-chế-game)
- [Hệ thống phòng & hạng](#-hệ-thống-phòng--hạng)
- [Cơ chế đấu giá](#-cơ-chế-đấu-giá)
- [Sự kiện ngẫu nhiên](#-sự-kiện-ngẫu-nhiên-vòng-3)
- [Cách kết thúc trò chơi](#-cách-kết-thúc-trò-chơi)
- [Hệ thống Bot](#-hệ-thống-bot-đấu-thử-với-máy)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Hướng dẫn cài đặt & chạy](#-hướng-dẫn-cài-đặt--chạy)

---

## 🌐 Tổng quan hệ thống

Dự án theo kiến trúc **Client-Server TCP/IP**:

```
[Client 1] ──┐
[Client 2] ──┤── TCP Socket (port 7777) ──► [Server] ──► [MySQL DB]
[Client N] ──┘
```

| Thành phần | Công nghệ |
|---|---|
| Giao tiếp mạng | Java TCP Socket (port **7777**) |
| Giao diện Client | JavaFX |
| Giao diện Admin Server | JavaFX |
| Cơ sở dữ liệu | MySQL |
| Xử lý đồng thời | ThreadPool + `GameRequestQueue` (FIFO per-room) |

---

## 🎮 Cơ chế game

### Luồng chơi tổng quát

```
Đăng nhập / Đăng ký
        ↓
   Chọn phòng (theo hạng ví)
        ↓
   Phòng chờ (tối đa 3 người)
        ↓
  Host bấm "Bắt đầu" (≥ 2 người)
        ↓
   ┌── Vòng 1 (45s) ──┐
   │   Vòng 2 (45s)   │  × 4 vòng
   │   Vòng 3 (45s)   │
   └── Vòng 4 (45s) ──┘
        ↓
   Kết thúc → Đập hộp → Kết quả
        ↓
      Lobby
```

### Thùng hàng bí ẩn

- Mỗi phiên đấu giá, server **random 1 chủ đề** (Công nghệ, Trang sức, Ô tô, Thời trang, v.v. — 14 chủ đề)
- Thùng chứa **6–8 vật phẩm** thuộc chủ đề đó, lấy ngẫu nhiên từ database
- **Danh sách & giá trị thật** của vật phẩm là **bí mật** — server KHÔNG gửi cho client
- Chỉ khi phiên kết thúc, toàn bộ vật phẩm mới được công bố (màn "đập hộp")

---

## 🏠 Hệ thống phòng & hạng

Phòng được phân hạng theo **số dư ví** của người chơi:

| Hạng | Yêu cầu ví | Trần giá thùng |
|---|---|---|
| 🟢 **Bình Dân** | 2.000.000 – 10.000.000 VNĐ | 15.000.000 VNĐ |
| 👑 **Đại Gia** | > 10.000.000 VNĐ | 50.000.000 VNĐ |

- Mỗi phòng tối đa **3 người chơi**
- **Host** (người tạo phòng / vào đầu tiên) là người bấm "Bắt đầu"
- Cần **tối thiểu 2 người** để bắt đầu
- Số dư ví mặc định khi đăng ký: **3.000.000 VNĐ**

---

## 🔨 Cơ chế đấu giá

### 4 Vòng đấu (mỗi vòng 45 giây)

Mỗi vòng, server **hé lộ thêm một phần** vật phẩm trong thùng:

| Vòng | Vật phẩm được hé lộ |
|---|---|
| **Vòng 1** | 1 món "mồi nhử" ngẫu nhiên (bắt buộc để tính giá) |
| **Vòng 2** | +1 món ngẫu nhiên |
| **Vòng 3** | +1 món ngẫu nhiên + sự kiện ngẫu nhiên ở giây 15 |
| **Vòng 4** | Hé lộ phần còn lại, giữ bí mật 1–2 món cuối |

### Công thức định giá ước tính (P_est)

Sau mỗi lần hé lộ, server tính lại **giá ước tính** theo công thức:

```
P_est = (Σ Giá_trị_lộ / Σ Khối_lượng_lộ) × Tổng_khối_lượng × K_vòng
```

Hệ số `K_vòng` giảm dần qua các vòng: `1.20 → 1.10 → 1.05 → 1.05`

### Giá Mua Đứt (P_buyout)

```
P_buyout = P_est × M_buyout  (M ngẫu nhiên trong khoảng mỗi vòng)
```

| Vòng | Khoảng M_buyout | Ý nghĩa |
|---|---|---|
| Vòng 1 | 2.5 – 3.0 | "Vùng cấm địa" — ngăn chốt sớm |
| Vòng 2 | 2.0 – 2.2 | Bắt đầu kích thích tâm lý |
| Vòng 3 | 1.5 – 1.8 | Điểm rơi chiến thuật |
| Vòng 4 | 1.2 – 1.3 | Phí bảo hiểm chốt hạ |

> **Lưu ý**: Giá mua đứt **không giảm theo giây** — chỉ thay đổi khi có hé lộ vật phẩm hoặc chuyển vòng.

### Luật BID (Đặt giá)

- Mỗi người chỉ được **đặt 1 lần / vòng** (nhưng vẫn có thể Mua Đứt)
- Được đặt **bất kỳ số tiền > 0** — không cần phải cao hơn người khác
- **Đấu giá kín**: giá đặt bị ẩn trong suốt vòng đó
- **Hết vòng** → server công bố tất cả giá đã đặt (`bid-reveal`)
- BID chỉ là cam kết cam kết — **chưa trừ tiền ngay**; chỉ người thắng mới bị trừ

### Điều kiện BID hợp lệ

- Phiên đang diễn ra
- Số tiền > 0
- Ví đủ tiền
- Chưa bid vòng này (mỗi người 1 lần/vòng)

---

## ⭐ Sự kiện ngẫu nhiên (Vòng 3)

Vào **giây thứ 15 của Vòng 3**, server kích hoạt **1 trong 5 sự kiện ngẫu nhiên** (mỗi phiên chỉ 1 lần):

| Sự kiện | Mô tả |
|---|---|
| 🎲 **EXTRA_REVEAL** | Hé lộ thêm 1 vật phẩm ẩn ngẫu nhiên → P_est thay đổi bất ngờ |
| 🏋️ **HEAVY_REVEAL** | Hé lộ vật phẩm **nặng nhất** còn ẩn → manh mối loại trừ "rác nặng rẻ" |
| 🔍 **APPRAISER** | Chuyên gia thẩm định: công bố giá trị trung bình 1 món (±5% sai số) |
| ⚡ **FLASH_SALE** | Giảm **30% giá mua đứt** trong 10 giây; sau 10s **giữ mức giá sau giảm** |
| 🧊 **BUYOUT_FREEZE** | Khoá cứng giá mua đứt đến hết vòng — ép chọn ngay hoặc chờ rủi ro |

---

## 🏁 Cách kết thúc trò chơi

### Trường hợp 1: Mua Đứt (BUYOUT)
- Người chơi bấm "Mua Đứt" và **trả ngay** `P_buyout` hiện tại
- Phiên **kết thúc ngay lập tức**
- Nếu 2 người cùng bấm cùng lúc → người gửi lệnh **đến server trước** (FIFO) thắng

### Trường hợp 2: Hết 4 Vòng — Có người thắng
- Người có **BID cao nhất** thắng, với điều kiện bid đó **vượt quá P_est cuối**
- Người thắng bị trừ tiền, người thua cộng điểm thua

### Trường hợp 3: Hết 4 Vòng — Huỷ phiên
- Nếu **không có bid nào** hợp lệ, **hoặc** bid cao nhất vẫn ≤ P_est cuối → **Huỷ phiên**
- Không ai thắng, **không ai bị trừ tiền**

### Màn "Đập Hộp" (kết thúc bất kỳ trường hợp nào)

```
Người thắng trả: giá trúng
Người thắng nhận: toàn bộ giá trị thật Σ realValue của các vật phẩm trong thùng
Lãi / lỗ = Σ realValue − giá_trúng
```

Toàn bộ vật phẩm (tên, hình, giá trị thật) được hiện ra cho tất cả người chơi xem.

---

## 🤖 Hệ thống Bot (Đấu thử với máy)

- Chọn chế độ "Đấu với máy" để vào phòng 1 người thật + 2 bot
- Bot dùng tài khoản `bot001`, `bot002`, `bot003` trong DB
- Mỗi bot có **tính cách riêng**:
  - `bot001` — cân bằng
  - `bot002` — "Săn deal" — thận trọng, ít mua đứt
  - `bot003` — "Cọc cời" — liều lĩnh hơn
- Bot **đi qua cùng hàng đợi FIFO** như người thật (không được ưu tiên)
- Bot chỉ **BID**, không Mua Đứt (để người thật có cơ hội hơn)

---

## 📁 Cấu trúc dự án

```
src/
├── Server/
│   ├── Server.java                  # Entry point server (port 7777)
│   ├── Controller/
│   │   ├── AuctionEngine.java       # Toàn bộ logic đấu giá (4 vòng, định giá, sự kiện)
│   │   ├── AuctionRoomState.java    # Trạng thái phiên đấu giá trong RAM
│   │   ├── BotManager.java          # Quản lý bot AI
│   │   ├── GameConfig.java          # Hằng số cấu hình gameplay
│   │   ├── GameRequestQueue.java    # Hàng đợi lệnh FIFO mỗi phòng (xử lý đồng thời)
│   │   ├── ItemsThemeHelper.java    # Tên hiển thị các chủ đề thùng hàng
│   │   ├── RequestObject.java       # Đối tượng lệnh BID/BUYOUT
│   │   ├── Room.java                # Quản lý phòng & hạng phòng
│   │   ├── ServerThread.java        # Mỗi client 1 luồng; xử lý giao thức
│   │   └── ServerThreadBUS.java     # Quản lý danh sách tất cả luồng
│   ├── DAO/
│   │   ├── DAO.java                 # Base class kết nối MySQL
│   │   ├── PlayerDAO.java           # CRUD người chơi, ví tiền
│   │   ├── ProductDAO.java          # CRUD vật phẩm
│   │   ├── AuctionSessionDAO.java   # Lưu lịch sử phiên đấu giá
│   │   ├── BidHistoryDAO.java       # Lịch sử giá đặt
│   │   └── GameMatchDAO.java        # Lịch sử trận đấu
│   ├── Model/                       # POJO: Player, Product, AuctionSession, BidHistory, GameMatch
│   └── View/
│       └── Admin.java               # Dashboard quản trị (JavaFX)
├── TestClient/
│   ├── Client.java                  # Entry point client (JavaFX Application)
│   ├── ClientHandler.java           # Luồng mạng client (đọc socket, điều phối màn hình)
│   ├── FxUi.java                    # Router màn hình JavaFX
│   └── ViewFx/
│       ├── AppShell.java            # Khung cửa sổ chính
│       ├── LoginPanel.java          # Màn đăng nhập
│       ├── RegisterPanel.java       # Màn đăng ký
│       ├── LobbyPanel.java          # Lobby (tạo/tìm phòng, chat)
│       ├── FindRoomPanel.java       # Tìm phòng ghép
│       ├── RoomListPanel.java       # Danh sách phòng
│       ├── WaitingRoomPanel.java    # Phòng chờ
│       ├── AuctionPanel.java        # Màn đấu giá chính
│       ├── RankingsPanel.java       # Bảng xếp hạng
│       └── OnlinePanel.java         # Danh sách người chơi online
└── Server/Database/
    ├── migration_mystery_box.sql    # Tạo bảng & dữ liệu mẫu
    ├── migration_themes.sql         # Dữ liệu vật phẩm theo chủ đề
    └── reset_demo_data.sql          # Reset dữ liệu về trạng thái ban đầu
```

---

## 🚀 Hướng dẫn cài đặt & chạy

### Yêu cầu hệ thống

| Phần mềm | Phiên bản |
|---|---|
| Java JDK | 17 trở lên |
| JavaFX | 24.0.1 (đã có sẵn trong `src/lib/`) |
| MySQL | 8.0 trở lên |
| MySQL Connector/J | 8.x (đã có trong `src/lib/`) |

---

### Bước 1: Cài đặt cơ sở dữ liệu

```sql
-- Chạy lần lượt trong MySQL Workbench hoặc CLI:
source src/Server/Database/migration_mystery_box.sql;
source src/Server/Database/migration_themes.sql;
```

Sau đó mở file `src/Server/DAO/DAO.java` và cập nhật thông tin kết nối:

```java
final String DATABASE_NAME = "btl_ltm";   // Tên database của bạn
final String JDBC_USER = "root";           // Username MySQL
final String JDBC_PASSWORD = "your_pass"; // Password MySQL
```

---

### Bước 2: Compile toàn bộ source

```powershell
# Tạo file danh sách sources
cd src
dir /s /b *.java > sources.txt

# Compile
javac -cp "lib\*" -d out @sources.txt
```

---

### Bước 3: Copy tài nguyên vào thư mục output

```powershell
# Copy CSS
copy resources\fx.css out\

# Copy ảnh assets (avatar, sản phẩm)
xcopy /E /I assets out\assets
```

---

### Bước 4: Chạy Server

```powershell
cd src
java -cp "out;lib\*" `
  --module-path "lib" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics `
  Server.Server
```

> Cửa sổ Admin JavaFX sẽ mở ra. Server lắng nghe tại **port 7777**.

---

### Bước 5: Chạy Client

> Mỗi người chơi chạy lệnh này trên máy của họ.

```powershell
cd src
java -cp "out;lib\*" `
  --module-path "lib" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics `
  TestClient.Client
```

**Lưu ý khi chạy qua LAN**: Mở file `src/TestClient/ClientHandler.java`, tìm dòng khai báo địa chỉ server và đổi thành **IP của máy chạy server**:

```java
// Ví dụ:
private static final String SERVER_HOST = "192.168.1.100"; // IP LAN của máy server
private static final int    SERVER_PORT = 7777;
```

---

### Chạy nhanh (script PowerShell)

Tạo file `run_server.ps1`:
```powershell
cd src
java -cp "out;lib\*" --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics Server.Server
```

Tạo file `run_client.ps1`:
```powershell
cd src
java -cp "out;lib\*" --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics TestClient.Client
```

---

## 👤 Tài khoản mẫu

Sau khi chạy migration, các tài khoản sau được tạo sẵn:

| Tài khoản | Mật khẩu | Loại |
|---|---|---|
| `admin` | `Admin@123` | Admin (nếu có) |
| `bot001` | `Bot@001` | Bot AI |
| `bot002` | `Bot@001` | Bot AI |
| `bot003` | `Bot@001` | Bot AI |

> Người chơi tự đăng ký tài khoản mới qua màn hình Register trong client.

---

## 🔧 Lỗi thường gặp

| Lỗi | Nguyên nhân | Giải pháp |
|---|---|---|
| `Address already in use: 7777` | Server đang chạy hoặc cổng bị chiếm | Tắt tiến trình Java cũ: `taskkill /F /IM java.exe` |
| `Connection to database failed` | Sai thông tin DB hoặc MySQL chưa chạy | Kiểm tra lại `DAO.java`, đảm bảo MySQL đang chạy |
| `Không đủ vật phẩm` | Chưa chạy `migration_themes.sql` | Chạy lại file SQL seed dữ liệu |
| Bot không vào phòng | Chưa seed tài khoản bot | Chạy lại `migration_mystery_box.sql` |

---

*Dự án Bài tập lớn — Lập trình Mạng — PTIT*

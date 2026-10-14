# PROMPT: Xây dựng giao diện JavaFX cho Client + Server Admin
# Dự án: "Đấu Giá Thùng Hàng Bí Ẩn" — BTL Java Socket + MySQL

> Cách dùng: dán toàn bộ khối dưới đây vào một phiên làm việc mới (Freebuff/agent khác).
> Prompt tự chứa (self-contained): giao thức đầy đủ, luật game, danh sách màn hình,
> ràng buộc "chỉ thay UI, không đổi logic", tiêu chí nghiệm thu.
> Lưu ý môi trường: máy chưa có JavaFX trong JDK và không có mvn/gradle trên PATH —
> bước đầu tiên của agent phải là tải OpenJFX SDK jars và chứng minh chạy được trên JDK 24.

````text
# NHIỆM VỤ: Xây dựng lại TOÀN BỘ giao diện (client + server admin) bằng JavaFX
# Dự án: "Đấu Giá Thùng Hàng Bí Ẩn" — BTL Java Socket + MySQL (workspace hiện có sẵn code)

Bạn là agent lập trình Java. Đây là đặc tả tự chứa (self-contained). Hãy đọc hết trước khi làm.
Nguyên tắc: GIỮ NGUYÊN giao thức socket, luật game, logic server, schema MySQL — CHỈ thay tầng giao diện.

## 1. Bối cảnh
- Game đấu giá 3 người/phòng, 4 vòng × 45 giây; server chạy trên cổng 7777, client kết nối 127.0.0.1:7777.
- SERVER: Java đa luồng (ThreadPool) + JDBC MySQL (DB `btl_ltm`), có cửa sổ admin (hiện là Swing) để quản lý người chơi/sản phẩm/lịch sử + phát thông báo broadcast cho client.
- CLIENT: hiện là Swing (13 màn hình), điều hướng qua lớp `TestClient.Client`, giao tiếp qua `TestClient.ClientHandler`.
- Giao tiếp: văn bản CSV, mỗi lệnh 1 dòng, phân tách bằng dấu phẩy, UTF-8. TUYỆT ĐỐI giữ nguyên chuỗi lệnh.
- JDK hiện tại: 24 (javac 24.0.2). Máy KHÔNG có sẵn JavaFX trong JDK và KHÔNG có `mvn`/`gradle` trên PATH.
- MySQL connector đang dùng: C:/Users/Mai/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar

## 2. Ràng buộc bất di bất dịch (KHÔNG được sửa)
1) Không đổi bất kỳ chuỗi giao thức nào ở mục 5 (tên lệnh, thứ tự token, kể cả lỗi chính tả "dupplicate-login").
2) Không sửa logic trong: Server/Controller/* (AuctionEngine, AuctionRoomState, BotManager, GameRequestQueue, Room, ServerThread, ServerThreadBUS, GameConfig), Server/DAO/*, Server/Model/*.
3) không đổi schema DB; giữ nguyên Server/Database/migration_mystery_box.sql và reset_demo_data.sql.
3b) KHÔNG tự động khởi động/tắt MySQL service, KHÔNG sửa file my.ini.
4) Server vẫn phải: bind cổng 7777, in log "Server is waiting to accept user..."; admin vẫn nhận log trò chuyện qua Server.admin.addMessage(String) (ServerThread gọi API này).
5) Tài khoản demo: test001..test006 / Test@001..Test@006; bot001..bot003 / Bot@001..Bot@003 (nick 🤖 Máy Trí Tuệ / 🤖 Máy Săn Deal / 🤖 Máy Cọc Cời); tất cả ví 100.000.000 → phải dùng phòng hạng ĐẠI GIA khi test.

## 3. Những gì ĐƯỢC PHÉP sửa
**GIAO DIỆN MỘT CỬA SỔ DUY NHẤT (Single-Window Interface) — yêu cầu硬 của khách hàng:**
- `AppShell` là khung duy nhất của client: **Thanh menu chức năng cố định bên trái**
  (thẻ hồ sơ mini + Sảnh chính / Danh sách phòng / Bảng xếp hạng / Đang online / Đăng xuất,
  item sáng theo trang đang mở), **vùng phải là nội dung swap** theo thao tác bấm.
- Mọi màn hình là 1 `ContentPanel` (Login → Đăng ký → Sảnh → Danh sách phòng/Tìm phòng →
  Phòng chờ → Đấu giá → BXH/Online) — TUYỆT ĐỐI không mở Stage/popup Windows mới.
- Alert/Confirm/TextInputDialog/ChoiceDialog bị cấm: thay bằng banner nội tuyến
  (`AppShell.bannerInfo/success/warn/error`), xác nhận nội tuyến (`AppShell.ask`), ô nhập
  nội tuyến (`AppShell.askInput` — ví dụ mật khẩu phòng, xác nhận MUA ĐỨT).
- Thay toàn bộ package `TestClient/View/*` (13 form Swing) bằng màn hình JavaFX.
- Viết lại phần quản lý mở/đóng màn hình trong `TestClient/Client.java`; được phép đổi package (ví dụ TestClient/ViewFx) miễn luồng chạy client vẫn rõ ràng (giữ entry `TestClient.Client` hoặc ghi lệnh mới vào README).
- Cập nhật các lời gọi UI trong `TestClient/ClientHandler.java` theo API JavaFX — NHƯNG giữ nguyên 100% logic socket + parse + tên lệnh.
- Thay package `Server/View/*` (Admin, PlayerManagement, ProductManagement, EditPlayer, EditProduct, MatchHistoryManagement, AuctionHistoryForm) bằng JavaFX; chỉ sửa `Server/Server.java` ở phần mở cửa sổ admin (cách khởi động JavaFX), giữ nguyên vòng accept.
- Xoá/không tham chiếu các file Swing cũ; tầng UI mới KHÔNG được import javax.swing.

## 4. Yêu cầu công nghệ
- JavaFX thuần (javafx.controls; có thể dùng FXML, khuyến khích code Java + 1 file CSS chung).
- Vì JDK 24 không kèm JavaFX và máy chưa có Maven CLI, ưu tiên: tải OpenJFX SDK jars (JavaFX 24.x, Windows x64, gluonhq.com/products/javafx) và chạy với:
  --module-path "<đường_dẫn_lib_của_JavaFX>" --add-modules javafx.controls,javafx.fxml
  (Phương án khác nếu tự cài được Maven cũng chấp nhận — miễn ghi rõ lệnh build/run thực tế đã kiểm chứng vào README.)
- Lưu ý bẫy kinh điển: chạy class kế thừa javafx.application.Application trực tiếp từ classpath có thể báo "JavaFX runtime components are missing" → dùng class Launcher riêng (không kế thừa Application) hoặc module-path đúng.
- Mọi cập nhật UI đến từ luồng mạng phải marshal qua Platform.runLater; không chặn FX Application Thread.
- Font tiếng Việt + emoji (🤖 📦 💰 ⚡ 🧊 🔍 ⏱️) hiển thị đúng trên Windows; thiết kế theme SÁNG, TỐI GIẢN (nền trắng/xám nhạt, 1 màu nhấn, bo góc nhẹ, chữ rõ), nhất quán toàn app.
- Ảnh: dùng thư mục `assets/` (avatar/, product/, icon/, game/); trường <picture>/<avatar> từ server là tên file — thiếu thì dùng placeholder.
- Không làm đơ UI khi bid đang chờ phản hồi; khi mất kết nối phải báo lỗi và quay về màn Đăng nhập (hành vi hiện tại của ClientHandler).

## 5. Giao thức CSV (nguồn chân lý để thiết kế UI — giữ nguyên từng token)
CLIENT → SERVER:
- client-verify,<username>,<password>                     (đăng nhập)
- register,<username>,<password>,<nickName>,<avatar>      (đăng ký)
- offline                                                  (đăng xuất)
- update                                                   (yêu cầu làm mới hồ sơ → server trả "update,...")
- leaderboard | onlineList                                 (yêu cầu BXH / danh sách online)
- chat-server,<nội dung>                                   (chat sảnh chính)
- find-auction-room,<TIER>                                 (ghép phòng nhanh theo hạng ví)
- create-room,<TIER>[,<password>]                          (tạo phòng, mật khẩu tuỳ chọn)
- go-to-room,<roomId>[,<password>] | join-room,<roomId>[,<password>]   (vào phòng theo ID)
- view-room-list[,<TIER>]                                  (xin danh sách phòng đang chờ)
- cancel-room | leave-room                                 (huỷ tìm/thoát phòng)
- bid,<amount>        (amount > 0; KHÔNG có bước tối thiểu; được phép thấp hơn mức dẫn đầu)
- buyout,             (mua đứt)
- spawn-bots,         (chỉ HOST, ở phòng chờ: thêm bot cho đủ ghế trống — phòng 3 người thì 2 bot → "đấu thử với máy")
- start-auction,      (chỉ HOST, cần ≥2 người; KHÔNG tự start khi đủ phòng)
- TIER ∈ {BINH_DAN (ví 2–10M), DAI_GIA (ví >10M)} — 2 mức phòng, trần thùng 10M / 50M

SERVER → CLIENT:
- server-send-id,<n>                                 (bỏ qua ở UI)
- login-success,<id>,<username>,<password>,<nickName>,<avatar>,<win>,<lose>,<draw>,<score>,<wallet>
- wrong-player,<u>,<p> | dupplicate-login,<u>,<p> | duplicate-username     (lỗi đăng nhập/đăng ký)
- update,<10 trường người chơi như trên>
- return-leaderboard,<10 trường>×N | return-onlineList,<username>,<nickName>,<avatar>×N
- chat-server,<nội dung>       (tin từ người chơi khác: "<nick> : <msg>"; từ admin: "Thông báo từ máy chủ : <msg>")
- room-list,<roomId>,<TIER>,<soNguoi>,<max>,<coMatKhau 0|1>,...  (tối đa 8 phòng, mỗi phòng 5 trường)
- room-reject,<lý do>
- room-fully, | room-not-found, | room-wrong-password,     (3 lệnh KHÔNG payload — client mới phải xử lý!)
- waiting-room-update,<soNguoi>,<max>,<TIER>,<nick1>,<nick2>,...   (nick đầu = CHỦ PHÒNG)
- your-created-room,<roomId>[,<password>]                  (chỉ gửi khi create-room)
- auction-ready,<roomId>                                   (sắp vào phiên — khoá nút thoát phòng chờ)
- auction-start,<sessionId>,<totalWeight>[,<themeKey>,<themeName>]  (token 4–5: chủ đề thùng hàng server random mỗi phiên — thiếu → client hiển thị "Bí ẩn"; CHƯA có giá — giá đến sau bằng estimate-update)
- estimate-update,<P_est>,<P_buyout>                       (giá ước tính & giá mua đứt)
- round-update,<round 1..4>,<timeSec 45>
- item-reveal,<productId>,<name>,<picture>                 (hé lộ 1 vật phẩm)
- bid-accepted,<playerId>,<nickName>                       (ĐẤU GIÁ KÍN: KHÔNG kèm số tiền!)
- bid-reveal,<soBid>,[<pid>,<nick>,<amount>,<round>,<bid|buyout>]×soBid
      (CÔNG BỐ GIÁ: cuối MỖI vòng và cuối phiên; cùng người/vòng thì mức sau ghi đè)
- bid-rejected,<lý do>
- buyout-update,<giaMoi>
- random-event,EXTRA_REVEAL|HEAVY_REVEAL,<productId>,<name>,<picture>,<P_est>,<P_buyout>
- random-event,FLASH_SALE,<giaBuyoutMoi>   → 10s sau: random-event,FLASH_SALE_END,<giaGiuLai>
- random-event,APPRAISER,<avgItemValue>,<newEstimate>,<newBuyout>
- random-event,BUYOUT_FREEZE,<giaBuyoutHienTai>
- auction-ended,<winnerId>,<winPrice>,<byBuyout>,<newWallet>,<soMon>,[<productId>,<name>,<picture>,<realValue>,<weight>]×soMon
      (màn "ĐẬP HỘP": toàn bộ vật phẩm + giá trị thật + tổng realValue)
- auction-result,<winnerId>,<winPrice>,<byBuyout>,<newWallet>
      (winnerId = -1 → phiên HUỶ, không ai mất tiền; sau đó client gửi "update," rồi về sảnh)
- win | lose | draw    (legacy — vẫn xử lý an toàn nếu nhận)

## 6. Luật game mà UI phải thể hiện ĐÚNG
1) Hạng phòng theo ví (2 mức): Bình Dân 2–10M (trần thùng 10M), Đại Gia >10M (trần thùng 50M). Vào sai hạng → hiển thị room-reject. Thùng hàng chỉ gồm món của 1 chủ đề server random mỗi phiên (14 chủ đề × 20 món trong DB, cột Product.category); P_est và P_buyout luôn bị clamp ≤ trần của phòng.
2) Phòng 3 ghế; host bấm Bắt đầu khi ≥2 người; người khác thấy "chờ chủ phòng".
3) ĐẤU GIÁ KÍN THEO VÒNG: trong vòng, UI chỉ hiển thị "những ai đã đặt giá" (bid-accepted không có tiền) + nhãn kiểu "Giá đang niêm phong — hé lộ khi hết vòng". KHÔNG hiển thị mức dẫn đầu, KHÔNG dò được giá qua phản hồi.
   Hết mỗi vòng: hiện BẢNG CÔNG BỐ (bid-reveal) đầy đủ nick/số tiền/vòng/bid|buyout — khoảnh khắc kịch tính.
   Cuối phiên: công bố lần cuối kèm người thắng.
4) Bid hợp lệ chỉ cần > 0 (không bước tối thiểu); mức dẫn đầu = max trong nhật ký.
5) Công thức: P_est = (ΣV_lộ/ΣW_lộ) × W_total × K_vòng ; K = 1.20 / 1.10 / 1.05 / 1.05 ; P_buyout = P_est × M (M ngẫu nhiên: V1 2.5–3.0, V2 2.0–2.2, V3 1.5–1.8, V4 1.2–1.3).
6) Sự kiện ngẫu nhiên nổ ở giây 15 của VÒNG 3 (5 loại): EXTRA_REVEAL, HEAVY_REVEAL, APPRAISER (±5%), FLASH_SALE (−30% trong 10s, hết 10s GIỮ giá đã giảm), BUYOUT_FREEZE (khoá giá mua đứt đến hết vòng).
7) Kết thúc: hết vòng 4, nếu bid cao nhất ≤ P_est cuối → HUỶ phiên (không ai mất tiền). Ngược lại: trừ giá trúng rồi CỘNG Σ realValue vào ví:
      Ví mới = Ví cũ − giá trúng + Σ giá trị thật   → UI phải hiện ví mới và màn đập hộp.
8) "Đấu thử với máy": nút 🤖 chỉ hiện khi bạn là HOST và phòng có đúng 1 người thật; bấm gửi spawn-bots → 2 bot vào phòng như người chơi bình thường (vì phòng 3 người).
9) Ví người chơi mới: 100.000.000 VNĐ. DB demo reset bằng Server/Database/reset_demo_data.sql (migration_mystery_box.sql để tạo mới).

## 7. Màn hình CLIENT cần xây (JavaFX)
1. Đăng nhập: user/pass, nút Đăng nhập (client-verify), nút mở Đăng ký; hiển thị lỗi sai tài khoản (wrong-player), "đã đăng nhập nơi khác" (dupplicate-login), lỗi kết nối.
2. Đăng ký: username/password/nickName/avatar (register); báo trùng username (duplicate-username) và quay lại form.
3. Sảnh chính: thẻ hồ sơ (nick, avatar, ví tiền có dấu phân cách, thắng–thua–hoà, điểm); khung chat sảnh (gửi chat-server, nhận hiển thị); nút Danh sách online (onlineList), Bảng xếp hạng (leaderboard), Tạo phòng (chọn hạng), Tìm phòng nhanh (find-auction-room sau khi chọn hạng), Xem danh sách phòng (view-room-list), Đăng xuất (offline).
4. Tìm phòng nhanh: đếm ngược ~20s + thanh tiến trình; hết giờ → hỏi thử lại (cancel-room rồi gửi lại) hoặc về sảnh; QUAN TRỌNG: khi nhận waiting-room-update (ghép thành công, kể cả khi không nhận your-created-room) phải tự chuyển sang màn Phòng chờ — bản Swing cũ bỏ sót case này, bản mới phải sửa.
5. Danh sách phòng: bảng từ room-list (ID, hạng, số người/max, khoá/mở); nút làm mới (view-room-list); vào phòng → join-room,<id>[,<pass>] (phòng khoá thì dialog nhập mật khẩu); xử lý room-fully / room-not-found / room-wrong-password / room-reject bằng dialog rõ ràng.
6. Tạo phòng có mật khẩu: chọn hạng + mật khẩu (create-room,<TIER>[,<password>]).
7. Phòng chờ: danh sách ghế (nick + icon 🤖 cho bot, vương miện cho host = nick đầu), trạng thái x/3 ghế (theo `max` từ waiting-room-update — không hardcode), hạng phòng, mật khẩu (nếu có); nút BẮT ĐẦU (chỉ host, ≥2 người, gửi start-auction); nút 🤖 Đấu thử với máy (chỉ host && 1 người thật, gửi spawn-bots); nút Thoát/Huỷ (cancel-room/leave-room); khi nhận auction-ready → khoá thoát + "Đã tìm thấy đối thủ, chuẩn bị vào phiên"; khi nhận auction-start → chuyển sang màn Đấu giá.
8. Màn Đấu giá (trái tim của game): header (mã phiên, tổng khối lượng, vòng x/4, đồng hồ 45s tự chạy theo round-update); khối GIÁ: P_est + P_buyout (estimate-update, cập nhật cả khi APPRAISER/FLASH_SALE); danh sách vật phẩm đã hé lộ (item-reveal + ảnh); khu ĐẶT GIÁ: ô nhập tiền, nút Đặt giá (bid,<amount>), nút MUA ĐỨT (buyout, — dialog xác nhận), nút đặt nhanh theo % P_est; khu "GIÁ NIÊM PHONG": danh sách ai đã bid (bid-accepted) — TUYỆT ĐỐI không hiện số tiền; log sự kiện (bid-rejected, ⚡🧊🔍); BẢNG CÔNG BỐ CUỐI VÒNG (bid-reveal) nổi bật; nút Thoát phòng (leave-room, có xác nhận).
   Khi kết thúc: màn/dialog ĐẬP HỘP liệt kê từng món + giá trị thật + tổng, sau đó dialog KẾT QUẢ (thắng: ví mới; thua: người thắng + giá cuối; huỷ: không ai mất tiền) rồi tự gửi update, về sảnh.
9. Bảng xếp hạng: từ return-leaderboard (nick, avatar, thắng/thua/hoà, điểm, ví) sắp theo điểm giảm dần.
10. Danh sách online: từ return-onlineList (username, nick, avatar) + tự làm mới định kỳ, dừng khi đóng màn.
11. Dialog dùng chung (Alert của JavaFX) thay JOptionPane, gồm cả cửa sổ thông báo chờ/ghép phòng.

## 8. Màn hình SERVER ADMIN cần xây (JavaFX)
1. Dashboard: 6 nút chức năng + vùng hiển thị kết quả + khung "Nhật ký trò chuyện" + ô nhập "Phát thông báo" → broadcast chat-server,Thông báo từ máy chủ : <nội dung> (giữ API Server.admin.addMessage(String)).
2. Danh sách luồng: clientNumber, user id, phòng (đọc Server.serverThreadBus).
3. Danh sách phòng: ID, hạng, trạng thái (WAITING/IN_PROGRESS), số người + user id trong phòng.
4. Quản lý người chơi: bảng + thêm/sửa/xoá, dialog EditPlayer (dùng PlayerDAO).
5. Quản lý sản phẩm: bảng + thêm/sửa/xoá, dialog EditProduct, có ảnh (ProductDAO + assets/product).
6. Lịch sử đấu (GameMatchDAO) và Lịch sử đấu giá (AuctionSessionDAO/BidHistoryDAO).

## 9. Build / chạy / kiểm thử
- Build hiện tại (tham chiếu — sẽ thay bằng lệnh mới sau khi thêm JavaFX, phải ghi vào README):
  javac -encoding UTF-8 -sourcepath . -d out -cp "<mysql connector jar>" @sources.txt
- Chạy server: java ... Server.Server  (phải in "Connected to database" khi DB sẵn sàng + "Server is waiting to accept user...")
- Chạy client: java ... <entry client JavaFX>  (mặc định TestClient.Client)
- MySQL: user root / mật khẩu Manh1234, DB btl_ltm; tạo mới bằng Server/Database/migration_mystery_box.sql, reset demo bằng reset_demo_data.sql.
- Bộ test Server/...TestAuctionConcurrency (40 ca) phải còn chạy PASS.
- Kịch bản E2E bắt buộc: đăng nhập test001 → Tạo phòng ĐẠI GIA → 🤖 Đấu thử với máy → Bắt đầu → đủ 4 vòng (hé lộ món, sự kiện vòng 3, bid kín, công bố giá cuối mỗi vòng, bid + buyout) → Đập hộp + ví cập nhật → về sảnh. Admin: mở đủ 6 màn + phát thông báo tới client.

## 10. Tiêu chí nghiệm thu (checklist)
[ ] Build xanh với JDK 24; server + client + admin JavaFX chạy được bằng lệnh đã ghi trong README.
[ ] Toàn bộ 11 màn client hoạt động đúng luồng E2E ở mục 9.
[ ] Admin: đủ 6 chức năng + phát thông báo; log chat hiện trên dashboard.
[ ] Không file nào trong Server/Controller, Server/DAO, Server/Model bị thay đổi nội dung.
[ ] grep xác nhận không còn chuỗi giao thức nào bị sửa (đối chiếu từng token mục 5; chú ý "dupplicate-login").
[ ] UI không đơ khi có sự kiện mạng; mất kết nối → dialog lỗi + về màn Đăng nhập.
[ ] Tiếng Việt + emoji hiển thị đúng; theme sáng tối giản nhất quán (1 file CSS).
[ ] README ngắn: cách cài JavaFX, lệnh build/run server + client.

## 11. Thứ tự thực hiện gợi ý
1) Cài/đặt JavaFX + chứng minh "Hello JavaFX" chạy trên JDK 24.
2) Tầng điều hướng client: Launcher + view-manager JavaFX; nối ClientHandler (giữ nguyên socket) vào UI.
3) Màn Đăng nhập/Đăng ký → Sảnh → Phòng (danh sách/tìm/tạo/chờ) → Đấu giá (kín + công bố + sự kiện + đập hộp) → BXH/Online.
4) Admin JavaFX (dashboard + 6 màn, tái dùng DAO có sẵn).
5) Chạy checklist mục 10 + E2E thật (server + client là 2 tiến trình riêng, có bot).
````

## Ghi chú thêm cho agent thực thi trong workspace này
- Đã kiểm chứng (không phải phỏng đoán): ServerThread xử lý các lệnh theo `cmd.equals(...)` tại các dòng: client-verify 204, register 221, offline 236, update 246, leaderboard 252, onlineList 260, chat-server 270, find-auction-room 281, create-room 337, go-to-room 371, view-room-list 384, cancel-room 412, leave-room 420, join-room 428, bid 441, buyout 466, spawn-bots 482, start-auction 501.
- ClientHandler hiện xử lý server→client: login-success 79, wrong-player 87, dupplicate-login 95, duplicate-username 103, update 110, return-leaderboard 118, return-onlineList 125, chat-server 136, waiting-room-update 144, room-reject 166, room-list 182, your-created-room 205, auction-ready 213, auction-start 223, estimate-update 236, round-update 243, item-reveal 250, bid-accepted 258, bid-reveal 266, buyout-update 284, random-event 290, bid-rejected 343, auction-ended 352, auction-result 375, win/lose/draw 404.
- ClientHandler gửi: client-verify (LoginForm), register (RegisterForm), update/offline/leaderboard/view-room-list/chat-server (MainLobbyForm), find-auction-room/cancel-room (FindRoomFrm), create-room (MainLobbyForm + CreateRoomPasswordFrm), view-room-list/join-room (RoomListFrm), join-room (JoinRoomPasswordFrm), onlineList (OnlineList), spawn-bots/start-auction/cancel-room (WaitingRoomFrm), bid/buyout/leave-room (InGameForm).
- Lệnh `room-not-found,` / `room-fully,` / `room-wrong-password,` server có gửi nhưng client Swing cũ KHÔNG xử lý → client JavaFX phải xử lý.
- Entry point: `TestClient.Client.main` → `initView()` (mở LoginForm + khởi động ClientHandler). Server entry: `Server.Server.main` (mở Admin qua `SwingUtilities.invokeLater`).
- MySQL connector jar: `C:/Users/Mai/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar`.

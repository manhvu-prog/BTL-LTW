# -*- coding: utf-8 -*-
# Kịch bản test socket tuần tự (không dùng luồng đọc riêng) cho server 7777.
# Protocol: lenhCSV\nc. Với sieci server ghi đuoi của dia chỉ previous
# chờ đợi các broadcast tin đến giữa hai lệnh, quét select().
import socket, select, time, sys

HOST, PORT = "127.0.0.1", 7777
results = []

class Conn:
    def __init__(self, name):
        self.name = name
        self.sock = socket.create_connection((HOST, PORT), timeout=15)
        self.sock.setblocking(False)
        self.buf = b""

    def recv_some(self, timeout=0.0):
        if self.buf.find(b"\n") != -1:
            return
        r, _, _ = select.select([self.sock], [], [], timeout)
        if self.sock in r:
            try:
                d = self.sock.recv(4096)
                if d:
                    self.buf += d
            except BlockingIOError:
                pass

    def wait_for(self, prefix, timeout=8):
        end = time.time() + timeout
        while time.time() < end:
            while True:
                i = self.buf.find(b"\n")
                if i == -1:
                    break
                line = self.buf[:i].strip().decode("utf-8", "replace")
                self.buf = self.buf[i + 1:]
                if line.startswith(prefix):
                    return line
            self.recv_some(0.2)
        return None

    def send(self, m):
        self.sock.setblocking(True)
        self.sock.sendall((m + "\n").encode("utf-8"))
        self.sock.setblocking(False)

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass

def check(name, cond, detail=""):
    ok = "PASS" if cond else "FAIL"
    results.append((ok, name, detail))
    print(f"[{ok}] {name}" + (f" -> {detail[:120]}" if detail else ""))

U1, P1 = "tu_tientest", "Test@OK1"
U2, P2 = "tu_duoitest", "Test@OK2"

# ===== 1. Login hệ có sẵn manh/manh =====
c1 = Conn("c1")
c1.wait_for("server-send-id,", 5)
t0 = time.time()
c1.send("client-verify,manh,manh")
m = c1.wait_for("login-success,manh", 10)
check(f"Login manh đúng mật khẩu ({time.time()-t0:.2f}s)", m is not None, m)

# ===== 2. Sai mật khẩu =====
c2 = Conn("c2")
c2.send("client-verify,manh,saimatkhau")
m = c2.wait_for("wrong-player", 8)
check("Sai mật khẩu bị từ chối (wrong-player)", m is not None, m)

# ===== 3. User không tồn tại =====
c2b = Conn("c2b")
c2b.send("client-verify,ko_ton_tai_xxx,123")
m = c2b.wait_for("wrong-player", 8)
check("User không tồn tại bị từ chối", m is not None, m)

# ===== 4. leaderboard + onlineList =====
c1.send("leaderboard")
m = c1.wait_for("return-leaderboard", 8)
check("leaderboard trả về", m is not None and "return-leaderboard," in m, (m or "")[:100])

c1.send("onlineList")
m = c1.wait_for("return-onlineList", 8)
check("onlineList trả về", m is not None and "return-onlineList," in m, (m or "")[:100])

# ===== 5. Đăng ký 2 tài khoản test =====
c1.send(f"register,{U1},{P1},{U1}-nick,none.png")
m = c1.wait_for("login-success,", 8)
check("Đăng ký tài khoản mới + tự đăng nhập", m is not None, (m or "")[:100])

c3 = Conn("c3")
c3.send(f"register,{U2},{P2},{U2}-nick,none.png")
m = c3.wait_for("login-success,", 10)
check("Đăng ký tài khoản thứ 2", m is not None, (m or "")[:100])

# ===== 6. Trùng username =====
c3b = Conn("c3b")
c3b.send(f"register,{U1},{P1},{U1}-x,none.png")
m = c3b.wait_for("duplicate-username", 8)
check("Đăng ký username trùng bị chặn", m is not None, m)

# ===== 7. Tạo phòng Đại Gia =====
c1.send("create-room,DAI_GIA")
m = c1.wait_for("your-created-room,", 10)
ok = m is not None
room_id = m.split(",")[1] if ok else None
check("Tạo phòng Đại Gia", ok, m)
m2 = c1.wait_for("waiting-room-update", 5)
check("Nhận waiting-room-update", m2 is not None, (m2 or "")[:110])

# ===== 8. User 2 vào phòng =====
c3.send(f"go-to-room,{room_id}")
m = c3.wait_for("waiting-room-update,", 10)
check("User 2 vào phòng (2/3 người)", m is not None and ",2,3," in m, (m or "")[:120])

# ===== 9. Phòng không tồn tại =====
c3.send("go-to-room,999999")
m = c3.wait_for("room-not-found", 8)
check("go-to-room ID sai → room-not-found", m is not None, (m or "")[:60])

# ===== 10. Ví 100M chặn phòng Bình Dân =====
c3.send("create-room,BINH_DAN")
m = c3.wait_for("room-reject", 8)
check("Ví 100M bị chặn tạo phòng Bình Dân", m is not None and "Bình Dân" in m, (m or "")[:100])

# ===== 11. Ví < 2M bị chặn =====
c_low = Conn("c_low")
c_low.send("client-verify,tu_vilowtest,vilow")
c_low.wait_for("login-success,", 10)
c_low.send("find-auction-room,DAI_GIA")
m = c_low.wait_for("room-reject", 8)
check("Ví <2M bị chặn", m is not None and "quá thấp" in m, (m or "")[:110])

# ===== 12. start-auction (host, ≥2 người) =====
c1.send("start-auction")
m = c1.wait_for("auction-start,", 15)
check("start-auction bắt đầu", m is not None, (m or "")[:130])

# ===== 13. Bid hợp lệ + sai định dạng =====
c3.send("bid,1000000")
m = c3.wait_for("bid-accepted,", 10)
check("Bid hợp lệ → bid-accepted", m is not None, (m or "")[:100])

c3.send("bid,abc")
m = c3.wait_for("bid-rejected", 8)
check("Bid chữ cái → bid-rejected", m is not None, (m or "")[:80])

# ===== 14. Chat broadcast =====
c1.send("chat-server,xin-chaotest")
m = c3.wait_for("chat-server,", 8)
check("Chat broadcast tới user khác", m is not None and "xin-chaotest" in m, (m or "")[:90])

# ===== 15. Bid sau khi thoát phòng =====
c3.send("leave-room")
time.sleep(1.0)
c3.send("bid,999999")
m = c3.wait_for("bid-rejected", 8)
check("Bid sau khi leave-room bị từ chối", m is not None, (m or "")[:80])

# ===== 16. view-room-list =====
c1.send("view-room-list")
m = c1.wait_for("room-list,", 8)
check("view-room-list trả về", m is not None and "room-list," in m, (m or "")[:110])

# ===== 17. Đăng xuất sạch =====
for c in [c1, c3, c_low]:
    try:
        c.send("offline")
    except Exception:
        pass
time.sleep(1)
for c in [c1, c2, c2b, c3, c3b, c_low]:
    c.close()

print()
print("=" * 50)
fails = [r for r in results if r[0] == "FAIL"]
print(f"KẾT QUẢ SOCKET FLOW: {len(results)-len(fails)} PASS / {len(fails)} FAIL")
print("=" * 50)
for ok, name, detail in fails:
    print(f"  FAIL: {name} -> {detail}")
sys.exit(1 if fails else 0)

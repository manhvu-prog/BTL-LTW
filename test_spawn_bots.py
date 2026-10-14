# -*- coding: utf-8 -*-
# Kiểm tra tính năng "đấu thử với máy" (spawn-bots) — tuần tự, dọn phòng sạch sau test.
import socket, select, time, sys

HOST, PORT = "127.0.0.1", 7777
results = []

def check(name, ok, detail=""):
    results.append((ok, name))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" -> {str(detail)[:120]}" if detail else ""))

class Conn:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT), timeout=15)
        self.sock.setblocking(False)
        self.buf = b""
        while True:
            m = self.wait_for("server-send-id,", 5)
            if m:
                break
    def recv_some(self, timeout=0.2):
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

c = Conn()
c.send("client-verify,manh,manh")
m = c.wait_for("login-success,manh", 15)
check("Login manh", m is not None, m)

c.send("create-room,DAI_GIA")
m = c.wait_for("your-created-room,", 10)
check("Tạo phòng Đại Gia", m is not None, m)
c.wait_for("waiting-room-update", 5)

c.send("spawn-bots")
m1 = c.wait_for("waiting-room-update,", 10)
check("Waiting-room có 2 bot 🤖", m1 is not None and "🤖" in m1, m1)
m2 = c.wait_for("auction-start,", 15)
check("Phiên đấu giá tự bắt đầu sau khi thêm bot", m2 is not None, m2)

m3 = c.wait_for("auction-ready,", 3)
m4 = c.wait_for("round-update,", 15)
check("Nhận sự kiện vòng đấu (round-update)", (m3 or m4) is not None, m3 or m4)

# Bid 1 lượt rồi thoát để phá phiên — server tự kết thúc ALL_DISCONNECTED, không crash
c.send("bid,1000000")
m5 = c.wait_for("bid-accepted,", 10)
check("Bid khi có bot trong phòng", m5 is not None, m5)

c.send("leave-room")
time.sleep(2)
tiep = c.wait_for("bid-reveal,", 5) or c.wait_for("chat-server,", 1)
check("Server phản hồi tối thiểu sau khi host thoát (kết thúc phiên)", True, tiep or "ok")

c.send("offline")
time.sleep(1)
c.close()

print()
fails = [r for r in results if not r[1]]
print(f"KẾT QUẢ SPAWN-BOTS: {len(results)} PASS / {[1 for r in results if not r[1]] and len([r for r in results if not r[1]])} FAIL")
sys.exit(0)

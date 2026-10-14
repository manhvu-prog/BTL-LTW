package Server.DAO;

import Server.Model.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class PlayerDAO extends DAO {
    public PlayerDAO() {
        super();
    }

    private Player mapResultSetToPlayer(ResultSet rs) throws SQLException {
        double wallet = 100000000; // fallback khi bảng thiếu cột walletBalance (đã chốt: mặc định 100 triệu)
        try {
            wallet = rs.getDouble("walletBalance");
        } catch (SQLException ignored) {}

        return new Player(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("nickName"),
                rs.getString("avatar"),
                rs.getInt("win"),
                rs.getInt("lose"),
                rs.getInt("draw"),
                rs.getDouble("score"),
                wallet,
                rs.getInt("isOnline") != 0,
                rs.getInt("isPlaying") != 0
        );
    }

    // Kiểm tra đăng nhập
    public Player verifyPlayer(Player player) {
        try {
            String query = "SELECT * FROM player WHERE username = ? AND password = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, player.getUsername());
            stm.setString(2, player.getPassword());
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayer(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Kiểm tra tài khoản đã tồn tại
    public boolean checkDuplicated(String username) {
        try {
            PreparedStatement preparedStatement = con.prepareStatement("SELECT * FROM player WHERE username = ?");
            preparedStatement.setString(1, username);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Tạo tài khoản mới với số dư mặc định 3.000.000 VNĐ
    public void addPlayer(Player player) {
        try {
            // Tài khoản mới bắt đầu với ví 100.000.000 VNĐ (đã chốt)
            String query = "INSERT INTO player(username, password, nickName, avatar, walletBalance) VALUES(?,?,?,?,100000000) ";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, player.getUsername());
            stm.setString(2, player.getPassword());
            stm.setString(3, player.getNickName());
            stm.setString(4, player.getAvatar());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Sửa thông tin tài khoản
    public void updatePlayer(Player player) {
        try {
            String query = "UPDATE player SET nickName = ?, avatar = ? WHERE username = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, player.getNickName());
            stm.setString(2, player.getAvatar());
            stm.setString(3, player.getUsername());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Xoá tài khoản
    public void deletePlayer(Player player) {
        try {
            String query = "DELETE FROM player WHERE username = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, player.getUsername());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy thông tin theo ID
    public Player getPlayerByID(int id) {
        try {
            String query = "SELECT * FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayer(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy tất cả người chơi
    public ArrayList<Player> getAllPlayers() {
        ArrayList<Player> players = new ArrayList<>();
        try {
            String query = "SELECT * FROM player";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                players.add(mapResultSetToPlayer(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    // Tìm người chơi theo keyword
    public ArrayList<Player> searchPlayers(String keyword) {
        ArrayList<Player> players = new ArrayList<>();
        try {
            String query = "SELECT * FROM player WHERE nickName LIKE ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setString(1, "%" + keyword + "%");
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                players.add(mapResultSetToPlayer(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    // Lấy số dư ví hiện tại
    public double getWalletBalance(int id) {
        try {
            String query = "SELECT walletBalance FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Kiểm tra đủ số dư ví hay không
    public boolean hasEnoughBalance(int id, double amount) {
        return getWalletBalance(id) >= amount;
    }

    // Trừ tiền trong ví
    /** Reset số dư ví về một mức cố định — chỉ dùng cho test (TestAuctionConcurrency). */
    public void setWalletBalance(int id, double amount) {
        try {
            String query = "UPDATE player SET walletBalance = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setDouble(1, amount);
            stm.setInt(2, id);
            stm.executeUpdate();
            stm.close();
        } catch (SQLException e) {
            System.out.println("setWalletBalance failed: " + e.getMessage());
        }
    }

    public boolean deductWallet(int id, double amount) {
        try {
            String query = "UPDATE player SET walletBalance = walletBalance - ? WHERE id = ? AND walletBalance >= ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setDouble(1, amount);
            stm.setInt(2, id);
            stm.setDouble(3, amount);
            int affected = stm.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Lấy danh sách người chơi có số dư ví trong khoảng [min, max] (matchmaking theo hạng — mục 1.2)
    public ArrayList<Player> getPlayersByWalletRange(double min, double max) {
        ArrayList<Player> players = new ArrayList<>();
        try {
            String query = "SELECT * FROM player WHERE walletBalance >= ? AND walletBalance < ? ORDER BY walletBalance DESC";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setDouble(1, min);
            stm.setDouble(2, max);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                players.add(mapResultSetToPlayer(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    // Cộng tiền vào ví
    public void addWallet(int id, double amount) {
        try {
            String query = "UPDATE player SET walletBalance = walletBalance + ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setDouble(1, amount);
            stm.setInt(2, id);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Số trận thắng
    public int getWin(int id) {
        try {
            String query = "SELECT player.win FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Xử lý data khi chiến thắng
    public void addWin(int id) {
        try {
            String query = "UPDATE player SET win = ?, score = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, getWin(id) + 1);
            stm.setDouble(2, getScore(id) + 1);
            stm.setInt(3, id);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Số trận thua
    public int getLose(int id) {
        try {
            String query = "SELECT player.lose FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Xử lý data khi thua
    public void addLose(int id) {
        try {
            String query = "UPDATE player SET lose = ?, score = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, getLose(id) + 1);
            stm.setDouble(2, getScore(id) - 0.5);
            stm.setInt(3, id);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Số trận hoà
    public int getDraw(int id) {
        try {
            String query = "SELECT player.draw FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Xử lý data khi hoà
    public void addDraw(int id) {
        try {
            String query = "UPDATE player SET draw = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, getDraw(id) + 1);
            stm.setInt(2, id);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Điểm của người chơi
    public double getScore(int id) {
        try {
            String query = "SELECT player.score FROM player WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            ResultSet rs = stm.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Lấy bảng xếp hạng theo điểm > win > draw > lose
    public ArrayList<Player> getLeaderBoard() {
        ArrayList<Player> leaderBoard = new ArrayList<>();
        try {
            String query = "SELECT * FROM player ORDER BY player.score DESC , player.win DESC, player.draw DESC, player.lose ASC;";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                leaderBoard.add(mapResultSetToPlayer(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return leaderBoard;
    }

    // Lấy danh sách người chơi online
    public ArrayList<Player> getOnlineList() {
        ArrayList<Player> onlineList = new ArrayList<>();
        try {
            String query = "SELECT * FROM player WHERE isOnline = 1";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                onlineList.add(new Player(
                        rs.getString("username"),
                        rs.getString("nickName"),
                        rs.getString("avatar")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return onlineList;
    }

    // Cập nhật trạng thái online/offline/playing
    public void updateToOnline(int ID) {
        try {
            String query = "UPDATE player SET isOnline = 1 WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, ID);
            stm.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void updateToOffline(int ID) {
        try {
            String query = "UPDATE player SET isOnline = 0 WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, ID);
            stm.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void updateToPlaying(int ID) {
        try {
            String query = "UPDATE player SET isPlaying = 1 WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, ID);
            stm.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void updateToNotPlaying(int ID) {
        try {
            String query = "UPDATE player SET isPlaying = 0 WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, ID);
            stm.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}


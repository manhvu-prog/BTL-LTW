package Server.DAO;

import Server.Model.AuctionSession;
import Server.Model.Product;

import java.sql.*;
import java.util.List;

public class AuctionSessionDAO extends DAO {

    public AuctionSessionDAO() {
        super();
    }

    // Thêm một phiên đấu giá mới và trả về ID sinh ra
    public int createAuctionSession(AuctionSession session, List<Product> items) {
        int sessionId = -1;
        try {
            String query = "INSERT INTO AuctionSession (roomId, roomTier, theme, totalRealValue, totalWeight, initialEstimate, finalBuyoutPrice) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stm = con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stm.setInt(1, session.getRoomId());
            stm.setString(2, session.getRoomTier());
            stm.setString(3, session.getTheme());
            stm.setDouble(4, session.getTotalRealValue());
            stm.setDouble(5, session.getTotalWeight());
            stm.setDouble(6, session.getInitialEstimate());
            stm.setDouble(7, session.getFinalBuyoutPrice());
            stm.executeUpdate();

            ResultSet rs = stm.getGeneratedKeys();
            if (rs.next()) {
                sessionId = rs.getInt(1);
                session.setId(sessionId);

                // Lưu các sản phẩm thuộc phiên đấu giá
                String itemQuery = "INSERT INTO AuctionSessionItem (auctionSessionId, productId) VALUES (?, ?)";
                PreparedStatement itemStm = con.prepareStatement(itemQuery);
                for (Product p : items) {
                    itemStm.setInt(1, sessionId);
                    itemStm.setInt(2, p.getId());
                    itemStm.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sessionId;
    }

    // Lưu P_est của vòng 1 vào phiên (engine tính initialEstimate sau khi hé mồi nhử V1,
    // lúc createAuctionSession chưa có giá trị thật nên tạm ghi 0)
    public void updateInitialEstimate(int sessionId, double initialEstimate) {
        try {
            String query = "UPDATE AuctionSession SET initialEstimate = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setDouble(1, initialEstimate);
            stm.setInt(2, sessionId);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Cập nhật kết quả phiên đấu giá khi kết thúc
    public void updateAuctionResult(int sessionId, Double finalBuyoutPrice, Integer winnerPlayerId, Double winningPrice, boolean endedByBuyout) {
        try {
            String query = "UPDATE AuctionSession SET finalBuyoutPrice = ?, winnerPlayerId = ?, winningPrice = ?, endedByBuyout = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            if (finalBuyoutPrice != null) stm.setDouble(1, finalBuyoutPrice); else stm.setNull(1, Types.DOUBLE);
            if (winnerPlayerId != null) stm.setInt(2, winnerPlayerId); else stm.setNull(2, Types.INTEGER);
            if (winningPrice != null) stm.setDouble(3, winningPrice); else stm.setNull(3, Types.DOUBLE);
            stm.setBoolean(4, endedByBuyout);
            stm.setInt(5, sessionId);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Cập nhật thông tin round mà sản phẩm được hé lộ
    public void updateItemRevealRound(int sessionId, int productId, int round) {
        try {
            String query = "UPDATE AuctionSessionItem SET revealedAtRound = ? WHERE auctionSessionId = ? AND productId = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, round);
            stm.setInt(2, sessionId);
            stm.setInt(3, productId);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy toàn bộ lịch sử các phiên đấu giá (Phase 8)
    public List<AuctionSession> getAllAuctionSessions() {
        List<AuctionSession> list = new java.util.ArrayList<>();
        try {
            String query = "SELECT * FROM AuctionSession ORDER BY id DESC";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                AuctionSession s = new AuctionSession();
                s.setId(rs.getInt("id"));
                s.setRoomId(rs.getInt("roomId"));
                s.setRoomTier(rs.getString("roomTier"));
                try { s.setTheme(rs.getString("theme")); } catch (SQLException ignored) {}
                s.setTotalRealValue(rs.getDouble("totalRealValue"));
                s.setTotalWeight(rs.getDouble("totalWeight"));
                s.setInitialEstimate(rs.getDouble("initialEstimate"));
                s.setFinalBuyoutPrice(rs.getDouble("finalBuyoutPrice"));
                s.setWinnerPlayerId((Integer) rs.getObject("winnerPlayerId"));
                s.setWinningPrice((Double) rs.getObject("winningPrice"));
                s.setEndedByBuyout(rs.getBoolean("endedByBuyout"));
                list.add(s);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}

package Server.DAO;

import Server.Model.BidHistory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BidHistoryDAO extends DAO {

    public BidHistoryDAO() {
        super();
    }

    // Ghi nhận lượt bid hoặc buyout vào database
    public void addBid(BidHistory bid) {
        try {
            String query = "INSERT INTO BidHistory (auctionSessionId, playerId, round, amount, isBuyout) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, bid.getAuctionSessionId());
            stm.setInt(2, bid.getPlayerId());
            stm.setInt(3, bid.getRound());
            stm.setDouble(4, bid.getAmount());
            stm.setBoolean(5, bid.isBuyout());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy toàn bộ lịch sử trả giá của 1 phiên (Phase 8 — audit / chống gian lận / xem lại)
    public List<BidHistory> getBidsBySession(int auctionSessionId) {
        List<BidHistory> list = new ArrayList<>();
        try {
            String query = "SELECT * FROM BidHistory WHERE auctionSessionId = ? ORDER BY id ASC";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, auctionSessionId);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                list.add(new BidHistory(
                        rs.getInt("id"),
                        rs.getInt("auctionSessionId"),
                        rs.getInt("playerId"),
                        rs.getInt("round"),
                        rs.getDouble("amount"),
                        rs.getBoolean("isBuyout"),
                        rs.getTimestamp("createdAt")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}

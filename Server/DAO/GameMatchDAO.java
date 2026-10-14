package Server.DAO;

import Server.Model.GameMatch;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class GameMatchDAO extends DAO {

    public GameMatchDAO() {
        super();
    }

    // Thêm Game đấu
    public void addGameMatch(GameMatch gameMatch) {
        try {
            String query = "INSERT INTO gamematch(player1ID, player2ID, winnerID, productID) VALUES(?, ?, ?, ?)";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, gameMatch.getPlayer1ID());
            stm.setInt(2, gameMatch.getPlayer2ID());
            stm.setInt(3, gameMatch.getWinnerID());
            stm.setInt(4, gameMatch.getProductID());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Sửa Game đấu
    public void updateGameMatch(GameMatch gameMatch) {
        try {
            String query = "UPDATE gamematch SET player1ID = ?, player2ID = ?, winnerID = ?, productID = ? WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, gameMatch.getPlayer1ID());
            stm.setInt(2, gameMatch.getPlayer2ID());
            stm.setInt(3, gameMatch.getWinnerID());
            stm.setInt(4, gameMatch.getProductID());
            stm.setInt(5, gameMatch.getId());
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Xoá Game đấu
    public void deleteGameMatch(int id) {
        try {
            String query = "DELETE FROM gamematch WHERE id = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, id);
            stm.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy danh sách tất cả Game đấu
    public ArrayList<GameMatch> getAllGameMatches() {
        ArrayList<GameMatch> gameMatches = new ArrayList<>();
        try {
            String query = "SELECT * FROM gamematch";
            PreparedStatement stm = con.prepareStatement(query);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                gameMatches.add(new GameMatch(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getInt(5)
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return gameMatches;
    }

    // Lấy danh sách Game đấu dựa trên playerID
    public ArrayList<GameMatch> getGameMatchesByPlayerID(int playerID) {
        ArrayList<GameMatch> gameMatches = new ArrayList<>();
        try {
            String query = "SELECT * FROM gamematch WHERE player1ID = ? OR player2ID = ?";
            PreparedStatement stm = con.prepareStatement(query);
            stm.setInt(1, playerID);
            stm.setInt(2, playerID);
            ResultSet rs = stm.executeQuery();
            while (rs.next()) {
                gameMatches.add(new GameMatch(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getInt(5)
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return gameMatches;
    }
}

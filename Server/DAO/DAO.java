package Server.DAO;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * @author Admin
 */
public class DAO {
    protected Connection con;

    public DAO() {
        final String DATABASE_NAME = "btl_ltm"; // TODO FILL YOUR DATABASE NAME
        final String jdbcURL = "jdbc:mysql://localhost:3306/" + DATABASE_NAME + "?useSSL=false&allowPublicKeyRetrieval=true";
        final String JDBC_USER = "root";  // TODO FILL YOUR DATABASE USER
        final String JDBC_PASSWORD = "Manh1234"; // TODO FILL YOUR DATABASE PASSWORD
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(jdbcURL, JDBC_USER, JDBC_PASSWORD);
            System.out.println("Connected to database");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Connection to database failed");
        }
    }
}
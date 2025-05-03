package server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class for obtaining JDBC connections to the game24db database.
 */
public class DBUtil {
    private static final String URL = "jdbc:mysql://localhost:3306/game24db";
    private static final String USER = "root";
    private static final String PASS = "12345678";

    /**
     * Returns a new JDBC Connection to the configured database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
} 
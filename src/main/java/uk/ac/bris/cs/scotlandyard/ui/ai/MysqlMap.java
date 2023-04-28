package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.sql.*;

public class MysqlMap {

    /* table structure:
    * +----------+-----------+
    * | distance | searchKey |
    * +----------+-----------+
    *
    * where 'searchKey' is the primary key for this table：
    * +-----------+------+------+-----+---------+----------------+
    * | Field     | Type | Null | Key | Default | Extra          |
    * +-----------+------+------+-----+---------+----------------+
    * | distance  | int  | NO   |     | NULL    |                |
    * | searchKey | int  | NO   | PRI | NULL    | auto_increment |
    * +-----------+------+------+-----+---------+----------------+
    *
    * */

    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://10.211.55.4:3306/ScotlandYard_map?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
//    private static final String DB_URL ="jdbc:mysql://10.211.55.4:3306/ScotlandYard_map";
    private static final String USER = "ScotlandYard_Eric";
    private static final String PASS = "";

    public static void createTable(ImmutableGameState gameState) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            // register JDBC driver
            Class.forName(JDBC_DRIVER);

            // check if table has already been created
            String checkSql = "SELECT distance FROM all_distances";
            try (ResultSet checkRs = stmt.executeQuery(checkSql)) {
                if (checkRs.next()) {
                    System.out.println("Table for shortest distances between all pairs of vertices in the graph has already been created");
                    return;
                }
            }

            // insert distances between all pairs of vertices into the table
            String insertSql = "INSERT INTO all_distances (distance) VALUES (?)";
            try {
                for (int start : gameState.getSetup().graph.nodes()) {
                    Dijkstra dijkstra = new Dijkstra(gameState, start);
                    for (int end : gameState.getSetup().graph.nodes()) {
                        PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                        insertStmt.setInt(1, dijkstra.distTo[end]);

                        int rowsInserted = insertStmt.executeUpdate();
                        if (rowsInserted != 1) {
                            throw new SQLException("Failed to insert distance between nodes " + start + " and " + end);
                        }
                        insertStmt.close();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException se) {
            // handle JDBC errors
            se.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
            // handle Class.forName errors
            cnfe.printStackTrace();
        }
    }
}

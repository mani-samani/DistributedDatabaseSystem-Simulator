package results;

import java.sql.*;

/**
 * This class is used to insert database results into the database
 */
public class DBConnection {

    public static void insertResults(ExperimentResults results) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost/ddb_results?" + "user=root&password=thesis");

            PreparedStatement statement = conn.prepareStatement("INSERT INTO results(experimentNumber,pcot,deadlockDetectionProtocol,deadlockResolutionProtocol," +
                    "topology,arrivalRate,priorityProtocol,numPages,detectionInterval,maxActiveTrans,overHeadIncurred,messageOverHeadIncurred,updateRate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
            statement.setLong  (1, results.getExpNum());
            statement.setDouble(2, results.getPCOT());
            statement.setString(3, results.getDDP());
            statement.setString(4, results.getDRP());
            statement.setString(5, results.getTopology());
            statement.setInt   (6, results.getArrivalRate());
            statement.setString(7, results.getPP());
            statement.setInt   (8, results.getNumPages());
            statement.setInt   (9, results.getDetectInterval());
            statement.setInt   (10, results.getMaxActiveTrans());
            statement.setInt   (11, results.getOverheadIncurred());
            statement.setInt   (12, results.getMessageOverheadIncurred());
            statement.setDouble(13, results.getUpdateRate());

            statement.execute();


        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
}
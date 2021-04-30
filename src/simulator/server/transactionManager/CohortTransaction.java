package simulator.server.transactionManager;

public class CohortTransaction extends Transaction {

    private final int masterServerID;

    public CohortTransaction(int ID, int serverID, int deadLine, int masterServerID) {
        super(ID, serverID, deadLine);
        this.masterServerID = masterServerID;
    }

    @Override
    public String toString() {
        return "Cohort " + super.toString();
    }

    public int getMasterServerID() {
        return masterServerID;
    }
}

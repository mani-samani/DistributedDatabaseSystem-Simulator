package simulator.server.lockManager;

import simulator.protocols.deadlockDetection.WFG.WFGNode;

public class Lock implements WFGNode {

    private static int nextID;
    private final int ID = nextID++;
    private final int pageNum;
    private final int transID;
    private final boolean exclusive;
    private final int serverID;
    private final int deadline;

    public Lock(int pageNum, int transID, boolean exclusive, int deadline, int serverID) {
        this.pageNum = pageNum;
        this.transID = transID;
        this.exclusive = exclusive;
        this.deadline = deadline;
        this.serverID = serverID;
    }

    public int getTransID() {
        return transID;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getServerID() {
        return serverID;
    }

    public int getDeadline() {
        return deadline;
    }

    @Override
    public int getID() {
        return ID;
    }

    @Override
    public String toString() {
        return "Lock{" +
                "pageNum=" + pageNum +
                ", transID=" + transID +
                ", exclusive=" + exclusive +
                '}';
    }
}

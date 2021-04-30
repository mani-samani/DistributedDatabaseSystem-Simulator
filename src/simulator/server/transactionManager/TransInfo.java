package simulator.server.transactionManager;

import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.protocols.priority.PriorityProtocol;

import java.util.List;

public class TransInfo implements WFGNode {
    public final int serverID;
    public final int transID;
    public final int deadline;
    public final int workload;
    public final int executionTime;

    public final int slackTime;
    public final List<Integer> readPages, writePages;

    public TransInfo(int serverID, int transID, int deadline, int workload, int executionTime, int slackTime, List<Integer> readPages, List<Integer> writePages) {
        this.serverID = serverID;
        this.transID = transID;
        this.deadline = deadline;
        this.workload = workload;
        this.executionTime = executionTime;
        this.slackTime = slackTime;
        this.readPages = readPages;
        this.writePages = writePages;
    }

    public int getPriority(List<TransInfo> transactionsInDeadlock, PriorityProtocol pp) {
        return pp.getTransPriority(transactionsInDeadlock, this.getID());
    }

    @Override
    public int getID() {
        return transID;
    }

    public int getDeadline() {
        return deadline;
    }

    public int getSlackTime() {
        return slackTime;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TransInfo))
            return false;
        TransInfo ti = (TransInfo) o;

        return ti.getID() == transID;
    }

    @Override
    public String toString() {
        return "TransInfo{Transaction ID= " + transID + "}";
    }
}

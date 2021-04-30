package simulator.protocols.deadlockDetection.mobileAgentEnabledApproach;

import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.Task;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.server.Server;
import simulator.server.transactionManager.TransInfo;
import ui.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StaticAgent {
    private final MAEDD maedd;
    private final Log log;
    private final Server server;
    //private List<List<WFGNode>> deadlocks = new LinkedList<>();

    /**
     * List of servers involved in the WFG
     */
    private Set<Integer> S_List = new HashSet<>();

    public StaticAgent(MAEDD maedd, Server server) {
        this.maedd = maedd;
        log = maedd.log;
        this.server = server;
    }

    protected void searchGraph(Graph<WFGNode> build) {
        if (Log.isLoggingEnabled())
            log.log("Static Agent - Searching graph");

        maedd.calculateAndIncurOverhead(build);

        List<Task<WFGNode>> allTransactions = new ArrayList<>(build.getTasks());
        List<TransInfo> transAtThisServer = new ArrayList<>();
        List<Integer> transAtRemoteServer = new ArrayList<>();

        S_List.clear();
        //collect all transactions this agent cares about
        for (int i = 0; i < allTransactions.size(); i++) {
            int transactionServer = ((TransInfo) allTransactions.get(i).getId()).serverID;
            if( transactionServer == server.getID())
                transAtThisServer.add((TransInfo) allTransactions.get(i).getId());

            transAtRemoteServer.add(transactionServer);

            S_List.add(transactionServer);
        }

        if(!transAtRemoteServer.isEmpty())
            S_List.add(server.getID());

        if (transAtThisServer.isEmpty()) {
            if (Log.isLoggingEnabled())
                log.log("No transactions for this agent");

            return;
        }

        if (Log.isLoggingEnabled())
            log.log("This agent cares about - " + transAtThisServer);
    }

    public Set getS_List() {
        return S_List;
    }
}

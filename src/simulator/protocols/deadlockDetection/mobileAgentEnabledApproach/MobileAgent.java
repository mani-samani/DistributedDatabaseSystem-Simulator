package simulator.protocols.deadlockDetection.mobileAgentEnabledApproach;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.GraphBuilder;
import simulator.protocols.deadlockDetection.WFG.Task;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.network.NetworkInterface;
import simulator.server.transactionManager.TransInfo;
import ui.Log;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static simulator.protocols.deadlockDetection.WFG_DDP.convertToList;

public class MobileAgent {

    private final MAEDD maedd;
    private final Log log;
    private final Server server;
    private final int serverID;
    private final SimParams simParams;
    private GraphBuilder<WFGNode> wfgBuilder;
    private final List<Integer> receivedFromServers;

    private List<List<WFGNode>> deadlocks = new LinkedList<>();
    private List<Graph<WFGNode>> receivedWFGs;
    private Consumer<Event> eventQueue;

    private final String BACKWARD = "BACKWARD";
    private final String FORWARD = "FORWARD";

    /**
     * List of servers for this agent to visit.
     */
    private final Set<Integer> S_List;

    /**
     * List of processes involved
     */
    private List<Integer> P_List = new LinkedList<>();

    public MobileAgent(MAEDD maedd, Server server) {
        this.maedd = maedd;
        log = maedd.log;
        this.server = server;
        this.serverID = server.getID();
        simParams = server.getSimParams();
        receivedWFGs = maedd.getReceivedWFGs();
        wfgBuilder = maedd.getWfgBuilder();
        receivedFromServers = maedd.getReceivedFromServers();
        eventQueue = simParams.eventQueue;

        S_List = new HashSet<>();
        P_List = new LinkedList<>();
    }

    /**
     * This is called when a WFG is received
     */
    public void updateWFGraph(Graph<WFGNode> graph, int server) {
        if (Log.isLoggingEnabled())
            log.log("Mobile Agent Updating graph with waits from server " + server);

        if (receivedWFGs.contains(graph))
            throw new WTFException(serverID + ": Have already received this WFG! " + graph);

        receivedWFGs.add(graph);

        graph.getTasks().forEach(wfgNodeTask -> {
            WFGNode trans = wfgNodeTask.getId();
            wfgBuilder.addTask(trans);
            wfgNodeTask.getWaitsForTasks().forEach(waitingFor -> wfgBuilder.addTaskWaitsFor(trans, waitingFor.getId()));
        });

        receivedFromServers.add(server);

        //if (receivedFromServers.containsAll(simParams.allServersList)) {
        if (receivedFromServers.containsAll(getS_List())) {
            BiConsumer<Graph<WFGNode>, Integer> wfGraphConsumer = maedd.getWfGraphConsumer();
            if (wfGraphConsumer != null) {
                //System.out.println("Graph has " + wfgBuilder.size() + " nodes at time " + simParams.getTime());
                Graph<WFGNode> copy = wfgBuilder.build();
                copy.setGlobal(true);
                wfGraphConsumer.accept(copy, simParams.getTime());
            }

            searchGraph(wfgBuilder.build());
            //eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, maedd::startDetectionIteration));

            //clear wfgBuilder so we can start fresh next round
            wfgBuilder = new GraphBuilder<>();

            receivedFromServers.clear();
            S_List.clear();
        }
    }

    protected void searchGraph(Graph<WFGNode> build) {
        if (Log.isLoggingEnabled())
            log.log("Mobile Agent - Searching graph");

        maedd.calculateAndIncurOverhead(build);
        deadlocks.clear();

        List<Integer> globalDetectors = maedd.getMobileAgentServers();

// MANI: CHANGE!!!
        List<Integer> involvedServers = new ArrayList<>(getS_List());
        Collections.sort(involvedServers);
        List<Integer> forward = new ArrayList<>(involvedServers.subList(0, involvedServers.size()/2));
        involvedServers.removeAll(forward);
        List<Integer> backward = new ArrayList<>(involvedServers);

//        System.out.println("************");
        log.log("Forward: " + forward);
        log.log("Backward: " + backward);

        //TODO share servers in s_list and hence the transactions between two agents
        log.log("serverID: "+serverID);

        //int thisNodesIndex = globalDetectors.indexOf(server.getID());

        List<Task<WFGNode>> allTransactions = new ArrayList<>(build.getTasks());
        List<TransInfo> transThisAgentCaresAbout = new ArrayList<>();

        //collect all transactions this agent cares about
        for (int i = 0; i < allTransactions.size(); i++) {
            //if (i % globalDetectors.size() == thisNodesIndex)
            TransInfo transaction = (TransInfo) allTransactions.get(i).getId();
            log.log("Transaction Server: "+ transaction.serverID);

            if(serverID == globalDetectors.get(0)){
                if (forward.contains(transaction.serverID))
                    transThisAgentCaresAbout.add(transaction);
            }
            else if(serverID == globalDetectors.get(1)) {
                if (backward.contains(transaction.serverID))
                    transThisAgentCaresAbout.add(transaction);
            }
        }

        log.log("Mobile Agent on server " + serverID + " cares about - " + transThisAgentCaresAbout.toString());

        if (transThisAgentCaresAbout.isEmpty()) {
            if (Log.isLoggingEnabled())
                log.log("Mobile Agent Mobile Agent - No transactions for this agent");

            return;
        }

        if (Log.isLoggingEnabled())
            log.log("Mobile Agent - This agent cares about - " + transThisAgentCaresAbout);

        //Get transInfo and start searching through its children
        for (TransInfo t : transThisAgentCaresAbout) {
            //Convert the TransInfo list to list of WFGNodes
            List<Task<WFGNode>> edgesFrom = build.getEdgesFrom(t);

            followCycle(t, edgesFrom, new LinkedList<>());
        }

        //Convert the list of lists of WFGNodes to a list of lists of Deadlocks
        List<List<TransInfo>> deadlocksTransInfo = new ArrayList<>();
        List<Deadlock> deadlocksList = new ArrayList<>();

        deadlocks.forEach(deadlock -> {
            List<TransInfo> deadlockTransInfo = new ArrayList<>();
            deadlock.forEach(wfgNode -> deadlockTransInfo.add((TransInfo) wfgNode));

            //If the deadlock was detected twice, ignore it the second time.
            if (!deadlocksTransInfo.contains(deadlockTransInfo)) {
                deadlocksTransInfo.add(deadlockTransInfo);

                deadlocksList.add(new Deadlock(deadlockTransInfo, server.getID(), simParams.getTime(), true));
            }
        });

        if (deadlocksList.isEmpty()) {
            if (Log.isLoggingEnabled())
                log.log("Mobile Agent - Found no deadlocks");

            return;
        }

        deadlocksList.forEach(maedd.getDeadlockListener());
        if (Log.isLoggingEnabled())
            log.log("Mobile Agent - Found deadlocks - " + deadlocksTransInfo);

        simParams.stats.addDeadlockFound();

        //Resolve the deadlocks
        maedd.getResolver().accept(deadlocksList);
    }

    private void followCycle(WFGNode lookingFor, List<Task<WFGNode>> edges, List<WFGNode> path) {
        if (edges.isEmpty())
            return;

        for (Task<WFGNode> t : edges) {
            WFGNode edge = t.getId();

            if (edge == lookingFor) {
                path.add(edge);
                LinkedList<WFGNode> deadlockPath = new LinkedList<>(path);
                deadlockPath.addFirst(deadlockPath.removeLast());

                deadlocks.add(deadlockPath);
                if (Log.isLoggingEnabled())
                    log.log("Mobile Agent - Found deadlock - " + deadlockPath);

                path.remove(edge);
            } else if ((edge.getID() > lookingFor.getID() && !path.contains(edge))) {
                path.add(edge);
                followCycle(lookingFor, convertToList(t.getWaitsForTasks()), path);
                path.remove(edge);
            }
        }
    }

    public void update_S_List(HashSet Local_S_List, int fromServerId) {
        if (Log.isLoggingEnabled())
            log.log("Mobile Agent Server " + fromServerId + " sent its S_List of " + Local_S_List);

        S_List.addAll(Local_S_List);

        receivedFromServers.add(fromServerId);

        log.log("\t Creating receivedFromServers of mobile agent in server " + serverID + " | adding " + fromServerId + " to " + receivedFromServers);

        if (Log.isLoggingEnabled())
            log.log("Creating receivedFromServers. Adding " + fromServerId + " | it is now: " + receivedFromServers);

        if (receivedFromServers.containsAll(simParams.allServersList)) {
            NetworkInterface NIC = server.getNIC();

            for (Integer s : S_List)
                NIC.sendMessage(new Message(s, ServerProcess.DDP, serverID + "", "Send LocalWFG to Mobiles", simParams.getTime()));


            //eventQueue.accept(new Event(simParams.getTime() + 1, serverID, maedd::sendLocalWFGToGlobals, true));

            receivedFromServers.clear();

            if (Log.isLoggingEnabled())
                log.log("Cleared received from servers list, it is now: " + receivedFromServers);
        }
    }

    public Set getS_List() {
        return S_List;
    }
}
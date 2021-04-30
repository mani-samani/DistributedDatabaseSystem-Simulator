package simulator.protocols.deadlockDetection.AgentDDP;

import java.util.*;
import java.util.function.Consumer;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.GraphBuilder;
import simulator.protocols.deadlockDetection.WFG.Task;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.protocols.deadlockDetection.WFG_DDP;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.network.NetworkInterface;
import ui.Log;

/**
 * Created by Mani,
 */
public class AgentDeadlockDetectionProtocol extends WFG_DDP {

    protected final Log log;
    protected List<Integer> allServers = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);

    private List<List<WFGNode>> deadlocks = new LinkedList<>();
    private final List<Integer> receivedFromServers = new ArrayList<>();

    private final LocalAgent localAgent;
    private GlobalAgent globalAgent;

    public AgentDeadlockDetectionProtocol(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        super(server, simParams, resolver, overheadIncurer, deadlockListener);
        simParams.usesWFG = true;
        simParams.agentBased = true;
        log = new Log(ServerProcess.DDP, server.getID(), simParams.timeProvider, simParams.log);

        localAgent = new LocalAgent(this, server);
        globalAgent = new GlobalAgent(this, server);
    }

    @Override
    public void start() {
        eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, this::startDetectionIteration));
    }

//    public void received(int n) {
//        receivedFromServers.add(n);
//        if (receivedFromServers.containsAll(allServers)) {//
//            if(wfg.getNodesInvolved().contains(serverID))
//                searchGraph();
//
//            receivedFromServers.clear();
//        }
//    }

    protected void searchGraph(Graph<WFGNode> build) {
        localAgent.searchGraph(build);
    }

    /**
     * This is called when a WFG is received
     */
    public void updateWFGraph(Graph<WFGNode> graph, int server) {
        if (Log.isLoggingEnabled())
            log.log("Updating graph (created at " + graph.getCreationTime() + ") with waits from server " + server);

        globalAgent.updateWFGraph(graph, server);
        wfgBuilder = new GraphBuilder<>();
    }

    @Override
    public void sendLocalWFGToGlobals() {
        //Create the local WFG
        Graph<WFGNode> localWFG = createLocalGraphOfWaits();

        //clear the wfgBuilder now that we have the local WFG
        wfgBuilder = new GraphBuilder<>();

        NetworkInterface NIC = server.getNIC();

        //Calculate the amount of overhead to incur
        int size = localWFG.getNumberOfWaits()/100;
        if (size == 0)
            size = 1;

        //Send our graph to the detector nodes
        for (int i = 0; i < simParams.globalDetectors; i++) {
            if (i != serverID) {
                Message message = new Message(i, ServerProcess.DDP, serverID + "", localWFG, simParams.getTime());
                message.setSize(size);
                message.setReoccuring(true);
                NIC.sendMessage(message);
                //simParams.messageOverhead += size;
            }
        }

        boolean isAGlobalDetector = serverID < simParams.globalDetectors;
        if (!isAGlobalDetector) {
            //If this isn't a global detector it posts an event to check for deadlocks in the future and clears its WFGBuilder
            eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval() + 100, serverID, this::startDetectionIteration, true));
            return;
        }

        updateWFGraph(localWFG, serverID);
    }

    private static void findDeadlocks(List<? extends WFGNode> trans, List<AgentDeadlockDetectionProtocol> addps, Graph<WFGNode> WFG) {
//        for (int j = 0; j < addps.size(); j++) {
//            AgentDeadlockDetectionProtocol addp = addps.get(j);
//
//            //collect all transactions this agent cares about
//            List<WFGNode> WFGNodesThisNodeCaresAbout = new ArrayList<>();
//
//            for (int i = 0; i < trans.size(); i++) {
//                if( i % addps.size() == j )
//                    WFGNodesThisNodeCaresAbout.add(trans.get(i));
//            }
//
//            for(WFGNode t : WFGNodesThisNodeCaresAbout ) {
//                addp.followCycle(WFG, t, WFG.getEdgesFrom(t), new ArrayList<>());
//            }
//        }
    }


    /* For debugging purposes
    public static void main(String[] args) {
        System.out.println("Testing Example Case");
        boolean passed = testExampleCase();
        System.out.println("\nTesting Second Case");
        passed &= testSecondCase();
        System.out.println("\nTesting Third Case");
        passed &= testThirdCase();
        System.out.println("\nTests Passed? " + passed);
    }
    */

    /**
     * Agent 0 should detect
     * t1 -> t2 -> t1
     * Agent 2 should detect
     * t3 -> t4 -> t5 -> t3
     */
//    private static boolean testExampleCase() {
//        WFGraph<WFGNode> WFG = new WFGraph<>();
//
//        WFG_Node t1 = new WFG_Node(1);
//        WFG_Node t2 = new WFG_Node(2);
//        WFG_Node t3 = new WFG_Node(3);
//        WFG_Node t4 = new WFG_Node(4);
//        WFG_Node t5 = new WFG_Node(5);
//        WFG_Node t6 = new WFG_Node(6);
//        WFG_Node t7 = new WFG_Node(7);
//        WFG_Node t8 = new WFG_Node(8);
//
//        List<WFG_Node> trans = new ArrayList<>(Arrays.asList(t1,t2,t3,t4,t5,t6,t7,t8));
//        trans.forEach(WFG::addVertex);
//
//        WFG.addEdge(t1,t2);
//        WFG.addEdge(t2,t1);
//        WFG.addEdge(t3,t1);
//        WFG.addEdge(t3,t4);
//        WFG.addEdge(t3,t7);
//        WFG.addEdge(t4,t5);
//        WFG.addEdge(t4,t8);
//        WFG.addEdge(t5,t3);
//        WFG.addEdge(t6,t7);
//
//
//        AgentDeadlockDetectionProtocol addp1 = new AgentDeadlockDetectionProtocol(null,null,l->{},overhead->{}, deadlock -> {});
//        AgentDeadlockDetectionProtocol addp2 = new AgentDeadlockDetectionProtocol(null,null,l->{},overhead->{}, deadlock -> {});
//        AgentDeadlockDetectionProtocol addp3 = new AgentDeadlockDetectionProtocol(null,null,l->{},overhead->{}, deadlock -> {});
//        AgentDeadlockDetectionProtocol addp4  = new AgentDeadlockDetectionProtocol(null,null,l->{},overhead->{}, deadlock -> {});
//        List<AgentDeadlockDetectionProtocol> addps = new ArrayList<>(Arrays.asList(addp1,addp2,addp3,addp4));
//
//
//        findDeadlocks(trans,addps,WFG);
//
//        printResults(addps);

//        boolean passed = true;

//
//
//        String deadlock1 = printDeadlocks(addps.get(0).deadlocks.get(0));
//        if( !deadlock1.equals("t1 -> t2 -> t1"))
//            passed = false;
//
//        String deadlock2 = printDeadlocks(addps.get(2).deadlocks.get(0));
//        if( !deadlock2.equals("t3 -> t4 -> t5 -> t3"))
//            passed = false;


//        return passed;
//    }

    /**
     * Deadlock should be detected by the first agent
     * t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1
     */
//    public static boolean testSecondCase() {
//        WFGraph<WFGNode> WFG = new WFGraph<>();
//
//
//        List<WFGNode> trans = getWFGNodes(9);
//        trans.forEach(WFG::addVertex);
//
//        // t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1
//
//        WFG.addEdge(trans.get(1),trans.get(2));
//        WFG.addEdge(trans.get(2),trans.get(5));
//        WFG.addEdge(trans.get(5),trans.get(3));
//        WFG.addEdge(trans.get(3),trans.get(4));
//        WFG.addEdge(trans.get(4),trans.get(7));
//        WFG.addEdge(trans.get(7),trans.get(1));
//
//
//        List<AgentDeadlockDetectionProtocol> addps = getADDPS(4);
//
//        findDeadlocks(trans,addps,WFG);
//
//        printResults(addps);
//
//

//        boolean passed = true;

//
//        String deadlock1 = printDeadlocks(addps.get(1).deadlocks.get(0));
//        if( !deadlock1.equals("t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1"))
//            passed = false;
//

//        return passed;
//    }

    /**
     * This Deadlock should be detected by agent 1
     * t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1
     * t1 -> t2 -> t5 -> t3 -> t8 -> t4 -> t7 -> t1
     * <p>
     * This Deadlock should be detected by agent 3
     * t3 -> t4 -> t3
     * t3 -> t8 -> t4 -> t3
     */
//    public static boolean testThirdCase() {
//        Graph<WFGNode> WFG = new Graph<>();
//
//        List<WFGNode> trans = getWFGNodes(9);
//        trans.forEach(WFG::addVertex);
//
//        // t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1
//
//        WFG.addEdge(trans.get(1),trans.get(2));
//        WFG.addEdge(trans.get(2),trans.get(5));
//        WFG.addEdge(trans.get(5),trans.get(3));
//        WFG.addEdge(trans.get(3),trans.get(4));
//        WFG.addEdge(trans.get(4),trans.get(7));
//        WFG.addEdge(trans.get(7),trans.get(1));
//
//        // t3 -> t8 -> t4 -> t3
//
//        WFG.addEdge(trans.get(3),trans.get(8));
//        WFG.addEdge(trans.get(8),trans.get(4));
//        WFG.addEdge(trans.get(4),trans.get(3));
//
//        List<AgentDeadlockDetectionProtocol> addps = getADDPS(4);
//
//        findDeadlocks(trans,addps,WFG);
//
//        printResults(addps);
//

//        boolean passed = true;
//
//        String deadlock1 = printDeadlocks(addps.get(1).deadlocks.get(0));
//        if( !deadlock1.equals("t1 -> t2 -> t5 -> t3 -> t4 -> t7 -> t1"))
//            passed = false;
//
//        String deadlock2 = printDeadlocks(addps.get(1).deadlocks.get(1));
//        if( !deadlock2.equals("t1 -> t2 -> t5 -> t3 -> t8 -> t4 -> t7 -> t1"))
//            passed = false;
//
//        deadlock1 = printDeadlocks(addps.get(3).deadlocks.get(0));
//        if( !deadlock1.equals("t3 -> t4 -> t3"))
//            passed = false;
//
//        deadlock2 = printDeadlocks(addps.get(3).deadlocks.get(1));
//        if( !deadlock2.equals("t3 -> t8 -> t4 -> t3"))
//            passed = false;


//        return passed;
//    }
    private static void printResults(List<AgentDeadlockDetectionProtocol> addps) {
        for (int j = 0; j < addps.size(); j++) {
            AgentDeadlockDetectionProtocol addp = addps.get(j);

            final int J = j;
            addp.deadlocks.forEach(deadlock -> {
                System.out.print("addp" + J + ".deadlocks = ");
                System.out.println("[" + printDeadlocks(deadlock) + "], ");
            });
        }
    }

    public static String printDeadlocks(List<WFGNode> deadlocks) {
        StringBuilder sb = new StringBuilder();

        for (WFGNode wfgNode : deadlocks) {
            sb.append('t').append(wfgNode.getID()).append(" -> ");
        }
        sb.append('t').append(deadlocks.get(0).getID());

        return sb.toString();
    }

    private static List<WFGNode> getWFGNodes(int numberOfNodes) {
        List<WFGNode> addps = new ArrayList<>();
        for (int i = 0; i < numberOfNodes; i++)
            addps.add(new WFG_Node(i));
        return addps;
    }

    private static List<AgentDeadlockDetectionProtocol> getADDPS(int numberOfAgents) {
        List<AgentDeadlockDetectionProtocol> addps = new ArrayList<>();
        for (int i = 0; i < numberOfAgents; i++)
            addps.add(new AgentDeadlockDetectionProtocol(null, null, l -> {
            }, overhead -> {
            }, deadlock -> {
            }));
        return addps;
    }

    public static class WFG_Node implements WFGNode {
        public WFG_Node(int id) {
            this.id = id;
        }

        int id;

        @Override
        public int getID() {
            return id;
        }

        @Override
        public String toString() {
            return "[id=" + id + "]";
        }

        public int compareTo(WFGNode o) {
            if (id < o.getID())
                return 1;
            else if (id > o.getID())
                return -1;
            return 0;
        }
    }
}
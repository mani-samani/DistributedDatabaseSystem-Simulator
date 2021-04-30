package simulator.protocols.deadlockDetection;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.GraphBuilder;
import simulator.protocols.deadlockDetection.WFG.Task;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.server.Server;
import simulator.server.lockManager.Lock;
import simulator.server.network.Message;
import simulator.server.network.NetworkInterface;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;
import ui.Log;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * t=0 Simulation starts
 * t=300 WFG get sent to all nodes
 * t=x all WFGs have been received, start searching, then periodically clear WFG and start over
 */
public class WFG_DDP extends DeadlockDetectionProtocol {
    private final Log log;
    protected Consumer<Deadlock> deadlockListener;

    protected GraphBuilder<WFGNode> wfgBuilder = new GraphBuilder<>();
    protected GraphBuilder<WFGNode> globalWfgBuilder = new GraphBuilder<>();

    private final List<Integer> receivedFromServers = new ArrayList<>();
    private BiConsumer<Graph<WFGNode>, Integer> wfGraphConsumer;

    public WFG_DDP(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        super(server, simParams, resolver, overheadIncurer, deadlockListener);

        log = new Log(ServerProcess.DDP, serverID, simParams.timeProvider, simParams.log);

        this.deadlockListener = deadlockListener;
    }

    @Override
    public void start() {
        super.start();

        simParams.eventQueue.accept(new Event(simParams.getTime() + 300, serverID, this::startDetectionIteration));
    }

    /**
     * Only receives one type of message, that is the wait for graph from another node.
     * The message's contents is just the serverID of the other server.
     * The message's object is the Wait for Graph
     */
    public void receiveMessage(Message msg) {
        if (Log.isLoggingEnabled())
            log.log("Received message - " + msg.toString());

        int remoteServerID = Integer.parseInt(msg.getContents());

        updateWFGraph((Graph<WFGNode>) msg.getObject(), remoteServerID);
    }

    /**
     * Sends WFG to all other nodes
     */
    public void startDetectionIteration() {
        if (Log.isLoggingEnabled())
            log.log("Starting Detection Iteration");

        //Create the local WFG
        Graph<WFGNode> localWFG = createLocalGraphOfWaits();

        //Calculate the amount of overhead to incur
        int size = localWFG.getNumberOfWaits();

        if (Log.isLoggingEnabled())
            log.log("Local WFG has " + size + " nodes.");

        //Search the local graph
        searchGraph(localWFG);

        //post an event to send the local WFG to the global detectors. We wait for this so the local deadlocks can resolve before going global
        eventQueue.accept(new Event(simParams.getTime() + 100, serverID, this::sendLocalWFGToGlobals, true));
    }

    public void sendLocalWFGToGlobals() {
//        System.out.println("sendLocalWFGToGlobals() simParams.globalDetectors=" + simParams.globalDetectors);

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

        updateWFGraph(localWFG, serverID);
    }

    /**
     * Creates the WFG for this server
     *
     * @return a new WFG instance
     */
    protected Graph<WFGNode> createLocalGraphOfWaits() {
        if (Log.isLoggingEnabled())
            log.log("Creating local graph");

        //For integrity checking
        Map<Integer, Transaction> allTrans = simParams.getAllTransactionsMap();
        Map<Integer, Transaction> activeTrans = simParams.getActiveTransactionsMap();

        Map<Integer, List<Lock>> heldLocksMap = server.getLM().getHeldLocks();
        Map<Integer, List<Lock>> waitingLocksMap = server.getLM().getWaitingLocks();

        for (Integer pageNum : waitingLocksMap.keySet()) {
            List<Lock> waitingLocks = waitingLocksMap.get(pageNum);

            for (Lock waitingLock : waitingLocks) {
                //Assert check to make sure all the locks belong to active transactions
                if (!activeTrans.containsKey(waitingLock.getTransID())) {
                    Transaction t = allTrans.get(waitingLock.getTransID());
                    //Sometimes a transaction that has just finished, so don't throw an error if it just finished
                    if (t.isCompleted() && t.getCompletedTime() < simParams.getTime() - 1000)
                        throw new WTFException(serverID + ": This waiting lock " + waitingLock + " doesn't belong to an active transaction!");
                    else {
                        //System.out.println(simParams.getTime() + " - Adding lock of " + t + " that has recently completed at " + t.getCompletedTime());
                    }
                }

                List<Lock> heldLocks = heldLocksMap.get(waitingLock.getPageNum());

                heldLocks.forEach(heldLock -> {
                    //Assert check to make sure all the locks belong to active transactions
                    if (!activeTrans.containsKey(heldLock.getTransID())) {

                        Transaction t = allTrans.get(heldLock.getTransID());
                        //Sometimes a transaction that has just finished, so don't throw an error if it just finished
                        if (t.isCompleted() && t.getCompletedTime() < simParams.getTime() - 1000)
                            throw new WTFException(serverID + ": This held lock " + heldLock + " doesn't belong to an active transaction!");
                        else {
                            //System.out.println(simParams.getTime() + " - Adding lock of " + t + " that has recently completed at " + t.getCompletedTime());
                        }
                    }

                    log.log(waitingLock.getTransID(), "Transaction " + waitingLock.getTransID() + " is waiting on " + heldLock.getTransID() + " for page " + heldLock.getPageNum());

                    addWait(waitingLock, heldLock);
                });
            }
        }
        Graph<WFGNode> graph = wfgBuilder.build();
        graph.setCreationTime(simParams.getTime());
        graph.setGlobal(false);
        return graph;
    }

    public void addWait(Lock waitingLock, Lock heldLock) {
        addWait(getTransInfo(waitingLock.getTransID()), getTransInfo(heldLock.getTransID()));
    }

    public void removeAllWaitsOn(Lock heldLock) {
        wfgBuilder.removeTask(getTransInfo(heldLock.getTransID()));
    }

    private TransInfo getTransInfo(int transID) {
        return simParams.transInfos.get(transID);
    }

    public final void addWait(TransInfo rfrom, TransInfo rto) {
        wfgBuilder.addTask(rfrom);
        wfgBuilder.addTask(rto);

        wfgBuilder.addTaskWaitsFor(rfrom, rto);
    }

//    public void updateWFGraph(List<Lock> waiting, List<Lock> current, Integer server) {
////        waitingLocks.addAll(waiting);
////        currentLocks.addAll(current);
////        waitingLocks.removeAll(current);
////        currentLocks.removeAll(waiting);
//
//        received(server);
//    }

    private List<Graph<WFGNode>> receivedWFGs = new ArrayList<>();

    /**
     * This is called when a WFG is received
     */
    public void updateWFGraph(Graph<WFGNode> graph, int server) {
        if (Log.isLoggingEnabled())
            log.log("Updating graph with waits from server " + server);

        if (receivedWFGs.contains(graph))
            throw new WTFException(serverID + ": Have already received this WFG! " + graph);
        receivedWFGs.add(graph);

        graph.getTasks().forEach(wfgNodeTask -> {
            WFGNode trans = wfgNodeTask.getId();
            globalWfgBuilder.addTask(trans);
            wfgNodeTask.getWaitsForTasks().forEach(waitingFor -> globalWfgBuilder.addTaskWaitsFor(trans, waitingFor.getId()));
        });

        receivedFromServers.add(server);
        if (receivedFromServers.containsAll(simParams.allServersList)) {
            if (wfGraphConsumer != null) {
                //System.out.println("Graph has " + wfgBuilder.size() + " nodes at time " + simParams.getTime());

                Graph<WFGNode> copy = globalWfgBuilder.build();
                copy.setGlobal(true);
                wfGraphConsumer.accept(copy, simParams.getTime());
            }

            searchGraph(globalWfgBuilder.build());

            //After searching for deadlocks, post event to search again, and clear the state
            eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, this::startDetectionIteration));
            globalWfgBuilder = new GraphBuilder<>();
            receivedFromServers.clear();
        }
    }

    public void calculateAndIncurOverhead(Graph<WFGNode> WFG) {
        //calc overhead
        int overhead = 1;
        for (Task<WFGNode> rt : WFG.getTasks()) {
            //add one for each vertex
            overhead++;

            //add one for each edge
            overhead += rt.getWaitsForTasks().size();
        }

        if (Log.isLoggingEnabled())
            log.log("Incurring overhead- " + overhead);

        overheadIncurer.accept(overhead/100);
    }

    protected void searchGraph(Graph<WFGNode> wfg) {
        if (Log.isLoggingEnabled())
            log.log("Search Graph (Nothing in default implementation)");
    }

    public void setGraphListener(BiConsumer<Graph<WFGNode>, Integer> wfGraphConsumer) {
        this.wfGraphConsumer = wfGraphConsumer;
    }

    public static List<Task<WFGNode>> convertToList(Set<Task<WFGNode>> waits) {
        List<Task<WFGNode>> edges = new ArrayList<>();
        waits.forEach(edges::add);
        return edges;
    }

    public BiConsumer<Graph<WFGNode>, Integer> getWfGraphConsumer() {
        return wfGraphConsumer;
    }

    public GraphBuilder<WFGNode> getWfgBuilder() {
        return wfgBuilder;
    }

    public List<Integer> getReceivedFromServers() {
        return receivedFromServers;
    }

    public List<Graph<WFGNode>> getReceivedWFGs() {
        return receivedWFGs;
    }

    public void setWfgBuilder(GraphBuilder<WFGNode> wfgBuilder) {
        this.wfgBuilder = wfgBuilder;
    }

    public void increaseNumberOfGlobalDetectors() {
        simParams.globalDetectors++;
        if (simParams.globalDetectors > simParams.numberOfServers)
            simParams.globalDetectors = simParams.numberOfServers;

        if (Log.isLoggingEnabled())
            log.log("Increasing number of global detectors to " + simParams.globalDetectors);
    }

    public void decreaseNumberOfGlobalDetectors() {
        simParams.globalDetectors--;
        if (simParams.globalDetectors < 2)
            simParams.globalDetectors = 2;

        if (Log.isLoggingEnabled())
            log.log("Decreasing number of global detectors to " + simParams.globalDetectors);
    }

    public int getNumberGlobalDetectors() {
        return simParams.globalDetectors;
    }
}
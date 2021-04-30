package simulator.protocols.deadlockDetection.mobileAgentEnabledApproach;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.GraphBuilder;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.protocols.deadlockDetection.WFG_DDP;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.network.NetworkInterface;
import ui.Log;

import java.util.*;
import java.util.function.Consumer;

public class MAEDD extends WFG_DDP {

    protected final Log log;
    protected List<Integer> allServers = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);

    protected List<Integer> mobileAgentServers = Arrays.asList(0, 7);

    private final StaticAgent staticAgent;
    private final MobileAgent mobileAgent;

    private final Server server;
    private final int serverID;

    //TODO occupy network by mobile agent's size

    public MAEDD(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        super(server, simParams, resolver, overheadIncurer, deadlockListener);
        simParams.usesWFG = true;
        simParams.agentBased = true;
        log = new Log(ServerProcess.DDP, server.getID(), simParams.timeProvider, simParams.log);

        staticAgent = new StaticAgent(this, server);
        mobileAgent = new MobileAgent(this, server);
        this.server = server;
        this.serverID = server.getID();
    }

    @Override
    public void start() {
        eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, this::startDetectionIteration));
    }

    protected void searchGraph(Graph<WFGNode> build) {
        staticAgent.searchGraph(build);
    }

    /**
     * This is called when a WFG is received
     */
    public void updateWFGraph(Graph<WFGNode> graph, int server) {
        if (Log.isLoggingEnabled())
            log.log("Updating graph (created at " + graph.getCreationTime() + ") with waits from server " + server);

        mobileAgent.updateWFGraph(graph, server);
        wfgBuilder = new GraphBuilder<>();
    }

    /**
     * Sends WFG to all other nodes
     */
    @Override
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

        //post an event to send the S_List to the mobile agents.
        eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::sendSListToMobiles, true));
    }

    private void sendSListToMobiles() {
        // Create the S_List
        Set s_List = staticAgent.getS_List();

        NetworkInterface NIC = server.getNIC();

        //Calculate the amount of overhead to incur
        int size = s_List.size()/100;
        if (size == 0)
            size = 1;

        if (Log.isLoggingEnabled())
            log.log("sending S_list from static agent " + serverID + " | list: " + s_List);

        //Send our S_List to the mobile agents
        for (int i = 0; i < mobileAgentServers.size(); i++) {
            int globalDetector = mobileAgentServers.get(i);

            Message message = new Message(globalDetector, ServerProcess.DDP, serverID + "", s_List, simParams.getTime());
            message.setSize(size);
            message.setReoccuring(true);
            NIC.sendMessage(message);
        }

        if (Log.isLoggingEnabled())
            log.log("Posting event for the next iteration");

        //If this isn't a global detector it posts an event to check for deadlocks in the future and clears its WFGBuilder
        eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval() + 100, serverID, this::startDetectionIteration, true));
    }

    /**
     * Only receives one type of message, that is the wait for graph from another node.
     * The message's contents is just the serverID of the other server.
     * The message's object is the Wait for Graph
     */
    @Override
    public void receiveMessage(Message msg) {
        if (Log.isLoggingEnabled())
            log.log("Received message - " + msg.toString());

        int remoteServerID = Integer.parseInt(msg.getContents());

        if ("Send LocalWFG to Mobiles".equals(msg.getObject()))
            sendLocalWFGToGlobals();
        else if (msg.getObject() instanceof HashSet)
            mobileAgent.update_S_List((HashSet) msg.getObject(), remoteServerID);
        else
            updateWFGraph((Graph<WFGNode>) msg.getObject(), remoteServerID);
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

        if (Log.isLoggingEnabled())
            log.log("Sending local WFG = " + localWFG.toString() + " From Server " + serverID);

        //Send our graph to the detector nodes
        for (int i = 0; i < mobileAgentServers.size(); i++) {
            int globalDetector = mobileAgentServers.get(i);

            //if (globalDetector != serverID) {
            Message message = new Message(globalDetector, ServerProcess.DDP, serverID + "", localWFG, simParams.getTime());
            message.setSize(size);
            message.setReoccuring(true);
            NIC.sendMessage(message);
            //}
        }

        //if (!mobileAgentServers.contains(serverID)) {
//            log.log("Posting event for the next iteration");
//            //If this isn't a global detector it posts an event to check for deadlocks in the future and clears its WFGBuilder
//            eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval() + 100, serverID, this::startDetectionIteration, true));
        //return;
        //}

        //updateWFGraph(localWFG, serverID);
    }

    public List<Integer> getMobileAgentServers() {
        return mobileAgentServers;
    }

}
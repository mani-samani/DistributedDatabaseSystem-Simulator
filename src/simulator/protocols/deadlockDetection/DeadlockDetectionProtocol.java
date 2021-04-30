package simulator.protocols.deadlockDetection;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.AgentDDP.AgentDeadlockDetectionProtocol;
import simulator.protocols.deadlockDetection.ChandyMisraHaas.ChandyMisraHaasDDP;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.protocols.deadlockDetection.mobileAgentEnabledApproach.MAEDD;
import simulator.server.Server;
import simulator.server.network.Message;
import ui.Log;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DeadlockDetectionProtocol {
    /**
     * For logging purposes
     */
    private final Log log;

    /**
     * When a deadlock is detected, the deadlock is passed through this interface to the GUI
     */
    protected Consumer<Deadlock> deadlockListener;

    protected final int serverID;
    protected final Server server;
    protected final SimParams simParams;

    /**
     * Used to post events to the eventQueue
     */
    protected final Consumer<Event> eventQueue;

    /**
     * Interface to the DRP
     */
    protected final Consumer<List<Deadlock>> resolver;

    /**
     * this is used to make the server incur the overhead of searching for deadlocks
     */
    protected final Consumer<Integer> overheadIncurer;

    /**
     * @param server           Obviously the server this protocol is at
     * @param simParams        A parameter object used to pass parameters and provide other utilities to the various components
     * @param resolver         This will pass the detected deadlock to the deadlock resolution protocol
     * @param overheadIncurer  This is used to tell the simulation how much overhead to incur because, this is dependent on how hard it was to detect the deadlock
     * @param deadlockListener This is used to pass the deadlocks to the GUI
     */
    public DeadlockDetectionProtocol(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        this.server = server;
        this.serverID = server.getID();
        this.simParams = simParams;
        this.eventQueue = simParams.eventQueue;
        this.resolver = resolver;
        this.overheadIncurer = overheadIncurer;
        log = new Log(ServerProcess.DDP, serverID, simParams.timeProvider, simParams.log);

        this.deadlockListener = deadlockListener;
    }

    /**
     * this is called once when the simulation starts
     */
    public void start() {
        if (Log.isLoggingEnabled())
            log.log("Start deadlock detection protocol");
    }

    public void receiveMessage(Message msg) {
        if (Log.isLoggingEnabled())
            log.log("Received message- " + msg);
    }

    /**
     * When the deadlock detection protocol creates a wait for graph, this interface is used to pass it to the GUI so the user can inspect it.
     */
    public void setGraphListener(BiConsumer<Graph<WFGNode>, Integer> consumer) {

    }

    public Consumer<Deadlock> getDeadlockListener() {
        return deadlockListener;
    }

    public Consumer<List<Deadlock>> getResolver() {
        return resolver;
    }

    public Consumer<Integer> getOverheadIncurer() {
        return overheadIncurer;
    }

    /**
     * Just used to get an instance of the protocol from a string (from the parameter file specifically)
     *
     * @param ddp              The string that is found in the params.txt file
     * @param deadlockListener This is the interface to the GUI
     */
    public static DeadlockDetectionProtocol get(Server server, String ddp, Consumer<Deadlock> deadlockListener) {
        switch (ddp) {
            case "AgentDeadlockDetectionProtocol":
                return new AgentDeadlockDetectionProtocol(server, server.getSimParams(), server.getDRP()::resolveMultiple, server::incurOverhead, deadlockListener);
            case "TimeoutDeadlockDetection":
                return new TimeoutDeadlockDetection(server, server.getSimParams(), server.getDRP()::resolveMultiple, server::incurOverhead);
            case "ChandyMisraHaasDDP":
                return new ChandyMisraHaasDDP(server, server.getSimParams(), server.getDRP()::resolveMultiple, server::incurOverhead, deadlockListener);
            case "MAEDD":
                return new MAEDD(server, server.getSimParams(), server.getDRP()::resolveMultiple, server::incurOverhead, deadlockListener);
        }
        throw new WTFException("Deadlock Detection Protocol has not been registered! add it here in the WFG_DDP class!");
    }
}
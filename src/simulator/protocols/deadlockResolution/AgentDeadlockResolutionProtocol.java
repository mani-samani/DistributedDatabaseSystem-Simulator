package simulator.protocols.deadlockResolution;

import exceptions.WTFException;
import javafx.util.Pair;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.transactionManager.TransInfo;
import ui.Log;

import java.util.*;

/**
 * Select a transaction to kill based on agent decision making
 *
 * @author Mani
 */
public class AgentDeadlockResolutionProtocol implements DeadlockResolutionProtocol {

    private final Log log;
    private final Server server;
    private final Map<Pair<Integer, Integer>, Agent> agentList = new HashMap<>();
    private final Map<Integer, Object[]> delayedReceiveDropability = new HashMap<>();

    public int numberOfResolvedDeadlocks = 0;
    private final List<List<TransInfo>> beingResolvedDeadlocks = new ArrayList<>();

    public AgentDeadlockResolutionProtocol(Server server) {
        this.server = server;
        SimParams simParams = server.getSimParams();
        log = new Log(ServerProcess.DRP, server.getID(), simParams.timeProvider, simParams.log);
    }

    /**
     * RD: Receive dropability
     * R: Resolve deadlock
     *
     * @param message
     */
    @Override
    public void receiveMessage(Message message) {
        String[] msg = message.getContents().split(":");

        switch (msg[0]) {
            case "RD": {
                if (Log.isLoggingEnabled())
                    log.log(Integer.parseInt(msg[1]), "Receive message: " + message);

                receiveDropability(Integer.parseInt(msg[1]), Integer.parseInt(msg[2]), Integer.parseInt(msg[3]), Integer.parseInt(msg[4]));
                break;
            }
            case "R": {
                if (Log.isLoggingEnabled())
                    log.log("Resolve- " + message.getObject());

                resolve((Deadlock) message.getObject(), true);
                break;
            }
            default:
                throw new WTFException("Badly formatted message! : " + message.getContents());
        }
    }

    public List<TransInfo> resolve(Deadlock deadlock) {
        return resolve(deadlock, false);
    }

    public List<TransInfo> resolve(Deadlock deadlock, boolean fromMsg) {
        List<TransInfo> transactionsInDeadlock = deadlock.getTransactionsInvolved();
        if (beingResolvedDeadlocks.contains(transactionsInDeadlock)) {
            if (Log.isLoggingEnabled())
                log.log("Already resolving deadlock- " + transactionsInDeadlock);

            return null;
        }

        beingResolvedDeadlocks.add(transactionsInDeadlock);

        if (Log.isLoggingEnabled())
            log.log("Resolving deadlock involving- " + transactionsInDeadlock);

        //List of servers we have informed about the deadlock already
        List<Integer> sentToAlready = new ArrayList<>();

        for (TransInfo ti : transactionsInDeadlock) {
            int serverID = ti.serverID;

            if (server.getID() == serverID) {
                Agent a = new Agent(ti.transID, deadlock, transactionsInDeadlock, server, deadlock.getDeadlockID());
                a.resolve();

                //Store a reference to the agent for this transaction + deadlock
                agentList.put(new Pair<>(ti.transID, deadlock.getDeadlockID()), a);

                //See if we have received dropabilities before this agent was created on this node
                if (delayedReceiveDropability.containsKey(ti.transID)) {
                    Object[] args = delayedReceiveDropability.remove(ti.transID);
                    receiveDropability(ti.transID, (int) args[0], (int) args[1], (int) args[2]);
                }

            } else if (!fromMsg && !sentToAlready.contains(serverID)) {
                server.getNIC().sendMessage(new Message(serverID, ServerProcess.DRP, "R:", deadlock, ti.deadline));
                sentToAlready.add(serverID);
            }
        }

        numberOfResolvedDeadlocks++;

        return null;
    }

    public void receiveDropability(int transID, int agentID, int deadlockID, int dropability) {
        if (Log.isLoggingEnabled())
            log.log(transID, "Received dropability:" + dropability + " from agent: " + agentID + " for deadlock: " + deadlockID);

        Pair<Integer, Integer> transDeadLockPair = new Pair<>(transID, deadlockID);

        //If we have received the dropability before this server knows about the deadlock
        if (!agentList.containsKey(transDeadLockPair))
            delayedReceiveDropability.put(transID, new Object[]{agentID, deadlockID, dropability});
        else
            agentList.get(transDeadLockPair).receiveDropability(dropability, agentID);
    }

    public void resolveMultiple(List<Deadlock> l) {
        l.forEach(this::resolve);
    }

    public int getNumberOfDeadlocks() {
        return numberOfResolvedDeadlocks;
    }

    @Override
    public void resolveDeadlocks(Deadlock deadlock) {

    }
}
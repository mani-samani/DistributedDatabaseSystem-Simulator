package simulator.protocols.deadlockDetection.ChandyMisraHaas;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.DeadlockDetectionProtocol;
import simulator.server.Server;
import simulator.server.lockManager.Lock;
import simulator.server.network.Message;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;
import ui.Log;

import java.util.*;
import java.util.function.Consumer;

public class ChandyMisraHaasDDP extends DeadlockDetectionProtocol {
    private static final double percStart = .1;
    private final Log log;

    public ChandyMisraHaasDDP(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        super(server, simParams, resolver, overheadIncurer, deadlockListener);
        log = new Log(ServerProcess.DDP, serverID, simParams.timeProvider, simParams.log);
    }

    @Override
    /**
     * Gets called right when the simulation starts.
     * This method posts an event DETECTION_INTERVAL ticks into the future to start detecting deadlocks
     */
    public void start() {
        simParams.eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, this::detectDeadlock));
    }

    /**
     * This method is ran periodically.
     * <p>
     * It first builds a list of all the waiting locks.
     * Then it picks percStart% of them to start sending messages from.
     * Then for each waiting lock that is chosen:
     * It then sends messages to all the transactions that hold a lock on the page.
     */
    protected void detectDeadlock() {
        if (Log.isLoggingEnabled())
            log.log("Detecting Deadlock");

        Map<Integer, List<Lock>> heldLocks = server.getLM().getHeldLocks();
        Map<Integer, List<Lock>> waitingLocks = server.getLM().getWaitingLocks();

        List<Lock> allWaitingLocks = new LinkedList<>();
        waitingLocks.values().forEach(allWaitingLocks::addAll);

        //Pick percStart% of the waiting locks to start sending messages
        int numStart = (int) (allWaitingLocks.size() * percStart);
        if (allWaitingLocks.size() > 1 && numStart == 0)
            numStart = 1;

        if (Log.isLoggingEnabled())
            log.log("Detecting Deadlock starting from " + numStart + " locks.");

        for (int i = 0; i < numStart; i++) {
            Lock l = allWaitingLocks.remove((int) (allWaitingLocks.size() * simParams.rand.get()));

            List<Lock> heldLocksForThisPage = heldLocks.get(l.getPageNum());
            heldLocksForThisPage.forEach(heldLock -> {
                if (Log.isLoggingEnabled())
                    log.log(l.getTransID(), "On behalf of trans " + l.getTransID() + " sending probe to trans " + heldLock.getTransID());

                server.getNIC().sendMessage(
                        new Message(heldLock.getServerID(), ServerProcess.DDP, "20",
                                new ProbeMessage(l.getTransID(), l.getTransID(), heldLock.getTransID()), simParams.getTime()));
            });
        }

        simParams.eventQueue.accept(new Event(simParams.getTime() + simParams.getDeadlockDetectInterval(), serverID, this::detectDeadlock, true));
    }

    @Override
    /**
     * Receives only probe messages
     *
     */
    public void receiveMessage(Message msg) {
        if (Log.isLoggingEnabled())
            log.log("Receive message- " + msg);

        //Make sure we have a probe message (we always should)
        if (msg.getObject() instanceof ProbeMessage) {
            ProbeMessage probeMessage = (ProbeMessage) msg.getObject();

            //If the probe got back to the initiator we abort that transaction
            if (probeMessage.getRecipient() == probeMessage.getInitiator()) {
                TransInfo aborted = simParams.transInfos.get(probeMessage.getInitiator());

                if (Log.isLoggingEnabled())
                    log.log(probeMessage.getInitiator(), "Probe has reached initiator! Trans " + probeMessage.getInitiator());

                simParams.stats.addDeadlockFound();
                simParams.stats.addDeadlockResolved();

                server.getNIC().sendMessage(
                        new Message(aborted.serverID, ServerProcess.TransactionManager, "A:" + aborted.transID, msg.getDeadline()));

            } else {//Else the probe must be sent to everything this transaction is waiting on

                //Prevent the message from cycling through the system indefinitely
                int remainingHops = Integer.parseInt(msg.getContents()) - 1;
                if (remainingHops == 0)
                    return;

                if (Log.isLoggingEnabled())
                    log.log(probeMessage.getInitiator(), "Probe has reached Trans " + probeMessage.getRecipient());

                //So now we get all the waiting locks this transaction has, to see if it is waiting on anything
                List<Lock> waitingLocks = server.getLM().getAllWaitingLocksFor(probeMessage.getRecipient());

                Map<Integer, List<Lock>> heldLocks = server.getLM().getHeldLocks();

                //POTENTIAL ISSUE
                // This transaction only looks at the waiting locks at its server, it may be waiting on remote locks on other servers
                // To solve this we could report back to the transaction that its remote lock is waiting on a list of other locks.

                //For every waiting lock
                waitingLocks.forEach(lock -> {
                    //Get all the held locks on that page (at this server)
                    List<Lock> heldLocksList = heldLocks.get(lock.getPageNum());

                    //For each of the held locks (held by other transactions)
                    heldLocksList.forEach(lock1 -> {
                        if (Log.isLoggingEnabled())
                            log.log(probeMessage.getInitiator(), "Sending probe to Trans " + lock1.getTransID());

                        //Send the probe to those transactions
                        server.getNIC().sendMessage(
                                new Message(lock1.getServerID(), ServerProcess.DDP, "" + remainingHops,
                                        new ProbeMessage(probeMessage.getInitiator(), lock.getTransID(), lock1.getTransID()), simParams.getTime()));
                    });
                });

                //Now send probe to all cohorts of this transaction (because the master is always waiting on its cohorts to complete too)
                Transaction recipient = server.getTM().getTransaction(probeMessage.getRecipient());
                recipient.getCohortServerIDS().forEach(serverID -> {
                    if (Log.isLoggingEnabled())
                        log.log(probeMessage.getInitiator(), "Sending probe to cohort on server " + serverID);

                    //Send the probe to all cohort transactions
                    server.getNIC().sendMessage(
                            new Message(serverID, ServerProcess.DDP, "" + remainingHops,
                                    new ProbeMessage(probeMessage.getInitiator(), probeMessage.getSender(), recipient.getID()), simParams.getTime()));
                });
            }
        }

    }
}
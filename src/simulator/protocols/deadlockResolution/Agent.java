package simulator.protocols.deadlockResolution;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;
import ui.Log;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Create agents to handle deadlock situation. Each agent calculate dropability number based on priority, extra time, and workload.
 * The calculated number will be shared with other agents for further decision making
 * <p>
 * Created by Mani
 */
public class Agent {
    // MANI: Change!
    private static final long WORKLOAD_COEFF = 5;
    private static final long PRIORITY_COEFF = 10;
    private static final long EXTRATIME_COEFF = 1;

    private final Log log;
    private final Server server;
    private final SimParams simParams;

    private final int agentID;
    private final Deadlock deadlock;
    private final int deadlockID;

    private final TransInfo myTrans;
    private final List<TransInfo> transactionsInDeadlock;
    private final Map<TransInfo, Integer> agentsToDropabilities = new HashMap<>();
    private long dropability;

    public Agent(int agentID, Deadlock deadlock, List<TransInfo> transactionsInDeadlock, Server server, int deadlockID) {
        this.agentID = agentID;
        this.deadlock = deadlock;
        this.deadlockID = deadlockID;

        myTrans = find(transactionsInDeadlock, agentID);
        this.transactionsInDeadlock = transactionsInDeadlock;
        this.server = server;
        simParams = server.getSimParams();
        log = new Log(ServerProcess.DRP, server.getID(), simParams.timeProvider, simParams.log);
    }

    public void resolve() {
        if (Log.isLoggingEnabled())
            log.log(agentID, deadlockID + ": Resolving deadlock for trans " + agentID + " for deadlock: " + transactionsInDeadlock);

        Transaction trans = server.getTM().getTransaction(agentID);

        long extraTime = trans.getDeadline() - server.getSimParams().timeProvider.get();
        long priority = trans.getPriority(transactionsInDeadlock, simParams.getPp());
        long workload = trans.getWorkload();

        dropability = (EXTRATIME_COEFF * extraTime) / ((PRIORITY_COEFF * priority) + (WORKLOAD_COEFF * workload));

        if (simParams.getTime() + trans.getExecutionTime()> trans.getDeadline()) {
            //System.out.println("####### HELP AGENT " + myTrans.getID() + " ##########");
            dropability = Integer.MAX_VALUE;
        }

//        System.out.println("*******************************");
//        System.out.print("AgentInfo: " + agentID + "; dropability = " + dropability);
//        System.out.print("; extraTime = " + extraTime);
//        System.out.print("; execution time = " + trans.getExecutionTime());
//        System.out.print("; deadline = " + trans.getDeadline());
//        System.out.print("; workload = " + workload);
//        System.out.println("; priority = " + priority);
//        System.out.println("*******************************");

        transactionsInDeadlock.forEach(ti -> {
            //If the transaction isn't that agent's transaction
            if (ti.transID != agentID) {
                if (Log.isLoggingEnabled())
                    log.log(agentID, deadlockID + ": Sending dropability (" + dropability + ") to other agent " + ti.transID);

                server.getNIC().sendMessage(new Message(ti.serverID, ServerProcess.DRP, "RD:" + ti.transID + ":" + agentID + ":" + deadlockID + ":" + dropability, ti.deadline));
            }
        });
    }

    public void receiveDropability(int dropability, int transID) {
        if (Log.isLoggingEnabled())
            log.log(this.agentID, deadlockID + ": Agent: receive Dropability (dropability = [" + dropability + "], transID = [" + transID + "])");

        TransInfo ti = find(transactionsInDeadlock, transID);

        agentsToDropabilities.put(ti, dropability);

        //If we have received all the dropabilities from the other transactions
        if (agentsToDropabilities.values().size() == transactionsInDeadlock.size() - 1) {
            long maxDropability = Collections.max(agentsToDropabilities.values());

            // If current agent has the highest dropability then drop its transaction
            if (maxDropability < this.dropability) {

                // If there is not enough time to restart, abort the transaction and do not restart it
//                if (simParams.getTime() + myTrans.executionTime > myTrans.deadline) {
//                    if (server.getTM().isOnThisServer(agentID)) {
//                        System.out.println("####### HELP AGENT (message) " + myTrans.getID() + "##########");
////                        if(Log.isLoggingEnabled()) log.log(agentID, "Agents determined that " + agentID + ",  should be dropped.");
//                    }
//                }
                if (Log.isLoggingEnabled())
                    log.log(agentID, deadlockID + ": Agents determined that " + agentID + "  should be dropped.");

                deadlock.setResolutionTime(simParams.getTime());
                simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                server.getTM().abort(agentID);

                return;
            } else if (maxDropability == this.dropability) {
                // If more than one agent have the same highest dropability
                if (count(agentsToDropabilities.values(), this.dropability) > 1) {
                    List<TransInfo> dropables = agentsToDropabilities.keySet().stream().filter(
                            agent -> agentsToDropabilities.get(agent) == this.dropability).collect(Collectors.toList());

                    // If current agent has the passed deadline then drop its transaction
                    if (maxDropability == Integer.MAX_VALUE){
                        deadlock.setResolutionTime(simParams.getTime());
                        simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                        server.getTM().abort(agentID);

                        return;
                    }

                    List<Integer> priorities = new ArrayList<>();
                    for (TransInfo dropable : dropables)
                        priorities.add(dropable.getPriority(transactionsInDeadlock, simParams.getPp()));

                    long lowestPriority = Collections.min(priorities);

                    // If more than one agent have the same highest dropability & lowest priority
                    if (count(priorities, lowestPriority) > 1) {
                        List<TransInfo> lowestPriorities = new ArrayList<>();
                        dropables.forEach(d -> {
                            if (d.getPriority(transactionsInDeadlock, simParams.getPp()) == lowestPriority)
                                lowestPriorities.add(d);
                        });

                        List<Integer> workloads = new ArrayList<>();
                        for (TransInfo dropable : lowestPriorities)
                            workloads.add(dropable.workload);

                        long lowestWorkload = Collections.min(workloads);

                        // If more than one agent have the same highest dropability & lowest priority & lowest workload
                        if (count(workloads, lowestWorkload) > 1) {
                            List<TransInfo> lowestWorkloads = new ArrayList<>();
                            lowestPriorities.forEach(d -> {
                                if (d.workload == lowestWorkload) lowestWorkloads.add(d);
                            });

                            TransInfo youngest = null;
                            for (TransInfo dropable : lowestWorkloads) {
                                if (youngest == null)
                                    youngest = dropable;
                                else if (dropable.transID > youngest.transID)
                                    youngest = dropable;
                            }

                            if (youngest.serverID == server.getID()) {
                                if (Log.isLoggingEnabled())
                                    log.log(agentID, deadlockID + ": Agents determined that " + youngest.transID + " should be dropped.");

                                deadlock.setResolutionTime(simParams.getTime());
                                simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                                server.getTM().abort(youngest.transID);
                            } else {
                                if (Log.isLoggingEnabled())
                                    log.log(agentID, deadlockID + ": Agents determined that " + youngest.transID + " should be dropped, but it not on this server!");
                            }
                            return;
                        }
                        // Else if the current agent has the highest dropability & lowest priority & lowest workload then drop its transaction
                        else {
                            dropables.forEach(dropable -> {
                                if (dropable.workload == lowestWorkload) {
                                    if (dropable.serverID == server.getID()) {
                                        if (Log.isLoggingEnabled())
                                            log.log(agentID, deadlockID + ": Agents determined that " + dropable.transID + "Should be dropped.");

                                        deadlock.setResolutionTime(simParams.getTime());
                                        simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                                        server.getTM().abort(dropable.transID);
                                    } else {
                                        if (Log.isLoggingEnabled())
                                            log.log(agentID, deadlockID + ": Agents determined that " + dropable.transID + "Should be dropped, but its not on this server!");
                                    }
                                }
                            });
                            return;
                        }
                    }
                    // Else if the current agent has the highest dropability & lowest priority then drop its transaction
                    else {
                        dropables.forEach(dropable -> {
                            if (dropable.getPriority(transactionsInDeadlock, simParams.getPp()) == lowestPriority) {
                                if (dropable.serverID == server.getID()) {
                                    if (Log.isLoggingEnabled())
                                        log.log(agentID, deadlockID + ": Agents determined that " + dropable.transID + "Should be dropped.");

                                    deadlock.setResolutionTime(simParams.getTime());
                                    simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                                    server.getTM().abort(dropable.transID);
                                } else {
                                    if (Log.isLoggingEnabled())
                                        log.log(agentID, deadlockID + ": Agents determined that " + dropable.transID + "Should be dropped, but its not on this server.");
                                }
                            }
                        });
                        return;
                    }
                }
                // Else if the current agent has the highest dropability then drop its transaction
                else {
                    if (Log.isLoggingEnabled())
                        log.log(agentID, deadlockID + ": Agents determined that " + agentID + ", Should be dropped.");

                    deadlock.setResolutionTime(simParams.getTime());
                    simParams.getDeadlockResolutionListener().accept(deadlock, agentID);
                    server.getTM().abort(agentID);
                    return;
                }
            }
        }
    }

    private TransInfo find(List<TransInfo> transactionsInDeadlock, int transID) {
        for (TransInfo ti : transactionsInDeadlock)
            if (ti.transID == transID)
                return ti;

        throw new NullPointerException("Server " + server.getID() + ": " + deadlockID + ": Could not find transaction t " + transID);
    }

    private int count(Collection<Integer> dropabilites, long value) {
        int count = 0;
        for (Integer drops : dropabilites)
            if (drops == value)
                count++;
        return count;
    }

    public long getAgentID() {
        return agentID;
    }

    public List<TransInfo> getTransactionsInDeadlock() {
        return transactionsInDeadlock;
    }

    public Server getServer() {
        return server;
    }
}
package simulator.server.transactionManager;

import com.sun.istack.internal.Nullable;
import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.Server;
import simulator.server.disk.DiskJob;
import simulator.server.lockManager.LockManager;
import simulator.server.network.Message;
import simulator.server.network.NetworkInterface;
import simulator.server.processor.ProcessorJob;
import ui.Log;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TransactionManager {
    public static final String COLON = ":";
    public static final String OBJECT = "OBJECT";
    private final SimParams simParams;
    private final Log log;

    private final Server server;
    private final int serverID;
    private final int maxActiveTrans;
    private final TransactionGenerator TG;

    private final Consumer<Event> eventQueue;
    private final Supplier<Integer> timeProvider;
    private final List<Transaction> queuedTransactions = new ArrayList<>();
    private final List<Transaction> activeTransactions = new ArrayList<>();
    private final List<Transaction> completedTransactions = new ArrayList<>();
    private final List<Transaction> abortedTransactions = new ArrayList<>();
    private final List<Transaction> allMasterTransactions = new ArrayList<>();
    private final List<Transaction> abortedAndGoingToBeRestartedTransactions = new ArrayList<>();

    private final Supplier<Double> transManagerRand;

    public TransactionManager(Server server, SimParams simParams) {
        this.server = server;
        serverID = server.getID();
        log = new Log(ServerProcess.TransactionManager, server.getID(), simParams.timeProvider, simParams.log);

        maxActiveTrans = simParams.maxActiveTrans;
        eventQueue = simParams.eventQueue;
        timeProvider = simParams.timeProvider;
        TG = new TransactionGenerator(server, simParams, this::acceptTrans);
        this.simParams = simParams;

        transManagerRand = simParams.getTransManagerRand();
    }

    private void acceptTrans(Transaction t) {
        if(Log.isLoggingEnabled())
            log.log(t,"Transaction generated: " + t.fullToString());
        allMasterTransactions.add(t);
        queuedTransactions.add(t);

        eventQueue.accept(new Event(timeProvider.get() + 1, serverID, this::checkToStartTrans));

        //This stuff is essential for deadlock detection
        TransInfo tInfo = new TransInfo(serverID, t.getID(), t.getDeadline(), t.getWorkload(), t.getExecutionTime(), t.getSlackTime(), t.getAllReadPageNums(), t.getAllWritePageNums());
        simParams.transInfos.put(tInfo.transID, tInfo);

        //So if we are not using a deadlock DP that requires a wait-for graph we do not need to do this
        if (simParams.usesWFG) {
            //Alert the other nodes of this transactions presence
            for (int i = 0; i < simParams.numberOfServers; i++) {
                if (i != serverID) {
                    Message message = new Message(i, ServerProcess.TransactionManager, tInfo, t.getDeadline());
                    message.setSize(1);
                    server.getNIC().sendMessage(message);
                    simParams.messageOverhead++;
                }
            }
        }
    }

    private void checkToStartTrans() {
        if (Log.isLoggingEnabled())
            log.log("checking to start trans. Num Active = " + activeTransactions.size() + ",  Num waiting = " + queuedTransactions.size());

        if (queuedTransactions.size() == 0) {
            if (Log.isLoggingEnabled())
                log.log("No transactions to start.");

            return;
        }

        if (activeTransactions.size() < maxActiveTrans) {
            if (Log.isLoggingEnabled())
                log.log("Starting new transactions (Num Active = " + activeTransactions.size() + ")");

            Transaction t = simParams.getPp().getHighestPriorityTrans(queuedTransactions);
            startTransaction(t);
        } else {
            if (Log.isLoggingEnabled())
                log.log("Too many active transactions to start another.");
        }
    }

    private void startTransaction(Transaction t) {
        if (Log.isLoggingEnabled())
            log.log(t, "Starting " + t);

        boolean abortedAndRestarted = abortedAndGoingToBeRestartedTransactions.remove(t);
        queuedTransactions.remove(t);


        /*
            If the system is performing very poorly the deadline will be in the past before the transaction is even started.
            This prevents the transaction from starting, then hitting its deadline immediately
        */
        if( t.getDeadline() < timeProvider.get() ){
            if (Log.isLoggingEnabled()) {
                log.log(t, "<b>Transaction's deadline is in the past! (This happens in very low performing runs) :(</b>");
                log.log(t, "<b>Not starting transaction</b>");
            }

            t.setCompletedTime(Integer.MAX_VALUE);
            t.setAborted(true);
            abortedTransactions.add(t);

            if( !(t instanceof CohortTransaction) ) {
                simParams.stats.addTimeout();
                simParams.stats.addNumAborted();
            }
            eventQueue.accept(new Event(timeProvider.get() + 1, serverID, this::checkToStartTrans));
            return;
        }
        //*/

        activeTransactions.add(t);

        //Set up a timeout event, only for master transactions
        if (!(t instanceof CohortTransaction))

            if( !abortedAndRestarted ) {
                //In certain ticks the timeout will occur
                eventQueue.accept(new Event(t.getDeadline() , serverID, () -> {

                    //timeout will only occur if the trans hasn't committed, completed, or aborted
                    if (!t.isCommitted() && !t.isCompleted() && !t.isAborted()) {
                        if (Log.isLoggingEnabled()) {
                            log.log(t.getID(), "<b>Timeout!");
                            log.log(t.getID(), "Locked read pages: " + t.getLockedReadPages());
                            log.log(t.getID(), "Locked write pages: " + t.getPageNumsToServerIDLocksAcquired());
                            log.log(t.getID(), "Processed pages: " + t.getProcessedPages());
                            log.log(t.getID(), "Ready to commit cohorts: " + t.getReadyToCommitCohorts());
                            log.log(t.getID(), "Committed cohorts: " + t.getCommittedCohorts());
                            log.log(t.getID(), "Complete cohorts: " + t.getCompletedCohorts()+"</b>");
                        }
                        abort(t);
                        simParams.stats.addTimeout();
                    }
                    eventQueue.accept(new Event(timeProvider.get() + 1, serverID, this::checkToStartTrans));
                }));
            }

        if (!(t instanceof CohortTransaction))
            spawnChildren(t);

        server.acquireLocks(t);
    }

    /**
     * Abort a transaction based on its ID
     */
    public void abort(int transID) {
        Transaction t = getAbortedTransaction(transID);
        if (t == null && !hasBeenAbortedAndGoingToBeRestarted(transID)) {
            t = getCompletedTransaction(transID);
            if (t == null) {
                t = getActiveTransaction(transID);

                if (!t.isCommitted())
                    abort(t);
            }
        } else {
            if (Log.isLoggingEnabled())
                log.log(transID, "<b><font color=\"red\">Told to abort transaction but it has already been aborted.</font></b>");
        }
    }

    /**
     * Abort this transaction t
     */
    private void abort(Transaction t) {
        t.incAbortCount();

        if (Log.isLoggingEnabled()) {
            if (t instanceof CohortTransaction)
                log.log(t, "<font color=\"red\">Aborting cohort</font>");
            else
                log.log(t, "<font color=\"red\">Aborting for the " + t.getAbortCount() + " time</font>");
        }



        if (!(t instanceof CohortTransaction)) {
            NetworkInterface NIC = server.getNIC();
            String abortMsg = "A:" + t.getID();
            t.getCohortServerIDS().forEach(serverID -> {
                if (Log.isLoggingEnabled())
                    log.log(t, "<font color=\"red\">Sending abort message to server " + serverID + "</font>");

                NIC.sendMessage(new Message(serverID, ServerProcess.TransactionManager, abortMsg, timeProvider.get()));
            });
        }

        server.abort(t);
        activeTransactions.remove(t);
        eventQueue.accept(new Event(timeProvider.get() + 1, serverID, this::checkToStartTrans));

        if( true && !(t instanceof CohortTransaction) && t.getDeadline() > simParams.timeProvider.get()+SimParams.predictedTransactionTime ){
            log.log(t, "<font color=\"green\">Deadline in the future, restarting transaction</font>");

            abortedAndGoingToBeRestartedTransactions.add(t);

            t.resetAfterAbort();
            // We post this event slightly in the future so the cohorts can be aborted before they receive a message to start the cohort again.
            eventQueue.accept(new Event(timeProvider.get()+30 ,serverID, () -> {
                queuedTransactions.add(t);
                startTransaction(t);
            }));

            simParams.stats.addNumAbortedAndRestarted();
        }
        else {
            t.setCompletedTime(Integer.MAX_VALUE);
            t.setAborted(true);
            abortedTransactions.add(t);

            if( !(t instanceof CohortTransaction) )
                simParams.stats.addNumAborted();
        }


    }


    /**
     * Protocol Message:
     * <p>
     * Create Trans    - C:<TransNum>:<deadline>:<ServerID>:<ReadPages>:<WritePages>
     * Abort Trans     - A:<TransNum>
     * Lock Acquired   - L:<TransNum>:<PageNum>:<ServerID>
     * Commit Trans    - CT:<TransNum>
     * Ready To Commit - RTC:<TransNum>:<ServerID>
     * Write Completed - WC:<TransNum>:<ServerID>:<PageNum>
     * Cohort Completed- CC:<TransNum>:<ServerID>
     * <p>
     * Object Message:
     * Message will be "OBJECT", we then look at the object field and act appropriately
     * <p>
     * ex
     * A:5:42:E Acquire page 5 for trans 42 exclusive
     * C:5:462:1,2,3,4:5,6,7,8 Create cohort trans with id=5, deadline=462, read pages:1,2,3,4, and write pages 5,6,7,8
     *
     * @param message
     */
    public void receiveMessage(Message message) {
        String msg = message.getContents();

        if (msg.equals(OBJECT)) {
            TransInfo tInfo = (TransInfo) message.getObject();
            //transInfos.put(tInfo.transID,tInfo);

            //if(Log.isLoggingEnabled()) log.log(tInfo.transID,"Received TransInfo");
            return;
        }

        String[] components = msg.split(COLON);

        int transID = Integer.parseInt(components[1]);

        switch (components[0]) {
            case "A": {

                if (Log.isLoggingEnabled())
                    log.log(transID, "Abort message received");

                if (isOnThisServer(transID))
                    abort(getActiveTransaction(transID));

                break;
            }
            case "C": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Received message to create a cohort transaction: " + msg);

                int deadline = Integer.parseInt(components[2]);
                int masterServerID = Integer.parseInt(components[3]);
                String[] readPagesStr = components[4].split(",");

                CohortTransaction ct = new CohortTransaction(transID, server.getID(), deadline, masterServerID);

                //Might be no read pages, this is for safety
                if (!components[4].isEmpty()) {
                    List<Integer> readPageNums = ct.getReadPageNums();

                    for (String readPageStr : readPagesStr)
                        readPageNums.add(Integer.parseInt(readPageStr));
                }

                //If it contains write pages
                if (components.length == 6) {
                    String[] writePagesStr = components[5].split(",");
                    List<Integer> writePageNums = ct.getWritePageNums();

                    for (String writePageStr : writePagesStr)
                        writePageNums.add(Integer.parseInt(writePageStr));
                }

                ct.prepareToStart();

                startTransaction(ct);

                break;
            }
            case "L": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Received message that a lock was acquired: " + msg);

                int pageNum = Integer.parseInt(components[2]);
                int serverID = Integer.parseInt(components[3]);

                activeTransactions.stream().filter(t -> t.getID() == transID).forEachOrdered(t -> {
                    t.lockAcquired(pageNum, serverID);
                });

                break;
            }
            case "CT": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Received message to commit: " + msg);

                Transaction t = getActiveTransaction(transID);

                commit((CohortTransaction) t);

                break;
            }
            case "RTC": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Received ready to commit message: " + msg);

                if (hasBeenAborted(transID) || hasBeenAbortedAndGoingToBeRestarted(transID)) {
                    if (Log.isLoggingEnabled())
                        log.log(transID, "Have already aborted though.");

                    break;
                }

                int serverID = Integer.parseInt(components[2]);

                Transaction t = getActiveTransaction(transID);
                t.cohortReadyToCommit(serverID);
                tryToCommit(t);

                break;
            }
            case "WC": {
                int serverID = Integer.parseInt(components[2]);
                int pageNum = Integer.parseInt(components[3]);
                if (Log.isLoggingEnabled())
                    log.log(transID, "Write job completed on page: " + pageNum + " at server: " + serverID);

                activeTransactions.stream().filter(t -> t.getID() == transID).forEachOrdered(t -> {
                    t.writeCompleted(pageNum);
                });

                tryToComplete(transID);

                break;
            }
            case "CC": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Cohort completed: " + msg);

                int serverID = Integer.parseInt(components[2]);

                Transaction t = getTransaction(transID);

                t.cohortCompleted(serverID);
                if (!t.isCompleted())
                    tryToComplete(transID);

                break;
            }

            default:
                throw new WTFException("Do not understand message! " + msg);
        }
    }

    /*
     * Used when the LM on this server has acquired a lock
     */
    public void lockAcquired(int transID, int pageNum) {
        if (!hasBeenAborted(transID) && !hasBeenAbortedAndGoingToBeRestarted(transID))
            lockAcquired(getActiveTransaction(transID), pageNum, server.getID());
    }

    public void lockAcquired(Transaction t, int pageNum) {
        lockAcquired(t, pageNum, server.getID());
    }

    public void lockAcquired(Transaction t, int pageNum, int serverID) {
        if (Log.isLoggingEnabled())
            log.log(t, "lockAcquired pageNum = [" + pageNum + "], server = " + serverID);

        //If the lock has been acquired on this server, start reading the page from memory
        if (serverID == server.getID())
            server.getDisk().addJob(new DiskJob(t.getID(), t.getDeadline(), pageNum, (pNum) -> {

                if (Log.isLoggingEnabled())
                    log.log(t, "Read job completed for page " + pageNum);

                //When the read is completed, start processing the page
                server.getCPU().addJob(new ProcessorJob(t.getID(), t.getDeadline(), pageNum, (pNum2) -> {

                    if (Log.isLoggingEnabled())
                        log.log(t, "Process job completed for page " + pageNum);

                    t.pageProcessed(pageNum);
                    tryToCommit(t);
                }));
            }));

        t.lockAcquired(pageNum, serverID);
    }

    public void lockAcquired(int transID, int pageNum, int serverID) {
        if (Log.isLoggingEnabled())
            log.log(transID, "Lock acquired for page " + pageNum + " on server " + serverID);

        if (!hasBeenAborted(transID) && !hasBeenAbortedAndGoingToBeRestarted(transID)) {
            Transaction t = getActiveTransaction(transID);
            t.lockAcquired(pageNum, serverID);
            tryToCommit(t);
        }
    }

    private boolean hasBeenAborted(int transID) {
        for (Transaction t : abortedTransactions)
            if (t.getID() == transID)
                return true;

        return false;
    }

    private boolean hasBeenAbortedAndGoingToBeRestarted(int transID) {
        for (Transaction t : abortedAndGoingToBeRestartedTransactions)
            if (t.getID() == transID)
                return true;

        return false;
    }



    /**
     * This method is called to attempt to commit the transaction
     * It will not commit if not all cohorts are ready to commit or not locks have been acquired on all write pages or not all processing has been done
     *
     * @param t
     */
    public void tryToCommit(Transaction t) {
        if (t.isReadyToCommit()) {
            if (t instanceof CohortTransaction) {
                if (Log.isLoggingEnabled())
                    log.log(t, "Informing master I am ready to commit");

                server.getNIC().sendMessage(new Message(((CohortTransaction) t).getMasterServerID(), ServerProcess.TransactionManager, "RTC:" + t.getID() + COLON + server.getID(), t.getDeadline()));
            } else {
                if (Log.isLoggingEnabled())
                    log.log(t, "Ready to commit, telling cohorts to commit");

                //Tell all cohorts to commit
                t.getCohortServerIDS().forEach(serverID -> {
                    if (Log.isLoggingEnabled())
                        log.log(t, "Sending message to cohort on server " + serverID + " to commit");

                    server.getNIC().sendMessage(new Message(serverID, ServerProcess.TransactionManager, "CT:" + t.getID(), t.getDeadline()));
                });

                if (Log.isLoggingEnabled())
                    log.log(t, "Committed");

                t.setCommitted(true);

                if (t.getAllWritePageNums().isEmpty()) {
                    if (Log.isLoggingEnabled())
                        log.log(t, "No write jobs to complete");

                    complete(t);
                } else
                    t.getWritePageNums().forEach(pageNum -> {
                        if (Log.isLoggingEnabled())
                            log.log(t, "Starting write job for page " + pageNum);

                        server.getDisk().addJob(new DiskJob(t.getID(), t.getDeadline(), pageNum, pNum -> {
                            if (Log.isLoggingEnabled())
                                log.log(t, "Write job completed for page " + pageNum);

                            t.writeCompleted(pNum);

                            if (t.allWriteJobsCompleted()) {
                                if (Log.isLoggingEnabled())
                                    log.log(t, "All write jobs completed");

                                complete(t);
                            } else {
                                List<Integer> notCompletedWriteJobs = t.getNotCompletedWriteJobs();
                                if (Log.isLoggingEnabled())
                                    log.log(t, "Not all write jobs completed yet though, still missing: " + notCompletedWriteJobs);
                            }
                        }));

                        //Do write jobs on the other servers with this page
                        Map<Integer, List<Integer>> writePageNumsToServersWithPage = t.getWritePageNumsToServersWithPage();
                        writePageNumsToServersWithPage.get(pageNum).forEach(remoteServID -> {
                            if (remoteServID != server.getID()) {
                                server.getNIC().sendMessage(new Message(remoteServID, ServerProcess.Disk, "W:" + t.getID() + COLON + pageNum + COLON + server.getID(), t.getDeadline()));
                            }
                        });
                    });
            }
        } else {
            if (Log.isLoggingEnabled())
                log.log(t, "Tried to commit, not ready yet");
        }
    }

    /**
     * This is called from a network message, therefor will only be called on cohort transactions
     *
     * @param t
     */
    private void commit(CohortTransaction t) {
        if (Log.isLoggingEnabled())
            log.log("Committing cohort transaction- " + t);

        t.setCommitted(true);

        if (t.getWritePageNums().isEmpty()) {
            complete(t);

            server.getNIC().sendMessage(new Message(t.getMasterServerID(), ServerProcess.TransactionManager, "CC:" + t.getID() + COLON + server.getID(), t.getDeadline()));
        } else
            t.getWritePageNums().forEach(pageNum -> {
                server.getDisk().addJob(new DiskJob(t.getID(), t.getDeadline(), pageNum, pNum -> {
                    if (Log.isLoggingEnabled())
                        log.log(t, "Write job completed for page " + pageNum);

                    t.writeCompleted(pNum);
                    server.getNIC().sendMessage(new Message(t.getMasterServerID(), ServerProcess.TransactionManager, "WC:" + t.getID() + COLON + server.getID() + COLON + pNum, t.getDeadline()));

                    if (t.allWriteJobsCompleted()) {
                        if (Log.isLoggingEnabled())
                            log.log(t, "All write jobs completed");

                        complete(t);

                        server.getNIC().sendMessage(new Message(t.getMasterServerID(), ServerProcess.TransactionManager, "CC:" + t.getID() + COLON + server.getID(), t.getDeadline()));
                    }
                }));
            });
    }

    private void tryToComplete(int transID) {
        Transaction t = getActiveTransaction(transID);
        if (t.allWriteJobsCompleted()) {
            if (Log.isLoggingEnabled())
                log.log(t, "All write jobs completed");

            complete(t);
        }
    }

    private void complete(Transaction t) {
        if (Log.isLoggingEnabled())
            log.log(t, "Releasing locks!");

        LockManager lm = server.getLM();
        t.getReadPageNums().forEach(pageNum -> lm.releaseLocks(t.getID(), pageNum, t.getDeadline()));
        t.getWritePageNums().forEach(pageNum -> lm.releaseLocks(t.getID(), pageNum, t.getDeadline()));

        int time = simParams.getTime();
        boolean completedOnTime = t.getDeadline() >= time;

        if (Log.isLoggingEnabled()) {
            if( completedOnTime )
                log.log(t, "<font color=\"green\">" + (t instanceof CohortTransaction ? "Cohort " : "") + "Transaction completed on time! :)" + "</font>");
            else
                log.log(t, "<font color=\"red\">" + (t instanceof CohortTransaction ? "Cohort " : "") + "Transaction completed late! :(" + "</font>");

        }
        t.setCompleted(true);
        t.setCompletedTime(time);

        if (!(t instanceof CohortTransaction)) {
            if (completedOnTime)
                simParams.stats.addCompletedOnTime(t.getID());
            else
                simParams.stats.addCompletedLate(t.getID());
        }

        completedTransactions.add(t);
        activeTransactions.remove(t);
        eventQueue.accept(new Event(timeProvider.get() + 1, serverID, this::checkToStartTrans));

        // Integrity Check!
        lm.getWaitingLocks().values().forEach(locksLists -> {
            locksLists.forEach(lock -> {
                if (lock.getTransID() == t.getID())
                    throw new WTFException(serverID + ": Transaction " + t.getID() + " just completed but it has waiting locks still! (Page " + lock.getPageNum() + ")");
            });
        });

        lm.getHeldLocks().values().forEach(locksLists -> {
            locksLists.forEach(lock -> {
                if (lock.getTransID() == t.getID())
                    throw new WTFException(serverID + ": Transaction " + t.getID() + " just completed but it has held locks still! (Page " + lock.getPageNum() + ")");
            });
        });
    }

    private void spawnChildren(Transaction t) {
        if (Log.isLoggingEnabled())
            log.log(t, "SpawnChildren for " + t);

        //Creates a map of servers to a list of pages that are on them.
        Map<Integer, List<Integer>> serversToPages = new HashMap<>();
        //List<Integer> servers


        //First look at the read pages
        t.getAllReadPageNums().forEach(pageNum -> {
            List<Integer> servsWithPage = simParams.getServersWithPage(pageNum);

            //If the page is on this server we will deal with it here, don't create a child for this.
            if (servsWithPage.contains(server.getID()))
                return;

            int serverID = servsWithPage.get((int) (transManagerRand.get() * servsWithPage.size()));

            if (!serversToPages.containsKey(serverID))
                serversToPages.put(serverID, new ArrayList<>());
            serversToPages.get(serverID).add(pageNum);
        });

        //Now the write pages
        t.getAllWritePageNums().forEach(pageNum -> {
            List<Integer> servsWithPage = simParams.getServersWithPage(pageNum);

            //If the page is on this server we will deal with it here, don't create a child for this.
            if (servsWithPage.contains(server.getID()))
                return;

            int serverID = servsWithPage.get((int) (transManagerRand.get() * servsWithPage.size()));

            if (!serversToPages.containsKey(serverID))
                serversToPages.put(serverID, new ArrayList<>());
            serversToPages.get(serverID).add(pageNum);
        });


        //This is to prevent children from being send to 0 and 4 since they have the same pages
        if (serversToPages.containsKey(0) && serversToPages.containsKey(4)) {
            if (transManagerRand.get() < 0.5) {
                serversToPages.get(0).addAll(serversToPages.remove(4));
            } else {
                serversToPages.get(4).addAll(serversToPages.remove(0));
            }
        }

        //This is to prevent children from being send to 1 and 5 since they have the same pages
        if (serversToPages.containsKey(1) && serversToPages.containsKey(5)) {
            if (transManagerRand.get() < 0.5) {
                serversToPages.get(1).addAll(serversToPages.remove(5));
            } else {
                serversToPages.get(5).addAll(serversToPages.remove(1));
            }
        }

        //This is to prevent children from being send to 2 and 6 since they have the same pages
        if (serversToPages.containsKey(2) && serversToPages.containsKey(6)) {
            if (transManagerRand.get() < 0.5) {
                serversToPages.get(2).addAll(serversToPages.remove(6));
            } else {
                serversToPages.get(6).addAll(serversToPages.remove(2));
            }
        }

        //This is to prevent children from being send to 3 and 7 since they have the same pages
        if (serversToPages.containsKey(3) && serversToPages.containsKey(7)) {
            if (transManagerRand.get() < 0.5) {
                serversToPages.get(3).addAll(serversToPages.remove(7));
            } else {
                serversToPages.get(7).addAll(serversToPages.remove(3));
            }
        }

        //Loop through all the servers that we will be creating children on
        serversToPages.keySet().forEach(serverID -> {
            t.addCohort(serverID);

            List<Integer> pagesOnThisServer = serversToPages.get(serverID);
            List<Integer> readPagesOnThisServer = new ArrayList<>();
            List<Integer> writePagesOnThisServer = new ArrayList<>();

            pagesOnThisServer.forEach(pageNum -> {
                if (t.getAllReadPageNums().contains(pageNum))
                    readPagesOnThisServer.add(pageNum);
                else if (t.getAllWritePageNums().contains(pageNum))
                    writePagesOnThisServer.add(pageNum);
            });

            //Remove these child pages from this transactions pages
            t.getReadPageNums().removeAll(readPagesOnThisServer);
            t.getWritePageNums().removeAll(writePagesOnThisServer);

            Message msg = generateCreateChildMessage(server.getID(), serverID, t.getID(), t.getDeadline(), readPagesOnThisServer, writePagesOnThisServer);

            if (Log.isLoggingEnabled())
                log.log(t, "GenerateChildMessage (serverID = [" + serverID + "], transID = [" + t.getID() + "], deadline = [" + t.getDeadline() + "], readPagesOnThisServer = [" + readPagesOnThisServer + "], writePagesOnThisServer = [" + writePagesOnThisServer + "]");

            server.getNIC().sendMessage(msg);
        });

        if (Log.isLoggingEnabled())
            log.log(t, "Master trans left with pages: readPages = [" + t.getReadPageNums() + "], writePages = [" + t.getWritePageNums() + "]");

        t.prepareToStart();
    }

    private static Message generateCreateChildMessage(int thisServerID, int serverID, int transID, int deadline, List<Integer> readPagesOnThisServer, List<Integer> writePagesOnThisServer) {
        StringBuilder sb = new StringBuilder();

        // C:<TransNum>:<deadline>:1,5,25,4:77,45,3,5
        sb.append("C:").append(transID).append(':').append(deadline).append(':').append(thisServerID).append(':');

        if (!readPagesOnThisServer.isEmpty()) {
            readPagesOnThisServer.forEach(pageNum -> sb.append(pageNum).append(','));
            sb.deleteCharAt(sb.length() - 1);
        }

        sb.append(':');

        if (!writePagesOnThisServer.isEmpty()) {
            writePagesOnThisServer.forEach(pageNum -> sb.append(pageNum).append(','));
            sb.deleteCharAt(sb.length() - 1);
        }
        return new Message(serverID, ServerProcess.TransactionManager, sb.toString(), deadline);
    }

    public void start() {
        TG.start();
    }

    public Transaction getTransaction(int transID) {
        for (Transaction t : activeTransactions)
            if (t.getID() == transID)
                return t;

        for (Transaction t : queuedTransactions)
            if (t.getID() == transID)
                return t;

        for (Transaction t : completedTransactions)
            if (t.getID() == transID)
                return t;

        for (Transaction t : abortedTransactions)
            if (t.getID() == transID)
                return t;
//
        for (Transaction t : allMasterTransactions)
            if (t.getID() == transID)
                return t;

        throw new WTFException(server.getID() + ":Could not find transaction " + transID);
    }

    private Transaction getActiveTransaction(int transID) {
        for (Transaction t : activeTransactions)
            if (t.getID() == transID)
                return t;

        throw new WTFException(server.getID() + ": Could not find transaction " + transID);
    }

    @Nullable
    private Transaction getAbortedTransaction(int transID) {
        for (Transaction t : abortedTransactions)
            if (t.getID() == transID)
                return t;

        return null;
    }


    private Transaction getCompletedTransaction(int transID) {
        for (Transaction t : completedTransactions)
            if (t.getID() == transID)
                return t;

        return null;
    }

    /* For debugging purposes
    public static void main(String[] args) {
        List<Integer> readPages = Arrays.asList(1, 2, 3, 4);
        List<Integer> writePages = new ArrayList<>();//Arrays.asList(5,6,7,8);
        Message msg = generateCreateChildMessage(0, 0, 1, 2, readPages, writePages);
        System.out.println(msg.getContents());
        String[] split = msg.getContents().split(COLON);
        for (String s : split) {
            System.out.println(s);
        }
    }
    */

    public int getNumberActiveTrans() {
        return activeTransactions.size();
    }

    public List<Transaction> getActiveTransactions() {
        return activeTransactions;
    }

    public boolean isOnThisServer(int transID) {
        for (Transaction t : activeTransactions)
            if (t.getID() == transID)
                return true;

        return false;
    }

    public List<Transaction> getAllMasterTransactions() {
        return allMasterTransactions;
    }

    public TransactionGenerator getTG() {
        return TG;
    }
}
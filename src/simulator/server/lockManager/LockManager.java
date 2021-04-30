package simulator.server.lockManager;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.transactionManager.Transaction;
import ui.Log;

import java.util.*;

public class LockManager {

    private static final String E = "E";
    private final Log log;
    private final Server server;
    private final SimParams simParams;
    private final int serverID;
    private final Range pageRange;
    private Map<Integer, List<Lock>> heldLocks = new HashMap<>(); //Page to list of locks
    private Map<Integer, List<Lock>> waitingLocks = new HashMap<>(); //Page to list of locks

    /*
        These hashmaps are in the form
        {Page Number -> {List of Locks}
    */

    public LockManager(Server server, SimParams simParams, Range pageRange) {
        this.server = server;
        this.simParams = simParams;
        serverID = server.getID();
        this.pageRange = pageRange;
        log = new Log(ServerProcess.LockManager, server.getID(), simParams.timeProvider, simParams.log);

        for (int i = pageRange.getMin(); i <= pageRange.getMax(); i++) {
            heldLocks.put(i, new LinkedList<>());
            waitingLocks.put(i, new LinkedList<>());
        }
    }

    /**
     * Called when a lock is released, checks to see if any waiting locks can move into the held locks list
     */
    private void checkForObtainableLocks() {
        waitingLocks.keySet().forEach(pageNum -> {
            List<Lock> wLocks = waitingLocks.get(pageNum);
            if (wLocks.isEmpty())
                return;

            List<Lock> locks = heldLocks.get(pageNum);
            if (locks == null) //Safety check
                throw new WTFException("Invalid Page Number! Waiting lock for page " + pageNum + " on server " + server.getID());

            //If there are no held locks on this page then we consider adding locks. This prevents starvation by continuously adding shared locks in-front of an exclusive one.
            if (locks.isEmpty()) {

                List<Lock> acquiredLocks = new ArrayList<>();

                boolean sharedLockAdded = false;
                for (Lock lock : wLocks) {

                    if (lock.isExclusive()) {
                        if (!sharedLockAdded) {
                            locks.add(lock);
                            acquiredLocks.add(lock);
                            break;
                        }
                    } else {
                        sharedLockAdded = true;
                        locks.add(lock);
                        acquiredLocks.add(lock);
                    }
                }

                wLocks.removeAll(acquiredLocks);

                acquiredLocks.forEach(lock -> {
                    if (lock.getServerID() == server.getID())
                        server.getTM().lockAcquired(lock.getTransID(), lock.getPageNum());
                    else {
                        server.getNIC().sendMessage(new Message(lock.getServerID(), ServerProcess.LockManager, "A:" + lock.getTransID() + ":" + lock.getPageNum() + ":" + server.getID(), lock.getDeadline()));
                    }
                });
            }
        });
    }


    /**
     * A|R:<pageNum>:<TransNum>:E|S
     * <p>
     * ex
     * L:5:42:2:E Acquire exclusive lock page 42 for trans 5 (which is on server 2)
     * R:5:42 Release lock on page 42 for trans 5
     * A:5:42:4 Acquired lock on page 42 for trans 5 on server 4
     */
    public void receiveMessage(Message message) {

        String msg = message.getContents();
        String[] components = msg.split(":");

        int transID = Integer.parseInt(components[1]);
        int pageNum = Integer.parseInt(components[2]);

        switch (components[0]) {
            case "L": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Remote lock request for page " + pageNum);

                int serverID = Integer.parseInt(components[3]);
                boolean exclusive = components[4].equals(E);

                List<Lock> locks = heldLocks.get(pageNum);
                if (exclusive) {
                    if (locks.isEmpty()) {
                        if (Log.isLoggingEnabled())
                            log.log(transID, "Remote lock request for page " + pageNum + " accepted.");

                        locks.add(new Lock(pageNum, transID, true, message.getDeadline(), serverID));
                        server.getNIC().sendMessage(new Message(serverID, ServerProcess.LockManager, "A:" + transID + ":" + pageNum + ":" + server.getID(), message.getDeadline()));
                    } else {
                        if (Log.isLoggingEnabled())
                            log.log(transID, "Remote lock request for page " + pageNum + " waiting.");

                        waitingLocks.get(pageNum).add(new Lock(pageNum, transID, true, message.getDeadline(), serverID));
                    }
                }
                break;
            }
            case "A": {
                int serverID = Integer.parseInt(components[3]);

                if (Log.isLoggingEnabled())
                    log.log(transID, "Acquired remote lock for page " + pageNum + " from server " + serverID);

                server.getTM().lockAcquired(transID, pageNum, serverID);

                break;
            }
            case "R": {
                if (Log.isLoggingEnabled())
                    log.log(transID, "Received message to release lock on page " + pageNum);

                List<Lock> locks = heldLocks.get(pageNum);
                for (int i = 0; i < locks.size(); i++) {
                    if (locks.get(i).getTransID() == transID) {
                        locks.remove(i);
                        i--;
                        if (Log.isLoggingEnabled())
                            log.log(transID, "Released held lock for page " + pageNum + " on server " + server.getID());
                    }
                }
                locks = waitingLocks.get(pageNum);
                for (int i = 0; i < locks.size(); i++) {
                    if (locks.get(i).getTransID() == transID) {
                        locks.remove(i);
                        i--;
                        if (Log.isLoggingEnabled())
                            log.log(transID, "Released waiting lock for page " + pageNum + " on server " + server.getID());
                    }
                }
                simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::checkForObtainableLocks));
                break;
            }
        }
    }

    public void acquireLocks(Transaction t) {
        if (Log.isLoggingEnabled())
            log.log(t, "acquireLocks(" + t);

        int transID = t.getID();
        int serverID = server.getID();

        for (int pageNum : t.getReadPageNums()) {
            List<Lock> locks = heldLocks.get(pageNum);

            if (locks == null) //Safety check
                throw new WTFException("Transaction " + transID + " tried to acquire lock on page " + pageNum + " on server " + server.getID());

            if (locks.isEmpty() || !locks.get(0).isExclusive()) {
                //Acquire lock locally
                locks.add(new Lock(pageNum, transID, false, t.getDeadline(), serverID));
                server.getTM().lockAcquired(t, pageNum);
                //Shared locks do not need to be acquired everywhere
            } else
                waitingLocks.get(pageNum).add(new Lock(pageNum, transID, false, t.getDeadline(), serverID));
        }

        for (int pageNum : t.getWritePageNums()) {
            List<Lock> locks = heldLocks.get(pageNum);

            if (locks.isEmpty()) {
                //Acquire lock locally
                locks.add(new Lock(pageNum, t.getID(), true, t.getDeadline(), serverID));
                server.getTM().lockAcquired(t, pageNum);
            } else {
                if (Log.isLoggingEnabled())
                    log.log(t, "Tried to acquire lock on page " + pageNum + " but I am waiting on " + locks);

                waitingLocks.get(pageNum).add(new Lock(pageNum, transID, true, t.getDeadline(), serverID));
            }
            //Acquire lock remotely
            List<Integer> serversWithPage = simParams.getServersWithPage(pageNum);
            serversWithPage.forEach(servID -> {
                if (server.getID() != servID) {
                    server.getNIC().sendMessage(new Message(servID, ServerProcess.LockManager, "L:" + transID + ":" + pageNum + ":" + server.getID() + ":E", t.getDeadline()));
                }
            });
            t.getWritePageNumsToServersWithPage().put(pageNum, serversWithPage);
        }
    }


    public void abort(Transaction t) {
        if (Log.isLoggingEnabled())
            log.log(t, "Releasing locks");

        int transID = t.getID();

        readPages:
        for (int pageNum : t.getReadPageNums()) {
            releaseLocks(t.getID(), pageNum, 0);

//            List<Lock> locks = waitingLocks.get(pageNum);
//            for (int i = 0; i < locks.size(); i++) {
//                Lock l = locks.get(i);
//                if( l.getTransID() == transID ){
//                    locks.remove(l);
//                    continue readPages;
//                }
//            }
//
//            locks = heldLocks.get(pageNum);
//            for (int i = 0; i < locks.size(); i++) {
//                Lock l = locks.get(i);
//                if( l.getTransID() == transID ){
//                    locks.remove(l);
//                    continue readPages;
//                }
//            }


        }

        writePages:
        for (int pageNum : t.getWritePageNums()) {
            releaseLocks(t.getID(), pageNum, 0);

//            List<Lock> locks = waitingLocks.get(pageNum);
//            for (int i = 0; i < locks.size(); i++) {
//                Lock l = locks.get(i);
//                if( l.getTransID() == transID ){
//                    locks.remove(l);
//                    continue writePages;
//                }
//            }
//
//            locks = heldLocks.get(pageNum);
//            for (int i = 0; i < locks.size(); i++) {
//                Lock l = locks.get(i);
//                if( l.getTransID() == transID ){
//                    locks.remove(l);
//                    continue writePages;
//                }
//            }
        }

        simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::checkForObtainableLocks));
    }

    /**
     * Releases only local locks
     */
    public void releaseLock(int transID, int pageNum) {

        if (Log.isLoggingEnabled())
            log.log(transID, "Trying to release lock on page " + pageNum);

        //Check for waiting locks
        Lock releasedLock = null;
        for (Lock lock : waitingLocks.get(pageNum)) {
            if (transID == lock.getTransID())
                releasedLock = lock;
        }

        if (releasedLock != null) {
            if (Log.isLoggingEnabled())
                log.log(transID, "Releasing waiting lock - " + releasedLock);

            waitingLocks.get(pageNum).remove(releasedLock);

            simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::checkForObtainableLocks));
            return;
        }

        if (Log.isLoggingEnabled())
            log.log(transID, "Lock not found in waiting locks for page " + pageNum);

        //Now check for held locks because there were no waiting locks

        boolean found = false;
        releasedLock = null;
        for (Lock lock : heldLocks.get(pageNum)) {
            if (transID == lock.getTransID()) {
                if (found)
                    throw new WTFException(serverID + ": Found multiple locks on page " + pageNum + " for trans " + transID + " found: " + releasedLock + " and " + lock);
                releasedLock = lock;
                found = true;
            }
        }

        if (releasedLock == null) {
            if (Log.isLoggingEnabled())
                log.log(transID, "Did not find held lock on page " + pageNum);

        } else {
            if (Log.isLoggingEnabled())
                log.log(transID, "Releasing held lock " + releasedLock + " on page " + pageNum);

            boolean successfullyRemoved = heldLocks.get(pageNum).remove(releasedLock);

            if (!successfullyRemoved)
                throw new WTFException(serverID + ": Lock " + releasedLock + " not successfully removed on page " + pageNum + " for trans " + transID);

            simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::checkForObtainableLocks));
        }
    }


    /**
     * Releases remote and local locks
     */
    public void releaseLocks(int transID, int pageNum, int deadline) {
        if (Log.isLoggingEnabled())
            log.log(transID, "Releasing all locks on page " + pageNum);

        if (pageRange.contains(pageNum)) {
            releaseLock(transID, pageNum);
        } else {
            if (Log.isLoggingEnabled())
                log.log(transID, "Page num outside range of this server " + pageRange);
        }

        List<Integer> serversWithPage = simParams.getServersWithPage(pageNum);
        if (Log.isLoggingEnabled())
            log.log(transID, "Servers " + serversWithPage + " have page " + pageNum);

        serversWithPage.stream()
                .filter(serverID -> serverID != server.getID())
                .forEach(serverID -> {
                    if (Log.isLoggingEnabled())
                        log.log(transID, "Sending message to server " + serverID + " to release lock on page " + pageNum);

                    server.getNIC().sendMessage(new Message(serverID, ServerProcess.LockManager, "R:" + transID + ":" + pageNum, deadline));
                });
    }

    public Map<Integer, List<Lock>> getHeldLocks() {
        return heldLocks;
    }

    public Map<Integer, List<Lock>> getWaitingLocks() {
        return waitingLocks;
    }

    public List<Lock> getAllWaitingLocksFor(int transID) {
        List<Lock> tLocks = new ArrayList<>();

        waitingLocks.values().forEach(locks -> locks.forEach(lock -> {
            if (lock.getTransID() == transID) {
                tLocks.add(lock);
            }
        }));

        return tLocks;
    }
}
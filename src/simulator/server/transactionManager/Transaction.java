package simulator.server.transactionManager;

import exceptions.WTFException;
import simulator.protocols.priority.PriorityProtocol;

import java.util.*;

public class Transaction {

    private final int ID;
    private final int serverID;
    private final int deadline;

    private int workload;
    private int executionTime;
    private int slackTime;

    private final List<Integer> allReadPageNums = new ArrayList<>();
    private final List<Integer> allWritePageNums = new ArrayList<>();

    private final List<Integer> readPageNums = new ArrayList<>();
    private final List<Integer> writePageNums = new ArrayList<>();

    private final List<Integer> lockedReadPages = new ArrayList<>();

    /**
     * write page num -> a list of servers with that page
     */
    private final Map<Integer, List<Integer>> writePageNumsToServersWithPage = new HashMap<>();

    /**
     * write page num -> a list of servers where a lock has been acquired on that page
     */
    private final Map<Integer, List<Integer>> pageNumsToServerIDLocksAcquired = new HashMap<>();

    private final List<Integer> processedPages = new ArrayList<>();

    //The values stored in here are the server ID's where the cohorts are.
    private final List<Integer> cohortServers = new ArrayList<>();
    private final List<Integer> readyToCommitCohorts = new ArrayList<>();
    private final List<Integer> committedCohorts = new ArrayList<>();

    private boolean committed;

    private final List<Integer> pagesWritten = new ArrayList<>();

    //Stores the serverID
    private List<Integer> completedCohorts = new ArrayList<>();
    private boolean completed;
    private int completedTime = -1;

    private boolean aborted;
    private int abortCount;

    public Transaction(int ID, int serverID, int deadLine) {
        this.ID = ID;
        this.serverID = serverID;
        this.deadline = deadLine;
    }

    // **  Stage 1  - Acquire Locks ** //

    public void lockAcquired(int pageNum, int serverID) {
        if (writePageNums.contains(pageNum)) {
            pageNumsToServerIDLocksAcquired.get(pageNum).add(serverID);
        } else if (readPageNums.contains(pageNum)) {
            lockedReadPages.add(pageNum);
        } else
            throw new WTFException("Transaction " + ID + ": Acquired a lock for a page I don't have! pageNum: " + pageNum + " on server: " + serverID);
    }


    // **  Stage 2  - ServerProcess Pages ** //

    public void pageProcessed(int pageNum) {
        processedPages.add(pageNum);
    }


    // **  Stage 3  - Commit ** //

    public boolean isReadyToCommit() {
        boolean allProcessingDone = processedPages.containsAll(readPageNums) && processedPages.containsAll(writePageNums);

        boolean allLocksAcquired = allLocksAcquired();

        boolean allCohortsAreReadyToCommit = allCohortsAreReadyToCommit();

        return allProcessingDone && allLocksAcquired && allCohortsAreReadyToCommit;
    }

    private boolean allCohortsAreReadyToCommit() {
        return readyToCommitCohorts.containsAll(cohortServers);
    }

    private boolean allLocksAcquired() {
        boolean allFound = true;

        for (int writePageNum : writePageNums) {
            List<Integer> serversWithThisPage = writePageNumsToServersWithPage.get(writePageNum);
            List<Integer> serversWithThisLockAcquired = pageNumsToServerIDLocksAcquired.get(writePageNum);

            allFound &= serversWithThisLockAcquired.containsAll(serversWithThisPage);
        }

        allFound &= lockedReadPages.containsAll(readPageNums);

        return allFound;
    }

    public void cohortReadyToCommit(int serverID) {
        readyToCommitCohorts.add(serverID);
    }


    // **  Stage 4  - Write Pages         ** //

    public void writeCompleted(int pNum) {
        pagesWritten.add(pNum);
    }




    /*   Getters and Setters    */

    public void addCohort(int serverID) {
        cohortServers.add(serverID);
    }

    public List<Integer> getCohortServerIDS() {
        return cohortServers;
    }

    public boolean allCohortsHaveCommited() {
        return committedCohorts.containsAll(cohortServers);
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isCommitted() {
        return committed;
    }

    public boolean allWriteJobsCompleted() {
        return pagesWritten.containsAll(allWritePageNums);
    }

    public Map<Integer, List<Integer>> getWritePageNumsToServersWithPage() {
        return writePageNumsToServersWithPage;
    }

    public int getID() {
        return ID;
    }

    public List<Integer> getReadPageNums() {
        return readPageNums;
    }

    public List<Integer> getWritePageNums() {
        return writePageNums;
    }

    public List<Integer> getAllReadPageNums() {
        return allReadPageNums;
    }

    public List<Integer> getAllWritePageNums() {
        return allWritePageNums;
    }

    public int getPriority(List<TransInfo> transactionsInDeadlock, PriorityProtocol pp) {
        return pp.getTransPriority(transactionsInDeadlock, this.getID());
    }

    public int getDeadline() {
        return deadline;
    }

    @Override
    public String toString() {
        return "Transaction " + ID;
    }

    public String fullToString() {
        return "Transaction{" +
                "ID= " + ID +
                ", allReadPageNums= " + allReadPageNums +
                ", allWritePageNums= " + allWritePageNums +
                ", readPageNums= " + readPageNums +
                ", writePageNums= " + writePageNums +
                ", deadline= " + deadline +
                '}';
    }

    public void cohortCompleted(int serverID) {
        completedCohorts.add(serverID);
    }

    public List<Integer> getNotCompletedWriteJobs() {
        List<Integer> notCompletedWriteJobs = new ArrayList<>(allWritePageNums);
        notCompletedWriteJobs.removeAll(pagesWritten);
        return notCompletedWriteJobs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void prepareToStart() {
        writePageNums.forEach(pageNum -> {
            pageNumsToServerIDLocksAcquired.put(pageNum, new ArrayList<>());
        });
    }

    public void setCompletedTime(int completedTime) {
        this.completedTime = completedTime;
    }

    public int getCompletedTime() {
        return completedTime;
    }

    public int getWorkload() {
        return workload;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setWorkload(int workload) {
        this.workload = workload;
    }

    public void setExecutionTime(int executionTime) {
        this.executionTime = executionTime;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    public List<Integer> getLockedReadPages() {
        return lockedReadPages;
    }

    public List<Integer> getProcessedPages() {
        return processedPages;
    }

    public List<Integer> getPagesWritten() {
        return pagesWritten;
    }

    public List<Integer> getReadyToCommitCohorts() {
        return readyToCommitCohorts;
    }

    public List<Integer> getCommittedCohorts() {
        return committedCohorts;
    }

    public List<Integer> getCompletedCohorts() {
        return completedCohorts;
    }

    public Map<Integer, List<Integer>> getPageNumsToServerIDLocksAcquired() {
        return pageNumsToServerIDLocksAcquired;
    }

    public int getSlackTime() {
        return slackTime;
    }

    public void setSlackTime(int slackTime) {
        this.slackTime = slackTime;
    }

    public void resetAfterAbort(){

        cohortServers.clear();

        readPageNums.clear();
        readPageNums.addAll(allReadPageNums);

        writePageNums.clear();
        writePageNums.addAll(allWritePageNums);

        lockedReadPages.clear();
        processedPages.clear();
        pagesWritten.clear();
        readyToCommitCohorts.clear();
        committedCohorts.clear();
        completedCohorts.clear();

        pageNumsToServerIDLocksAcquired.clear();
        writePageNumsToServersWithPage.clear();
    }

    public void incAbortCount() {
        abortCount++;
    }

    public int getAbortCount() {
        return abortCount;
    }
}
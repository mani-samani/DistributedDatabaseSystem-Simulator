package simulator;

import simulator.eventQueue.Event;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.priority.PriorityProtocol;
import simulator.server.Server;
import simulator.server.lockManager.Range;
import simulator.server.transactionManager.CohortTransaction;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;
import stats.Statistics;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is used by every component in the simulation.
 * It is used to provide them with parameters and provide utility methods.
 * For instance, since every component holds a reference to this object, they can all add events, get the current time, get random numbers between 0 and 1, add messages to the log, etc.
 */
public class SimParams {

    public static int predictedTransactionTime = 1000;
    public final Consumer<String> log;
    public final Statistics stats;
    public final List<Server> allServers = new ArrayList<>();

    public static final int diskReadWriteTime = 35;
    public static final int processTime = 15;
    public static int Bandwidth = 1000;
    public static int latency = 5;
    public final int arrivalRateMean;
    public final int maxActiveTrans;
    private int numTransPerServer = 400;
    private final double updateRate;
    private final int numPages;
    public String DRP;
    public String DDP;

    public boolean agentBased = false;
    private Supplier<Double> transGeneratorRand;
    private Supplier<Double> transManagerRand;

    public int getNumberOfServers() {
        return numberOfServers;
    }

    public final int numberOfServers = 8;
    public int messageOverhead = 0;

    public static final int transactionTimeoutMean = 5000;


    public final Map<Integer, Range> serverToPageRange = new HashMap<>();
    public final Consumer<Event> eventQueue;
    public final Supplier<Double> rand;
    public final Supplier<Integer> timeProvider;

    /**
     * Used by the Transaction Generator to ensure no transactions have the same ID
     */
    public final Supplier<Integer> IDProvider;

    public Supplier<Integer> getPageNumProvider() {
        return pageNumProvider;
    }

    public final Supplier<Integer> pageNumProvider;

    private BiConsumer<Integer, Integer> overheadIncurer;

    public boolean usesWFG = false;

    public final List<Integer> allServersList;
    private int overIncurred;
    private Consumer<Deadlock> deadlockListener;
    private BiConsumer<Deadlock, Integer> deadlockResolutionListener;
    public final Map<Integer, TransInfo> transInfos = new HashMap<>();
    private PriorityProtocol pp;
    private int searchInterval;
    public final int agentsHistoryLength;

    public int globalDetectors = 2;


    /**
     * @param eventQueue          Interface to EventQueue. This is a reference to the method addEvent(Event e) in the class EventQueue. This allows any component in the simulation to add events.
     * @param rand                Interface to the Random object created in the Simulation class.
     * @param timeProvider        Interface to EventQueue. This is a reference to the method int getTime() in the class EventQueue.
     * @param IDProvider          Used by the Transaction Generator to ensure no transactions have the same ID
     * @param pageNumProvider     Gets a random page to give to a transaction during transaction generation
     * @param maxActiveTrans
     * @param arrivalRate
     * @param log
     * @param stats
     * @param incurOverhead
     * @param agentsHistoryLength
     * @param numPages
     */
    public SimParams(Supplier<Double> transGeneratorRand,Supplier<Double> transManagerRand, Consumer<Event> eventQueue, Supplier<Double> rand, Supplier<Integer> timeProvider, Supplier<Integer> IDProvider,
                     Supplier<Integer> pageNumProvider, int maxActiveTrans, int arrivalRate, Consumer<String> log, Statistics stats,
                     BiConsumer<Integer, Integer> incurOverhead, int agentsHistoryLength, double updateRate, int numPages) {
        this.transGeneratorRand = transGeneratorRand;
        this.transManagerRand = transManagerRand;
        this.eventQueue = eventQueue;
        this.rand = rand;
        this.timeProvider = timeProvider;
        this.IDProvider = IDProvider;
        this.pageNumProvider = pageNumProvider;
        this.maxActiveTrans = maxActiveTrans;
        this.log = log;
        this.stats = stats;
        overheadIncurer = incurOverhead;

        arrivalRateMean = arrivalRate;
        this.agentsHistoryLength = agentsHistoryLength;
        this.updateRate = updateRate;
        this.numPages = numPages;

        List<Integer> allServersList = new ArrayList<>();
        for (int i = 0; i < numberOfServers; i++)
            allServersList.add(i);
        this.allServersList = Collections.unmodifiableList(allServersList);
    }

    public List<Integer> getServersWithPage(int pageNum) {
        List<Integer> serverIDs = new ArrayList<>();
        serverToPageRange.keySet().forEach(serverID -> {
            Range range = serverToPageRange.get(serverID);
            if (range.contains(pageNum))
                serverIDs.add(serverID);
        });
        return serverIDs;
    }

    public int getNumTransPerServer() {
        return numTransPerServer;
    }

    public void setNumTransPerServer(int numTransPerServer) {
        this.numTransPerServer = numTransPerServer;
    }

    public void incurOverhead(int serverID, int overhead) {
        overIncurred += overhead;
//        if( Math.random()<0.01)
//            System.err.println("Not incuring overhead");
        overheadIncurer.accept(serverID,overhead);
    }

    void setDeadlockListener(Consumer<Deadlock> deadlockListener) {
        this.deadlockListener = deadlockListener;
    }

    public Consumer<Deadlock> getDeadlockListener() {
        return deadlockListener;
    }

    public PriorityProtocol getPp() {
        return pp;
    }

    public void setPp(PriorityProtocol pp) {
        this.pp = pp;
    }

    public int getDeadlockDetectInterval() {
        return searchInterval;
    }

    public void setDeadlockDetectInterval(int searchInterval) {
        this.searchInterval = searchInterval;
    }

    public BiConsumer<Deadlock, Integer> getDeadlockResolutionListener() {
        return deadlockResolutionListener;
    }

    public void setDeadlockResolutionListener(BiConsumer<Deadlock, Integer> deadlockResolutionListener) {
        this.deadlockResolutionListener = (deadlock, integer) -> {
            deadlockResolutionListener.accept(deadlock,integer);
            stats.addDeadlockResolved();
        };
    }

    public int getTime() {
        return timeProvider.get();
    }


    /**
     * Used for integrity checking
     */
    public boolean add(Server server) {
        return allServers.add(server);
    }

    /**
     * Used for integrity checking
     */
    public List<Transaction> getAllActiveTransactions() {
        List<Transaction> allTrans = new ArrayList<>();
        for (Server s : allServers) {

            allTrans.addAll(s.getTM().getActiveTransactions());
        }
        return allTrans;
    }

    /**
     * Used for integrity checking
     * Only gets master transactions, does not get cohorts
     */
    public Map<Integer, Transaction> getActiveTransactionsMap() {
        Map<Integer, Transaction> allTrans = new HashMap<>();

        for (Server s : allServers) {

            List<Transaction> transactions = s.getTM().getActiveTransactions();

            for (Transaction t : transactions)
                if (!(t instanceof CohortTransaction))
                    allTrans.put(t.getID(), t);
        }

        return allTrans;
    }


    /**
     * Used for integrity checking
     * Only gets master transactions, does not get cohorts
     */
    public Map<Integer, Transaction> getAllTransactionsMap() {
        Map<Integer, Transaction> allTrans = new HashMap<>();

        for (Server s : allServers) {

            List<Transaction> transactions = s.getTM().getAllMasterTransactions();

            for (Transaction t : transactions)
                if (!(t instanceof CohortTransaction))
                    allTrans.put(t.getID(), t);
        }

        return allTrans;
    }

    /**
     * Used for integrity checking
     */
    public List<Integer> getAllActiveTransactionIDs() {
        List<Transaction> allTrans = getAllActiveTransactions();
        List<Integer> allTransIDs = new ArrayList<>();

        for (Transaction t : allTrans)
            allTransIDs.add(t.getID());

        return allTransIDs;
    }

    public int getOverIncurred() {
        return overIncurred;
    }

    public double getUpdateRate() {
        return updateRate;
    }

    public Supplier<Double> getTransactionGeneratorRand() {
        return transGeneratorRand;
    }

    public int getNumPages() {
        return numPages;
    }

    public Supplier<Double> getTransManagerRand() {
        return transManagerRand;
    }
}
package simulator;

import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.WFG.Graph;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import stats.Statistics;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is used to pass parameters from the Main class to the Simulation class
 */
public class SimSetupParams {

    private final long SEED;
    private final int numPages;
    private final int maxActiveTrans;
    private final int numServers;
    private final String DDP;
    private final String DRP;
    private final Consumer<String> log;
    private final Statistics stats;
    private final int arrivalRate;
    private BiConsumer<Graph<WFGNode>, Integer> wfGraphConsumer;
    public final Supplier<Long> sleepTime;
    public final Consumer<Integer> timeUpdater;
    private Consumer<Deadlock> deadlockListener;
    private BiConsumer<Deadlock, Integer> deadlockResolutionListener;
    private String PP;
    private int detectInterval;
    private int agentsHistoryLength;
    private double updateRate;

    public SimSetupParams(long SEED, int numPages, int maxActiveTrans, int numServers, int arrivalRate, double updateRate, int detectInterval, String DDP, String DRP, String PP, Consumer<String> log, Statistics stats, Supplier<Long> sleepTime, Consumer<Integer> timeUpdater) {
        this.SEED = SEED;
        this.numPages = numPages;
        this.maxActiveTrans = maxActiveTrans;
        this.numServers = numServers;
        this.arrivalRate = arrivalRate;
        this.updateRate = updateRate;
        this.detectInterval = detectInterval;
        this.DDP = DDP;
        this.DRP = DRP;
        this.PP = PP;
        this.log = log;
        this.stats = stats;
        this.sleepTime = sleepTime;
        this.timeUpdater = timeUpdater;
    }

    public long getSEED() {
        return SEED;
    }

    public int getNumPages() {
        return numPages;
    }

    public int getMaxActiveTrans() {
        return maxActiveTrans;
    }

    public int getNumServers() {
        return numServers;
    }

    public String getDDP() {
        return DDP;
    }

    public String getDRP() {
        return DRP;
    }

    public Consumer<String> getLog() {
        return log;
    }

    public Statistics getStats() {
        return stats;
    }

    public int getArrivalRate() {
        return arrivalRate;
    }

    public BiConsumer<Graph<WFGNode>, Integer> getWfGraphConsumer() {
        return wfGraphConsumer;
    }

    public void setWfGraphConsumer(BiConsumer<Graph<WFGNode>,Integer> wfGraphConsumer) {
        this.wfGraphConsumer = wfGraphConsumer;
    }

    public void setDeadlockListener(Consumer<Deadlock> deadlockListener) {
        this.deadlockListener = deadlockListener;
    }

    public Consumer<Deadlock> getDeadlockListener() {
        return deadlockListener;
    }

    public String getPP() {
        return PP;
    }

    public int getDetectInterval() {
        return detectInterval;
    }

    public BiConsumer<Deadlock, Integer> getDeadlockResolutionListener() {
        return deadlockResolutionListener;
    }

    public void setDeadlockResolutionListener(BiConsumer<Deadlock, Integer> deadlockResolutionListener) {
        this.deadlockResolutionListener = deadlockResolutionListener;
    }

    public int getAgentsHistoryLength() {
        return agentsHistoryLength;
    }

    public void setAgentsHistoryLength(int agentsHistoryLength) {
        this.agentsHistoryLength = agentsHistoryLength;
    }

    public double getUpdateRate() {
        return updateRate;
    }
}
package simulator.server.transactionManager;

import simulator.SimParams;
import simulator.eventQueue.Event;
import simulator.server.Server;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TransactionGenerator {
    private int remainingTransactions;

    private final Supplier<Double> transGeneratorRand;
    private final Supplier<Integer> timeProvider;
    private final Supplier<Integer> IDProvider;
    private final Supplier<Integer> pageNumProvider;
    private final SimParams simParams;
    private final Consumer<Transaction> transConsumer;

    private final Server server;
    private final int serverID;
    private final Consumer<Event> eventQueue;

    public void start() {
        generateTransaction();
    }

    private void generateTransaction() {
        if (remainingTransactions-- == 0)
            return;

        int nextTransArriveTime = timeProvider.get() + getPoisson(simParams.arrivalRateMean, transGeneratorRand);

        int numReadPages = (int) ((8 * transGeneratorRand.get() + 2) * (1 - simParams.getUpdateRate()));
        int numWritePages = (int) ((8 * transGeneratorRand.get() + 2) * simParams.getUpdateRate());

        // Write pages have to be read AND written to disk. So they have 2*disk read write time
        // Read pages have to be only read from disk then processed.
        // The coefficient of 4 is arbitrary. It is there to give the transaction time to send messages, acquire locks, etc.
        int execTime = (numReadPages * 4 * (SimParams.processTime + SimParams.diskReadWriteTime)) + (numWritePages * 4 * (SimParams.processTime + (2 * SimParams.diskReadWriteTime)));

        // MANI: CHANGE!!!
        int SLACKTIME_COEFF = 2;
        int slackTime = execTime * SLACKTIME_COEFF;
        int deadline = timeProvider.get() + execTime + slackTime;

        Transaction t = new Transaction(IDProvider.get(), server.getID(), deadline);
        t.setSlackTime(slackTime);

        List<Integer> allReadPageNums = t.getAllReadPageNums();
        List<Integer> readPageNums = t.getReadPageNums();
        for (int i = 0; i < numReadPages; i++) {
            int pageNum = pageNumProvider.get();
            if (!readPageNums.contains(pageNum)) {
                allReadPageNums.add(pageNum);
                readPageNums.add(pageNum);
            } else {
                i--;
            }
        }

        List<Integer> allWritePageNums = t.getAllWritePageNums();
        List<Integer> writePageNums = t.getWritePageNums();

        for (int i = 0; i < numWritePages; i++) {
            int pageNum = pageNumProvider.get();
            if (!readPageNums.contains(pageNum) && !writePageNums.contains(pageNum)) {
                allWritePageNums.add(pageNum);
                writePageNums.add(pageNum);
            } else {
                i--;
            }
        }

        t.setWorkload(allReadPageNums.size() + allWritePageNums.size());
        t.setExecutionTime(execTime);

        transConsumer.accept(t);

        eventQueue.accept(new Event(nextTransArriveTime, serverID, this::generateTransaction, false));
    }

    public int getRemainingTransactions() {
        return remainingTransactions;
    }

    public TransactionGenerator(Server server, SimParams simParams, Consumer<Transaction> transactionConsumer) {
        this.server = server;
        this.eventQueue = simParams.eventQueue;
        this.transGeneratorRand = simParams.getTransactionGeneratorRand();
        this.timeProvider = simParams.timeProvider;
        IDProvider = simParams.IDProvider;
        this.pageNumProvider = () -> (int) (simParams.getNumPages()*transGeneratorRand.get());
        this.simParams = simParams;
        this.transConsumer = transactionConsumer;

        remainingTransactions = simParams.getNumTransPerServer();

        serverID = server.getID();
    }

    public static int getPoisson(double lambda, Supplier<Double> rand) {
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= rand.get();
        } while (p > L);

        return k - 1;
    }
}
package simulator.protocols.priority;

import simulator.server.lockManager.Lock;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;
import java.util.List;
import java.util.Random;

public class RandomPriority implements PriorityProtocol {

    @Override
    public Transaction getHighestPriorityTrans(List<Transaction> transactions) {
        Random rnd = new Random();
        return transactions.get(rnd.nextInt(transactions.size()));
    }

    @Override
    public int getTransPriority(List<TransInfo> transactions, int transID) {
        Random rnd = new Random();
        return rnd.nextInt(transactions.size());
    }

    @Override
    public Lock getHighestPriorityLock(List<Lock> locks) {
        Random rnd = new Random();
        return locks.get(rnd.nextInt(locks.size()));
    }
}

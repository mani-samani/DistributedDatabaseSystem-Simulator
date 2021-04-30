package simulator.protocols.priority;

import exceptions.WTFException;
import simulator.server.lockManager.Lock;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;

import java.util.Arrays;
import java.util.List;

public class EarliestDeadlineFirst implements PriorityProtocol {

    @Override
    public Transaction getHighestPriorityTrans(List<Transaction> transactions) {
        int lowestDeadline = Integer.MAX_VALUE;
        Transaction lowest = null;
        for (Transaction t : transactions)
            if (t.getDeadline() < lowestDeadline) {
                lowest = t;
                lowestDeadline = lowest.getDeadline();
            }
        return lowest;
    }

    @Override
    public int getTransPriority(List<TransInfo> transactions, int transID) {
        Object[] sortedArray = transactions.stream().sorted((t1, t2) -> Integer.compare(t2.getDeadline(), t1.getDeadline())).toArray();
        List sortedList = Arrays.asList(sortedArray);

        for (TransInfo ti : transactions)
            if (ti.getID() == transID)
                return (sortedList.indexOf(ti) + 1);

        throw new WTFException("Transaction " + transID + " is not in the list of priority.");
    }

    @Override
    public Lock getHighestPriorityLock(List<Lock> locks) {
        int lowestDeadline = Integer.MAX_VALUE;
        Lock lowest = null;
        for (Lock t : locks)
            if (t.getDeadline() < lowestDeadline) {
                lowest = t;
                lowestDeadline = lowest.getDeadline();
            }
        return lowest;
    }
}

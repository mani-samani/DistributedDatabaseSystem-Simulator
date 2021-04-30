package simulator.protocols.priority;

import exceptions.WTFException;
import simulator.server.lockManager.Lock;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;

import java.util.Arrays;
import java.util.List;

public class LeastSlackFirst implements PriorityProtocol {

    @Override
    public Transaction getHighestPriorityTrans(List<Transaction> transactions) {
        int lowestSlackTime = Integer.MAX_VALUE;
        Transaction lowest = null;
        for(Transaction t : transactions)
            if( t.getSlackTime() < lowestSlackTime ){
                lowest = t;
                lowestSlackTime = lowest.getSlackTime();
            }
        return lowest;
    }

    @Override
    public int getTransPriority(List<TransInfo> transactions, int transID) {
        Object[] sortedArray = transactions.stream().sorted((t1, t2) -> Integer.compare(t2.getSlackTime(), t1.getSlackTime())).toArray();
        List sortedList = Arrays.asList(sortedArray);

        for (TransInfo ti : transactions)
            if (ti.getID() == transID)
                return (sortedList.indexOf(ti) + 1);

        throw new WTFException("Transaction " + transID + " is not in the list of priority.");
    }

    @Override
    public Lock getHighestPriorityLock(List<Lock> locks) {
        Lock lowest = null;
        return lowest;
    }
}

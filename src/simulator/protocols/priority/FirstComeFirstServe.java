package simulator.protocols.priority;

import exceptions.WTFException;
import simulator.server.lockManager.Lock;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;

import java.util.Arrays;
import java.util.List;

public class FirstComeFirstServe implements PriorityProtocol {
    @Override
    public Transaction getHighestPriorityTrans(List<Transaction> transactions) {
        int lowestID = Integer.MAX_VALUE;
        Transaction lowest = null;
        for(Transaction t : transactions)
            if( t.getID() < lowestID ){
                lowest = t;
                lowestID = lowest.getID();
            }
        return lowest;
    }

    @Override
    public int getTransPriority(List<TransInfo> transactions, int transID) {
        Object[] sortedArray = transactions.stream().sorted((t1, t2) -> Integer.compare(t2.getID(), t1.getID())).toArray();
        List sortedList = Arrays.asList(sortedArray);

        for (TransInfo ti : transactions)
            if (ti.getID() == transID)
                return (sortedList.indexOf(ti) + 1);

        throw new WTFException("Transaction " + transID + " is not in the list of priority.");
    }

    @Override
    public Lock getHighestPriorityLock(List<Lock> locks) {
        int lowestID = Integer.MAX_VALUE;
        Lock lowest = null;
        for(Lock t : locks)
            if( t.getTransID() < lowestID ){
                lowest = t;
                lowestID = lowest.getTransID();
            }
        return lowest;
    }
}

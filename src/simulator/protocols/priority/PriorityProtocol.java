package simulator.protocols.priority;

import exceptions.WTFException;
import simulator.server.lockManager.Lock;
import simulator.server.transactionManager.TransInfo;
import simulator.server.transactionManager.Transaction;

import java.util.List;

public interface PriorityProtocol {

    Transaction getHighestPriorityTrans(List<Transaction> transactions);
    int getTransPriority(List<TransInfo> transactions, int transID);
    Lock getHighestPriorityLock(List<Lock> locks);

    static PriorityProtocol getPp(String pp) {
        switch(pp){
            case "FirstComeFirstServe": return new FirstComeFirstServe();
            case "EarliestDeadlineFirst": return new EarliestDeadlineFirst();
            case "LeastSlackFirst": return new LeastSlackFirst();
            case "RandomPriority": return new RandomPriority();
        }
        throw new WTFException("Priority Protocol not registered! add them in the PriorityProtocol class!");
    }
}

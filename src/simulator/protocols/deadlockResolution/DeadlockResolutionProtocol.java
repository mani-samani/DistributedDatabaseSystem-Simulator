package simulator.protocols.deadlockResolution;

import exceptions.WTFException;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.server.Server;
import simulator.server.network.Message;

import java.util.List;

public interface DeadlockResolutionProtocol {
    void resolveDeadlocks(Deadlock deadlock);
    void resolveMultiple(List<Deadlock> l);

    static DeadlockResolutionProtocol get(Server server, String drp) {
        switch (drp){
            case "AgentDeadlockResolutionProtocol": return new AgentDeadlockResolutionProtocol(server);
            case "FirstDeadlockResolution": return new FirstDeadlockResolution(server);
            case "PriorityDeadlockResolution": return new PriorityDeadlockResolution(server);
        }

        throw new WTFException("Deadlock Resolution Protocol has not been registered! add it here in the DeadlockResolutionProtocol class!");
    }

    void receiveMessage(Message msg);
}
package simulator.protocols.deadlockResolution;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.server.Server;
import simulator.server.network.Message;
import simulator.server.transactionManager.TransInfo;
import ui.Log;

import java.util.List;

public class FirstDeadlockResolution implements DeadlockResolutionProtocol {

    private final Server server;
    private final SimParams simParams;
    private final Log log;

    public FirstDeadlockResolution(Server server) {
        this.server = server;
        this.simParams = server.getSimParams();
        log = new Log(ServerProcess.DRP, server.getID(), simParams.timeProvider, simParams.log);
    }

    /**
     * Picks a random transaction that is at this server and aborts it
     */
    @Override
    public void resolveDeadlocks(Deadlock deadlock) {
        List<TransInfo> transactionsInDeadlock = deadlock.getTransactionsInvolved();
        TransInfo firstTrans = transactionsInDeadlock.get(0);

        if (simParams.agentBased) {
            server.getNIC().sendMessage(new Message(firstTrans.serverID, ServerProcess.DRP, "A:" + firstTrans.getID() + ":" + server.getID(), deadlock, firstTrans.getDeadline()));
            return;
        }

        if (firstTrans.serverID != server.getID())
            return;

        deadlock.setResolutionTime(simParams.getTime());
        simParams.getDeadlockResolutionListener().accept(deadlock, firstTrans.transID);
        server.getTM().abort(firstTrans.getID());
    }

    @Override
    public void resolveMultiple(List<Deadlock> l) {
        l.forEach(this::resolveDeadlocks);
    }

    @Override
    public void receiveMessage(Message msg) {
        String message = msg.getContents();
        String[] components = message.split(":");
        int transID = Integer.parseInt(components[1]);
        Deadlock deadlock = (Deadlock) msg.getObject();

        if (Log.isLoggingEnabled())
            log.log("FirstDeadlockResolution - Server " + components[2] + " told to abort transaction " + transID);

        deadlock.setResolutionTime(simParams.getTime());
        simParams.getDeadlockResolutionListener().accept(deadlock, transID);
        server.getTM().abort(transID);
    }
}
package simulator.protocols.deadlockDetection;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.Server;
import simulator.server.network.Message;
import ui.Log;

import java.util.List;
import java.util.function.Consumer;

/**
 * Created by Chris on 10/30/2016.
 */
public class ExampleDDP extends DeadlockDetectionProtocol {

    private final Log log ;

    /**
     * @param server           Obviously the server this protocol is at
     * @param simParams        A parameter object used to pass parameters and provide other utilities to the various components
     * @param resolver         This will pass the detected deadlock to the deadlock resolution protocol
     * @param overheadIncurer  This is used to tell the simulation how much overhead to incur because, this is dependent on how hard it was to detect the deadlock
     * @param deadlockListener This is used to pass the deadlocks to the GUI
     */
    public ExampleDDP(Server server, SimParams simParams, Consumer<List<Deadlock>> resolver, Consumer<Integer> overheadIncurer, Consumer<Deadlock> deadlockListener) {
        super(server, simParams, resolver, overheadIncurer, deadlockListener);

        simParams.usesWFG = true;
        log = new Log(ServerProcess.DDP,server.getID(),simParams.timeProvider, simParams.log);
    }

    @Override
    public void start() {
        eventQueue.accept(new Event(simParams.getTime()+simParams.getDeadlockDetectInterval(),serverID,this::runThisMethodEverySoOften));
    }


    public void runThisMethodEverySoOften(){


        //Do something to detect deadlocks

        //Send message to every other server
        for (int destServer = 0; destServer < simParams.numberOfServers; destServer++) {

            if(destServer != server.getID()){

                server.getNIC().sendMessage(new Message(destServer, ServerProcess.DDP, "The message to send to the other server" , simParams.getTime() ));
            }

        }




        //Post the event to run this method again
        start();
    }
}
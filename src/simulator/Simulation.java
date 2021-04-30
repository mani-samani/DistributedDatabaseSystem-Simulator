package simulator;

import simulator.eventQueue.EventQueue;
import simulator.protocols.priority.PriorityProtocol;
import simulator.server.Server;
import simulator.server.lockManager.Lock;
import simulator.server.lockManager.Range;
import stats.Statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Simulation {

    private final Random rand, transGeneratorRand;
    private final int numPages;
    private final EventQueue eventQueue;
    private final List<Server> servers = new ArrayList<>();
    private int nextTransID;
    private final SimParams simParams;

    public Simulation(SimSetupParams simSetupParams) {
        //Get parameters from setup param object
        this.rand = new Random(simSetupParams.getSEED());
        this.transGeneratorRand = new Random(simSetupParams.getSEED()/2);
        this.numPages = simSetupParams.getNumPages();

        eventQueue = new EventQueue(simSetupParams.sleepTime, simSetupParams.timeUpdater);


        //Create simParam object to give to each server, which is given to every component in the simulation
        simParams = new SimParams(transGeneratorRand::nextDouble, transGeneratorRand::nextDouble, eventQueue::addEvent, rand::nextDouble, eventQueue::getTime, this::getNextTransID,
                this::getRandPageNum, simSetupParams.getMaxActiveTrans(), simSetupParams.getArrivalRate(), simSetupParams.getLog(),
                simSetupParams.getStats(), eventQueue::incurOverhead, simSetupParams.getAgentsHistoryLength(), simSetupParams.getUpdateRate(), numPages);

        simParams.DDP = simSetupParams.getDDP();
        simParams.DRP = simSetupParams.getDRP();
        simParams.setPp(PriorityProtocol.getPp(simSetupParams.getPP()));
        simParams.setDeadlockListener(simSetupParams.getDeadlockListener());
        simParams.setDeadlockResolutionListener(simSetupParams.getDeadlockResolutionListener());
        simParams.setDeadlockDetectInterval(simSetupParams.getDetectInterval());


        //Calculate which servers get what pages.

        int numPagesPerServer = simSetupParams.getNumPages() / (simSetupParams.getNumServers() / 2);
        int minPage = 0;
        int maxPage = numPagesPerServer;

        for (int i = 0; i < simSetupParams.getNumServers(); i++) {
            simSetupParams.getLog().accept("Creating server " + i + " with page range: " + minPage + " to " + maxPage);
            Server s = new Server(simParams, i, new Range(minPage, maxPage - 1));
            servers.add(s);

            simParams.serverToPageRange.put(i, new Range(minPage, maxPage - 1));

            minPage += numPagesPerServer;
            maxPage += numPagesPerServer;

            if (minPage == simSetupParams.getNumPages()) {
                minPage = 0;
                maxPage = numPagesPerServer;
            }

            // Set the deadlock detection protocol's 'Graph Listener'. It is used to display the WFGs in the GUI.
            final int serverID = i;
            s.getDDP().setGraphListener((wfGraph, time) -> {
                wfGraph.setServerID(serverID);
                simSetupParams.getWfGraphConsumer().accept(wfGraph, time);
//                    eventQueue.stop();
            });
        }
    }

    private int getNextTransID() {
        return nextTransID++;
    }

    private int getRandPageNum() {
        return (int) (rand.nextDouble() * numPages);
    }

    public List<Server> getServers() {
        return servers;
    }

    /**
     *
     * @return an array with different result values
     */
    public Object[] start() {
        servers.forEach(Server::start);

        //Run through all events
        eventQueue.start();

        System.out.println("Sim Done");


        //Consistency Checks
        servers.forEach(server -> {
            //Check for still active transactions
            int activeTrans = server.getTM().getNumberActiveTrans();
            if (activeTrans != 0) {
                System.out.println("Server " + server.getID() + " has " + activeTrans + " active transactions still. They are: " + server.getTM().getActiveTransactions());
            }

            //Check to make sure all transactions have been created
            int remainingTrans = server.getTM().getTG().getRemainingTransactions();
            if (remainingTrans != -1)
                System.out.println("Server " + server.getID() + " has " + remainingTrans + " remaining transactions.");

            //Check to see if locks are still being held
            Map<Integer, List<Lock>> heldLocks = server.getLM().getHeldLocks();
            Range pageRange = simParams.serverToPageRange.get(server.getID());

            int numberOfLocksHeld = 0;
            for (int i = pageRange.getMin(); i < pageRange.getMax(); i++)
                numberOfLocksHeld += heldLocks.get(i).size();


            if (numberOfLocksHeld != 0) {
                System.out.println("Server " + server.getID() + " has " + numberOfLocksHeld + " held locks!");
                for (int i = pageRange.getMin(); i < pageRange.getMax(); i++)
                    if(!heldLocks.get(i).isEmpty() )
                        System.out.println("\tPage: " + i + "\t" + heldLocks.get(i));

            }
        });

        Statistics stats = simParams.stats;

        double PCOT = ((double) stats.getCompletedOnTime()) / (servers.size() * simParams.getNumTransPerServer());

        return new Object[]{PCOT, simParams.getOverIncurred(), simParams.messageOverhead};
    }

    public SimParams getSimParams() {
        return simParams;
    }
}
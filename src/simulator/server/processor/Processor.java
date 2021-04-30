package simulator.server.processor;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import ui.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class Processor {

    private final int serverID;
    private final SimParams simParams;
    private final Log log;

    private ProcessorJob activeProcessorJob;

    private Queue<ProcessorJob> processingJobs = new PriorityQueue<>();

    public Processor(int serverID, SimParams simParams) {
        this.serverID = serverID;

        this.simParams = simParams;
        log = new Log(ServerProcess.Processor, serverID, simParams.timeProvider, simParams.log);
    }

    private void tryToStartJob() {
        if (activeProcessorJob == null && !processingJobs.isEmpty()) {
            activeProcessorJob = processingJobs.remove();

            if (Log.isLoggingEnabled())
                log.log(activeProcessorJob.getTransID(), "Processing started for page " + activeProcessorJob.getPageNum());

            Event activeEvent = new Event(simParams.getTime() + SimParams.processTime, serverID, () -> {
                if (Log.isLoggingEnabled())
                    log.log(activeProcessorJob.getTransID(), "Processing completed for page " + activeProcessorJob.getPageNum());

                activeProcessorJob.getCompletedListener().accept(activeProcessorJob.getPageNum());

                simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));

                activeProcessorJob = null;
            });
            activeProcessorJob.setActiveEvent(activeEvent);
            simParams.eventQueue.accept(activeEvent);
        }
    }

    public void addJob(ProcessorJob pj) {
        if (Log.isLoggingEnabled())
            log.log(pj.getTransID(), "Processing job added for page " + pj.getPageNum());

        processingJobs.add(pj);
        simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));
    }

    public void abort(int transNum) {
        if (Log.isLoggingEnabled())
            log.log(transNum, "Aborting processing jobs");

        if (activeProcessorJob != null && activeProcessorJob.getTransID() == transNum) {
            activeProcessorJob.getActiveEvent().abort();
            activeProcessorJob = null;
        }

        List<ProcessorJob> toBeAbortedDiskJobs = new ArrayList<>();
        processingJobs.forEach(diskJob -> {
            if (diskJob.getTransID() == transNum) toBeAbortedDiskJobs.add(diskJob);
        });
        processingJobs.removeAll(toBeAbortedDiskJobs);

        simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));
    }
}
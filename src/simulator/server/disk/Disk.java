package simulator.server.disk;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.lockManager.Range;
import simulator.server.network.Message;
import ui.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Consumer;

public class Disk {
    //private static final int accessTime = 30;

    private final Log log;
    private final Consumer<Event> eventQueue;
    private final int serverID;
    private final SimParams simParams;
    private final Range pageRange;
    private final List<Page> pages = new ArrayList<>();
    private DiskJob activeDiskJob;
    private Queue<DiskJob> diskJobs = new PriorityQueue<>();

    public Disk(int serverID, SimParams simParams, Range pageRange) {
        this.serverID = serverID;
        this.simParams = simParams;
        log = new Log(ServerProcess.Disk, serverID, simParams.timeProvider, simParams.log);

        this.pageRange = pageRange;

        for (int i = pageRange.getMin(); i <= pageRange.getMax(); i++) {
            pages.add(new Page(i));
        }
        eventQueue = simParams.eventQueue;
    }

    private void tryToStartJob() {
        if (activeDiskJob == null && !diskJobs.isEmpty()) {
            activeDiskJob = diskJobs.remove();

            if (Log.isLoggingEnabled())
                log.log(activeDiskJob.getTransID(), "Starting disk job " + activeDiskJob);

            Event e = new Event(simParams.getTime() + SimParams.diskReadWriteTime, serverID, () -> {
                if (Log.isLoggingEnabled())
                    log.log(activeDiskJob.getTransID(), "Disk job completed " + activeDiskJob);

                activeDiskJob.getCompletedListener().accept(activeDiskJob.getPageNum());

                eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));

                activeDiskJob = null;
            });
            activeDiskJob.setActiveEvent(e);
            eventQueue.accept(e);
        }
    }

    public void addJob(DiskJob dj) {
        if (!pageRange.contains(dj.getPageNum()))
            throw new WTFException("Disk job page outside of range!");

        if (Log.isLoggingEnabled())
            log.log(dj.getTransID(), "Queueing disk job " + dj);

        diskJobs.add(dj);
        eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));
    }


    /**
     * W:<TransNum>:<PageNum>:<DeadLine>
     * <p>
     * ex
     * W:5:42:2:E Write page 42 exclusive lock page 5 for trans 42 (which is on server 2)
     */
    public void receiveMessage(Message message) {
        String msg = message.getContents();
        String[] msgParts = msg.split(":");

        int transID = Integer.parseInt(msgParts[1]);
        int pageNum = Integer.parseInt(msgParts[2]);
        int serverID = Integer.parseInt(msgParts[3]);
        int deadline = message.getDeadline();

        addJob(new DiskJob(transID, deadline, pageNum, pNum -> {
            if (Log.isLoggingEnabled())
                log.log(transID, "Write job completed: page " + pageNum);
            //Do nothing when the write job finishes.
        }));
    }

    public void abort(int transNum) {
        if (Log.isLoggingEnabled())
            log.log(transNum, "Clearing disk jobs");
        if (activeDiskJob != null && activeDiskJob.getTransID() == transNum) {
            activeDiskJob.getActiveEvent().abort();
            activeDiskJob = null;
        }

        List<DiskJob> toBeAbortedDiskJobs = new ArrayList<>();
        diskJobs.forEach(diskJob -> {
            if (diskJob.getTransID() == transNum) toBeAbortedDiskJobs.add(diskJob);
        });
        diskJobs.removeAll(toBeAbortedDiskJobs);

        eventQueue.accept(new Event(simParams.getTime() + 1, serverID, this::tryToStartJob));
    }

}
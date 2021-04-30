package simulator.server.disk;

import simulator.eventQueue.Event;

import java.util.function.Consumer;

public class DiskJob implements Comparable<DiskJob> {
    private final int transID, deadline, pageNum;

    private Event activeEvent;
    private Consumer<Integer> completedListener;

    public DiskJob(int transID, int deadline, int pageNum, Consumer<Integer> completedListener) {
        this.transID = transID;
        this.deadline = deadline;
        this.pageNum = pageNum;
        this.completedListener = completedListener;
    }

    public int getTransID() {
        return transID;
    }

    public int getDeadline() {
        return deadline;
    }

    public int getPageNum() {
        return pageNum;
    }

    public Event getActiveEvent() {
        return activeEvent;
    }

    public void setActiveEvent(Event activeEvent) {
        this.activeEvent = activeEvent;
    }

    @Override
    public int compareTo(DiskJob o) {
        if (deadline < o.deadline)
            return -1;
        else if (deadline > o.deadline)
            return 1;
        else
            return 0;
    }

    public Consumer<Integer> getCompletedListener() {
        return completedListener;
    }

    public void setCompletedListener(Consumer<Integer> completedListener) {
        this.completedListener = completedListener;
    }

    @Override
    public String toString() {
        return "DiskJob{" +
                "transID=" + transID +
                ", deadline=" + deadline +
                ", pageNum=" + pageNum +
                ", activeEvent=" + activeEvent +
                '}';
    }
}

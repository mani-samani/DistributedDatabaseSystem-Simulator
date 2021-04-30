package simulator.server.processor;

import simulator.eventQueue.Event;

import java.util.function.Consumer;

public class ProcessorJob implements Comparable<ProcessorJob> {
    private final int transID, deadline, pageNum;

    private Event activeEvent;
    private Consumer<Integer> completedListener;

    public ProcessorJob(int transID, int deadline, int pageNum, Consumer<Integer> completedListener) {
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
    public int compareTo(ProcessorJob o) {
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
}

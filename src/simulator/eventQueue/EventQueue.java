package simulator.eventQueue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EventQueue {

    private List<Event> queue = new LinkedList<>();
    private int time;
    private volatile boolean stop;
    private final Supplier<Long> sleepTime;
    private Consumer<Integer> timeUpdater;

    public EventQueue(Supplier<Long> sleepTime, Consumer<Integer> timeUpdater) {
        this.sleepTime = sleepTime;
        this.timeUpdater = timeUpdater;
    }

    public void addEvent(Event e) {
        if (queue.isEmpty())
            queue.add(e);
        else {
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).getTime() > e.getTime()) {
                    queue.add(i, e);
                    return;
                }
            }
            queue.add(e);
        }
    }

    public int getTime() {
        return time;
    }


    private boolean sleptThisTick = false;

    public void start() {
        System.out.println("** Simulation Starting **");

        while (!queue.isEmpty() && !stop && notOnlyRecurringEventsRemain()) {
            Event e = queue.remove(0);
            if (e.isAborted())
                continue;

            updateTime(e.getTime());
            e.getJob().run();

            if (!sleptThisTick) {
                long sleeptime = sleepTime.get();
                if (sleeptime > 0) {
                    try {
                        Thread.sleep(sleeptime);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                sleptThisTick = true;
            }


//            if( queue.size() > 3000 ){
//                stop = true;
//                queue.forEach(System.out::println);
//            }
        }

        if (stop)
            System.out.println("Stopped Simulation");
        else
            System.out.println("** Simulation Over at tick " + time + " **");
    }


    private boolean notOnlyRecurringEventsRemain() {
//        if( time > 200000 ){
//            System.out.println("Break!");
//        }

        for (Event e : queue)
            if (!e.isReoccurring())
                return true;

        return false;
    }

    public void incurOverhead(int serverID, int overhead) {
        List<Event> eventsAtThisServer = new ArrayList<>();
        Iterator<Event> it = queue.iterator();
        while (it.hasNext()) {
            Event e = it.next();

            if (e.getServerID() == serverID) {
                e.setTime(e.getTime() + overhead);
                eventsAtThisServer.add(e);
                it.remove();
            }
        }
        eventsAtThisServer.forEach(this::addEvent);
    }

    public void stop() {
        stop = true;
    }

    private void updateTime(int time) {
        if (time != this.time) {
            this.time = time;
            sleptThisTick = false;
            timeUpdater.accept(time);
        }
    }
}

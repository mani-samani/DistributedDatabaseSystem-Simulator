package simulator.eventQueue;

public class Event {

    private int time;
    private final Runnable job;
    private final boolean reoccurring;
    private boolean aborted;
    private final int serverID;

    public Event(int time, int serverID, Runnable job) {
        this(time, serverID, job, false);
    }

    public Event(int time, int serverID, Runnable job, boolean reoccurring) {
        this.time = time;
        this.serverID = serverID;
        this.job = job;
        this.reoccurring = reoccurring;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public Runnable getJob() {
        return job;
    }

    public boolean isReoccurring() {
        return reoccurring;
    }

    public void abort() {
        aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }

    public int getServerID() {
        return serverID;
    }

    @Override
    public String toString() {
        return "Event time: " + time + " job:" + job + " at server " + serverID;
    }
}

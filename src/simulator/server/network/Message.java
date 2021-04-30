package simulator.server.network;

import simulator.SimParams;
import simulator.enums.ServerProcess;

public class Message implements Comparable<Message> {

    public static final String OBJECT = "OBJECT";

    private final int destServerID;
    private final ServerProcess process;
    private final String contents;
    private final int deadline;
    private final Object object;

    private boolean reoccuring;
    private int size = 1;

    public Message(int destServerID, ServerProcess process, String contents, int deadline) {
        this.destServerID = destServerID;
        this.process = process;
        this.contents = contents;
        this.deadline = deadline;
        object = null;
    }

    public Message(int destServerID, ServerProcess process, String contents, Object object, int deadline) {
        this.destServerID = destServerID;
        this.process = process;
        this.object = object;
        this.deadline = deadline;
        this.contents = contents;
    }

    public Message(int destServerID, ServerProcess process, Object object, int deadline) {
        this.destServerID = destServerID;
        this.process = process;
        this.object = object;
        this.deadline = deadline;
        this.contents = OBJECT;

    }

    public int getDestServerID() {
        return destServerID;
    }

    /**
     * @return The process this message is destined for.
     */
    public ServerProcess getProcess() {
        return process;
    }

    /**
     * This is normally used for network communications. Sometimes though, an object is needs to be sent.
     *
     * @return a string normally in the form A:54:19 where A, 54, and 19 are specific to the ServerProcess this message is going to. See TransactionManagers receive message for clarity.
     */
    public String getContents() {
        return contents;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size > SimParams.Bandwidth) {
            System.out.println("Issue! Setting message size(" + size + ") to be larger than bandwidth(" + SimParams.Bandwidth + ")");
            size = SimParams.Bandwidth;
        }
//        System.out.println("Size: " + size);
        this.size = size;
    }

    @Override
    public String toString() {
        return "Message{" +
                "destServerID=" + destServerID +
                ", process=" + process +
                ", contents=" + contents +
                ", size=" + size +
                '}';
    }

    @Override
    public int compareTo(Message o) {
        return 0;
    }

    public int getDeadline() {
        return deadline;
    }

    public Object getObject() {
        return object;
    }

    public boolean isReoccuring() {
        return reoccuring;
    }

    public void setReoccuring(boolean reoccuring) {
        this.reoccuring = reoccuring;
    }
}
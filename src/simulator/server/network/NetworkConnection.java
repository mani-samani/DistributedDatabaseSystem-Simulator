package simulator.server.network;

import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.Server;
import ui.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Consumer;

public class NetworkConnection {
    private static final int bandwidth = SimParams.Bandwidth;
    private static final int latency = SimParams.latency;

    private final Log log;

    private final Consumer<Event> eventQueue;
    private final Consumer<Message> msgConsumer;
    private final SimParams simParams;
    private final Server src;
    private final Server dest;

    private int sizeOnTheWire = 0;
    private List<Message> onTheWire = new ArrayList<>();
    private Queue<Message> queue = new PriorityQueue<>();

    public NetworkConnection(SimParams simParams, Server src, Server dest) {
        this.simParams = simParams;
        log = new Log(ServerProcess.NetworkConnection, src.getID(), simParams.timeProvider, simParams.log);

        this.src = src;
        this.dest = dest;

        eventQueue = simParams.eventQueue;
        this.msgConsumer = msg -> dest.getNIC().receiveMessage(msg);
    }

    public void sendMessage(Message msg) {
        if (Log.isLoggingEnabled())
            log.log(src.getID() + ": Send message: " + msg);

        queue.add(msg);
        eventQueue.accept(new Event(simParams.getTime() + 1, src.getID(), this::checkForRoomForMessage, true));
    }

    private void messageArrives(Message msg) {
        if (Log.isLoggingEnabled())
            log.log(dest.getID() + ": Message arrives at dest: " + msg);

        onTheWire.remove(msg);
        sizeOnTheWire -= msg.getSize();

        msgConsumer.accept(msg);

        eventQueue.accept(new Event(simParams.getTime() + 1, dest.getID(), this::checkForRoomForMessage, true));
    }

    private void checkForRoomForMessage() {

        if (!queue.isEmpty() && (bandwidth - sizeOnTheWire) >= queue.peek().getSize()) {
            Message msg = queue.remove();
            onTheWire.add(msg);
            sizeOnTheWire += msg.getSize();
            eventQueue.accept(new Event(simParams.getTime() + latency, src.getID(), () -> messageArrives(msg), msg.isReoccuring()));
        } else {
            if (queue.isEmpty()) {
                if (Log.isLoggingEnabled())
                    log.log(src.getID() + ": Queue is empty");
            } else {
                if (Log.isLoggingEnabled())
                    log.log(src.getID() + ": No room on wire");
            }
        }
    }

    public Server getSrc() {
        return src;
    }

    public Server getDest() {
        return dest;
    }

    public void abort(int transNum) {
    }
}
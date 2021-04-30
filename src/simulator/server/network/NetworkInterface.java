package simulator.server.network;

import exceptions.WTFException;
import simulator.SimParams;
import simulator.enums.ServerProcess;
import simulator.eventQueue.Event;
import simulator.server.Server;
import ui.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkInterface {
    private final Log log;

    private final Map<Integer, List<NetworkConnection>> routingTable = new HashMap<>();
    private final List<NetworkConnection> connections = new ArrayList<>();
    private final Server server;
    private final int serverID;
    private final SimParams simParams;

    public NetworkInterface(Server server, SimParams simParams) {
        this.server = server;
        serverID = server.getID();
        this.simParams = simParams;
        log = new Log(ServerProcess.NetworkInterface, server.getID(), simParams.timeProvider, simParams.log);
    }

    public void addConnection(NetworkConnection connection) {
        connections.add(connection);

        List<NetworkConnection> routes = new ArrayList<>();
        routes.add(connection);
        routingTable.put(connection.getDest().getID(), routes);
    }

    public void sendMessage(Message message) {
        //If the message is destined for a process on the same server
        if (message.getDestServerID() == server.getID()) {
            simParams.eventQueue.accept(new Event(simParams.getTime() + 1, serverID, () -> receiveMessage(message), message.isReoccuring()));
        } else
            route(message).sendMessage(message);

        simParams.messageOverhead += message.getSize();
    }

    private NetworkConnection route(Message message) {
        List<NetworkConnection> ncs = routingTable.get(message.getDestServerID());
        return ncs.get((int) (simParams.rand.get() * ncs.size()));
    }

    public NetworkConnection getConnection(int destServID) {
        for (NetworkConnection nc : connections) {
            if (nc.getDest().getID() == destServID)
                return nc;
        }
        throw new WTFException(server.getID() + ": No connection to this server! : " + destServID);
    }

    public void addRoutingTableEntry(int destServID, List<NetworkConnection> networkConnections) {
        routingTable.put(destServID, networkConnections);
    }

// Removing the trans's messages will prevent them from being sent to abort its cohorts
//    public void abort(int transNum) {
//        if(Log.isLoggingEnabled()) log.log(transNum,"Removing any queued messages.");
//        connections.forEach(conn -> conn.abort(transNum));
//    }

    public void receiveMessage(Message msg) {
        //If the message arrived here but must be forwarded to another node
        if (msg.getDestServerID() != server.getID()) {
            sendMessage(msg);
        } else {
            if (Log.isLoggingEnabled())
                log.log("Message Arrives- " + msg);

            server.receiveMessage(msg);
        }
    }
}
package simulator.server.network;

import simulator.server.Server;

import java.util.Arrays;
import java.util.List;

public class HyperCube {

    public static void setup(List<Server> s) {
        addConnection(s.get(0), s.get(1));
        addConnection(s.get(0), s.get(2));
        addConnection(s.get(0), s.get(4));

        addConnection(s.get(1), s.get(3));
        addConnection(s.get(2), s.get(3));
        addConnection(s.get(2), s.get(6));

        addConnection(s.get(7), s.get(3));
        addConnection(s.get(7), s.get(6));
        addConnection(s.get(7), s.get(5));

        addConnection(s.get(4), s.get(5));
        addConnection(s.get(4), s.get(6));
        addConnection(s.get(1), s.get(5));

        buildRoutingTables(s);
    }


    private static void buildRoutingTables(List<Server> s) {
        Server serv = s.get(0);
        NetworkInterface nic = serv.getNIC();
        nic.addRoutingTableEntry(6, Arrays.asList(nic.getConnection(2), nic.getConnection(4)));
        nic.addRoutingTableEntry(5, Arrays.asList(nic.getConnection(1), nic.getConnection(4)));
        nic.addRoutingTableEntry(3, Arrays.asList(nic.getConnection(1), nic.getConnection(2)));
        nic.addRoutingTableEntry(7, Arrays.asList(nic.getConnection(1), nic.getConnection(2), nic.getConnection(4)));


        serv = s.get(1);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(4, Arrays.asList(nic.getConnection(0), nic.getConnection(5)));
        nic.addRoutingTableEntry(2, Arrays.asList(nic.getConnection(0), nic.getConnection(3)));
        nic.addRoutingTableEntry(7, Arrays.asList(nic.getConnection(3), nic.getConnection(5)));
        nic.addRoutingTableEntry(6, Arrays.asList(nic.getConnection(0), nic.getConnection(3), nic.getConnection(5)));


        serv = s.get(2);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(1, Arrays.asList(nic.getConnection(0), nic.getConnection(3)));
        nic.addRoutingTableEntry(4, Arrays.asList(nic.getConnection(0), nic.getConnection(6)));
        nic.addRoutingTableEntry(7, Arrays.asList(nic.getConnection(3), nic.getConnection(6)));
        nic.addRoutingTableEntry(5, Arrays.asList(nic.getConnection(0), nic.getConnection(3), nic.getConnection(6)));


        serv = s.get(3);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(0, Arrays.asList(nic.getConnection(1), nic.getConnection(2)));
        nic.addRoutingTableEntry(5, Arrays.asList(nic.getConnection(1), nic.getConnection(7)));
        nic.addRoutingTableEntry(6, Arrays.asList(nic.getConnection(2), nic.getConnection(7)));
        nic.addRoutingTableEntry(4, Arrays.asList(nic.getConnection(1), nic.getConnection(2), nic.getConnection(7)));


        serv = s.get(4);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(1, Arrays.asList(nic.getConnection(0), nic.getConnection(5)));
        nic.addRoutingTableEntry(2, Arrays.asList(nic.getConnection(0), nic.getConnection(6)));
        nic.addRoutingTableEntry(7, Arrays.asList(nic.getConnection(5), nic.getConnection(6)));
        nic.addRoutingTableEntry(3, Arrays.asList(nic.getConnection(0), nic.getConnection(5), nic.getConnection(6)));


        serv = s.get(5);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(0, Arrays.asList(nic.getConnection(1), nic.getConnection(4)));
        nic.addRoutingTableEntry(3, Arrays.asList(nic.getConnection(1), nic.getConnection(7)));
        nic.addRoutingTableEntry(6, Arrays.asList(nic.getConnection(4), nic.getConnection(7)));
        nic.addRoutingTableEntry(2, Arrays.asList(nic.getConnection(1), nic.getConnection(4), nic.getConnection(7)));


        serv = s.get(6);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(0, Arrays.asList(nic.getConnection(2), nic.getConnection(4)));
        nic.addRoutingTableEntry(3, Arrays.asList(nic.getConnection(2), nic.getConnection(7)));
        nic.addRoutingTableEntry(5, Arrays.asList(nic.getConnection(4), nic.getConnection(7)));
        nic.addRoutingTableEntry(1, Arrays.asList(nic.getConnection(2), nic.getConnection(4), nic.getConnection(7)));


        serv = s.get(7);
        nic = serv.getNIC();
        nic.addRoutingTableEntry(1, Arrays.asList(nic.getConnection(3), nic.getConnection(5)));
        nic.addRoutingTableEntry(2, Arrays.asList(nic.getConnection(3), nic.getConnection(6)));
        nic.addRoutingTableEntry(4, Arrays.asList(nic.getConnection(5), nic.getConnection(6)));
        nic.addRoutingTableEntry(0, Arrays.asList(nic.getConnection(3), nic.getConnection(5), nic.getConnection(6)));
    }


    private static void addConnection(Server s1, Server s2) {
        NetworkConnection oneToTwo = new NetworkConnection(s1.getSimParams(), s1, s2);
        NetworkConnection twoToOne = new NetworkConnection(s1.getSimParams(), s2, s1);

        s1.getNIC().addConnection(oneToTwo);
        s2.getNIC().addConnection(twoToOne);
    }
}

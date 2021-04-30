package ui;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import simulator.protocols.deadlockDetection.Deadlock;
import simulator.protocols.deadlockDetection.WFG.WFGNode;
import simulator.server.transactionManager.TransInfo;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DeadlockPanel extends JPanel {

    private final JLabel deadlocksCounter;
    private final JLabel deadlocksID;
    private final JLabel deadlocksTime;
    private final JLabel resolveTime;
    private final JLabel deadlocksServer;
    private final JLabel toString;

    private List<Deadlock> deadlocks = new ArrayList<>();
    private final JComboBox<Deadlock> deadlockJComboBox;

    private final mxGraph graph;
    private final Map<WFGNode,Object> nodesToGraphObj = new HashMap<>();
    private Map<Deadlock, List<Integer>> deadlockResolutions = new HashMap<>();

    public DeadlockPanel() {
        super(new BorderLayout());

        JPanel top = new JPanel();

        deadlocksCounter = new JLabel("Deadlocks: 0");

        top.add(deadlocksCounter);

        deadlockJComboBox = new JComboBox<>();
        deadlockJComboBox.addActionListener(e->{
            synchronized (deadlockJComboBox) {
                showDeadlock((Deadlock) deadlockJComboBox.getSelectedItem());
            }
        });
        top.add(deadlockJComboBox);

        deadlocksID = new JLabel("Deadlock ID: 0");
        top.add(deadlocksID);

        deadlocksTime = new JLabel("Found At Time: 0");
        top.add(deadlocksTime);

        resolveTime = new JLabel("Resolved At Time: 0");
        top.add(resolveTime);

        deadlocksServer = new JLabel("Found At Server: 0");
        top.add(deadlocksServer);

        toString = new JLabel("");
        top.add(toString);

        add(top, BorderLayout.NORTH);


        graph = new mxGraph();

        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setSize(1500,800);
        graphComponent.setMaximumSize(new Dimension(1500,800));
        graphComponent.setPreferredSize(new Dimension(1500,800));
        add(graphComponent,BorderLayout.CENTER);
    }

    public void showDeadlock(Deadlock deadlock ){

        deadlocksID.setText("  Deadlock ID: " + deadlock.getDeadlockID());
        deadlocksTime.setText("  Found At Time: " + deadlock.getDetectionTime());
        resolveTime.setText("  Resolved At Time: " + deadlock.getResolutionTime());
        deadlocksServer.setText("  Found At Server: " + deadlock.getServerID());
        toString.setText(deadlock.toLongString());


        Object parent = graph.getDefaultParent();

        Random rand = new Random(245684);

        int width = getWidth();
        int height = getHeight();

        graph.getModel().beginUpdate();
        try
        {
            nodesToGraphObj.values().forEach(graph.getModel()::remove);
            nodesToGraphObj.clear();

            List<String> nodes = new ArrayList<>();

            for( TransInfo tInfo : deadlock.getTransactionsInvolved() ) {
                String name = tInfo.getID()+ "";

                Object v1;

                if( deadlockResolutions.get(deadlock).contains(tInfo.getID()) ) {
                    //System.out.println("Coloring " + tInfo);
                    v1 = graph.insertVertex(parent, null, name, rand.nextDouble() * (width - 200) + 100, rand.nextDouble() * (height - 200) + 100, 40, 40, "fillColor=red");
                }
                else{
                    v1 = graph.insertVertex(parent, null, name, rand.nextDouble() * (width - 200) + 100, rand.nextDouble() * (height - 200) + 100, 40, 40);
                }

                nodes.add(name);
                nodesToGraphObj.put(tInfo,v1);
            }

            //List<String> deadlockEdges = new ArrayList<>();

            for( int i = 0 ; i < deadlock.getTransactionsInvolved().size()-1 ; ++i ) {
                TransInfo tInfo = deadlock.getTransactionsInvolved().get(i);
                TransInfo tInfo2 = deadlock.getTransactionsInvolved().get(i+1);

                graph.insertEdge(parent, null, "", nodesToGraphObj.get(tInfo), nodesToGraphObj.get(tInfo2));
            }

            TransInfo tInfo = deadlock.getTransactionsInvolved().get(deadlock.getTransactionsInvolved().size()-1);
            TransInfo tInfo2 = deadlock.getTransactionsInvolved().get(0);
            graph.insertEdge(parent, null, "", nodesToGraphObj.get(tInfo), nodesToGraphObj.get(tInfo2));

        }
        finally
        {
            graph.getModel().endUpdate();
        }
    }


    public void addDeadlock(Deadlock deadlock){
        if( !deadlockResolutions.containsKey(deadlock) )
            deadlockResolutions.put(deadlock,new ArrayList<>());

        synchronized (deadlockJComboBox) {
            deadlocks.add(deadlock);
            deadlockJComboBox.addItem(deadlock);
            deadlocksCounter.setText("Deadlocks: " + deadlocks.size());
        }
    }

    public void deadLockResolved(Deadlock deadlock, Integer transID) {
//        System.out.println("Deadlock resolved, dropping " + transID + " in " + deadlock.toLongString());
        deadlockResolutions.get(deadlock).add(transID);
    }
}
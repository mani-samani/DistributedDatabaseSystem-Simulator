package ui;

import simulator.enums.ServerProcess;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;

public class Output extends JPanel{

    public static final String COLON = ":";
    private static final String ALL = "All";
    public static final String EMPTY = "";
    public static final String SPAN_BR = "</span><br>";
    private final JTextPane txtArea;

    private final java.util.List<String> allMessages = new ArrayList<>();
    private final Map<String, java.util.List<String>> transIDToMessages = new HashMap<>();
    private final Map<String, java.util.List<String>> serverIDToMessages = new HashMap<>();
    private final Map<String, java.util.List<String>> processToMessages = new HashMap<>();

    private String[] backgroundColorsForServers;


    public Output() throws HeadlessException {
        super(new BorderLayout());
        txtArea = new JTextPane();
        txtArea.setEditable(false);

        backgroundColorsForServers = new String[8];
        backgroundColorsForServers[0] = "<span style=\"background-color:lightred\">";
        backgroundColorsForServers[1] = "<span style=\"background-color:lightblue\">";
        backgroundColorsForServers[2] = "<span style=\"background-color:lightorange\">";
        backgroundColorsForServers[3] = "<span style=\"background-color:lightyellow\">";
        backgroundColorsForServers[4] = "<span style=\"background-color:lightgreen\">";
        backgroundColorsForServers[5] = "<span style=\"background-color:lightpurple\">";
        backgroundColorsForServers[6] = "<span style=\"background-color:beige\">";
        backgroundColorsForServers[7] = "<span>";




        //txtArea.setContentType("text/html");
//        JPanel txtAreaPanel = new JPanel();
//        txtAreaPanel.add(txtArea);
        JScrollPane scrollPane = new JScrollPane(txtArea);
//        scrollPane.add();
        scrollPane.setSize(1400,750);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        JPanel topPanel = new JPanel();

        JLabel legend = new JLabel("<Time>:<NodeNum>:<Process>:<TransID>:<Message>");
        topPanel.add(legend);


        JComboBox<String> serverSelector = new JComboBox<>();
        serverSelector.addItem(ALL);
        for (int i = 0; i < 8; i++)
            serverSelector.addItem(""+i);
        serverSelector.setLightWeightPopupEnabled(false);

        JComboBox<String> processSelector = new JComboBox<>();
        processSelector.addItem(ALL);
        processSelector.addItem(ServerProcess.Server.toString());
        processSelector.addItem(ServerProcess.Disk.toString());
        processSelector.addItem(ServerProcess.DDP.toString());
        processSelector.addItem(ServerProcess.DRP.toString());
        processSelector.addItem(ServerProcess.NetworkConnection.toString());
        processSelector.addItem(ServerProcess.NetworkInterface.toString());
        processSelector.addItem(ServerProcess.LockManager.toString());
        processSelector.addItem(ServerProcess.TransactionManager.toString());
        processSelector.addItem(ServerProcess.Processor.toString());
        processSelector.setLightWeightPopupEnabled(false);

        JTextField transIDField = new JTextField("All",8);
        JTextField containsField = new JTextField("",15);

        ActionListener listener = e->{
            txtArea.setContentType("text/html");
            System.out.println("There are " + allMessages.size() + " log messages");

            txtArea.setText("<html>");
            StringBuilder sb = new StringBuilder();
            synchronized (allMessages) {
                for (String msg : allMessages) {
                    String style = EMPTY;

                    String[] split = msg.split(COLON);
                    if (split.length > 4) {
                        String selectedServer = (String) serverSelector.getSelectedItem();
                        if (!ALL.equals(selectedServer)) {
                            if (!split[1].equals(selectedServer))
                                continue;

                        }
                        style = getBackgroundColorForServer(split[1]);

                        String selectedProcess = (String) processSelector.getSelectedItem();
                        if (!ALL.equals(selectedProcess)) {
                            if (!split[2].equals(selectedProcess))
                                continue;
                        }

                        String selectedTrans = transIDField.getText();
                        if (!ALL.equals(selectedTrans)) {
                            if (!split[3].equals(selectedTrans))
                                continue;
                        }

                        String txt = containsField.getText();
                        if (!ALL.equals(txt)) {
                            if (!split[4].contains(txt))
                                continue;
                        }
                    }
                    if (sb.length() > 200000) {
                        sb.append("<b>     Too many messages to render efficiently, apply more filters!</b>");
                        break;
                    }
                    sb.append(style).append(msg).append(SPAN_BR);//'\n');//
                }
            }
            txtArea.setText("<html>" + sb.toString());//+"</html>");
        };

        serverSelector.addActionListener(listener);
        processSelector.addActionListener(listener);
        transIDField.addActionListener(listener);
        containsField.addActionListener(listener);

        JLabel servSelLabel = new JLabel("Server: ");
        topPanel.add(servSelLabel);
        topPanel.add(serverSelector);

        JLabel procSelLabel = new JLabel("Process: ");
        topPanel.add(procSelLabel);
        topPanel.add(processSelector);

        JLabel transIDLabel = new JLabel("Trans ID: ");
        topPanel.add(transIDLabel);
        topPanel.add(transIDField);

        JLabel containsLabel = new JLabel("  Contains: ");
        topPanel.add(containsLabel);
        topPanel.add(containsField);


        JLabel selectFilter = new JLabel("Select a filter to show data!");
        topPanel.add(selectFilter);

        add(topPanel,BorderLayout.NORTH);

    }

    private String getBackgroundColorForServer(String serverID) {
        try{
            return backgroundColorsForServers[Integer.parseInt(serverID)];
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return "<span>";
    }

    public void log(String s){
        synchronized (allMessages) {
            allMessages.add(s);
        }

        String[] split = s.split(COLON);

        if(split.length>4) {
            if (!serverIDToMessages.containsKey(split[1]))
                serverIDToMessages.put(split[1], new ArrayList<>());
            serverIDToMessages.get(split[1]).add(s);

            if (!processToMessages.containsKey(split[2]))
                processToMessages.put(split[2], new ArrayList<>());
            processToMessages.get(split[2]).add(s);

            if (!split[3].isEmpty()) {
                if (!transIDToMessages.containsKey(split[3]))
                    transIDToMessages.put(split[3], new ArrayList<>());
                transIDToMessages.get(split[3]).add(s);
            }
        }

//        txtArea.append(s+'\n');
    }
}
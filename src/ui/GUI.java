package ui;

import javax.swing.*;
import java.awt.*;

public class GUI extends JFrame{
    private final JTabbedPane tPane;
    private long sleepTime = 0;
    private final JLabel timeLabel = new JLabel("Time: 0");

    public GUI()  {
        JPanel content = new JPanel(new BorderLayout());
        setContentPane(content);

        JPanel top = new JPanel();

        top.add(timeLabel);

        JTextField sleepTimeField = new JTextField(""+sleepTime, 10);
        sleepTimeField.addActionListener(e->{
            sleepTime = Long.parseLong(sleepTimeField.getText());
        });
        top.add(sleepTimeField);
        content.add(top,BorderLayout.NORTH);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        tPane = new JTabbedPane();
        content.add(tPane,BorderLayout.CENTER);


        setSize(1800,1000);

        setVisible(true);
    }

    public void add(JPanel panel, String tabName){
        tPane.add(tabName,panel);
    }

    public long getSleepTime(){
        return sleepTime;
    }

    public void updateTime(int time) {
        timeLabel.setText("Time: " + time);
    }
}

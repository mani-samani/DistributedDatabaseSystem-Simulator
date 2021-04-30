package simulator.protocols.deadlockDetection.ChandyMisraHaas;

import java.awt.*;

public class ProbeMessage {

    private int initiator;
    private int sender;
    private int recipient;

    private final Color color = Color.ORANGE;

    public ProbeMessage(int initiator, int sender, int recipient) {
        this.initiator = initiator;
        this.sender = sender;
        this.recipient = recipient;
    }

    public int getInitiator() {
        return initiator;
    }

    public int getSender() {
        return sender;
    }

    public int getRecipient() {
        return recipient;
    }

    @Override
    public String toString() {
        return "(" + getInitiator() + "," + getSender() + "," + getRecipient() + ")";
    }

}

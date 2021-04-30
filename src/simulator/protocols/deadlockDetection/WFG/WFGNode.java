package simulator.protocols.deadlockDetection.WFG;

public interface WFGNode extends Comparable {

    int getID();

    default int compareTo(Object o) {
        if(this == o) {
            return 0;
        }
        if(o instanceof WFGNode) {
            WFGNode rt = (WFGNode) o;
            if( getID() < rt.getID() )
                return 1;
            else if( getID() > rt.getID() )
                return -1;
            return 0;
        } else {
            throw new IllegalStateException("tried to compare " + this + " to " + o);
        }
    }
}

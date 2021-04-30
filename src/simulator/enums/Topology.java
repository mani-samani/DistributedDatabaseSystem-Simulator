package simulator.enums;

public enum Topology {
    HyperCube, FullyConnected;

    public static Topology fromString(String s) {
        switch (s){
            default:
            case "HyperCube": return HyperCube;
            case "FullyConnected": return FullyConnected;
        }
    }
}

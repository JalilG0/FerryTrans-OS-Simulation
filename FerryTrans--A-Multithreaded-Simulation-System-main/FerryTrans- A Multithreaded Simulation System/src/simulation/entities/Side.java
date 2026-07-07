package simulation.entities;

public enum Side {
    MAINLAND,
    ISLAND;
    
    public Side getOpposite() {
        return this == MAINLAND ? ISLAND : MAINLAND;
    }
}

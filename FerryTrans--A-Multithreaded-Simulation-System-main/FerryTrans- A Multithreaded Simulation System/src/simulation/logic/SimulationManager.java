package simulation.logic;

import simulation.entities.Side;
import simulation.entities.Port;
import simulation.entities.Ferry;
import simulation.config.SimulationConfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SimulationManager {
    private static Map<Side, Port> ports = new EnumMap<>(Side.class);
    private static Ferry ferry;
    
    private static CountDownLatch vehicleCompletionLatch = new CountDownLatch(SimulationConfig.TOTAL_CARS + SimulationConfig.TOTAL_MINIBUSES + SimulationConfig.TOTAL_TRUCKS);

    static {
        ports.put(Side.MAINLAND, new Port(Side.MAINLAND));
        ports.put(Side.ISLAND, new Port(Side.ISLAND));
    }
    
    public static void reset() {
        ports.clear();
        ports.put(Side.MAINLAND, new Port(Side.MAINLAND));
        ports.put(Side.ISLAND, new Port(Side.ISLAND));
        ferry = null;
        vehicleCompletionLatch = new CountDownLatch(SimulationConfig.TOTAL_CARS + SimulationConfig.TOTAL_MINIBUSES + SimulationConfig.TOTAL_TRUCKS);
    }

    public static Port getPort(Side side) {
        return ports.get(side);
    }
    
    public static void setFerry(Ferry f) {
        ferry = f;
    }
    
    public static Ferry getFerry() {
        return ferry;
    }
    
    public static void markVehicleCompleted() {
        vehicleCompletionLatch.countDown();
    }
    
    public static void awaitAllVehicles() throws InterruptedException {
        vehicleCompletionLatch.await();
    }
}

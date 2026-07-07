package simulation.main;

import simulation.entities.Ferry;
import simulation.entities.Vehicle;
import simulation.logic.SimulationManager;
import simulation.entities.VehicleType;
import simulation.config.SimulationConfig;
import simulation.util.StatisticsManager;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static Thread masterSimulationThread;
    private static Ferry ferry;
    private static List<Thread> vehicleThreads = new ArrayList<>();
    
    public static synchronized void resetAndStart() {
        if (masterSimulationThread != null && masterSimulationThread.isAlive()) {
            if (ferry != null) ferry.terminate();
            for (Thread t : vehicleThreads) {
                if (t != null) t.interrupt();
            }
            masterSimulationThread.interrupt();
            try {
                masterSimulationThread.join(2000);
            } catch (InterruptedException e) {}
        }
        
        SimulationManager.reset();
        StatisticsManager.reset();
        
        masterSimulationThread = new Thread(() -> runSimulation(), "Master-Simulation-Thread");
        masterSimulationThread.start();
    }
    
    public static synchronized void terminateAll() {
        if (masterSimulationThread != null && masterSimulationThread.isAlive()) {
            if (ferry != null) ferry.terminate();
            for (Thread t : vehicleThreads) {
                if (t != null) t.interrupt();
            }
            masterSimulationThread.interrupt();
            try {
                masterSimulationThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        StatisticsManager.log("Simulation cleanly terminated via STOP.");
        System.exit(0);
    }
    
    private static void runSimulation() {
        StatisticsManager.log("Starting Ferry Trans Simulation...");
        
        ferry = new Ferry();
        Thread ferryThread = new Thread(ferry, "Ferry-Thread");
        ferryThread.start();
        
        vehicleThreads = new ArrayList<>();
        
        for (int i = 0; i < SimulationConfig.TOTAL_CARS; i++) {
            Thread t = new Thread(new Vehicle(VehicleType.CAR), "Car-" + (i + 1));
            vehicleThreads.add(t);
            t.start();
        }
        
        for (int i = 0; i < SimulationConfig.TOTAL_MINIBUSES; i++) {
            Thread t = new Thread(new Vehicle(VehicleType.MINIBUS), "Minibus-" + (i + 1));
            vehicleThreads.add(t);
            t.start();
        }
        
        for (int i = 0; i < SimulationConfig.TOTAL_TRUCKS; i++) {
            Thread t = new Thread(new Vehicle(VehicleType.TRUCK), "Truck-" + (i + 1));
            vehicleThreads.add(t);
            t.start();
        }
        
        try {
            SimulationManager.awaitAllVehicles();
            
            // Dynamically calculate the total number of vehicles
            int totalVehicles = SimulationConfig.TOTAL_CARS
                    + SimulationConfig.TOTAL_MINIBUSES
                    + SimulationConfig.TOTAL_TRUCKS;
            
            StatisticsManager.log(
                    "\n--- All " + totalVehicles +
                    " vehicles have completed their round trips! ---"
            );
            
            ferry.terminate();
            
            for (Thread t : vehicleThreads) {
                t.join();
            }
            
            ferryThread.join();
            
            StatisticsManager.log(
                    "Simulation fully terminated successfully with zero orphan threads."
            );
            
            StatisticsManager.printFinalStatistics();
            
            if (simulation.gui.SimulationGUI.getInstance() != null) {
                // Grace period: let GUI snapshotTimer drain final animations
                // before freezing the chart (4 cycles of 800ms each = ~3.2 seconds)
                try { Thread.sleep(4000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                simulation.gui.SimulationGUI.getInstance().updateStats(StatisticsManager.getFinalStats());
            }
            
        } catch (InterruptedException e) {
            StatisticsManager.log("Simulation run was interrupted for restart.");
            ferry.terminate();
            for (Thread t : vehicleThreads) t.interrupt();
        }
    }

    public static void main(String[] args) {
        resetAndStart();
    }
}
package simulation.entities;

import simulation.config.SimulationConfig;
import simulation.util.StatisticsManager;
import simulation.logic.SimulationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ThreadLocalRandom;

public class Ferry implements Runnable {
    private Side currentSide;
    private int currentLoad;
    private final List<Vehicle> onboardVehicles;
    private final List<Vehicle> boardingVehicles = new ArrayList<>();

    private volatile boolean isRunning = true;
    private volatile boolean isCrossing = false;

    private final ReentrantLock ferryLock = new ReentrantLock();
    private final Condition vehicleArrived = ferryLock.newCondition();

    public int getCurrentLoad() {
        return currentLoad;
    }

    public Side getCurrentSide() {
        return currentSide;
    }

    public List<Vehicle> getOnboardVehicles() {
        return onboardVehicles;
    }
    
    public List<Vehicle> getBoardingVehicles() {
        return boardingVehicles;
    }

    public boolean isCrossing() {
        return isCrossing;
    }

    public Ferry() {
        // [BUGFIX RESOLVED]: Enforces system randomization rule. Selected starting pier
        // is randomized.
        this.currentSide = ThreadLocalRandom.current().nextBoolean() ? Side.MAINLAND : Side.ISLAND;
        this.currentLoad = 0;
        this.onboardVehicles = new ArrayList<>();
    }

    public void unloadVehicles() {
        if (onboardVehicles.isEmpty())
            return;

        // [CRITICAL POLICY]: Unloading sequence completes entirely before boarding
        // routines can access critical state
        StatisticsManager.log(
                "ARRIVAL: Ferry arrived at " + currentSide + ". Unloading " + onboardVehicles.size() + " vehicles...");
        for (Vehicle v : onboardVehicles) {
            StatisticsManager.log("UNLOADING: Vehicle " + v.getId() + " disembarked at " + currentSide);
            v.notifyDisembark();
            // Pace unloading so GUI can animate each vehicle card departing
            try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        onboardVehicles.clear();
        currentLoad = 0;
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
    }

    public void notifyVehicleArrived() {
        ferryLock.lock();
        try {
            vehicleArrived.signal();
        } finally {
            ferryLock.unlock();
        }
    }

    public void terminate() {
        isRunning = false;
        ferryLock.lock();
        try {
            vehicleArrived.signalAll();
        } finally {
            ferryLock.unlock();
        }
    }

    public void loadVehicles() {
        Port currentPort = SimulationManager.getPort(currentSide);
        WaitingArea waitingArea = currentPort.getWaitingArea();

        while (isRunning) {
            BoardingTicket nextTicket = waitingArea.peekNextVehicle();
            if (nextTicket == null) {
                break;
            }

            Vehicle nextVehicle = nextTicket.getVehicle();
            int requiredSpace = nextVehicle.getType().getSize();

            if (currentLoad + requiredSpace <= SimulationConfig.FERRY_CAPACITY) {
                waitingArea.getNextVehicle();
                boardingVehicles.add(nextVehicle);
                
                // --- VISUAL/LOGICAL THROTTLE ---
                StatisticsManager.log("BOARDING WAIT: Vehicle " + nextVehicle.getId() + " is boarding (sliding to ferry)...");
                if (simulation.gui.SimulationGUI.getInstance() != null) {
                    simulation.gui.SimulationGUI.getInstance().updateGUI(); // Force UI snapshot
                }
                
                boolean wasLocked = ferryLock.isHeldByCurrentThread();
                if (wasLocked) ferryLock.unlock();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (wasLocked) ferryLock.lock();
                
                boardingVehicles.remove(nextVehicle);
                if (!isRunning) break;
                // -----------------------------

                currentLoad += requiredSpace;
                onboardVehicles.add(nextVehicle);

                // Track boarding steps with both type properties and priority levels
                StatisticsManager.log("BOARDING: Ferry loaded Vehicle " + nextVehicle.getId() + " ("
                        + nextVehicle.getType() + " - " + nextVehicle.getPriority() + "). Load: " + currentLoad + "/"
                        + SimulationConfig.FERRY_CAPACITY);

                nextTicket.allowBoarding();
            } else {
                // Enforce strict FIFO behavior - Overtaking blocked if the next queued vehicle
                // exceeds remaining capacity
                StatisticsManager.log("CRITICAL CHECK: Vehicle " + nextVehicle.getId()
                        + " cannot fit. Boarding STOPS for this trip.");
                break;
            }
        }
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
    }

    @Override
    public void run() {
        SimulationManager.setFerry(this);

        while (isRunning) {
            StatisticsManager.log("\n--- FERRY DOCKED AT " + currentSide + " ---");
            unloadVehicles();

            if (!isRunning && currentLoad == 0)
                break;

            long deadline = System.currentTimeMillis() + SimulationConfig.MAX_WAIT_TIME_MS;

            ferryLock.lock();
            try {
                while (isRunning) {
                    loadVehicles();

                    if (currentLoad == SimulationConfig.FERRY_CAPACITY) {
                        StatisticsManager.log("DEPARTURE CONDITION MET: Ferry is FULL (20 units).");
                        break;
                    }

                    Port currentPort = SimulationManager.getPort(currentSide);
                    if (!currentPort.getWaitingArea().isEmpty()) {
                        StatisticsManager.log("DEPARTURE CONDITION MET: Next waiting vehicle cannot fit.");
                        break;
                    }

                    long remainingTime = deadline - System.currentTimeMillis();
                    if (remainingTime <= 0) {
                        if (currentLoad > 0) {
                            StatisticsManager
                                    .log("DEPARTURE CONDITION MET: Max wait time reached with load " + currentLoad);
                            break;
                        } else {
                            Port otherPort = SimulationManager.getPort(currentSide.getOpposite());
                            if (!otherPort.getWaitingArea().isEmpty()) {
                                StatisticsManager.log(
                                        "DEPARTURE CONDITION MET: Empty, but other side has vehicles (Starvation Avoidance).");
                                break;
                            } else {
                                // Suspend ferry execution safely to prevent CPU heavy busy-waiting conditions
                                StatisticsManager.log("WAIT: Ferry is empty and no vehicles waiting. Suspending...");
                                vehicleArrived.await();
                                if (!isRunning)
                                    break;
                                deadline = System.currentTimeMillis() + SimulationConfig.MAX_WAIT_TIME_MS;
                                continue;
                            }
                        }
                    }

                    vehicleArrived.await(remainingTime, TimeUnit.MILLISECONDS);
                    if (!isRunning)
                        break;
                }
            } catch (InterruptedException e) {
                StatisticsManager.log("INTERRUPT: Ferry interrupted.");
                Thread.currentThread().interrupt();
                break;
            } finally {
                ferryLock.unlock();
            }

            if (!isRunning && currentLoad == 0)
                break;

            crossRiver();
        }
        StatisticsManager.log("SHUTDOWN: Ferry thread terminating cleanly.");
    }

    private void crossRiver() {
        StatisticsManager.log("DEPARTURE: Ferry departing " + currentSide + " and crossing the river...");
        StatisticsManager.recordTrip(currentLoad);
        isCrossing = true;
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
        try {
            Thread.sleep(3000); // 3s matches GUI Lerp animation duration for full visual crossing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        currentSide = currentSide.getOpposite();
        isCrossing = false;
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
    }
}
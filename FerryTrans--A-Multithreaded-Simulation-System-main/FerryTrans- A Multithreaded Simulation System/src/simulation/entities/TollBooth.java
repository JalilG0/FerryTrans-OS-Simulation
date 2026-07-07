package simulation.entities;

import simulation.util.StatisticsManager;

public class TollBooth {
    private final String id;

    private volatile boolean isBusy = false;

    public TollBooth(String id) {
        this.id = id;
    }

    public boolean isBusy() {
        return isBusy;
    }

    public void processVehicle(Vehicle vehicle) throws InterruptedException {
        StatisticsManager.log("TOLL: Vehicle " + vehicle.getId() + " (" + vehicle.getType() + ") entering " + id);
        isBusy = true;
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
        Thread.sleep(50); // Simulating toll payment and barrier processing
        isBusy = false;
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
        StatisticsManager.log("TOLL: Vehicle " + vehicle.getId() + " cleared " + id);
    }
}

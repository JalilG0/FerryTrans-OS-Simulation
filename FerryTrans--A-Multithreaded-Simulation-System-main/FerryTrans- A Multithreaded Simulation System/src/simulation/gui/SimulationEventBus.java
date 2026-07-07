package simulation.gui;

import javax.swing.SwingUtilities;

public class SimulationEventBus {

    private static SimulationGUI activeGUI;

    public static void setGUI(SimulationGUI gui) {
        activeGUI = gui;
    }

    public static void publishLog(String message) {
        SwingUtilities.invokeLater(() -> {
            if (activeGUI != null) {
                activeGUI.appendConsole(message);
            }
        });
    }

    public static void updateQueue(String port, String htmlList) {
        // Obsolete
    }

    public static void updateFerryStatus(int currentLoad, String statusMsg) {
        // Obsolete
    }

    public static void updateStats(String stats) {
        SwingUtilities.invokeLater(() -> {
            if (activeGUI != null) {
                activeGUI.updateStats(stats);
            }
        });
    }
}

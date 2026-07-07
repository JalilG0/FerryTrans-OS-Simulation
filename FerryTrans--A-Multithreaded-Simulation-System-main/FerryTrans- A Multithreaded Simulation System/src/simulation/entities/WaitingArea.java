package simulation.entities;

import java.util.concurrent.PriorityBlockingQueue;

public class WaitingArea {
    // Thread-safe lock-free data structure designed for custom sorted elements
    private final PriorityBlockingQueue<BoardingTicket> queue;

    public WaitingArea(Side side) {
        this.queue = new PriorityBlockingQueue<>();
    }

    public void addVehicle(BoardingTicket ticket) {
        queue.add(ticket);
        if (simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
    }

    public BoardingTicket getNextVehicle() {
        BoardingTicket ticket = queue.poll();
        if (ticket != null && simulation.gui.SimulationGUI.getInstance() != null) {
            simulation.gui.SimulationGUI.getInstance().updateGUI();
        }
        return ticket;
    }

    public PriorityBlockingQueue<BoardingTicket> getQueue() {
        return queue;
    }

    public BoardingTicket peekNextVehicle() {
        return queue.peek();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}
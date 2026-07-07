package simulation.entities;

import simulation.util.StatisticsManager;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoardingTicket implements Comparable<BoardingTicket> {
    private final Vehicle vehicle;
    private final ReentrantLock lock;
    private final Condition boardingCondition;
    private boolean allowedToBoard = false;
    private final long enqueueTime;
    private int assignedTollBoothId = 0;

    public BoardingTicket(Vehicle vehicle) {
        this.vehicle = vehicle;
        this.lock = new ReentrantLock();
        this.boardingCondition = lock.newCondition();
        this.enqueueTime = System.nanoTime();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public long getEnqueueTime() {
        return enqueueTime;
    }

    public int getAssignedTollBoothId() {
        return assignedTollBoothId;
    }

    public void setAssignedTollBoothId(int id) {
        this.assignedTollBoothId = id;
    }

    public void awaitBoarding() throws InterruptedException {
        lock.lock();
        try {
            while (!allowedToBoard) {
                boardingCondition.await();
            }
            long waitTime = (System.nanoTime() - enqueueTime) / 1_000_000; // Convert to milliseconds
            StatisticsManager.recordWaitTime(waitTime);
        } finally {
            lock.unlock();
        }
    }

    public void allowBoarding() {
        lock.lock();
        try {
            allowedToBoard = true;
            boardingCondition.signal();
        } finally {
            lock.unlock();
        }
    }

    // [CRITICAL BONUS B LOGIC]: Priority-FIFO queue scheduling comparator algorithm
    @Override
    public int compareTo(BoardingTicket other) {
        // 1. Check priority status: EMERGENCY always overrides STANDARD vehicles
        if (this.vehicle.getPriority() != other.vehicle.getPriority()) {
            return this.vehicle.getPriority() == VehiclePriority.EMERGENCY ? -1 : 1;
        }
        // 2. If priorities match, fallback to strict arrival timestamp order (FIFO)
        int timeComparison = Long.compare(this.enqueueTime, other.enqueueTime);
        if (timeComparison != 0) {
            return timeComparison;
        }
        // 3. Failsafe: If nanoTime is identical, use Vehicle ID as the final tie-breaker
        return Integer.compare(this.vehicle.getId(), other.vehicle.getId());
    }
}
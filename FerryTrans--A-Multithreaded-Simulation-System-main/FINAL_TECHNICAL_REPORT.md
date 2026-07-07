# FerryTrans: A Multithreaded Transportation Simulation System
## Academic Technical Report

---

### 1. Abstract
The **FerryTrans** project is a highly optimized, multithreaded Operating Systems simulation developed in Java. The system accurately models a complex cross-river transportation network consisting of a centralized ferry, autonomous vehicle threads, and highly constrained toll plaza bottlenecks. The core objective of this system is to demonstrate advanced concurrency control, strict mutual exclusion, and thread safety under heavy, dynamic loads. This report details the major architectural design decisions and the specific synchronization primitives utilized to fulfill the project's stringent operating system requirements.

### 2. Architectural Design: Decoupled MVC & GUI Snapshotting
To ensure maximum backend performance and strictly abide by the Separation of Concerns (MVC) principle, the OS logic (Backend) was designed to be completely decoupled from the visualization layer (GUI). 

#### 2.1 Complete Backend Autonomy
The backend simulation—comprising the `Vehicle`, `Ferry`, and `TollBooth` thread logics—operates independently of any visual components. GUI interaction hooks within the backend are strictly gated by null-checks (e.g., `if (SimulationGUI.getInstance() != null)`). This guarantees that the core simulation does not rely on the frontend for state management or execution; if the GUI is entirely disabled, the multithreaded backend will continue to run flawlessly to completion without throwing `NullPointerException`s or deadlocking.

#### 2.2 Defensive Snapshotting for Thread Safety
A common vulnerability in multithreaded visualizers is the `ConcurrentModificationException`, which occurs when a rapid UI rendering loop attempts to read a collection that is simultaneously being mutated by backend threads. 
To resolve this, the GUI employs a **Defensive Snapshotting** technique. A dedicated `snapshotTimer` polls the backend every 800ms, making safe, defensive copies of highly volatile collections (such as the `PriorityBlockingQueue`). These copy operations are wrapped in safe `try-catch` blocks that swallow exceptions without blocking or locking the backend threads. The GUI's 60FPS `paintComponent` loop then runs mathematical interpolation (Lerping) over this static snapshot, visualizing state transitions smoothly while keeping the highly volatile backend queues entirely unblocked.

### 3. Concurrency Control and Mutual Exclusion
The toll booths on the mainland and island ports represent significant bottlenecks requiring strict mutual exclusion to prevent race conditions and starvation.

#### 3.1 Flawless Toll Booth Load Balancing
Each port contains exactly two toll booths. To manage access, the system utilizes a combination of Semaphores and Locks:
- **Plaza Entry (Semaphore):** A `Semaphore(2, true)` governs entry to the toll plaza. This strictly restricts the total number of vehicles in the plaza to two. The `true` flag enforces a strict fairness policy, guaranteeing that threads are granted permits in the exact order they requested them (FIFO), absolutely preventing thread starvation.
- **Dynamic Load Balancing (ReentrantLocks):** Once inside the plaza, the thread dynamically selects a booth. It utilizes an array of `ReentrantLock`s, first attempting to enter Booth 1 via a non-blocking `tryLock()`. If Booth 1 is occupied, the thread gracefully falls back to Booth 2 using a blocking `lock()` call. This maximizes throughput while guaranteeing absolute mutual exclusion at the physical booth level.

### 4. Advanced Queue Management
Following the toll booth processing, vehicles enter a waiting area prior to boarding the ferry. This area requires complex scheduling to accommodate varying vehicle priorities and arrival times.

#### 4.1 Priority-Based FIFO Ordering
The waiting area is governed by a thread-safe `PriorityBlockingQueue<BoardingTicket>`, fulfilling the requirement for custom, priority-based scheduling. The `compareTo` logic within the `BoardingTicket` processes elements in two tiers:
1. **Tier 1 (Priority Bypass):** `EMERGENCY` vehicles unconditionally bypass `STANDARD` vehicles in the queue, ensuring critical threads are serviced immediately.
2. **Tier 2 (Absolute FIFO):** For vehicles of the same priority tier, strict FIFO ordering is enforced based on exact arrival times. 

**Critical Determinism Upgrade:** Early iterations of the system suffered from unstable sorting due to the low resolution of `System.currentTimeMillis()` (~1-15ms accuracy), causing identical timestamps for vehicles arriving in rapid succession. This flaw was rectified by upgrading the temporal tracking to `System.nanoTime()`. The nanosecond precision guarantees absolute, deterministic FIFO ordering, entirely eliminating race conditions related to arbitrary tie-breaking and random line-cutting. As a final failsafe, the sequentially generated `Vehicle ID` is utilized as a tertiary tie-breaker.

### 5. Thread Lifecycle and Simulation Realism
To accurately model physical reality, the `Vehicle` threads implement distinct lifecycle phases utilizing `Thread.sleep()` to simulate delays outside of critical sections:
- **Toll Processing Delay:** Threads sleep to simulate the physical time taken to pay the toll.
- **Destination Stay Delay:** Upon successfully disembarking at the destination port, vehicle threads initiate a localized delay (2-5 seconds). This represents the vehicle fulfilling its real-world business objectives on the opposite shore before returning to re-queue for the return trip. 

### 6. Core Synchronization Policies
The `Ferry` thread acts as the central consumer, constantly polling the island and mainland queues. 

#### 6.1 Departure and Boarding Policies
The synchronization between the ferry and the waiting vehicles is orchestrated using `ReentrantLock`s and their associated `Condition` variables (`await()` and `signalAll()`). The ferry thread evaluates three departure conditions:
1. **Full Capacity:** The ferry departs immediately upon reaching maximum capacity (20).
2. **Next Won't Fit:** If the ferry is partially full, but the next vehicle in the queue is a Truck requiring more space than is currently available, the ferry departs early.
3. **Timeout Threshold:** If the queue is empty, the ferry implements a timed wait loop. If the specific idle timeout threshold is breached, the ferry departs partially full to prevent system stagnation.

#### 6.2 Deadlock Avoidance
The system architecture was explicitly designed to prevent deadlocks by systematically breaking Coffman conditions:
- **No Nested Locking:** Threads never attempt to acquire a secondary lock while holding a primary lock. A vehicle thread acquires the toll booth lock, processes, completely releases it, and only then proceeds to acquire the boarding lock.
- **Lock-Free Collections:** The utilization of `PriorityBlockingQueue` minimizes manual locking for queue insertions and deletions, isolating the remaining manual `ReentrantLock`s strictly to localized entity state transitions.

### 7. Conclusion
The FerryTrans simulation successfully implements all required Operating System paradigms. By carefully orchestrating Semaphores, ReentrantLocks, Condition variables, and nanosecond-precision queues, the system guarantees strict mutual exclusion, prevents starvation, and completely avoids deadlocks. The architectural decoupling of the backend logic from the high-fidelity graphical interface ensures a robust, production-ready multithreaded command center.

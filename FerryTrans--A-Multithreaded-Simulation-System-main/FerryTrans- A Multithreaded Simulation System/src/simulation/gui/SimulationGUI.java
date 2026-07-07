package simulation.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.util.LinkedList;
import java.util.Iterator;
import simulation.config.SimulationConfig;
import simulation.main.Main;

import simulation.logic.SimulationManager;
import simulation.entities.Ferry;
import simulation.entities.Vehicle;
import simulation.entities.Port;
import simulation.entities.TollBooth;
import simulation.entities.Side;
import simulation.entities.BoardingTicket;
import simulation.entities.VehiclePriority;

@SuppressWarnings("serial")
public final class SimulationGUI extends JFrame {

    private static SimulationGUI instance;

    // UI Colors
    private static final Color OBSIDIAN_NAVY = new Color(11, 15, 25);
    private static final Color NEON_CYAN = new Color(0, 210, 255);
    private static final Color NEON_RED = new Color(255, 49, 49);
    private static final Color NEON_GREEN = new Color(57, 255, 20);
    private static final Color GLASS_BG = new Color(30, 40, 60, 180);
    private static final Color DARK_OVERLAY = new Color(0, 0, 0, 150);

    // Cached Fonts
    private static final Font FONT_SEGOE_10 = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_SEGOE_11 = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SEGOE_SEMIBOLD_10 = new Font("Segoe UI Semibold", Font.PLAIN, 10);
    private static final Font FONT_SEGOE_SEMIBOLD_12 = new Font("Segoe UI Semibold", Font.PLAIN, 12);
    private static final Font FONT_SEGOE_SEMIBOLD_14 = new Font("Segoe UI Semibold", Font.PLAIN, 14);
    private static final Font FONT_SEGOE_SEMIBOLD_16 = new Font("Segoe UI Semibold", Font.PLAIN, 16);
    private static final Font FONT_CONSOLAS_12 = new Font("Consolas", Font.PLAIN, 12);

    // Cached Colors
    private static final Color COLOR_TOOLTIP_BG = new Color(11, 15, 25, 220);
    private static final Color COLOR_CYAN_50 = new Color(0, 210, 255, 50);
    private static final Color COLOR_CYAN_80 = new Color(0, 210, 255, 80);
    private static final Color COLOR_CYAN_120 = new Color(0, 210, 255, 120);
    private static final Color COLOR_WHITE_20 = new Color(255, 255, 255, 20);
    private static final Color COLOR_WHITE_50 = new Color(255, 255, 255, 50);
    private static final Color COLOR_DARK_GRAY = new Color(50, 50, 50);
    private static final Color COLOR_TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color COLOR_CYAN_0 = new Color(0, 210, 255, 0);
    private static final Color COLOR_FIELD_BG = new Color(20, 30, 50);
    private static final Color COLOR_PORT_BG_LIGHT = new Color(40, 50, 70);
    private static final Color COLOR_STANDARD_VEHICLE = new Color(150, 200, 255);
    private static final Color COLOR_RIVER_BG = new Color(15, 25, 40);
    private static final Color COLOR_RIVER_WAVE = new Color(0, 150, 255, 50);
    private static final Color COLOR_WATER_EDGE = new Color(40, 50, 65);
    private static final Color COLOR_WAKE_TRAIL = new Color(20, 30, 45);
    private static final Color COLOR_FERRY_BASE = new Color(10, 15, 25);

    // Cached Strokes
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_2 = new BasicStroke(2);
    private static final BasicStroke STROKE_4 = new BasicStroke(4);
    private static final BasicStroke STROKE_DASHED_5 = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 5f, 5f }, 0f);
    private static final BasicStroke STROKE_DASHED_3 = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 3f, 3f }, 0f);

    private JTextField txtCapacity, txtCars, txtMinibus, txtTruck;
    private transient List<String> consoleLogs = new ArrayList<>();
    private String statsText = "\n  Awaiting Simulation...\n  Data will populate at end.";

    private MainlandPanel mainlandPanel;
    private IslandPanel islandPanel;
    private RiverPanel riverPanel;
    private ConsolePanel consolePanel;
    private StatsPanel statsPanel;

    // Animation & Lerp variables
    private double visualFerryX = 10.0;
    private double targetFerryX = 10.0;
    private double visualFerryLoad = 0.0;
    private double targetFerryLoad = 0.0;
    private Side targetFerrySide = Side.MAINLAND;
    private boolean targetIsCrossing = false;
    private transient List<Vehicle> targetOnboardVehicles = new ArrayList<>();
    private transient List<Vehicle> targetBoardingVehicles = new ArrayList<>();
    private transient List<BoardingTicket> targetMainlandQueue = new ArrayList<>();
    private transient List<BoardingTicket> targetIslandQueue = new ArrayList<>();

    private JButton btnStart;

    class TollBoothUIState {
        boolean lastBusy = false;
        long transitionStartTime = 0;

        public void update(boolean currentBusy) {
            if (currentBusy != lastBusy) {
                lastBusy = currentBusy;
                transitionStartTime = System.currentTimeMillis();
            }
        }
    }

    private transient Map<String, TollBoothUIState> tollStates = new ConcurrentHashMap<>();
    private transient LinkedList<Integer> liveChartData = new LinkedList<>();
    private transient long lastChartUpdateTime = System.currentTimeMillis();

    class WakeParticle {
        double x, y;
        double age;
        double maxAge;
        double size;

        public WakeParticle(double x, double y) {
            this.x = x;
            this.y = y + (Math.random() * 10 - 5);
            this.age = 0;
            this.maxAge = 20 + Math.random() * 20;
            this.size = 20 + Math.random() * 20;
        }
    }

    private transient LinkedList<WakeParticle> wakeParticles = new LinkedList<>();

    class VehicleAnimState {
        double x, y;
        double targetX, targetY;
        Vehicle vehicle;

        public VehicleAnimState(double x, double y, Vehicle v) {
            this.x = x;
            this.y = y;
            this.targetX = x;
            this.targetY = y;
            this.vehicle = v;
        }
    }

    private transient Map<Integer, VehicleAnimState> animBuffer = new ConcurrentHashMap<>();
    private Vehicle hoveredVehicle = null;
    private int mouseX = 0;
    private int mouseY = 0;
    private AnimationOverlayPanel overlayPanel;

    private Timer snapshotTimer;
    private Timer uiEffectsTimer;

    private double pulseAlpha = 0.0;
    private boolean pulseRising = true;

    public static SimulationGUI getInstance() {
        return instance;
    }

    public SimulationGUI() {
        instance = this;
        setTitle("FerryTrans Premium Command Center");
        setSize(1100, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel contentPane = new JPanel(null);
        contentPane.setBackground(OBSIDIAN_NAVY);

        // Header Config Area
        JPanel configPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setColor(GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(NEON_CYAN);
                g2.setStroke(STROKE_2);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
            }
        };
        configPanel.setBounds(20, 20, 1060, 60);
        configPanel.setOpaque(false);

        addConfigField(configPanel, "Ferry Cap:", txtCapacity = new JTextField("20"), 20);
        addConfigField(configPanel, "Cars:", txtCars = new JTextField("12"), 190);
        addConfigField(configPanel, "Minibus:", txtMinibus = new JTextField("10"), 360);
        addConfigField(configPanel, "Truck:", txtTruck = new JTextField("8"), 530);

        btnStart = createGlowButton("START", NEON_GREEN, 720, 15);
        JButton btnStop = createGlowButton("STOP", NEON_RED, 830, 15);
        JButton btnSave = createGlowButton("SAVE", NEON_CYAN, 940, 15);

        btnSave.addActionListener(e -> {
            try {
                int cap = Integer.parseInt(txtCapacity.getText().trim());
                int cars = Integer.parseInt(txtCars.getText().trim());
                int minis = Integer.parseInt(txtMinibus.getText().trim());
                int trucks = Integer.parseInt(txtTruck.getText().trim());
                if (cap <= 0 || cars <= 0 || minis <= 0 || trucks <= 0) {
                    throw new NumberFormatException("Values must be strictly greater than 0");
                }
                SimulationConfig.saveConfig(cap, cars, minis, trucks);
                JOptionPane.showMessageDialog(this, "Configuration Saved.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid input! Please enter positive numbers only.", "Error", JOptionPane.ERROR_MESSAGE);
                txtCapacity.setText(String.valueOf(SimulationConfig.FERRY_CAPACITY));
                txtCars.setText(String.valueOf(SimulationConfig.TOTAL_CARS));
                txtMinibus.setText(String.valueOf(SimulationConfig.TOTAL_MINIBUSES));
                txtTruck.setText(String.valueOf(SimulationConfig.TOTAL_TRUCKS));
            } catch (Exception ex) {}
        });

        btnStart.addActionListener(e -> {
            try {
                int cap = Integer.parseInt(txtCapacity.getText().trim());
                int cars = Integer.parseInt(txtCars.getText().trim());
                int minis = Integer.parseInt(txtMinibus.getText().trim());
                int trucks = Integer.parseInt(txtTruck.getText().trim());
                if (cap <= 0 || cars <= 0 || minis <= 0 || trucks <= 0) {
                    throw new NumberFormatException("Values must be strictly greater than 0");
                }
                SimulationConfig.saveConfig(cap, cars, minis, trucks);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid input! Please enter positive numbers only.", "Error", JOptionPane.ERROR_MESSAGE);
                return; // Stop execution
            }
            btnStart.setEnabled(false);
            resetGUI();
            Main.resetAndStart();
        });

        btnStop.addActionListener(e -> Main.terminateAll());

        configPanel.add(btnStart);
        configPanel.add(btnStop);
        configPanel.add(btnSave);
        contentPane.add(configPanel);

        // Main Port and River Panels
        mainlandPanel = new MainlandPanel();
        mainlandPanel.setBounds(20, 100, 300, 420);
        contentPane.add(mainlandPanel);

        riverPanel = new RiverPanel();
        riverPanel.setBounds(340, 100, 420, 420);
        contentPane.add(riverPanel);

        islandPanel = new IslandPanel();
        islandPanel.setBounds(780, 100, 300, 420);
        contentPane.add(islandPanel);

        // Footer Panels
        consolePanel = new ConsolePanel();
        consolePanel.setBounds(20, 540, 740, 190);
        contentPane.add(consolePanel);

        statsPanel = new StatsPanel();
        statsPanel.setBounds(780, 540, 300, 190);
        contentPane.add(statsPanel);

        setContentPane(contentPane);

        overlayPanel = new AnimationOverlayPanel();
        setGlassPane(overlayPanel);
        overlayPanel.setVisible(true);

        SimulationEventBus.setGUI(this);

        uiEffectsTimer = new Timer(16, e -> {
            if (mainlandPanel != null)
                mainlandPanel.repaint();
            if (islandPanel != null)
                islandPanel.repaint();
            if (statsPanel != null)
                statsPanel.repaint();
        });
        uiEffectsTimer.start();

        // --- Timers for dynamic UI logic ---
        snapshotTimer = new Timer(800, e -> {
            Ferry ferry = SimulationManager.getFerry();
            if (ferry != null) {
                targetFerryLoad = ferry.getCurrentLoad();
                targetFerrySide = ferry.getCurrentSide();
                targetIsCrossing = ferry.isCrossing();

                try {
                    targetOnboardVehicles = new ArrayList<>(ferry.getOnboardVehicles());
                    targetBoardingVehicles = new ArrayList<>(ferry.getBoardingVehicles());
                } catch (Exception ex) {
                    // Ignore concurrent modification during snapshot
                }

                liveChartData.addLast(ferry.getCurrentLoad());
                if (liveChartData.size() > 31)
                    liveChartData.removeFirst();
                lastChartUpdateTime = System.currentTimeMillis();

                // Set target spatial coordinate based on ferry side
                if (targetIsCrossing) {
                    targetFerryX = (targetFerrySide == Side.MAINLAND) ? 170.0 : 10.0;
                } else {
                    targetFerryX = (targetFerrySide == Side.MAINLAND) ? 10.0 : 170.0;
                }
            }

            Port mp = SimulationManager.getPort(Side.MAINLAND);
            if (mp != null) {
                try {
                    targetMainlandQueue = new ArrayList<>(mp.getWaitingArea().getQueue());
                } catch (Exception ex) {
                    // Ignore concurrent modification during snapshot
                }
            }

            Port ip = SimulationManager.getPort(Side.ISLAND);
            if (ip != null) {
                try {
                    targetIslandQueue = new ArrayList<>(ip.getWaitingArea().getQueue());
                } catch (Exception ex) {
                    // Ignore concurrent modification during snapshot
                }
            }

            // === MAINLAND QUEUE (GLOBAL COORDS FOR LERP) ===
            {
                int lane1Idx = 0, lane2Idx = 0;
                for (BoardingTicket bt : targetMainlandQueue) {
                    if (bt.getAssignedTollBoothId() % 2 == 0) { // Even IDs (Booth 1) go to Lane 1
                        int vy = 230 + (lane1Idx * 45);
                        if (vy + 40 > 510) break; // Strict overflow cutoff
                        updateAnimState(bt.getVehicle(), 65, vy); // 20 + 45
                        lane1Idx++;
                    } else { // Odd IDs (Booth 2) go to Lane 2
                        int vy = 230 + (lane2Idx * 45);
                        if (vy + 40 > 510) break; // Strict overflow cutoff
                        updateAnimState(bt.getVehicle(), 185, vy); // 20 + 165
                        lane2Idx++;
                    }
                }
            }

            // === ISLAND QUEUE (GLOBAL COORDS FOR LERP) ===
            {
                int lane1Idx = 0, lane2Idx = 0;
                for (BoardingTicket bt : targetIslandQueue) {
                    if (bt.getAssignedTollBoothId() % 2 == 0) { // Even IDs (Booth 1) go to Lane 1
                        int vy = 230 + (lane1Idx * 45);
                        if (vy + 40 > 510) break;
                        updateAnimState(bt.getVehicle(), 825, vy); // 780 + 45
                        lane1Idx++;
                    } else { // Odd IDs (Booth 2) go to Lane 2
                        int vy = 230 + (lane2Idx * 45);
                        if (vy + 40 > 510) break;
                        updateAnimState(bt.getVehicle(), 945, vy); // 780 + 165
                        lane2Idx++;
                    }
                }
            }

            // === FERRY VEHICLES (GLOBAL COORDS FOR LERP) ===
            double tFerryX = targetIsCrossing ? ((targetFerrySide == Side.MAINLAND) ? 170.0 : 10.0)
                    : ((targetFerrySide == Side.MAINLAND) ? 10.0 : 170.0);
            int startX = 340 + (int) tFerryX + 15; // ferryPanelX + 15
            int startY = 100 + 40 + 55 + 30; // ferryPanelY + 40 (below load bar)

            List<Vehicle> combinedFerryVehicles = new ArrayList<>();
            java.util.Set<Integer> seenIds = new java.util.HashSet<>();
            
            // Strictly deduplicate ferry vehicles using their IDs to prevent slot collisions
            for (Vehicle v : targetOnboardVehicles) {
                if (seenIds.add(v.getId())) combinedFerryVehicles.add(v);
            }
            for (Vehicle v : targetBoardingVehicles) {
                if (seenIds.add(v.getId())) combinedFerryVehicles.add(v);
            }

            int columns = 4;
            for (int i = 0; i < combinedFerryVehicles.size(); i++) {
                int col = i % columns;
                int row = i / columns;
                int targetX = startX + (col * 45); // vehicleWidth (40) + gap (5)
                int targetY = startY + (row * 45); // vehicleHeight (40) + gap (5)
                updateAnimState(combinedFerryVehicles.get(i), targetX, targetY);
            }

            // Cleanup vehicles no longer present
            animBuffer.keySet()
                    .removeIf(id -> targetMainlandQueue.stream().noneMatch(t -> t.getVehicle().getId() == id) &&
                            targetIslandQueue.stream().noneMatch(t -> t.getVehicle().getId() == id) &&
                            combinedFerryVehicles.stream().noneMatch(v -> v.getId() == id));
        });
        snapshotTimer.start();

        // 2. Animation Timer: 40ms (~25 FPS)
        Timer animTimer = new Timer(40, e -> {
            visualFerryX += (targetFerryX - visualFerryX) * 0.1;
            visualFerryLoad += (targetFerryLoad - visualFerryLoad) * 0.1;

            // Pulse logic for emergency red
            if (pulseRising) {
                pulseAlpha += 0.05;
                if (pulseAlpha >= 1.0)
                    pulseRising = false;
            } else {
                pulseAlpha -= 0.05;
                if (pulseAlpha <= 0.2)
                    pulseRising = true;
            }

            for (VehicleAnimState state : animBuffer.values()) {
                state.x += (state.targetX - state.x) * 0.15;
                state.y += (state.targetY - state.y) * 0.15;
            }

            // Wake Particle logic
            Iterator<WakeParticle> it = wakeParticles.iterator();
            while (it.hasNext()) {
                WakeParticle p = it.next();
                p.age += 1.0;
                p.size += 0.8;
                if (p.age >= p.maxAge) {
                    it.remove();
                }
            }
            if (targetIsCrossing && Math.abs(targetFerryX - visualFerryX) > 1.0) {
                if (wakeParticles.size() < 20 && Math.random() < 0.6) {
                    int fx = (int) visualFerryX;
                    int fy = 40, fw = 240, fh = 340;
                    if (targetFerrySide == Side.MAINLAND) {
                        wakeParticles.add(new WakeParticle(fx - 5, fy + fh / 2));
                    } else {
                        wakeParticles.add(new WakeParticle(fx + fw + 5, fy + fh / 2));
                    }
                }
            }

            repaint();
            overlayPanel.repaint();
        });
        animTimer.start();
    }

    private void updateAnimState(Vehicle v, int tx, int ty) {
        VehicleAnimState state = animBuffer.computeIfAbsent(v.getId(), k -> new VehicleAnimState(tx, ty, v));
        state.targetX = tx;
        state.targetY = ty;
    }

    private void resetGUI() {
        SwingUtilities.invokeLater(() -> {
            consoleLogs.clear();
            statsText = "\n  Awaiting Simulation...\n  Data will populate at end.";
            visualFerryX = 10.0;
            targetFerryX = 10.0;
            visualFerryLoad = 0.0;
            targetFerryLoad = 0.0;
            targetFerrySide = Side.MAINLAND;
            targetIsCrossing = false;
            targetOnboardVehicles.clear();
            targetBoardingVehicles.clear();
            targetMainlandQueue.clear();
            targetIslandQueue.clear();
            animBuffer.clear();
            wakeParticles.clear();
            tollStates.clear();
            liveChartData.clear();
            repaint();
        });
    }

    public void updateGUI() {
    }

    private void addConfigField(JPanel parent, String labelText, JTextField field, int x) {
        JLabel lbl = new JLabel(labelText);
        lbl.setBounds(x, 20, 80, 20);
        lbl.setFont(FONT_SEGOE_SEMIBOLD_14);
        lbl.setForeground(NEON_CYAN);

        field.setBounds(x + 80, 15, 60, 30);
        field.setBackground(COLOR_FIELD_BG);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(NEON_CYAN, 1),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setFont(FONT_SEGOE_SEMIBOLD_14);

        parent.add(lbl);
        parent.add(field);
    }

    private JButton createGlowButton(String text, Color glowColor, int x, int y) {
        JButton btn = new JButton(text);
        btn.setBounds(x, y, 100, 30);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_SEGOE_SEMIBOLD_12);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            boolean hovered = false;

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                btn.repaint();
            }
        });

        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                boolean hover = btn.getModel().isRollover();

                if (hover) {
                    g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 50));
                    g2.fillRoundRect(0, 0, btn.getWidth(), btn.getHeight(), 15, 15);
                    g2.setColor(glowColor);
                    g2.setStroke(STROKE_2);
                    g2.drawRoundRect(1, 1, btn.getWidth() - 3, btn.getHeight() - 3, 15, 15);
                } else {
                    g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 150));
                    g2.fillRoundRect(0, 0, btn.getWidth(), btn.getHeight(), 15, 15);
                }

                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Color.WHITE);
                g2.drawString(text, (btn.getWidth() - fm.stringWidth(text)) / 2,
                        (btn.getHeight() + fm.getAscent()) / 2 - 2);
                g2.dispose();
            }
        });
        return btn;
    }

    public void appendConsole(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleLogs.add(message);
            if (consoleLogs.size() > 10)
                consoleLogs.remove(0);
        });
    }

    public void onSimulationEnded() {
        SwingUtilities.invokeLater(() -> {
            if (btnStart != null) {
                btnStart.setEnabled(true);
            }
        });
    }

    public void updateStats(String stats) {
        SwingUtilities.invokeLater(() -> {
            this.statsText = stats;
            // Freeze all data-polling timers when the final report arrives
            if (stats != null && stats.contains("Total Simulation Time")) {
                if (snapshotTimer != null)
                    snapshotTimer.stop();
                if (uiEffectsTimer != null)
                    uiEffectsTimer.stop();
            }
        });
    }

    private void drawGlassPanel(Graphics2D g2, int w, int h, String title) {
        g2.setColor(GLASS_BG);
        g2.fillRoundRect(0, 0, w, h, 20, 20);
        g2.setColor(COLOR_WHITE_20);
        g2.fillRoundRect(0, 0, w, 40, 20, 20);
        g2.setColor(NEON_CYAN);
        g2.setStroke(STROKE_1);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(FONT_SEGOE_SEMIBOLD_16);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (w - fm.stringWidth(title)) / 2, 26);

        g2.setColor(COLOR_WHITE_50);
        g2.drawLine(20, 40, w - 20, 40);
    }

    private void drawSmartToll(Graphics2D g2, int x, int y, int w, int h, String title, boolean isBusy,
            String uniqueId) {
        TollBoothUIState state = tollStates.computeIfAbsent(uniqueId, k -> new TollBoothUIState());
        state.update(isBusy);

        long elapsed = System.currentTimeMillis() - state.transitionStartTime;
        if (elapsed < 600) {
            float progress = elapsed / 600f;
            float alpha = 1.0f - progress;
            int expand = (int) (progress * 40);

            Color pulseColor = isBusy ? NEON_RED : NEON_GREEN;

            Composite oldComp = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.5f));

            // Bloom
            g2.setColor(new Color(pulseColor.getRed(), pulseColor.getGreen(), pulseColor.getBlue(), 100));
            g2.setStroke(STROKE_4);
            g2.drawRoundRect(x - expand, y - expand, w + expand * 2, h + expand * 2, 10, 10);

            // Core
            g2.setColor(pulseColor);
            g2.setStroke(STROKE_1_5);
            g2.drawRoundRect(x - expand, y - expand, w + expand * 2, h + expand * 2, 10, 10);

            g2.setComposite(oldComp);
        }

        Color baseColor = isBusy ? NEON_RED : NEON_GREEN;
        g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 30));
        g2.fillRoundRect(x, y, w, h, 10, 10);

        g2.setColor(baseColor);
        g2.setStroke(STROKE_2);
        g2.drawRoundRect(x, y, w, h, 10, 10);

        g2.setColor(Color.WHITE);
        g2.setFont(FONT_SEGOE_SEMIBOLD_12);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, x + (w - fm.stringWidth(title)) / 2, y + 20);

        String status = isBusy ? "PROCESSING" : "AVAILABLE";
        g2.setFont(FONT_SEGOE_10);
        g2.setColor(baseColor);
        fm = g2.getFontMetrics();
        g2.drawString(status, x + (w - fm.stringWidth(status)) / 2, y + h - 10);
    }

    private void drawVehicle(Graphics2D g2, int x, int y, Vehicle v) {
        boolean isAcil = v.getPriority() == VehiclePriority.EMERGENCY;

        if (isAcil) {
            int alpha = (int) (pulseAlpha * 100);
            g2.setColor(new Color(NEON_RED.getRed(), NEON_RED.getGreen(), NEON_RED.getBlue(), alpha));
            g2.fillRoundRect(x - 2, y - 2, 44, 44, 8, 8);
        }

        g2.setColor(COLOR_PORT_BG_LIGHT);
        g2.fillRoundRect(x, y, 40, 40, 8, 8);

        g2.setColor(isAcil ? NEON_RED : NEON_CYAN);
        g2.setStroke(STROKE_1);
        g2.drawRoundRect(x, y, 40, 40, 8, 8);

        g2.setColor(Color.WHITE);
        g2.setFont(FONT_SEGOE_SEMIBOLD_10);
        String t1 = v.getType().toString().charAt(0) + "-" + v.getId();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(t1, x + (40 - fm.stringWidth(t1)) / 2, y + 16);

        g2.setColor(isAcil ? NEON_RED : COLOR_STANDARD_VEHICLE);
        String t2 = isAcil ? "Acil" : "Std";
        fm = g2.getFontMetrics();
        g2.drawString(t2, x + (40 - fm.stringWidth(t2)) / 2, y + 32);
    }

    private void drawVehicleGrid(Graphics2D g2, int startX, int startY, int maxCols, List<Vehicle> vehicles) {
        int x = startX;
        int y = startY;
        for (int i = 0; i < vehicles.size(); i++) {
            drawVehicle(g2, x, y, vehicles.get(i));
            x += 45;
            if ((i + 1) % maxCols == 0) {
                x = startX;
                y += 45;
            }
        }
    }

    @SuppressWarnings("serial")
    class AnimationOverlayPanel extends JPanel {
        public AnimationOverlayPanel() {
            setOpaque(false);
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof MouseEvent) {
                    MouseEvent me = (MouseEvent) event;
                    if (me.getID() == MouseEvent.MOUSE_MOVED) {
                        try {
                            Point p = me.getLocationOnScreen();
                            SwingUtilities.convertPointFromScreen(p, this);
                            mouseX = p.x;
                            mouseY = p.y;
                            hoveredVehicle = null;
                            for (VehicleAnimState state : animBuffer.values()) {
                                if (mouseX >= state.x && mouseX <= state.x + 40 &&
                                        mouseY >= state.y && mouseY <= state.y + 40) {
                                    hoveredVehicle = state.vehicle;
                                    break;
                                }
                            }
                        } catch (Exception ex) {
                            // Ignore concurrent modification during hover check
                        }
                    }
                }
            }, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        @Override
        public boolean contains(int x, int y) {
            return false; // Crucial fix: Do not block mouse clicks from reaching buttons underneath
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            for (VehicleAnimState state : animBuffer.values()) {
                drawVehicle(g2, (int) state.x, (int) state.y, state.vehicle);
            }

            if (hoveredVehicle != null) {
                drawHolographicTooltip(g2, mouseX, mouseY, hoveredVehicle);
            }
        }
    }

    private void drawHolographicTooltip(Graphics2D g2, int x, int y, Vehicle v) {
        int w = 150, h = 90;
        int px = x + 15;
        int py = y + 15;
        if (px + w > getWidth())
            px = x - w - 10;
        if (py + h > getHeight())
            py = y - h - 10;

        g2.setColor(COLOR_TOOLTIP_BG);
        g2.fillRoundRect(px, py, w, h, 10, 10);
        g2.setColor(NEON_CYAN);
        g2.setStroke(STROKE_1);
        g2.drawRoundRect(px, py, w, h, 10, 10);

        g2.setColor(Color.WHITE);
        g2.setFont(FONT_SEGOE_SEMIBOLD_12);
        g2.drawString("VEHICLE ID: " + v.getId(), px + 10, py + 22);

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(FONT_SEGOE_11);
        g2.drawString("Type: " + v.getType(), px + 10, py + 40);
        g2.drawString("Priority: " + v.getPriority(), px + 10, py + 55);
        g2.drawString("Start: " + v.getStartingSide(), px + 10, py + 70);
    }

    @SuppressWarnings("serial")
    class MainlandPanel extends JPanel {
        public MainlandPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            drawGlassPanel(g2, getWidth(), getHeight(), "MAINLAND PORT");

            Port port = SimulationManager.getPort(Side.MAINLAND);
            if (port != null) {
                TollBooth[] booths = port.getTollBooths();
                if (booths != null && booths.length >= 2) {
                    drawSmartToll(g2, 20, 60, 90, 50, "Toll 1", booths[0].isBusy(), "Mainland-Toll 1");
                    drawSmartToll(g2, 140, 60, 90, 50, "Toll 2", booths[1].isBusy(), "Mainland-Toll 2");
                }
            } else {
                drawSmartToll(g2, 20, 60, 90, 50, "Toll 1", false, "Mainland-Toll 1");
                drawSmartToll(g2, 140, 60, 90, 50, "Toll 2", false, "Mainland-Toll 2");
            }

            g2.setColor(COLOR_CYAN_50);
            g2.setStroke(
                    STROKE_DASHED_5);
            g2.drawLine(125, 120, 125, getHeight() - 20);

        }
    }

    @SuppressWarnings("serial")
    class IslandPanel extends JPanel {
        public IslandPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            drawGlassPanel(g2, getWidth(), getHeight(), "ISLAND PORT");

            Port port = SimulationManager.getPort(Side.ISLAND);
            if (port != null) {
                TollBooth[] booths = port.getTollBooths();
                if (booths != null && booths.length >= 2) {
                    drawSmartToll(g2, 20, 60, 90, 50, "Toll 1", booths[0].isBusy(), "Island-Toll 1");
                    drawSmartToll(g2, 140, 60, 90, 50, "Toll 2", booths[1].isBusy(), "Island-Toll 2");
                }
            } else {
                drawSmartToll(g2, 20, 60, 90, 50, "Toll 1", false, "Island-Toll 1");
                drawSmartToll(g2, 140, 60, 90, 50, "Toll 2", false, "Island-Toll 2");
            }

            g2.setColor(COLOR_CYAN_50);
            g2.setStroke(
                    STROKE_DASHED_5);
            g2.drawLine(125, 120, 125, getHeight() - 20);

        }
    }

    @SuppressWarnings("serial")
    class RiverPanel extends JPanel {
        public RiverPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // River Background
            g2.setColor(COLOR_RIVER_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(COLOR_RIVER_WAVE);
            g2.setStroke(STROKE_2);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

            int fx = (int) visualFerryX;
            int fy = 40, fw = 240, fh = 340;

            // Ferry Wake (Water Effect)
            for (WakeParticle p : wakeParticles) {
                float opacity = (float) (1.0 - (p.age / p.maxAge)) * 0.8f;
                if (opacity < 0f)
                    opacity = 0f;
                if (opacity > 1f)
                    opacity = 1f;

                Composite originalComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

                float radius = (float) p.size / 2f;
                if (radius <= 0)
                    radius = 1f;
                Point2D center = new Point2D.Float((float) p.x, (float) p.y);
                float[] dist = { 0.0f, 1.0f };
                Color[] colors = { NEON_CYAN, COLOR_CYAN_0 };

                RadialGradientPaint rgp = new RadialGradientPaint(center, radius, dist, colors);
                g2.setPaint(rgp);
                g2.fillOval((int) (p.x - radius), (int) (p.y - radius), (int) p.size, (int) p.size);

                g2.setComposite(originalComposite);
            }

            // Ferry Hull
            g2.setColor(COLOR_WATER_EDGE);
            g2.fillRoundRect(fx, fy, fw, fh, 30, 30);
            g2.setColor(NEON_CYAN);
            g2.drawRoundRect(fx, fy, fw, fh, 30, 30);

            // Ferry Bridge
            g2.setColor(COLOR_WAKE_TRAIL);
            g2.fillRoundRect(fx, fy, fw, 40, 30, 30);

            String fStatus = targetIsCrossing ? "FERRY IN ROUTE ───>" : "DOCKED AT " + targetFerrySide;
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_SEGOE_SEMIBOLD_12);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(fStatus, fx + (fw - fm.stringWidth(fStatus)) / 2, fy + 25);

            // Progress Bar
            int barY = fy + 55;
            g2.setColor(COLOR_FERRY_BASE);
            g2.fillRoundRect(fx + 20, barY, fw - 40, 15, 10, 10);

            int maxCap = Math.max(1, SimulationConfig.FERRY_CAPACITY);
            double ratio = Math.min(1.0, visualFerryLoad / Math.max(1, maxCap));

            g2.setColor(NEON_GREEN);
            g2.fillRoundRect(fx + 20, barY, (int) ((fw - 40) * ratio), 15, 10, 10);

            g2.setColor(Color.WHITE);
            g2.setFont(FONT_SEGOE_SEMIBOLD_10);
            String loadStr = String.format("LOAD: %.0f / %d", visualFerryLoad, maxCap);
            fm = g2.getFontMetrics();
            g2.drawString(loadStr, fx + (fw - fm.stringWidth(loadStr)) / 2, barY + 12);

        }
    }

    @SuppressWarnings("serial")
    class ConsolePanel extends JPanel {
        public ConsolePanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g2.setColor(DARK_OVERLAY);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(COLOR_DARK_GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

            g2.setColor(NEON_GREEN);
            g2.setFont(FONT_CONSOLAS_12);
            int y = 25;
            for (String s : consoleLogs) {
                g2.drawString("> " + s, 20, y);
                y += 16;
            }
        }
    }

    @SuppressWarnings("serial")
    class StatsPanel extends JPanel {
        public StatsPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            drawGlassPanel(g2, getWidth(), getHeight(), "PERFORMANCE METRICS");

            g2.setFont(FONT_SEGOE_SEMIBOLD_14);
            int y = 70;
            for (String line : statsText.split("\n")) {
                if (!line.trim().isEmpty() && !line.contains("=====") && !line.contains("FINAL")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        g2.setColor(Color.LIGHT_GRAY);
                        g2.drawString(parts[0] + ":", 20, y);
                        g2.setColor(NEON_GREEN);
                        g2.drawString(parts[1].trim(), 180, y);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.drawString(line.trim(), 20, y);
                    }
                    y += 25;
                }
            }

            // --- Elite Live Analytics Chart ---
            int chX = 35, chY = 110, chW = getWidth() - 55, chH = 70;

            // Background
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(chX, chY, chW, chH, 5, 5);
            g2.setColor(COLOR_DARK_GRAY);
            g2.setStroke(STROKE_1);
            g2.drawRoundRect(chX, chY, chW, chH, 5, 5);

            // Labels & Titles
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_SEGOE_SEMIBOLD_10);
            g2.drawString("100%", 5, chY + 10);
            g2.drawString("0%", 15, chY + chH);
            g2.drawString("SYSTEM LOAD (%)", chX + 5, chY + 15);

            // Grid System
            g2.setStroke(
                    STROKE_DASHED_3);
            g2.setColor(COLOR_DARK_GRAY);
            g2.drawLine(chX, chY + chH / 2, chX + chW, chY + chH / 2); // 50% line

            if (liveChartData.size() > 1) {
                long now = System.currentTimeMillis();
                float progress = Math.min(1.0f, (now - lastChartUpdateTime) / 800f);
                int step = chW / 29;
                int pixelOffset = (int) (progress * step);

                // Scrolling Vertical Grids
                for (int i = 0; i < 31; i += 5) {
                    int gx = chX + i * step - pixelOffset;
                    if (gx >= chX && gx <= chX + chW) {
                        g2.drawLine(gx, chY, gx, chY + chH);
                    }
                }

                // Construct Path2D
                Path2D.Float path = new Path2D.Float();
                int maxCap = Math.max(1, SimulationConfig.FERRY_CAPACITY);
                if (maxCap == 0)
                    maxCap = 1;

                float firstX = chX - pixelOffset;
                float firstY = chY + chH - ((liveChartData.get(0) / (float) maxCap) * chH);
                path.moveTo(firstX, firstY);

                float lastPx = firstX;
                float lastPy = firstY;

                for (int i = 1; i < liveChartData.size(); i++) {
                    float px = chX + i * step - pixelOffset;
                    float py = chY + chH - ((liveChartData.get(i) / (float) maxCap) * chH);
                    path.lineTo(px, py);
                    lastPx = px;
                    lastPy = py;
                }

                // Clipping Region to stay inside chart
                Shape oldClip = g2.getClip();
                g2.clipRect(chX, chY, chW, chH);

                // Gradient Area Fill
                Path2D.Float fillPath = (Path2D.Float) path.clone();
                fillPath.lineTo(lastPx, chY + chH);
                fillPath.lineTo(firstX, chY + chH);
                fillPath.closePath();

                g2.setPaint(new LinearGradientPaint(chX, chY, chX, chY + chH,
                        new float[] { 0f, 1f }, new Color[] { COLOR_CYAN_120, COLOR_TRANSPARENT }));
                g2.fill(fillPath);

                // Glow-on-Glow Stroke
                g2.setStroke(STROKE_4);
                g2.setColor(COLOR_CYAN_80); // Bloom
                g2.draw(path);

                g2.setStroke(STROKE_1_5);
                g2.setColor(NEON_CYAN); // Core
                g2.draw(path);

                // Lead Dot
                g2.fillOval((int) lastPx - 3, (int) lastPy - 3, 6, 6);

                g2.setClip(oldClip);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulationGUI gui = new SimulationGUI();
            gui.setVisible(true);
        });
    }
}
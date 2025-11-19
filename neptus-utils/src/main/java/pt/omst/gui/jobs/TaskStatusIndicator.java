package pt.omst.gui.jobs;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.Timer;

public class TaskStatusIndicator extends JComponent {

    private final Window parentWindow;
    private final Timer blinkTimer;

    // --- STATIC REFERENCE TO ENSURE ONLY ONE DIALOG EXISTS ---
    private static TaskManagerDialog currentDialog;

    // State variables
    private boolean isLedHigh = false;
    private int taskCount = 0;

    // Styling
    private static final int DIAMETER = 16;
    private static final Color COL_IDLE = new Color(160, 160, 160);
    private static final Color COL_ACTIVE_HIGH = new Color(50, 255, 50);
    private static final Color COL_ACTIVE_LOW = new Color(0, 150, 0);
    private static final Color COL_BORDER = new Color(80, 80, 80);
    private static final Color COL_TEXT = Color.BLACK;

    public TaskStatusIndicator(Window parentWindow) {
        this.parentWindow = parentWindow;

        setPreferredSize(new Dimension(20, 20));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 1. Blink Timer
        blinkTimer = new Timer(500, e -> {
            isLedHigh = !isLedHigh;
            repaint();
        });

        // 2. Listen to Data Changes
        JobManager.getInstance().getTableModel().addTableModelListener(e -> updateStatus());

        // 3. Handle Click
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openOrFocusTaskManager();
            }
        });

        // Initial State
        updateStatus();
    }

    private void updateStatus() {
        taskCount = JobManager.getInstance().getTableModel().getRowCount();

        if (taskCount > 0) {
            String text = taskCount + " background task" + (taskCount > 1 ? "s" : "") + " running.";
            setToolTipText(text);

            if (!blinkTimer.isRunning()) {
                blinkTimer.start();
                isLedHigh = true;
            }
        } else {
            setToolTipText("No background tasks running.");
            if (blinkTimer.isRunning()) {
                blinkTimer.stop();
            }
            isLedHigh = false;
            repaint();
        }
    }

    /**
     * Logic to Open or Focus the dialog
     */
    private void openOrFocusTaskManager() {
        Frame parentFrame = (parentWindow instanceof Frame) ? (Frame) parentWindow : null;

        // Check if dialog exists and is not "disposed" (closed by user)
        if (currentDialog == null || !currentDialog.isDisplayable()) {
            // Create new instance
            currentDialog = new TaskManagerDialog(parentFrame);
        }

        // Ensure it is visible
        if (!currentDialog.isVisible()) {
            currentDialog.setVisible(true);
        }

        // Bring it to the front (in case it was buried under other windows)
        currentDialog.toFront();
        currentDialog.requestFocus();
    }

   @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        
        // High quality rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x = (getWidth() - DIAMETER) / 2;
        int y = (getHeight() - DIAMETER) / 2;

        // 1. Determine Colors
        Color fillColor = (taskCount == 0) ? COL_IDLE : (isLedHigh ? COL_ACTIVE_HIGH : COL_ACTIVE_LOW);
        
        // 2. Draw LED Body
        g2.setColor(fillColor);
        g2.fillOval(x, y, DIAMETER, DIAMETER);
        g2.setColor(COL_BORDER);
        g2.drawOval(x, y, DIAMETER, DIAMETER);

        // 3. Draw Text (or Glass effect if count <= 1)
        if (taskCount > 1) {
            g2.setColor(COL_TEXT);
            
            String text;
            Font fontToUse;

            if (taskCount < 10) {
                // Single digit (e.g., "5") -> Big Font
                text = String.valueOf(taskCount);
                fontToUse = new Font("SansSerif", Font.BOLD, 12);
            } else if (taskCount < 100) {
                // Double digit (e.g., "20") -> Small Font to fit circle
                text = String.valueOf(taskCount);
                fontToUse = new Font("SansSerif", Font.BOLD, 10); 
            } else {
                // Three digits or more -> Cap it
                text = "99+";
                fontToUse = new Font("SansSerif", Font.BOLD, 8); // Tiny font
            }

            g2.setFont(fontToUse);
            FontMetrics fm = g2.getFontMetrics();

            // Precise Centering Logic
            int textX = x + (DIAMETER - fm.stringWidth(text)) / 2;
            int textY = y + ((DIAMETER - fm.getHeight()) / 2) + fm.getAscent();
            
            // Manual nudge: fonts often render slightly too high in small circles
            textY -= 1; 

            g2.drawString(text, textX, textY);
        } else {
            // Draw glass reflection only if no text is obscuring it
            g2.setColor(new Color(255, 255, 255, 150));
            g2.fillOval(x + 3, y + 3, DIAMETER / 3, DIAMETER / 3);
        }
    }
}
//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************

package pt.omst.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pt.omst.neptus.util.GuiUtils;

/**
 * A zoomable and scrollable time interval selector component.
 * Allows users to select a time interval using draggable handles,
 * zoom with mouse wheel, and pan by dragging.
 */
@Slf4j
public class ZoomableTimeIntervalSelector extends JPanel {
    
    // Time range configuration
    @Getter @Setter
    private Instant absoluteMinTime;
    @Getter @Setter
    private Instant absoluteMaxTime;
    
    // Current view window
    private Instant viewStartTime;
    private Instant viewEndTime;
    
    // Selected interval
    @Getter
    private Instant selectedStartTime;
    @Getter
    private Instant selectedEndTime;
    
    // Visual constants
    private static final int HANDLE_WIDTH = 8;
    private static final int HANDLE_HEIGHT = 20;
    private static final int TIMELINE_HEIGHT = 30;
    private static final Color SELECTION_COLOR = new Color(100, 150, 255, 100);
    private static final Color HANDLE_COLOR = new Color(50, 100, 200);
    private static final Color BACKGROUND_COLOR = new Color(240, 240, 240);
    private static final Color TIMELINE_COLOR = new Color(200, 200, 200);
    private static final Color TICK_COLOR = new Color(80, 80, 80);
    private static final Color LABEL_COLOR = new Color(40, 40, 40);
    
    // Interaction state
    private enum DragMode { NONE, START_HANDLE, END_HANDLE, PANNING }
    private DragMode dragMode = DragMode.NONE;
    private int dragStartX;
    private Instant panStartViewStart;
    private Instant panStartViewEnd;
    
    // Zoom configuration
    private static final double ZOOM_FACTOR = 1.2;
    private static final double MIN_VIEW_DURATION_SECONDS = 60; // 1 minute minimum
    
    /**
     * Creates a new ZoomableTimeIntervalSelector with the specified time range.
     */
    public ZoomableTimeIntervalSelector(Instant minTime, Instant maxTime) {
        this.absoluteMinTime = minTime;
        this.absoluteMaxTime = maxTime;
        this.viewStartTime = minTime;
        this.viewEndTime = maxTime;
        
        // Initialize selection to middle 50%
        long totalMillis = Duration.between(minTime, maxTime).toMillis();
        this.selectedStartTime = minTime.plusMillis(totalMillis / 4);
        this.selectedEndTime = maxTime.minusMillis(totalMillis / 4);
        
        setPreferredSize(new Dimension(800, TIMELINE_HEIGHT + 25));
        setBackground(BACKGROUND_COLOR);
        
        setupMouseListeners();
    }
    
    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }
            
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouseWheel(e);
            }
        };
        
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }
    
    private void handleMousePressed(MouseEvent e) {
        int x = e.getX();
        int startHandleX = timeToX(selectedStartTime);
        int endHandleX = timeToX(selectedEndTime);
        
        // Check if clicking on start handle
        if (Math.abs(x - startHandleX) <= HANDLE_WIDTH / 2) {
            dragMode = DragMode.START_HANDLE;
            setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        }
        // Check if clicking on end handle
        else if (Math.abs(x - endHandleX) <= HANDLE_WIDTH / 2) {
            dragMode = DragMode.END_HANDLE;
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        }
        // Otherwise, start panning
        else {
            dragMode = DragMode.PANNING;
            dragStartX = x;
            panStartViewStart = viewStartTime;
            panStartViewEnd = viewEndTime;
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }
    
    private void handleMouseDragged(MouseEvent e) {
        int x = e.getX();
        
        switch (dragMode) {
            case START_HANDLE:
                Instant newStart = xToTime(x);
                if (newStart.isBefore(absoluteMinTime)) newStart = absoluteMinTime;
                if (newStart.isAfter(selectedEndTime)) newStart = selectedEndTime;
                selectedStartTime = newStart;
                repaint();
                break;
                
            case END_HANDLE:
                Instant newEnd = xToTime(x);
                if (newEnd.isAfter(absoluteMaxTime)) newEnd = absoluteMaxTime;
                if (newEnd.isBefore(selectedStartTime)) newEnd = selectedStartTime;
                selectedEndTime = newEnd;
                repaint();
                break;
                
            case PANNING:
                int deltaX = dragStartX - x;
                long viewDurationMillis = Duration.between(viewStartTime, viewEndTime).toMillis();
                long deltaMillis = (long) ((double) deltaX / getWidth() * viewDurationMillis);
                
                Instant newViewStart = panStartViewStart.plusMillis(deltaMillis);
                Instant newViewEnd = panStartViewEnd.plusMillis(deltaMillis);
                
                // Constrain to absolute bounds
                if (newViewStart.isBefore(absoluteMinTime)) {
                    long diff = Duration.between(newViewStart, absoluteMinTime).toMillis();
                    newViewStart = absoluteMinTime;
                    newViewEnd = newViewEnd.plusMillis(diff);
                }
                if (newViewEnd.isAfter(absoluteMaxTime)) {
                    long diff = Duration.between(absoluteMaxTime, newViewEnd).toMillis();
                    newViewEnd = absoluteMaxTime;
                    newViewStart = newViewStart.minusMillis(diff);
                }
                
                viewStartTime = newViewStart;
                viewEndTime = newViewEnd;
                repaint();
                break;
        }
    }
    
    private void handleMouseReleased(MouseEvent e) {
        // Fire property change only when handle dragging stops
        if (dragMode == DragMode.START_HANDLE || dragMode == DragMode.END_HANDLE) {
            fireSelectionChanged();
        }
        
        dragMode = DragMode.NONE;
        setCursor(Cursor.getDefaultCursor());
    }
    
    private void handleMouseWheel(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        double zoomMultiplier = Math.pow(ZOOM_FACTOR, notches);
        
        // Get mouse position as ratio
        double mouseRatio = (double) e.getX() / getWidth();
        
        // Calculate new view duration
        long currentDuration = Duration.between(viewStartTime, viewEndTime).toMillis();
        long newDuration = (long) (currentDuration * zoomMultiplier);
        
        // Prevent zooming in too far
        if (newDuration < MIN_VIEW_DURATION_SECONDS * 1000) {
            newDuration = (long) (MIN_VIEW_DURATION_SECONDS * 1000);
        }
        
        // Prevent zooming out beyond absolute bounds
        long absoluteDuration = Duration.between(absoluteMinTime, absoluteMaxTime).toMillis();
        if (newDuration > absoluteDuration) {
            newDuration = absoluteDuration;
        }
        
        // Calculate new start and end times, centered on mouse position
        long mouseTimeOffset = (long) (mouseRatio * currentDuration);
        Instant mouseTime = viewStartTime.plusMillis(mouseTimeOffset);
        
        long beforeMouse = (long) (newDuration * mouseRatio);
        long afterMouse = newDuration - beforeMouse;
        
        Instant newViewStart = mouseTime.minusMillis(beforeMouse);
        Instant newViewEnd = mouseTime.plusMillis(afterMouse);
        
        // Constrain to absolute bounds
        if (newViewStart.isBefore(absoluteMinTime)) {
            long diff = Duration.between(newViewStart, absoluteMinTime).toMillis();
            newViewStart = absoluteMinTime;
            newViewEnd = newViewEnd.plusMillis(diff);
        }
        if (newViewEnd.isAfter(absoluteMaxTime)) {
            long diff = Duration.between(absoluteMaxTime, newViewEnd).toMillis();
            newViewEnd = absoluteMaxTime;
            newViewStart = newViewStart.minusMillis(diff);
        }
        
        viewStartTime = newViewStart;
        viewEndTime = newViewEnd;
        repaint();
    }
    
    private int timeToX(Instant time) {
        long viewDuration = Duration.between(viewStartTime, viewEndTime).toMillis();
        long timeOffset = Duration.between(viewStartTime, time).toMillis();
        return (int) ((double) timeOffset / viewDuration * getWidth());
    }
    
    private Instant xToTime(int x) {
        long viewDuration = Duration.between(viewStartTime, viewEndTime).toMillis();
        long timeOffset = (long) ((double) x / getWidth() * viewDuration);
        return viewStartTime.plusMillis(timeOffset);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw timeline background
        g2.setColor(TIMELINE_COLOR);
        g2.fillRect(0, 10, width, TIMELINE_HEIGHT);
        
        // Draw time ticks and labels
        drawTimeLabels(g2);
        
        // Draw selected interval
        int startX = timeToX(selectedStartTime);
        int endX = timeToX(selectedEndTime);
        g2.setColor(SELECTION_COLOR);
        g2.fillRect(startX, 10, endX - startX, TIMELINE_HEIGHT);
        
        // Draw handles
        drawHandle(g2, startX, 10 + TIMELINE_HEIGHT / 2);
        drawHandle(g2, endX, 10 + TIMELINE_HEIGHT / 2);
        
        // Draw border
        g2.setColor(TICK_COLOR);
        g2.drawRect(0, 10, width - 1, TIMELINE_HEIGHT);
    }
    
    private void drawHandle(Graphics2D g2, int x, int centerY) {
        int[] xPoints = {x, x - HANDLE_WIDTH / 2, x - HANDLE_WIDTH / 2, x, x + HANDLE_WIDTH / 2, x + HANDLE_WIDTH / 2};
        int[] yPoints = {
            centerY - HANDLE_HEIGHT / 2,
            centerY - HANDLE_HEIGHT / 2 + 5,
            centerY + HANDLE_HEIGHT / 2 - 5,
            centerY + HANDLE_HEIGHT / 2,
            centerY + HANDLE_HEIGHT / 2 - 5,
            centerY - HANDLE_HEIGHT / 2 + 5
        };
        
        g2.setColor(HANDLE_COLOR);
        g2.fillPolygon(xPoints, yPoints, 6);
        g2.setColor(Color.WHITE);
        g2.drawPolygon(xPoints, yPoints, 6);
    }
    
    private void drawTimeLabels(Graphics2D g2) {
        long viewDurationSeconds = Duration.between(viewStartTime, viewEndTime).getSeconds();
        
        // Determine appropriate tick interval based on zoom level
        TickInterval tickInterval = determineTickInterval(viewDurationSeconds);
        
        g2.setColor(TICK_COLOR);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g2.getFontMetrics();
        
        List<TickMark> ticks = generateTicks(tickInterval, viewDurationSeconds);
        
        for (TickMark tick : ticks) {
            int x = timeToX(tick.time);
            if (x < 0 || x > getWidth()) continue;
            
            // Draw tick line
            int tickHeight = tick.isMajor ? 6 : 3;
            g2.drawLine(x, 10, x, 10 + tickHeight);
            g2.drawLine(x, 10 + TIMELINE_HEIGHT - tickHeight, x, 10 + TIMELINE_HEIGHT);
            
            // Draw label for major ticks
            if (tick.isMajor && tick.label != null) {
                Rectangle2D bounds = fm.getStringBounds(tick.label, g2);
                int labelX = x - (int) bounds.getWidth() / 2;
                int labelY = 10 + TIMELINE_HEIGHT + 14;
                
                g2.setColor(LABEL_COLOR);
                g2.drawString(tick.label, labelX, labelY);
                g2.setColor(TICK_COLOR);
            }
        }
    }
    
    private TickInterval determineTickInterval(long durationSeconds) {
        // Years (> 2 years)
        if (durationSeconds > 2L * 365 * 24 * 3600) {
            return new TickInterval(TickLevel.YEAR, 1, "yyyy");
        }
        // Months (> 2 months)
        else if (durationSeconds > 60L * 24 * 3600) {
            return new TickInterval(TickLevel.MONTH, 1, "MMM yyyy");
        }
        // Weeks (> 14 days)
        else if (durationSeconds > 14L * 24 * 3600) {
            return new TickInterval(TickLevel.DAY, 7, "MMM dd");
        }
        // Days (> 2 days)
        else if (durationSeconds > 2L * 24 * 3600) {
            return new TickInterval(TickLevel.DAY, 1, "MMM dd");
        }
        // 12 hours (> 12 hours)
        else if (durationSeconds > 12L * 3600) {
            return new TickInterval(TickLevel.HOUR, 6, "HH:mm");
        }
        // Hours (> 2 hours)
        else if (durationSeconds > 2L * 3600) {
            return new TickInterval(TickLevel.HOUR, 1, "HH:mm");
        }
        // 30 minutes
        else if (durationSeconds > 3600) {
            return new TickInterval(TickLevel.MINUTE, 30, "HH:mm");
        }
        // 15 minutes
        else if (durationSeconds > 1800) {
            return new TickInterval(TickLevel.MINUTE, 15, "HH:mm");
        }
        // 5 minutes
        else if (durationSeconds > 600) {
            return new TickInterval(TickLevel.MINUTE, 5, "HH:mm");
        }
        // 1 minute
        else if (durationSeconds > 120) {
            return new TickInterval(TickLevel.MINUTE, 1, "HH:mm:ss");
        }
        // 10 seconds
        else {
            return new TickInterval(TickLevel.SECOND, 10, "HH:mm:ss");
        }
    }
    
    private List<TickMark> generateTicks(TickInterval interval, long viewDurationSeconds) {
        List<TickMark> ticks = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(interval.format).withZone(zone);
        
        Instant current = alignToInterval(viewStartTime, interval, zone);
        
        // Generate major ticks
        while (current.isBefore(viewEndTime) || current.equals(viewEndTime)) {
            if (current.isAfter(viewStartTime) || current.equals(viewStartTime)) {
                String label = formatter.format(current);
                ticks.add(new TickMark(current, true, label));
            }
            current = incrementTime(current, interval, zone);
        }
        
        // Generate minor ticks (between major ticks)
        TickInterval minorInterval = getMinorInterval(interval);
        if (minorInterval != null) {
            current = alignToInterval(viewStartTime, minorInterval, zone);
            while (current.isBefore(viewEndTime)) {
                if (current.isAfter(viewStartTime) && !isMajorTick(current, ticks)) {
                    ticks.add(new TickMark(current, false, null));
                }
                current = incrementTime(current, minorInterval, zone);
            }
        }
        
        return ticks;
    }
    
    private boolean isMajorTick(Instant time, List<TickMark> majorTicks) {
        for (TickMark tick : majorTicks) {
            if (tick.isMajor && Math.abs(Duration.between(tick.time, time).toMillis()) < 1000) {
                return true;
            }
        }
        return false;
    }
    
    private Instant alignToInterval(Instant time, TickInterval interval, ZoneId zone) {
        var zdt = time.atZone(zone);
        switch (interval.level) {
            case YEAR:
                return zdt.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
            case MONTH:
                return zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
            case DAY:
                return zdt.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
            case HOUR:
                int hour = (zdt.getHour() / interval.value) * interval.value;
                return zdt.withHour(hour).withMinute(0).withSecond(0).withNano(0).toInstant();
            case MINUTE:
                int minute = (zdt.getMinute() / interval.value) * interval.value;
                return zdt.withMinute(minute).withSecond(0).withNano(0).toInstant();
            case SECOND:
                int second = (zdt.getSecond() / interval.value) * interval.value;
                return zdt.withSecond(second).withNano(0).toInstant();
        }
        return time;
    }
    
    private Instant incrementTime(Instant time, TickInterval interval, ZoneId zone) {
        var zdt = time.atZone(zone);
        switch (interval.level) {
            case YEAR:
                return zdt.plusYears(interval.value).toInstant();
            case MONTH:
                return zdt.plusMonths(interval.value).toInstant();
            case DAY:
                return zdt.plusDays(interval.value).toInstant();
            case HOUR:
                return zdt.plusHours(interval.value).toInstant();
            case MINUTE:
                return zdt.plusMinutes(interval.value).toInstant();
            case SECOND:
                return zdt.plusSeconds(interval.value).toInstant();
        }
        return time;
    }
    
    private TickInterval getMinorInterval(TickInterval major) {
        switch (major.level) {
            case YEAR:
                return new TickInterval(TickLevel.MONTH, 3, "MMM");
            case MONTH:
                return new TickInterval(TickLevel.DAY, 7, "dd");
            case DAY:
                if (major.value == 7) return new TickInterval(TickLevel.DAY, 1, "dd");
                return new TickInterval(TickLevel.HOUR, 6, "HH");
            case HOUR:
                if (major.value == 6) return new TickInterval(TickLevel.HOUR, 1, "HH");
                return new TickInterval(TickLevel.MINUTE, 15, "mm");
            case MINUTE:
                if (major.value >= 15) return new TickInterval(TickLevel.MINUTE, 5, "mm");
                if (major.value >= 5) return new TickInterval(TickLevel.MINUTE, 1, "mm");
                return new TickInterval(TickLevel.SECOND, 15, "ss");
            case SECOND:
                return new TickInterval(TickLevel.SECOND, 2, "ss");
        }
        return null;
    }
    
    public void setSelectedInterval(Instant start, Instant end) {
        this.selectedStartTime = start;
        this.selectedEndTime = end;
        repaint();
        fireSelectionChanged();
    }
    
    private void fireSelectionChanged() {
        firePropertyChange("selection", null, new Instant[]{selectedStartTime, selectedEndTime});
    }
    
    // Inner classes
    private enum TickLevel { YEAR, MONTH, DAY, HOUR, MINUTE, SECOND }
    
    private static class TickInterval {
        TickLevel level;
        int value;
        String format;
        
        TickInterval(TickLevel level, int value, String format) {
            this.level = level;
            this.value = value;
            this.format = format;
        }
    }
    
    private static class TickMark {
        Instant time;
        boolean isMajor;
        String label;
        
        TickMark(Instant time, boolean isMajor, String label) {
            this.time = time;
            this.isMajor = isMajor;
            this.label = label;
        }
    }
    
    // Test main method
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        
        JFrame frame = new JFrame("Zoomable Time Interval Selector Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Create test data spanning 10 years
        Instant now = Instant.now();
        Instant tenYearsAgo = now.minus(Duration.ofDays(10 * 365));
        
        ZoomableTimeIntervalSelector selector = new ZoomableTimeIntervalSelector(tenYearsAgo, now);
        
        // Add property change listener to show selection changes
        selector.addPropertyChangeListener("selection", evt -> {
            Instant[] selection = (Instant[]) evt.getNewValue();
            log.info("Selection changed: {} to {}", selection[0], selection[1]);
        });
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(selector, BorderLayout.CENTER);
        
        // Add info label
        JLabel infoLabel = new JLabel("<html><b>Instructions:</b><br>" +
                "• Drag handles to adjust selection<br>" +
                "• Scroll wheel to zoom in/out<br>" +
                "• Drag background to pan left/right</html>");
        panel.add(infoLabel, BorderLayout.SOUTH);
        
        frame.setContentPane(panel);
        frame.setSize(900, 130);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

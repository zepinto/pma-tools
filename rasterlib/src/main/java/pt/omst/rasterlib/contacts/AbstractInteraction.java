package pt.omst.rasterlib.contacts;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Abstract class for interactions with components.
 * @param <T> the component type
 */
public abstract class AbstractInteraction<T extends Component> extends MouseAdapter implements KeyListener {

    protected T component;

    /**
     * Class constructor.
     * @param component the component to interact with
     */
    public AbstractInteraction(T component) {
        this.component = component;
    }

    /**
     * Paints the interaction. Default implementation does nothing.
     * @param graphics the graphics object to paint with
     */
    public void paint(Graphics2D graphics) {
        // Default implementation does nothing
    }

    /**
     * Called when the mouse is pressed. Default implementation requests focus for the component.
     * @param e the event to be processed
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        component.requestFocusInWindow();
    }

    /**
     * Sets the cursor of the component.
     * @param cursor the cursor to set
     */
    protected void setCursor(Cursor cursor) {
        component.setCursor(cursor);
    }

    /**
     * Repaints the component.
     */
    protected void repaint() {
        component.repaint();
    }

    /**
     * Called when a key is pressed. Default implementation does nothing.
     */
    @Override
    public void keyPressed(KeyEvent e) {
    }

    /**
     * Called when a key is released. Default implementation does nothing.
     * @param e the event to be processed by this listener
     */
    @Override
    public void keyReleased(KeyEvent e) {
    }

    /**
     * Called when a key is typed. Default implementation does nothing.
     * @param e the event to be processed by this listener
     */
    @Override
    public void keyTyped(KeyEvent e) {
    }
}


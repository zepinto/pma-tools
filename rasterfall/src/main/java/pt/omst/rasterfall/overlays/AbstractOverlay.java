package pt.omst.rasterfall.overlays;

import pt.omst.rasterfall.RasterfallTiles;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import java.awt.*;
import java.awt.event.*;

public abstract class AbstractOverlay extends LayerUI<RasterfallTiles> {

    public ImageIcon getIcon() {
        return null;
    }

    public String getTooltip() {
        return getClass().getSimpleName();
    }

    public String getToolbarName() {
        return getClass().getSimpleName();
    }

    public abstract void cleanup(RasterfallTiles waterfall);

    public abstract void install(RasterfallTiles waterfall);

    public void paint(Graphics g, JComponent c) {
        // nothing to do
    }

    @Override
    protected void processMouseEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processMouseEvent(e, l);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processMouseMotionEvent(e, l);
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processMouseWheelEvent(e, l);
    }

    @Override
    protected void processHierarchyBoundsEvent(HierarchyEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processHierarchyBoundsEvent(e, l);
    }

    @Override
    protected void processComponentEvent(ComponentEvent e, JLayer<? extends RasterfallTiles> l) {
        super.processComponentEvent(e, l);
    }
}

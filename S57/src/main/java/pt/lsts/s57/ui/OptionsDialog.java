/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 *
 * Created by Hugo Dias
 * Nov 7, 2011
 */
package pt.lsts.s57.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.Timer;

import pt.omst.mapview.StateRenderer2D;

/**
 * @author Hugo Dias
 */
public class OptionsDialog extends JDialog implements WindowListener {

    private static final long serialVersionUID = -2895143026604070400L;
    private static final int messageTimeout = 5000;

    private StateRenderer2D srend;
    protected Timer timer;

    // ui components
    private final JTabbedPane tabsPanel = new JTabbedPane(JTabbedPane.TOP);
    protected JProgressBar statusProgress;
    protected JLabel statusText;

    private List<OptionsPanel> tabs = new ArrayList<OptionsPanel>();

    /**
     * Create the dialog.
     */
    public OptionsDialog() {
        this((Window) null);
    }

    /**
     * Create the dialog.
     */
    public OptionsDialog(JDialog parent) {
        this((Window) parent);
    }

    /**
     * Create the dialog.
     */
    public OptionsDialog(JFrame parent) {
        this((Window) parent);
    }

    /**
     * Create the dialog.
     */
    private OptionsDialog(Window parent) {
        super((parent != null) && !(parent instanceof Frame) && !(parent instanceof Dialog) ? null : parent);

        this.srend = null;

        this.setTitle("S57 Settings");
        this.setBounds(100, 100, 600, 600);
        this.getContentPane().setLayout(new BorderLayout());
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        this.getContentPane().add(tabsPanel, BorderLayout.CENTER);

        this.addWindowListener(this);

        // bottom buttons panel
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(buttonPane, BorderLayout.SOUTH);

        JButton okButton = new JButton("OK");
        okButton.setActionCommand("OK");
        buttonPane.add(okButton);
        okButton.setAction(new Ok(this));
        getRootPane().setDefaultButton(okButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand("Cancel");
        cancelButton.setAction(new Cancel(this));
        buttonPane.add(cancelButton);
    }

    public void addTab(String title, Icon icon, OptionsPanel component, String tip) {
        tabsPanel.addTab(title, icon, (Component) component, tip);
        tabs.add(component);
    }

    /**
     * Actions
     */

    private class Ok extends AbstractAction {
        private static final long serialVersionUID = -5559433311567315453L;
        JDialog dialog;

        public Ok(JDialog dialog) {
            this.dialog = dialog;
            putValue(NAME, "OK");
            putValue(SHORT_DESCRIPTION, "Some short description");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dialog.setVisible(false);
        }
    }

    private class Cancel extends AbstractAction {
        private static final long serialVersionUID = -5559433311567315453L;
        JDialog dialog;

        public Cancel(JDialog dialog) {
            this.dialog = dialog;
            putValue(NAME, "Cancel");
            putValue(SHORT_DESCRIPTION, "Some short description");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dialog.setVisible(false);
        }
    }

    /**
     * Events
     */

    @Override
    public void windowOpened(WindowEvent e) {
    }

    @Override
    public void windowClosing(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
        refresh();
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        refresh();
    }

    /**
     * Helpers
     */

    public void refresh() {
        // refresh renderer
        refreshRenderer();
        for (OptionsPanel tab : tabs) {
            tab.refresh();
        }
    }

    /**
     * @return the srend
     */
    public StateRenderer2D getRenderer() {
        return srend;
    }

    /**
     * @param srend the srend to set
     */
    public void setRenderer(StateRenderer2D srend) {
        this.srend = srend;
    }

    public void refreshRenderer() {
        if (srend != null) {
            srend.invalidate();
            srend.validate();
            srend.repaint();
        }
    }

    protected void setMessage(String msg) {
        statusText.setText(statusText.getText() + " " + msg);
        timer = new Timer(messageTimeout, new MessageListener());
        timer.restart();
    }

    class MessageListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            statusText.setText("");
            timer.stop();
        }

    }

    public ImageIcon getIcon(String path, int size) {
        URL imageURL = OptionsDialog.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }

    public ImageIcon getIconSmall(String path) {
        URL imageURL = OptionsDialog.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(11, 11, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }

    public ImageIcon getIconMedium(String path) {
        URL imageURL = OptionsDialog.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }
}

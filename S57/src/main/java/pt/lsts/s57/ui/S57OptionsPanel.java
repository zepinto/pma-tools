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
 * May 2, 2012
 */
package pt.lsts.s57.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;

import net.miginfocom.swing.MigLayout;
import pt.lsts.s57.S57;
import pt.lsts.s57.S57Utils;

/**
 * @author Hugo Dias
 */
public class S57OptionsPanel extends JPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;
    private static final int messageTimeout = 5000;

    private S57 s57;
    private JList<String> folderList;
    private JList<String> mapsList;
    private JPanel mapInfo;

    protected OptionsDialog parent;
    protected JProgressBar statusProgress;
    protected JLabel statusText;
    protected JButton btnAddFolder;
    protected JButton btnDelFolder;
    protected Timer timer;

    /**
     * Constructor
     * 
     * @param s57
     * @param folderList
     * @param statusText
     * @param statusProgress
     */
    public S57OptionsPanel(S57 s57, OptionsDialog parent) {
        super();
        this.s57 = s57;
        this.parent = parent;
        S57Utils.loadSession(s57);
        setLayout(new MigLayout());
        createComponents();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void createComponents() {
        // add button to add folders
        btnAddFolder = new JButton();
        btnAddFolder.setAction(new LoadFolder(this));
        btnAddFolder.setIcon(this.getIconMedium("icons/folder-download.png"));
        this.add(btnAddFolder, "split");

        // add button to del folders
        btnDelFolder = new JButton();
        btnDelFolder.setAction(new RemoveFolder());
        btnDelFolder.setIcon(this.getIconMedium("icons/cancel2.png"));
        this.add(btnDelFolder, "wrap");

        // list of folder
        JScrollPane listScroll = new JScrollPane();
        folderList = new JList();
        folderList.addMouseListener(folderListMouseListener);
        listScroll.setViewportView(folderList);
        this.add(listScroll, "h 25%, span, w 100%");

        // status bar
        JPanel statusBar = new JPanel();
        statusBar.setPreferredSize(new Dimension(10, 20));
        statusBar.setName("Status Bar");
        statusBar.setLayout(new BorderLayout(0, 0));
        this.add(statusBar, "h 6%, span, w 100%");

        statusText = new JLabel();
        statusBar.add(statusText, BorderLayout.WEST);

        statusProgress = new JProgressBar(0, 100);
        statusProgress.setValue(0);
        statusProgress.setEnabled(false);
        statusProgress.setStringPainted(true);
        statusProgress.setVisible(false);
        statusBar.add(statusProgress, BorderLayout.EAST);

        // maps list
        JScrollPane mapsScroll = new JScrollPane();
        mapsList = new JList();
        mapsScroll.setViewportView(mapsList);
        mapsList.addMouseListener(mapsListMouseListener);
        mapsList.setCellRenderer(new MapsListRenderer());
        this.add(mapsScroll, "w 35%, h 69%, split");

        mapInfo = new JPanel(new MigLayout());
        JScrollPane mapInfoScroll = new JScrollPane(mapInfo, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.add(mapInfoScroll, "w 80%, h 69%, wrap");
    }

    public void refresh() {
        // System.out.println("refresh");
        List<String> folders = s57.getFolders();
        folderList.setListData(folders.toArray(new String[folders.size()]));
        mapsList.setListData(new String[0]);
        mapInfo.removeAll();

    }

    private JFileChooser createFileChooser(String name) {
        JFileChooser fc = new JFileChooser();
        fc.setName(name);
        fc.setMultiSelectionEnabled(true);
        return fc;
    }

    protected void setMessage(String msg) {
        statusText.setText(msg);
        timer = new Timer(messageTimeout, new MessageListener());
        timer.restart();
    }

    private class LoadFolder extends AbstractAction implements PropertyChangeListener {

        private static final long serialVersionUID = 7862292957980519987L;
        private LoadS57FolderTask loadTask;
        private File[] dir;
        private S57OptionsPanel container;

        public LoadFolder(S57OptionsPanel container) {
            putValue(NAME, "Add Folder");
            putValue(SHORT_DESCRIPTION, "Add a folder containing S57 maps");
            this.container = container;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fc = createFileChooser("openFileChooser");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String currentDir = S57Utils.sessionGet(s57, "lastS57Folder");
            if (currentDir != null)
                fc.setCurrentDirectory(new File(currentDir));
            int option = fc.showOpenDialog(parent.getContentPane());
            if (JFileChooser.APPROVE_OPTION == option) {
                dir = fc.getSelectedFiles();
                S57Utils.sessionSaveProperty(s57, "lastS57Folder", dir[0].toString());
                loadTask = new LoadS57FolderTask(dir, s57, container);
                loadTask.addPropertyChangeListener(this);
                loadTask.execute();

                statusProgress.setVisible(true);
                statusProgress.setEnabled(true);
                statusProgress.setIndeterminate(true);
                btnAddFolder.setEnabled(false);
                btnDelFolder.setEnabled(false);

            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();

            if ("progress" == propertyName) {
                int progress = (Integer) evt.getNewValue();
                statusProgress.setEnabled(true);
                statusProgress.setIndeterminate(false);
                statusProgress.setValue(progress);
            }
            else if ("state" == propertyName) {
                if (loadTask.isDone() || loadTask.isCancelled()) {
                    if (loadTask.isCancelled()) {
                        setMessage("Error loading maps");
                    }
                    else if (loadTask.isDone()) {
                        if (statusProgress.getValue() == 100)
                            setMessage("Loading maps complete!");
                    }
                    refresh();
                    parent.refreshRenderer();
                    statusProgress.setEnabled(false);
                    statusProgress.setIndeterminate(false);
                    statusProgress.setValue(0);
                    statusProgress.setVisible(false);
                    btnAddFolder.setEnabled(true);
                    btnDelFolder.setEnabled(true);
                }
            }
        }

    }

    private class RemoveFolder extends AbstractAction {
        private static final long serialVersionUID = -5559433311567315453L;

        public RemoveFolder() {
            putValue(NAME, "Remove Folder");
            putValue(SHORT_DESCRIPTION, "Remove selected folder.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<String> folders = folderList.getSelectedValuesList();
            if (folders != null)
                s57.removeFolders(folders, true);
            refresh();
            parent.refreshRenderer();
        }
    }

    private class MessageListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            statusText.setText("");
            timer.stop();
        }
    }

    MouseListener folderListMouseListener = new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 1) {
                int index = folderList.locationToIndex(e.getPoint());
                String folderPath = (String) folderList.getSelectedValue();
                if (index > -1) {
                    mapsList.setListData(s57.getMapsFromFolder(folderPath).toArray(new String[0]));
                }
            }
        }
    };

    MouseListener mapsListMouseListener = new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 1) {
                int index = mapsList.locationToIndex(e.getPoint());
                if (index == -1)
                    return;
                // clear jpanel and start the info label
                mapInfo.removeAll();
                JLabel newinfo = new JLabel();
                newinfo.setText("<html><body style='margin:0;padding:0'>");

                // write data to the label
                String mapName = (String) mapsList.getSelectedValue();
                try {
                    Map<String, String> infos = s57.getMapMeta(mapName);
                    for (Entry<String, String> info : infos.entrySet()) {
                        newinfo.setText(
                                newinfo.getText() + "<b>" + info.getKey() + "</b> : " + info.getValue() + "<br/>");
                    }
                }
                catch (Exception e2) {
                    newinfo.setText(newinfo.getText() + e2.getMessage() + "<br/>");
                }
                // close the html, add label to parent and force repaint
                newinfo.setText(newinfo.getText() + "</body></html>");
                mapInfo.add(newinfo, "top, left");
                mapInfo.invalidate();
                mapInfo.validate();
                mapInfo.repaint();

            }
        }
    };

    @SuppressWarnings("rawtypes")
    class MapsListRenderer extends JLabel implements ListCellRenderer {

        private static final long serialVersionUID = -5340963297390020776L;

        public MapsListRenderer() {
            setOpaque(true);
            setHorizontalAlignment(LEFT);
            setVerticalAlignment(CENTER);
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            // Get the selected index. (The index param isn't
            // always valid, so just use the value.)
            // int selectedIndex = ((Integer) value).intValue();
            String text = (String) value;
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            }
            else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            if (s57.getMap(text) != null) {
                setText(text);
                setFont(list.getFont());
                setIcon(S57OptionsPanel.this.getIconSmall("icons/location.png"));
                return this;
            }
            else {
                setText(text);
                setFont(list.getFont());
                setIcon(S57OptionsPanel.this.getIconSmall("icons/warning.png"));
                return this;
            }
        }
    }

    public ImageIcon getIcon(String path, int size) {
        URL imageURL = S57OptionsPanel.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }

    public ImageIcon getIconSmall(String path) {
        URL imageURL = S57OptionsPanel.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(11, 11, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }

    public ImageIcon getIconMedium(String path) {
        URL imageURL = S57OptionsPanel.class.getResource(path);
        if (imageURL != null) {
            ImageIcon icon = new ImageIcon(imageURL);
            Image image = icon.getImage().getScaledInstance(14, 14, Image.SCALE_SMOOTH);
            icon.setImage(image);
            return icon;
        }
        return null;
    }
}

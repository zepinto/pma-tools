//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.contacts.browser;

import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jdesktop.swingx.JXTable;

import lombok.extern.slf4j.Slf4j;
import pt.lsts.neptus.tablelayout.TableLayout;
import pt.lsts.neptus.util.GuiUtils;
import pt.omst.contacts.ContactEditor;
import pt.omst.mapview.SlippyMap;
import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

@Slf4j
public class ContactBrowser extends JPanel {

    JXTable table;
    SlippyMap map;
    ContactEditor editor;
    ContactCollection collection;
    
    public ContactBrowser(File folder) throws IOException {

        setLayout(new TableLayout(new double[]{TableLayout.FILL, TableLayout.FILL}, new double[]{TableLayout.PREFERRED, TableLayout.FILL}));

        collection = new ContactCollection(folder);

        editor = new ContactEditor();
        map = new SlippyMap();
        table = new JXTable();
        ZContactTableModel model = new ZContactTableModel(collection);
        table.setModel(model);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1)
                return;
            CompressedContact contact = model.getContactAt(row);
            map.focus(contact, 19);
            try {
                editor.loadZct(contact.getZctFile());
            } catch (IOException ex) {
                log.warn("Error loading contact", ex);
            }
        });
        JScrollPane scroll = new JScrollPane(table);


        add(editor, "0, 0, 1, 0");
        add(scroll, "0, 1");
        add(map, "1, 1");

        table.packAll();
    }

    public void setSelectedContact(File contact) {
        if (contact == null) {
            table.clearSelection();
            return;
        }
        CompressedContact c = collection.getContact(contact);
        if (c == null) {
            table.clearSelection();
            return;
        }
        for (int i = 0; i < table.getRowCount(); i++) {
            CompressedContact cc = ((ZContactTableModel) table.getModel()).getContactAt(i);
            if (cc.equals(c)) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                break;
            }
        }
    }


    @Override
    public void doLayout() {
        super.doLayout();
        table.packAll();
    }

    public static void main(String[] args) {
       // GeneralPreferences.uiLookAndFeel = GuiUtils.UILookAndFeel.NEPTUS_DARK;
        GuiUtils.setLookAndFeel();
        try {
            JFrame frame = new JFrame("Contact Browser");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setContentPane(new ContactBrowser(new File("/LOGS/")));
            frame.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

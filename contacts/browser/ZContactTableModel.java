package pt.omst.rasterlib.contacts.browser;

import pt.omst.rasterlib.contacts.CompressedContact;
import pt.omst.rasterlib.contacts.ContactCollection;

import javax.swing.table.AbstractTableModel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ZContactTableModel extends AbstractTableModel {
    private final List<CompressedContact> contacts;
    private final String[] columnNames = {"Timestamp", "Label", "Classification", "Description", "Position", "Dimensions"};
    private final ContactCollection collection;

    public ZContactTableModel(ContactCollection collection) {
        this.collection = collection;
        this.contacts = collection.getAllContacts();
    }

    @Override
    public int getRowCount() {
        return contacts.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0, 1, 2, 3, 4, 5 -> String.class;
            default -> null;
        };
    }

    public CompressedContact getContactAt(int row) {
        return contacts.get(row);
    }

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    @Override
    public Object getValueAt(int row, int col) {
        CompressedContact contact = contacts.get(row);
        return switch (col) {
            case 0 -> sdf.format(new Date(contact.getTimestamp()));
            case 1 -> contact.getLabel();
            case 2 -> contact.getClassification();
            case 3 -> contact.getDescription();
            case 4 -> contact.getLocation().toString();
            case 5 -> ""; //fixme
            default -> null;
        };
    }
}

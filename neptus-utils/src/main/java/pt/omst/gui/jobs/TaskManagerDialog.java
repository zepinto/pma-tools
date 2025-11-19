package pt.omst.gui.jobs;

import java.awt.Component;
import java.awt.Frame;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

public class TaskManagerDialog extends JDialog {

    public TaskManagerDialog(Frame owner) {
        super(owner, "Background Task Manager", false); // Non-modal
        setSize(600, 400);
        setLocationRelativeTo(owner);

        JTable table = new JTable(JobManager.getInstance().getTableModel());
        table.setRowHeight(30);

        // 1. Setup Progress Bar Renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new ProgressBarRenderer());

        // 2. Setup Cancel Button Renderer & Editor
        table.getColumnModel().getColumn(3).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new ButtonEditor(new JCheckBox()));

        add(new JScrollPane(table));
    }

    // --- Helpers for Table Rendering ---

    static class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressBarRenderer() {
            setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            int progress = (int) value;
            setValue(progress);
            return this;
        }
    }

    static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setText("Cancel");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            return this;
        }
    }

    static class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private BackgroundJob currentJob;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton("Cancel");
            button.setOpaque(true);
            button.addActionListener(e -> {
                if (currentJob != null && !currentJob.isDone()) {
                    currentJob.cancel(true); // Request cancellation
                }
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
                int column) {
            currentJob = (BackgroundJob) value;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return currentJob;
        }
    }
}
package pt.omst.gui.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.table.AbstractTableModel;

public class JobManager {
    private static JobManager instance;
    private final List<BackgroundJob> jobs;
    private final ExecutorService executor;
    private final JobTableModel tableModel;

    private JobManager() {
        jobs = new ArrayList<>();
        // Limits to 5 concurrent jobs, others wait in queue
        executor = Executors.newFixedThreadPool(2);
        tableModel = new JobTableModel();
    }

    public static synchronized JobManager getInstance() {
        if (instance == null)
            instance = new JobManager();
        return instance;
    }

    public void submit(BackgroundJob job) {
        jobs.add(job);
        // Listen for progress/status changes to update the UI
        job.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName()) || "status".equals(evt.getPropertyName())) {
                int index = jobs.indexOf(job);
                if (index != -1)
                    tableModel.fireTableRowsUpdated(index, index);
            }
        });

        tableModel.fireTableRowsInserted(jobs.size() - 1, jobs.size() - 1);

        // Execute using the thread pool (SwingWorker.execute() uses its own pool,
        // but defining our own gives us more control)
        executor.submit(job);
    }

    public void removeJob(BackgroundJob job) {
        // We keep it for a moment to show "Completed" state,
        // but for this simple framework, let's remove it after a short delay
        // or immediately depending on preference.
        // Here we remove immediately for simplicity:

        // Note: In a real app, you might want to move it to a "History" list.
        int index = jobs.indexOf(job);
        if (index != -1) {
            jobs.remove(index);
            tableModel.fireTableRowsDeleted(index, index);
        }
    }

    public JobTableModel getTableModel() {
        return tableModel;
    }

    // Inner class for the Table Model
    public class JobTableModel extends AbstractTableModel {
        private final String[] columns = { "Job Name", "Status", "Progress", "Action" };

        @Override
        public int getRowCount() {
            return jobs.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 3; // The Cancel button column
        }

        @Override
        public Object getValueAt(int row, int col) {
            BackgroundJob job = jobs.get(row);
            switch (col) {
                case 0:
                    return job.getJobName();
                case 1:
                    return job.getStatus();
                case 2:
                    return job.getProgress(); // Returns 0-100
                case 3:
                    return job; // Return the job object for the button
                default:
                    return null;
            }
        }
    }
}
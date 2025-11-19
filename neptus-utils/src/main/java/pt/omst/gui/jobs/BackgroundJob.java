package pt.omst.gui.jobs;

import javax.swing.SwingWorker;

public abstract class BackgroundJob extends SwingWorker<Void, String> {
    private final String name;
    private String status = "Waiting...";

    public BackgroundJob(String name) {
        this.name = name;
    }

    public String getJobName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    // Helper to update status and notify UI
    protected void updateStatus(String newStatus) {
        String oldStatus = this.status;
        this.status = newStatus;
        firePropertyChange("status", oldStatus, newStatus);
    }

    @Override
    protected void done() {
        // Cleanup logic when job finishes (cancelled or completed)
        if (isCancelled()) {
            updateStatus("Cancelled");
        } else {
            updateStatus("Completed");
        }
        JobManager.getInstance().removeJob(this);
    }
}
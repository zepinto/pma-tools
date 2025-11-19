package pt.omst.gui.jobs;

import java.awt.BorderLayout;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import pt.lsts.neptus.util.GuiUtils;

public class MainApp {
    public static void main(String[] args) {
        GuiUtils.setLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("My Application");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);
            frame.setLayout(new BorderLayout());

            // --- Create the Toolbar ---
            JToolBar toolbar = new JToolBar();
            
            JButton startJobBtn = new JButton("Start Job");
            startJobBtn.addActionListener(e -> {
                 // Submit a dummy job (using the classes from step 1)
                 JobManager.getInstance().submit(new DummyJob("Job " + System.currentTimeMillis()));
            });

            // --- INSTANTIATE THE WIDGET ---
            TaskStatusIndicator statusWidget = new TaskStatusIndicator(frame);

            toolbar.add(startJobBtn);
            toolbar.add(Box.createHorizontalGlue()); // Push widget to the right
            toolbar.add(new JLabel("Status: "));
            toolbar.add(statusWidget); // <--- Add widget here

            frame.add(toolbar, BorderLayout.NORTH);
            frame.setVisible(true);
        });
    }
    
    // Simple dummy job for testing
    static class DummyJob extends BackgroundJob {
        public DummyJob(String name) { super(name); }
        protected Void doInBackground() throws Exception {
            for(int i=0; i<100; i++) { 
                if(isCancelled()) break; 
                Thread.sleep(50); 
                setProgress(i);
                if (i == 50) {
                    updateStatus("Halfway done");
                }
            } 
            return null; 
        }
    }
}
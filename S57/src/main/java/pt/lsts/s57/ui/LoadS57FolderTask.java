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
 * Dec 14, 2011
 */
package pt.lsts.s57.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingWorker;

import pt.lsts.s57.S57;
import pt.lsts.s57.S57Listener;

/**
 * @author Hugo Dias
 */
public class LoadS57FolderTask extends SwingWorker<Void, String> {

    private List<File> folders = new ArrayList<File>();
    private S57 s57;
    private S57OptionsPanel container;

    public LoadS57FolderTask(File[] folders, S57 s57, S57OptionsPanel container) {
        this.folders = Arrays.asList(folders);
        this.s57 = s57;
        this.container = container;
    }

    public LoadS57FolderTask(List<Object> preloaded, S57 s57) {
        for (Object string : preloaded) {
            this.folders.add(new File((String) string));
        }
        this.s57 = s57;
        this.container = null;
    }

    @Override
    protected Void doInBackground() throws Exception {
        setProgress(0);
        s57.loadFolders(folders, new S57Listener() {

            @Override
            public void publishResult(Object result) {
                setProgress((Integer) result);
            }

            @Override
            public void setMessage(String msg) {
                if (msg != null)
                    publish(msg);
            }
        });
        return null;

    }

    @Override
    protected void process(List<String> chunks) {
        if (container != null)
            container.setMessage(chunks.get(chunks.size() - 1));
    }

}

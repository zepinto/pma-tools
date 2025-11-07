/*
 * Copyright (c) 2004-2021 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: zp
 * Oct 10, 2020
 */
package pt.omst.neptus.sidescan;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zp
 *
 */
public class SidescanHistogramNormalizer implements Serializable {
    
    private static final Logger LOG = LoggerFactory.getLogger(SidescanHistogramNormalizer.class);
    
    private static final long serialVersionUID = -6926787167196556272L;
    private static final int LINES_TO_COMPUTE_HISTOGRAM = 1000;

    private LinkedHashMap<Integer, float[]> histograms = new LinkedHashMap<Integer, float[]>();
    private LinkedHashMap<Integer, Float> averages = new LinkedHashMap<Integer, Float>();
    private static final Random random = new Random(System.currentTimeMillis());    
    

    public void normalize(SidescanLine line, int subsys) {
        if (!histograms.containsKey(subsys)) {
            LOG.warn("No histogram calculated for subsystem "+subsys);
            return;
        }
        float[] hist = histograms.get(subsys);
        float avg = averages.get(subsys);
        double[] data = line.getData();
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.pow(data[i], 1.2) * (avg/hist[i]);
        }
        line.setData(data);
    }
    
    public double[] normalize(double[] data) {
        return normalize(data, 0);
    }
    
    public double[] normalize(double[] data, int subsys) {
        double[] ret = new double[data.length];
        
        if (!histograms.containsKey(subsys)) {
            LOG.warn("No histogram calculated for subsystem "+subsys);
            return data;
        }
        float[] hist = histograms.get(subsys);
        float avg = averages.get(subsys);
        for (int i = 0; i < data.length; i++) {
            ret[i] = Math.pow(data[i], 1.2) * (avg/hist[i]);
        }
        return ret;
    }
   
    public synchronized static SidescanHistogramNormalizer create(SidescanParser ssParser, File logFolder) {
        if (!new File(logFolder, "mra").exists())
                new File(logFolder, "mra").mkdirs();
                            
        File cache = new File(logFolder, "mra/histogram.cache");
        
        try {
                
            if (cache.canRead()) {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cache));
                SidescanHistogramNormalizer histogram = (SidescanHistogramNormalizer) ois.readObject();
                ois.close();
                LOG.info("Read histogram from cache file.");
                if (histogram.averages.isEmpty())
                    throw new Exception();
                return histogram;
            }
            if (ssParser == null) {
                LOG.warn("Using empty histogram normalizer");
                return new SidescanHistogramNormalizer();
            }

        }
        catch (Exception e) {
            
        }
        if (ssParser == null) {
            LOG.warn("Using empty histogram normalizer");
            return new SidescanHistogramNormalizer();
        }
        LOG.info("Histogram cache not found. Creating new one.");
        SidescanHistogramNormalizer hist = new SidescanHistogramNormalizer();

        for (int subId : ssParser.getSubsystemList()) {
            LOG.info("Calculating histogram for subsystem "+subId);
            try {
                SidescanLine pivot = ssParser.getLinesBetween(ssParser.lastPingTimestamp(subId)-1000, ssParser.lastPingTimestamp(subId), subId, ssParser.getDefaultParams()).get(0);
                float[] avg = new float[pivot.getData().length];
                int count = 0;
                int logSeconds = (int) ((ssParser.lastPingTimestamp(subId) - ssParser.firstPingTimestamp(subId)) / 1000);
                while (count < LINES_TO_COMPUTE_HISTOGRAM) {
                    long randomPosition = ssParser.firstPingTimestamp(subId) + random.nextInt(logSeconds) * 1000;
                    ArrayList<SidescanLine> lines = ssParser.getLinesBetween(randomPosition, randomPosition + 1000, subId, ssParser.getDefaultParams());
                    for (int l = 0; l < lines.size(); l++) {
                        double data[] = lines.get(l).getData();
                        for (int i = 0; i < data.length; i++)
                            avg[i] = (float) ((avg[i] * count) + data[i]) / (count+1);
                        count++;
                    };
                }

                double sum = 0;
                for (int i = 0; i < avg.length; i++) {
                    if (!Float.isFinite(avg[i]))
                        avg[i] = 0;
                    sum += avg[i];
                }
                hist.histograms.put(subId, avg);
                hist.averages.put(subId, (float) (sum / avg.length));                    
            }
            catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }       
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cache));
            oos.writeObject(hist);
            oos.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        LOG.info("Histogram cache saved to "+cache.getPath()+".");
        
        return hist;
    }
    
    public static SidescanHistogramNormalizer create(File dir) {
        SidescanParser ssParser = SidescanParserFactory.build(dir);
        return create(ssParser, dir);
    }
    
    boolean hasHistogram() {
        return !histograms.isEmpty();
    }
    private SidescanHistogramNormalizer() {}
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (float[] hist : histograms.values())
            for (int i = 0; i < hist.length; i++) {
                builder.append(hist[i]+"\n");
            }
        builder.append("\n");
        return builder.toString();

    }
}

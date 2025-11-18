//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.sidescan.sdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TimeZone;

public class SdfBathymData {
    private static final Logger LOG = LoggerFactory.getLogger(SdfBathymData.class);
    private final SdfHeader header;
    public short[] bathyPortIntensity;
    public short[] bathyPortQuality;
    public short[] bathyPortAngle;
    public short[] bathyPortX;
    public short[] bathyPortY;
    public short[] bathyPortZ;
    public short[] bathyStbdIntensity;
    public short[] bathyStbdQuality;
    public short[] bathyStbdAngle;
    public short[] bathyStbdX;
    public short[] bathyStbdY;
    public short[] bathyStbdZ;

    private long timestamp;
    private int numSamples;

    public SdfBathymData(SdfHeader header) {
        this.header = header;
        numSamples = header.getNumSamples();   
        calculateTimeStamp();
    }

    void readShortArray(ByteBuffer buf, short[] array) {
        int numSamples = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numSamples; i++) {
            array[i] = buf.getShort();
        }
    }

    void skipShortArray(ByteBuffer buf) {
        int numSamples = buf.getShort() & 0xFFFF;
        LOG.info("Skipping " + numSamples + " samples");
        buf.position(buf.position() + numSamples * 2);
    }

    void parseData(ByteBuffer buf) {
        buf.position(buf.position()+6);
        
        bathyPortX = new short[numSamples];
        bathyPortY = new short[numSamples];
        bathyPortZ = new short[numSamples];
        bathyPortAngle = new short[numSamples];
        bathyPortQuality = new short[numSamples];
        bathyPortIntensity = new short[numSamples];

        bathyStbdX = new short[numSamples];
        bathyStbdY = new short[numSamples];
        bathyStbdZ = new short[numSamples];
        bathyStbdAngle = new short[numSamples];
        bathyStbdQuality = new short[numSamples];
        bathyStbdIntensity = new short[numSamples];

        readShortArray(buf, bathyPortIntensity);
        readShortArray(buf, bathyPortQuality);
        readShortArray(buf, bathyPortX);
        readShortArray(buf, bathyPortY);
        readShortArray(buf, bathyPortZ);        
        readShortArray(buf, bathyPortAngle);
        
        readShortArray(buf, bathyStbdIntensity);
        readShortArray(buf, bathyStbdQuality);
        readShortArray(buf, bathyStbdX);
        readShortArray(buf, bathyStbdY);
        readShortArray(buf, bathyStbdZ);
        readShortArray(buf, bathyStbdAngle);
    }

    /**
     *
     */
    public void calculateTimeStamp() {
        
        int year = header.getFixTimeYear();
        int month = header.getFixTimeMonth();
        int day = header.getFixTimeDay();
        int hour = header.getFixTimeHour();
        int minute = header.getFixTimeMinute();
        float fSeconds = header.getFixTimeSecond();
        int seconds = (int) fSeconds;
        double decimalSeconds =  fSeconds % 1;

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.set(year, month - 1, day, hour, minute, seconds);
        cal.set(Calendar.MILLISECOND, (int) (decimalSeconds * 1000));
        setTimestamp(cal.getTimeInMillis());
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

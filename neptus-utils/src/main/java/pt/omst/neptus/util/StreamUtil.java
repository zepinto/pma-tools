/*
 * Copyright (c) 2004-2025 OceanScan-MST
 * All rights reserved.
 *
 * This file is part of Neptus Utilities.
 */

package pt.omst.neptus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stream utility methods.
 * 
 * @author Paulo Dias
 */
public class StreamUtil {
    private static final Logger LOG = LoggerFactory.getLogger(StreamUtil.class);

    /** To avoid instantiation */
    private StreamUtil() {
    }

    /**
     * Copies an input stream to a string until end of stream.
     * This won't close the stream!! You have to close them.
     *
     * @param inStream input stream
     * @return string
     */
    public static String copyStreamToString(InputStream inStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copyStreamToStream(inStream, baos);
        return baos.toString();
    }

    /**
     * Copies an input stream to the output stream until end of stream.
     * This won't close the streams!! You have to close them.
     *
     * @param inStream input stream
     * @param outStream output stream
     * @return true if able to copy, false otherwise
     */
    public static boolean copyStreamToStream(InputStream inStream, OutputStream outStream) {
        try {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
                outStream.flush();
            }
            return true;
        } catch (IOException e) {
            LOG.error("Failed to copy stream", e);
            return false;
        }
    }
}
